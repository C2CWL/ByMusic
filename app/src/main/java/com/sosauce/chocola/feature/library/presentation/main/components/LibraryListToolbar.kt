package com.sosauce.chocola.feature.library.presentation.main.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sosauce.chocola.R
import com.sosauce.chocola.core.presentation.components.CuteSearchbarDefaults

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryListToolbar(
    trackCount: Int,
    onShuffle: () -> Unit,
    onQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sortExpanded by remember { mutableStateOf(false) }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onShuffle) {
            Icon(
                painter = painterResource(R.drawable.shuffle),
                contentDescription = stringResource(R.string.shuffle),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = trackCount.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.weight(1f))
        androidx.compose.foundation.layout.Box {
            IconButton(onClick = { sortExpanded = true }) {
                Icon(
                    painter = painterResource(R.drawable.shapes),
                    contentDescription = stringResource(R.string.library_sort),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DropdownMenuPopup(
                expanded = sortExpanded,
                onDismissRequest = { sortExpanded = false },
            ) {
                CuteSearchbarDefaults.TrackSortPopupContent()
            }
        }
        IconButton(onClick = onQueue) {
            Icon(
                painter = painterResource(R.drawable.queue),
                contentDescription = stringResource(R.string.queue),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
