package com.musictag.artistcover.data

import android.content.Context
import android.content.SharedPreferences
import com.musictag.artistcover.model.Platform

/** 目录授权与平台开关的持久化。 */
class Prefs(context: Context) {

    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("music_artist_cover", Context.MODE_PRIVATE)

    var songTreeUri: String?
        get() = sp.getString(KEY_SONG_TREE, null)
        set(value) = sp.edit().putString(KEY_SONG_TREE, value).apply()

    var outputTreeUri: String?
        get() = sp.getString(KEY_OUT_TREE, null)
        set(value) = sp.edit().putString(KEY_OUT_TREE, value).apply()

    /** 是否覆盖保存文件夹中的同名旧图。 */
    var overwriteExisting: Boolean
        get() = sp.getBoolean(KEY_OVERWRITE, true)
        set(value) = sp.edit().putBoolean(KEY_OVERWRITE, value).apply()

    /** 歌曲列表排序方式（枚举 id）。 */
    var songSort: String
        get() = sp.getString(KEY_SONG_SORT, "added_desc") ?: "added_desc"
        set(value) = sp.edit().putString(KEY_SONG_SORT, value).apply()

    /** 歌手列表排序方式（枚举 id）。 */
    var artistSort: String
        get() = sp.getString(KEY_ARTIST_SORT, "count_desc") ?: "count_desc"
        set(value) = sp.edit().putString(KEY_ARTIST_SORT, value).apply()

    /** 未设置过时默认全开。 */
    fun enabledPlatforms(): Set<Platform> {
        val raw = sp.getString(KEY_PLATFORMS, null) ?: return Platform.entries.toSet()
        val ids = raw.split(',').filter { it.isNotBlank() }.toSet()
        val picked = Platform.entries.filter { it.id in ids }.toSet()
        return picked.ifEmpty { Platform.entries.toSet() }
    }

    fun setEnabledPlatforms(platforms: Set<Platform>) {
        val value = Platform.entries.filter { it in platforms }.joinToString(",") { it.id }
        sp.edit().putString(KEY_PLATFORMS, value).apply()
    }

    /** 平台优先级顺序（用户在设置里用箭头调整）。 */
    fun platformOrder(): List<Platform> {
        val raw = sp.getString(KEY_PLATFORM_ORDER, null)
        val saved = raw
            ?.split(',')
            ?.mapNotNull { id -> Platform.entries.firstOrNull { it.id == id.trim() } }
            ?: emptyList()
        // 追加后来才加入、但顺序表里还没有的平台
        return saved + Platform.entries.filter { it !in saved }
    }

    fun setPlatformOrder(order: List<Platform>) {
        sp.edit().putString(KEY_PLATFORM_ORDER, order.joinToString(",") { it.id }).apply()
    }

    private companion object {
        const val KEY_SONG_TREE = "song_tree_uri"
        const val KEY_OUT_TREE = "output_tree_uri"
        const val KEY_PLATFORMS = "enabled_platforms"
        const val KEY_PLATFORM_ORDER = "platform_order"
        const val KEY_OVERWRITE = "overwrite_existing"
        const val KEY_SONG_SORT = "song_sort"
        const val KEY_ARTIST_SORT = "artist_sort"
    }
}
