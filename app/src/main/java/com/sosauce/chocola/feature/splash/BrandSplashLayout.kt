package com.sosauce.chocola.feature.splash

/**
 * Layout constants taken from the confirmed direction-A preview
 * (1080×1920 composition). These are this app's values, not a minimum
 * on-screen duration.
 */
object BrandSplashLayout {
    const val PREVIEW_WIDTH_PX = 1080f
    const val WORDMARK_FONT_PX_AT_1080 = 78f
    const val WORDMARK_TRACKING_EM = 0.18f
    const val WORDMARK_BASELINE_FRACTION = 0.70f
    const val RING_CENTER_FRACTION = 0.88f
    const val RING_RADIUS_PX_AT_1080 = 40f
    const val RING_STROKE_PX_AT_1080 = 1.85f
    /** Practical floor so the hairline stays visible on dense screens. */
    const val RING_STROKE_MIN_PX = 1.5f
    const val RING_ARC_START_DEG = -38f
    const val RING_ARC_SWEEP_DEG = 90f
    const val RING_ROTATION_MS = 1400

    /** Safety cap only: never spin forever. Not a minimum display time. */
    const val MAX_WAIT_MS = 8_000L

    val wordmarkColor = androidx.compose.ui.graphics.Color(226, 220, 208, 230)
    val ringDimColor = androidx.compose.ui.graphics.Color(180, 172, 158, 70)
    val ringArcColor = androidx.compose.ui.graphics.Color(236, 230, 216, 245)
}
