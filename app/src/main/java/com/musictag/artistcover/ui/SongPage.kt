package com.musictag.artistcover.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.musictag.artistcover.R
import com.musictag.artistcover.data.NameMatcher
import com.musictag.artistcover.data.SavedFiles
import com.musictag.artistcover.data.downloadStateOf
import com.musictag.artistcover.model.ArtistGroup
import com.musictag.artistcover.model.DownloadState
import com.musictag.artistcover.model.Platform
import com.musictag.artistcover.model.SongItem
import com.musictag.artistcover.theme.FaintText
import com.musictag.artistcover.theme.MutedText
import kotlinx.coroutines.launch
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
    selectedSongIds: Set<String>,
    selectedArtistNames: Set<String>,
    selectionMode: Boolean,
    savedFiles: SavedFiles,
    platformOrder: List<Platform>,
    enabledPlatforms: Set<Platform>,
    outputFolderReady: Boolean,
    matching: Boolean,
    progressDone: Int,
    progressTotal: Int,
    songSort: SongSort,
    artistSort: ArtistSort,
    onSelectSongSort: (SongSort) -> Unit,
    onSelectArtistSort: (ArtistSort) -> Unit,
    onToggleSelectionMode: () -> Unit,
    onPickFolder: () -> Unit,
    onRescan: () -> Unit,
    onToggleSong: (String) -> Unit,
    onToggleArtist: (String) -> Unit,
    onSelectAllSongs: (List<SongItem>) -> Unit,
    onSelectAllArtists: (List<ArtistGroup>) -> Unit,
    onClearSelection: () -> Unit,
    onSingleMatch: (SongItem) -> Unit,
    onArtistMatch: (ArtistGroup) -> Unit,
    onTogglePlatform: (Platform) -> Unit,
    onBatchDownloadSongs: () -> Unit,
    onBatchDownloadArtists: () -> Unit,
    onTagFix: () -> Unit,
) {
    var view by remember { mutableStateOf(LibraryView.SONGS) }
    var searchActive by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var showTopButton by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 上滑（列表往前进）时浮出返回顶部按钮，下滑（往回滚）时收起；回到顶部一定收起。
    LaunchedEffect(listState) {
        var lastIndex = listState.firstVisibleItemIndex
        var lastOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                when {
                    index == 0 && offset == 0 -> showTopButton = false
                    index > lastIndex || (index == lastIndex && offset > lastOffset) -> showTopButton = true
                    index < lastIndex || (index == lastIndex && offset < lastOffset) -> showTopButton = false
                }
                lastIndex = index
                lastOffset = offset
            }
    }

    val keyword = NameMatcher.normalize(query)
    val visibleSongs = remember(songs, keyword, songSort, savedFiles) {
        val base = if (keyword.isEmpty()) {
            songs
        } else {
            songs.filter { song ->
                NameMatcher.normalize(song.displayTitle).contains(keyword) ||
                    song.artistList.any { NameMatcher.normalize(it).contains(keyword) }
            }
        }
        // addedAt 为 0（拿不到修改时间）的一律排到最后
        when (songSort) {
            SongSort.MISSING_FIRST -> base.sortedWith(
                compareBy<SongItem> { downloadStateOf(it.artistList, savedFiles) == DownloadState.DOWNLOADED }
                    .thenByDescending { it.addedAt },
            )

            SongSort.ADDED_DESC -> base.sortedWith(
                compareBy<SongItem> { it.addedAt == 0L }.thenByDescending { it.addedAt },
            )

            SongSort.ADDED_ASC -> base.sortedWith(
                compareBy<SongItem> { it.addedAt == 0L }.thenBy { it.addedAt },
            )
        }
    }
    val visibleArtists = remember(artistGroups, keyword, artistSort) {
        val base = if (keyword.isEmpty()) {
            artistGroups
        } else {
            artistGroups.filter { NameMatcher.normalize(it.name).contains(keyword) }
        }
        when (artistSort) {
            ArtistSort.MISSING_FIRST -> base.sortedWith(
                compareBy<ArtistGroup> { it.downloadState == DownloadState.DOWNLOADED }
                    .thenByDescending { it.songCount },
            )

            ArtistSort.COUNT_DESC -> base.sortedWith(
                compareByDescending<ArtistGroup> { it.songCount }.thenBy { it.name },
            )

            ArtistSort.COUNT_ASC -> base.sortedWith(
                compareBy<ArtistGroup> { it.songCount }.thenBy { it.name },
            )

            ArtistSort.RECENT_DESC -> base.sortedWith(
                compareBy<ArtistGroup> { it.latestAddedAt == 0L }.thenByDescending { it.latestAddedAt },
            )
        }
    }

    val artistMode = view == LibraryView.ARTISTS
    val activeSelectedCount = if (artistMode) selectedArtistNames.size else selectedSongIds.size

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(stringResource(R.string.songs_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                actions = {
                    IconButton(
                        onClick = {
                            searchActive = !searchActive
                            if (!searchActive) query = ""
                        },
                    ) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                    }
                    val hasContent = if (artistMode) artistGroups.isNotEmpty() else songs.isNotEmpty()
                    if (hasContent) {
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

            if (searchActive) {
                SearchField(
                    query = query,
                    hint = if (artistMode) {
                        stringResource(R.string.search_hint_artists)
                    } else {
                        stringResource(R.string.search_hint_songs)
                    },
                    onQueryChange = { query = it },
                    onClose = {
                        searchActive = false
                        query = ""
                    },
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    FolderCard(
                        folderName = folderName,
                        cachedAt = cachedAt,
                        isScanning = isScanning,
                        songCount = songs.size,
                        onPickFolder = onPickFolder,
                    )
                }

                if (songs.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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

                            val options = if (artistMode) {
                                ArtistSort.entries.map { it.id to stringResource(it.labelRes) }
                            } else {
                                SongSort.entries.map { it.id to stringResource(it.labelRes) }
                            }
                            SortMenu(
                                label = if (artistMode) {
                                    stringResource(artistSort.labelRes)
                                } else {
                                    stringResource(songSort.labelRes)
                                },
                                options = options,
                                selectedId = if (artistMode) artistSort.id else songSort.id,
                                onSelect = { id ->
                                    if (artistMode) {
                                        onSelectArtistSort(ArtistSort.from(id))
                                    } else {
                                        onSelectSongSort(SongSort.from(id))
                                    }
                                },
                            )
                        }
                    }
                }

                when {
                    songs.isEmpty() && !isScanning -> item {
                        ListEmptyState(
                            message = stringResource(R.string.empty_songs),
                            actionLabel = stringResource(R.string.pick_song_folder),
                            onAction = onPickFolder,
                        )
                    }

                    artistMode -> {
                        if (visibleArtists.isEmpty()) {
                            item { ListEmptyState(stringResource(R.string.no_search_result)) }
                        } else {
                            item {
                                SelectionBar(
                                    summary = when {
                                        selectionMode -> stringResource(
                                            R.string.selected_artists,
                                            selectedArtistNames.size,
                                            visibleArtists.size,
                                        )

                                        keyword.isNotEmpty() -> stringResource(R.string.found_artists, visibleArtists.size)
                                        else -> stringResource(R.string.artists_summary, visibleArtists.size)
                                    },
                                    selectionMode = selectionMode,
                                    onSelectAll = { onSelectAllArtists(visibleArtists) },
                                    onClearSelection = onClearSelection,
                                )
                            }
                            items(items = visibleArtists, key = { "artist-${it.name}" }) { group ->
                                ArtistRow(
                                    group = group,
                                    selectionMode = selectionMode,
                                    checked = group.name in selectedArtistNames,
                                    onClick = {
                                        if (selectionMode) onToggleArtist(group.name) else onArtistMatch(group)
                                    },
                                )
                            }
                        }
                    }

                    else -> {
                        if (visibleSongs.isEmpty()) {
                            item { ListEmptyState(stringResource(R.string.no_search_result)) }
                        } else {
                            item {
                                SelectionBar(
                                    summary = when {
                                        selectionMode -> stringResource(
                                            R.string.selected_summary,
                                            selectedSongIds.size,
                                            visibleSongs.size,
                                        )

                                        keyword.isNotEmpty() -> stringResource(R.string.found_songs, visibleSongs.size)
                                        else -> stringResource(R.string.tap_to_match_hint)
                                    },
                                    selectionMode = selectionMode,
                                    onSelectAll = { onSelectAllSongs(visibleSongs) },
                                    onClearSelection = onClearSelection,
                                )
                            }
                            items(items = visibleSongs, key = { it.id }) { song ->
                                SongRow(
                                    song = song,
                                    selectionMode = selectionMode,
                                    checked = song.id in selectedSongIds,
                                    downloadState = downloadStateOf(song.artistList, savedFiles),
                                    onClick = {
                                        if (selectionMode) onToggleSong(song.id) else onSingleMatch(song)
                                    },
                                )
                            }
                        }
                    }
                }
            }

            if (selectionMode) {
                BatchActionBar(
                    platformOrder = platformOrder,
                    enabledPlatforms = enabledPlatforms,
                    onTogglePlatform = onTogglePlatform,
                    selectedCount = activeSelectedCount,
                    countText = if (artistMode) {
                        stringResource(R.string.selected_artists_count, activeSelectedCount)
                    } else {
                        stringResource(R.string.selected_count, activeSelectedCount)
                    },
                    outputReady = outputFolderReady,
                    matching = matching,
                    progressDone = progressDone,
                    progressTotal = progressTotal,
                    onDownload = if (artistMode) onBatchDownloadArtists else onBatchDownloadSongs,
                    onTagFix = onTagFix,
                )
            }
        }

        if (showTopButton && !selectionMode) {
            SmallFloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(R.string.back_to_top),
                )
            }
        }
    }
}

