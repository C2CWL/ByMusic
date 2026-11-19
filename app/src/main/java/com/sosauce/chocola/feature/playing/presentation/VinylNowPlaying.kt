package com.sosauce.chocola.feature.playing.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.sosauce.chocola.R
import com.sosauce.chocola.core.designsystem.NowPlayingButton
import com.sosauce.chocola.core.designsystem.components.ActionButtonsRow
import com.sosauce.chocola.core.designsystem.components.CuteSlider
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.domain.player.PlayerActions
import com.sosauce.chocola.core.presentation.components.SpeedCard
import com.sosauce.chocola.core.presentation.components.TitleAndArtist
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.chocola.core.presentation.preferences.rememberSnapSpeedAndPitch
import com.sosauce.chocola.feature.playing.presentation.components.Artwork
import com.sosauce.chocola.feature.playing.presentation.components.CuteTimePicker
import com.sosauce.chocola.feature.playing.presentation.components.NowPlayingTrackDropdownMenu

/**
 * Swipe-down-to-close on the settled page. The host forwards it to the same
 * expand progress the open animation uses, so dragging down plays the
 * expand animation backwards.
 */
class NowPlayingDragToClose(
    val onStart: () -> Unit,
    val onDelta: (Float) -> Unit,
    val onStop: (Float) -> Unit
)

/** Vertical drag that feeds [drag]; no-op without one. */
@Composable
internal fun Modifier.dragToClose(drag: NowPlayingDragToClose?): Modifier {
    if (drag == null) return this
    val current by rememberUpdatedState(drag)
    val state = rememberDraggableState { delta -> current.onDelta(delta) }
    return draggable(
        state = state,
        orientation = Orientation.Vertical,
        onDragStarted = { current.onStart() },
        onDragStopped = { velocity -> current.onStop(velocity) }
    )
}

/** Accessibility: a "close" action, since the page has no close button. */
private fun Modifier.closeAction(label: String, onClose: () -> Unit): Modifier =
    semantics {
        customActions = listOf(CustomAccessibilityAction(label) { onClose(); true })
    }

