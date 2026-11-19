package com.sosauce.chocola.feature.playing.presentation.expand

import android.view.WindowManager
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.PopupPositionProvider

/**
 * Window placement rules for the expand overlay [androidx.compose.ui.window.Popup].
 *
 * A plain `Popup(alignment = TopStart)` is aligned to its *parent layout*. The
 * overlay is composed inside the bottom bar, so the full-screen layer used to
 * start at the mini player's top edge and was drawn almost entirely below the
 * screen. The overlay must instead sit at the activity window's origin, so the
 * popup's own coordinate space equals the activity window's coordinate space
 * and every bound can be measured with `boundsInWindow()`.
 */
object PlayerExpandPopup {

    /** Popup origin in the activity window. Always the window's top-left. */
    val windowOrigin: IntOffset = IntOffset.Zero

    /** Ignores the anchor (the bottom bar) and pins the popup to the window origin. */
    val positionProvider: PopupPositionProvider = object : PopupPositionProvider {
        override fun calculatePosition(
            anchorBounds: IntRect,
            windowSize: IntSize,
            layoutDirection: LayoutDirection,
            popupContentSize: IntSize
        ): IntOffset = windowOrigin
    }

    /**
     * Window flags for the overlay.
     *
     * - Never focusable: focus and back events stay with the activity window, so the
     *   existing BackHandler keeps collapsing the player.
     * - Never clipped to the screen, so it can cover the status bar area like before.
     * - Not touchable while it is only a hidden measure pass (progress 0), so the
     *   invisible layer cannot swallow taps meant for the mini player.
     */
    fun flags(interactive: Boolean): Int {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        if (!interactive) {
            flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        }
        return flags
    }

    /** The overlay only takes touches once something is actually visible. */
    fun isInteractive(progress: Float): Boolean = progress > 0f
}
