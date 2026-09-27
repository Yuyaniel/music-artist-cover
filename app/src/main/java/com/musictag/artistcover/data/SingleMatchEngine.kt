package com.musictag.artistcover.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.musictag.artistcover.model.ArtistCandidate
import com.musictag.artistcover.model.ArtistMatch
import com.musictag.artistcover.model.LocalArtistImage
import com.musictag.artistcover.model.MatchState
import com.musictag.artistcover.model.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 一次保存的结果，交给调用方决定怎么回写界面。 */
data class SaveOutcome(
    val savedCount: Int,
    val state: MatchState,
    val matchedArtist: String?,
    val platform: Platform?,
    val savedName: String?,
    val error: String?,
)

/**
 * 匹配引擎（歌曲 / 歌手两种视角共用）：
 * - 读取保存文件夹里目标歌手**已经下载过的图片**；
 * - 在指定平台上取回多个候选（含预览图）；
 * - 由用户挑选后再保存。
 */
class SingleMatchEngine(private val context: Context) {

    private val http = HttpClient()

    /** 每位歌手最多准备多少个候选供用户挑选。 */
    private val candidateLimit = ArtistImageSource.DEFAULT_CANDIDATES

    /** 预览解码目标尺寸，够大才能看清人脸。 */
    private val previewSize = 480

    fun resolveOutputDir(treeUri: Uri?): DocumentFile? =
        runCatching { treeUri?.let { DocumentFile.fromTreeUri(context, it) } }.getOrNull()

    /** 列出保存文件夹里已有的文件，构建「文件名 → Uri」索引。 */
    suspend fun loadSavedFiles(outputDir: DocumentFile?): SavedFiles = withContext(Dispatchers.IO) {
        val files = outputDir?.let { dir -> runCatching { dir.listFiles() }.getOrNull() }
            ?: return@withContext SavedFiles.EMPTY
        // 按归一化文件名建索引，系统加过「(1)」后缀的副本同样算「已下载」
        val map = LinkedHashMap<String, Uri>()
        files.forEach { file ->
            val raw = file.name ?: return@forEach
            map.putIfAbsent(ImageDownloader.canonicalFileName(raw), file.uri)
        }
        SavedFiles(map)
    }

    /** 读取目标歌手在保存文件夹里已有的图片。 */
    suspend fun loadLocalImages(artists: List<String>, outputDir: DocumentFile?): List<LocalArtistImage> =
        withContext(Dispatchers.IO) {
            artists.map { artist ->
                val expected = ImageDownloader.safeFileName(artist)
                // 连同系统加过「(1)」后缀的旧文件一起认作已下载
                val file = outputDir?.let { dir ->
                    runCatching { ImageDownloader.findExistingFiles(dir, expected).firstOrNull() }.getOrNull()
                }
                LocalArtistImage(
                    artist = artist,
                    fileName = file?.name,
                    preview = file?.let { ArtworkCache.loadFromUri(context, it.uri, LOCAL_PREVIEW_SIZE) },
                )
            }
        }

    /** 在指定平台搜索每位歌手，返回候选列表。 */
    suspend fun search(artists: List<String>, platform: Platform, label: String): List<ArtistMatch> =
        withContext(Dispatchers.IO) {
            if (artists.isEmpty()) return@withContext emptyList()
            val source = SourceRegistry.sourceOf(platform)
            val matches = artists.map { artist ->
                if (source == null) {
                    ArtistMatch(artist = artist, error = "平台不可用")
                } else {
                    searchOne(artist, source)
                }
            }
            AppLog.i(
                "搜索 $label（${platform.displayName}）" +
                    "→ 共 ${matches.sumOf { it.candidates.size }} 个候选",
            )
            matches
        }

    private fun searchOne(artist: String, source: ArtistImageSource): ArtistMatch {
        val hits = try {
            source.searchCandidates(artist, http, candidateLimit)
        } catch (t: Throwable) {
            return ArtistMatch(artist = artist, error = t.message ?: t.javaClass.simpleName)
        }

        if (hits.isEmpty()) {
            return ArtistMatch(artist = artist, error = "${source.platform.displayName} 未找到此歌手")
        }

        val candidates = hits.map { hit ->
            val fetched = try {
                ImageDownloader.fetchBytes(http, hit)
            } catch (_: Throwable) {
                null
            }
            ArtistCandidate(
                hit = hit,
                exact = NameMatcher.matches(artist, hit.name, hit.aliases),
                preview = fetched?.let { ArtworkCache.decode(it.bytes, previewSize) },
                bytes = fetched?.bytes,
            )
        }

        // 默认选中第一个已校准且有图的候选，否则退到第一个有图的候选
        val defaultIndex = candidates.indexOfFirst { it.exact && it.bytes != null }
            .takeIf { it >= 0 }
            ?: candidates.indexOfFirst { it.bytes != null }

        return ArtistMatch(
            artist = artist,
            candidates = candidates,
            selected = if (defaultIndex >= 0) defaultIndex else 0,
            error = if (candidates.none { it.bytes != null }) "候选图片均下载失败" else null,
        )
    }

    /** 保存当前选中的候选。 */
    suspend fun save(
        outputDir: DocumentFile,
        matches: List<ArtistMatch>,
        overwrite: Boolean,
    ): SaveOutcome = withContext(Dispatchers.IO) {
        var savedCount = 0
        var anyNew = false
        var lastError: String? = null
        val savedNames = mutableListOf<String>()
        val failedArtists = mutableListOf<String>()

        for (match in matches) {
            val candidate = match.selectedCandidate
            val data = candidate?.bytes
            if (candidate == null || data == null) {
                failedArtists.add(match.artist)
                match.error?.let { lastError = it }
                continue
            }
            // 文件名用歌曲里识别出的歌手名（而不是平台返回的名字），
            // 这样「已下载/未下载」检测、覆盖判断才能对上同一个文件
            val fileName = ImageDownloader.safeFileName(match.artist)
            when (val result = ImageDownloader.save(context, outputDir, fileName, data, overwrite)) {
                is SaveResult.Saved -> {
                    savedCount++
                    anyNew = true
                    savedNames.add(result.fileName)
                }

                is SaveResult.Exists -> savedNames.add(result.fileName)
                is SaveResult.Failed -> {
                    failedArtists.add(match.artist)
                    lastError = result.reason
                }
            }
        }

        val hits = matches.mapNotNull { it.selectedCandidate?.hit }
        val state = when {
            hits.isEmpty() -> if (matches.any { it.error != null }) MatchState.FAILED else MatchState.NOT_FOUND
            failedArtists.isNotEmpty() -> MatchState.PARTIAL
            anyNew -> MatchState.DONE
            else -> MatchState.EXISTS
        }

        AppLog.i("保存完成：新写入 $savedCount 张${if (failedArtists.isEmpty()) "" else "，未成功 ${failedArtists.joinToString("、")}"}")

        SaveOutcome(
            savedCount = savedCount,
            state = state,
            matchedArtist = hits.joinToString("、") { it.name }.ifBlank { null },
            platform = hits.firstOrNull()?.platform,
            savedName = savedNames.joinToString("、").ifBlank { null },
            error = when (state) {
                MatchState.PARTIAL -> "未成功：" + failedArtists.joinToString("、")
                MatchState.FAILED -> lastError
                else -> null
            },
        )
    }

    private companion object {
        const val LOCAL_PREVIEW_SIZE = 512
    }
}
