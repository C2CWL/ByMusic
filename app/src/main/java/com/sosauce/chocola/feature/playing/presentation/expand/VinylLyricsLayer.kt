package com.sosauce.chocola.feature.playing.presentation.expand

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sosauce.chocola.R
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.domain.player.PlayerActions
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.chocola.feature.lyrics.presentation.LyricsLayout
import com.sosauce.chocola.feature.lyrics.presentation.LyricsPage
import com.sosauce.chocola.feature.lyrics.presentation.LyricsPageReveal
import com.sosauce.chocola.feature.playing.presentation.components.TurntableMotion
import com.sosauce.chocola.feature.playing.presentation.components.TurntableSharedState
import com.sosauce.chocola.feature.playing.presentation.components.drawTurntableArm
import com.sosauce.chocola.feature.playing.presentation.components.turntableHalo
import com.sosauce.chocola.feature.playing.presentation.components.turntableSheen
import com.sosauce.chocola.feature.playing.presentation.components.turntableVinyl
import kotlin.math.roundToInt

/**
 * The lyrics page as a layer of the expand overlay, over the settled turntable
 * page. [progress] 0 = turntable page, 1 = lyrics page. The record flies from
 * its place on the page into the header cover while the lyrics page fades in;
 * going back runs the same curves in reverse. The overlay draws the shared
 * opaque backdrop under the page, so no frame shows the library underneath.
 */
@Composable
internal fun VinylLyricsLayer(
    progress: Float,
    musicState: MusicState,
    fullArt: Rect,
    windowOrigin: Offset,
    rtl: Boolean,
    portraitVinyl: Boolean,
    turntable: TurntableSharedState,
    onClose: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onHandlePlayerActions: (PlayerActions) -> Unit
) {
    var headerArt by remember { mutableStateOf<Rect?>(null) }
    val p = progress.coerceIn(0f, 1f)
    val reveal = LyricsPageReveal(
        background = VinylLyricsMath.backgroundAlpha(p),
        header = VinylLyricsMath.headerAlpha(p),
        headerArtwork = if (VinylLyricsMath.landed(p)) 1f else 0f,
        lyrics = VinylLyricsMath.lyricsAlpha(p),
        lyricsOffsetDp = VinylLyricsMath.lyricsOffsetDp(p),
        toolbar = VinylLyricsMath.toolbarAlpha(p)
    )

    Box(
        Modifier
            .fillMaxSize()
            // Takes the hit test, so the hidden turntable page below never gets touches.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent()
                }
            }
    ) {
        LyricsPage(
            onNavigateBack = onClose,
            onNavigate = onNavigate,
            musicState = musicState,
            onHandlePlayerActions = onHandlePlayerActions,
            reveal = reveal,
            onHeaderArtworkBounds = { headerArt = Rect(it.left - windowOrigin.x, it.top - windowOrigin.y, it.right - windowOrigin.x, it.bottom - windowOrigin.y) }
        )
        if (!VinylLyricsMath.landed(p)) {
            VinylLyricsFlight(
                progress = p,
                musicState = musicState,
                fullArt = fullArt,
                headerArt = headerArt,
                rtl = rtl,
                portraitVinyl = portraitVinyl,
                turntable = turntable
            )
        }
    }
}

/** The record, drawn with the page's own pieces, on its way into the header. */
@Composable
private fun VinylLyricsFlight(
    progress: Float,
    musicState: MusicState,
    fullArt: Rect,
    headerArt: Rect?,
    rtl: Boolean,
    portraitVinyl: Boolean,
    turntable: TurntableSharedState
) {
    val density = LocalDensity.current
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val metal = if (portraitVinyl) accent else MaterialTheme.colorScheme.secondary

    val startLabel = VinylExpandMath.labelRectFor(fullArt, rtl)
    val cover = VinylLyricsMath.coverRect(progress, startLabel, headerArt ?: startLabel)
    val disc = VinylLyricsMath.discAround(cover)
    val artBox = VinylExpandMath.artBoxForDisc(disc, rtl)
    val degrees = VinylLyricsMath.coverDegrees(progress, turntable.rotation.value)
    val cornerPx = VinylLyricsMath.coverCornerPx(
        p = progress,
        sidePx = cover.width,
        endCornerPx = with(density) { LyricsLayout.HeaderArtworkCornerDp.dp.toPx() }
    )
    val haloAlpha = VinylLyricsMath.haloAlpha(progress)
    val discAlpha = VinylLyricsMath.discAlpha(progress)
    val armAlpha = VinylLyricsMath.armAlpha(progress)
    val armDegrees = VinylLyricsMath.armDegrees(
        p = progress,
        startDegrees = TurntableMotion.playPauseArmTarget(musicState.isPlaying),
        restDegrees = TurntableMotion.ARM_REST_DEGREES
    )

    Box(Modifier.fillMaxSize()) {
        if (haloAlpha > 0f) {
            Box(
                Modifier
                    .placeRect(artBox)
                    .graphicsLayer { alpha = haloAlpha }
                    .turntableHalo(surface, accent)
            )
        }
        if (discAlpha > 0f) {
            Box(
                Modifier
                    .placeRect(disc)
                    .graphicsLayer {
                        alpha = discAlpha
                        rotationZ = degrees
                    }
                    .clip(CircleShape)
                    .turntableVinyl(surface, accent, vivid = portraitVinyl)
            )
        }
        Box(
            modifier = Modifier
                .placeRect(cover)
                .graphicsLayer { rotationZ = degrees }
                .clip(RoundedCornerShape(cornerPx))
                .background(surface),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.music_note),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.fillMaxSize(0.35f)
            )
            AsyncImage(
                model = musicState.track.artUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (discAlpha > 0f) {
            // Over the cover, as on the page: the light does not turn with the record.
            Box(
                Modifier
                    .placeRect(disc)
                    .graphicsLayer { alpha = discAlpha }
                    .turntableSheen(surface, accent, turntable.glint)
            )
        }
        if (armAlpha > 0f) {
            Canvas(
                Modifier
                    .placeRect(artBox)
                    .graphicsLayer { alpha = armAlpha }
            ) { drawTurntableArm(armDegrees, surface, metal, wide = portraitVinyl) }
        }
    }
}

/** Places a child at [rect] (sheet-local coordinates). */
private fun Modifier.placeRect(rect: Rect): Modifier =
    layout { measurable, constraints ->
        val width = rect.width.coerceAtLeast(1f).roundToInt()
        val height = rect.height.coerceAtLeast(1f).roundToInt()
        val placeable = measurable.measure(Constraints.fixed(width, height))
        layout(constraints.minWidth, constraints.minHeight) {
            placeable.place(rect.left.roundToInt(), rect.top.roundToInt())
        }
    }
