package com.sosauce.chocola.feature.playing.presentation.expand

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSheetGeometryTest {
    private val viewport = SheetRect(0f, 0f, 360f, 804f)
    private val dock = SheetRect(12f, 714f, 348f, 778f)
    private val thumb = SheetRect(8f, 8f, 56f, 56f)
    private val classic = SheetRect(30f, 158f, 330f, 458f)

    @Test
    fun cardStartsAtMeasuredDockAndReachesRootEdgesIndependently() {
        val rootInWindow = viewport.offset(27f, 43f)
        val dockInWindow = dock.offset(27f, 43f)
        val local = dockInWindow.offset(-rootInWindow.left, -rootInWindow.top)
        assertEquals(dock, PlayerExpandMath.containerRect(0f, local, viewport))
        assertEquals(viewport, PlayerExpandMath.containerRect(1f, local, viewport))

        val widthLanded = PlayerExpandMath.containerRect(.45f, local, viewport)
        assertEquals(viewport.left, widthLanded.left, 0f)
        assertEquals(viewport.right, widthLanded.right, 0f)
        assertTrue(widthLanded.bottom < viewport.bottom)
        assertEquals(714f * .55f, widthLanded.top, .001f)

        assertEquals(viewport.bottom, PlayerExpandMath.containerRect(.8f, local, viewport).bottom, 0f)
    }

    @Test
    fun coverStaysInsideTheCardAndEndsExactlyOnBothStyleSlots() {
        for (full in listOf(classic, VinylExpandMath.labelSlot(classic, rtl = false))) {
            assertEquals(thumb.offset(dock.left, dock.top), cover(0f, full))
            assertEquals(full, cover(1f, full))
            for (frame in 0..1000) {
                val g = frame / 1000f
                val card = PlayerExpandMath.containerRect(g, dock, viewport)
                val cover = cover(g, full)
                assertTrue(cover.left >= card.left && cover.top >= card.top)
                assertTrue(cover.right <= card.right + .001f && cover.bottom <= card.bottom + .001f)
            }
        }
    }

    @Test
    fun geometryIsContinuousAcrossPropertyWindows() {
        for (g in listOf(.02f, .12f, .2f, .25f, .45f, .6f, .65f, .72f, .75f, .8f, .9f, 1f)) {
            val before = cover(g - .00001f, classic)
            val after = cover((g + .00001f).coerceAtMost(1f), classic)
            assertEquals(before.left, after.left, .05f)
            assertEquals(before.top, after.top, .05f)
            assertEquals(before.width, after.width, .05f)
        }
    }

    @Test
    fun nativeDockCornersSettleAt24ThenMatchTheViewport() {
        for (start in listOf(0f, 24f, 28f)) {
            assertEquals(start, PlayerExpandMath.corner(0f, start), 0f)
            assertEquals(24f, PlayerExpandMath.corner(.12f, start), 0f)
            assertEquals(24f, PlayerExpandMath.corner(.9f, start), 0f)
            assertEquals(0f, PlayerExpandMath.corner(1f, start), 0f)
        }
        assertEquals(1f, PlayerExpandMath.shadowAlpha(.9f), 0f)
        assertEquals(0f, PlayerExpandMath.shadowAlpha(1f), 0f)
    }

    private fun cover(g: Float, full: SheetRect): SheetRect {
        val card = PlayerExpandMath.containerRect(g, dock, viewport)
        return PlayerExpandMath.coverRect(g, thumb, full, card, PlayerExpandMath.settleOffsetDp(g))
    }
}
