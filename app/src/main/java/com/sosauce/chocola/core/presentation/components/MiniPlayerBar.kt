@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.sosauce.chocola.core.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sosauce.chocola.R
import com.sosauce.chocola.core.designsystem.components.PlayPauseButton
import com.sosauce.chocola.core.designsystem.components.libraryGlass
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.domain.player.PlayerActions
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.chocola.feature.playing.presentation.expand.PlayerExpandMath
import com.sosauce.chocola.feature.playing.presentation.expand.PlayerExpandOverlay
import com.sosauce.chocola.feature.playing.presentation.expand.rememberPlayerExpandController
import com.sosauce.chocola.feature.playing.presentation.expand.systemAnimationsDisabled

/**
 * Compact player bar with a static translucent glass surface.
 * Deliberately independent of the navigation root: no full-screen bitmap capture
 * during the expand animation and no backdrop state to lose on lifecycle changes.
 */
@Composable
fun SharedTransitionScope.MiniPlayerBar(
    modifier: Modifier = Modifier,
    musicState: MusicState,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit,
    onOpenFullPlayer: () -> Unit,
    miniArtAlpha: Float = 1f,
    onMiniCardBoundsChanged: (Rect) -> Unit = {},
    onMiniArtBoundsChanged: (Rect) -> Unit = {}
) {
    if (!musicState.isPlayerReady) return

    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(28.dp)
    val highlight = colors.onSurface.copy(alpha = 0.08f)
    val highlightStroke = with(LocalDensity.current) { 1.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .navigationBarsPadding()
            .height(64.dp)
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = colors.scrim.copy(alpha = 0.12f),
                spotColor = colors.scrim.copy(alpha = 0.20f)
            )
            .libraryGlass(shape)
            .drawWithCache {
                val stroke = highlightStroke
                onDrawWithContent {
                    drawContent()
                    drawLine(
                        color = highlight,
                        start = Offset(0f, stroke),
                        end = Offset(size.width, stroke),
                        strokeWidth = stroke
                    )
                }
            }
            .clickable { onOpenFullPlayer() }
            .onGloballyPositioned { onMiniCardBoundsChanged(it.boundsInWindow()) },
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceContainerHigh)
                    .onGloballyPositioned { onMiniArtBoundsChanged(it.boundsInWindow()) }
                    .graphicsLayer { alpha = miniArtAlpha },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = contentColorFor(colors.surfaceContainerHigh)
                )
                AsyncImage(
                    model = musicState.track.artUri,
                    contentDescription = stringResource(R.string.artwork),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Text(
                    text = musicState.track.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = musicState.track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            PlayPauseButton(
                isPlaying = musicState.isPlaying,
                onHandlePlayerActions = onHandlePlayerActions
            )

            IconButton(onClick = { onNavigate(Screen.Queue) }) {
                Icon(painterResource(R.drawable.queue), stringResource(R.string.queue))
            }
        }
    }
}

/**
 * Hosts the glass mini bar and its full-screen expansion for screens that do
 * not already own a [PlayerExpandOverlay], such as online music.
 */
@Composable
fun SharedTransitionScope.MiniPlayerBarHost(
    modifier: Modifier = Modifier,
    musicState: MusicState,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit,
    libraryStyle: Boolean = true
) {
    var showFullPlayerSaved by rememberSaveable { mutableStateOf(false) }
    val controller = rememberPlayerExpandController(initiallyExpanded = showFullPlayerSaved)
    controller.reduceMotion = systemAnimationsDisabled()
    val expanding by remember(controller) { derivedStateOf { controller.progress > 0f } }
    val windowInfo = LocalWindowInfo.current

    LaunchedEffect(controller) {
        snapshotFlow {
            val progress = controller.progress
            when {
                PlayerExpandMath.isSettledCollapsed(progress) -> false
                PlayerExpandMath.isSettledExpanded(progress) -> true
                else -> null
            }
        }.collect { settled ->
            if (settled != null) showFullPlayerSaved = settled
        }
    }
    LaunchedEffect(musicState.isPlayerReady) {
        if (!musicState.isPlayerReady) {
            controller.onPlayerNotReady()
        } else if (controller.fullArt == null) {
            controller.requestMeasure()
        }
    }
    LaunchedEffect(windowInfo.containerSize) {
        controller.invalidateFullArt()
        if (musicState.isPlayerReady) {
            controller.requestMeasure()
        }
    }

    BackHandler(enabled = expanding) {
        controller.collapse()
    }

    Box(
        modifier = modifier.graphicsLayer {
            alpha = if (PlayerExpandMath.isSettledExpanded(controller.progress)) 0f else 1f
        }
    ) {
        MiniPlayerBar(
            musicState = musicState,
            onHandlePlayerActions = onHandlePlayerActions,
            onNavigate = onNavigate,
            onOpenFullPlayer = { controller.open() },
            miniArtAlpha = if (expanding && PlayerExpandMath.boundsReady(
                controller.miniContainer, controller.miniArt, controller.fullArt
            )) 0f else 1f,
            onMiniCardBoundsChanged = { controller.onMiniContainer(it) },
            onMiniArtBoundsChanged = { controller.onMiniArt(it) }
        )
    }

    PlayerExpandOverlay(
        controller = controller,
        musicState = musicState,
        libraryStyle = libraryStyle,
        onHandlePlayerActions = onHandlePlayerActions,
        onNavigate = onNavigate
    )
}
