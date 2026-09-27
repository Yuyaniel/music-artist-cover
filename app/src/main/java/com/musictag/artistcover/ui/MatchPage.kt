package com.musictag.artistcover.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musictag.artistcover.R
import com.musictag.artistcover.model.MatchState
import com.musictag.artistcover.model.Platform
import com.musictag.artistcover.model.SongItem
import com.musictag.artistcover.theme.FaintText
import com.musictag.artistcover.theme.MutedText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatchPage(
    platformOrder: List<Platform>,
    enabledPlatforms: Set<Platform>,
    onTogglePlatform: (Platform) -> Unit,
    onMovePlatform: (Platform, Int) -> Unit,
    outputFolderName: String?,
    onPickOutput: () -> Unit,
    overwrite: Boolean,
    onToggleOverwrite: (Boolean) -> Unit,
    results: List<SongItem>,
    appVersion: String,
    onRetry: (SongItem) -> Unit,
    onOpenRepo: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(stringResource(R.string.match_title)) },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SectionCard(
                    title = stringResource(R.string.section_platforms),
                    subtitle = stringResource(R.string.platforms_hint),
                ) {
                    platformOrder.forEachIndexed { index, platform ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "${index + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MutedText,
                                modifier = Modifier.width(18.dp),
                            )
                            Text(
                                text = platform.displayName,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { onMovePlatform(platform, -1) },
                                enabled = index > 0,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = stringResource(R.string.move_up),
                                )
                            }
                            IconButton(
                                onClick = { onMovePlatform(platform, 1) },
                                enabled = index < platformOrder.lastIndex,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = stringResource(R.string.move_down),
                                )
                            }
                            Switch(
                                checked = platform in enabledPlatforms,
                                onCheckedChange = { onTogglePlatform(platform) },
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(
                    title = stringResource(R.string.section_output),
                    subtitle = outputFolderName,
                    modifier = Modifier.clickable(onClick = onPickOutput),
                ) {
                    Text(
                        text = if (outputFolderName == null) stringResource(R.string.pick_output_folder)
                        else "点击可更换保存文件夹",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText,
                    )
                }
            }

            item {
                SectionCard(
                    title = stringResource(R.string.overwrite_existing),
                    subtitle = stringResource(R.string.overwrite_hint),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (overwrite) stringResource(R.string.will_overwrite)
                            else stringResource(R.string.will_skip),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedText,
                        )
                        Switch(
                            checked = overwrite,
                            onCheckedChange = onToggleOverwrite,
                        )
                    }
                }
            }

            if (results.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.section_results),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(items = results, key = { "result-${it.id}" }) { song ->
                    ResultRow(song = song, onRetry = { onRetry(song) }, enabled = true)
                }
            }

            item {
                SectionCard(
                    title = stringResource(R.string.section_about),
                    subtitle = stringResource(R.string.about_repo_hint),
                ) {
                    KeyValueRow(stringResource(R.string.about_app), stringResource(R.string.app_name))
                    KeyValueRow(stringResource(R.string.about_version), appVersion)
                    KeyValueRow(stringResource(R.string.about_author), "Yuyaniel")
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(onClick = onOpenRepo, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.open_repo))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(song: SongItem, onRetry: () -> Unit, enabled: Boolean) {
    SectionCard {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = song.displayTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(8.dp))
                StateBadge(song.matchState)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = song.matchedArtist ?: song.artist ?: stringResource(R.string.artist_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText,
                )
                song.platform?.let { platform ->
                    Spacer(modifier = Modifier.width(8.dp))
                    PlatformBadge(platform.displayName)
                }
            }

            song.savedName?.let { saved ->
                Text(text = saved, style = MaterialTheme.typography.bodySmall, color = FaintText)
            }

            song.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (song.matchState == MatchState.FAILED || song.matchState == MatchState.NOT_FOUND) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onRetry, enabled = enabled) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
        }
    }
}