/** Turntable page in portrait: record on top, every control at the bottom. */
@Composable
internal fun VinylNowPlayingContent(
    modifier: Modifier = Modifier,
    musicState: MusicState,
    hiddenButtons: Set<NowPlayingButton>,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit,
    onShrinkToSearchbar: () -> Unit,
    onArtworkBoundsChanged: ((Rect) -> Unit)?,
    dragToClose: NowPlayingDragToClose?
) {
    var snap by rememberSnapSpeedAndPitch()
    var showSpeedCard by remember { mutableStateOf(false) }

    if (showSpeedCard) {
        SpeedCard(
            musicState = musicState,
            onHandlePlayerAction = onHandlePlayerActions,
            onDismissRequest = { showSpeedCard = false },
            shouldSnap = snap,
            onChangeSnap = { snap = !snap }
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = Color.Transparent
    ) { paddingValues ->
        VinylNowPlayingBody(
            paddingValues = paddingValues,
            musicState = musicState,
            hiddenButtons = hiddenButtons,
            onHandlePlayerActions = onHandlePlayerActions,
            onNavigate = onNavigate,
            onShrinkToSearchbar = onShrinkToSearchbar,
            onShowSpeedCard = { showSpeedCard = true },
            recordAreaModifier = Modifier.dragToClose(dragToClose),
            artwork = { artModifier ->
                Artwork(
                    modifier = artModifier,
                    musicState = musicState,
                    onHandlePlayerActions = onHandlePlayerActions,
                    onArtworkBoundsChanged = onArtworkBoundsChanged
                )
            }
        )
    }
}

/**
 * Portrait turntable body. Shared with the expand transition, which passes an
 * empty box for [artwork], so its last frame lays the controls out exactly
 * like the page (the controls are anchored to the bottom and do not move with
 * the record).
 */
@Composable
internal fun VinylNowPlayingBody(
    paddingValues: PaddingValues,
    musicState: MusicState,
    hiddenButtons: Set<NowPlayingButton>,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit,
    onShrinkToSearchbar: () -> Unit,
    onShowSpeedCard: () -> Unit,
    recordAreaModifier: Modifier = Modifier,
    handleAlpha: Float = 1f,
    artwork: @Composable (Modifier) -> Unit
) {
    val closeLabel = stringResource(R.string.close)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(horizontal = 15.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Everything above the title is the record's: swipe down here to close.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .closeAction(closeLabel, onShrinkToSearchbar)
                .then(recordAreaModifier)
        ) {
            VinylHandle(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = VinylLayout.HANDLE_TOP_DP.dp)
                    .graphicsLayer { alpha = handleAlpha }
            )
            Box(
                modifier = Modifier.align(BiasAlignment(0f, VinylLayout.RECORD_VERTICAL_BIAS))
            ) {
                artwork(
                    Modifier.sizeIn(
                        maxWidth = VinylLayout.MAX_RECORD_DP.dp,
                        maxHeight = VinylLayout.MAX_RECORD_DP.dp
                    )
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        TitleAndArtist(
            title = musicState.track.title,
            artist = musicState.track.artist,
            album = musicState.track.album,
            isFavorite = musicState.track.isFavorite,
            onToggleFavorite = {
                musicState.track.mediaId.toLongOrNull()
                    ?.let { onHandlePlayerActions(PlayerActions.ToggleFavorite(it)) }
            },
            hiddenButtons = hiddenButtons
        )
        Spacer(Modifier.height(16.dp))
        CuteSlider(
            musicState = musicState,
            onHandlePlayerActions = onHandlePlayerActions
        )
        Spacer(Modifier.height(16.dp))
        ActionButtonsRow(
            musicState = musicState,
            onHandlePlayerActions = onHandlePlayerActions,
            hiddenButtons = hiddenButtons
        )
        if (VinylLayout.showsBar(hiddenButtons)) {
            Spacer(Modifier.height(16.dp))
            VinylControlBar(
                musicState = musicState,
                hiddenButtons = hiddenButtons,
                onHandlePlayerActions = onHandlePlayerActions,
                onNavigate = onNavigate,
                onShrinkToSearchbar = onShrinkToSearchbar,
                onShowSpeedCard = onShowSpeedCard
            )
        }
        Spacer(Modifier.height(12.dp))
    }
}

/** The faint swipe-down hint. Decorative only: not clickable, no semantics. */
@Composable
internal fun VinylHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(VinylLayout.HANDLE_WIDTH_DP.dp, VinylLayout.HANDLE_HEIGHT_DP.dp)
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = VinylLayout.HANDLE_ALPHA),
                CircleShape
            )
    )
}

/**
 * 44 dp pill: lyrics · queue | shuffle · repeat · speed · timer | more.
 * Hidden buttons leave no gap; the rest share the width evenly. Active
 * shuffle / repeat / changed speed / running timer use the accent color.
 */
