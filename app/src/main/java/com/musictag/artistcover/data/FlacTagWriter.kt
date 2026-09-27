package com.musictag.artistcover.data

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * FLAC 专辑艺术家（Vorbis Comment `ALBUMARTIST`）写入。
 *
 * ## 安全设计——和 MP3 一样，绝不移动一个字节
 *
 * FLAC 的元数据块在文件最前面，音频紧随其后。要在不移动音频的前提下塞进新内容，
 * 必须找到等量的可回收空间：**标签尾部预留的 PADDING 块**（`flac` 命令行默认会留 8KB）。
 *
 * 做法是把 `VORBIS_COMMENT` 到音频起点之间的整段区域重新组装：
 * 新 comment 块 + 原样保留的其它块（PICTURE / SEEKTABLE 等）+ 重新计算大小的 PADDING，
 * 总长度与原区域**完全一致**，所以音频起点不变、文件长度不变。
 *
 * 空间不够时直接放弃并说明原因，绝不去重排音频数据。
 */
object FlacTagWriter {

    private const val BLOCK_VORBIS_COMMENT = 4
    private const val BLOCK_PADDING = 1
    private const val KEY = "ALBUMARTIST"

    /** 元数据区的合理上限，防止损坏文件导致巨量读取。 */
    private const val MAX_REGION = 64 * 1024 * 1024

    private const val MAX_COMMENTS = 4096

    fun writeAlbumArtist(context: Context, uri: Uri, value: String): TagWriteResult {
        val descriptor = try {
            context.contentResolver.openFileDescriptor(uri, "rw")
        } catch (_: Throwable) {
            null
        } ?: return TagWriteResult.Failed("无法以读写方式打开文件")

        return descriptor.use { pfd ->
            val input = FileInputStream(pfd.fileDescriptor)
            val output = FileOutputStream(pfd.fileDescriptor)

            val magic = ByteArray(4)
            if (TagIo.readAt(input, ByteBuffer.wrap(magic), 0L) < 4) return TagWriteResult.Skipped("文件过小")
            if (String(magic, Charsets.ISO_8859_1) != "fLaC") return TagWriteResult.Skipped("不是 FLAC 文件")

            // 遍历元数据块，定位 VORBIS_COMMENT 与音频起点
            var offset = 4L
            var commentStart = -1L
            var audioStart = -1L
            var blockCount = 0
            val header = ByteArray(4)
            while (true) {
                if (TagIo.readAt(input, ByteBuffer.wrap(header), offset) < 4) {
                    return TagWriteResult.Skipped("元数据块不完整")
                }
                val isLast = (header[0].toInt() and 0x80) != 0
                val type = header[0].toInt() and 0x7F
                val length = be24(header, 1)
                if (type == BLOCK_VORBIS_COMMENT && commentStart < 0) commentStart = offset
                offset += 4 + length
                blockCount++
                if (isLast) {
                    audioStart = offset
                    break
                }
                if (blockCount > 1024) return TagWriteResult.Skipped("元数据块数量异常")
            }

            if (commentStart < 0) return TagWriteResult.Skipped("没有 VORBIS_COMMENT 块（需要重建元数据，本工具不做）")

            val regionLength = (audioStart - commentStart).toInt()
            if (regionLength <= 0) return TagWriteResult.Skipped("元数据区大小异常")
            if (regionLength > MAX_REGION) return TagWriteResult.Skipped("元数据区过大（$regionLength 字节）")

            val region = ByteArray(regionLength)
            if (TagIo.readAt(input, ByteBuffer.wrap(region), commentStart) < regionLength) {
                return TagWriteResult.Failed("元数据区读取不完整")
            }

            // 拆出 comment 载荷；其它块（PICTURE / SEEKTABLE…）原样保留，padding 丢掉待重算
            var pos = 0
            var commentPayload: ByteArray? = null
            val keep = ArrayList<Pair<Int, ByteArray>>()
            while (pos + 4 <= region.size) {
                val type = region[pos].toInt() and 0x7F
                val length = be24(region, pos + 1)
                if (pos + 4 + length > region.size) break
                val blockPayload = region.copyOfRange(pos + 4, pos + 4 + length)
                when {
                    type == BLOCK_VORBIS_COMMENT && commentPayload == null -> commentPayload = blockPayload
                    type != BLOCK_PADDING -> keep.add(type to blockPayload)
                }
                pos += 4 + length
            }
            val payload = commentPayload ?: return TagWriteResult.Failed("VORBIS_COMMENT 解析失败")

            val newPayload = try {
                setAlbumArtist(payload, value)
            } catch (_: Throwable) {
                return TagWriteResult.Failed("VORBIS_COMMENT 解析失败")
            }

            val used = 4 + newPayload.size + keep.sumOf { 4 + it.second.size }
            // 末尾始终保留一个（可以是 0 长度的）PADDING 块
            if (used + 4 > regionLength) {
                return TagWriteResult.Skipped(
                    "预留空间不足（需要 ${used + 4} 字节，只有 $regionLength 字节）；" +
                        "可用 Mp3tag 给 FLAC 加 padding 后重试",
                )
            }

            val paddingLength = regionLength - used - 4
            val out = ByteArray(regionLength)
            var cursor = 0

            // padding 永远写在最后，所以前面的块一概不是 last-block
            cursor = writeBlock(out, cursor, BLOCK_VORBIS_COMMENT, newPayload, isLast = false)
            keep.forEach { (type, blockPayload) ->
                cursor = writeBlock(out, cursor, type, blockPayload, isLast = false)
            }
            cursor = writeBlock(out, cursor, BLOCK_PADDING, ByteArray(paddingLength), isLast = true)

            if (cursor != regionLength) return TagWriteResult.Failed("元数据区拼装长度不一致")

            TagIo.backup(context, uri, region)

            val written = try {
                TagIo.writeAt(output, ByteBuffer.wrap(out), commentStart)
                output.fd.sync()
                true
            } catch (_: Throwable) {
                false
            }
            if (!written) return TagWriteResult.Failed("写入失败")

            val verified = TagIo.readBackAlbumArtist(context, uri) == value
            return TagWriteResult.Written(value, verified)
        }
    }

