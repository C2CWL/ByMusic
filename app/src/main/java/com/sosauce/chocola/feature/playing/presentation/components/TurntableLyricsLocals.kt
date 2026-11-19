package com.sosauce.chocola.feature.playing.presentation.components

import androidx.compose.runtime.compositionLocalOf

/** Turntable look, lyrics on the cover: record and lyrics cross-fade within this. */
internal const val TURNTABLE_ART_LYRICS_MS = 300

/** The record shrinks to this while it fades out under the cover lyrics. */
internal const val TURNTABLE_ART_LYRICS_DISC_SCALE = 0.94f

/**
 * True while the expand overlay's lyrics layer is open or moving: that layer
 * draws a flying copy of the record, so the page's own record hides to avoid
 * drawing the halo and sheen twice.
 */
internal val LocalTurntableRecordHidden = compositionLocalOf { false }

/**
 * Set by the expand overlay for the turntable page: opens lyrics as a layer of
 * the overlay (record flies into the lyrics header) instead of navigating to
 * the lyrics screen. Null anywhere else, which keeps the plain navigation.
 */
internal val LocalOpenVinylLyrics = compositionLocalOf<(() -> Unit)?> { null }
