@file:OptIn(
    ExperimentalSharedTransitionApi::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class
)

package com.sosauce.chocola.core.presentation.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.RichTooltip
import androidx.compose.material3.SelectableDropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import coil3.compose.AsyncImage
import com.sosauce.chocola.R
import com.sosauce.chocola.core.designsystem.SharedTransitionKeys
import com.sosauce.chocola.core.designsystem.components.PlayPauseButton
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.domain.player.PlayerActions
import com.sosauce.chocola.core.presentation.navigation.LocalScreen
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.chocola.core.presentation.preferences.rememberAlbumGrids
import com.sosauce.chocola.core.presentation.preferences.rememberAlbumSort
import com.sosauce.chocola.core.presentation.preferences.rememberArtistSort
import com.sosauce.chocola.core.presentation.preferences.rememberGroupByFolders
import com.sosauce.chocola.core.presentation.preferences.rememberHasSeenTip
import com.sosauce.chocola.core.presentation.preferences.rememberMatchCaseFilter
import com.sosauce.chocola.core.presentation.preferences.rememberRegexFilter
import com.sosauce.chocola.core.presentation.preferences.rememberShowShuffleButton
import com.sosauce.chocola.core.presentation.preferences.rememberSortAlbumsAscending
import com.sosauce.chocola.core.presentation.preferences.rememberSortArtistsAscending
import com.sosauce.chocola.core.presentation.preferences.rememberSortTracksAscending
import com.sosauce.chocola.core.presentation.preferences.rememberTrackSort
import com.sosauce.chocola.feature.playing.presentation.expand.PlayerExpandMath
import com.sosauce.chocola.feature.playing.presentation.expand.PlayerExpandOverlay
import com.sosauce.chocola.feature.playing.presentation.expand.rememberPlayerExpandController
import com.sosauce.chocola.feature.playing.presentation.expand.systemAnimationsDisabled
import com.sosauce.nekobites.animations.AnimatedDrawable
import com.sosauce.nekobites.animations.AnimatedDrawableFile
import com.sosauce.nekobites.animations.AnimatedFab
import com.sosauce.nekobites.animations.bouncySpec
import com.sosauce.nekobites.components.Spacer
import com.sosauce.nekobites.helpers.rememberInteractionSource
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun SharedTransitionScope.CuteSearchbar(
    modifier: Modifier = Modifier,
    musicState: MusicState,
    textFieldState: TextFieldState,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit,
    libraryStyle: Boolean = false,
    onFullPlayerChanged: (Boolean) -> Unit = {},
    fab: @Composable (() -> Unit)? = null,
    backButton: @Composable (() -> Unit)? = null,
    sortMenu: @Composable () -> Unit
) {
    var showFullPlayerSaved by rememberSaveable { mutableStateOf(false) }
    val controller = rememberPlayerExpandController(initiallyExpanded = showFullPlayerSaved)
    controller.reduceMotion = systemAnimationsDisabled()
    val progress = controller.progress
    val windowInfo = LocalWindowInfo.current

    LaunchedEffect(progress) {
        if (PlayerExpandMath.isSettledCollapsed(progress)) {
            showFullPlayerSaved = false
        } else if (PlayerExpandMath.isSettledExpanded(progress)) {
            showFullPlayerSaved = true
        }
    }
    LaunchedEffect(progress > 0f) {
        onFullPlayerChanged(progress > 0f)
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

    BackHandler(enabled = progress > 0f) {
        controller.collapse()
    }

    // Keep the mini player mounted (even when fully expanded, visually hidden)
    // so the scaffold bottom bar does not jump and mini bounds stay valid.
    Box(
        modifier = modifier.graphicsLayer {
            alpha = if (PlayerExpandMath.isSettledExpanded(progress)) 0f else 1f
        }
    ) {
        CuteSearchbarContent(
            modifier = Modifier,
            musicState = musicState,
            textFieldState = textFieldState,
            onHandlePlayerActions = onHandlePlayerActions,
            onOpenFullPlayer = { controller.open() },
            onInteractiveExpandDelta = { deltaFraction ->
                controller.onDragDelta(deltaFraction)
            },
            onInteractiveExpandSettled = { velocity ->
                controller.onDragSettled(velocity)
            },
            expandProgress = progress,
            miniArtAlpha = if (progress > 0f && PlayerExpandMath.boundsReady(
                controller.miniContainer, controller.miniArt, controller.fullArt
            )) 0f else 1f,
            onMiniCardBoundsChanged = { controller.onMiniContainer(it) },
            onMiniArtBoundsChanged = { controller.onMiniArt(it) },
            sortMenu = sortMenu,
            onNavigate = onNavigate,
            libraryStyle = libraryStyle,
            fab = fab,
            backButton = backButton
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


@Composable
private fun SharedTransitionScope.CuteSearchbarContent(
    modifier: Modifier = Modifier,
    musicState: MusicState,
    textFieldState: TextFieldState,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit,
    onOpenFullPlayer: () -> Unit,
    onInteractiveExpandDelta: (Float) -> Unit = {},
    onInteractiveExpandSettled: (Float) -> Unit = {},
    expandProgress: Float = 0f,
    miniArtAlpha: Float = 1f,
    onMiniCardBoundsChanged: (Rect) -> Unit = {},
    onMiniArtBoundsChanged: (Rect) -> Unit = {},
    libraryStyle: Boolean = false,
    fab: @Composable (() -> Unit)? = null,
    backButton: @Composable (() -> Unit)? = null,
    sortMenu: @Composable () -> Unit
) {

    if (libraryStyle) {
        LibraryMiniPlayer(
            musicState = musicState,
            onHandlePlayerActions = onHandlePlayerActions,
            onNavigate = onNavigate,
            onOpenFullPlayer = onOpenFullPlayer,
            miniArtAlpha = miniArtAlpha,
            onMiniCardBoundsChanged = onMiniCardBoundsChanged,
            onMiniArtBoundsChanged = onMiniArtBoundsChanged
        )
        return
    }

    val windowInfo = LocalWindowInfo.current
    val currentScreen = LocalScreen.current
    val scope = rememberCoroutineScope()
    val screenHeightPx = windowInfo.containerSize.height.toFloat().coerceAtLeast(1f)
    val oneFourthOfHeight = screenHeightPx / 4f
    val yTranslationSearchbar = remember { Animatable(0f) }
    var isInScreenSelectionMode by rememberSaveable { mutableStateOf(false) }
    val showFab by rememberShowShuffleButton()


    val searchbarDragState = rememberDraggableState { dragAmount ->
        // When dragging upward (dragAmount < 0) or already expanding, drive the interactive shared expansion directly
        if (expandProgress > 0f || (dragAmount < 0f && yTranslationSearchbar.value <= 0f)) {
            onInteractiveExpandDelta(-dragAmount / screenHeightPx)
        } else {
            scope.launch {
                val newValue = (yTranslationSearchbar.value + dragAmount).coerceAtLeast(0f)
                yTranslationSearchbar.snapTo(newValue)
            }
        }
    }
    // force 24.dp when player isn't ready because controls won't be here
    val animatedRadius by animateDpAsState(
        if (isInScreenSelectionMode || !musicState.isPlayerReady) 24.dp else 0.dp
    )


    Column(
        modifier = modifier
            // Keep the measured landing bounds fixed while the overlay animates.
            .fillMaxWidth(0.85f)
            .navigationBarsPadding()
            .imePadding()
            .draggable(
                state = searchbarDragState,
                orientation = Orientation.Vertical,
                enabled = musicState.isPlayerReady && !isInScreenSelectionMode,
                onDragStopped = { velocity ->
                    if (expandProgress > 0f) {
                        onInteractiveExpandSettled(velocity)
                    } else {
                        val targetValue = yTranslationSearchbar.value
                        if (targetValue > 0f) {
                            onHandlePlayerActions(PlayerActions.StopPlayback)
                        }
                        scope.launch {
                            yTranslationSearchbar.animateTo(0f, bouncySpec())
                        }
                    }
                }
            )
            .graphicsLayer {
                translationY = yTranslationSearchbar.value
            }
    ) {
        // FABs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp)
        ) {
            backButton?.invoke()
            Spacer(Modifier.weight(1f))
            if (showFab || currentScreen == Screen.Playlists) {
                fab?.invoke()
            }
        }
        // Unified mini-player + searchbar container measured for shared expansion bounds
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    onMiniCardBoundsChanged(coords.boundsInWindow())
                }
        ) {
            // Controls
            AnimatedVisibility(
                visible = musicState.isPlayerReady,
                enter = fadeIn() + slideInVertically { it }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(
                            shape = RoundedCornerShape(
                                topStart = 24.dp,
                                topEnd = 24.dp,
                                bottomStart = animatedRadius,
                                bottomEnd = animatedRadius
                            )
                        )
                        .background(
                            color = MaterialTheme.colorScheme.surfaceContainer
                        )
                        .clickable(
                            enabled = musicState.isPlayerReady,
                            onClick = onOpenFullPlayer,
                            indication = null,
                            interactionSource = null
                        )
                        .drawMusicPosition(
                            position = { musicState.position.toFloat() },
                            trackDuration = { musicState.duration },
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp)
                            .size(42.dp)
                            .onGloballyPositioned { coords ->
                                onMiniArtBoundsChanged(coords.boundsInWindow())
                            }
                            .graphicsLayer { alpha = miniArtAlpha }
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.music_note),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = contentColorFor(MaterialTheme.colorScheme.surfaceContainerHigh)
                        )
                        AsyncImage(
                            model = musicState.track.artUri,
                            contentDescription = stringResource(R.string.artwork),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    AnimatedContent(
                        targetState = musicState.track.title,
                        transitionSpec = { slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut() },
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .weight(1f)
                    ) {
                        Text(
                            text = it,
                            modifier = Modifier.basicMarquee()
                        )
                    }
                    Row {
                        IconButton(
                            onClick = { onHandlePlayerActions(PlayerActions.SeekToPreviousMusic) },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.skip_previous),
                                contentDescription = null
                            )
                        }
                        PlayPauseButton(
                            isPlaying = musicState.isPlaying,
                            onHandlePlayerActions = onHandlePlayerActions
                        )
                        IconButton(
                            onClick = { onHandlePlayerActions(PlayerActions.SeekToNextMusic) },
                            shapes = IconButtonDefaults.shapes()
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.skip_next),
                                contentDescription = null
                            )
                        }
                    }
                }
            }
            // Bottom content

            val spacerHeight by animateDpAsState(
                targetValue = if (isInScreenSelectionMode) 10.dp else 0.dp,
                animationSpec = bouncySpec()
            )

            if (musicState.isPlayerReady) { // To avoid FAB bounce when no controls
                Spacer(spacerHeight)
            }
            Box(
                modifier = Modifier
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(SharedTransitionKeys.SEARCHBAR),
                        animatedVisibilityScope = LocalNavAnimatedContentScope.current
                    )
                    .fillMaxWidth()
                    .clip(
                        shape = RoundedCornerShape(
                            topStart = animatedRadius,
                            topEnd = animatedRadius,
                            bottomStart = 24.dp,
                            bottomEnd = 24.dp
                        )
                    )
                    .background(
                        color = MaterialTheme.colorScheme.surfaceContainer
                    )
                    .drawMusicPosition(
                        position = { musicState.position.toFloat() },
                        trackDuration = { musicState.duration },
                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
            ) {
                SharedTransitionLayout {
                    AnimatedContent(
                        targetState = isInScreenSelectionMode,
                        transitionSpec = {
                            slideInVertically { it } + fadeIn() togetherWith
                                    slideOutVertically { it } + fadeOut()
                        }
                    ) {
                        CompositionLocalProvider(LocalNavAnimatedContentScope provides this) {
                            if (it) {
                                ScreenSelection(
                                    onNavigate = onNavigate,
                                    dismiss = { isInScreenSelectionMode = false }
                                )
                            } else {
                                CuteSearchbarDefaults.CuteSearchbarTextField(
                                    state = textFieldState,
                                    onNavigate = onNavigate,
                                    onSwitchToScreenSelection = { isInScreenSelectionMode = true },
                                    sortingMenuPopupContent = sortMenu
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedTransitionScope.ScreenSelection(
    onNavigate: (Screen) -> Unit,
    dismiss: () -> Unit
) {

    val currentScreen = LocalScreen.current
    val haptic = LocalHapticFeedback.current
    val screens = listOf(
        ScreenCategory(
            screen = Screen.Main,
            onClick = { onNavigate(Screen.Main) },
            unselectedIcon = R.drawable.music_note,
            selectedIcon = R.drawable.music_note,
            name = R.string.tracks
        ),
        ScreenCategory(
            screen = Screen.Albums,
            onClick = { onNavigate(Screen.Albums) },
            unselectedIcon = androidx.media3.session.R.drawable.media3_icon_album,
            selectedIcon = R.drawable.album_filled,
            name = R.string.albums
        ),
        ScreenCategory(
            screen = Screen.Artists,
            onClick = { onNavigate(Screen.Artists) },
            unselectedIcon = R.drawable.artist_rounded,
            selectedIcon = R.drawable.artists_filled,
            name = R.string.artists
        ),
        ScreenCategory(
            screen = Screen.Playlists,
            onClick = { onNavigate(Screen.Playlists) },
            unselectedIcon = R.drawable.queue_music_rounded,
            selectedIcon = R.drawable.queue_music_rounded,
            name = R.string.playlists
        )
    )

    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme

    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(6.dp)
            .height(TextFieldDefaults.MinHeight)
    ) {
        screens.forEach { screen ->
            val selected = currentScreen == screen.screen
            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()

            val weight by animateFloatAsState(
                targetValue = when {
                    selected -> 2.2f
                    isPressed -> 1.5f
                    else -> 1f
                },
                animationSpec = motion.fastSpatialSpec()
            )
            val corner by animateDpAsState(
                targetValue = when {
                    isPressed -> 12.dp
                    selected -> TextFieldDefaults.MinHeight / 2
                    else -> 18.dp
                },
                animationSpec = motion.fastSpatialSpec()
            )
            val containerColor by animateColorAsState(
                if (selected) colors.primary else colors.surfaceContainerHighest
            )
            val contentColor by animateColorAsState(
                if (selected) colors.onPrimary else colors.onSurfaceVariant
            )

            Surface(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                    dismiss()
                    screen.onClick()
                },
                shape = RoundedCornerShape(corner),
                color = containerColor,
                contentColor = contentColor,
                interactionSource = interactionSource,
                modifier = Modifier
                    .weight(weight)
                    .fillMaxHeight()
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    val icon = if (selected) screen.selectedIcon else screen.unselectedIcon
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = stringResource(screen.name),
                        modifier = Modifier
                            .sharedElement(
                                sharedContentState = rememberSharedContentState(icon),
                                animatedVisibilityScope = LocalNavAnimatedContentScope.current
                            )
                    )
                    if (selected) {
                        Spacer(8.dp)
                        Text(
                            text = stringResource(screen.name),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

private data class ScreenCategory(
    val screen: Screen,
    val onClick: () -> Unit,
    val name: Int,
    val unselectedIcon: Int,
    val selectedIcon: Int
)


private fun Modifier.drawMusicPosition(
    position: () -> Float,
    trackDuration: () -> Long,
    color: Color
): Modifier = this.drawWithCache {
    onDrawBehind {
        val duration = trackDuration()
        val fraction = if (duration <= 0L) {
            0f
        } else {
            (position() / duration.toFloat()).coerceIn(0f, 1f)
        }
        val drawWidth = size.width * fraction
        drawRect(
            color = color,
            size = Size(drawWidth, size.height)
        )
    }
}


object CuteSearchbarDefaults {

    @Composable
    fun BackButton(
        onNavigateBack: () -> Unit
    ) {
        AnimatedFab(
            onClick = onNavigateBack,
            icon = R.drawable.back,
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    }

    @Composable
    context(sharedTransitionScope: SharedTransitionScope)
    fun CuteSearchbarTextField(
        state: TextFieldState,
        onNavigate: (Screen) -> Unit,
        sortingMenuPopupContent: @Composable () -> Unit,
        onSwitchToScreenSelection: () -> Unit
    ) {

        var hasSeenTip by rememberHasSeenTip()
        var sortMenuExpanded by remember { mutableStateOf(false) }

        val screenToLeadingIcon = mapOf(
            Screen.Main to R.drawable.music_note,
            Screen.Albums to R.drawable.album_filled,
            Screen.Artists to R.drawable.artists_filled,
            Screen.Playlists to R.drawable.queue_music_rounded,
        )
        val currentScreen = LocalScreen.current
        val isSearching = state.text.isNotEmpty()



        with(sharedTransitionScope) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .padding(6.dp)
            ) {
                TextField(
                    state = state,
                    modifier = Modifier
                        .fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    placeholder = {
                        Text(
                            text = stringResource(R.string.search_here),
                            maxLines = 1
                        )
                    },
                    leadingIcon = {
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                TooltipAnchorPosition.Above
                            ),
                            tooltip = {
                                RichTooltip(
                                    caretShape = TooltipDefaults.caretShape(),
                                    colors = TooltipDefaults.richTooltipColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                                        contentColor = contentColorFor(
                                            MaterialTheme.colorScheme.primaryContainer
                                        )
                                    ),
                                ) { Text(stringResource(R.string.click_hint)) }
                            },
                            state = rememberTooltipState(
                                initialIsVisible = !hasSeenTip,
                                isPersistent = !hasSeenTip
                            )
                        ) {
                            AnimatedContent(
                                targetState = isSearching
                            ) {
                                if (it) {
                                    Icon(
                                        painter = painterResource(R.drawable.search),
                                        contentDescription = null
                                    )
                                } else {

                                    val icon = screenToLeadingIcon[currentScreen]

                                    IconButton(
                                        onClick = {
                                            onSwitchToScreenSelection()
                                            hasSeenTip = true
                                        },
                                        shapes = IconButtonDefaults.shapes()
                                    ) {
                                        Icon(
                                            painter = painterResource(icon ?: R.drawable.search),
                                            modifier = Modifier
                                                .sharedElement(
                                                    sharedContentState = rememberSharedContentState(
                                                        icon ?: R.drawable.search
                                                    ),
                                                    animatedVisibilityScope = LocalNavAnimatedContentScope.current
                                                ),
                                            contentDescription = null
                                        )
                                    }
                                }
                            }

                        }
                    },
                    trailingIcon = {
                        AnimatedContent(
                            targetState = isSearching
                        ) {
                            if (it) {
                                IconButton(
                                    onClick = state::clearText,
                                    shapes = IconButtonDefaults.shapes()
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.cancel_filled),
                                        contentDescription = null
                                    )
                                }
                            } else {
                                Row {
                                    Box {
                                        IconButton(
                                            onClick = { sortMenuExpanded = !sortMenuExpanded },
                                            shapes = IconButtonDefaults.shapes()
                                        ) {
                                            AnimatedDrawable(
                                                drawable = AnimatedDrawableFile.SORT,
                                                atEnd = sortMenuExpanded
                                            )
                                        }
                                        DropdownMenuPopup(
                                            expanded = sortMenuExpanded,
                                            onDismissRequest = { sortMenuExpanded = false }
                                        ) {
                                            sortingMenuPopupContent()
                                        }
                                    }
                                    IconButton(
                                        onClick = { onNavigate(Screen.Settings()) },
                                        shapes = IconButtonDefaults.shapes()
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.settings_filled),
                                            contentDescription = stringResource(R.string.settings)
                                        )
                                    }
                                }
                            }
                        }
                    },
                    lineLimits = TextFieldLineLimits.SingleLine,
                    shape = FloatingToolbarDefaults.ContainerShape
                )
            }
        }

    }


    @Composable
    fun AlbumSortPopupContent() {

        var numberOfAlbumGrids by rememberAlbumGrids()
        var isSortedByASC by rememberSortAlbumsAscending()
        var albumSort by rememberAlbumSort()

        DropdownMenuGroup(
            shapes = MenuDefaults.groupShape(0, 2)
        ) {
            DropdownMenuItem(
                onClick = {
                    numberOfAlbumGrids =
                        if (numberOfAlbumGrids == 4) 2 else numberOfAlbumGrids + 1
                },
                text = { Text(stringResource(R.string.no_of_grids)) },
                trailingContent = { Text("$numberOfAlbumGrids") },
                shape = MenuDefaults.leadingItemShape
            )
        }
        Spacer(Modifier.height(MenuDefaults.GroupSpacing))
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShape(1, 2),
            content = {
                repeat(2) { i ->
                    val text = when (i) {
                        0 -> R.string.name
                        1 -> R.string.artist
                        else -> throw IndexOutOfBoundsException()
                    }
                    SelectableDropdownMenuItem(
                        selected = albumSort == i,
                        onClick = { albumSort = i },
                        shapes = MenuDefaults.itemShape(i, 2),
                        colors = MenuDefaults.selectableItemColors(),
                        text = { Text(stringResource(text)) }
                    )
                }
            }
        )
        Spacer(Modifier.height(MenuDefaults.GroupSpacing))
        SortingButtons(
            ascending = isSortedByASC,
            onChangeSort = { isSortedByASC = it }
        )
    }

    @Composable
    fun TrackSortPopupContent() {

        var trackSort by rememberTrackSort()
        var sortTracksAsc by rememberSortTracksAscending()
        var groupByFolders by rememberGroupByFolders()


        DropdownMenuGroup(
            shapes = MenuDefaults.groupShape(0, 2)
        ) {
            SelectableDropdownMenuItem(
                selected = groupByFolders,
                onClick = { groupByFolders = !groupByFolders },
                shapes = MenuDefaults.itemShape(0, 2),
                colors = MenuDefaults.selectableItemColors(),
                text = { Text(stringResource(R.string.group_tracks)) }
            )
        }
        Spacer(Modifier.height(MenuDefaults.GroupSpacing))
        DropdownMenuGroup(
            shapes = MenuDefaults.groupShape(1, 2),
            content = {
                repeat(5) { i ->
                    val text = when (i) {
                        0 -> R.string.title
                        1 -> R.string.artist
                        2 -> R.string.album
                        3 -> R.string.year
                        4 -> R.string.date_modified
                        else -> throw IndexOutOfBoundsException()
                    }

                    SelectableDropdownMenuItem(
                        selected = trackSort == i,
                        onClick = { trackSort = i },
                        shapes = MenuDefaults.itemShape(i, 5),
                        colors = MenuDefaults.selectableItemColors(),
                        text = { Text(stringResource(text)) }
                    )
                }
            }
        )
        Spacer(Modifier.height(MenuDefaults.GroupSpacing))
        SortingButtons(
            ascending = sortTracksAsc,
            onChangeSort = { sortTracksAsc = it }
        )
    }

    @Composable
    fun ArtistSortPopupContent() {

        var isSortedByASC by rememberSortArtistsAscending()
        var artistSort by rememberArtistSort()

        DropdownMenuGroup(
            shapes = MenuDefaults.groupShapes(),
            content = {
                repeat(3) { i ->
                    val text = when (i) {
                        0 -> R.string.name
                        1 -> R.string.number_of_tracks
                        2 -> R.string.number_of_albums
                        else -> throw IndexOutOfBoundsException()
                    }

                    SelectableDropdownMenuItem(
                        selected = artistSort == i,
                        onClick = { artistSort = i },
                        shapes = MenuDefaults.itemShape(i, 3),
                        colors = MenuDefaults.selectableItemColors(),
                        text = { Text(stringResource(text)) }
                    )
                }
            }
        )
        Spacer(Modifier.height(MenuDefaults.GroupSpacing))
        SortingButtons(
            ascending = isSortedByASC,
            onChangeSort = { isSortedByASC = it }
        )
    }

    @Composable
    fun SortingButtons(
        ascending: Boolean,
        onChangeSort: (Boolean) -> Unit
    ) {

        val interactionSources = List(2) { rememberInteractionSource() }

        ButtonGroup(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            overflowIndicator = {}
        ) {
            customItem(
                buttonGroupContent = {
                    ToggleButton(
                        checked = ascending,
                        onCheckedChange = { onChangeSort(true) },
                        interactionSource = interactionSources[0],
                        modifier = Modifier
                            .animateWidth(interactionSources[0])
                            .weight(1f)
                            .size(IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide)),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.up),
                            contentDescription = null,
                            modifier = Modifier.size(IconButtonDefaults.mediumIconSize)
                        )
                    }
                },
                menuContent = {}
            )

            customItem(
                buttonGroupContent = {
                    ToggleButton(
                        checked = !ascending,
                        onCheckedChange = { onChangeSort(false) },
                        interactionSource = interactionSources[1],
                        modifier = Modifier
                            .animateWidth(interactionSources[1])
                            .weight(1f)
                            .size(IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide)),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.down),
                            contentDescription = null,
                            modifier = Modifier.size(IconButtonDefaults.mediumIconSize)
                        )
                    }
                },
                menuContent = {}
            )
        }
        Spacer(Modifier.height(MenuDefaults.GroupSpacing))
        RegexButtons()
    }

    @Composable
    private fun RegexButtons() {

        var regexFilter by rememberRegexFilter()
        var matchCaseFilter by rememberMatchCaseFilter()
        val interactionSources = List(2) { rememberInteractionSource() }

        ButtonGroup(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            overflowIndicator = {}
        ) {
            customItem(
                buttonGroupContent = {
                    ToggleButton(
                        checked = regexFilter,
                        onCheckedChange = { regexFilter = !regexFilter },
                        interactionSource = interactionSources[0],
                        modifier = Modifier
                            .animateWidth(interactionSources[0])
                            .weight(1f)
                            .size(IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide)),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.regular_expression),
                            contentDescription = null,
                            modifier = Modifier.size(IconButtonDefaults.mediumIconSize)
                        )
                    }
                },
                menuContent = {}
            )

            customItem(
                buttonGroupContent = {
                    ToggleButton(
                        checked = matchCaseFilter,
                        onCheckedChange = { matchCaseFilter = !matchCaseFilter },
                        interactionSource = interactionSources[1],
                        modifier = Modifier
                            .animateWidth(interactionSources[1])
                            .weight(1f)
                            .size(IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide)),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.match_case),
                            contentDescription = null,
                            modifier = Modifier.size(IconButtonDefaults.mediumIconSize)
                        )
                    }
                },
                menuContent = {}
            )
        }
    }


}













