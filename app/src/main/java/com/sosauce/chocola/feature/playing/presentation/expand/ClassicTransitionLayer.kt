@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class
)

package com.sosauce.chocola.feature.playing.presentation.expand

import androidx.compose.runtime.getValue
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sosauce.chocola.R
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.domain.player.PlayerActions
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.chocola.core.presentation.preferences.rememberHiddenNowPlayingButtons
import com.sosauce.chocola.core.presentation.preferences.rememberIsLandscape
import com.sosauce.chocola.core.presentation.preferences.rememberUseArtAsBackground
import com.sosauce.chocola.feature.playing.presentation.ClassicNowPlayingBody
import com.sosauce.chocola.feature.playing.presentation.NowPlayingLandscapeBody
import com.sosauce.chocola.feature.playing.presentation.NowPlayingQuickActionsBar
import com.sosauce.chocola.feature.playing.presentation.NowPlayingTopBar
import top.yukonga.miuix.kmp.basic.Scaffold
import kotlin.math.roundToInt

/**
 * Expand transition when the turntable page is off: the mini bar card grows
 * into the classic page. Same container as [VinylTransitionLayer]: only the
 * outer clip changes size, the content is laid out once in window coordinates
 * with the classic page's own top bar, body and bottom bar (an empty box holds
 * the artwork's place), so the last frame is the page and nothing slides.
 */
