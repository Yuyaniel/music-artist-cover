package com.musictag.artistcover.data

import android.content.Context
import android.net.Uri

/** 扫描到的一个音频文件。 */
data class SongEntry(
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
)

/**
 * 递归扫描用户指定的歌曲目录。
 *
 * 走 [DocumentQuery] 的投影查询：每个目录只发一次查询，文件直接带上名字/大小/修改时间，
 * 不再对每个文件单独查询（否则一千首歌要四千次查询，慢到无法接受）。
 */
object SongScanner {

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "flac", "m4a", "aac", "ogg", "oga", "opus",
        "wav", "wma", "ape", "mka", "mp4", "wv", "dsf",
    )

    private const val MAX_DEPTH = 8

    fun scan(context: Context, treeUri: Uri): List<SongEntry> {
        val rootId = DocumentQuery.treeDocumentId(treeUri) ?: return emptyList()
        val result = ArrayList<SongEntry>()
        walk(context, treeUri, rootId, result, 0)
        return result
    }

    private fun walk(
        context: Context,
        treeUri: Uri,
        documentId: String,
        out: MutableList<SongEntry>,
        depth: Int,
    ) {
        if (depth > MAX_DEPTH) return
        for (child in DocumentQuery.listChildren(context, treeUri, documentId)) {
            if (child.isDirectory) {
                walk(context, treeUri, child.documentId, out, depth + 1)
            } else if (extensionOf(child.name) in AUDIO_EXTENSIONS) {
                out.add(SongEntry(child.uri, child.name, child.size, child.lastModified))
            }
        }
    }

    private fun extensionOf(name: String): String = name.substringAfterLast('.', "").lowercase()
}