@Composable
internal fun VinylControlBar(
    musicState: MusicState,
    hiddenButtons: Set<NowPlayingButton>,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit,
    onShrinkToSearchbar: () -> Unit,
    onShowSpeedCard: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showTimePicker by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    if (showTimePicker) {
        CuteTimePicker(
            currentTimerTime = musicState.sleepTimerRemainingDuration,
            onDismissRequest = { showTimePicker = false },
            onSetTimer = { hours, minutes ->
                showTimePicker = false
                onHandlePlayerActions(PlayerActions.SetSleepTimer(hours, minutes))
            },
            onCancelTimer = { onHandlePlayerActions(PlayerActions.CancelSleepTimer) }
        )
    }

    val accent = MaterialTheme.colorScheme.primary
    val normal = MaterialTheme.colorScheme.onSurfaceVariant
    val divider = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val isRepeating =
        musicState.repeatMode == Player.REPEAT_MODE_ONE || musicState.repeatMode == Player.REPEAT_MODE_ALL
    val isRateChanged = musicState.speed != 1.0f || musicState.pitch != 1.0f
    val timerRunning = musicState.sleepTimerRemainingDuration > 0

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(VinylLayout.BAR_HEIGHT_DP.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f), CircleShape)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VinylLayout.visibleBarGroups(hiddenButtons).forEachIndexed { groupIndex, group ->
            if (groupIndex > 0) {
                Box(
                    Modifier
                        .width(1.dp)
                        .height(18.dp)
                        .background(divider)
                )
            }
            group.forEach { button ->
                val (icon, active) = when (button) {
                    NowPlayingButton.LYRICS -> R.drawable.lyrics_filled to false
                    NowPlayingButton.QUEUE -> R.drawable.queue to false
                    NowPlayingButton.SHUFFLE -> R.drawable.shuffle to musicState.shuffle
                    NowPlayingButton.REPEAT ->
                        (if (musicState.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.repeat_one else R.drawable.repeat) to isRepeating
                    NowPlayingButton.SPEED -> R.drawable.speed_filled to isRateChanged
                    NowPlayingButton.SLEEP_TIMER ->
                        (if (timerRunning) R.drawable.sleep_timer_active_filled else R.drawable.sleep_timer_filled) to timerRunning
                    else -> R.drawable.more_vert to false
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(role = Role.Button) {
                            when (button) {
                                NowPlayingButton.LYRICS -> onNavigate(Screen.Lyrics)
                                NowPlayingButton.QUEUE -> {
                                    onShrinkToSearchbar()
                                    onNavigate(Screen.Queue)
                                }
                                NowPlayingButton.SHUFFLE -> onHandlePlayerActions(PlayerActions.Shuffle)
                                NowPlayingButton.REPEAT -> onHandlePlayerActions(PlayerActions.ChangeRepeatMode)
                                NowPlayingButton.SPEED -> onShowSpeedCard()
                                NowPlayingButton.SLEEP_TIMER -> showTimePicker = true
                                else -> showMoreMenu = !showMoreMenu
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = stringResource(button.label),
                        tint = if (active) accent else normal,
                        modifier = Modifier.size(VinylLayout.BAR_ICON_DP.dp)
                    )
                    if (button == NowPlayingButton.MORE_OPTIONS) {
                        NowPlayingTrackDropdownMenu(
                            track = musicState.track,
                            isExpanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false },
                            onNavigate = onNavigate,
                            onHandlePlayerActions = onHandlePlayerActions
                        )
                    }
                }
            }
        }
    }
}

/**
 * Turntable page in landscape: record on the start side (swipe down on it to
 * close), title at the top of the side column and progress, playback row and
 * pill bar at its bottom. Also no top buttons. Shared with the transition.
 */
@Composable
internal fun VinylLandscapeBody(
    paddingValues: PaddingValues,
    musicState: MusicState,
    hiddenButtons: Set<NowPlayingButton>,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit,
    onShrinkToSearchbar: () -> Unit,
    onShowSpeedCard: () -> Unit,
    recordAreaModifier: Modifier = Modifier,
    handleAlpha: Float = 1f,
    artwork: @Composable () -> Unit
) {
    val closeLabel = stringResource(R.string.close)
    Box(
        Modifier
            .fillMaxSize()
            .padding(paddingValues)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 15.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .closeAction(closeLabel, onShrinkToSearchbar)
                    .then(recordAreaModifier),
                contentAlignment = Alignment.Center
            ) {
                artwork()
            }
            Spacer(Modifier.width(10.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TitleAndArtist(
                    title = musicState.track.title,
                    artist = musicState.track.artist,
                    album = musicState.track.album,
                    isFavorite = musicState.track.isFavorite,
                    onToggleFavorite = {
                        musicState.track.mediaId.toLongOrNull()
                            ?.let { onHandlePlayerActions(PlayerActions.ToggleFavorite(it)) }
                    },
                    hiddenButtons = hiddenButtons
                )
                Spacer(Modifier.weight(1f))
                CuteSlider(
                    musicState = musicState,
                    onHandlePlayerActions = onHandlePlayerActions
                )
                ActionButtonsRow(
                    musicState = musicState,
                    onHandlePlayerActions = onHandlePlayerActions,
                    hiddenButtons = hiddenButtons
                )
                if (VinylLayout.showsBar(hiddenButtons)) {
                    Spacer(Modifier.height(8.dp))
                    VinylControlBar(
                        musicState = musicState,
                        hiddenButtons = hiddenButtons,
                        onHandlePlayerActions = onHandlePlayerActions,
                        onNavigate = onNavigate,
                        onShrinkToSearchbar = onShrinkToSearchbar,
                        onShowSpeedCard = onShowSpeedCard
                    )
                }
            }
        }
        VinylHandle(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = VinylLayout.HANDLE_TOP_DP.dp)
                .graphicsLayer { alpha = handleAlpha }
        )
    }
}
