package com.sosauce.chocola.feature.playing.presentation.components

import android.os.SystemClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.skydoves.cloudy.cloudy
import com.sosauce.chocola.R
import com.sosauce.chocola.core.domain.player.MusicState
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Cover swap while skipping (design value: 120ms). */
internal const val TURNTABLE_COVER_SWAP_MS = 120

@Composable
internal fun TurntableDisc(
    musicState: MusicState,
    blur: Int,
    drawLabel: Boolean = true,
    recordAlpha: () -> Float = { 1f },
    armAlpha: () -> Float = { 1f },
    armOffset: () -> Float = { 0f },
    rotationDegrees: ((Float) -> Float)? = null,
) {
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val metal = MaterialTheme.colorScheme.secondary
    // The sheet and its lyrics layer share the record angle and reflected light.
    val shared = LocalTurntableShared.current
    val rotation = shared?.rotation ?: remember { Animatable(0f) }
    val armAngle = remember {
        Animatable(TurntableMotion.playPauseArmTarget(musicState.isPlaying))
    }
    val glint = shared?.glint ?: remember { TurntableGlintClock() }
    var seenMediaId by remember { mutableStateOf(musicState.track.mediaId) }
    var lastCover by remember { mutableStateOf<Painter?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val motionPaused = LocalTurntableMotionPaused.current
    // "Remove animations" on: glints stay still (the sheen itself is static).
    val glintsStill = com.sosauce.chocola.feature.playing.presentation.expand.systemAnimationsDisabled()
    LaunchedEffect(musicState.isPlaying, lifecycle, motionPaused) {
        // Stop producing frames when the host lifecycle leaves the foreground.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (musicState.isPlaying && !motionPaused && !glintsStill) {
                while (true) {
                    val start = rotation.value % 360f
                    rotation.snapTo(start)
                    rotation.animateTo(start + 360f, tween(20000, easing = LinearEasing))
                }
            }
        }
    }
    LaunchedEffect(musicState.isPlaying, lifecycle, motionPaused) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (musicState.isPlaying && !motionPaused && !glintsStill) {
                var lastFrame = 0L
                while (true) {
                    withFrameNanos { now ->
                        val dt = if (lastFrame == 0L) 0f else ((now - lastFrame) / 1_000_000_000f)
                        lastFrame = now
                        // Glints only; read at draw time, so this redraws the sheen, no recomposition.
                        glint.advance(dt)
                    }
                }
            }
        }
    }
    // Tonearm: one Animatable chasing one target picked by [NeedleDirector].
    // A skip lifts it at once; it lands only after skipping settles, and the
    // short "buffering" not-playing blip of a track change is ignored.
    val playIntent = musicState.playWhenReady || musicState.isPlaying
    val needle = remember { NeedleDirector(musicState.isPlaying, playIntent) }
    val needleWake = remember { Channel<Unit>(Channel.CONFLATED) }
    val mediaId = musicState.track.mediaId
    LaunchedEffect(mediaId) {
        if (seenMediaId != mediaId) {
            seenMediaId = mediaId
            needle.onTrackChanged(SystemClock.uptimeMillis())
            needleWake.trySend(Unit)
        }
    }
    LaunchedEffect(musicState.isPlaying, playIntent) {
        needle.onPlayback(musicState.isPlaying, playIntent, SystemClock.uptimeMillis())
        needleWake.trySend(Unit)
    }
    LaunchedEffect(needle) {
        var armJob: Job? = null
        while (true) {
            val now = SystemClock.uptimeMillis()
            val move = needle.step(now)
            if (move != null) {
                val target = needle.currentTarget
                armJob?.cancel()
                armJob = launch { armAngle.animateTo(target, tween(move.durationMs)) }
            }
            val deadline = needle.nextDeadlineMs()
            if (deadline == null) {
                needleWake.receive()
            } else {
                withTimeoutOrNull((deadline - now).coerceAtLeast(1L)) { needleWake.receive() }
            }
        }
    }

    // The page recomposes on every position tick; remembering the drawing
    // modifiers keeps their caches (brushes, strokes) until size/color change.
    val haloLayer = remember(surface, accent) { Modifier.turntableHalo(surface, accent) }
    val vinylLayer = remember(surface, accent) { Modifier.turntableVinyl(surface, accent) }
    val sheenLayer = remember(surface, accent, glint) { Modifier.turntableSheen(surface, accent, glint) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer {
            alpha = recordAlpha()
            compositingStrategy = CompositingStrategy.ModulateAlpha
        }) {
            Box(Modifier.fillMaxSize().then(haloLayer))
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(TURNTABLE_DISC_FRACTION)
                        .align(Alignment.CenterStart)
                        .graphicsLayer { rotationZ = rotationDegrees?.invoke(rotation.value) ?: rotation.value }
                        .clip(CircleShape)
                        .then(vinylLayer),
                contentAlignment = Alignment.Center,
            ) {
                if (drawLabel) Box(
                    modifier = Modifier.fillMaxSize(TURNTABLE_LABEL_FRACTION).clip(CircleShape).background(surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.music_note),
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.fillMaxSize(0.35f),
                    )
                    // Covers swap with a short crossfade from the previous cover,
                    // so fast skipping never flashes the empty note icon.
                    val context = LocalContext.current
                    val artUri = musicState.track.artUri
                    val artRequest =
                        remember(artUri) {
                            ImageRequest.Builder(context).data(artUri).crossfade(TURNTABLE_COVER_SWAP_MS).build()
                        }
                    AsyncImage(
                        model = artRequest,
                        contentDescription = stringResource(R.string.artwork),
                        placeholder = lastCover,
                        onSuccess = { lastCover = it.painter },
                        onError = { lastCover = null },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().cloudy(radius = blur, enabled = blur > 0),
                    )
                }
            }
            // Light does not turn with the record: the sheen sits on top, unrotated.
            Box(
                modifier =
                    Modifier
                        .fillMaxSize(TURNTABLE_DISC_FRACTION)
                        .align(Alignment.CenterStart)
                        .then(sheenLayer),
            )
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = armAlpha() }) {
            drawTurntableArm(armAngle.value + armOffset(), surface, metal)
        }
    }
}
