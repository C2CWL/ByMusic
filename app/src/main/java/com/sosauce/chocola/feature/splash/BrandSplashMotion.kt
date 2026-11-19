package com.sosauce.chocola.feature.splash

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** A disc (centre + radius) in the splash overlay's local pixel space. */
data class DiscGeometry(val centerX: Float, val centerY: Float, val radius: Float)

/** Bounds of the system splash icon view, in activity-window pixels. */
data class SplashIconBounds(val left: Float, val top: Float, val width: Float, val height: Float)

/**
 * Timeline and geometry of the seamless splash, as confirmed in the preview:
 * system frame (black + small disc) → identical first app frame → the disc grows
 * and moves to the artwork's real record centre while the artwork fades in →
 * wordmark and ring fade in → hold ≥ 700 ms (max 8 s overall) → 250 ms fade out.
 */
object BrandSplashMotion {
    const val GROW_MS = 550
    const val CHROME_FADE_MS = 250
    const val MIN_HOLD_MS = 700L
    const val EXIT_FADE_MS = 250

    /** If no system splash hands off (e.g. no splash on this launch), start anyway. */
    const val HANDOFF_FALLBACK_MS = 1_000L

    /** Disc diameter on screen in dp when the icon view is the standard 288 dp. */
    const val DISC_DP = 112f

    // Record geometry fitted from splash_vinyl_bg.webp (1080×1920): the grooves
    // are concentric around a centre outside the top-left corner.
    const val ART_WIDTH = 1080f
    const val ART_HEIGHT = 1920f
    const val ART_RECORD_CENTER_X = -402f
    const val ART_RECORD_CENTER_Y = -456f
    const val ART_RECORD_RADIUS = 1650f

    const val DISC_FADE_START = 0.60f
    const val ART_FADE_START = 0.30f
    const val ART_FADE_END = 0.90f

    fun shouldAnimate(animatorDurationScale: Float): Boolean = animatorDurationScale > 0f

    /** Scale used by ContentScale.Crop for the artwork filling the overlay. */
    fun artScale(viewWidth: Float, viewHeight: Float): Float =
        max(viewWidth / ART_WIDTH, viewHeight / ART_HEIGHT)

    /** The artwork's record circle in overlay space (Crop + TopStart alignment). */
    fun recordGeometry(viewWidth: Float, viewHeight: Float): DiscGeometry {
        val s = artScale(viewWidth, viewHeight)
        return DiscGeometry(ART_RECORD_CENTER_X * s, ART_RECORD_CENTER_Y * s, ART_RECORD_RADIUS * s)
    }

    /** Where the system splash draws the disc when no icon view could be measured. */
    fun defaultStart(viewWidth: Float, viewHeight: Float, density: Float): DiscGeometry =
        DiscGeometry(viewWidth / 2f, viewHeight / 2f, DISC_DP * density / 2f)

    /**
     * Start disc from the measured system icon view (already translated into
     * overlay space). The drawable fills the view, and the disc is
     * [BrandSplashDisc.DIAMETER]/[BrandSplashDisc.CANVAS] of it.
     */
    fun startFromIcon(bounds: SplashIconBounds): DiscGeometry? {
        if (bounds.width <= 0f || bounds.height <= 0f) return null
        val side = min(bounds.width, bounds.height)
        return DiscGeometry(
            bounds.left + bounds.width / 2f,
            bounds.top + bounds.height / 2f,
            side * (BrandSplashDisc.RADIUS / BrandSplashDisc.CANVAS)
        )
    }

    /** Disc at grow progress [t]: centre moves linearly, radius scales geometrically. */
    fun discAt(t: Float, start: DiscGeometry, end: DiscGeometry): DiscGeometry {
        val p = t.coerceIn(0f, 1f)
        val r0 = max(start.radius, 1f)
        val r1 = max(end.radius, 1f)
        return DiscGeometry(
            start.centerX + (end.centerX - start.centerX) * p,
            start.centerY + (end.centerY - start.centerY) * p,
            r0 * (r1 / r0).pow(p)
        )
    }

    fun discAlpha(t: Float): Float = 1f - smoothstep(DISC_FADE_START, 1f, t)

    fun artAlpha(t: Float): Float = smoothstep(ART_FADE_START, ART_FADE_END, t)

    /** Remaining time the fully formed vinyl page must still stay on screen. */
    fun holdRemainingMs(elapsedSinceFormedMs: Long): Long =
        (MIN_HOLD_MS - elapsedSinceFormedMs).coerceAtLeast(0L)

    fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        if (edge1 <= edge0) return if (x < edge0) 0f else 1f
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
