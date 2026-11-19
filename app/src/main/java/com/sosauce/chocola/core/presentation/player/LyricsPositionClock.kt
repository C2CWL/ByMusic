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
 *
 * The poll is only a correction source. Lyrics that animate inside a line
 * (word-by-word fill) estimate the position on every display frame from the
 * latest sample, see [estimateMs], so they move at the screen refresh rate
 * instead of stepping 20 times a second.
 */
object LyricsPositionClock {
    const val PLAYING_POLL_MS = 50L

    /** A sample slightly behind what is already on screen is treated as jitter, not a seek. */
    const val BACKWARD_JITTER_MS = 120L

    /** Never run further ahead of the last real sample than this (stalls, buffering). */
    const val MAX_LEAD_MS = 400L

    fun shouldPollWhilePlaying(isPlaying: Boolean): Boolean = isPlaying

    fun currentLineIndex(positionMs: Long, timestampsMs: List<Int>): Int {
        if (timestampsMs.isEmpty()) return -1
        return timestampsMs.indexOfLast { positionMs >= it }
    }

    /**
     * Where the per-frame estimate restarts from when a new player sample arrives.
     * Small backward corrections keep the value already shown so the fill never
     * twitches backwards; anything larger (a real seek) jumps to the sample.
     */
    fun reconcileBaseMs(shownMs: Long, sampleMs: Long, isPlaying: Boolean): Long {
        if (!isPlaying) return sampleMs
        val behind = shownMs - sampleMs
        return if (behind in 1..BACKWARD_JITTER_MS) shownMs else sampleMs
    }

    /** Position for the current frame, from the base and the time since the sample. */
    fun estimateMs(
        baseMs: Long,
        sampleMs: Long,
        elapsedNanos: Long,
        speed: Float,
        isPlaying: Boolean
    ): Long {
        if (!isPlaying) return baseMs
        val safeSpeed = if (speed.isFinite() && speed > 0f) speed else 1f
        val advanced = baseMs + (elapsedNanos.coerceAtLeast(0L) / 1_000_000.0 * safeSpeed).toLong()
        val limit = maxOf(baseMs, sampleMs + (MAX_LEAD_MS * safeSpeed).toLong())
        return advanced.coerceAtMost(limit)
    }
}
