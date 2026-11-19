package com.sosauce.chocola.feature.playing.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurntableSheenTest {

    @Test
    fun lobeStopsAscendInsideZeroToOneAndPeakAtTheTwoFixedAngles() {
        for (halfWidth in listOf(TurntableSheen.WIDE_HALF_WIDTH, TurntableSheen.NARROW_HALF_WIDTH, 0.12f)) {
            val stops = TurntableSheen.lobeStops(halfWidth)
            assertEquals(0f, stops.first().first, 0f)
            assertEquals(1f, stops.last().first, 0f)
            assertEquals(0f, stops.first().second, 0f)
            assertEquals(0f, stops.last().second, 0f)
            stops.zipWithNext().forEach { (a, b) -> assertTrue("$a !< $b", a.first < b.first) }
            assertTrue(stops.all { it.second in 0f..1f })
            val key = stops.first { it.second == 1f }
            assertEquals(TurntableSheen.KEY_LOBE_DEGREES / 360f, key.first, 1e-6f)
            val fill = stops.first { it.second == TurntableSheen.FILL_LOBE_STRENGTH }
            assertEquals(TurntableSheen.FILL_LOBE_DEGREES / 360f, fill.first, 1e-6f)
        }
    }

    @Test
    fun oledDimsTheSheenByAboutTwentyPercentAndTheHaloMore() {
        assertEquals(1f, TurntableSheen.intensity(oled = false), 0f)
        assertEquals(0.8f, TurntableSheen.intensity(oled = true), 1e-6f)
        assertTrue(TurntableSheen.haloIntensity(oled = true) < TurntableSheen.haloIntensity(oled = false))
    }

    @Test
    fun glintPhaseWrapsAndTakesAboutFiveSecondsPerCycle() {
        var phase = 0f
        repeat(300) { phase = TurntableGlint.advance(phase, 1f / 60f) } // 5 s at 60 fps
        assertTrue("phase=$phase", phase < 0.01f || phase > 0.99f)
        assertTrue(TurntableGlint.advance(0.99f, 0.05f) in 0f..1f)
        // Long frame gaps are clamped, so a resume never jumps.
        assertEquals(TurntableGlint.advance(0f, 0.05f), TurntableGlint.advance(0f, 3f), 0f)
        assertEquals(0.5f, TurntableGlint.advance(0.5f, 0f), 0f)
    }

    @Test
    fun atMostThreeSoftGlintsStayOnTheirHighlightAndFadeInAndOut() {
        assertEquals(3, TurntableGlint.COUNT)
        for (index in 0 until TurntableGlint.COUNT) {
            val r = TurntableGlint.radiusFraction(index)
            assertTrue(r > TURNTABLE_LABEL_FRACTION && r < 1f)
            var maxAlpha = 0f
            var step = 0
            while (step <= 100) {
                val phase = step / 100f
                val alpha = TurntableGlint.alpha(index, phase)
                assertTrue(alpha in 0f..TurntableGlint.MAX_ALPHA)
                maxAlpha = maxOf(maxAlpha, alpha)
                val angle = TurntableGlint.angleDegrees(index, phase)
                val nearKey = kotlin.math.abs(angle - TurntableSheen.KEY_LOBE_DEGREES) <= TurntableGlint.TRAVEL_DEGREES / 2f + 1e-3f
                val nearFill = kotlin.math.abs(angle - TurntableSheen.FILL_LOBE_DEGREES) <= TurntableGlint.TRAVEL_DEGREES / 2f + 1e-3f
                assertTrue("glint $index at $angle", nearKey || nearFill)
                step++
            }
            assertTrue(maxAlpha > 0.1f)
        }
        // Each glint is invisible at the ends of its pass (no popping).
        val atPassStart = TurntableGlint.alpha(0, 0f)
        assertEquals(0f, atPassStart, 1e-6f)
    }

    @Test
    fun groovesAreFineUnevenAndBoundedToTheRecord() {
        val radius = TurntableGrooves.radius
        assertEquals(TurntableGrooves.COUNT, radius.size)
        radius.toList().zipWithNext().forEach { (a, b) -> assertTrue(a < b) }
        assertTrue(radius.first() > TURNTABLE_LABEL_FRACTION)
        assertTrue(radius.last() < 1f)
        assertTrue(TurntableGrooves.alpha.all { it > 0f && it <= 0.15f })
        assertTrue(TurntableGrooves.alpha.toSet().size > TurntableGrooves.COUNT / 2)
        for (i in 0 until TurntableGrooves.DUST_COUNT) {
            val d = kotlin.math.hypot(TurntableGrooves.dustX[i], TurntableGrooves.dustY[i])
            assertTrue(d >= TurntableGrooves.INNER - 1e-4f && d <= TurntableGrooves.OUTER + 1e-4f)
            assertTrue(TurntableGrooves.dustAlpha[i] <= 0.2f)
        }
    }
}
