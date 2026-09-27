package com.musictag.artistcover.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musictag.artistcover.R
import com.musictag.artistcover.data.ArtworkCache
import com.musictag.artistcover.model.ArtistGroup
import com.musictag.artistcover.model.ArtistSourceKind
import com.musictag.artistcover.model.DownloadState
import com.musictag.artistcover.model.SongItem
import com.musictag.artistcover.theme.FaintText
import com.musictag.artistcover.theme.MutedText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 歌曲列表的一行。 */
@Composable
fun SongRow(
    song: SongItem,
    selectionMode: Boolean,
    checked: Boolean,
    downloadState: DownloadState,
    onClick: () -> Unit,
) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = checked, onCheckedChange = { onClick() })
                Spacer(modifier = Modifier.width(4.dp))
            }
            Artwork(song = song)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = artistSummary(song),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatSize(song.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = FaintText,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                DownloadBadge(downloadState)
                if (!selectionMode) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.single_match),
                        tint = FaintText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** 歌手列表的一行。 */
@Composable
fun ArtistRow(
    group: ArtistGroup,
    selectionMode: Boolean,
    checked: Boolean,
    onClick: () -> Unit,
) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                Checkbox(checked = checked, onCheckedChange = { onClick() })
                Spacer(modifier = Modifier.width(4.dp))
            }
            LocalArtistThumb(fileName = group.savedFileName, uri = group.savedUri, size = 48.dp)
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.artist_song_count, group.songCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText,
                )
                group.savedFileName?.let { name ->
                    Text(
                        text = name,
                        style = MaterialTheme.typography.bodySmall,
                        color = FaintText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                DownloadBadge(group.downloadState)
                if (!selectionMode) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = stringResource(R.string.single_match),
                        tint = FaintText,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/** 保存文件夹里已下载的歌手图片缩略图。 */
@Composable
fun LocalArtistThumb(fileName: String?, uri: Uri?, size: Dp) {
    val context = LocalContext.current
    val cache = ArtworkCache.localArtist

    LaunchedEffect(fileName, uri) {
        if (fileName != null && uri != null && !cache.containsKey(fileName)) {
            cache[fileName] = withContext(Dispatchers.IO) {
                ArtworkCache.loadFromUri(context, uri, 96)
            }
        }
    }

    val bitmap = fileName?.let { cache[it] }
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_music_note),
                contentDescription = null,
                tint = FaintText,
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}

/** 歌曲内嵌专辑封面，取不到时显示音符占位。 */
@Composable
fun Artwork(song: SongItem, size: Dp = 48.dp, shape: Dp = 10.dp) {
    val context = LocalContext.current
    val cache = ArtworkCache.embedded

    LaunchedEffect(song.id) {
        if (!cache.containsKey(song.id)) {
            val loaded = withContext(Dispatchers.IO) {
                ArtworkCache.loadEmbedded(context, song.uri)
            }
            cache[song.id] = loaded
        }
    }

    val bitmap = cache[song.id]
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(shape))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = stringResource(R.string.album_art),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_music_note),
                contentDescription = null,
                tint = FaintText,
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}

@Composable
private fun artistSummary(song: SongItem): String {
    val artists = song.artistList
    val name = if (artists.isEmpty()) stringResource(R.string.artist_unknown) else artists.joinToString(" / ")
    val source = when (song.artistSource) {
        ArtistSourceKind.ID3 -> stringResource(R.string.artist_from_tag)
        ArtistSourceKind.FILENAME -> stringResource(R.string.artist_from_name)
        ArtistSourceKind.UNKNOWN -> stringResource(R.string.artist_unknown)
    }
    return if (artists.size > 1) {
        "$name · ${stringResource(R.string.artist_count, artists.size)} · $source"
    } else {
        "$name · $source"
    }
}

/** 列表空态：居中一行提示 + 可选的行动按钮。 */
@Composable
fun ListEmptyState(message: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
            )
            if (actionLabel != null && onAction != null) {
                Spacer(modifier = Modifier.padding(vertical = 6.dp))
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        }
    }
}
