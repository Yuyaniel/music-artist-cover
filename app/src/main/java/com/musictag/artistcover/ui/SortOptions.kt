package com.musictag.artistcover.ui

import com.musictag.artistcover.R

/** 歌曲列表排序方式。文字标签在 [labelRes]，实际比较逻辑见 SongPage。 */
enum class SongSort(val id: String, val labelRes: Int) {
    MISSING_FIRST("missing_first", R.string.sort_missing_first),
    ADDED_DESC("added_desc", R.string.sort_added_desc),
    ADDED_ASC("added_asc", R.string.sort_added_asc);

    companion object {
        fun from(id: String?): SongSort = entries.firstOrNull { it.id == id } ?: ADDED_DESC
    }
}

/** 歌手列表排序方式。 */
enum class ArtistSort(val id: String, val labelRes: Int) {
    MISSING_FIRST("missing_first", R.string.sort_missing_first),
    COUNT_DESC("count_desc", R.string.sort_count_desc),
    COUNT_ASC("count_asc", R.string.sort_count_asc),
    RECENT_DESC("recent_desc", R.string.sort_recent_desc);

    companion object {
        fun from(id: String?): ArtistSort = entries.firstOrNull { it.id == id } ?: COUNT_DESC
    }
}
