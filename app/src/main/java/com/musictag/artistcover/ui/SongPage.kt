package com.musictag.artistcover.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.musictag.artistcover.data.SavedFiles
import com.musictag.artistcover.data.downloadStateOf
import com.musictag.artistcover.model.ArtistGroup
import com.musictag.artistcover.model.ArtistSourceKind
import com.musictag.artistcover.model.Platform
import com.musictag.artistcover.model.SongItem
import com.musictag.artistcover.theme.FaintText
import com.musictag.artistcover.theme.MutedText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LibraryView { SONGS, ARTISTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongPage(
    folderName: String?,
    cachedAt: Long?,
    isScanning: Boolean,
    songs: List<SongItem>,
    artistGroups: List<ArtistGroup>,
    selectedIds: Set<String>,
    selectionMode: Boolean,
    savedFiles: SavedFiles,
    platformOrder: List<Platform>,
    enabledPlatforms: Set<Platform>,
    outputFolderReady: Boolean,
    matching: Boolean,
    progressDone: Int,
    progressTotal: Int,
    onToggleSelectionMode: () -> Unit,
    onPickFolder: () -> Unit,
    onRescan: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onSingleMatch: (SongItem) -> Unit,
    onArtistMatch: (ArtistGroup) -> Unit,
    onTogglePlatform: (Platform) -> Unit,
    onBatchDownload: () -> Unit,
) {
    var view by remember { mutableStateOf(LibraryView.SONGS) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.songs_title)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
            actions = {
                if (view == LibraryView.SONGS && songs.isNotEmpty()) {
                    TextButton(onClick = onToggleSelectionMode) {
                        Text(
                            if (selectionMode) stringResource(R.string.multi_select_done)
                            else stringResource(R.string.multi_select),
                        )
                    }
                }
                IconButton(onClick = onRescan, enabled = folderName != null && !isScanning) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.rescan))
                }
            },
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(
                    title = folderName ?: stringResource(R.string.pick_song_folder),
                    modifier = Modifier.clickable(onClick = onPickFolder),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text(
                                text = if (isScanning) stringResource(R.string.scanning)
                                else "${songs.size} 个音频文件",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedText,
                            )
                            if (cachedAt != null && !isScanning) {
                                Text(
                                    text = stringResource(R.string.scan_cached_at, formatTime(cachedAt)),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FaintText,
                                )
                            }
                        }
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            if (songs.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = view == LibraryView.SONGS,
                            onClick = { view = LibraryView.SONGS },
                            label = { Text(stringResource(R.string.view_songs)) },
                        )
                        FilterChip(
                            selected = view == LibraryView.ARTISTS,
                            onClick = { view = LibraryView.ARTISTS },
                            label = { Text(stringResource(R.string.view_artists)) },
                        )
                    }
                }
            }

            when {
                songs.isEmpty() && !isScanning -> item { EmptyState(onPickFolder) }

                view == LibraryView.ARTISTS -> {
                    item {
                        Text(
                            text = stringResource(R.string.artists_summary, artistGroups.size),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedText,
                        )
                    }
                    items(items = artistGroups, key = { "artist-${it.name}" }) { group ->
                        ArtistRow(group = group, onClick = { onArtistMatch(group) })
                    }
                }

                else -> {
                    item {
                        SelectionBar(
                            selectionMode = selectionMode,
                            selectedCount = selectedIds.size,
                            total = songs.size,
                            onSelectAll = onSelectAll,
                            onClearSelection = onClearSelection,
                        )
                    }
                    items(items = songs, key = { it.id }) { song ->
                        SongRow(
                            song = song,
                            selectionMode = selectionMode,
                            checked = song.id in selectedIds,
                            downloadState = downloadStateOf(song.artistList, savedFiles),
                            onClick = { if (selectionMode) onToggle(song.id) else onSingleMatch(song) },
                        )
                    }
                }
            }
        }

        if (selectionMode) {
            BatchActionBar(
                platformOrder = platformOrder,
                enabledPlatforms = enabledPlatforms,
                onTogglePlatform = onTogglePlatform,
                selectedCount = selectedIds.size,
                outputReady = outputFolderReady,
                matching = matching,
                progressDone = progressDone,
                progressTotal = progressTotal,
                onDownload = onBatchDownload,
            )
        }
    }
}

/**
 * 多选模式下才出现的底部操作条：选下载平台 + 批量下载。
 * 平台选择与「设置」页共用同一份状态，两边始终一致。
 */
@Composable
private fun BatchActionBar(
    platformOrder: List<Platform>,
    enabledPlatforms: Set<Platform>,
    onTogglePlatform: (Platform) -> Unit,
    selectedCount: Int,
    outputReady: Boolean,
    matching: Boolean,
    progressDone: Int,
    progressTotal: Int,
    onDownload: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.download_platform),
                style = MaterialTheme.typography.labelSmall,
                color = MutedText,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                platformOrder.forEach { platform ->
                    FilterChip(
                        selected = platform in enabledPlatforms,
                        onClick = { onTogglePlatform(platform) },
                        enabled = !matching,
                        label = { Text(platform.displayName) },
                    )
                }
            }

            if (progressTotal > 0) {
                LinearProgressIndicator(
                    progress = { progressDone.toFloat() / progressTotal.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.progress_text, progressDone, progressTotal),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        !outputReady -> stringResource(R.string.need_output)
                        enabledPlatforms.isEmpty() -> stringResource(R.string.no_platform_selected)
                        else -> stringResource(R.string.selected_count, selectedCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (outputReady) MutedText else MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onDownload,
                    enabled = !matching && selectedCount > 0 && outputReady &&
                        enabledPlatforms.isNotEmpty(),
                ) {
                    Text(
                        if (matching) stringResource(R.string.matching)
                        else stringResource(R.string.batch_download),
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionBar(
    selectionMode: Boolean,
    selectedCount: Int,
    total: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (selectionMode) stringResource(R.string.selected_summary, selectedCount, total)
            else stringResource(R.string.tap_to_match_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
            modifier = Modifier.weight(1f),
        )
        if (selectionMode) {
            Row {
                TextButton(onClick = onSelectAll) { Text(stringResource(R.string.select_all)) }
                TextButton(onClick = onClearSelection) { Text(stringResource(R.string.clear_selection)) }
            }
        }
    }
}

@Composable
private fun SongRow(
    song: SongItem,
    selectionMode: Boolean,
    checked: Boolean,
    downloadState: com.musictag.artistcover.model.DownloadState,
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

@Composable
private fun ArtistRow(group: ArtistGroup, onClick: () -> Unit) {
    SectionCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LocalArtistThumb(
                fileName = group.savedFileName,
                uri = group.savedUri,
                size = 48.dp,
            )
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

/** 保存文件夹里已下载的歌手图片缩略图。 */
@Composable
fun LocalArtistThumb(fileName: String?, uri: android.net.Uri?, size: Dp) {
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
private fun EmptyState(onPickFolder: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 64.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = stringResource(R.string.empty_songs),
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
            )
            Spacer(modifier = Modifier.padding(vertical = 6.dp))
            TextButton(onClick = onPickFolder) {
                Text(stringResource(R.string.pick_song_folder))
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
