package com.musictag.artistcover.data

import android.content.Context
import android.net.Uri
import com.musictag.artistcover.model.ArtistSourceKind
import com.musictag.artistcover.model.SongItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class CachedSongs(
    val songs: List<SongItem>,
    val savedAt: Long,
)

/**
 * 扫描结果缓存。
 *
 * 歌曲元数据读取（MediaMetadataRetriever）很慢，全量重扫动辄十几秒，
 * 所以把结果按目录持久化到应用私有目录，启动时直接还原，只有手动刷新才重新扫描。
 */
object SongCache {

    private const val FILE_NAME = "song_cache.json"

    fun load(context: Context, treeUri: String?): CachedSongs? {
        if (treeUri.isNullOrBlank()) return null
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        return try {
            val root = JSONObject(file.readText())
            if (root.optString("tree") != treeUri) return null
            val array = root.optJSONArray("songs") ?: return null
            val songs = (0 until array.length()).mapNotNull { parseSong(array.optJSONObject(it)) }
            CachedSongs(songs, root.optLong("savedAt"))
        } catch (_: Throwable) {
            null
        }
    }

    fun save(context: Context, treeUri: String, songs: List<SongItem>) {
        try {
            val array = JSONArray()
            songs.forEach { song -> array.put(toJson(song)) }
            val root = JSONObject()
                .put("tree", treeUri)
                .put("savedAt", System.currentTimeMillis())
                .put("songs", array)
            File(context.filesDir, FILE_NAME).writeText(root.toString())
        } catch (_: Throwable) {
            // 缓存写失败不影响主流程
        }
    }

    private fun toJson(song: SongItem): JSONObject = JSONObject()
        .put("id", song.id)
        .put("name", song.displayName)
        .put("size", song.sizeBytes)
        .put("addedAt", song.addedAt)
        .put("title", song.title ?: JSONObject.NULL)
        .put("artist", song.artist ?: JSONObject.NULL)
        .put("source", song.artistSource.name)
        .put("artists", JSONArray().apply { song.artists.forEach { put(it) } })

    private fun parseSong(json: JSONObject?): SongItem? {
        json ?: return null
        val id = json.optString("id").takeIf { it.isNotBlank() } ?: return null
        val artists = json.optJSONArray("artists")?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it)?.takeIf { name -> name.isNotBlank() } }
        } ?: emptyList()
        return SongItem(
            id = id,
            uri = Uri.parse(id),
            displayName = json.optString("name"),
            sizeBytes = json.optLong("size"),
            addedAt = json.optLong("addedAt"),
            title = json.optString("title").takeIf { it.isNotBlank() && it != "null" },
            artist = json.optString("artist").takeIf { it.isNotBlank() && it != "null" },
            artists = artists,
            artistSource = runCatching { ArtistSourceKind.valueOf(json.optString("source")) }
                .getOrDefault(ArtistSourceKind.UNKNOWN),
        )
    }
}
