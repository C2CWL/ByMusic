package com.sosauce.chocola.feature.playing.presentation

import com.sosauce.chocola.core.designsystem.NowPlayingButton
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VinylLayoutTest {

    @Test
    fun barHasSevenIconsInThreeGroupsInTheDesignOrder() {
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
    fun settingsHideTheCollapseToggleOnlyForTheTurntablePage() {
        val turntable = VinylLayout.settingsToggles(turntableLayout = true)
        val classic = VinylLayout.settingsToggles(turntableLayout = false)
        assertFalse(NowPlayingButton.CLOSE in turntable)
        assertTrue(NowPlayingButton.CLOSE in classic)
        assertEquals(NowPlayingButton.hideable, classic)
        assertEquals(NowPlayingButton.hideable - NowPlayingButton.CLOSE, turntable)
        // The stored name is kept, so old backups that hid CLOSE still load.
        assertEquals(setOf(NowPlayingButton.CLOSE), setOf("CLOSE").let { names ->
            NowPlayingButton.hideable.filter { it.name in names }.toSet()
        })
    }
}
