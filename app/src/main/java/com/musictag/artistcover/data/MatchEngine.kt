package com.musictag.artistcover.data

import android.content.Context
import android.net.Uri
import com.musictag.artistcover.model.ArtistHit
import com.musictag.artistcover.model.MatchState
import com.musictag.artistcover.model.Platform
import com.musictag.artistcover.model.SongItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlin.coroutines.coroutineContext

/** 某个歌手的处理结果。 */
data class ArtistOutcome(
    val artist: String,
    val hit: ArtistHit?,
    val fileName: String?,
    val savedNow: Boolean,
    val error: String?,
)

/**
 * 批量匹配编排：把选中歌曲的歌手全部拆出来去重，同一歌手只搜索/下载一次，再回写每首歌的状态。
 * 一首歌有多个歌手（如「郭静/韦礼安」）时逐个处理，部分成功记为「部分成功」。
 */
class MatchEngine(private val context: Context) {

    private val http = HttpClient()

    suspend fun match(
        songs: List<SongItem>,
        orderedPlatforms: List<Platform>,
        outputTreeUri: Uri,
        overwrite: Boolean,
        onSongUpdated: (SongItem) -> Unit,
        onProgress: (done: Int, total: Int) -> Unit,
    ) {
        if (songs.isEmpty()) return
        val sources = SourceRegistry.ordered(orderedPlatforms)
        val total = songs.size
        val outcomes = LinkedHashMap<String, ArtistOutcome>()
        val finalized = HashSet<String>()
        var done = 0

        songs.filter { it.artistList.isEmpty() }.forEach { song ->
            onSongUpdated(song.copy(matchState = MatchState.NOT_FOUND, error = "未能识别歌手名"))
            finalized.add(song.id)
        }
        done = finalized.size
        onProgress(done, total)

        val uniqueArtists = LinkedHashSet<String>()
        songs.forEach { song -> song.artistList.forEach { uniqueArtists.add(it) } }

        for (artist in uniqueArtists) {
            coroutineContext.ensureActive()
            val key = NameMatcher.normalize(artist)

            songs.filter { it.id !in finalized && it.artistList.any { a -> NameMatcher.normalize(a) == key } }
                .forEach { onSongUpdated(it.copy(matchState = MatchState.SEARCHING, error = null)) }

            outcomes[key] = resolveArtist(artist, sources, outputTreeUri, overwrite)

            for (song in songs) {
                if (song.id in finalized) continue
                val updated = aggregate(song, outcomes) ?: continue
                onSongUpdated(updated)
                finalized.add(song.id)
                done++
                onProgress(done, total)
            }
            delay(REQUEST_GAP_MS)
        }
    }

    /**
     * 按歌手批量匹配：不关心歌曲，直接对每位歌手搜索 + 下载一次。
     * 歌手视图的多选下载走这里。
     */
    suspend fun matchArtists(
        artists: List<String>,
        orderedPlatforms: List<Platform>,
        outputTreeUri: Uri,
        overwrite: Boolean,
        onArtistDone: (ArtistOutcome) -> Unit,
        onProgress: (done: Int, total: Int) -> Unit,
    ) {
        if (artists.isEmpty()) return
        val sources = SourceRegistry.ordered(orderedPlatforms)
        val total = artists.size
        var done = 0
        onProgress(done, total)

        for (artist in artists) {
            coroutineContext.ensureActive()
            val outcome = resolveArtist(artist, sources, outputTreeUri, overwrite)
            onArtistDone(outcome)
            done++
            onProgress(done, total)
            delay(REQUEST_GAP_MS)
        }
    }

    private fun resolveArtist(
        artist: String,
        sources: List<ArtistImageSource>,
        outputTreeUri: Uri,
        overwrite: Boolean,
    ): ArtistOutcome {
        val hit = searchAcrossPlatforms(artist, sources)
            ?: return ArtistOutcome(artist, null, null, false, null)
        return when (
            val result = ImageDownloader.download(
                context = context,
                treeUri = outputTreeUri,
                hit = hit,
                http = http,
                overwrite = overwrite,
                targetName = artist,
            )
        ) {
            is SaveResult.Saved -> ArtistOutcome(artist, hit, result.fileName, true, null)
            is SaveResult.Exists -> ArtistOutcome(artist, hit, result.fileName, false, null)
            is SaveResult.Failed -> ArtistOutcome(artist, hit, null, false, result.reason)
        }
    }

    /** 返回 null 表示这首歌还有歌手没处理完。 */
    private fun aggregate(song: SongItem, outcomes: Map<String, ArtistOutcome>): SongItem? {
        val artists = song.artistList
        if (artists.isEmpty()) return null
        val resolved = artists.map { outcomes[NameMatcher.normalize(it)] }
        if (resolved.any { it == null }) return null

        val list = resolved.filterNotNull()
        val hits = list.mapNotNull { it.hit }
        val failed = list.filter { it.hit == null || it.fileName == null }
        val savedNames = list.mapNotNull { it.fileName }

        val state = when {
            hits.isEmpty() -> if (list.any { it.error != null }) MatchState.FAILED else MatchState.NOT_FOUND
            failed.isNotEmpty() -> MatchState.PARTIAL
            list.any { it.savedNow } -> MatchState.DONE
            else -> MatchState.EXISTS
        }

        return song.copy(
            matchState = state,
            matchedArtist = hits.joinToString("、") { it.name }.ifBlank { null },
            platform = hits.firstOrNull()?.platform,
            savedName = savedNames.joinToString("、").ifBlank { null },
            error = when (state) {
                MatchState.PARTIAL -> "未成功：" + failed.joinToString("、") { it.artist }
                MatchState.FAILED -> list.firstNotNullOfOrNull { it.error }
                MatchState.NOT_FOUND -> null
                else -> null
            },
        )
    }

    private fun searchAcrossPlatforms(artist: String, sources: List<ArtistImageSource>): ArtistHit? {
        for (source in sources) {
            try {
                val hit = source.searchVerified(artist, http)
                if (hit != null) {
                    AppLog.i("${source.platform.displayName} 命中：$artist → ${hit.name}")
                    return hit
                }
                AppLog.i("${source.platform.displayName} 未命中「$artist」，尝试下一个平台")
            } catch (t: Throwable) {
                AppLog.w("${source.platform.displayName} 查询「$artist」失败：${t.message ?: t.javaClass.simpleName}")
            }
        }
        return null
    }

    private companion object {
        const val REQUEST_GAP_MS = 200L
    }
}
