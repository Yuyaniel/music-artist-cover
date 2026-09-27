package com.musictag.artistcover.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/** 专辑封面 / 图片预览的解码与缓存。 */
object ArtworkCache {

    /** key 为歌曲 id；value 为 null 表示该歌曲没有内嵌封面（用 containsKey 区分未加载）。 */
    val embedded = mutableStateMapOf<String, ImageBitmap?>()

    /** key 为保存文件夹里的文件名，用于歌曲库/歌手列表的小缩略图。 */
    val localArtist = mutableStateMapOf<String, ImageBitmap?>()

    fun loadEmbedded(context: Context, uri: Uri, targetSize: Int = 96): ImageBitmap? {
        val bytes = try {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                retriever.embeddedPicture
            } finally {
                runCatching { retriever.release() }
            }
        } catch (_: Throwable) {
            null
        }
        return bytes?.let { decode(it, targetSize) }
    }

    fun loadFromUri(context: Context, uri: Uri, targetSize: Int): ImageBitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            decode(stream.readBytes(), targetSize)
        }
    } catch (_: Throwable) {
        null
    }

    fun decode(bytes: ByteArray, targetSize: Int): ImageBitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, targetSize)
        }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)?.asImageBitmap()
    } catch (_: Throwable) {
        null
    }

    private fun sampleSize(width: Int, height: Int, target: Int): Int {
        if (width <= 0 || height <= 0) return 1
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= target && h / 2 >= target) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }
}
