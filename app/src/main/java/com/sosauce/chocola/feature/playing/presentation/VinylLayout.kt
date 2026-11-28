package com.sosauce.chocola.feature.playing.presentation

import com.sosauce.chocola.core.designsystem.NowPlayingButton

/**
 * Layout rules shared by the portrait and landscape turntable pages.
 */
internal object VinylLayout {
    const val PORTRAIT_DISC_FRACTION = .94f
    const val PORTRAIT_DISC_BLEED = .085f
    const val PORTRAIT_LABEL_FRACTION = .48f
    const val PORTRAIT_COVER_IN_LABEL = .86f
    const val PORTRAIT_COVER_FRACTION = PORTRAIT_LABEL_FRACTION * PORTRAIT_COVER_IN_LABEL

    fun portraitDiscOffset(side: Float, rtl: Boolean): Float =
        side * PORTRAIT_DISC_BLEED * if (rtl) 1f else -1f

    /** Faint swipe-down hint at the top: not clickable, takes no layout space. */
    const val HANDLE_WIDTH_DP = 36
    const val HANDLE_HEIGHT_DP = 4
    const val HANDLE_TOP_DP = 8
    const val HANDLE_ALPHA = 0.27f

    /** Bottom pill bar. */
    const val BAR_HEIGHT_DP = 44
    const val BAR_ICON_DP = 20

    /** Pill bar groups, separated by thin dividers: lyrics, queue | shuffle, repeat, speed, timer | more. */
    val BAR_GROUPS: List<List<NowPlayingButton>> = listOf(
        listOf(NowPlayingButton.LYRICS, NowPlayingButton.QUEUE),
        listOf(
            NowPlayingButton.SHUFFLE,
            NowPlayingButton.REPEAT,
            NowPlayingButton.SPEED,
            NowPlayingButton.SLEEP_TIMER
        ),
        listOf(NowPlayingButton.MORE_OPTIONS)
    )

    val PORTRAIT_TOP_ACTIONS = listOf(listOf(NowPlayingButton.LYRICS, NowPlayingButton.MORE_OPTIONS))
    val PORTRAIT_QUICK_ACTIONS = listOf(listOf(
        NowPlayingButton.SHUFFLE,
        NowPlayingButton.REWIND,
        NowPlayingButton.SPEED,
        NowPlayingButton.FORWARD,
        NowPlayingButton.SLEEP_TIMER
    ))
    val PORTRAIT_REPEAT = listOf(listOf(NowPlayingButton.REPEAT))
    val PORTRAIT_QUEUE = listOf(listOf(NowPlayingButton.QUEUE))

    fun settingsToggles(turntableLayout: Boolean, landscape: Boolean): List<NowPlayingButton> =
        if (turntableLayout && landscape) NowPlayingButton.hideable - NowPlayingButton.CLOSE
        else NowPlayingButton.hideable

    /** Visible groups with hidden buttons removed; empty groups leave no gap or divider. */
    fun visibleBarGroups(
        hidden: Set<NowPlayingButton>,
        groups: List<List<NowPlayingButton>> = BAR_GROUPS
    ): List<List<NowPlayingButton>> =
        groups.map { group -> group.filter { it !in hidden } }.filter { it.isNotEmpty() }

    fun showsBar(hidden: Set<NowPlayingButton>): Boolean = visibleBarGroups(hidden).isNotEmpty()

}
