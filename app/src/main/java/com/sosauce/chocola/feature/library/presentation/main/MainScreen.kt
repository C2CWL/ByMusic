@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sosauce.chocola.feature.library.presentation.main

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Icon
import com.sosauce.chocola.feature.library.presentation.main.components.LibraryHeader
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import com.sosauce.chocola.R
import com.sosauce.chocola.core.designsystem.components.NoResult
import com.sosauce.chocola.core.designsystem.components.SelectOverlayItem
import com.sosauce.chocola.core.domain.model.CuteTrack
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.domain.player.PlaySource
import com.sosauce.chocola.core.domain.player.PlayerActions
import com.sosauce.chocola.core.presentation.components.CuteSearchbar
import com.sosauce.chocola.core.presentation.components.CuteSearchbarDefaults
import com.sosauce.chocola.core.presentation.components.TracksSelectedBar
import com.sosauce.chocola.core.presentation.components.trackItems
import com.sosauce.chocola.core.presentation.components.trackSelectActions
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.chocola.core.presentation.util.barsContentTransform
import com.sosauce.chocola.core.presentation.util.selfAlignHorizontally
import com.sosauce.chocola.feature.library.presentation.main.components.FavoriteTrackItem
import com.sosauce.chocola.feature.library.presentation.main.components.FolderHeader
import com.sosauce.nekobites.components.LoadingBox
import com.sosauce.nekobites.components.NoXFound
import com.sosauce.nekobites.components.Spacer
import com.sosauce.sweetselect.rememberSweetSelectState

