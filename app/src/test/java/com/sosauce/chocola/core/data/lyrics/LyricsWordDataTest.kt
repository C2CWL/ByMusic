package com.sosauce.chocola.core.data.lyrics

import com.sosauce.chocola.core.domain.model.LyricWord
import com.sosauce.chocola.core.domain.model.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Lyric text here is made-up sample text written for these tests. */
class LyricsWordDataTest {

    @Test
    fun enhancedLrcKeepsWordTimingAndLineText() {
        val raw = "[00:10.00]<00:10.00>晚<00:10.50>风<00:11.00>吹<00:11.40>"
        val line = LyricsTextParser.parseLyricText(raw, LyricSource.SIDECAR).single()
        assertEquals("晚风吹", line.lineLyrics)
        assertEquals(10000, line.timestamp)
        assertTrue(line.hasWordTiming)
        assertEquals(listOf("晚", "风", "吹"), line.words.map { it.text })
        assertEquals(listOf(10000, 10500, 11000), line.words.map { it.start })
        assertEquals(listOf(10500, 11000, 11400), line.words.map { it.end })
    }

    @Test
    fun untimedPrefixBecomesFirstWord() {
        val raw = "[00:10.00]纸船<00:10.50>慢慢<00:11.00>漂远<00:11.60>"
        val line = LyricsTextParser.parseLyricText(raw, LyricSource.SIDECAR).single()
        assertEquals("纸船慢慢漂远", line.lineLyrics)
        assertEquals("纸船慢慢漂远", line.words.joinToString("") { it.text })
        assertEquals(10000, line.words.first().start)
    }

    @Test
    fun plainLrcHasNoWordTiming() {
        val raw = "[00:01.00]第一行\n[00:03.00]第二行"
        val lyrics = LyricsTextParser.parseLyricText(raw, LyricSource.SIDECAR)
        assertEquals(listOf("第一行", "第二行"), lyrics.map { it.lineLyrics })
        assertTrue(lyrics.none { it.hasWordTiming })
    }

    @Test
    fun sameTimeSecondLineIsKeptAsTranslation() {
        val raw = "[00:01.00]晚风吹过河岸\n[00:01.00]The evening wind crosses the river"
        val line = LyricsTextParser.parseLyricText(raw, LyricSource.SIDECAR).single()
        assertEquals("晚风吹过河岸", line.lineLyrics)
        assertEquals("The evening wind crosses the river", line.translation)
    }

    private val ttml = """
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
          <body>
            <div>
              <p begin="00:01.000" end="00:02.000"><span begin="00:01.000" end="00:01.400">纸</span><span begin="00:01.400" end="00:02.000">船</span><span ttm:role="x-translation" xml:lang="en">Paper boat</span></p>
              <p begin="00:03.000" end="00:04.000"><span begin="00:03.000" end="00:03.500">回</span><span begin="00:03.500" end="00:04.000">家</span></p>
            </div>
          </body>
        </tt>
    """.trimIndent()

    @Test
    fun ttmlKeepsLinesWordsAndTranslation() {
        assertTrue(LyricsTextParser.looksLikeTtml(ttml))
        val lyrics = LyricsTextParser.parseTtmlText(ttml)
        assertEquals(listOf("纸船", "回家"), lyrics.map { it.lineLyrics })
        assertEquals(listOf(1000, 3000), lyrics.map { it.timestamp })
        assertEquals(listOf("纸", "船"), lyrics[0].words.map { it.text })
        assertEquals(listOf(1000, 1400), lyrics[0].words.map { it.start })
        assertEquals("Paper boat", lyrics[0].translation)
        assertNull(lyrics[1].translation)
    }

    @Test
    fun ttmlNamedFileWithLrcContentFallsBackToLrc() {
        val lyrics = LyricsTextParser.parseTtmlText("[00:01.00]第一行")
        assertEquals(listOf("第一行"), lyrics.map { it.lineLyrics })
    }

    @Test
    fun brokenTtmlIsEmptyNotCrash() {
        val lyrics = LyricsTextParser.parseTtmlText(ttml) { throw IllegalStateException("bad xml") }
        assertTrue(lyrics.isEmpty())
    }

    @Test
    fun sidecarPrefersLrcThenTtml() {
        val song = "/music/a/song.flac"
        assertEquals(
            listOf("/music/a/song.lrc", "/music/a/song.ttml"),
            LyricsSidecar.candidates(song).map { it.path }
        )
        val both = setOf("/music/a/song.lrc", "/music/a/song.ttml")
        assertEquals(LyricsSidecar.Kind.LRC, LyricsSidecar.find(song) { it in both }?.kind)
        val onlyTtml = setOf("/music/a/song.ttml")
        assertEquals(LyricsSidecar.Kind.TTML, LyricsSidecar.find(song) { it in onlyTtml }?.kind)
        assertNull(LyricsSidecar.find(song) { false })
    }

    @Test
    fun sidecarForNameWithoutExtensionDoesNotPointAtSong() {
        val song = "/music/a/song"
        assertFalse(LyricsSidecar.candidates(song).any { it.path == song })
    }

    @Test
    fun pickedNamesAcceptLrcAndTtml() {
        assertTrue(LyricsSidecar.isSupportedLyricsName("content://x/document/primary%3AMusic%2Fa.lrc"))
        assertTrue(LyricsSidecar.isSupportedLyricsName("content://x/document/primary%3AMusic%2Fa.TTML"))
        assertFalse(LyricsSidecar.isSupportedLyricsName("content://x/document/primary%3AMusic%2Fa.mp3"))
    }

    @Test
    fun manualEditDropsStaleWordTiming() {
        val line = Lyrics(
            timestamp = 1000,
            lineLyrics = "纸船",
            endTimestamp = 2000,
            words = listOf(LyricWord("纸", 1000, 1400), LyricWord("船", 1400, 2000)),
            translation = "Paper boat"
        )
        val edited = line.copy(lineLyrics = "纸鹤").withoutTimedExtras()
        assertFalse(edited.hasWordTiming)
        assertNull(edited.endTimestamp)
        assertEquals("Paper boat", edited.translation)
        assertEquals(line.id, edited.id)
    }
}
