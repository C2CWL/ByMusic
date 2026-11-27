@file:OptIn(
    ExperimentalSharedTransitionApi::class, ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class
)

package com.sosauce.chocola.feature.playing.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.skydoves.cloudy.cloudy
import com.sosauce.chocola.R
import com.sosauce.chocola.core.designsystem.ArtworkShape
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.domain.player.PlayerActions
import com.sosauce.chocola.core.presentation.preferences.rememberArtLyrics
import com.sosauce.chocola.core.presentation.preferences.rememberArtworkShape
import com.sosauce.chocola.core.presentation.preferences.rememberCarousel
import com.sosauce.chocola.core.presentation.preferences.rememberIsLandscape
import com.sosauce.chocola.core.presentation.preferences.rememberNowPlayingShapeMorph
import com.sosauce.chocola.feature.lyrics.presentation.LyricsList
import com.sosauce.nekobites.animations.rememberAnimatedShape
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapLatest

@Composable
private fun TurntableArtwork(
    musicState: MusicState,
    showLyrics: Boolean,
    onHandlePlayerActions: (PlayerActions) -> Unit
) {
    // Lyrics on the cover, turntable look: the record fades out and shrinks a
    // little while the lyrics fade in, all within 300ms; no 1000ms blur.
    val discAlpha by animateFloatAsState(
        targetValue = if (showLyrics) 0f else 1f,
        animationSpec = tween(TURNTABLE_ART_LYRICS_MS)
    )
    val discScale by animateFloatAsState(
        targetValue = if (showLyrics) TURNTABLE_ART_LYRICS_DISC_SCALE else 1f,
        animationSpec = tween(TURNTABLE_ART_LYRICS_MS)
    )
    // The expand overlay's lyrics layer draws its own flying copy of the record.
    val recordHidden = LocalTurntableRecordHidden.current
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = if (recordHidden) 0f else discAlpha
                    scaleX = discScale
                    scaleY = discScale
                },
            contentAlignment = Alignment.Center
        ) {
            TurntableDisc(musicState = musicState, blur = 0)
        }
        AnimatedVisibility(
            visible = showLyrics,
            enter = fadeIn(tween(TURNTABLE_ART_LYRICS_MS)),
            exit = fadeOut(tween(TURNTABLE_ART_LYRICS_MS))
        ) {
            LyricsList(
                textShadow = true,
                musicState = musicState,
                onHandlePlayerActions = onHandlePlayerActions,
                emptyLyrics = {
                    Text(
                        text = stringResource(R.string.no_lyrics_note),
                        style = MaterialTheme.typography.titleLargeEmphasized.copy(
                            shadow = Shadow(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                offset = Offset(10f, 5f),
                                blurRadius = 10f
                            )
                        )
                    )
                }
            )
        }
    }
}

/** The root sheet supplies artwork while the normal layout measures its slot. */
internal val LocalArtworkSlot = staticCompositionLocalOf<(@Composable (Modifier) -> Unit)?> { null }