// https://medium.com/@gregkorossy/hacking-lazylist-in-android-jetpack-compose-38afacb3df67
@Composable
fun SharedTransitionScope.MainScreen(
    state: MainState,
    musicState: MusicState,
    textFieldState: TextFieldState,
    onNavigate: (Screen) -> Unit,
    onHandlePlayerAction: (PlayerActions) -> Unit,
    onFolderToggle: (String) -> Unit
) {

    val lazyState = rememberLazyListState()
    val multiSelectState = rememberSweetSelectState<CuteTrack>()
    val activeTrackId = remember(musicState.track) { musicState.track.mediaId }

    val groupByFolders = state.groupByFolders
    val backdrop = rememberGraphicsLayer()
    val favoriteListState = rememberLazyListState()
    var locateTrackId by remember { mutableStateOf<String?>(null) }

    // Wait for search clearing and folder expansion to reach the displayed state.
    LaunchedEffect(locateTrackId, state, textFieldState.text.toString()) {
        val id = locateTrackId ?: return@LaunchedEffect
        if (state.isLoading || textFieldState.text.isNotEmpty() || state.searchQuery.isNotEmpty()) {
            return@LaunchedEffect
        }
        val favoriteIndex = state.favoriteTracks.indexOfFirst { it.mediaId == id }
        if (favoriteIndex >= 0) {
            lazyState.animateScrollToItem(0)
            favoriteListState.animateScrollToItem(favoriteIndex)
            locateTrackId = null
        } else {
            var index = if (state.favoriteTracks.isEmpty()) 0 else 1
            var targetIndex = -1
            if (groupByFolders) {
                for (category in state.categories) {
                    index++
                    val trackIndex = category.tracks.indexOfFirst { it.mediaId == id }
                    if (trackIndex >= 0 && category.isHidden) {
                        onFolderToggle(category.path)
                        return@LaunchedEffect
                    }
                    if (!category.isHidden) {
                        if (trackIndex >= 0) targetIndex = index + trackIndex
                        index += category.tracks.size
                    }
                }
            } else {
                val trackIndex = state.nonFavoriteTracks.indexOfFirst { it.mediaId == id }
                if (trackIndex >= 0) targetIndex = index + trackIndex
            }
            if (targetIndex >= 0) {
                lazyState.animateScrollToItem(targetIndex)
            }
            locateTrackId = null
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        floatingActionButton = {
            if (musicState.isPlayerReady && !multiSelectState.isInSelectionMode && activeTrackId.isNotEmpty()) {
                SmallFloatingActionButton(onClick = {
                    textFieldState.edit { replace(0, length, "") }
                    locateTrackId = activeTrackId
                }, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Icon(painterResource(R.drawable.locate_track), stringResource(R.string.locate_current_track))
                }
            }
        },
        bottomBar = {
            AnimatedContent(
                targetState = multiSelectState.isInSelectionMode,
                transitionSpec = { barsContentTransform },
            ) { isInSelectionMode ->
                if (isInSelectionMode) {
                    TracksSelectedBar(
                        modifier = Modifier.selfAlignHorizontally(),
                        tracks = state.tracks,
                        multiSelectState = multiSelectState,
                        onHandlePlayerActions = onHandlePlayerAction
                    )
                } else {
                    CuteSearchbar(
                        modifier = Modifier.selfAlignHorizontally(),
                        libraryStyle = true,
                        musicState = musicState,
                        textFieldState = textFieldState,
                        onHandlePlayerActions = onHandlePlayerAction,
                        onNavigate = onNavigate,
                        sortMenu = {
                            CuteSearchbarDefaults.TrackSortPopupContent()
                        }
                    )
                }
            }
        }
    ) { paddingValues ->

        Box(Modifier.fillMaxSize()) {
            LoadingBox(isLoading = state.isLoading) {
                LazyColumn(
                    modifier = Modifier.drawWithContent {
                        backdrop.record { this@drawWithContent.drawContent() }
                        drawLayer(backdrop)
                    },
                    state = lazyState,
                    contentPadding = PaddingValues(
                        top = paddingValues.calculateTopPadding() + 104.dp,
                        bottom = paddingValues.calculateBottomPadding() + 72.dp
                    )
                ) {
                    if (state.favoriteTracks.isNotEmpty()) {
                        item(key = "favorites") {
                            LazyRow(
                                state = favoriteListState,
                                modifier = Modifier
                                    .animateItem()
                                    .fillMaxWidth(),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(
                                    items = state.favoriteTracks,
                                    key = { it.mediaId }
                                ) { track ->
                                    val isSelected by multiSelectState.isSelectedAsState(track)

                                    SelectOverlayItem(
                                        modifier = Modifier.animateItem(),
                                        enabled = !multiSelectState.isInSelectionMode,
                                        actions = trackSelectActions(
                                            track = track,
                                            onNavigate = onNavigate,
                                            onHandlePlayerAction = onHandlePlayerAction
                                        ),
                                        onSelect = { multiSelectState.addAll(listOf(track)) }
                                    ) { showOverlay ->
                                        FavoriteTrackItem(
                                            track = track,
                                            isSelected = isSelected,
                                            isInSelectionMode = multiSelectState.isInSelectionMode,
                                            onClick = {
                                                if (multiSelectState.isInSelectionMode) {
                                                    multiSelectState.toggle(track)
                                                } else {
                                                    onHandlePlayerAction(
                                                        PlayerActions.PlayFromSource(
                                                            mediaId = track.mediaId,
                                                            source = PlaySource.ExplicitTracks(state.favoriteTracks)
                                                        )
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                if (multiSelectState.isInSelectionMode) {
                                                    multiSelectState.toggle(track)
                                                } else {
                                                    showOverlay()
                                                }
                                            },
                                            onUnfavorite = {
                                                track.mediaId.toLongOrNull()?.let {
                                                    onHandlePlayerAction(
                                                        PlayerActions.ToggleFavorite(it)
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (state.tracks.isEmpty()) {
                        item(
                            key = "empty"
                        ) {
                            if (textFieldState.text.isEmpty()) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    NoXFound(
                                        modifier = Modifier.animateItem(),
                                        headlineText = R.string.no_music_title,
                                        bodyText = R.string.no_music_desc,
                                        icon = R.drawable.music_note
                                    )
                                    Spacer(15.dp)
                                    Text(
                                        text = stringResource(R.string.no_music_tip),
                                        style = MaterialTheme.typography.bodySmallEmphasized.copy(
                                            textAlign = TextAlign.Center,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            } else {
                                NoResult(modifier = Modifier.animateItem())
                            }

                        }
                    }


                    if (groupByFolders) {
                        state.categories.fastForEach { category ->
                            item(category.path) {
                                FolderHeader(
                                    modifier = Modifier.animateItem(),
                                    category = category,
                                    isHidden = category.isHidden,
                                    onToggleVisibility = {
                                        onFolderToggle(category.path)
                                    },
                                    onHandlePlayerAction = onHandlePlayerAction
                                )
                            }
                            if (!category.isHidden) {
                                trackItems(
                                    tracks = category.tracks,
                                    multiSelectState = multiSelectState,
                                    activeTrackId = activeTrackId,
                                    source = PlaySource.ExplicitTracks(category.tracks),
                                    onNavigate = onNavigate,
                                    onHandlePlayerAction = onHandlePlayerAction
                                )
                            }
                        }
                    } else {
                        trackItems(
                            tracks = state.nonFavoriteTracks,
                            multiSelectState = multiSelectState,
                            activeTrackId = activeTrackId,
                            source = PlaySource.All,
                            onNavigate = onNavigate,
                            onHandlePlayerAction = onHandlePlayerAction
                        )
                    }
                }
            }
            LibraryHeader(
                backdrop = backdrop,
                textFieldState = textFieldState,
                count = state.tracks.size,
                onNavigate = onNavigate,
                onShuffle = {
                    onHandlePlayerAction(PlayerActions.PlayFromSource(null, PlaySource.All))
                }
            )
        }
    }
}
