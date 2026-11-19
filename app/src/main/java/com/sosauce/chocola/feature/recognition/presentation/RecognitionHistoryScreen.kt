package com.sosauce.chocola.feature.recognition.presentation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sosauce.chocola.R
import com.sosauce.chocola.feature.recognition.data.RecognitionHistoryEntry
import com.sosauce.chocola.feature.recognition.data.RecognitionHistoryStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun RecognitionHistoryRoute(
    viewModel: RecognitionViewModel,
    onNavigateBack: () -> Unit
) {
    val entries by viewModel.historyEntries.collectAsStateWithLifecycle()
    RecognitionHistoryScreen(
        entries = entries,
        onNavigateBack = onNavigateBack,
        onClear = viewModel::clearHistory
    )
}

/** Buckets for the list headers (mockup 10). Public for tests. */
enum class HistoryBucket { TODAY, YESTERDAY, EARLIER }

fun historyBucket(timestampMs: Long, today: LocalDate, zone: ZoneId): HistoryBucket {
    val date = Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate()
    return when {
        !date.isBefore(today) -> HistoryBucket.TODAY
        date == today.minusDays(1) -> HistoryBucket.YESTERDAY
        else -> HistoryBucket.EARLIER
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionHistoryScreen(
    entries: List<RecognitionHistoryEntry>,
    onNavigateBack: () -> Unit,
    onClear: () -> Unit
) {
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val grouped = remember(entries, today) {
        entries.groupBy { historyBucket(it.timestampMs, today, zone) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.recognition_history)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(painterResource(R.drawable.back), stringResource(R.string.recognition_back))
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(
                                painterResource(R.drawable.trash_rounded),
                                stringResource(R.string.recognition_history_clear)
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            if (entries.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.recognition_history_empty),
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            HistoryBucket.entries.forEach { bucket ->
                val list = grouped[bucket].orEmpty()
                if (list.isNotEmpty()) {
                    item(key = "header_${bucket.name}") {
                        SectionLabel(
                            stringResource(
                                when (bucket) {
                                    HistoryBucket.TODAY -> R.string.recognition_history_today
                                    HistoryBucket.YESTERDAY -> R.string.recognition_history_yesterday
                                    HistoryBucket.EARLIER -> R.string.recognition_history_earlier
                                }
                            )
                        )
                    }
                    item(key = "group_${bucket.name}") {
                        RecognitionCard {
                            list.forEachIndexed { index, entry ->
                                HistoryRow(
                                    entry = entry,
                                    bucket = bucket,
                                    zone = zone,
                                    showDivider = index < list.lastIndex
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.recognition_history_note),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.recognition_history_clear)) },
            text = { Text(stringResource(R.string.recognition_history_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClear()
                }) { Text(stringResource(R.string.recognition_history_clear_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) {
                    Text(stringResource(R.string.recognition_cancel))
                }
            }
        )
    }
}

@Composable
private fun HistoryRow(
    entry: RecognitionHistoryEntry,
    bucket: HistoryBucket,
    zone: ZoneId,
    showDivider: Boolean
) {
    val matched = entry.status == RecognitionHistoryStatus.MATCH
    val time = Instant.ofEpochMilli(entry.timestampMs).atZone(zone)
    val timeText = when (bucket) {
        HistoryBucket.TODAY -> time.format(TIME_FORMAT)
        HistoryBucket.YESTERDAY ->
            stringResource(R.string.recognition_history_yesterday_time, time.format(TIME_FORMAT))

        HistoryBucket.EARLIER -> time.format(DATE_FORMAT)
    }
    val title = when (entry.status) {
        RecognitionHistoryStatus.MATCH -> entry.title.orEmpty()
        RecognitionHistoryStatus.NO_MATCH -> stringResource(R.string.recognition_fail_nomatch_title)
        RecognitionHistoryStatus.NETWORK -> stringResource(R.string.recognition_history_network)
        RecognitionHistoryStatus.PLUGIN_ERROR -> stringResource(R.string.recognition_history_plugin_error)
    }
    val subtitle = listOfNotNull(entry.artist?.takeIf { matched && it.isNotBlank() }, entry.pluginName)
        .joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (matched) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(if (matched) R.drawable.music_note else R.drawable.search),
                contentDescription = null,
                tint = if (matched) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            timeText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
    if (showDivider) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d HH:mm")