@Composable
internal fun ClassicTransitionLayer(
    progress: () -> Float,
    libraryStyle: Boolean,
    musicState: MusicState,
    miniContainer: Rect,
    miniArt: Rect,
    fullArt: Rect,
    viewport: Rect,
    skin: MiniCardSkin?,
    onCardDrawn: () -> Unit,
    onDragDelta: (Float) -> Unit,
    onDragSettled: (Float) -> Unit,
    onCollapse: () -> Unit,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val container = remember(progress, miniContainer, viewport) {
        derivedStateOf { PlayerExpandMath.containerRect(progress(), miniContainer, viewport) }
    }
    val showChrome by remember(progress) { derivedStateOf { PlayerExpandMath.fullChromeAlpha(progress()) > 0f } }
    val showFace by remember(progress, skin) {
        derivedStateOf { skin != null && PlayerExpandMath.miniFaceAlpha(progress()) > 0f }
    }
    val dragState = rememberDraggableState { delta -> onDragDelta(delta) }
    val background = MaterialTheme.colorScheme.background
    val artBackground = MaterialTheme.colorScheme.surfaceContainerHigh

    Box(Modifier.fillMaxSize(), contentAlignment = AbsoluteAlignment.TopLeft) {
        Box(
            modifier = Modifier
                .absoluteOffset {
                    val rect = container.value
                    IntOffset(rect.left.roundToInt(), rect.top.roundToInt())
                }
                .graphicsLayer {
                    val t = progress()
                    shape = RoundedCornerShape(PlayerExpandMath.containerCornerRadiusDp(t, libraryStyle).dp)
                    if (skin != null) {
                        miniCardShadow(skin, t)
                    } else {
                        shadowElevation = if (t in 0.02f..0.98f) {
                            8.dp.toPx() * PlayerExpandMath.transitionBackgroundAlpha(t)
                        } else {
                            0f
                        }
                    }
                    clip = true
                }
                .drawWithContent {
                    val t = progress()
                    if (skin != null) drawMiniSurface(skin, t)
                    drawRect(background, alpha = PlayerExpandMath.transitionBackgroundAlpha(t))
                    drawContent()
                    if (t > 0f) onCardDrawn()
                }
                .draggable(
                    state = dragState,
                    orientation = Orientation.Vertical,
                    onDragStopped = { velocity -> onDragSettled(velocity) }
                )
                .layout { measurable, _ ->
                    val content = measurable.measure(
                        Constraints.fixed(viewport.width.pixelSize(), viewport.height.pixelSize())
                    )
                    val rect = container.value
                    layout(rect.width.pixelSize(), rect.height.pixelSize()) {
                        content.place(-rect.left.roundToInt(), -rect.top.roundToInt())
                    }
                },
            contentAlignment = AbsoluteAlignment.TopLeft
        ) {
            if (showFace && skin != null) {
                MiniCardFace(
                    skin = skin,
                    miniContainer = miniContainer,
                    musicState = musicState,
                    progress = progress
                )
            }

            if (showChrome) {
                ClassicTransitionChrome(
                    musicState = musicState,
                    fullArt = fullArt,
                    chromeAlpha = { PlayerExpandMath.fullChromeAlpha(progress()) },
                    topChromeAlpha = { PlayerExpandMath.topChromeAlpha(progress()) },
                    pointerEnabled = { progress() > 0.8f },
                    onCollapse = onCollapse,
                    onHandlePlayerActions = onHandlePlayerActions,
                    onNavigate = onNavigate
                )
            }

            // The one cover, in window coordinates: mini art at 0, page artwork at 1.
            Box(
                modifier = Modifier
                    .layout { measurable, constraints ->
                        val cover = PlayerExpandMath.coverRect(progress(), miniArt, fullArt)
                        val art = measurable.measure(
                            Constraints.fixed(cover.width.pixelSize(), cover.height.pixelSize())
                        )
                        layout(constraints.minWidth, constraints.minHeight) {
                            art.place(cover.left.roundToInt(), cover.top.roundToInt())
                        }
                    }
                    .graphicsLayer {
                        shape = RoundedCornerShape(PlayerExpandMath.coverCornerRadiusDp(progress(), libraryStyle).dp)
                        clip = true
                    }
                    .background(artBackground),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = contentColorFor(artBackground)
                )
                AsyncImage(
                    model = musicState.track.artUri,
                    contentDescription = stringResource(R.string.artwork),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * Classic page controls during the transition, built from the page's own
 * pieces ([NowPlayingTopBar], [ClassicNowPlayingBody], [NowPlayingQuickActionsBar];
 * landscape: [NowPlayingLandscapeBody]) with an empty box in the artwork slot.
 * They stay at their final place and only fade, so they never slide.
 */
@Composable
private fun ClassicTransitionChrome(
    musicState: MusicState,
    fullArt: Rect,
    chromeAlpha: () -> Float,
    topChromeAlpha: () -> Float,
    pointerEnabled: () -> Boolean,
    onCollapse: () -> Unit,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit
) {
    val density = LocalDensity.current
    val hiddenButtons = rememberHiddenNowPlayingButtons()
    val isLandscape = rememberIsLandscape()
    val useArtBackground by rememberUseArtAsBackground()
    val guardedCollapse: () -> Unit = { if (pointerEnabled()) onCollapse() }
    val guardedActions: (PlayerActions) -> Unit = { if (pointerEnabled()) onHandlePlayerActions(it) }
    val guardedNavigate: (Screen) -> Unit = { if (pointerEnabled()) onNavigate(it) }
    val artPlaceholder: @Composable (Modifier) -> Unit = { modifier ->
        Box(
            modifier.size(
                with(density) { fullArt.width.toDp() },
                with(density) { fullArt.height.toDp() }
            )
        )
    }

    if (isLandscape) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = chromeAlpha() },
            containerColor = Color.Transparent
        ) { paddingValues ->
            NowPlayingLandscapeBody(
                paddingValues = paddingValues,
                musicState = musicState,
                hiddenButtons = hiddenButtons,
                onHandlePlayerActions = guardedActions,
                onNavigate = guardedNavigate,
                onShrinkToSearchbar = guardedCollapse,
                onShowSpeedCard = {},
                vinylLayout = false,
                artwork = { artPlaceholder(Modifier) }
            )
        }
        return
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = chromeAlpha() },
        containerColor = Color.Transparent,
        topBar = {
            NowPlayingTopBar(
                modifier = Modifier.graphicsLayer { alpha = topChromeAlpha() },
                musicState = musicState,
                hiddenButtons = hiddenButtons,
                onClose = guardedCollapse,
                onNavigate = guardedNavigate,
                onShrinkToSearchbar = guardedCollapse,
                onHandlePlayerActions = guardedActions
            )
        },
        bottomBar = {
            NowPlayingQuickActionsBar(
                musicState = musicState,
                hiddenButtons = hiddenButtons,
                onShowSpeedCard = {},
                onHandlePlayerActions = guardedActions
            )
        }
    ) { paddingValues ->
        ClassicNowPlayingBody(
            paddingValues = paddingValues,
            musicState = musicState,
            hiddenButtons = hiddenButtons,
            onHandlePlayerActions = guardedActions,
            showArtBackground = useArtBackground,
            artwork = { modifier -> artPlaceholder(modifier) }
        )
    }
}

private fun Float.pixelSize(): Int = coerceAtLeast(1f).roundToInt()
