package com.musictag.artistcover.data

import android.content.Context
import android.net.Uri
import com.musictag.artistcover.model.ArtistHit
import com.musictag.artistcover.model.Platform

sealed interface SaveResult {
    /** 本次真实写盘成功；[sizeBytes] 是写完后回查到的实际字节数。 */
    data class Saved(val fileName: String, val sizeBytes: Long = 0L) : SaveResult

    /** 目标目录已有同名文件且未开启覆盖。 */
    data class Exists(val fileName: String) : SaveResult

    /** 保存失败。 */
    data class Failed(val reason: String) : SaveResult
}

/** 已成功取回的图片数据及其来源地址。 */
data class FetchedImage(val bytes: ByteArray, val url: String)

/**
 * 下载歌手图片并写入用户选择的输出目录。
 *
 * 全程按 Uri 操作、走 [DocumentQuery] 的投影查询，避免 `DocumentFile` 那种
 * 「读一个属性发一次查询」的开销——保存和刷新都只发一到两次查询。
 */
object ImageDownloader {

    private val ILLEGAL_CHARS = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")

    private val AUTO_SUFFIX = Regex("\\s*\\(\\d+\\)$")

    /** 「歌手名.jpg」，文件名中的非法字符统一替换，控制长度以免超出文件系统限制。 */
    fun safeFileName(artistName: String): String {
        val cleaned = ILLEGAL_CHARS.replace(artistName, "_").trim().trimEnd('.', ' ')
        val base = cleaned.ifBlank { "未知歌手" }.take(60)
        return "$base.jpg"
    }

