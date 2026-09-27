package com.musictag.artistcover.data

import com.musictag.artistcover.model.ArtistHit
import com.musictag.artistcover.model.Platform

/** 各平台统一抽象。 */
interface ArtistImageSource {
    val platform: Platform

    /**
     * 搜索候选艺人，已校准（名称一致）的排在前面，最多返回 limit 个。
     * 网络异常直接抛出，由上层决定降级或标记失败。
     */
    fun searchCandidates(name: String, http: HttpClient, limit: Int = DEFAULT_CANDIDATES): List<ArtistHit>

    companion object {
        const val DEFAULT_CANDIDATES = 4
    }
}

/** 批量匹配用：只取名称能对上的那一个。 */
fun ArtistImageSource.searchVerified(name: String, http: HttpClient): ArtistHit? =
    searchCandidates(name, http).firstOrNull { NameMatcher.matches(name, it.name, it.aliases) }

/** 歌手名归一化与比对，用于「校准」平台返回的搜索结果。 */
object NameMatcher {

    /** 全角转半角、去掉所有非字母数字字符、统一小写。 */
    fun normalize(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            val c = when {
                ch == '\u3000' -> ' '
                ch.code in 0xFF01..0xFF5E -> (ch.code - 0xFEE0).toChar()
                else -> ch
            }
            if (c.isLetterOrDigit()) sb.append(c.lowercaseChar())
        }
        return sb.toString()
    }

    fun matches(target: String, candidate: String, aliases: List<String>): Boolean {
        val key = normalize(target)
        if (key.isEmpty()) return false
        if (normalize(candidate) == key) return true
        return aliases.any { normalize(it) == key }
    }

    /** 精确匹配的排在前面，其余保持平台原始相关度顺序。 */
    fun <T> orderByExact(target: String, items: List<T>, nameOf: (T) -> String, aliasOf: (T) -> List<String>): List<T> =
        items.sortedByDescending { matches(target, nameOf(it), aliasOf(it)) }
}

/** 平台注册表：按用户勾选顺序返回可用的数据源。 */
object SourceRegistry {

    private val all: Map<Platform, ArtistImageSource> = mapOf(
        Platform.NETEASE to NeteaseSource,
        Platform.QQ to QqMusicSource,
    )

    fun ordered(platforms: Collection<Platform>): List<ArtistImageSource> =
        Platform.entries.filter { it in platforms }.mapNotNull { all[it] }

    fun sourceOf(platform: Platform): ArtistImageSource? = all[platform]
}
