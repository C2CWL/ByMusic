@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.sosauce.chocola.feature.settings.presentation

import android.widget.Toast
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sosauce.chocola.R
import com.sosauce.chocola.core.data.diagnostics.DiagBootstrap
import com.sosauce.chocola.core.data.diagnostics.DiagLogger
import com.sosauce.chocola.core.data.diagnostics.DiagShare
import com.sosauce.chocola.core.presentation.preferences.rememberDiagLoggingEnabled
import com.sosauce.chocola.feature.settings.presentation.components.SettingsClickable
import com.sosauce.nekobites.components.settings.SettingsGroup
import com.sosauce.chocola.feature.settings.presentation.components.SettingsSwitch

@Composable
fun ColumnScope.SettingsDiagnostics() {
    val context = LocalContext.current
    var loggingEnabled by rememberDiagLoggingEnabled()
    LaunchedEffect(loggingEnabled) {
        if (loggingEnabled) {
            DiagBootstrap.ensureReady(context)
        }
    }
    Text(
        text = stringResource(R.string.diag_logs_help),
        style = MaterialTheme.typography.bodyMediumEmphasized.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
    )
    SettingsGroup {
        SettingsSwitch(
            title = stringResource(R.string.diag_logging),
            description = stringResource(R.string.diag_logging_desc),
            checked = loggingEnabled,
            onCheckedChange = { loggingEnabled = !loggingEnabled }
        )
        SettingsClickable(
            title = stringResource(R.string.export_diag_logs),
            description = stringResource(R.string.export_diag_logs_desc),
            icon = R.drawable.share_filled,
            onClick = { DiagShare.share(context) }
        )
        SettingsClickable(
            title = stringResource(R.string.diag_clear_logs),
            description = stringResource(R.string.diag_clear_logs_desc),
            icon = R.drawable.trash_rounded,
            onClick = {
                DiagLogger.clearLogs()
                Toast.makeText(
                    context,
                    context.getString(R.string.diag_logs_cleared),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}
