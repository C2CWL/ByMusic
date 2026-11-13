package com.sosauce.chocola.feature.settings.presentation

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sosauce.chocola.R
import com.sosauce.nekobites.components.settings.SettingsClickable

private data class LauncherIcon(val alias: String, val label: String, val image: Int)
private val launcherIcons = listOf(
    LauncherIcon("Icon01MinimalDark", "01 · 深色", R.drawable.bymusic_01_minimal_dark),
    LauncherIcon("Icon01MinimalLight", "01 · 浅色", R.drawable.bymusic_01_minimal_light),
    LauncherIcon("Icon02GradientDark", "02 · 深色", R.drawable.bymusic_02_gradient_dark),
    LauncherIcon("Icon02GradientLight", "02 · 浅色", R.drawable.bymusic_02_gradient_light),
    LauncherIcon("Icon03GlassDark", "03 · 深色", R.drawable.bymusic_03_glass_dark),
    LauncherIcon("Icon03GlassLight", "03 · 浅色", R.drawable.bymusic_03_glass_light),
    LauncherIcon("Icon04GeometricDark", "04 · 深色", R.drawable.bymusic_04_geometric_dark),
    LauncherIcon("Icon04GeometricLight", "04 · 浅色", R.drawable.bymusic_04_geometric_light),
    LauncherIcon("Icon05WarmDark", "05 · 深色", R.drawable.bymusic_05_warm_dark),
    LauncherIcon("Icon05WarmLight", "05 · 浅色", R.drawable.bymusic_05_warm_light),
    LauncherIcon("Icon06WaveDark", "06 · 深色", R.drawable.bymusic_06_wave_dark),
    LauncherIcon("Icon06WaveLight", "06 · 浅色", R.drawable.bymusic_06_wave_light),
)

@Composable
fun LauncherIconSetting() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    var selected by remember {
        mutableStateOf(launcherIcons.first { icon ->
            val state = context.packageManager.getComponentEnabledSetting(
                ComponentName(context.packageName, "com.sosauce.chocola." + icon.alias)
            )
            state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED ||
                (state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT && icon.alias == "Icon04GeometricLight")
        }.alias)
    }
    SettingsClickable(
        title = stringResource(R.string.launcher_icon),
        description = launcherIcons.first { it.alias == selected }.label,
        icon = R.drawable.system_theme,
        onClick = { open = true }
    )
    if (open) AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(stringResource(R.string.launcher_icon)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.launcher_icon_delay))
                launcherIcons.forEach { icon ->
                    Row(Modifier.fillMaxWidth().clickable {
                        val pm = context.packageManager
                        val changes = launcherIcons.map {
                            val component = ComponentName(context.packageName, "com.sosauce.chocola." + it.alias)
                            val state = if (it == icon) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
                            component to state
                        }
                        if (Build.VERSION.SDK_INT >= 33) {
                            pm.setComponentEnabledSettings(changes.map { (component, state) ->
                                PackageManager.ComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP)
                            })
                        } else {
                            // Enable the new entry first so the app always has a launcher entry.
                            changes.sortedBy { if (it.second == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) 0 else 1 }
                                .forEach { (component, state) -> pm.setComponentEnabledSetting(component, state, PackageManager.DONT_KILL_APP) }
                        }
                        selected = icon.alias
                        open = false
                    }.padding(vertical = 8.dp)) {
                        RadioButton(selected = selected == icon.alias, onClick = null)
                        Image(painterResource(icon.image), null, Modifier.size(48.dp))
                        Text(icon.label, Modifier.padding(12.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { open = false }) { Text(stringResource(android.R.string.cancel)) } }
    )
}
