package com.musictag.artistcover.data

import android.content.Context
import android.net.Uri
import com.musictag.artistcover.model.ArtistHit
import com.musictag.artistcover.model.MatchState
import com.musictag.artistcover.model.Platform
import com.musictag.artistcover.model.SongItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
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
        onSongUpdated: suspend (SongItem) -> Unit,
        onProgress: suspend (done: Int, total: Int) -> Unit,
    ) {
        if (songs.isEmpty()) return
        val sources = SourceRegistry.ordered(orderedPlatforms)
        val total = songs.size
        val outcomes = HashMap<String, ArtistOutcome>()
        val finalized = HashSet<String>()

        // 预计算「每首歌的归一化歌手键」+「歌手键 → 歌曲」倒排索引。
        // 原先内层对**每一首歌**都跑一次 aggregate()，等于 O(歌手数 × 歌曲数) 次 normalize；
        // 千首级曲库下这是千万次字符串分配。改成倒排后，总工作量只跟「歌手-歌曲关系数」成正比。
        val keysOfSong = HashMap<String, List<String>>()
        val songsOfKey = LinkedHashMap<String, MutableList<SongItem>>()
        for (song in songs) {
            val keys = song.artistList.map { NameMatcher.normalize(it) }.distinct()
            keysOfSong[song.id] = keys
            keys.forEach { key -> songsOfKey.getOrPut(key) { mutableListOf() }.add(song) }
        }

        // 按归一化键去重：同一歌手写成「周杰伦 / 周杰倫」时只搜一次
        val uniqueArtists = LinkedHashMap<String, String>()
        for (song in songs) {
            for (name in song.artistList) {
                uniqueArtists.putIfAbsent(NameMatcher.normalize(name), name)
            }
        }

        var done = 0
        for (song in songs) {
            if (keysOfSong[song.id].isNullOrEmpty()) {
                onSongUpdated(song.copy(matchState = MatchState.NOT_FOUND, error = "未能识别歌手名"))
                finalized.add(song.id)
                done++
            }
        }
        onProgress(done, total)

        for ((key, artist) in uniqueArtists) {
            coroutineContext.ensureActive()
            val affected = songsOfKey[key].orEmpty()

            affected.filter { it.id !in finalized }
                .forEach { onSongUpdated(it.copy(matchState = MatchState.SEARCHING, error = null)) }

            outcomes[key] = resolveArtist(artist, sources, outputTreeUri, overwrite)

            // 一首歌只可能在「它最后一位未处理的歌手」刚处理完时凑齐，
            // 所以只需复查受本次影响的这几首，不必再扫全表。
            for (song in affected) {
                if (song.id in finalized) continue
                val updated = aggregate(song, keysOfSong[song.id].orEmpty(), outcomes) ?: continue
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
        onArtistDone: suspend (ArtistOutcome) -> Unit,
        onProgress: suspend (done: Int, total: Int) -> Unit,
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

    /**
     * 搜索 + 下载一位歌手。整段是阻塞式的网络与 SAF 写入，
     * 所以在这里切到 IO 线程；这样 [match] / [matchArtists] 本身跑在调用方
     * 的线程上，回调（界面状态回写）就自然落在主线程，不需要跨线程改状态。
     */
    private suspend fun resolveArtist(
        artist: String,
        sources: List<ArtistImageSource>,
        outputTreeUri: Uri,
        overwrite: Boolean,
    ): ArtistOutcome = withContext(Dispatchers.IO) {
        val hit = searchAcrossPlatforms(artist, sources)
            ?: return@withContext ArtistOutcome(artist, null, null, false, null)
        when (
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

    /**
     * 把一首歌的所有歌手结果合并成最终状态；返回 null 表示这首歌还有歌手没处理完。
     *
     * [keys] 是这首歌预计算好的归一化歌手键，避免在热路径里反复 normalize。
     */
    private fun aggregate(
        song: SongItem,
        keys: List<String>,
        outcomes: Map<String, ArtistOutcome>,
    ): SongItem? {
        if (keys.isEmpty()) return null
        val resolved = keys.map { outcomes[it] }
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
