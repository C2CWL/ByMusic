package com.sosauce.chocola.feature.lyrics.presentation

import com.sosauce.chocola.core.domain.model.Lyrics

/**
 * Where playback should start when a lyric is tapped.
 *
 * - Lines with word timing: the tapped character's word start, a little early so
 *   the first sound is not clipped, but never before the line itself.
 * - Lines without word timing (plain LRC): the line start.
 *
 * Characters outside every timed word (spaces, punctuation) belong to the next word;
 * at the end of a line they belong to the previous one.
 */
object LyricsTapTarget {

    /** Start this much before the tapped word. */
    const val WORD_PREROLL_MS = 80L

    /** A tap within this time after the user's own scroll ends only stops the list. */
    const val SCROLL_TAP_GUARD_MS = 200L

    fun wordIndexForChar(ranges: List<IntRange?>, charIndex: Int): Int? {
        var next: Int? = null
        var previous: Int? = null
        for (i in ranges.indices) {
            val range = ranges[i] ?: continue
            if (charIndex in range) return i
            if (range.first > charIndex && next == null) next = i
            if (range.last < charIndex) previous = i
        }
        return next ?: previous
    }

    fun seekTargetMs(lyric: Lyrics, ranges: List<IntRange?>, charIndex: Int?): Long {
        val lineStart = lyric.timestamp.toLong().coerceAtLeast(0L)
        if (charIndex == null || !lyric.hasWordTiming) return lineStart
        val wordIndex = wordIndexForChar(ranges, charIndex) ?: return lineStart
        val wordStart = lyric.words.getOrNull(wordIndex)?.start?.toLong() ?: return lineStart
        val earliest = minOf(lineStart, wordStart)
        return maxOf(earliest, wordStart - WORD_PREROLL_MS).coerceAtLeast(0L)
    }

    /** Index of the character whose horizontal center is closest to [x]; -1 when there is none. */
    fun nearestCharIndex(centersX: List<Float>, x: Float): Int {
        var best = -1
        var bestDistance = Float.MAX_VALUE
        for (i in centersX.indices) {
            val distance = kotlin.math.abs(centersX[i] - x)
            if (distance < bestDistance) {
                bestDistance = distance
                best = i
            }
        }
        return best
    }

    /** Taps while the user is scrolling, or right after, only stop the list. */
    fun isTapBlocked(userScrolling: Boolean, nowMs: Long, lastUserScrollEndMs: Long): Boolean =
        userScrolling || nowMs - lastUserScrollEndMs in 0 until SCROLL_TAP_GUARD_MS
}
