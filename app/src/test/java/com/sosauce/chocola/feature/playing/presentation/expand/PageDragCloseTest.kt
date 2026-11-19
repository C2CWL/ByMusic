package com.sosauce.chocola.feature.playing.presentation.expand

import org.junit.Assert.assertEquals
import org.junit.Test

class PageDragCloseTest {

    @Test
    fun shortPullDownFromTheSettledPageCloses() {
        assertEquals(0f, PlayerExpandMath.pageDragSettleTarget(0.79f, 0f), 0f)
        assertEquals(0f, PlayerExpandMath.pageDragSettleTarget(0.3f, 0f), 0f)
    }

    @Test
    fun tinyPullSpringsBackOpen() {
        assertEquals(1f, PlayerExpandMath.pageDragSettleTarget(0.95f, 0f), 0f)
        assertEquals(1f, PlayerExpandMath.pageDragSettleTarget(PlayerExpandMath.PAGE_DRAG_CLOSE_BELOW, 0f), 0f)
    }

    @Test
    fun flingDirectionWins() {
        assertEquals(0f, PlayerExpandMath.pageDragSettleTarget(0.98f, PlayerExpandMath.CLOSE_VELOCITY_PX_PER_SEC), 0f)
        assertEquals(1f, PlayerExpandMath.pageDragSettleTarget(0.4f, PlayerExpandMath.OPEN_VELOCITY_PX_PER_SEC), 0f)
    }

    @Test
    fun pullingDownRunsTheExpandProgressBackwards() {
        val travel = PlayerExpandMath.travelPx(viewportHeight = 2000f, miniContainerHeight = 200f)
        val delta = PlayerExpandMath.dragDeltaProgress(dragPx = 180f, travelPx = travel)
        assertEquals(-0.1f, delta, 1e-5f)
        assertEquals(0.9f, PlayerExpandMath.applyDragDelta(1f, delta), 1e-5f)
        // Pulling up past fully open stays open.
        assertEquals(1f, PlayerExpandMath.applyDragDelta(1f, PlayerExpandMath.dragDeltaProgress(-50f, travel)), 0f)
    }

    @Test
    fun handleAndControlsAreFullyShownOnTheLastFrame() {
        assertEquals(1f, PlayerExpandMath.fullChromeAlpha(1f), 1e-5f)
        assertEquals(1f, PlayerExpandMath.topChromeAlpha(1f), 1e-5f)
    }
}
