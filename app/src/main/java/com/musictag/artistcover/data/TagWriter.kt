package com.musictag.artistcover.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import com.musictag.artistcover.model.SongItem
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer

sealed interface TagWriteResult {
    /** 写入成功；[verified] 表示写完后回读确认过。 */
    data class Written(val value: String, val verified: Boolean) : TagWriteResult

    /** 这首歌不需要处理（已有值 / 没有可用的艺术家名 / 非 MP3）。 */
    data class Skipped(val reason: String) : TagWriteResult

    /** 写入失败。 */
    data class Failed(val reason: String) : TagWriteResult
}

/** 待补全的一首歌。 */
data class TagFixCandidate(val song: SongItem, val target: String)

/** 一首歌的处理结果。 */
data class TagFixOutcome(val song: SongItem, val result: TagWriteResult)

/**
 * MP3 专辑艺术家（ID3v2 TPE2）写入。
 *
 * ## 安全设计——绝不移动一个字节
 *
 * 只允许两种写法的**就地覆盖**，文件长度在写入前后完全一致，音频数据一个字节都不碰：
 *
 * 1. 已有 `TPE2` 帧 → 原地改写该帧（不够则用 0 填充，剩 4 字节以上会被解析器当作 padding 跳过）
 * 2. 没有 `TPE2` 但标签尾部有足够 padding → 把新帧写进 padding，剩余部分保持 0
 *
 * 空间不够时**直接放弃并说明原因**，不会去重排整个文件——重排一旦写坏，用户丢的是整首歌。
 * 写入前还会把原标签区备份到应用私有目录，便于出问题时回溯。
 */
object Mp3TagWriter {

    private const val FRAME_ID = "TPE2"

    /** 找出「专辑艺术家为空、但艺术家已知」的 MP3。 */
    fun planAlbumArtistFixes(songs: List<SongItem>): List<TagFixCandidate> = songs
        .filter { song ->
            song.albumArtist.isNullOrBlank() &&
                !song.artist.isNullOrBlank() &&
                song.displayName.endsWith(".mp3", ignoreCase = true)
        }
        .map { song -> TagFixCandidate(song, song.artist!!.trim()) }

    fun writeAlbumArtist(context: Context, uri: Uri, value: String): TagWriteResult {
        val descriptor = try {
            context.contentResolver.openFileDescriptor(uri, "rw")
        } catch (_: Throwable) {
            null
        } ?: return TagWriteResult.Failed("无法以读写方式打开文件")

        return descriptor.use { pfd -> writeInPlace(context, pfd, uri, value) }
    }

    private fun writeInPlace(
        context: Context,
        pfd: ParcelFileDescriptor,
        uri: Uri,
        value: String,
    ): TagWriteResult {
        // FileInputStream / FileOutputStream 共用同一个 fd，位置独立，可用带 offset 的读写
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)

        val header = ByteArray(10)
        if (readAt(input, ByteBuffer.wrap(header), 0L) < 10) return TagWriteResult.Skipped("文件过小")

        if (header[0].toInt() != 'I'.code || header[1].toInt() != 'D'.code || header[2].toInt() != '3'.code) {
            return TagWriteResult.Skipped("没有 ID3v2 标签（需要重排文件，本工具不做）")
        }
        val version = header[3].toInt() and 0xFF
        if (version != 3 && version != 4) return TagWriteResult.Skipped("ID3v2.$version 暂不支持")
        val flags = header[5].toInt() and 0xFF
        if (flags and 0x80 != 0) return TagWriteResult.Skipped("标签启用了 unsynchronisation，暂不支持")
        if (flags and 0x40 != 0) return TagWriteResult.Skipped("标签含扩展头，暂不支持")
        if (version == 4 && flags and 0x10 != 0) return TagWriteResult.Skipped("标签含 footer，暂不支持")

        val tagSize = synchsafe(header, 6)
        val tagEnd = 10 + tagSize
        if (tagSize <= 0) return TagWriteResult.Skipped("标签长度为 0")

        val tag = ByteArray(tagEnd)
        System.arraycopy(header, 0, tag, 0, 10)
        // wrap(array, offset, length) 会把 position 定位到 10，正好接着头部往后读
        val restLength = tagEnd - 10
        if (readAt(input, ByteBuffer.wrap(tag, 10, restLength), 10L) < restLength) {
            return TagWriteResult.Failed("标签区读取不完整")
        }

        // 解析帧，顺便找到 padding 起点
        var offset = 10
        var existing: Int = -1
        var existingLength = 0
        var paddingStart = tagEnd
        while (offset + 10 <= tagEnd) {
            if (tag[offset].toInt() == 0) {
                paddingStart = offset
                break
            }
            val id = String(tag, offset, 4, Charsets.ISO_8859_1)
            if (!id.all { it.isLetterOrDigit() }) return TagWriteResult.Skipped("标签结构无法解析")
            val size = if (version == 4) synchsafe(tag, offset + 4) else beInt(tag, offset + 4)
            if (size < 0 || offset + 10 + size > tagEnd) return TagWriteResult.Failed("帧「$id」长度异常")
            if (id == FRAME_ID) {
                existing = offset
                existingLength = 10 + size
            }
            offset += 10 + size
        }
        if (existing < 0 && paddingStart == tagEnd && offset != tagEnd) paddingStart = offset

