package com.sosauce.chocola.feature.playing.presentation

import com.sosauce.chocola.core.designsystem.NowPlayingButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VinylLayoutTest {

    @Test
    fun landscapeBarHasSevenIconsInThreeGroupsInTheDesignOrder() {
        assertEquals(
            listOf(
                listOf(NowPlayingButton.LYRICS, NowPlayingButton.QUEUE),
                listOf(
                    NowPlayingButton.SHUFFLE,
                    NowPlayingButton.REPEAT,
                    NowPlayingButton.SPEED,
                    NowPlayingButton.SLEEP_TIMER
                ),
                listOf(NowPlayingButton.MORE_OPTIONS)
            ),
            VinylLayout.visibleBarGroups(emptySet())
        )
        assertEquals(7, VinylLayout.BAR_GROUPS.flatten().size)
        assertEquals(44, VinylLayout.BAR_HEIGHT_DP)
        assertEquals(36, VinylLayout.HANDLE_WIDTH_DP)
        assertEquals(4, VinylLayout.HANDLE_HEIGHT_DP)
    }

    @Test
    fun barKeepsEveryExistingFunctionAndNothingElse() {
        val inBar = VinylLayout.BAR_GROUPS.flatten().toSet()
        // Favorite stays next to the title, playback keys stay in their row,
        // the collapse button is replaced by swipe down / Back.
        val elsewhere = setOf(
            NowPlayingButton.CLOSE,
            NowPlayingButton.FAVORITE,
            NowPlayingButton.PREVIOUS,
            NowPlayingButton.REWIND,
            NowPlayingButton.PLAY_PAUSE,
            NowPlayingButton.FORWARD,
            NowPlayingButton.NEXT
        )
        assertTrue(inBar.intersect(elsewhere).isEmpty())
        assertEquals(NowPlayingButton.entries.toSet(), inBar + elsewhere)
    }

    @Test
    fun hiddenButtonsLeaveNoGapAndEmptyGroupsDropTheirDivider() {
        val hidden = setOf(NowPlayingButton.LYRICS, NowPlayingButton.QUEUE, NowPlayingButton.SPEED)
        assertEquals(
            listOf(
                listOf(NowPlayingButton.SHUFFLE, NowPlayingButton.REPEAT, NowPlayingButton.SLEEP_TIMER),
                listOf(NowPlayingButton.MORE_OPTIONS)
            ),
            VinylLayout.visibleBarGroups(hidden)
        )
        assertTrue(VinylLayout.showsBar(hidden))
        val all = VinylLayout.BAR_GROUPS.flatten().toSet()
        assertTrue(VinylLayout.visibleBarGroups(all).isEmpty())
        assertFalse(VinylLayout.showsBar(all))
    }

    @Test
    fun portraitActionsKeepEveryExistingControlAvailable() {
        val bars = VinylLayout.PORTRAIT_TOP_ACTIONS + VinylLayout.PORTRAIT_QUICK_ACTIONS +
            VinylLayout.PORTRAIT_REPEAT + VinylLayout.PORTRAIT_QUEUE
        val individualControls = listOf(
            NowPlayingButton.CLOSE,
            NowPlayingButton.FAVORITE,
            NowPlayingButton.PREVIOUS,
            NowPlayingButton.PLAY_PAUSE,
            NowPlayingButton.NEXT
        )
        val controls = bars.flatten() + individualControls
        assertEquals(NowPlayingButton.entries.size, controls.size)
        assertEquals(NowPlayingButton.entries.toSet(), controls.toSet())
    }

    @Test
    fun settingsHideTheCollapseToggleOnlyForTheLandscapeTurntablePage() {
        val portraitTurntable = VinylLayout.settingsToggles(turntableLayout = true, landscape = false)
        val landscapeTurntable = VinylLayout.settingsToggles(turntableLayout = true, landscape = true)
        val classic = VinylLayout.settingsToggles(turntableLayout = false, landscape = false)
        assertEquals(NowPlayingButton.hideable, portraitTurntable)
        assertEquals(NowPlayingButton.hideable - NowPlayingButton.CLOSE, landscapeTurntable)
        assertEquals(NowPlayingButton.hideable, classic)
    }
}
