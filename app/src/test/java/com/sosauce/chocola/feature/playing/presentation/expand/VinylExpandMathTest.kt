package com.sosauce.chocola.feature.playing.presentation.expand

import androidx.compose.ui.geometry.Rect
import com.sosauce.chocola.feature.playing.presentation.components.TURNTABLE_DISC_FRACTION
import com.sosauce.chocola.feature.playing.presentation.components.TURNTABLE_LABEL_FRACTION
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VinylExpandMathTest {

    private val miniArt = Rect(16f, 1820f, 112f, 1916f)
    private val fullArt = Rect(45f, 300f, 1035f, 1290f)

    private fun assertRect(expected: Rect, actual: Rect, eps: Float = 1e-2f) {
        assertEquals(expected.left, actual.left, eps)
        assertEquals(expected.top, actual.top, eps)
        assertEquals(expected.right, actual.right, eps)
        assertEquals(expected.bottom, actual.bottom, eps)
    }

    @Test
    fun fractionsMatchTheSettledTurntable() {
        assertEquals(TURNTABLE_DISC_FRACTION, VinylExpandMath.DISC_FRACTION, 0f)
        assertEquals(TURNTABLE_LABEL_FRACTION, VinylExpandMath.LABEL_FRACTION, 0f)
    }

    @Test
    fun settledDiscAndLabelFollowTurntableLayout() {
        val disc = VinylExpandMath.discRectFor(fullArt)
        val side = fullArt.width * 0.91f
        // Anchored to the start edge, centered vertically.
        assertEquals(fullArt.left, disc.left, 1e-3f)
        assertEquals(side, disc.width, 1e-2f)
        assertEquals(fullArt.center.y, disc.center.y, 1e-2f)
        val label = VinylExpandMath.labelRectFor(fullArt)
        assertEquals(side * 0.55f, label.width, 1e-2f)
        assertEquals(disc.center.x, label.center.x, 1e-2f)
        assertEquals(disc.center.y, label.center.y, 1e-2f)
    }

    @Test
    fun coverStartsOnMiniArtAndLandsOnLabel() {
        assertRect(miniArt, VinylExpandMath.labelRect(0f, miniArt, fullArt))
        assertRect(VinylExpandMath.labelRectFor(fullArt), VinylExpandMath.labelRect(1f, miniArt, fullArt))
        // Out-of-range progress is clamped.
        assertRect(miniArt, VinylExpandMath.labelRect(-0.2f, miniArt, fullArt))
        assertRect(VinylExpandMath.labelRectFor(fullArt), VinylExpandMath.labelRect(1.3f, miniArt, fullArt))
    }

    @Test
    fun squareCoverGrowsIntoTheRecordFirstThenShrinksIntoTheLabel() {
        val disc = VinylExpandMath.discRectFor(fullArt)
        // Halfway the square cover covers exactly the record's place.
        assertRect(disc, VinylExpandMath.labelRect(VinylExpandMath.SQUARE_PHASE_END, miniArt, fullArt))
        var previous = 0f
        for (i in 0..20) {
            val p = i / 20f * VinylExpandMath.SQUARE_PHASE_END
            val label = VinylExpandMath.labelRect(p, miniArt, fullArt)
            assertEquals("cover stays square", label.width, label.height, 1e-2f)
            assertTrue("cover grows in the first half", label.width >= previous - 1e-3f)
            previous = label.width
        }
        for (i in 0..20) {
            val p = VinylExpandMath.SQUARE_PHASE_END + i / 20f * (1f - VinylExpandMath.SQUARE_PHASE_END)
            val label = VinylExpandMath.labelRect(p, miniArt, fullArt)
            // Second half: shrinks in place on the record's center.
            assertEquals(disc.center.x, label.center.x, 1e-2f)
            assertEquals(disc.center.y, label.center.y, 1e-2f)
            assertTrue("cover shrinks in the second half", label.width <= previous + 1e-3f)
            previous = label.width
        }
    }

    @Test
    fun recordStaysAtItsSettledPlaceAndOnlyFadesInLate() {
        val disc = VinylExpandMath.discRectFor(fullArt)
        for (i in 0..20) {
            assertRect(disc, VinylExpandMath.discRect(i / 20f, fullArt))
        }
        assertEquals(0f, VinylExpandMath.discAlpha(VinylExpandMath.SQUARE_PHASE_END), 0f)
        assertEquals(0f, VinylExpandMath.discAlpha(0.3f), 0f)
    }

    @Test
    fun artBoxForSettledDiscIsTheMeasuredArtwork() {
        val disc = VinylExpandMath.discRectFor(fullArt)
        assertRect(fullArt, VinylExpandMath.artBoxForDisc(disc))
    }

    @Test
    fun coverCornersStaySquareFirstThenCloseIntoCircle() {
        val side = 200f
        assertEquals(12f, VinylExpandMath.labelCornerRadiusPx(0f, 12f, side), 1e-4f)
        // Square-cover phase keeps the mini corner.
        assertEquals(12f, VinylExpandMath.labelCornerRadiusPx(0.25f, 12f, side), 1e-4f)
        assertEquals(12f, VinylExpandMath.labelCornerRadiusPx(VinylExpandMath.SQUARE_PHASE_END, 12f, side), 1e-4f)
        assertEquals(100f, VinylExpandMath.labelCornerRadiusPx(VinylExpandMath.ROUND_END, 12f, side), 1e-4f)
        assertEquals(100f, VinylExpandMath.labelCornerRadiusPx(1f, 12f, side), 1e-4f)
        val mid = VinylExpandMath.labelCornerRadiusPx(0.7f, 12f, side)
        assertTrue(mid in 12.01f..99.99f)
        // A start corner larger than a circle is clamped.
        assertEquals(100f, VinylExpandMath.labelCornerRadiusPx(0f, 400f, side), 1e-4f)
    }

    @Test
    fun fadesHitExactEndpoints() {
        assertEquals(0f, VinylExpandMath.discAlpha(0f), 0f)
        assertEquals(1f, VinylExpandMath.discAlpha(1f), 0f)
        assertEquals(1f, VinylExpandMath.discAlpha(VinylExpandMath.DISC_FADE_END), 1e-5f)
        // Tonearm only in the second half.
        assertEquals(0f, VinylExpandMath.armAlpha(0.5f), 0f)
        assertTrue(VinylExpandMath.armAlpha(0.75f) in 0.01f..0.99f)
        assertEquals(1f, VinylExpandMath.armAlpha(1f), 0f)
        // Still stars only near the end.
        assertEquals(0f, VinylExpandMath.starsAlpha(0.7f), 0f)
        assertEquals(1f, VinylExpandMath.starsAlpha(1f), 0f)
        // Backdrop: mini-bar color at 0 and through the square phase start, page backdrop fully by the end.
        assertEquals(0f, VinylExpandMath.backdropAlpha(0f), 0f)
        assertEquals(0f, VinylExpandMath.backdropAlpha(0.4f), 0f)
        assertTrue(VinylExpandMath.backdropAlpha(0.6f) in 0.01f..0.99f)
        assertEquals(1f, VinylExpandMath.backdropAlpha(1f), 0f)
    }

    @Test
    fun recordAngleStartsUprightAndLandsOnSettledAngle() {
        assertEquals(0f, VinylExpandMath.recordRotation(0f, 250f), 0f)
        // 250 degrees lands as -110, the same orientation turned the short way.
        assertEquals(-110f, VinylExpandMath.recordRotation(1f, 250f), 1e-3f)
        assertEquals(90f, VinylExpandMath.recordRotation(1f, 450f), 1e-3f)
        // Upright through the square phase, turns only in the second half.
        assertEquals(0f, VinylExpandMath.recordRotation(0.5f, 250f), 1e-3f)
        assertEquals(-55f, VinylExpandMath.recordRotation(0.75f, 250f), 1e-3f)
    }

    @Test
    fun normalizeDegreesFoldsIntoHalfOpenRange() {
        assertEquals(180f, VinylExpandMath.normalizeDegrees(180f), 0f)
        assertEquals(180f, VinylExpandMath.normalizeDegrees(-180f), 0f)
        assertEquals(-170f, VinylExpandMath.normalizeDegrees(190f), 1e-3f)
        assertEquals(0f, VinylExpandMath.normalizeDegrees(720f), 1e-3f)
        assertEquals(10f, VinylExpandMath.normalizeDegrees(-350f), 1e-3f)
    }

    @Test
    fun turntableSwitchPicksTransitionStyle() {
        assertEquals(VinylExpandMath.Style.Vinyl, VinylExpandMath.styleFor(true))
        assertEquals(VinylExpandMath.Style.Classic, VinylExpandMath.styleFor(false))
    }

    @Test
    fun settledPageIsPreparedOnlyNearTheEnd() {
        assertFalse(VinylExpandMath.shouldPrepareSettledPage(0f))
        assertFalse(VinylExpandMath.shouldPrepareSettledPage(0.5f))
        assertTrue(VinylExpandMath.shouldPrepareSettledPage(VinylExpandMath.PREPARE_PAGE_FROM))
        assertTrue(VinylExpandMath.shouldPrepareSettledPage(1f))
    }
}
