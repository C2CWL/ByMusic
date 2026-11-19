@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sosauce.chocola.feature.playing.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.kmpalette.rememberDominantColorState
import com.materialkolor.DynamicMaterialExpressiveTheme
import com.materialkolor.DynamicMaterialThemeState
import com.materialkolor.rememberDynamicMaterialThemeState
import com.sosauce.chocola.core.designsystem.theme.anyDarkColorScheme
import com.sosauce.chocola.core.presentation.preferences.rememberUseTurntable

/**
 * Set by the mini-player expand overlay, which already wraps both the
 * transition layer and the settled page in one turntable color theme. A
 * [TurntableTheme] below it then only draws the backdrop and does not pick
 * colors a second time (a second pick is what made colors jump on landing).
 */
val LocalTurntableThemeHoisted = compositionLocalOf { false }

/**
 * The turntable switch as already read by the expand overlay. The overlay is
 * composed for the whole library session, so its value is loaded long before
 * the page opens; a page composed fresh inside the overlay would otherwise see
 * the preference default for its first frame.
 */
val LocalTurntableEnabledOverride = compositionLocalOf<Boolean?> { null }

/** Turntable switch, preferring the value the expand overlay already holds. */
@Composable
fun rememberTurntableEnabled(): Boolean {
    val preference by rememberUseTurntable()
    return LocalTurntableEnabledOverride.current ?: preference
}

/**
 * Artwork-seeded dark color state for the turntable page. Hoistable: the expand
 * overlay keeps one alive while the mini player is shown, so the colors are
 * ready before the page opens. While a new cover is loading, the previous
 * seed is kept instead of falling back to the system color for a moment.
 */
@Composable
fun rememberTurntableThemeState(artworkUri: String?): DynamicMaterialThemeState {
    val context = LocalContext.current
    val fallback = anyDarkColorScheme().primary
    var seed by remember { mutableStateOf<Color?>(null) }
    val dominantColorState = rememberDominantColorState()
    LaunchedEffect(artworkUri) {
        if (artworkUri == null) {
            seed = null
            return@LaunchedEffect
        }
        val request =
            ImageRequest
                .Builder(context)
                .data(artworkUri)
                .size(128)
                .allowHardware(false)
                .build()
        val bitmap: ImageBitmap? =
            context.imageLoader
                .execute(request)
                .image
                ?.toBitmap()
                ?.asImageBitmap()
        if (bitmap == null) {
            seed = null
        } else {
            dominantColorState.updateFrom(bitmap)
            seed = dominantColorState.color
        }
    }
    return rememberDynamicMaterialThemeState(seedColor = seed ?: fallback, isDark = true)
}

/** Applies a turntable color state without drawing anything. */
@Composable
fun TurntableColors(
    state: DynamicMaterialThemeState,
    content: @Composable () -> Unit,
) {
    DynamicMaterialExpressiveTheme(state = state, animate = true) {
        CompositionLocalProvider(LocalTurntableThemeHoisted provides true) {
            content()
        }
    }
}

/** The turntable page backdrop. Read inside a turntable color theme. */
@Composable
fun turntableBackdropBrush(): Brush =
    Brush.verticalGradient(
        listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
    )

@Composable
fun TurntableTheme(
    enabled: Boolean,
    artworkUri: String?,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    if (LocalTurntableThemeHoisted.current) {
        // Colors come from the overlay. The overlay sits in its own window, so
        // the backdrop gets an opaque base; the transition draws the same one.
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .background(turntableBackdropBrush()),
        ) {
            content()
        }
        return
    }
    val state = rememberTurntableThemeState(artworkUri)
    DynamicMaterialExpressiveTheme(state = state, animate = true) {
        Box(Modifier.fillMaxSize().background(turntableBackdropBrush())) {
            content()
        }
    }
}