    /** 在原 Vorbis Comment 载荷上设置 ALBUMARTIST（存在则替换，否则追加）。 */
    private fun setAlbumArtist(payload: ByteArray, value: String): ByteArray {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)

        val vendorLength = buffer.int
        if (vendorLength < 0 || vendorLength > payload.size) throw IllegalArgumentException("vendor 长度异常")
        val vendor = ByteArray(vendorLength)
        buffer.get(vendor)

        val count = buffer.int
        if (count < 0 || count > MAX_COMMENTS) throw IllegalArgumentException("comment 数量异常")

        val comments = ArrayList<String>(count + 1)
        var replaced = false
        for (index in 0 until count) {
            val length = buffer.int
            if (length < 0 || length > buffer.remaining()) throw IllegalArgumentException("comment 长度异常")
            val bytes = ByteArray(length)
            buffer.get(bytes)
            val text = String(bytes, Charsets.UTF_8)
            if (!replaced && text.substringBefore('=').equals(KEY, ignoreCase = true)) {
                comments.add("$KEY=$value")
                replaced = true
            } else {
                comments.add(text)
            }
        }
        if (!replaced) comments.add("$KEY=$value")

        val out = ByteArrayOutputStream()
        val intBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
        fun writeInt(number: Int) {
            intBuffer.clear()
            intBuffer.putInt(number)
            out.write(intBuffer.array(), 0, 4)
        }

        writeInt(vendorLength)
        out.write(vendor)
        writeInt(comments.size)
        comments.forEach { comment ->
            val bytes = comment.toByteArray(Charsets.UTF_8)
            writeInt(bytes.size)
            out.write(bytes)
        }
        return out.toByteArray()
    }

    private fun writeBlock(
        out: ByteArray,
        cursor: Int,
        type: Int,
        payload: ByteArray,
        isLast: Boolean,
    ): Int {
        out[cursor] = ((if (isLast) 0x80 else 0) or type).toByte()
        out[cursor + 1] = ((payload.size ushr 16) and 0xFF).toByte()
        out[cursor + 2] = ((payload.size ushr 8) and 0xFF).toByte()
        out[cursor + 3] = (payload.size and 0xFF).toByte()
        System.arraycopy(payload, 0, out, cursor + 4, payload.size)
        return cursor + 4 + payload.size
    }

    private fun be24(bytes: ByteArray, start: Int): Int =
        ((bytes[start].toInt() and 0xFF) shl 16) or
            ((bytes[start + 1].toInt() and 0xFF) shl 8) or
            (bytes[start + 2].toInt() and 0xFF)
}
