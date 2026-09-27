package com.musictag.artistcover.data

import com.musictag.artistcover.model.ArtistHit
import com.musictag.artistcover.model.Platform
import org.json.JSONArray
import org.json.JSONObject

/**
 * 网易云音乐。
 *
 * 搜索接口：/api/search/get?type=100 直接返回歌手及其图片，无需二次请求。
 * 原图动辄 1MB 以上，统一追加 CDN 缩图参数。
 *
 * 取图务必**头像优先**：接口同时给出 picUrl 与 img1v1Url，
 * - `img1v1Url` 是 1:1 的**歌手头像**（img1v1 = image 1v1），头像位专用；
 * - `picUrl` 是歌手主页的**主图/横幅**，宽度不固定（如周杰伦的是 1050×800 的横图，
 *   人物只占一角、大半是背景）。按正方形显示或保存时会被裁得很难看，不是用户要的头像。
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
            val avatar = artist.optString("img1v1Url").takeIf { it.isNotBlank() }
            val banner = artist.optString("picUrl").takeIf { it.isNotBlank() }
            val primary = avatar ?: banner ?: continue
            // 头像缺失时才退回横幅
            val secondary = banner?.takeIf { it != primary }
            hits.add(
                ArtistHit(
                    name = candidateName,
                    aliases = aliases,
                    imageUrls = buildList {
                        add("$primary?param=500y500")
                        add("$primary?param=300y300")
                        secondary?.let { add("$it?param=500y500") }
                    },
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