    /**
     * 去掉系统自动加的「(1)」后缀。
     *
     * 有些 ROM 在写入同目录同名字文件时会自行改名成 `郭静 (1).jpg`，
     * 靠这个归一化，状态检测和覆盖逻辑才能把两份认成同一个歌手。
     */
    fun canonicalFileName(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "")
        val base = if (ext.isEmpty()) fileName else fileName.substringBeforeLast('.')
        val stripped = AUTO_SUFFIX.replace(base, "").trim().trimEnd('.', ' ')
        return if (ext.isEmpty()) stripped else "$stripped.$ext"
    }

    /** 按顺序尝试候选地址，返回第一份可用图片；全部失败时抛出最后一个错误。 */
    fun fetchBytes(http: HttpClient, hit: ArtistHit): FetchedImage {
        var lastError: Throwable? = null
        for (url in hit.imageUrls) {
            try {
                val bytes = http.getBytes(url, referer = refererOf(hit.platform))
                if (bytes.isNotEmpty()) return FetchedImage(bytes, url)
                lastError = IllegalStateException("返回空图片数据")
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("无可用图片地址")
    }

    /**
     * 写入图片。
     *
     * 覆盖策略：先删掉目录里所有同名的旧文件（含系统加过「(1)」后缀的），再新建；
     * 若系统仍然改名，则改回标准名并清掉多余副本；删不掉旧文件时退化为原地截断重写。
     *
     * 无论走哪条路径，最后都必须用 [verifyOnDisk] 回查目录确认文件真的在，才敢报成功。
     */
    fun save(
        context: Context,
        treeUri: Uri,
        fileName: String,
        bytes: ByteArray,
        overwrite: Boolean,
    ): SaveResult {
        if (bytes.isEmpty()) return SaveResult.Failed("图片数据为空")

        val key = canonicalFileName(fileName)
        val parent = DocumentQuery.rootDocumentUri(treeUri)
            ?: return SaveResult.Failed("保存目录不可用，请重新选择保存文件夹")
        val existing = findExisting(context, treeUri, key)

        // 已有真实文件（非 0 字节）且不允许覆盖：跳过
        if (!overwrite && existing.any { DocumentQuery.sizeOf(context, it) > 0L }) {
            return SaveResult.Exists(fileName)
        }

        var allDeleted = true
        existing.forEach { uri -> if (!DocumentQuery.delete(context, uri)) allDeleted = false }

        if (allDeleted) {
            // 旧文件已清干净，直接新建。失败就如实报失败——
            // 绝不能再退化去写那些刚被删掉的 Uri，否则 provider 可能「写成功」却
            // 不产出任何文件，界面显示已下载、文件夹里却什么都没有。
            return createAndWrite(context, treeUri, parent, fileName, bytes)
        }

        // 有旧文件删不掉：原地覆盖它（这个 Uri 确实还活着）
        val target = findExisting(context, treeUri, key).firstOrNull()
            ?: return SaveResult.Failed("无法清理同名旧文件 $fileName")
        if (!writeInto(context, target, bytes)) return SaveResult.Failed("写入失败：$fileName")
        return verifyOnDisk(context, treeUri, key, fileName, target)
    }

    /**
     * 写完回查：必须同时满足「文件确实出现在保存目录里」和「大小大于 0」才算成功。
     *
     * 之前只看「输出流没抛异常」就报 [SaveResult.Saved]：一旦 provider 静默失败
     * （或对着刚删除的 Uri 拿到了流），就会谎报成功——界面显示已下载、文件夹里却什么都没有。
     */
    private fun verifyOnDisk(
        context: Context,
        treeUri: Uri,
        key: String,
        fileName: String,
        fallback: Uri? = null,
    ): SaveResult {
        // provider 偶尔会有极短的可见性延迟，查不到时稍等再查一次
        repeat(2) { attempt ->
            val inFolder = findExisting(context, treeUri, key).firstOrNull()
            val size = when {
                inFolder != null -> DocumentQuery.sizeOf(context, inFolder)
                fallback != null -> DocumentQuery.sizeOf(context, fallback)
                else -> 0L
            }
            if (inFolder != null && size > 0L) return SaveResult.Saved(fileName, size)
            if (attempt == 0) runCatching { Thread.sleep(150) }
        }
        return SaveResult.Failed("$fileName 未出现在保存文件夹里（保存目录可能已失效，请重新选择）")
    }

    /** 命中一次：下载 + 保存。文件名用歌曲里识别出的歌手名，保证与状态检测一致。 */
    fun download(
        context: Context,
        treeUri: Uri,
        hit: ArtistHit,
        http: HttpClient,
        overwrite: Boolean,
        targetName: String,
    ): SaveResult {
        val fileName = safeFileName(targetName)
        val bytes = try {
            fetchBytes(http, hit).bytes
        } catch (t: Throwable) {
            return SaveResult.Failed(t.message ?: "图片下载失败")
        }
        return save(context, treeUri, fileName, bytes, overwrite)
    }

    /** Deezer 的 CDN 不需要 Referer，返回空串即可（HttpClient 会跳过空 Referer）。 */
    fun refererOf(platform: Platform): String = when (platform) {
        Platform.NETEASE -> "https://music.163.com/"
        Platform.QQ -> "https://y.qq.com/"
        Platform.DEEZER -> ""
    }

    private fun createAndWrite(
        context: Context,
        treeUri: Uri,
        parent: Uri,
        fileName: String,
        bytes: ByteArray,
    ): SaveResult {
        val created = DocumentQuery.createFile(context, parent, "image/jpeg", fileName)
            ?: return SaveResult.Failed("创建文件失败：$fileName")

        if (!writeInto(context, created, bytes)) {
            DocumentQuery.delete(context, created)
            return SaveResult.Failed("写入失败：$fileName")
        }

        val key = canonicalFileName(fileName)
        val actual = DocumentQuery.displayNameOf(context, created)
        if (actual != null && canonicalFileName(actual) != key) {
            // 系统给新文件加了「(1)」：改回标准名，并清掉其余同名副本
            DocumentQuery.rename(context, created, fileName)
            removeOthers(context, treeUri, key, created)
        }
        return verifyOnDisk(context, treeUri, key, fileName, created)
    }

    /** 目录里所有「同一个歌手」的文件（含系统加过后缀的），一次查询拿全。 */
    private fun findExisting(context: Context, treeUri: Uri, key: String): List<Uri> =
        DocumentQuery.listChildren(context, treeUri)
            .filter { !it.isDirectory && canonicalFileName(it.name) == key }
            .map { it.uri }

    private fun removeOthers(context: Context, treeUri: Uri, key: String, keep: Uri) {
        DocumentQuery.listChildren(context, treeUri)
            .filter { !it.isDirectory && canonicalFileName(it.name) == key && it.uri != keep }
            .forEach { DocumentQuery.delete(context, it.uri) }
    }

    /** 优先用「wt」（截断）模式，失败再退到「w」。 */
    private fun writeInto(context: Context, uri: Uri, bytes: ByteArray): Boolean {
        for (mode in listOf("wt", "w")) {
            try {
                val stream = context.contentResolver.openOutputStream(uri, mode) ?: continue
                stream.use { out ->
                    out.write(bytes)
                    out.flush()
                }
                return true
            } catch (_: Throwable) {
                // 换下一个模式
            }
        }
        return false
    }
}