        val frame = buildTextFrame(version, FRAME_ID, value)

        val writeOffset: Int
        val writeLength: Int
        if (existing >= 0) {
            // 情况 1：已有 TPE2，原地改写
            if (frame.size > existingLength) {
                return TagWriteResult.Skipped("已有 TPE2 帧空间不足（需要 ${frame.size} 字节，只有 $existingLength 字节）")
            }
            val leftover = existingLength - frame.size
            if (leftover in 1..3) return TagWriteResult.Skipped("改写后剩余空间无法对齐到 padding")
            writeOffset = existing
            writeLength = existingLength
        } else {
            // 情况 2：写进标签尾部预留的 padding
            val available = tagEnd - paddingStart
            if (available < frame.size) {
                return TagWriteResult.Skipped(
                    "标签预留空间不足（需要 ${frame.size} 字节，可用 $available 字节）；" +
                        "可用 Mp3tag 等工具给标签扩容后重试",
                )
            }
            writeOffset = paddingStart
            writeLength = frame.size
        }

        backupTag(context, uri, tag)

        val payload = ByteBuffer.allocate(writeLength)
        payload.put(frame)
        while (payload.position() < writeLength) payload.put(0) // 剩余部分保持 0
        payload.flip()

        val written = try {
            writeAt(output, payload, writeOffset.toLong())
            output.fd.sync()
            true
        } catch (_: Throwable) {
            false
        }
        if (!written) return TagWriteResult.Failed("写入失败")

        val verified = readBackAlbumArtist(context, uri)?.equals(value, ignoreCase = false) == true
        return TagWriteResult.Written(value, verified)
    }

    /** 构造一个 UTF-16（带 BOM）编码的文本帧，中英文都能存。 */
    private fun buildTextFrame(version: Int, frameId: String, value: String): ByteArray {
        val text = value.toByteArray(Charsets.UTF_16LE)
        val payload = ByteArray(1 + 2 + text.size)
        payload[0] = 0x01                        // 编码：UTF-16 + BOM
        payload[1] = 0xFF.toByte()               // BOM（小端）
        payload[2] = 0xFE.toByte()
        System.arraycopy(text, 0, payload, 3, text.size)

        val frame = ByteArray(10 + payload.size)
        System.arraycopy(frameId.toByteArray(Charsets.ISO_8859_1), 0, frame, 0, 4)
        if (version == 4) {
            writeSynchsafe(frame, 4, payload.size)
        } else {
            frame[4] = (payload.size ushr 24).toByte()
            frame[5] = (payload.size ushr 16).toByte()
            frame[6] = (payload.size ushr 8).toByte()
            frame[7] = payload.size.toByte()
        }
        // frame[8..9] 是 flags，保持 0
        System.arraycopy(payload, 0, frame, 10, payload.size)
        return frame
    }

    /** 把原标签区留一份备份，出问题时可以比对恢复。 */
    private fun backupTag(context: Context, uri: Uri, tag: ByteArray) {
        runCatching {
            val dir = File(context.filesDir, "tag_backup")
            if (!dir.exists()) dir.mkdirs()
            val name = uri.toString().hashCode().toString(16) + ".id3"
            File(dir, name).writeBytes(tag)
        }
    }

    private fun readBackAlbumArtist(context: Context, uri: Uri): String? {
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

    /** 从 [fileOffset] 处读入 [buffer]（写入位置由 buffer 自身的 position 决定）。 */
    private fun readAt(stream: FileInputStream, buffer: ByteBuffer, fileOffset: Long): Int =
        stream.channel.read(buffer, fileOffset).let { if (it < 0) 0 else it }

    private fun writeAt(stream: FileOutputStream, buffer: ByteBuffer, offset: Long) {
        var position = offset
        while (buffer.hasRemaining()) {
            position += stream.channel.write(buffer, position)
        }
    }

    private fun synchsafe(bytes: ByteArray, start: Int): Int =
        ((bytes[start].toInt() and 0x7F) shl 21) or
            ((bytes[start + 1].toInt() and 0x7F) shl 14) or
            ((bytes[start + 2].toInt() and 0x7F) shl 7) or
            (bytes[start + 3].toInt() and 0x7F)

    private fun writeSynchsafe(bytes: ByteArray, start: Int, value: Int) {
        bytes[start] = ((value ushr 21) and 0x7F).toByte()
        bytes[start + 1] = ((value ushr 14) and 0x7F).toByte()
        bytes[start + 2] = ((value ushr 7) and 0x7F).toByte()
        bytes[start + 3] = (value and 0x7F).toByte()
    }

    private fun beInt(bytes: ByteArray, start: Int): Int =
        ((bytes[start].toInt() and 0xFF) shl 24) or
            ((bytes[start + 1].toInt() and 0xFF) shl 16) or
            ((bytes[start + 2].toInt() and 0xFF) shl 8) or
            (bytes[start + 3].toInt() and 0xFF)
}
