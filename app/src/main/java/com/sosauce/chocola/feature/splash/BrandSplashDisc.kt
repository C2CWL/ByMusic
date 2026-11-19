package com.sosauce.chocola.feature.splash

/**
 * Single source of truth for the small vinyl disc shown by the system splash
 * (res/drawable/splash_system_mark.xml) and redrawn by the in-app splash at the
 * same place and size on its first frame.
 *
 * Units are vector-drawable units of a 288×288 icon canvas (Android 12+ icon
 * without background). The disc is centred and is 112 units in diameter, so it
 * stays well inside the 160 dp visible circle.
 */
object BrandSplashDisc {
    const val CANVAS = 288f
    const val CENTER = CANVAS / 2f
    const val DIAMETER = 112f
    const val RADIUS = DIAMETER / 2f

    const val BODY_COLOR = 0xFF0C0C0CL
    const val RIM_COLOR = 0xFF403C37L
    const val RIM_WIDTH = 0.6f

    const val GROOVE_COLOR = 0xFFE2DCD0L
    const val GROOVE_WIDTH = 0.55f
    const val GROOVE_ALPHA_STRONG = 0.275f
    const val GROOVE_ALPHA_WEAK = 0.15f

    /** Groove radii as fractions of the disc radius. Every third groove is stronger. */
    val GROOVES = floatArrayOf(
        0.44f, 0.50f, 0.555f, 0.61f, 0.66f, 0.71f,
        0.755f, 0.80f, 0.845f, 0.89f, 0.93f, 0.965f
    )

    fun grooveAlpha(index: Int): Float =
        if (index % 3 == 0) GROOVE_ALPHA_STRONG else GROOVE_ALPHA_WEAK

    /**
     * Sheen toward the lower right (≈51°, clockwise from 3 o'clock), the same
     * direction as the artwork's highlight seen from the record's real centre.
     * Stacked arcs approximate a soft wedge without a gradient.
     */
    const val SHEEN_COLOR = 0xFFF0EADCL
    const val SHEEN_WIDTH = 0.7f
    const val SHEEN_CENTER_DEG = 51f
    val SHEEN_HALF_SPANS = floatArrayOf(30f, 18f, 8f)
    val SHEEN_ALPHAS = floatArrayOf(0.22f, 0.30f, 0.35f)
    /** Grooves (by index into [GROOVES]) that carry the sheen. */
    val SHEEN_GROOVES = 1..10

    const val LABEL_FRACTION = 0.32f
    const val LABEL_COLOR = 0xFF28241FL
    const val LABEL_RIM_COLOR = 0xFF70685EL
    const val LABEL_RIM_WIDTH = 0.5f

    const val HOLE_FRACTION = 0.035f
    const val HOLE_COLOR = 0xFF000000L
}