/** 排序下拉菜单：按钮上直接显示当前排序方式。 */
@Composable
private fun SortMenu(
    label: String,
    options: List<Pair<String, String>>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) {
            Text(text = label, maxLines = 1)
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = stringResource(R.string.sort),
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (id, text) ->
                DropdownMenuItem(
                    text = { Text(text) },
                    onClick = {
                        expanded = false
                        onSelect(id)
                    },
                    trailingIcon = {
                        if (id == selectedId) {
                            Icon(Icons.Default.Check, contentDescription = null)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun SearchField(
    query: String,
    hint: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        placeholder = { Text(hint) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.search_close))
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.large,
    )
}

@Composable
private fun FolderCard(
    folderName: String?,
    cachedAt: Long?,
    isScanning: Boolean,
    songCount: Int,
    onPickFolder: () -> Unit,
) {
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
                    text = if (isScanning) stringResource(R.string.scanning) else "$songCount 个音频文件",
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

@Composable
private fun SelectionBar(
    summary: String,
    selectionMode: Boolean,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = summary,
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
    countText: String,
    outputReady: Boolean,
    matching: Boolean,
    progressDone: Int,
    progressTotal: Int,
    onDownload: () -> Unit,
    onTagFix: () -> Unit,
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

            Text(
                text = when {
                    selectedCount == 0 -> stringResource(R.string.selected_none)
                    !outputReady -> stringResource(R.string.output_needed_for_download)
                    enabledPlatforms.isEmpty() -> stringResource(R.string.no_platform_selected)
                    else -> countText
                },
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
            )

            // 左边补标签（不需要保存目录），右边下载
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onTagFix,
                    enabled = !matching && selectedCount > 0,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.tag_fix_button))
                }
                Button(
                    onClick = onDownload,
                    enabled = !matching && selectedCount > 0 && outputReady && enabledPlatforms.isNotEmpty(),
                    modifier = Modifier.weight(1f),
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

private fun formatTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
