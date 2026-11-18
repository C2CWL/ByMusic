package com.sosauce.chocola.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.sosauce.chocola.core.designsystem.CuteTheme

data class AppearancePrefs(
    val themeMode: String,
    val oledBlack: Boolean
)

/**
 * Legacy [CuteTheme.AMOLED] was a fourth exclusive theme. Resolve it to dark mode
 * plus the independent OLED switch so "follow system" can be chosen without
 * dropping the pure-black preference.
 */
fun resolveAppearancePrefs(storedTheme: String, storedOled: Boolean): AppearancePrefs {
    return if (storedTheme == CuteTheme.AMOLED) {
        AppearancePrefs(themeMode = CuteTheme.DARK, oledBlack = true)
    } else {
        AppearancePrefs(themeMode = storedTheme, oledBlack = storedOled)
    }
}

fun isEffectiveDark(storedTheme: String, systemDark: Boolean): Boolean {
    return when (resolveAppearancePrefs(storedTheme, storedOled = false).themeMode) {
        CuteTheme.DARK -> true
        CuteTheme.LIGHT -> false
        CuteTheme.SYSTEM -> systemDark
        else -> systemDark
    }
}

fun shouldApplyOledBlack(
    storedTheme: String,
    storedOled: Boolean,
    systemDark: Boolean
): Boolean {
    val prefs = resolveAppearancePrefs(storedTheme, storedOled)
    return prefs.oledBlack && isEffectiveDark(storedTheme, systemDark)
}

/** Changing system/light/dark must not clear the OLED flag. */
fun selectThemeMode(
    storedTheme: String,
    storedOled: Boolean,
    newMode: String
): AppearancePrefs {
    val resolved = resolveAppearancePrefs(storedTheme, storedOled)
    val mode = if (newMode == CuteTheme.AMOLED) CuteTheme.DARK else newMode
    return AppearancePrefs(
        themeMode = mode,
        oledBlack = resolved.oledBlack || newMode == CuteTheme.AMOLED
    )
}

fun setOledBlackPreference(storedTheme: String, enabled: Boolean): AppearancePrefs {
    return AppearancePrefs(
        themeMode = resolveAppearancePrefs(storedTheme, storedOled = enabled).themeMode,
        oledBlack = enabled
    )
}

/** Re-read persisted values as after a process restart. */
fun reloadAppearancePrefs(persistedTheme: String, persistedOled: Boolean): AppearancePrefs {
    return resolveAppearancePrefs(persistedTheme, persistedOled)
}

/**
 * Flatten page and container surfaces to true black. Accent, content, and
 * outline tokens are left unchanged so selected states, icons, and hairlines
 * stay visible. Only applied when [shouldApplyOledBlack] is true.
 */
fun ColorScheme.withOledPageBackgrounds(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceBright = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerHigh = Color.Black,
    surfaceContainerHighest = Color.Black
)

@Composable
fun OledPageBackgroundTheme(
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    if (!enabled) {
        content()
        return
    }
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.withOledPageBackgrounds(),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}
