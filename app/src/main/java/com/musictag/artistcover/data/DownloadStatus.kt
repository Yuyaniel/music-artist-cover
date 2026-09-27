package com.musictag.artistcover.data

import android.content.Context
import android.net.Uri
import com.musictag.artistcover.model.DownloadState

/**
 * 保存文件夹里的文件名 → Uri 索引。
 *
 * 判断「下过没有」只看这份索引，不需要逐个文件去查 DocumentsProvider，所以列表滚动时零开销。
 */
class SavedFiles(val files: Map<String, Uri>) {

    operator fun contains(fileName: String): Boolean = files.containsKey(fileName)

    fun uriOf(fileName: String): Uri? = files[fileName]

    companion object {
        val EMPTY = SavedFiles(emptyMap())

        /**
         * 读取保存文件夹索引。
         *
         * 只发**一次** ContentResolver 查询；若改用 `DocumentFile.listFiles()` + `getName()`，
         * 几百个文件会放大成几百次查询，刷新能卡好几秒。
         */
        fun load(context: Context, treeUri: Uri?): SavedFiles {
            if (treeUri == null) return EMPTY
            return try {
                val map = LinkedHashMap<String, Uri>()
                DocumentQuery.listChildren(context, treeUri).forEach { child ->
                    if (child.isDirectory) return@forEach
                    map.putIfAbsent(ImageDownloader.canonicalFileName(child.name), child.uri)
                }
                SavedFiles(map)
            } catch (_: Throwable) {
                EMPTY
            }
        }
    }
}

/** 根据歌手列表与本地已有文件，判断这首歌的下载状态。 */
fun downloadStateOf(artists: List<String>, saved: SavedFiles): DownloadState {
    if (artists.isEmpty()) return DownloadState.UNKNOWN
    val hits = artists.count { ImageDownloader.safeFileName(it) in saved }
    return when {
        hits == 0 -> DownloadState.MISSING
        hits == artists.size -> DownloadState.DOWNLOADED
        else -> DownloadState.PARTIAL
    }
}

/** 单个歌手的下载状态。 */
fun downloadStateOf(artist: String, saved: SavedFiles): DownloadState =
    if (ImageDownloader.safeFileName(artist) in saved) DownloadState.DOWNLOADED else DownloadState.MISSING
