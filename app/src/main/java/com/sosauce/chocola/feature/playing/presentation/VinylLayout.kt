package com.sosauce.chocola.feature.playing.presentation

import com.sosauce.chocola.core.designsystem.NowPlayingButton

/**
 * Pure layout rules of the turntable page with every button at the bottom
 * (no top bar). Kept free of Compose so they can be unit tested.
 */
internal object VinylLayout {
    /** Faint swipe-down hint at the top: not clickable, takes no layout space. */
    const val HANDLE_WIDTH_DP = 36
    const val HANDLE_HEIGHT_DP = 4
    const val HANDLE_TOP_DP = 8
    const val HANDLE_ALPHA = 0.27f

    /** Bottom pill bar. */
    const val BAR_HEIGHT_DP = 44
    const val BAR_ICON_DP = 20

    /** The record takes the room above the controls, up to this side. */
    const val MAX_RECORD_DP = 360

    /** Record sits a little below the middle of its space, near the title. */
    const val RECORD_VERTICAL_BIAS = 0.35f

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

    /** Visible groups with hidden buttons removed; empty groups leave no gap or divider. */
    fun visibleBarGroups(hidden: Set<NowPlayingButton>): List<List<NowPlayingButton>> =
        BAR_GROUPS.map { group -> group.filter { it !in hidden } }.filter { it.isNotEmpty() }

    fun showsBar(hidden: Set<NowPlayingButton>): Boolean = visibleBarGroups(hidden).isNotEmpty()

    /**
     * Buttons the settings screen can toggle. The turntable page has no
     * collapse button any more (swipe down / Back), so CLOSE is not listed
     * there. Its stored name is kept, so old backups still load.
     */
    fun settingsToggles(turntableLayout: Boolean): List<NowPlayingButton> =
        if (turntableLayout) NowPlayingButton.hideable - NowPlayingButton.CLOSE else NowPlayingButton.hideable
}
