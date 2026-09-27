package com.musictag.artistcover.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musictag.artistcover.R
import com.musictag.artistcover.data.TagFixCandidate
import com.musictag.artistcover.data.TagFixOutcome
import com.musictag.artistcover.data.TagWriteResult
import com.musictag.artistcover.theme.FaintText
import com.musictag.artistcover.theme.MutedText
import com.musictag.artistcover.theme.SuccessColor
import com.musictag.artistcover.theme.WarningColor

/**
 * 补全专辑艺术家：先是确认（因为要动音乐文件），写入中显示进度，结束后给出逐条结果。
 */
@Composable
fun TagFixDialog(
    candidates: List<TagFixCandidate>,
    outcomes: List<TagFixOutcome>,
    writing: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!writing) onDismiss() },
        title = { Text(stringResource(R.string.tag_fix_dialog_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when {
                    writing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        Text(
                            text = stringResource(R.string.tag_fix_writing),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(start = 10.dp),
                        )
                    }

                    outcomes.isNotEmpty() -> {
                        val ok = outcomes.count { it.result is TagWriteResult.Written }
                        Text(
                            text = stringResource(R.string.tag_fix_done, ok, outcomes.size),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (ok == outcomes.size) SuccessColor else WarningColor,
                        )
                        outcomes.filter { it.result !is TagWriteResult.Written }.take(20).forEach { outcome ->
                            Column {
                                Text(
                                    text = outcome.song.displayTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = when (val result = outcome.result) {
                                        is TagWriteResult.Skipped -> result.reason
                                        is TagWriteResult.Failed -> "失败：${result.reason}"
                                        is TagWriteResult.Written -> ""
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = FaintText,
                                )
                            }
                        }
                    }

                    candidates.isEmpty() -> Text(
                        text = stringResource(R.string.tag_fix_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedText,
                    )

                    else -> {
                        Text(
                            text = stringResource(R.string.tag_fix_dialog_body, candidates.size),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = stringResource(R.string.tag_fix_dialog_warn),
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedText,
                        )
                        candidates.take(5).forEach { candidate ->
                            Text(
                                text = stringResource(
                                    R.string.tag_fix_sample,
                                    candidate.song.displayTitle,
                                    candidate.target,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = FaintText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        if (candidates.size > 5) {
                            Text(
                                text = stringResource(R.string.tag_fix_more, candidates.size - 5),
                                style = MaterialTheme.typography.labelSmall,
                                color = FaintText,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                writing -> Unit

                outcomes.isNotEmpty() -> TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.tag_fix_close))
                }

                candidates.isEmpty() -> TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.tag_fix_close))
                }

                else -> TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.tag_fix_confirm))
                }
            }
        },
        dismissButton = {
            if (!writing && outcomes.isEmpty() && candidates.isNotEmpty()) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
