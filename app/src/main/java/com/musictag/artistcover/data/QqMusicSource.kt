package com.musictag.artistcover.data

import com.musictag.artistcover.model.ArtistHit
import com.musictag.artistcover.model.Platform
import org.json.JSONObject

/**
 * QQ 音乐。
 *
 * 必须携带 Referer: https://y.qq.com/，否则接口会拒绝。
 * 搜索结果里拿到歌手的 mid，再拼公开 CDN 头像地址。
 */
object QqMusicSource : ArtistImageSource {

    override val platform: Platform = Platform.QQ

    private const val SEARCH_URL = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp"
    private const val REFERER = "https://y.qq.com/"
    private const val AVATAR_500 = "https://y.gtimg.cn/music/photo_new/T001R500x500M000%s.jpg"
    private const val AVATAR_300 = "https://y.gtimg.cn/music/photo_new/T001R300x300M000%s.jpg"

    override fun searchCandidates(name: String, http: HttpClient, limit: Int): List<ArtistHit> {
        val keyword = HttpClient.encodeKeyword(name)
        val url = "$SEARCH_URL?w=$keyword&format=json&p=1&n=10&t=0"
        val root = JSONObject(http.getString(url, referer = REFERER))
        val songs = root.optJSONObject("data")
            ?.optJSONObject("song")
            ?.optJSONArray("list")
            ?: return emptyList()

        val seenMids = HashSet<String>()
        val hits = ArrayList<ArtistHit>()
        val collectLimit = limit * 3

        for (index in 0 until songs.length()) {
            val singers = songs.optJSONObject(index)?.optJSONArray("singer") ?: continue
            for (singerIndex in 0 until singers.length()) {
                val singer = singers.optJSONObject(singerIndex) ?: continue
                val mid = singer.optString("mid")
                val singerName = singer.optString("name")
                if (mid.isBlank() || singerName.isBlank() || !seenMids.add(mid)) continue
                hits.add(
                    ArtistHit(
                        name = singerName,
                        aliases = emptyList(),
                        imageUrls = listOf(AVATAR_500.format(mid), AVATAR_300.format(mid)),
                        platform = platform,
                    ),
                )
                if (hits.size >= collectLimit) break
            }
            if (hits.size >= collectLimit) break
        }
        return NameMatcher.orderByExact(name, hits, { it.name }, { it.aliases }).take(limit)
    }
}
