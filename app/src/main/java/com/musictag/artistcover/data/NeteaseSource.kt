package com.musictag.artistcover.data

import com.musictag.artistcover.model.ArtistHit
import com.musictag.artistcover.model.Platform
import org.json.JSONArray
import org.json.JSONObject

/**
 * 网易云音乐。
 *
 * 搜索接口：/api/search/get?type=100 直接返回歌手及其 picUrl，无需二次请求。
 * 原图动辄 1MB 以上，统一追加 CDN 缩图参数。
 */
object NeteaseSource : ArtistImageSource {

    override val platform: Platform = Platform.NETEASE

    private const val SEARCH_URL = "https://music.163.com/api/search/get"
    private const val REFERER = "https://music.163.com/"

    override fun searchCandidates(name: String, http: HttpClient, limit: Int): List<ArtistHit> {
        val keyword = HttpClient.encodeKeyword(name)
        val fetchSize = maxOf(limit * 2, 6)
        val url = "$SEARCH_URL?s=$keyword&type=100&offset=0&limit=$fetchSize"
        val root = JSONObject(http.getString(url, referer = REFERER))
        val artists = root.optJSONObject("result")?.optJSONArray("artists") ?: return emptyList()

        val hits = ArrayList<ArtistHit>()
        for (index in 0 until artists.length()) {
            val artist = artists.optJSONObject(index) ?: continue
            val candidateName = artist.optString("name")
            if (candidateName.isBlank()) continue
            val aliases = artist.optJSONArray("alias").toStringList()
            val picUrl = artist.optString("picUrl").ifBlank { artist.optString("img1v1Url") }
            if (picUrl.isBlank()) continue
            hits.add(
                ArtistHit(
                    name = candidateName,
                    aliases = aliases,
                    imageUrls = listOf("$picUrl?param=500y500", "$picUrl?param=300y300"),
                    platform = platform,
                ),
            )
        }
        return NameMatcher.orderByExact(name, hits, { it.name }, { it.aliases }).take(limit)
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { optString(it) }.filter { it.isNotBlank() }
    }
}
