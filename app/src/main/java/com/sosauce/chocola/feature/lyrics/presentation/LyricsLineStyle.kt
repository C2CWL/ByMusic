package com.sosauce.chocola.feature.lyrics.presentation

/**
 * Visual levels for the lyrics list (lyrics page v2). Calm by design: the current
 * line is larger and bright, other lines fade and soften with distance, and every
 * change is an eased transition without overshoot. No floating, glowing or bouncing.
 *
 * Lines drawn over the artwork or vinyl ("on artwork") keep close to the previous
 * look and are never blurred, so text over a picture stays readable.
 */
object LyricsLineStyle {
    const val TransitionMs = 350
    const val FarDistance = 3

    /** The current line is drawn this much larger than the user's lyrics font size. */
    const val PageCurrentSizeFactor = 1.15f

    /** Unsung part of the current line when word timing is available. */
    const val UnsungAlpha = 0.35f

    /** Translation / romanization lines, relative to the user's font size. */
    const val ExtraSizeFactor = 0.62f
    const val ExtraAlpha = 0.7f

    /** Soft edge of the word fill. */
    const val FillEdgeDp = 14

    /** Press feedback: a faint tint on the line and around the pressed character. */
    const val PressedLineAlpha = 0.06f
    const val PressedCharAlpha = 0.14f
    const val PressedCharPadDp = 3
    const val PressFadeMs = 120

    fun currentSizeFactor(onArtwork: Boolean): Float = if (onArtwork) 1f else PageCurrentSizeFactor

    /** Scale of non-current lines so they read at the user's normal font size. */
    fun inactiveScale(onArtwork: Boolean): Float =
        if (onArtwork) 1f / 1.04f else 1f / PageCurrentSizeFactor

    fun alphaFor(distance: Int, passed: Boolean, onArtwork: Boolean): Float {
        val base = if (onArtwork) {
            when (distance) {
                0 -> 1f
                1 -> 0.72f
                else -> 0.45f
            }
        } else {
            when (distance) {
                0 -> 1f
                1 -> 0.55f
                2 -> 0.32f
                else -> 0.2f
            }
        }
        return if (passed && distance > 0) base * 0.85f else base
    }

    /** Blur radius in dp; 0 for the current line and for lyrics drawn over artwork. */
    fun blurFor(distance: Int, onArtwork: Boolean): Float {
        if (onArtwork) return 0f
        return when (distance) {
            0 -> 0f
            1 -> 0.6f
            2 -> 1.4f
            else -> 2f
        }
    }
}
