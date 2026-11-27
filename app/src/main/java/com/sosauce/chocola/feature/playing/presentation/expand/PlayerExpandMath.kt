package com.sosauce.chocola.feature.playing.presentation.expand

import kotlin.math.abs
import kotlin.math.sqrt

internal data class SheetRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    val centerX get() = (left + right) / 2f
    val centerY get() = (top + bottom) / 2f

    fun offset(x: Float, y: Float) = SheetRect(left + x, top + y, right + x, bottom + y)
}

internal object PlayerExpandMath {
    const val EXPAND_MS = 280
    const val COLLAPSE_MS = 300
    const val RELEASE_STIFFNESS = 400f

    fun lerp(a: Float, b: Float, t: Float): Float = when (t) {
        0f -> a
        1f -> b
        else -> a + (b - a) * t
    }

    fun smoothstep(start: Float, end: Float, value: Float): Float {
        val t = ((value - start) / (end - start)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    fun tapEasing(fraction: Float): Float {
        val t = fraction.coerceIn(0f, 1f)
        // Move the peak speed modestly earlier while keeping both endpoint slopes at zero.
        return smoothstep(0f, 1f, t + .6f * t * (1f - t))
    }
    fun miniFaceAlpha(g: Float) = 1f - smoothstep(0f, .12f, g)
    fun chromeAlpha(g: Float) = smoothstep(.72f, 1f, g)
    fun settleOffsetDp(g: Float) = 26f * (1f - smoothstep(.6f, 1f, g))
    fun tintAlpha(g: Float) = smoothstep(.02f, .25f, g)
    fun shadowAlpha(g: Float) = 1f - smoothstep(.9f, 1f, g)

    fun corner(g: Float, dockRadius: Float) =
        lerp(dockRadius, 24f, smoothstep(0f, .12f, g)) * shadowAlpha(g)

    fun containerRect(g: Float, dock: SheetRect, viewport: SheetRect): SheetRect {
        val width = smoothstep(0f, .45f, g)
        return SheetRect(
            lerp(dock.left, viewport.left, width),
            lerp(dock.top, viewport.top, g),
            lerp(dock.right, viewport.right, width),
            lerp(dock.bottom, viewport.bottom, smoothstep(0f, .8f, g))
        )
    }

    // Artwork slots are local to their endpoint surfaces.
    fun coverRect(
        g: Float,
        dockArt: SheetRect,
        fullArt: SheetRect,
        container: SheetRect,
        settleOffsetPx: Float
    ): SheetRect {
        if (g == 0f) return dockArt.offset(container.left, container.top)
        if (g == 1f) return fullArt.offset(container.left, container.top)
        val size = lerp(dockArt.width, fullArt.width, g)
            .coerceAtMost(minOf(container.width, container.height))
        val x = lerp(dockArt.left, fullArt.left, g).coerceIn(0f, (container.width - size).coerceAtLeast(0f))
        val y = (lerp(dockArt.top, fullArt.top, g) + g * settleOffsetPx)
            .coerceIn(0f, (container.height - size).coerceAtLeast(0f))
        return SheetRect(x, y, x + size, y + size).offset(container.left, container.top)
    }

    fun travelPx(dockTop: Float, fullTop: Float) = abs(dockTop - fullTop).coerceAtLeast(1f)
    fun backProgress(start: Float, gesture: Float) = start * (1f - gesture.coerceIn(0f, 1f))

    fun progressVelocity(velocityY: Float, travel: Float) = -velocityY / travel.coerceAtLeast(1f)
    fun dragProgress(start: Float, displacementY: Float, travel: Float) =
        (start + progressVelocity(displacementY, travel)).coerceIn(0f, 1f)

    /**
     * Limit extreme flings close to a bound. For a critically damped response
     * the velocity contribution is v*t*exp(-omega*t), bounded by v/(e*omega).
     * Toward the target, v <= omega*distance avoids crossing the target.
     */
    fun boundReleaseVelocity(start: Float, target: Float, velocity: Float): Float {
        if (start == target) return 0f
        val omega = sqrt(RELEASE_STIFFNESS)
        val towardsTarget = if (target > start) velocity > 0f else velocity < 0f
        val distance = if (towardsTarget) abs(target - start) else 1f - abs(target - start)
        val limit = omega * distance * if (towardsTarget) 1f else 2.7182818f
        return velocity.coerceIn(-limit, limit)
    }

    fun settleTarget(g: Float, velocityY: Float, startedExpanded: Boolean = false): Float = when {
        velocityY <= -800f -> 1f
        velocityY >= 800f -> 0f
        g >= (if (startedExpanded) .8f else .5f) -> 1f
        else -> 0f
    }
}
