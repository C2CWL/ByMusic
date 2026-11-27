@file:OptIn(
    ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)

package com.sosauce.chocola.feature.playing.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sosauce.chocola.core.designsystem.NowPlayingButton
import com.sosauce.chocola.core.designsystem.components.ActionButtonsRow
import com.sosauce.chocola.core.designsystem.components.CuteSlider
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.domain.player.PlayerActions
import com.sosauce.chocola.core.presentation.components.PlaylistPicker
import com.sosauce.chocola.core.presentation.components.SpeedCard
import com.sosauce.chocola.core.presentation.components.TitleAndArtist
import com.sosauce.chocola.core.presentation.components.dialogs.tracksDetails.TracksDetailsDialog
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.chocola.core.presentation.preferences.rememberSnapSpeedAndPitch
import com.sosauce.chocola.feature.playing.presentation.components.Artwork
import com.sosauce.chocola.feature.playing.presentation.components.PlayingTopRow
import com.sosauce.chocola.feature.playing.presentation.components.QuickActionsRow
import com.sosauce.chocola.feature.playing.presentation.components.rememberTurntableEnabled

@Composable
fun NowPlayingLandscape(
    onHandlePlayerActions: (PlayerActions) -> Unit,
    musicState: MusicState,
    hiddenButtons: Set<NowPlayingButton>,
    onNavigate: (Screen) -> Unit,
    onShrinkToSearchbar: () -> Unit,
    onArtworkBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    dragToClose: NowPlayingDragToClose? = null
) {
    var showSpeedCard by remember { mutableStateOf(false) }
    var snap by rememberSnapSpeedAndPitch()
    var showPlaylistDialog by remember { mutableStateOf(false) }

    var showDetailsDialog by remember { mutableStateOf(false) }

    val useTurntable = rememberTurntableEnabled()
    Scaffold(
        // Pull down anywhere on the page to close.
        modifier = Modifier.dragToClose(dragToClose),
        containerColor = if (useTurntable) Color.Transparent else MaterialTheme.colorScheme.background
    ) { paddingValues ->

        if (showDetailsDialog) {
            TracksDetailsDialog(
                track = musicState.track,
                onDismissRequest = { showDetailsDialog = false }
            )
        }

        if (showPlaylistDialog) {
            PlaylistPicker(
                mediaId = listOf(musicState.track.mediaId),
                onDismissRequest = { showPlaylistDialog = false }
            )
        }


        if (showSpeedCard) {
            SpeedCard(
                musicState = musicState,
                onHandlePlayerAction = onHandlePlayerActions,
                onDismissRequest = { showSpeedCard = false },
                shouldSnap = snap,
                onChangeSnap = { snap = !snap }
            )
        }

        NowPlayingLandscapeBody(
            paddingValues = paddingValues,
            musicState = musicState,
            hiddenButtons = hiddenButtons,
            onHandlePlayerActions = onHandlePlayerActions,
            onNavigate = onNavigate,
            onShrinkToSearchbar = onShrinkToSearchbar,
            onShowSpeedCard = { showSpeedCard = true },
            vinylLayout = useTurntable,
            artwork = {
                Artwork(
//                    pagerModifier = Modifier
//                        .fillMaxWidth(0.4f),
                    musicState = musicState,
                    onHandlePlayerActions = onHandlePlayerActions,
                    onArtworkBoundsChanged = onArtworkBoundsChanged
                )
            }
        )
    }
}

/**
 * Landscape page body: artwork slot on the start side, controls column beside
 * it. Shared with the expand transition, which passes an empty box the size of
 * the measured artwork, so its last frame lays the controls out like the page.
 */
@Composable
internal fun NowPlayingLandscapeBody(
    paddingValues: PaddingValues,
    musicState: MusicState,
    hiddenButtons: Set<NowPlayingButton>,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit,
    onShrinkToSearchbar: () -> Unit,
    onShowSpeedCard: () -> Unit,
    vinylLayout: Boolean = false,
    recordAreaModifier: Modifier = Modifier,
    artwork: @Composable () -> Unit
) {
    if (vinylLayout) {
        VinylLandscapeBody(
            paddingValues = paddingValues,
            musicState = musicState,
            hiddenButtons = hiddenButtons,
            onHandlePlayerActions = onHandlePlayerActions,
            onNavigate = onNavigate,
            onShrinkToSearchbar = onShrinkToSearchbar,
            onShowSpeedCard = onShowSpeedCard,
            recordAreaModifier = recordAreaModifier,
            artwork = artwork
        )
        return
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(paddingValues)
            .padding(horizontal = 15.dp)
            .fillMaxSize()
    ) {
        Column {
            artwork()
        }
        Spacer(modifier = Modifier.width(10.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PlayingTopRow(
                musicState = musicState,
                onNavigate = onNavigate,
                onShrinkToSearchbar = onShrinkToSearchbar,
                onHandlePlayerActions = onHandlePlayerActions,
                hiddenButtons = hiddenButtons
            )
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
            Spacer(Modifier.height(24.dp))
            CuteSlider(
                musicState = musicState,
                onHandlePlayerActions = onHandlePlayerActions
            )
            ActionButtonsRow(
                musicState = musicState,
                onHandlePlayerActions = onHandlePlayerActions,
                hiddenButtons = hiddenButtons
            )
            Spacer(modifier = Modifier.weight(1f))
            QuickActionsRow(
                musicState = musicState,
                onShowSpeedCard = onShowSpeedCard,
                onHandlePlayerActions = onHandlePlayerActions,
                hiddenButtons = hiddenButtons
            )
        }

    }
}
