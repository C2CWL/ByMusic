package com.sosauce.chocola.feature.lyrics.presentation

import com.sosauce.chocola.core.domain.model.LyricWord
import com.sosauce.chocola.core.domain.model.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sample lyric text is made up. */
class LyricsTapTargetTest {

    private val wordLine = Lyrics(
        timestamp = 10_000,
        lineLyrics = "晚风，吹过",
        words = listOf(
            LyricWord("晚", 10_000, 10_400),
            LyricWord("风，", 10_400, 11_000),
            LyricWord("吹", 11_500, 12_000),
            LyricWord("过", 12_000, 12_600)
        )
    )
    private val ranges = LyricsKaraoke.wordCharRanges(wordLine.lineLyrics, wordLine.words)

    @Test
    fun tapOnWordStartsAtThatWordWithSmallPreroll() {
        // "吹" is character 3.
        assertEquals(11_500L - LyricsTapTarget.WORD_PREROLL_MS, LyricsTapTarget.seekTargetMs(wordLine, ranges, 3))
    }

    @Test
    fun firstWordNeverStartsBeforeTheLine() {
        assertEquals(10_000L, LyricsTapTarget.seekTargetMs(wordLine, ranges, 0))
    }

    @Test
    fun prerollIsClampedToLineStart() {
        // "风" starts 400 ms in; preroll keeps it after the line start.
        assertEquals(10_320L, LyricsTapTarget.seekTargetMs(wordLine, ranges, 1))
    }

    @Test
    fun plainLrcLineAlwaysStartsAtTheLine() {
        val plain = Lyrics(timestamp = 5_000, lineLyrics = "第一行")
        val plainRanges = LyricsKaraoke.wordCharRanges(plain.lineLyrics, plain.words)
        assertEquals(5_000L, LyricsTapTarget.seekTargetMs(plain, plainRanges, 2))
        assertEquals(5_000L, LyricsTapTarget.seekTargetMs(plain, plainRanges, null))
    }

    @Test
    fun noCharacterMeansLineStart() {
        assertEquals(10_000L, LyricsTapTarget.seekTargetMs(wordLine, ranges, null))
    }

    @Test
    fun untimedCharacterBelongsToNextWordOrPreviousAtEnd() {
        val gapRanges = listOf(0..0, null, 2..2)
        assertEquals(2, LyricsTapTarget.wordIndexForChar(gapRanges, 1))
        assertEquals(2, LyricsTapTarget.wordIndexForChar(gapRanges, 5))
        assertEquals(0, LyricsTapTarget.wordIndexForChar(listOf(0..0), 0))
        assertNull(LyricsTapTarget.wordIndexForChar(listOf(null), 0))
    }

    @Test
    fun nearestCenterWinsSoGapsNeverMiss() {
        val centers = listOf(10f, 40f, 70f)
        assertEquals(0, LyricsTapTarget.nearestCharIndex(centers, -20f))
        assertEquals(1, LyricsTapTarget.nearestCharIndex(centers, 30f))
        assertEquals(2, LyricsTapTarget.nearestCharIndex(centers, 400f))
        assertEquals(-1, LyricsTapTarget.nearestCharIndex(emptyList(), 5f))
    }

    @Test
    fun tapsDuringOrRightAfterUserScrollAreBlocked() {
        assertTrue(LyricsTapTarget.isTapBlocked(userScrolling = true, nowMs = 1_000, lastUserScrollEndMs = 0))
        assertTrue(LyricsTapTarget.isTapBlocked(userScrolling = false, nowMs = 1_100, lastUserScrollEndMs = 1_000))
        assertFalse(LyricsTapTarget.isTapBlocked(userScrolling = false, nowMs = 1_500, lastUserScrollEndMs = 1_000))
        assertFalse(LyricsTapTarget.isTapBlocked(userScrolling = false, nowMs = 1_000, lastUserScrollEndMs = Long.MIN_VALUE / 2))
    }
}
