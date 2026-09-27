package com.musictag.artistcover.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/** 递归扫描用户指定的歌曲目录。 */
object SongScanner {

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "flac", "m4a", "aac", "ogg", "oga", "opus",
        "wav", "wma", "ape", "mka", "mp4", "wv", "dsf",
    )

    private const val MAX_DEPTH = 8

    fun scan(context: Context, treeUri: Uri): List<DocumentFile> {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return emptyList()
        val result = ArrayList<DocumentFile>()
        walk(root, result, 0)
        return result
    }

    private fun walk(dir: DocumentFile, out: MutableList<DocumentFile>, depth: Int) {
        if (depth > MAX_DEPTH) return
        val children = try {
            dir.listFiles()
        } catch (_: Throwable) {
            return
        }
        for (child in children) {
            if (child.isDirectory) {
                walk(child, out, depth + 1)
            } else {
                val name = child.name ?: continue
                if (extensionOf(name) in AUDIO_EXTENSIONS) out.add(child)
            }
        }
    }

    private fun extensionOf(name: String): String =
        name.substringAfterLast('.', "").lowercase()
}
