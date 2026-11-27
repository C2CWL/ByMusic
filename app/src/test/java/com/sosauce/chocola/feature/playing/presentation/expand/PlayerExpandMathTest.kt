package com.sosauce.chocola.feature.playing.presentation.expand

import androidx.compose.ui.geometry.Rect
import com.sosauce.chocola.feature.playing.presentation.expand.PlayerExpandMath.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerExpandMathTest {

    @Test
    fun settleEasingLeadsEarlyAndNeverDoubleApplies() {
        assertEquals(0f, PlayerExpandMath.settleEasing(0f))
        assertEquals(1f, PlayerExpandMath.settleEasing(1f))
        assertTrue(PlayerExpandMath.settleEasing(0.25f) > 0.55f)
        assertTrue(PlayerExpandMath.settleEasing(0.5f) > 0.85f)
        // Soft start: the first frames do not throw the card half way (old ease-out did).
        assertTrue(PlayerExpandMath.settleEasing(0.05f) < 0.05f)
        var previous = -1f
        for (i in 0..100) {
            val value = PlayerExpandMath.settleEasing(i / 100f)
            assertTrue(value + 0.00001f >= previous)
            previous = value
        }
    }

    @Test
    fun transitionBackgroundRevealsTheRealMiniBarBeforeFullChromeFadesIn() {
        assertEquals(0f, PlayerExpandMath.transitionBackgroundAlpha(0f), 0f)
        assertEquals(1f, PlayerExpandMath.transitionBackgroundAlpha(1f), 0f)
        assertEquals(0.5f, PlayerExpandMath.transitionBackgroundAlpha(PlayerExpandMath.BACKGROUND_FADE_END / 2f), 1e-5f)
        assertEquals(1f, PlayerExpandMath.transitionBackgroundAlpha(PlayerExpandMath.BACKGROUND_FADE_END), 0f)
        // The card is still see-through when the full chrome starts, opaque before the settled page.
        assertTrue(PlayerExpandMath.transitionBackgroundAlpha(PlayerExpandMath.FULL_CHROME_FADE_START) < 1f)
        assertTrue(PlayerExpandMath.BACKGROUND_FADE_END < PlayerExpandMath.CLOSE_PROGRESS_THRESHOLD)
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
    fun containerCornersStartAtTheMiniBarAndEndSquare() {
        assertEquals(28f, PlayerExpandMath.containerCornerRadiusDp(0f, libraryStyle = true), 0f)
        assertEquals(0f, PlayerExpandMath.containerCornerRadiusDp(1f, libraryStyle = true), 0f)
        assertEquals(24f, PlayerExpandMath.containerCornerRadiusDp(0f, libraryStyle = false), 0f)
        assertEquals(0f, PlayerExpandMath.containerCornerRadiusDp(1f, libraryStyle = false), 0f)
        // Still fully round while the card first rises, then shrinks monotonically.
        assertEquals(28f, PlayerExpandMath.containerCornerRadiusDp(PlayerExpandMath.CONTAINER_CORNER_SQUARE_FROM, true), 0f)
        var previous = Float.MAX_VALUE
        for (i in 0..100) {
            val value = PlayerExpandMath.containerCornerRadiusDp(i / 100f, libraryStyle = true)
            assertTrue(value <= previous + 1e-5f)
            previous = value
        }
    }

    @Test
    fun miniBarCopyOnTheCardIsWholeAtZeroAndGoneBeforeThePage() {
        assertEquals(1f, PlayerExpandMath.miniFaceAlpha(0f), 0f)
        assertEquals(1f, PlayerExpandMath.miniSurfaceAlpha(0f), 0f)
        assertEquals(0f, PlayerExpandMath.miniFaceAlpha(PlayerExpandMath.MINI_FACE_FADE_END), 0f)
        assertEquals(0f, PlayerExpandMath.miniSurfaceAlpha(PlayerExpandMath.MINI_SURFACE_FADE_END), 0f)
        assertEquals(0f, PlayerExpandMath.miniFaceAlpha(1f), 0f)
        assertEquals(0f, PlayerExpandMath.miniSurfaceAlpha(1f), 0f)
        // Face leaves first, the glass surface stays until the page background covers the card.
        assertTrue(PlayerExpandMath.MINI_FACE_FADE_END < PlayerExpandMath.MINI_SURFACE_FADE_END)
        assertTrue(PlayerExpandMath.MINI_SURFACE_FADE_END <= PlayerExpandMath.BACKGROUND_FADE_END)
        var face = 2f
        var surface = 2f
        for (i in 0..100) {
            val p = i / 100f
            assertTrue(PlayerExpandMath.miniFaceAlpha(p) <= face + 1e-6f)
            assertTrue(PlayerExpandMath.miniSurfaceAlpha(p) <= surface + 1e-6f)
            face = PlayerExpandMath.miniFaceAlpha(p)
            surface = PlayerExpandMath.miniSurfaceAlpha(p)
        }
    }

    @Test
    fun cardShadowStartsAsTheBarShadowAndIsGoneOnThePage() {
        assertEquals(PlayerExpandMath.MINI_ELEVATION_DP, PlayerExpandMath.containerElevationDp(0f), 0f)
        assertEquals(PlayerExpandMath.MINI_ELEVATION_DP, PlayerExpandMath.containerElevationDp(PlayerExpandMath.ELEVATION_FADE_START), 0f)
        assertEquals(0f, PlayerExpandMath.containerElevationDp(1f), 0f)
    }

    @Test
    fun barIsHiddenOnlyWhileTheCardIsOnScreenAway() {
        // Before the card has drawn, the bar stays (never neither).
        assertFalse(PlayerExpandMath.barHidden(0.1f, cardDrawn = false))
        // Card drawn and moving: only the card (never two bars).
        assertTrue(PlayerExpandMath.barHidden(0.001f, cardDrawn = true))
        assertTrue(PlayerExpandMath.barHidden(0.5f, cardDrawn = true))
        // Settled page: bar hidden even if the card never drew (restored state).
        assertTrue(PlayerExpandMath.barHidden(1f, cardDrawn = false))
        // Back on the bar: the bar is visible in that same frame.
        assertFalse(PlayerExpandMath.barHidden(0f, cardDrawn = true))
        assertFalse(PlayerExpandMath.barHidden(0f, cardDrawn = false))
        assertTrue(PlayerExpandMath.HANDOFF_LINGER_FRAMES >= 1)
    }

    @Test
    fun releaseVelocityBecomesProgressVelocity() {
        // Finger down (positive px/s) closes: progress goes down.
        assertEquals(-0.5f, PlayerExpandMath.progressVelocity(1000f, 2000f), 1e-6f)
        assertEquals(0.5f, PlayerExpandMath.progressVelocity(-1000f, 2000f), 1e-6f)
        // Unknown travel: no velocity, plain tween.
        assertEquals(0f, PlayerExpandMath.progressVelocity(1000f, 0f), 0f)
    }

    @Test
    fun settleDurationIsShortenedByTheDistanceLeft() {
        assertEquals(PlayerExpandMath.DURATION_MS, PlayerExpandMath.settleDurationMs(0f, 1f))
        assertEquals(PlayerExpandMath.DURATION_MS, PlayerExpandMath.settleDurationMs(1f, 0f))
        assertEquals(225, PlayerExpandMath.settleDurationMs(0.5f, 0f))
        assertEquals(PlayerExpandMath.MIN_DURATION_MS, PlayerExpandMath.settleDurationMs(0.05f, 0f))
        assertEquals(PlayerExpandMath.MIN_DURATION_MS, PlayerExpandMath.settleDurationMs(1f, 1f))
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
        assertEquals(450, PlayerExpandMath.DURATION_MS)
        assertTrue(PlayerExpandMath.DURATION_MS in 420..480)
    }
}
