package com.musictag.artistcover.data

import android.content.Context
import android.net.Uri
import com.musictag.artistcover.model.SongItem

/** 歌曲里有、但保存文件夹里没有图片的歌手。 */
data class MissingArtist(val artist: String, val songCount: Int)

/** 保存文件夹里有、但歌曲里没有对应歌手的图片文件。 */
data class ExtraImageFile(val fileName: String, val uri: Uri)

data class LibraryCheckResult(
    val missing: List<MissingArtist>,
    val extra: List<ExtraImageFile>,
) {
    val isClean: Boolean get() = missing.isEmpty() && extra.isEmpty()
}

/**
 * 本地检查：把「歌曲里出现的全部歌手」与「保存文件夹里已有的艺术家图片」做差异对比。
 */
object LibraryChecker {

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")

    fun check(songs: List<SongItem>, saved: SavedFiles): LibraryCheckResult {
        // 歌曲里需要的歌手：归一化名 -> (展示名, 歌曲数)
        val needed = LinkedHashMap<String, Pair<String, Int>>()
        songs.forEach { song ->
            song.artistList.forEach { artist ->
                val key = NameMatcher.normalize(artist)
                if (key.isEmpty()) return@forEach
                val existing = needed[key]
                needed[key] = if (existing == null) artist to 1 else existing.first to (existing.second + 1)
            }
        }

        val localKeys = saved.files.keys

        // 缺少：歌曲里有这位歌手，但保存文件夹里找不到「歌手名.jpg」
        val missing = needed.values
            .filter { (display, _) -> ImageDownloader.safeFileName(display) !in localKeys }
            .map { (display, count) -> MissingArtist(display, count) }
            .sortedByDescending { it.songCount }

        // 多余：保存文件夹里的图片，其文件名反推的歌手没有任何歌曲包含
        val extra = localKeys
            .filter { name -> name.substringAfterLast('.', "").lowercase() in IMAGE_EXTENSIONS }
            .filter { name ->
                val artistKey = NameMatcher.normalize(name.substringBeforeLast('.'))
                artistKey.isNotEmpty() && artistKey !in needed
            }
            .sorted()
            .mapNotNull { name -> saved.uriOf(name)?.let { ExtraImageFile(name, it) } }

        return LibraryCheckResult(missing, extra)
    }

    /** 删除选中的多余图片，返回成功删除的数量。 */
    fun deleteExtras(context: Context, files: List<ExtraImageFile>): Int {
        var deleted = 0
        for (file in files) {
            if (DocumentQuery.delete(context, file.uri)) deleted++
        }
        return deleted
    }
}
