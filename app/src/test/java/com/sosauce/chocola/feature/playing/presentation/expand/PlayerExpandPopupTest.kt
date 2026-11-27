package com.sosauce.chocola.feature.playing.presentation.expand

import android.view.WindowManager
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerExpandPopupTest {

    private val window = IntSize(1080, 2400)

    @Test
    fun popupIsPinnedToWindowOriginWhateverTheAnchor() {
        // The overlay is composed inside the bottom bar; the bar's bounds must
        // not move the popup, otherwise the full-screen layer lands below the screen.
        val bottomBar = IntRect(0, 2200, 1080, 2400)
        val floatingBar = IntRect(54, 2050, 1026, 2330)
        for (anchor in listOf(bottomBar, floatingBar, IntRect(0, 0, 0, 0))) {
            for (dir in LayoutDirection.values()) {
                assertEquals(
                    IntOffset.Zero,
                    PlayerExpandPopup.positionProvider.calculatePosition(
                        anchor, window, dir, IntSize(1080, 2400)
                    )
                )
            }
        }
        assertEquals(IntOffset.Zero, PlayerExpandPopup.windowOrigin)
    }

    @Test
    fun windowBoundsMapToOverlayWithoutOffset() {
        // With the popup at the window origin, a window-space rect is already in
        // overlay space: the settled container fills the viewport exactly and
        // the collapsed container sits on the measured mini player.
        val viewport = Rect(0f, 0f, 1080f, 2400f)
        val mini = Rect(0f, 2200f, 1080f, 2400f)
        val origin = PlayerExpandPopup.windowOrigin
        val miniInOverlay = mini.translate(-origin.x.toFloat(), -origin.y.toFloat())
        assertEquals(mini, miniInOverlay)
        assertEquals(viewport, PlayerExpandMath.containerRect(1f, miniInOverlay, viewport))
        assertEquals(miniInOverlay, PlayerExpandMath.containerRect(0f, miniInOverlay, viewport))
        val settled = PlayerExpandMath.containerRect(1f, miniInOverlay, viewport)
        assertTrue(settled.top >= 0f && settled.bottom <= viewport.bottom)
    }

    @Test
    fun hiddenMeasurePassDoesNotTakeTouches() {
        val hidden = PlayerExpandPopup.flags(interactive = PlayerExpandPopup.isInteractive(0f))
        assertTrue(hidden and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)

        val visible = PlayerExpandPopup.flags(interactive = PlayerExpandPopup.isInteractive(0.3f))
        assertEquals(0, visible and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)

        assertFalse(PlayerExpandPopup.isInteractive(0f))
        assertTrue(PlayerExpandPopup.isInteractive(1f))
    }

    @Test
    fun overlayNeverStealsFocusAndIsNotClipped() {
        for (interactive in listOf(true, false)) {
            val flags = PlayerExpandPopup.flags(interactive)
            assertTrue(flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
            assertTrue(flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS != 0)
        }
    }

    @Test
    fun collapsedCardSitsExactlyOnTheRoundedMiniBar() {
        // Library mini bar: 12dp side margins, 28dp corners. The overlay card at
        // progress 0 must have the same rect and corner so the handoff does not flash.
        val viewport = Rect(0f, 0f, 1080f, 2400f)
        val miniCard = Rect(36f, 2160f, 1044f, 2352f)
        assertEquals(miniCard, PlayerExpandMath.containerRect(0f, miniCard, viewport))
        assertEquals(28f, PlayerExpandMath.containerCornerRadiusDp(0f, libraryStyle = true), 0f)
        assertEquals(0f, PlayerExpandMath.transitionBackgroundAlpha(0f), 0f)
        assertEquals(1f, PlayerExpandMath.glassRimAlpha(0f), 0f)
        // Overlay becomes touchable as soon as the card is visible at all.
        assertTrue(PlayerExpandPopup.isInteractive(0.01f))
        assertFalse(PlayerExpandPopup.isInteractive(0f))
    }
}
