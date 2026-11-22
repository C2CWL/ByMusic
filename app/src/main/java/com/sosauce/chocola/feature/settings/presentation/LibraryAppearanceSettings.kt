package com.sosauce.chocola.feature.settings.presentation

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import com.sosauce.chocola.R
import com.sosauce.chocola.core.presentation.preferences.rememberLibraryBackground
import com.sosauce.chocola.core.presentation.preferences.rememberLightweightGlass
import com.sosauce.chocola.feature.settings.presentation.components.SettingsClickable
import com.sosauce.chocola.feature.settings.presentation.components.SettingsSwitch

@Composable
fun LibraryAppearanceSettings() {
    val context = LocalContext.current
    var background by rememberLibraryBackground()
    var glass by rememberLightweightGlass()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val previous = background
                background = uri.toString()
                if (previous.isNotEmpty() && previous != uri.toString()) {
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(previous.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                }
            } catch (_: SecurityException) {
                Toast.makeText(context, R.string.library_background_error, Toast.LENGTH_SHORT).show()
            }
        }
    }
    SettingsClickable(
        title = stringResource(R.string.library_background),
        onClick = { picker.launch(arrayOf("image/*")) }
    )
    if (background.isNotEmpty()) {
        SettingsClickable(
            title = stringResource(R.string.library_background_reset),
            onClick = {
                val previous = background
                background = ""
                runCatching {
                    context.contentResolver.releasePersistableUriPermission(previous.toUri(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        )
    }
    SettingsSwitch(
        title = stringResource(R.string.lightweight_glass),
        description = stringResource(R.string.lightweight_glass_desc),
        checked = glass,
        onCheckedChange = { glass = it }
    )
}
