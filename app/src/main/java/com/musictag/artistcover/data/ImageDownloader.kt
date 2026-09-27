package com.musictag.artistcover.data

import android.content.Context
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
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

/** 下载歌手图片并写入用户选择的输出目录。 */
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
     * 覆盖策略：先删掉目标目录里所有同名的旧文件（含系统加过「(1)」后缀的），
     * 再新建；若系统仍然改名，则把新文件改回标准名并清掉多余副本；
     * 连删除都失败时，退化为用 `wt` 模式原地截断重写。
     */
    fun save(
        context: Context,
        outputDir: DocumentFile,
        fileName: String,
        bytes: ByteArray,
        overwrite: Boolean,
    ): SaveResult {
        val existing = findExistingFiles(outputDir, fileName)

        if (existing.isEmpty()) {
            return createAndWrite(context, outputDir, fileName, bytes, fileName)
        }
        if (!overwrite) return SaveResult.Exists(fileName)

        var allDeleted = true
        for (file in existing) {
            if (!runCatching { file.delete() }.getOrDefault(false)) allDeleted = false
        }
        if (allDeleted) {
            val created = createAndWrite(context, outputDir, fileName, bytes, fileName)
            if (created is SaveResult.Saved) return created
        }

        // 删除失败或创建失败：退化为原地覆盖
        val target = findExistingFiles(outputDir, fileName).firstOrNull() ?: existing.first()
        return if (writeInto(context, target, bytes)) {
            SaveResult.Saved(target.name ?: fileName)
        } else {
            SaveResult.Failed("无法覆盖旧文件 $fileName")
        }
    }

    /** 命中一次：下载 + 保存。文件名用歌曲里识别出的歌手名，保证与状态检测一致。 */
    fun download(
        context: Context,
        outputDir: DocumentFile,
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
        return save(context, outputDir, fileName, bytes, overwrite)
    }

    fun refererOf(platform: Platform): String = when (platform) {
        Platform.NETEASE -> "https://music.163.com/"
        Platform.QQ -> "https://y.qq.com/"
    }

    private fun createAndWrite(
        context: Context,
        outputDir: DocumentFile,
        fileName: String,
        bytes: ByteArray,
        expectedName: String,
    ): SaveResult {
        val created = try {
            outputDir.createFile("image/jpeg", fileName)
        } catch (_: Throwable) {
            null
        } ?: return SaveResult.Failed("创建文件失败：$fileName")

        if (!writeInto(context, created, bytes)) {
            runCatching { created.delete() }
            return SaveResult.Failed("写入失败：$fileName")
        }

        var actual = created.name ?: expectedName
        if (actual != expectedName) {
            // 系统给新文件加了「(1)」：改回标准名，并清掉其余同名单副本
            runCatching {
                DocumentsContract.renameDocument(context.contentResolver, created.uri, expectedName)
            }
            removeOthers(outputDir, expectedName, created)
            actual = created.name ?: expectedName
            if (canonicalFileName(actual) == canonicalFileName(expectedName)) actual = expectedName
        }
        return SaveResult.Saved(actual)
    }

    /** 找出目标目录里所有「同一个歌手」的文件（含系统加过后缀的）。 */
    fun findExistingFiles(outputDir: DocumentFile, fileName: String): List<DocumentFile> {
        val key = canonicalFileName(fileName)
        val files = try {
            outputDir.listFiles()
        } catch (_: Throwable) {
            return emptyList()
        }
        return files.filter { file ->
            val name = file.name ?: return@filter false
            canonicalFileName(name) == key
        }
    }

    private fun removeOthers(outputDir: DocumentFile, fileName: String, keep: DocumentFile) {
        findExistingFiles(outputDir, fileName).forEach { file ->
            if (file.uri != keep.uri) runCatching { file.delete() }
        }
    }

    /** 优先用「wt」（截断）模式，失败再退到「w」。 */
    private fun writeInto(context: Context, file: DocumentFile, bytes: ByteArray): Boolean {
        for (mode in listOf("wt", "w")) {
            try {
                val stream = context.contentResolver.openOutputStream(file.uri, mode) ?: continue
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
