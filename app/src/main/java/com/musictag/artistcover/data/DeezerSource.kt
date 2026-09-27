package com.musictag.artistcover.data

import com.musictag.artistcover.model.ArtistHit
import com.musictag.artistcover.model.Platform
import org.json.JSONObject

/**
 * Deezer。
 *
 * 正规公开 API：免费、无需 key、不需要 Referer，也没有国内平台那种风控。
 * 返回的 picture_xl 是 1000×1000 的艺术家大图，质量比缩图接口好。
 */
object DeezerSource : ArtistImageSource {

    override val platform: Platform = Platform.DEEZER

    private const val SEARCH_URL = "https://api.deezer.com/search/artist"

    override fun searchCandidates(name: String, http: HttpClient, limit: Int): List<ArtistHit> {
        val keyword = HttpClient.encodeKeyword(name)
        val fetchSize = maxOf(limit * 2, 6)
        val root = JSONObject(http.getString("$SEARCH_URL?q=$keyword&limit=$fetchSize"))
        val data = root.optJSONArray("data") ?: return emptyList()

        val hits = ArrayList<ArtistHit>()
        for (index in 0 until data.length()) {
            val artist = data.optJSONObject(index) ?: continue
            val candidateName = artist.optString("name")
            if (candidateName.isBlank()) continue

            val large = artist.optString("picture_xl").ifBlank { artist.optString("picture_big") }
            if (large.isBlank()) continue
            val medium = artist.optString("picture_big").ifBlank { large }

            hits.add(
                ArtistHit(
                    name = candidateName,
                    // Deezer 不返回别名
                    aliases = emptyList(),
                    imageUrls = listOf(large, medium),
                    platform = platform,
                ),
            )
        }
        return NameMatcher.orderByExact(name, hits, { it.name }, { it.aliases }).take(limit)
    }
}
