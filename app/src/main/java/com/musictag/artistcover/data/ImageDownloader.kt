package com.musictag.artistcover.data

import android.content.Context
import android.net.Uri
import com.musictag.artistcover.model.ArtistHit
import com.musictag.artistcover.model.Platform

sealed interface SaveResult {
    /** 本次真实写盘成功。 */
    data class Saved(val fileName: String) : SaveResult

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
     * 若系统仍然改名，则改回标准名并清掉多余副本；连删除都失败时退化为原地截断重写。
     */
    fun save(
        context: Context,
        treeUri: Uri,
        fileName: String,
        bytes: ByteArray,
        overwrite: Boolean,
    ): SaveResult {
        val key = canonicalFileName(fileName)
        val existing = findExisting(context, treeUri, key)
        val parent = DocumentQuery.rootDocumentUri(treeUri)

        if (existing.isEmpty()) {
            parent ?: return SaveResult.Failed("保存目录不可用")
            return createAndWrite(context, treeUri, parent, fileName, bytes)
        }
        if (!overwrite) return SaveResult.Exists(fileName)

        var allDeleted = true
        existing.forEach { uri -> if (!DocumentQuery.delete(context, uri)) allDeleted = false }
        if (allDeleted && parent != null) {
            val created = createAndWrite(context, treeUri, parent, fileName, bytes)
            if (created is SaveResult.Saved) return created
        }

        // 删除失败或创建失败：退化为原地覆盖
        val target = findExisting(context, treeUri, key).firstOrNull() ?: existing.first()
        return if (writeInto(context, target, bytes)) {
            SaveResult.Saved(fileName)
        } else {
            SaveResult.Failed("无法覆盖旧文件 $fileName")
        }
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

        val actual = DocumentQuery.displayNameOf(context, created)
        if (actual != null && actual != fileName) {
            // 系统给新文件加了「(1)」：改回标准名，并清掉其余同名副本
            DocumentQuery.rename(context, created, fileName)
            removeOthers(context, treeUri, canonicalFileName(fileName), created)
        }
        // 状态检测按归一化文件名比对，即使改名失败也能对上
        return SaveResult.Saved(fileName)
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
