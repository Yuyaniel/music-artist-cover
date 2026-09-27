package com.musictag.artistcover.ui

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.musictag.artistcover.R
import com.musictag.artistcover.data.SaveOutcome
import com.musictag.artistcover.data.SavedFiles
import com.musictag.artistcover.data.SingleMatchEngine
import com.musictag.artistcover.model.ArtistMatch
import com.musictag.artistcover.model.LocalArtistImage
import com.musictag.artistcover.model.MatchTarget
import com.musictag.artistcover.model.Platform
import com.musictag.artistcover.model.SongItem
import com.musictag.artistcover.theme.ErrorColor
import com.musictag.artistcover.theme.FaintText
import com.musictag.artistcover.theme.MutedText
import com.musictag.artistcover.theme.SuccessColor
import kotlinx.coroutines.launch

private val LOCAL_ART_BOX = 148.dp
private val RESULT_BIG = 172.dp
private val RESULT_THUMB = 88.dp

/**
 * 匹配面板（歌曲 / 歌手共用）。
 *
 * 主体是目标歌手在保存文件夹里**已经下载过的图片**（判断下过没有、下的是什么），
 * 下面是所选平台返回的候选大图，由用户挑选后决定是否保存。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleMatchSheet(
    target: MatchTarget,
    outputTreeUri: Uri?,
    savedFiles: SavedFiles,
    overwrite: Boolean,
    onSaved: (MatchTarget, SaveOutcome) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val engine = remember { SingleMatchEngine(context) }

    var platform by remember(target) { mutableStateOf(Platform.NETEASE) }
    var searching by remember(target) { mutableStateOf(false) }
    var saving by remember(target) { mutableStateOf(false) }
    var matches by remember(target) { mutableStateOf<List<ArtistMatch>>(emptyList()) }
    var locals by remember(target) { mutableStateOf<List<LocalArtistImage>>(emptyList()) }
    var readingLocal by remember(target) { mutableStateOf(false) }
    // 歌手视图里可切换到「该歌手参与的作品」
    var showSongs by remember(target) { mutableStateOf(false) }

    fun runSearch() {
        scope.launch {
            searching = true
            matches = engine.search(target.artists, platform, target.title)
            searching = false
        }
    }

    fun runSave() {
        val treeUri = outputTreeUri ?: return
        scope.launch {
            saving = true
            val outcome = engine.save(treeUri, matches, overwrite)
            saving = false
            onSaved(target, outcome)
            onDismiss()
        }
    }

    fun selectCandidate(artist: String, index: Int) {
        matches = matches.map { if (it.artist == artist) it.copy(selected = index) else it }
    }

    // 读取保存文件夹里已有的图片（索引已由外层一次查好，这里只解码缩略图）
    LaunchedEffect(target, savedFiles) {
        readingLocal = true
        locals = engine.loadLocalImages(target.artists, savedFiles)
        readingLocal = false
    }

    // 打开面板或切换平台后自动搜索
    LaunchedEffect(target, platform) { runSearch() }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            TargetHeader(target)

            if (target.relatedSongs.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !showSongs,
                        onClick = { showSongs = false },
                        label = { Text(stringResource(R.string.platform_candidates)) },
                    )
                    FilterChip(
                        selected = showSongs,
                        onClick = { showSongs = true },
                        label = { Text(stringResource(R.string.works_of_artist, target.relatedSongs.size)) },
                    )
                }
            }

            if (showSongs) {
                SectionCard(
                    title = stringResource(R.string.works_title),
                    subtitle = stringResource(R.string.artist_song_count, target.relatedSongs.size),
                ) {
                    target.relatedSongs.forEach { song -> WorkRow(song) }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
                }
            } else {

            SectionCard(
                title = stringResource(R.string.local_artwork),
                subtitle = stringResource(R.string.local_artwork_from_folder),
            ) {
                LocalImages(
                    artists = target.artists,
                    locals = locals,
                    reading = readingLocal,
                    outputReady = outputTreeUri != null,
                )
            }

            SectionCard(title = stringResource(R.string.section_platforms)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Platform.entries.forEach { item ->
                        Box(modifier = Modifier.weight(1f)) {
                            FilterChip(
                                selected = platform == item,
                                onClick = { if (platform != item) platform = item },
                                label = {
                                    Text(
                                        text = item.displayName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { runSearch() }, enabled = !searching && !saving) {
                    Text(stringResource(R.string.search_again))
                }
                Button(
                    onClick = { runSave() },
                    enabled = !saving && !searching && outputTreeUri != null && matches.any { it.hasImage },
                ) {
                    Text(if (saving) stringResource(R.string.saving) else stringResource(R.string.save_images))
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
            }

            if (searching) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.searching),
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText,
                    )
                }
            }

            if (matches.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.platform_results),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            matches.forEach { match ->
                ArtistMatchCard(
                    match = match,
                    onSelect = { index -> selectCandidate(match.artist, index) },
                )
            }
            }
        }
    }
}

@Composable
private fun WorkRow(song: SongItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(song = song, size = 40.dp, shape = 8.dp)
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.displayTitle,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatSize(song.sizeBytes),
                style = MaterialTheme.typography.labelSmall,
                color = FaintText,
            )
        }
    }
}

@Composable
private fun TargetHeader(target: MatchTarget) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = target.title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = target.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MutedText,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 本地已保存的艺术图：逐位歌手展示，未下载过的明确标注。 */
@Composable
private fun LocalImages(
    artists: List<String>,
    locals: List<LocalArtistImage>,
    reading: Boolean,
    outputReady: Boolean,
) {
    when {
        artists.isEmpty() -> Text(
            text = stringResource(R.string.no_artist_in_song),
            style = MaterialTheme.typography.bodySmall,
            color = MutedText,
        )

        !outputReady -> Text(
            text = stringResource(R.string.output_not_set_hint),
            style = MaterialTheme.typography.bodySmall,
            color = ErrorColor,
        )

        reading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.reading_local),
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
            )
        }

        else -> Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            artists.forEach { artist ->
                val local = locals.firstOrNull { it.artist == artist }
                val downloaded = local?.fileName != null
                Column(
                    modifier = Modifier.width(LOCAL_ART_BOX),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PreviewThumb(
                        bitmap = local?.preview,
                        size = LOCAL_ART_BOX,
                        fallbackLabel = null,
                        highlighted = downloaded,
                    )
                    Text(
                        text = artist,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = local?.fileName ?: stringResource(R.string.not_downloaded),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (downloaded) SuccessColor else FaintText,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistMatchCard(match: ArtistMatch, onSelect: (Int) -> Unit) {
    val selected = match.selectedCandidate

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = match.artist, style = MaterialTheme.typography.titleMedium)
            if (match.hasExact) {
                Text(
                    text = stringResource(R.string.verified),
                    style = MaterialTheme.typography.labelSmall,
                    color = SuccessColor,
                )
            } else if (match.candidates.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.not_calibrated),
                    style = MaterialTheme.typography.labelSmall,
                    color = ErrorColor,
                )
            }
        }

        PreviewThumb(
            bitmap = selected?.preview,
            size = RESULT_BIG,
            fallbackLabel = null,
            highlighted = selected != null,
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = selected?.hit?.name ?: stringResource(R.string.artist_unknown),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                selected?.hit?.aliases?.takeIf { it.isNotEmpty() }?.let { aliases ->
                    Text(
                        text = aliases.joinToString(" / "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                match.error?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = ErrorColor,
                    )
                }
            }
            selected?.hit?.let {
                PlatformBadge(stringResource(R.string.source_platform, it.platform.displayName))
            }
        }

        if (match.candidates.size > 1) {
            Text(
                text = stringResource(R.string.candidate_others),
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                match.candidates.forEachIndexed { index, candidate ->
                    Box(modifier = Modifier.clickable { onSelect(index) }) {
                        PreviewThumb(
                            bitmap = candidate.preview,
                            size = RESULT_THUMB,
                            fallbackLabel = null,
                            highlighted = index == match.selected,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewThumb(bitmap: ImageBitmap?, size: Dp, fallbackLabel: String?, highlighted: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    val borderColor = if (highlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(if (highlighted) 2.dp else 1.dp, borderColor, shape),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (fallbackLabel != null) {
            Text(text = fallbackLabel, style = MaterialTheme.typography.labelSmall, color = MutedText)
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
