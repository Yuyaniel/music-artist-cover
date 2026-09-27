package com.musictag.artistcover.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musictag.artistcover.R
import com.musictag.artistcover.model.DownloadState
import com.musictag.artistcover.model.MatchState
import com.musictag.artistcover.theme.ErrorColor
import com.musictag.artistcover.theme.FaintText
import com.musictag.artistcover.theme.InfoColor
import com.musictag.artistcover.theme.MutedText
import com.musictag.artistcover.theme.SuccessColor
import com.musictag.artistcover.theme.WarningColor
import java.util.Locale

/** 圆角分组卡片，统一页面视觉。 */
@Composable
fun SectionCard(
    title: String? = null,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = FaintText,
                )
            }
            content()
        }
    }
}

/** 匹配状态小徽标。 */
@Composable
fun StateBadge(state: MatchState, modifier: Modifier = Modifier) {
    val (labelRes, color) = stateStyle(state)
    val label = stringResource(labelRes)
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
private fun stateStyle(state: MatchState): Pair<Int, Color> = when (state) {
    MatchState.IDLE -> R.string.state_idle to MutedText
    MatchState.SEARCHING -> R.string.state_searching to InfoColor
    MatchState.DONE -> R.string.state_done to SuccessColor
    MatchState.EXISTS -> R.string.state_exists to MaterialTheme.colorScheme.primary
    MatchState.PARTIAL -> R.string.state_partial to WarningColor
    MatchState.NOT_FOUND -> R.string.state_not_found to WarningColor
    MatchState.FAILED -> R.string.state_failed to ErrorColor
}

/** 歌曲库里的下载状态徽标：已下载 / 部分下载 / 未下载 / 无法判断。 */
@Composable
fun DownloadBadge(state: DownloadState, modifier: Modifier = Modifier) {
    val (labelRes, color) = when (state) {
        DownloadState.DOWNLOADED -> R.string.state_downloaded to SuccessColor
        DownloadState.PARTIAL -> R.string.state_partial_download to WarningColor
        DownloadState.MISSING -> R.string.state_missing to MutedText
        DownloadState.UNKNOWN -> R.string.state_unknown to FaintText
    }
    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** 平台徽标（纯文本，避免额外图标依赖）。 */
@Composable
fun PlatformBadge(name: String, modifier: Modifier = Modifier) {
    Text(
        text = name,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
fun KeyValueRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MutedText)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024L -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}
