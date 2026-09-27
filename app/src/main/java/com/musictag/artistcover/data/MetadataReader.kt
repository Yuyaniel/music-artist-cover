package com.musictag.artistcover.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.musictag.artistcover.model.ArtistSourceKind

data class SongMeta(
    val title: String?,
    val artist: String?,
    val artists: List<String>,
    val kind: ArtistSourceKind,
)

/**
 * 读取歌曲元信息：优先内置标签，标签缺失时从「艺术家 - 歌名」式文件名解析。
 * 歌手字段会按分隔符拆分，例如「郭静/韦礼安」视为两位歌手。
 */
object MetadataReader {

    /** 分隔符按优先级排列，带空格的长分隔符优先，避免误切分含连字符的歌手名。 */
    private val SEPARATORS = listOf(" - ", " – ", " — ", " —", " –", "- ", " -", "–", "—", "_", "|")

    private val TRACK_PREFIX = Regex("^\\s*(\\d{1,3})\\s*[.\\-_)]\\s*")

    /** 多歌手分隔符：斜杠、顿号、分号、竖线、乘号、间隔号、连接符。 */
    private val ARTIST_SPLITTER = Regex("\\s*[/、;；|×＆&·•]\\s*")

    /** feat. / ft. / with 这类合作署名。 */
    private val FEAT_SPLITTER = Regex("\\s+(?:feat\\.?|ft\\.?|with)\\s+", RegexOption.IGNORE_CASE)

    /** 单首歌最多处理的歌手数量，避免个别脏标签导致请求爆炸。 */
    private const val MAX_ARTISTS = 6

    fun read(context: Context, uri: Uri, displayName: String): SongMeta {
        val baseName = displayName.substringBeforeLast('.')
        val tags = readTags(context, uri)

        val resolvedTitle = tags.title?.takeIf { it.isNotBlank() }
        val effective = tags.artist?.takeIf { it.isNotBlank() }

        if (effective != null) {
            return SongMeta(
                title = resolvedTitle ?: baseName,
                artist = effective,
                artists = splitArtists(effective),
                kind = ArtistSourceKind.ID3,
            )
        }

        val guess = parseFromFileName(baseName)
        if (guess != null) {
            return SongMeta(
                title = resolvedTitle ?: guess.second,
                artist = guess.first,
                artists = splitArtists(guess.first),
                kind = ArtistSourceKind.FILENAME,
            )
        }

        return SongMeta(
            title = resolvedTitle ?: baseName,
            artist = null,
            artists = emptyList(),
            kind = ArtistSourceKind.UNKNOWN,
        )
    }

    /** 成对的包裹符号，例如「（周杰伦）」。 */
    private val BRACKET_PAIRS = listOf(
        '（' to '）',
        '(' to ')',
        '「' to '」',
        '《' to '》',
        '【' to '】',
        '"' to '"',
        '\'' to '\'',
    )

    /** 把「郭静/韦礼安」这类字符串拆成独立的歌手名，去重并保持顺序。 */
    fun splitArtists(raw: String): List<String> {
        val parts = raw
            .split(FEAT_SPLITTER)
            .flatMap { it.split(ARTIST_SPLITTER) }
            .map { stripWrappingBrackets(it) }
            .filter { it.isNotBlank() }

        val seen = LinkedHashSet<String>()
        for (part in parts) {
            if (part.length > 40) continue
            val key = NameMatcher.normalize(part)
            if (key.isEmpty()) continue
            if (seen.none { NameMatcher.normalize(it) == key }) seen.add(part)
            if (seen.size >= MAX_ARTISTS) break
        }
        // 拆分没有意义时退回原串（例如名字里本来带斜杠的作品名被误当作歌手）
        return if (seen.isEmpty()) listOfNotNull(raw.trim().takeIf { it.isNotBlank() }) else seen.toList()
    }

    /**
     * 只剥掉**成对包裹**在首尾的括号。
     *
     * 这里不能用 `trim('(', ')')`：那个 API 会把两端的任意一个字符都削掉，
     * 于是「xxx(xx)」会被截成「xxx(xx」，存出来的文件名也就成了「xxx(xx.jpg」。
     */
    private fun stripWrappingBrackets(text: String): String {
        var value = text.trim()
        var changed = true
        while (changed && value.length >= 2) {
            changed = false
            for ((open, close) in BRACKET_PAIRS) {
                if (value.first() == open && value.last() == close) {
                    value = value.substring(1, value.length - 1).trim()
                    changed = true
                }
            }
        }
        return value
    }

    private class Tags(val title: String?, val artist: String?)

    private fun readTags(context: Context, uri: Uri): Tags {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            Tags(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.trim(),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.trim(),
            )
        } catch (_: Throwable) {
            Tags(null, null)
        } finally {
            runCatching { retriever.release() }
        }
    }

    /** 从文件名解析，返回 (artist, title)；解析不出返回 null。 */
    private fun parseFromFileName(rawName: String): Pair<String, String>? {
        val name = TRACK_PREFIX.replace(rawName, "")
        for (separator in SEPARATORS) {
            val index = name.indexOf(separator)
            if (index <= 0) continue
            val left = name.substring(0, index).trim()
            val right = name.substring(index + separator.length).trim()
            if (left.isEmpty() || right.isEmpty()) continue
            if (left.any { it.isLetter() }) return left to right
        }
        return null
    }
}
