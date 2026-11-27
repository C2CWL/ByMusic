package com.sosauce.chocola.feature.playing.presentation.expand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VinylExpandMathTest {
    private val art = SheetRect(20f, 100f, 320f, 400f)

    @Test
    fun labelMatchesTheDiscRatioAndMirrorsItsStartAlignment() {
        val ltr = VinylExpandMath.labelSlot(art, rtl = false)
        val rtl = VinylExpandMath.labelSlot(art, rtl = true)
        val discSide = art.width * VinylExpandMath.DISC_FRACTION
        assertEquals(discSide * VinylExpandMath.LABEL_FRACTION, ltr.width, .0001f)
        assertEquals(art.left + discSide / 2f, ltr.centerX, .0001f)
        assertEquals(art.right - discSide / 2f, rtl.centerX, .0001f)
        assertEquals(art.centerY, rtl.centerY, .0001f)
        for (direction in listOf(false, true)) {
            val label = VinylExpandMath.labelSlot(art, rtl = direction)
            val restored = VinylExpandMath.artAroundLabel(label, rtl = direction)
            assertEquals(art.left, restored.left, .0001f)
            assertEquals(art.top, restored.top, .0001f)
            assertEquals(art.right, restored.right, .0001f)
            assertEquals(art.bottom, restored.bottom, .0001f)
        }
    }

    @Test
    fun vinylBackdropDiscAndArmFollowTheirOwnProgressWindows() {
        assertEquals(0f, VinylExpandMath.backdropAlpha(.2f), 0f)
        assertEquals(1f, VinylExpandMath.backdropAlpha(.75f), 0f)
        assertEquals(0f, VinylExpandMath.discAlpha(.25f), 0f)
        assertEquals(1f, VinylExpandMath.discAlpha(.65f), 0f)
        assertEquals(0f, VinylExpandMath.armAlpha(.6f), 0f)
        assertEquals(1f, VinylExpandMath.armAlpha(.95f), 0f)
        assertEquals(-14f, VinylExpandMath.armOffset(.6f), 0f)
        assertEquals(0f, VinylExpandMath.armOffset(1f), 0f)
        for (i in 0 until 1000) {
            val before = VinylExpandMath.backdropAlpha(i / 1000f)
            val after = VinylExpandMath.backdropAlpha((i + 1) / 1000f)
            assertTrue(after >= before && after - before < .004f)
        }
    }

    @Test
    fun recordUsesShortestRotationThroughTheTransition() {
        assertEquals(-110f, VinylExpandMath.normalizeDegrees(250f), 0f)
        assertEquals(90f, VinylExpandMath.normalizeDegrees(450f), 0f)
        assertEquals(180f, VinylExpandMath.normalizeDegrees(-180f), 0f)
    }
}
