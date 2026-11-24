package com.sosauce.chocola.feature.library.presentation.favorites

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sosauce.chocola.R
import com.sosauce.chocola.core.domain.model.CuteTrack
import com.sosauce.chocola.core.domain.player.PlaySource
import com.sosauce.chocola.core.domain.player.PlayerActions
import com.sosauce.chocola.core.presentation.components.trackItems
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.chocola.feature.library.presentation.main.MainState
import com.sosauce.chocola.feature.library.presentation.main.components.LibraryListToolbar
import com.sosauce.sweetselect.rememberSweetSelectState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    state: MainState,
    onNavigate: (Screen) -> Unit,
    onNavigateBack: () -> Unit,
    onHandlePlayerAction: (PlayerActions) -> Unit
) {
    val lazyState = rememberLazyListState()
    val multiSelectState = rememberSweetSelectState<CuteTrack>()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.favorite)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(painterResource(R.drawable.back), contentDescription = null)
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            state = lazyState,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                top = paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding() + 72.dp
            ),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "toolbar") {
                LibraryListToolbar(
                    trackCount = state.favoriteTracks.size,
                    onShuffle = {
                        onHandlePlayerAction(
                            PlayerActions.PlayFromSource(
                                mediaId = null,
                                source = PlaySource.ExplicitTracks(state.favoriteTracks)
                            )
                        )
                    },
                    onQueue = { onNavigate(Screen.Queue) }
                )
            }
            if (state.favoriteTracks.isEmpty()) {
                item(key = "empty") {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(stringResource(R.string.no_fav_tracks))
                    }
                }
            } else {
                trackItems(
                    tracks = state.favoriteTracks,
                    multiSelectState = multiSelectState,
                    activeTrackId = "",
                    source = PlaySource.ExplicitTracks(state.favoriteTracks),
                    showAddToQueueButton = true,
                    onNavigate = onNavigate,
                    onHandlePlayerAction = onHandlePlayerAction
                )
            }
        }
    }
}