@Composable
private fun LibraryMiniPlayer(
    musicState: MusicState,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit,
    onOpenFullPlayer: () -> Unit,
    miniArtAlpha: Float = 1f,
    onMiniCardBoundsChanged: (Rect) -> Unit = {},
    onMiniArtBoundsChanged: (Rect) -> Unit = {}
) {
    if (!musicState.isPlayerReady) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.onGloballyPositioned { coords ->
            onMiniCardBoundsChanged(coords.boundsInWindow())
        }
    ) {
        Column(Modifier.navigationBarsPadding()) {
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenFullPlayer)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .onGloballyPositioned { coords ->
                            onMiniArtBoundsChanged(coords.boundsInWindow())
                        }
                        .graphicsLayer { alpha = miniArtAlpha }
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.music_note),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = contentColorFor(MaterialTheme.colorScheme.surfaceContainerHigh)
                    )
                    AsyncImage(
                        model = musicState.track.artUri,
                        contentDescription = stringResource(R.string.artwork),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(musicState.track.title, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Default,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Normal))
                    Text(musicState.track.artist, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Default),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PlayPauseButton(isPlaying = musicState.isPlaying, onHandlePlayerActions = onHandlePlayerActions)
                IconButton(onClick = { onNavigate(Screen.Queue) }) {
                    Icon(painterResource(R.drawable.queue), stringResource(R.string.queue))
                }
            }
        }
    }
}
