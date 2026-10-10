package com.musictag.artistcover.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

/**
 * 有容量上限、近似 LRU 的图片缓存。
 *
 * 为什么不直接用裸 `mutableStateMapOf`：曲库上千首时，一张 96px 的 ARGB_8888 位图
 * 约 36KB，无界增长会把低端机的堆直接吃爆（2000 首 ≈ 72MB）。这里底层仍是
 * SnapshotStateMap，重组照常触发，只是在外面补了一层淘汰。
 */
class BoundedImageCache(private val maxEntries: Int) {

    private val map = mutableStateMapOf<String, ImageBitmap?>()

    /** 访问顺序：命中的 key 移到队尾，淘汰时从队头丢，近似 LRU。 */
    private val order = ArrayDeque<String>()

    operator fun get(key: String): ImageBitmap? = map[key]

    fun containsKey(key: String): Boolean = map.containsKey(key)

    operator fun set(key: String, value: ImageBitmap?) {
        if (map.containsKey(key)) order.remove(key)
        map[key] = value
        order.addLast(key)
        while (order.size > maxEntries) {
            map.remove(order.removeFirst())
        }
    }

    fun clear() {
        map.clear()
        order.clear()
    }
}

/** 专辑封面 / 图片预览的解码与缓存。 */
object ArtworkCache {

    /**
     * 每个缓存最多保留多少张。按 96px 位图约 36KB 估算，两个缓存合计上限约 29MB，
     * 足够覆盖列表滚动，又不会在低端机上顶到堆顶。
     */
    private const val MAX_ENTRIES = 400

    /** key 为歌曲 id；value 为 null 表示该歌曲没有内嵌封面（用 containsKey 区分未加载）。 */
    val embedded = BoundedImageCache(MAX_ENTRIES)

    /** key 为保存文件夹里的文件名，用于歌曲库/歌手列表的小缩略图。 */
    val localArtist = BoundedImageCache(MAX_ENTRIES)

    /** 曲库整体变化（重扫 / 换文件夹）后调用：旧位图既占内存，也可能按 uri 串到新曲目上。 */
    fun clear() {
        embedded.clear()
        localArtist.clear()
    }

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
