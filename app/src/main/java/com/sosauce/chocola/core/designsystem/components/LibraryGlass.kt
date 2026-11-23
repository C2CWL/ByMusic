package com.sosauce.chocola.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.sosauce.chocola.core.presentation.preferences.rememberLightweightGlass
import androidx.compose.runtime.getValue

/** Static translucent glass: no backdrop capture, offscreen layer or per-frame blur. */
@Composable
fun Modifier.libraryGlass(shape: Shape): Modifier {
    val enabled by rememberLightweightGlass()
    val colors = MaterialTheme.colorScheme
    return if (enabled) {
        clip(shape).background(Brush.verticalGradient(listOf(
            colors.surfaceContainerHigh.copy(alpha = 0.72f),
            colors.surface.copy(alpha = 0.60f)
        ))).border(1.dp, colors.outlineVariant.copy(alpha = 0.55f), shape)
    } else {
        clip(shape).background(colors.surface)
    }
}
