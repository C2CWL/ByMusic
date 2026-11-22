package com.sosauce.chocola.feature.playing.presentation.expand

import androidx.compose.ui.geometry.Rect
import com.sosauce.chocola.feature.playing.presentation.expand.PlayerExpandMath.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerExpandMathTest {

    @Test
    fun settleMotionLeadsAndTheCoverFollows() {
        val container = PlayerExpandMath.motionProgress(0.5f)
        val cover = PlayerExpandMath.coverMotionProgress(0.5f)
        assertTrue(container > 0.75f)
        assertTrue(cover in 0.3f..0.5f)
        assertTrue(container - cover > 0.25f)
        assertEquals(0f, PlayerExpandMath.coverMotionProgress(0f))
        assertEquals(1f, PlayerExpandMath.motionProgress(1f))
        assertEquals(1f, PlayerExpandMath.coverMotionProgress(1f))
    }

    @Test
    fun transitionBackgroundRevealsTheRealMiniBarBeforeFullChromeFadesIn() {
        assertEquals(0f, PlayerExpandMath.transitionBackgroundAlpha(0f), 0f)
        assertEquals(1f, PlayerExpandMath.transitionBackgroundAlpha(1f), 0f)
        assertEquals(0.5f, PlayerExpandMath.transitionBackgroundAlpha(PlayerExpandMath.FULL_CHROME_FADE_START / 2f), 1e-5f)
        assertEquals(1f, PlayerExpandMath.transitionBackgroundAlpha(PlayerExpandMath.FULL_CHROME_FADE_START), 0f)
        assertEquals(0f, PlayerExpandMath.fullChromeAlpha(0f), 1e-5f)
        assertEquals(1f, PlayerExpandMath.fullChromeAlpha(1f), 1e-5f)
        assertEquals(0f, PlayerExpandMath.topChromeAlpha(0f), 1e-5f)
        assertEquals(1f, PlayerExpandMath.topChromeAlpha(1f), 1e-5f)
        assertTrue(PlayerExpandMath.fullChromeAlpha(0.5f) in 0.01f..0.99f)
        assertTrue(PlayerExpandMath.fullChromeAlpha(0.5f) > PlayerExpandMath.fullChromeAlpha(0.3f))
    }

    @Test
    fun containerAndCoverRectsMeetExactEndpoints() {
        val mini = Rect(0f, 800f, 400f, 900f)
        val view = Rect(0f, 0f, 400f, 1000f)
        val miniArt = Rect(16f, 820f, 64f, 868f)
        val fullArt = Rect(40f, 80f, 360f, 400f)
        assertEquals(mini, PlayerExpandMath.containerRect(0f, mini, view))
        assertEquals(view, PlayerExpandMath.containerRect(1f, mini, view))
        assertEquals(miniArt, PlayerExpandMath.coverRect(0f, miniArt, fullArt))
        assertEquals(fullArt, PlayerExpandMath.coverRect(1f, miniArt, fullArt))
        val mid = PlayerExpandMath.coverRect(0.5f, miniArt, fullArt)
        assertEquals((miniArt.left + fullArt.left) / 2f, mid.left, 1e-4f)
    }

    @Test
    fun libraryContainerCornersStaySquare() {
        assertEquals(0f, PlayerExpandMath.containerCornerRadiusDp(0f, libraryStyle = true), 0f)
        assertEquals(0f, PlayerExpandMath.containerCornerRadiusDp(1f, libraryStyle = true), 0f)
        assertEquals(24f, PlayerExpandMath.containerCornerRadiusDp(0f, libraryStyle = false), 0f)
        assertEquals(0f, PlayerExpandMath.containerCornerRadiusDp(1f, libraryStyle = false), 0f)
    }

    @Test
    fun settleThresholdsOpenAndCloseWithoutClaimingExternalNumbers() {
        // Weak drag below the 0.25 open floor closes; at/above 0.75 stays open.
        // Between those floors a weak drag goes to the nearer end (0.5).
        // Strong flings override progress.
        assertEquals(0f, PlayerExpandMath.settleTarget(0.26f, 0f), 0f)
        assertEquals(0f, PlayerExpandMath.settleTarget(0.2f, 0f), 0f)
        assertEquals(0f, PlayerExpandMath.settleTarget(0.25f, 0f), 0f)
        assertEquals(1f, PlayerExpandMath.settleTarget(0.75f, 0f), 0f)
        assertEquals(1f, PlayerExpandMath.settleTarget(0.05f, -900f), 0f)
        assertEquals(0f, PlayerExpandMath.settleTarget(0.95f, 900f), 0f)
        assertEquals(1f, PlayerExpandMath.settleTarget(0.6f, 0f), 0f)
        assertEquals(0f, PlayerExpandMath.settleTarget(0.4f, 0f), 0f)
    }

    @Test
    fun dragDeltaIsClampedAndUsesTravel() {
        assertEquals(0.5f, PlayerExpandMath.applyDragDelta(0.4f, 0.1f), 1e-5f)
        assertEquals(1f, PlayerExpandMath.applyDragDelta(0.95f, 0.2f), 1e-5f)
        assertEquals(0f, PlayerExpandMath.applyDragDelta(0.05f, -0.2f), 1e-5f)
        // Upward pointer motion (negative y) increases progress
        assertEquals(0.25f, PlayerExpandMath.dragDeltaProgress(dragPx = -50f, travelPx = 200f), 1e-5f)
        assertEquals(200f, PlayerExpandMath.travelPx(1000f, 800f), 0f)
        assertEquals(1f, PlayerExpandMath.travelPx(100f, 800f), 0f)
    }

    @Test
    fun interruptKeepsCurrentProgress() {
        val (phase, progress) = PlayerExpandMath.interruptSettle(0.63f)
        assertEquals(Phase.Dragging, phase)
        assertEquals(0.63f, progress, 0f)
        val clamped = PlayerExpandMath.interruptSettle(1.4f)
        assertEquals(1f, clamped.second, 0f)
    }

    @Test
    fun boundsGateRejectsMissingOrEmptyRects() {
        val box = Rect(0f, 0f, 10f, 10f)
        val empty = Rect(0f, 0f, 0f, 10f)
        assertFalse(PlayerExpandMath.boundsReady(null, box, box))
        assertFalse(PlayerExpandMath.boundsReady(box, box, empty))
        assertTrue(PlayerExpandMath.boundsReady(box, box, box))
    }

    @Test
    fun reduceMotionJumpsToEndpoints() {
        assertEquals(1f, PlayerExpandMath.reduceMotionTarget(expand = true), 0f)
        assertEquals(0f, PlayerExpandMath.reduceMotionTarget(expand = false), 0f)
    }

    @Test
    fun phaseTrackingForSettleDragAndRest() {
        assertEquals(
            Phase.Dragging,
            PlayerExpandMath.phaseAfterProgressChange(Phase.Settling, 0.4f, animating = true, dragging = true)
        )
        assertEquals(
            Phase.Settling,
            PlayerExpandMath.phaseAfterProgressChange(Phase.Dragging, 0.4f, animating = true, dragging = false)
        )
        assertEquals(
            Phase.Expanded,
            PlayerExpandMath.phaseAfterProgressChange(Phase.Settling, 1f, animating = false, dragging = false)
        )
        assertEquals(
            Phase.Collapsed,
            PlayerExpandMath.phaseAfterProgressChange(Phase.Settling, 0f, animating = false, dragging = false)
        )
    }

    @Test
    fun durationIsTheAppSchemeAndHasNoHold() {
        assertEquals(420, PlayerExpandMath.DURATION_MS)
    }
}
