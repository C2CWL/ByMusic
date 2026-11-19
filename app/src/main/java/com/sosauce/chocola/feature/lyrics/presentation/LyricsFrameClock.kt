package com.sosauce.chocola.feature.lyrics.presentation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import com.sosauce.chocola.core.presentation.player.LyricsPositionClock

/**
 * Playback position that advances on every display frame between player samples.
 * Read it in draw or layout code (or inside derivedStateOf) so per-frame changes
 * only redraw what needs it instead of recomposing the lyrics list.
 */
@Composable
fun rememberFramePositionMs(
    sampleMs: Long,
    isPlaying: Boolean,
    speed: Float
): LongState {
    val position = remember { mutableLongStateOf(sampleMs) }
    LaunchedEffect(sampleMs, isPlaying, speed) {
        val base = LyricsPositionClock.reconcileBaseMs(position.longValue, sampleMs, isPlaying)
        position.longValue = base
        if (!isPlaying) return@LaunchedEffect
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                position.longValue = LyricsPositionClock.estimateMs(
                    baseMs = base,
                    sampleMs = sampleMs,
                    elapsedNanos = now - startNanos,
                    speed = speed,
                    isPlaying = true
                )
            }
        }
    }
    return position
}
