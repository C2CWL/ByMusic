@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.sosauce.chocola.core.presentation.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.Dp
import kotlinx.coroutines.launch
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sosauce.chocola.R
import com.sosauce.chocola.core.designsystem.components.PlayPauseButton
import com.sosauce.chocola.core.designsystem.components.LocalGlassBackdrop
import com.sosauce.chocola.core.designsystem.components.liquidGlass
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.domain.player.PlayerActions
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.chocola.feature.playing.presentation.expand.PlayerExpandMath
import com.sosauce.chocola.feature.playing.presentation.expand.PlayerExpandOverlay
import com.sosauce.chocola.feature.playing.presentation.expand.rememberPlayerExpandController
import com.sosauce.chocola.feature.playing.presentation.expand.systemAnimationsDisabled

/**
 * Compact player bar with a liquid-glass surface ([liquidGlass]).
 * Only the bar's own area is blurred, from the screen's [LocalGlassBackdrop]
 * (API 31+); there is no full-screen bitmap capture (see #83/#85). Without a
 * backdrop or below API 31 it falls back to the static glass.
 *
 * Tapping first copies the glass surface (only this bar's pixels, once) and
 * hands it to [onSurfaceSnapshot], so the expand card starts as exactly this bar.
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
    onMiniArtBoundsChanged: (Rect) -> Unit = {},
    onSurfaceSnapshot: (ImageBitmap?) -> Unit = {}
) {
    if (!musicState.isPlayerReady) return

    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(MiniPlayerDefaults.CORNER_DP.dp)
    val surfaceLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .navigationBarsPadding()
            .height(MiniPlayerDefaults.HEIGHT_DP.dp)
            .miniPlayerShadow(shape, colors.scrim, MiniPlayerDefaults.ELEVATION_DP.dp)
            .liquidGlass(shape, surfaceCapture = surfaceLayer)
            .clickable {
                scope.launch {
                    val snapshot = if (surfaceLayer.size.width > 0 && surfaceLayer.size.height > 0) {
                        runCatching { surfaceLayer.toImageBitmap() }.getOrNull()
                    } else {
                        null
                    }
                    onSurfaceSnapshot(snapshot)
                    onOpenFullPlayer()
                }
            }
            .onGloballyPositioned { onMiniCardBoundsChanged(it.boundsInWindow()) },
        contentAlignment = Alignment.Center
    ) {
        MiniPlayerFace(
            musicState = musicState,
            onHandlePlayerActions = onHandlePlayerActions,
            onNavigate = onNavigate,
            artAlpha = miniArtAlpha,
            onArtBoundsChanged = onMiniArtBoundsChanged
        )
    }
}

/** Shared sizes of the mini bar, also used by the expand card that starts on it. */
object MiniPlayerDefaults {
    const val CORNER_DP = 28f
    const val HEIGHT_DP = 64f
    const val ELEVATION_DP = 6f
    const val ART_CORNER_DP = 10f
}

/** The mini bar's shadow; the expand card uses the same colors so its first frame matches. */
fun Modifier.miniPlayerShadow(shape: Shape, scrim: Color, elevation: Dp): Modifier = shadow(
    elevation = elevation,
    shape = shape,
    ambientColor = scrim.copy(alpha = 0.12f),
    spotColor = scrim.copy(alpha = 0.20f)
)

/**
 * The mini bar's content (artwork, title, artist, play/pause, queue). Drawn by
 * the bar itself and, with the artwork hidden and inert actions, by the expand
 * card while it leaves / returns to the bar, so both are the same pixels.
 */
@Composable
internal fun MiniPlayerFace(
    musicState: MusicState,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit,
    artAlpha: Float = 1f,
    onArtBoundsChanged: (Rect) -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .onGloballyPositioned { onArtBoundsChanged(it.boundsInWindow()) }
                .graphicsLayer { alpha = artAlpha }
                .clip(RoundedCornerShape(MiniPlayerDefaults.ART_CORNER_DP.dp))
                .background(colors.surfaceContainerHigh),
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

    FreezeGlassWhileExpanding(expanding)

    BackHandler(enabled = expanding) {
        controller.collapse()
    }

    // The card takes over from the bar on its first frame and hands back at
    // progress 0 (see PlayerExpandMath.barHidden): the bar is never shown
    // together with a different-looking card.
    Box(
        modifier = modifier.graphicsLayer {
            alpha = if (controller.barHidden) 0f else 1f
        }
    ) {
        MiniPlayerBar(
            musicState = musicState,
            onHandlePlayerActions = onHandlePlayerActions,
            onNavigate = onNavigate,
            onOpenFullPlayer = { controller.open() },
            onMiniCardBoundsChanged = { controller.onMiniContainer(it) },
            onMiniArtBoundsChanged = { controller.onMiniArt(it) },
            onSurfaceSnapshot = { controller.onMiniSurface(it) }
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

/** Stops the glass backdrop from re-recording while the expand transition is visible. */
@Composable
internal fun FreezeGlassWhileExpanding(expanding: Boolean) {
    val backdrop = LocalGlassBackdrop.current ?: return
    DisposableEffect(backdrop, expanding) {
        backdrop.frozen = expanding
        onDispose { backdrop.frozen = false }
    }
}
