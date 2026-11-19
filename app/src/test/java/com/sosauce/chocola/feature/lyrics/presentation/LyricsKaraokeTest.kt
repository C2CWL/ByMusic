package com.sosauce.chocola.feature.lyrics.presentation

import com.sosauce.chocola.core.domain.model.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Sample lyric text is made up. */
class LyricsKaraokeTest {

    private val text = "晚风吹过"
    private val words = listOf(
        LyricWord("晚", 1000, 1400),
        LyricWord("风", 1400, 2000),
        LyricWord("吹过", 2500, 3000)
    )
    private val ranges = LyricsKaraoke.wordCharRanges(text, words)

    @Test
    fun wordsMapToCharacterRanges() {
        assertEquals(listOf(0..0, 1..1, 2..3), ranges)
    }

    @Test
    fun nothingFilledBeforeFirstWord() {
        assertEquals(0f, LyricsKaraoke.filledChars(text.length, words, ranges, 500), 0.0001f)
    }

    @Test
    fun fillMovesSmoothlyInsideAWord() {
        assertEquals(1.5f, LyricsKaraoke.filledChars(text.length, words, ranges, 1700), 0.0001f)
    }

    @Test
    fun fillHoldsInGapBetweenWords() {
        assertEquals(2f, LyricsKaraoke.filledChars(text.length, words, ranges, 2200), 0.0001f)
    }

    @Test
    fun multiCharWordFillsAcrossItsCharacters() {
        assertEquals(3f, LyricsKaraoke.filledChars(text.length, words, ranges, 2750), 0.0001f)
    }

    @Test
    fun everythingFilledAfterLastWord() {
        assertEquals(4f, LyricsKaraoke.filledChars(text.length, words, ranges, 9000), 0.0001f)
    }

    @Test
    fun englishWordsWithSpacesAreFound() {
        val en = "paper boat home"
        val enWords = listOf(LyricWord("paper ", 0, 100), LyricWord("boat ", 100, 200), LyricWord("home", 200, 300))
        assertEquals(listOf(0..4, 6..9, 11..14), LyricsKaraoke.wordCharRanges(en, enWords))
    }

    @Test
    fun unmatchedWordIsSkippedNotCrash() {
        val r = LyricsKaraoke.wordCharRanges("纸船", listOf(LyricWord("别的", 0, 10), LyricWord("船", 10, 20)))
        assertNull(r[0])
        assertEquals(1..1, r[1])
        assertTrue(LyricsKaraoke.canFill(r))
        assertFalse(LyricsKaraoke.canFill(listOf(null)))
    }

    @Test
    fun lineStyleIsCalm() {
        assertEquals(1f, LyricsLineStyle.alphaFor(0, passed = false, onArtwork = false), 0f)
        assertTrue(LyricsLineStyle.alphaFor(2, false, false) < LyricsLineStyle.alphaFor(1, false, false))
        assertEquals(0f, LyricsLineStyle.blurFor(0, onArtwork = false), 0f)
        assertEquals(0f, LyricsLineStyle.blurFor(3, onArtwork = true), 0f)
        assertTrue(LyricsLineStyle.inactiveScale(false) < 1f)
    }
}
