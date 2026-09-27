package com.musictag.artistcover.model

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap

/** 图片来源平台，顺序即匹配优先级。 */
enum class Platform(val id: String, val displayName: String) {
    NETEASE("netease", "网易云音乐"),
    QQ("qq", "QQ音乐"),
}

/** 歌手名的来源：内置标签 / 文件名解析 / 未识别。 */
enum class ArtistSourceKind { ID3, FILENAME, UNKNOWN }

/** 匹配过程的中间状态（用于匹配页的结果列表）。 */
enum class MatchState { IDLE, SEARCHING, DONE, EXISTS, PARTIAL, NOT_FOUND, FAILED }

/** 歌曲库里的下载状态：直接看保存文件夹里有没有对应歌手的图片。 */
enum class DownloadState { DOWNLOADED, PARTIAL, MISSING, UNKNOWN }

/** 一首本地歌曲。 */
data class SongItem(
    val id: String,
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    /** 文件最后修改时间，用于「添加时间」排序；0 表示未知。 */
    val addedAt: Long = 0L,
    val title: String? = null,
    /** 原始歌手字符串（如「郭静/韦礼安」），仅用于展示。 */
    val artist: String? = null,
    /** 拆分后的歌手列表（如 ["郭静", "韦礼安"]），匹配时逐个处理。 */
    val artists: List<String> = emptyList(),
    val artistSource: ArtistSourceKind = ArtistSourceKind.UNKNOWN,
    val matchState: MatchState = MatchState.IDLE,
    val matchedArtist: String? = null,
    val platform: Platform? = null,
    val savedName: String? = null,
    val error: String? = null,
) {
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: displayName.substringBeforeLast('.')

    /** 参与匹配的歌手；拆分失败时退回原始字符串。 */
    val artistList: List<String>
        get() = artists.ifEmpty { listOfNotNull(artist?.takeIf { it.isNotBlank() }) }
}

/** 平台搜索命中的歌手信息。 */
data class ArtistHit(
    val name: String,
    val aliases: List<String>,
    /** 候选图片地址，按顺序尝试下载。 */
    val imageUrls: List<String>,
    val platform: Platform,
)

/**
 * 平台返回的一个候选艺人。
 *
 * 故意不做成 data class：内部携带图片字节，按引用比较可以避免逐字节比较带来的开销。
 */
class ArtistCandidate(
    val hit: ArtistHit,
    /** 名称与目标歌手一致（已校准）。 */
    val exact: Boolean,
    val preview: ImageBitmap? = null,
    val bytes: ByteArray? = null,
)

/** 单次匹配中，一位歌手的候选列表。 */
data class ArtistMatch(
    val artist: String,
    val candidates: List<ArtistCandidate> = emptyList(),
    /** 当前选中的候选下标。 */
    val selected: Int = 0,
    val error: String? = null,
) {
    val selectedCandidate: ArtistCandidate? get() = candidates.getOrNull(selected)

    val hasImage: Boolean get() = selectedCandidate?.bytes != null

    /** 候选里是否存在已校准的名字，用于提示用户。 */
    val hasExact: Boolean get() = candidates.any { it.exact }
}

/** 保存文件夹里某位歌手已有的图片。 */
data class LocalArtistImage(
    val artist: String,
    /** 已有文件名；为 null 表示还没下载过。 */
    val fileName: String? = null,
    val preview: ImageBitmap? = null,
)

/** 歌曲库里按歌手聚合出的一行。 */
data class ArtistGroup(
    val name: String,
    val songCount: Int,
    /** 该歌手所有歌曲里最新的添加时间，用于「最近添加」排序。 */
    val latestAddedAt: Long = 0L,
    /** 已下载时的文件名。 */
    val savedFileName: String? = null,
    /** 已下载图片的本地 Uri，用于列表缩略图。 */
    val savedUri: Uri? = null,
) {
    val downloadState: DownloadState
        get() = if (savedFileName != null) DownloadState.DOWNLOADED else DownloadState.MISSING
}

/** 单次匹配的目标：可以是「一首歌」，也可以是「一位歌手」。 */
data class MatchTarget(
    val title: String,
    val subtitle: String,
    val artists: List<String>,
    /** 非空时，保存后回写这首歌的状态。 */
    val songId: String? = null,
    /** 该歌手参与的作品，用于歌手页里的「参与的作品」切换。 */
    val relatedSongs: List<SongItem> = emptyList(),
)