@Composable
fun Artwork(
    modifier: Modifier = Modifier,
    musicState: MusicState,
    onHandlePlayerActions: (PlayerActions) -> Unit,
) {
    val isLandscape = rememberIsLandscape()
    val slotModifier = if (isLandscape) {
        modifier.fillMaxWidth(0.4f).aspectRatio(1f)
    } else {
        modifier.layout { measurable, constraints ->
            val height = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.maxWidth
            val side = minOf(constraints.maxWidth, height).coerceAtLeast(0)
            val placeable = measurable.measure(Constraints.fixed(side, side))
            layout(side, side) { placeable.place(0, 0) }
        }
    }
    val slot = LocalArtworkSlot.current
    if (slot != null) {
        slot(slotModifier)
        return
    }

    val useCarousel by rememberCarousel()
    var artworkShape by rememberArtworkShape()
    val artLyrics by rememberArtLyrics()
    val shapeMorph by rememberNowPlayingShapeMorph()
    val useTurntable = rememberTurntableEnabled()

    var showLyrics by remember { mutableStateOf(false) }
    val blur by animateIntAsState(
        targetValue = if (showLyrics) 100 else 0,
        animationSpec = tween(1000)
    )
    val scale by animateFloatAsState(
        targetValue = if (useTurntable || musicState.isPlaying) 1f else 0.9f
    )
    val animatedShape = if (shapeMorph) {
        rememberAnimatedShape(
            condition = musicState.isPlaying,
            shapeA = MaterialShapes.Circle,
            shapeB = ArtworkShape.toRoundedPolygon(artworkShape)
        )
    } else ArtworkShape.toShape(artworkShape)

    Box(
        modifier = slotModifier
            .clickable(
                enabled = artLyrics,
                indication = null,
                interactionSource = null,
                onClick = { showLyrics = !showLyrics }
            )
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        if (useTurntable) {
            TurntableArtwork(
                musicState = musicState,
                showLyrics = showLyrics,
                onHandlePlayerActions = onHandlePlayerActions
            )
        } else if (useCarousel) {

            val carouselState = rememberCarouselState(
                initialItem = musicState.mediaIndex
            ) { musicState.loadedMedias.size }

            val currentTrack by rememberUpdatedState(musicState.track)
            val loadedMedias by rememberUpdatedState(musicState.loadedMedias)

            LaunchedEffect(musicState.loadedMedias, musicState.track.mediaId) {
                val target =
                    musicState.loadedMedias.indexOfFirst { it.mediaId == musicState.track.mediaId }
                if (target != -1 && carouselState.currentItem != target) {
                    carouselState.animateScrollToItem(target)
                }
            }

            LaunchedEffect(carouselState) {
                snapshotFlow { carouselState.isScrollInProgress }
                    .filter { !it }
                    .mapLatest { carouselState.currentItem }
                    .distinctUntilChanged()
                    .collectLatest { settled ->
                        val track = loadedMedias.getOrNull(settled) ?: return@collectLatest
                        if (track.mediaId != currentTrack.mediaId) {
                            onHandlePlayerActions(PlayerActions.PlayTrack(track))
                        }
                    }
            }

            HorizontalCenteredHeroCarousel(
                state = carouselState,
                itemSpacing = 5.dp,
            ) { page ->
                Box(
                    modifier = Modifier
                        .maskClip(MaterialTheme.shapes.extraLarge)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.music_note),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize(0.4f)
                            .cloudy(
                                radius = blur,
                                enabled = carouselState.currentItem == page && blur > 0
                            ),
                        tint = contentColorFor(MaterialTheme.colorScheme.surfaceContainer)

                    )
                    AsyncImage(
                        model = musicState.loadedMedias[page].artUri,
                        contentDescription = stringResource(R.string.artwork),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .cloudy(
                                radius = blur,
                                enabled = carouselState.currentItem == page && blur > 0
                            )
                    )
                    AnimatedVisibility(
                        visible = carouselState.currentItem == page && showLyrics,
                        enter = slideInVertically { it } + fadeIn(),
                        exit = slideOutVertically { it } + fadeOut(),
                    ) {
                        LyricsList(
                            textShadow = true,
                            musicState = musicState,
                            onHandlePlayerActions = onHandlePlayerActions,
                            emptyLyrics = {
                                Text(
                                    text = stringResource(R.string.no_lyrics_note),
                                    style = MaterialTheme.typography.titleLargeEmphasized.copy(
                                        shadow = Shadow(
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            offset = Offset(10f, 5f),
                                            blurRadius = 10f
                                        )
                                    )
                                )
                            }
                        )
                    }
                }

            }

        } else {

            Box(
                modifier = Modifier
                    .clip(animatedShape)
                    .background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize(0.4f)
                        .cloudy(
                            radius = blur,
                            enabled = blur > 0
                        ),
                    tint = contentColorFor(MaterialTheme.colorScheme.surfaceContainer)

                )
                AsyncImage(
                    model = musicState.track.artUri,
                    contentDescription = stringResource(R.string.artwork),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .cloudy(
                            radius = blur,
                            enabled = blur > 0
                        )
                )
                AnimatedVisibility(
                    visible = showLyrics,
                    enter = slideInVertically { it } + fadeIn(),
                    exit = slideOutVertically { it } + fadeOut()
                ) {
                    LyricsList(
                        textShadow = true,
                        musicState = musicState,
                        onHandlePlayerActions = onHandlePlayerActions,
                        emptyLyrics = {
                            Text(
                                text = stringResource(R.string.no_lyrics_note),
                                style = MaterialTheme.typography.titleLargeEmphasized.copy(
                                    shadow = Shadow(
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        offset = Offset(10f, 5f),
                                        blurRadius = 10f
                                    )
                                )
                            )
                        }
                    )
                }
            }
        }

    }

}
