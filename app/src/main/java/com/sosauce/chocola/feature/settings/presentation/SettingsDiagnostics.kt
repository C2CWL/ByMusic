@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.sosauce.chocola.feature.settings.presentation

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sosauce.chocola.R
import com.sosauce.chocola.core.data.diagnostics.DiagShare
import com.sosauce.nekobites.components.settings.SettingsClickable
import com.sosauce.nekobites.components.settings.SettingsGroup

@Composable
fun ColumnScope.SettingsDiagnostics() {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.diag_logs_help),
        style = MaterialTheme.typography.bodyMediumEmphasized.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
    SettingsGroup {
        SettingsClickable(
            title = stringResource(R.string.export_diag_logs),
            description = stringResource(R.string.export_diag_logs_desc),
            icon = R.drawable.share_filled,
            onClick = { DiagShare.share(context) }
        )
    }
}
