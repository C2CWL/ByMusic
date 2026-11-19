package com.sosauce.chocola.feature.playing.presentation.expand

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VinylLyricsMathTest {

    private val fullArt = Rect(45f, 300f, 1035f, 1290f)
    private val headerCover = Rect(140f, 120f, 296f, 276f)

    private fun assertRect(expected: Rect, actual: Rect, eps: Float = 1e-2f) {
        assertEquals(expected.left, actual.left, eps)
        assertEquals(expected.top, actual.top, eps)
        assertEquals(expected.right, actual.right, eps)
        assertEquals(expected.bottom, actual.bottom, eps)
    }

    private fun assertBetween(value: Float, a: Float, b: Float, eps: Float = 1e-2f) {
        assertTrue(value >= minOf(a, b) - eps && value <= maxOf(a, b) + eps)
    }

    private val samples = (0..100).map { it / 100f }

    @Test
    fun durationsMatchDesign() {
        assertEquals(450, VinylLyricsMath.ENTER_MS)
        assertEquals(400, VinylLyricsMath.EXIT_MS)
    }

    @Test
    fun startFrameIsExactlyTheTurntablePage() {
        assertEquals(1f, VinylLyricsMath.pageAlpha(0f), 0f)
        assertEquals(1f, VinylLyricsMath.armAlpha(0f), 0f)
        assertEquals(1f, VinylLyricsMath.haloAlpha(0f), 0f)
        assertEquals(1f, VinylLyricsMath.discAlpha(0f), 0f)
        assertEquals(0f, VinylLyricsMath.lyricsAlpha(0f), 0f)
        assertEquals(0f, VinylLyricsMath.headerAlpha(0f), 0f)
        assertEquals(0f, VinylLyricsMath.toolbarAlpha(0f), 0f)
        assertEquals(0f, VinylLyricsMath.backgroundAlpha(0f), 0f)
        val label = VinylExpandMath.labelRectFor(fullArt)
        assertRect(label, VinylLyricsMath.coverRect(0f, label, headerCover))
        // The flying record around the cover is the page's record.
        assertRect(VinylExpandMath.discRectFor(fullArt), VinylLyricsMath.discAround(label))
    }

    @Test
    fun endFrameIsExactlyTheLyricsPage() {
        assertEquals(0f, VinylLyricsMath.pageAlpha(1f), 0f)
        assertEquals(0f, VinylLyricsMath.armAlpha(1f), 0f)
        assertEquals(0f, VinylLyricsMath.haloAlpha(1f), 0f)
        assertEquals(0f, VinylLyricsMath.discAlpha(1f), 0f)
        assertEquals(1f, VinylLyricsMath.lyricsAlpha(1f), 0f)
        assertEquals(0f, VinylLyricsMath.lyricsOffsetDp(1f), 0f)
        assertEquals(1f, VinylLyricsMath.headerAlpha(1f), 0f)
        assertEquals(1f, VinylLyricsMath.toolbarAlpha(1f), 0f)
        assertEquals(1f, VinylLyricsMath.backgroundAlpha(1f), 0f)
        val label = VinylExpandMath.labelRectFor(fullArt)
        assertRect(headerCover, VinylLyricsMath.coverRect(1f, label, headerCover))
        assertEquals(8f, VinylLyricsMath.coverCornerPx(1f, headerCover.width, 8f), 1e-3f)
        assertEquals(0f, VinylLyricsMath.coverDegrees(1f, 123f), 1e-3f)
        assertTrue(VinylLyricsMath.landed(1f))
    }

    @Test
    fun windowsFollowTheDesignTimeline() {
        // Page controls and tonearm are gone by 30%.
        assertEquals(0f, VinylLyricsMath.pageAlpha(0.3f), 0f)
        assertEquals(0f, VinylLyricsMath.armAlpha(0.3f), 0f)
        // Outer vinyl fades over 45%..90%.
        assertEquals(1f, VinylLyricsMath.discAlpha(0.45f), 0f)
        assertEquals(0f, VinylLyricsMath.discAlpha(0.9f), 0f)
        // Lyrics start at 42%, toolbar background over 20%..60%.
        assertEquals(0f, VinylLyricsMath.lyricsAlpha(0.42f), 0f)
        assertEquals(VinylLyricsMath.LYRICS_RISE_DP, VinylLyricsMath.lyricsOffsetDp(0.42f), 0f)
        assertEquals(0f, VinylLyricsMath.toolbarAlpha(0.2f), 0f)
        assertEquals(1f, VinylLyricsMath.toolbarAlpha(0.6f), 0f)
        // Cover stays a circle until the last 18%.
        assertEquals(78f, VinylLyricsMath.coverCornerPx(0.82f, 156f, 8f), 1e-3f)
        assertFalse(VinylLyricsMath.landed(0.99f))
    }

    @Test
    fun everyCurveIsMonotonicWithoutOvershoot() {
        val rising = listOf<(Float) -> Float>(
            VinylLyricsMath::lyricsAlpha,
            VinylLyricsMath::headerAlpha,
            VinylLyricsMath::toolbarAlpha,
            VinylLyricsMath::backgroundAlpha
        )
        val falling = listOf<(Float) -> Float>(
            VinylLyricsMath::pageAlpha,
            VinylLyricsMath::armAlpha,
            VinylLyricsMath::haloAlpha,
            VinylLyricsMath::discAlpha,
            VinylLyricsMath::lyricsOffsetDp
        )
        rising.forEach { f ->
            samples.zipWithNext().forEach { (a, b) -> assertTrue(f(b) >= f(a)) }
            samples.forEach { assertTrue(f(it) in 0f..1f) }
        }
        falling.forEach { f ->
            samples.zipWithNext().forEach { (a, b) -> assertTrue(f(b) <= f(a)) }
        }
        val corners = samples.map { VinylLyricsMath.coverCornerPx(it, 156f, 8f) }
        corners.zipWithNext().forEach { (a, b) -> assertTrue(b <= a) }
    }

    @Test
    fun coverFlightStaysBetweenStartAndEnd() {
        val label = VinylExpandMath.labelRectFor(fullArt)
        samples.forEach { p ->
            val r = VinylLyricsMath.coverRect(p, label, headerCover)
            assertBetween(r.left, label.left, headerCover.left)
            assertBetween(r.top, label.top, headerCover.top)
            assertBetween(r.width, label.width, headerCover.width)
            assertEquals(r.width, r.height, 1e-2f)
        }
        // Out-of-range progress is clamped.
        assertRect(label, VinylLyricsMath.coverRect(-1f, label, headerCover))
        assertRect(headerCover, VinylLyricsMath.coverRect(2f, label, headerCover))
    }

    @Test
    fun coverTurnsUprightTheShortWay() {
        assertEquals(-60f, VinylLyricsMath.coverDegrees(0f, 300f), 1e-3f)
        assertEquals(-60f, VinylLyricsMath.coverDegrees(0.2f, 660f), 1e-3f)
        val mid = VinylLyricsMath.coverDegrees(0.6f, 300f)
        assertTrue(mid < 0f && mid > -60f)
    }

    @Test
    fun armLiftsLikePause() {
        assertEquals(10f, VinylLyricsMath.armDegrees(0f, 10f, -19f), 0f)
        assertEquals(-19f, VinylLyricsMath.armDegrees(0.3f, 10f, -19f), 0f)
        assertEquals(-19f, VinylLyricsMath.armDegrees(1f, 10f, -19f), 0f)
    }

    @Test
    fun cornerNeverExceedsACircle() {
        assertEquals(20f, VinylLyricsMath.coverCornerPx(1f, 40f, 64f), 1e-3f)
    }
}
