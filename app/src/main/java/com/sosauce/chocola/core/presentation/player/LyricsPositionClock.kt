package com.sosauce.chocola.core.presentation.player

/**
 * How often the UI should sample [androidx.media3.common.Player.getCurrentPosition]
 * while audio is actually playing.
 *
 * The previous 1000 ms tick made the highlighted line lag the media clock by
 * up to one second (typically a few hundred milliseconds). A short poll stays
 * close to the player clock without inventing a lyric offset or accumulating
 * elapsed time, which would drift. Polling must stop when paused or in the
 * background of a finished session so it does not keep waking the dispatcher.
 */
object LyricsPositionClock {
    const val PLAYING_POLL_MS = 50L

    fun shouldPollWhilePlaying(isPlaying: Boolean): Boolean = isPlaying

    fun currentLineIndex(positionMs: Long, timestampsMs: List<Int>): Int {
        if (timestampsMs.isEmpty()) return -1
        return timestampsMs.indexOfLast { positionMs >= it }
    }
}
