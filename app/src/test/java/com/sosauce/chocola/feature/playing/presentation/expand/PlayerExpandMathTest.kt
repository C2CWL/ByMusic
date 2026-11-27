package com.sosauce.chocola.feature.playing.presentation.expand

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FloatTweenSpec
import kotlin.math.abs
import kotlin.math.roundToLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerExpandMathTest {
    // frame-analysis.md, sampled at 60 Hz with frame zero at motion start.
    private val referenceOpen = listOf(0f, .11f, .25f, .38f, .51f, .61f, .70f, .78f, .84f, .89f, .93f, .96f, .98f, 1f)
    private val referenceCloseTop = listOf(60, 109, 193, 281, 361, 426, 485, 534, 571, 604, 629, 651, 668, 681, 693, 700)
    private val open = listOf(
        0f, .024f, .087f, .175f, .278f, .385f, .492f, .593f, .685f, .765f,
        .832f, .887f, .929f, .960f, .980f, .993f, .999f, 1f, 1f
    )
    private val close = listOf(
        1f, .979f, .923f, .844f, .751f, .651f, .550f, .453f, .363f, .282f,
        .211f, .152f, .104f, .067f, .039f, .020f, .008f, .002f, 0f
    )

    @Test
    fun softerTimingTracksTheExpectedFrameTable() {
        for (frame in open.indices) {
            val ns = (frame * 1_000_000_000.0 / 60).roundToLong()
            val opening = tap(PlayerExpandMath.EXPAND_MS, ns, 0f, 1f)
            val closing = tap(PlayerExpandMath.COLLAPSE_MS, ns, 1f, 0f)
            // Rounded golden values. The reference gets a wider envelope because its
            // measured video moves earlier and finishes sooner than the approved code curve.
            assertEquals("opening frame $frame", open[frame], opening, .001f)
            assertEquals("closing frame $frame", close[frame], closing, .001f)
            referenceOpen.getOrNull(frame)?.let { assertEquals(it, opening, .24f) }
            referenceCloseTop.getOrNull(frame)?.let { assertEquals((700f - it) / 640f, closing, .23f) }
        }
    }

    @Test
    fun bothDirectionsAreMonotoneAndMoveLessThanPointTwelvePerRefresh() {
        for ((ms, from, to) in listOf(Triple(PlayerExpandMath.EXPAND_MS, 0f, 1f), Triple(PlayerExpandMath.COLLAPSE_MS, 1f, 0f))) {
            val end = ms * 1_000_000L
            assertEquals(from, tap(ms, 0L, from, to), 0f)
            assertEquals(to, tap(ms, end, from, to), 0f)
            // Slide a full refresh window at 1 ms intervals, including both edge frames.
            for (ns in 0L..end step 1_000_000L) {
                val delta = tap(ms, ns + 16_666_667L, from, to) - tap(ms, ns, from, to)
                assertTrue(delta * (to - from) >= 0f)
                assertTrue(abs(delta) < .12f)
            }
            val linearStep = 1f / ms
            assertTrue(abs(tap(ms, 1_000_000L, from, to) - from) < linearStep * .05f)
            assertTrue(abs(tap(ms, end - 1_000_000L, from, to) - to) < linearStep * .05f)
        }
    }

    private fun tap(ms: Int, ns: Long, from: Float, to: Float): Float =
        FloatTweenSpec(ms, easing = Easing(PlayerExpandMath::tapEasing)).getValueFromNanos(ns, from, to, 0f)
}
