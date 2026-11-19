package com.sosauce.chocola.feature.recognition.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sosauce.chocola.R
import com.sosauce.chocola.feature.recognition.domain.RecognitionEntryPolicy
import com.sosauce.chocola.feature.recognition.domain.RecognitionPluginInfo

/** Top-bar mic button. Renders nothing unless a plugin declares recognition. */
@Composable
fun RecognitionEntryButton(
    plugins: List<RecognitionPluginInfo>,
    onStart: () -> Unit
) {
    if (!RecognitionEntryPolicy.isEntryVisible(plugins)) return
    IconButton(onClick = onStart) {
        Icon(painterResource(R.drawable.mic), stringResource(R.string.recognition_title))
    }
}

/** "听歌识曲" group (mockup 01). Renders nothing unless a plugin declares recognition. */
@Composable
fun RecognitionEntrySection(
    plugins: List<RecognitionPluginInfo>,
    onStart: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!RecognitionEntryPolicy.isEntryVisible(plugins)) return
    Column(modifier = modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.recognition_title))
        RecognitionCard {
            RecognitionRow(
                icon = R.drawable.mic,
                title = stringResource(R.string.recognition_start),
                subtitle = stringResource(R.string.recognition_entry_start_desc),
                showDivider = true,
                onClick = onStart
            )
            RecognitionRow(
                icon = R.drawable.time_filled,
                title = stringResource(R.string.recognition_history),
                subtitle = stringResource(R.string.recognition_entry_history_desc),
                onClick = onOpenHistory
            )
        }
        Text(
            text = stringResource(R.string.recognition_entry_note),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
