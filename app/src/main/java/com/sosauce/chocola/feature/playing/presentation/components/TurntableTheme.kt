@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sosauce.chocola.feature.playing.presentation.components

import androidx.compose.animation.core.tween
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
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
import kotlin.random.Random
import kotlinx.coroutines.delay

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
        // Fast skipping: the previous colors stay until skipping settles, and a
        // further skip cancels this effect before any decode or color pick.
        // The very first pick (no seed yet) runs at once.
        if (seed != null) delay(TurntableNeedle.SKIP_SETTLE_MS)
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

/** Color change for the track the user stopped on (design value: 300ms). */
private val turntableColorSpec = tween<Color>(TURNTABLE_COLOR_CHANGE_MS)

internal const val TURNTABLE_COLOR_CHANGE_MS = 300

/** Applies a turntable color state without drawing anything. */
@Composable
fun TurntableColors(
    state: DynamicMaterialThemeState,
    content: @Composable () -> Unit,
) {
    DynamicMaterialExpressiveTheme(state = state, animate = true, animationSpec = turntableColorSpec) {
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

// Stable normalized positions: no animation, re-layout, or random allocation during drawing.
private val turntableStars = Random(718).let { random ->
    List(54) { Offset(random.nextFloat(), random.nextFloat()) }
}

internal fun DrawScope.drawTurntableStars(color: Color, alpha: Float = 1f) {
    turntableStars.forEachIndexed { index, point ->
        val center = Offset(point.x * size.width, point.y * size.height)
        val prominent = index % 13 == 0
        val brightness = if (prominent) 0.23f else 0.08f + (index % 4) * 0.035f
        val tint = color.copy(alpha = alpha * brightness)
        val radius = if (prominent) 1.1.dp.toPx() else (0.55f + (index % 3) * 0.23f).dp.toPx()
        drawCircle(tint, radius, center)
        if (prominent) {
            val arm = 2.7.dp.toPx()
            val stroke = 0.65.dp.toPx()
            drawLine(tint, center - Offset(arm, 0f), center + Offset(arm, 0f), strokeWidth = stroke)
            drawLine(tint, center - Offset(0f, arm), center + Offset(0f, arm), strokeWidth = stroke)
        }
    }
}

@Composable
internal fun Modifier.turntableBackdrop(): Modifier {
    val brush = turntableBackdropBrush()
    val starlight = MaterialTheme.colorScheme.onSurface
    return background(brush).drawBehind { drawTurntableStars(starlight) }
}

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
                .turntableBackdrop(),
        ) {
            content()
        }
        return
    }
    val state = rememberTurntableThemeState(artworkUri)
    DynamicMaterialExpressiveTheme(state = state, animate = true, animationSpec = turntableColorSpec) {
        Box(Modifier.fillMaxSize().turntableBackdrop()) {
            content()
        }
    }
}
