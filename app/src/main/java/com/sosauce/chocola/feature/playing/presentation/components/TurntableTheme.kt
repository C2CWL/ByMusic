@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sosauce.chocola.feature.playing.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.materialkolor.DynamicMaterialExpressiveTheme
import com.materialkolor.rememberDynamicMaterialThemeState
import com.sosauce.chocola.core.designsystem.theme.rememberSeedColor

@Composable
internal fun TurntableTheme(
    enabled: Boolean,
    artworkUri: String?,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        content()
        return
    }
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, artworkUri) {
        value = null
        if (artworkUri != null) {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(artworkUri)
                    .size(128)
                    .allowHardware(false)
                    .build()
            value =
                context.imageLoader
                    .execute(request)
                    .image
                    ?.toBitmap()
                    ?.asImageBitmap()
        }
    }
    val seed = rememberSeedColor(isDark = true, useMaterialArt = true, imageBitmap = bitmap)
    val state = rememberDynamicMaterialThemeState(seedColor = seed, isDark = true)
    DynamicMaterialExpressiveTheme(state = state, animate = true) {
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                ),
            ),
        ) {
            content()
        }
    }
}
