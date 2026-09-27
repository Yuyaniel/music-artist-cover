package com.musictag.artistcover.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * 标签写入的公共设施：定位读写、备份、写后回读校验。
 *
 * 为什么用文件描述符：SAF 只给流，不支持随机写；`ParcelFileDescriptor` 拿到的 fd
 * 可以用 `FileChannel.read/write(buffer, position)` 定点读写，
 * 这样就能「只覆盖标签区的那几十个字节」，不必重写整个文件——音频数据一个字节都不动。
 */
internal object TagIo {

    fun readAt(stream: FileInputStream, buffer: ByteBuffer, fileOffset: Long): Int =
        stream.channel.read(buffer, fileOffset).let { if (it < 0) 0 else it }

    fun writeAt(stream: FileOutputStream, buffer: ByteBuffer, offset: Long) {
        var position = offset
        while (buffer.hasRemaining()) {
            position += stream.channel.write(buffer, position)
        }
    }

    /** 把原标签区留一份备份，出问题时可以比对恢复。 */
    fun backup(context: Context, uri: Uri, tag: ByteArray) {
        runCatching {
            val dir = File(context.filesDir, "tag_backup")
            if (!dir.exists()) dir.mkdirs()
            File(dir, uri.toString().hashCode().toString(16) + ".bin").writeBytes(tag)
        }
    }

    /** 写完后回读专辑艺术家，确认真的写进去了（API 30+ 才有该字段）。 */
    fun readBackAlbumArtist(context: Context, uri: Uri): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)?.trim()
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }
}
