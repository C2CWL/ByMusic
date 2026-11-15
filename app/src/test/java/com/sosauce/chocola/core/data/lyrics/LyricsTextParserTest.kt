package com.sosauce.chocola.core.data.lyrics

import com.mocharealm.accompanist.lyrics.core.parser.EnhancedLrcParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.coroutines.cancellation.CancellationException

class LyricsTextParserTest {

    private val normalLrc = """
        [ti:example]
        [00:00.00]hello
        [00:05.50]world
    """.trimIndent()

    private val outOfOrderLrc = """
        [ti:example]
        [00:12.00]second
        [00:10.00]first
    """.trimIndent()

    private val invertedSyllableLrc = "[00:10.00]<00:10.00>Hel<00:09.00>lo"

    @Test
    fun officialParserThrowsFailedRequirementOnOutOfOrderLineTimes() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            EnhancedLrcParser.parse(LyricsTextParser.splitLines(outOfOrderLrc))
        }
        assertEquals("Failed requirement.", error.message)
    }

    @Test
    fun officialParserThrowsFailedRequirementOnInvertedSyllableTimes() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            EnhancedLrcParser.parse(listOf(invertedSyllableLrc))
        }
        assertEquals("Failed requirement.", error.message)
    }

    @Test
    fun normalLyricsAreKeptWithTimestamps() {
        val lyrics = LyricsTextParser.parseLyricText(normalLrc, LyricSource.SIDECAR)
        assertEquals(listOf("hello", "world"), lyrics.map { it.lineLyrics })
        assertEquals(0, lyrics[0].timestamp)
        assertEquals(5500, lyrics[1].timestamp)
    }

    @Test
    fun outOfOrderLineTimesAreRecoveredAndKept() {
        assertTrue(LyricsTextParser.hasInvertedLineTimes(LyricsTextParser.splitLines(outOfOrderLrc)))
        val lyrics = LyricsTextParser.parseLyricText(outOfOrderLrc, LyricSource.SIDECAR)
        assertEquals(listOf("first", "second"), lyrics.map { it.lineLyrics })
        assertEquals(10000, lyrics[0].timestamp)
        assertEquals(12000, lyrics[1].timestamp)
    }

    @Test
    fun invertedSyllableTimesAreRecoveredAndTextIsKept() {
        assertTrue(LyricsTextParser.hasInvertedSyllableTimes(listOf(invertedSyllableLrc)))
        val lyrics = LyricsTextParser.parseLyricText(invertedSyllableLrc, LyricSource.SIDECAR)
        assertEquals(1, lyrics.size)
        assertEquals("Hello", lyrics[0].lineLyrics)
        assertEquals(10000, lyrics[0].timestamp)
    }

    @Test
    fun sidecarWithoutTimestampsIsEmpty() {
        val lyrics = LyricsTextParser.parseLyricText("just plain text\nwithout times", LyricSource.SIDECAR)
        assertTrue(lyrics.isEmpty())
    }

    @Test
    fun embeddedUnsyncedTextIsKeptAsPlainLyrics() {
        val lyrics = LyricsTextParser.parseLyricText("plain unsynced verse", LyricSource.EMBEDDED)
        assertEquals(listOf("plain unsynced verse"), lyrics.map { it.lineLyrics })
        assertEquals(0, lyrics[0].timestamp)
    }

    @Test
    fun utf8BomDoesNotDropNormalLyrics() {
        val raw = "\uFEFF[00:00.00]hello\n[00:01.00]world"
        val lyrics = LyricsTextParser.parseLyricText(raw, LyricSource.SIDECAR)
        assertEquals(listOf("hello", "world"), lyrics.map { it.lineLyrics })
    }

    @Test
    fun decodeUtf16LeBomSidecar() {
        val text = "[00:00.00]hello"
        val encoded = text.toByteArray(Charsets.UTF_16LE)
        val bytes = if (encoded.size >= 2 && encoded[0] == 0xFF.toByte() && encoded[1] == 0xFE.toByte()) {
            encoded
        } else {
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + encoded
        }
        val decoded = LyricsTextParser.decodeLyricBytes(bytes)
        val lyrics = LyricsTextParser.parseLyricText(decoded, LyricSource.SIDECAR)
        assertEquals(listOf("hello"), lyrics.map { it.lineLyrics })
    }

    @Test
    fun textBeforeFirstAngleSyllableIsPreserved() {
        val raw = "[00:10.00]这些年<00:10.50>有的感情<00:11.00>变淡"
        for (source in LyricSource.entries) {
            val lyrics = LyricsTextParser.parseLyricText(raw, source)
            assertEquals("这些年有的感情变淡", lyrics.single().lineLyrics)
            assertEquals(10000, lyrics.single().timestamp)
        }
    }

    @Test
    fun textBeforeFirstSquareSyllableIsPreserved() {
        val raw = "[00:10.00]手机里[00:10.50]收藏的视频[00:11.00]不知道分享给谁看"
        val lyrics = LyricsTextParser.parseLyricText(raw, LyricSource.SIDECAR)
        assertEquals("手机里收藏的视频不知道分享给谁看", lyrics.single().lineLyrics)
        assertEquals(10000, lyrics.single().timestamp)
    }

    @Test
    fun lenientFallbackAlsoKeepsUntimedPrefix() {
        for (raw in listOf(
            "[00:10.00]这些年<00:10.50>有的感情<00:11.00>变淡",
            "[00:10.00]这些年[00:10.50]有的感情[00:11.00]变淡"
        )) {
            val lyrics = LyricsTextParser.parseLyricText(raw, LyricSource.SIDECAR) {
                throw IllegalArgumentException("force fallback")
            }
            assertEquals("这些年有的感情变淡", lyrics.single().lineLyrics)
            assertEquals(10000, lyrics.single().timestamp)
        }
    }

    @Test
    fun strippingTagsDoesNotDropPrefixOrLiteralBrackets() {
        val lyrics = LyricsTextParser.parseLenientLrc(
            listOf("[00:10.00]前文[注释]<00:10.50>后文")
        )
        assertEquals("前文[注释]后文", lyrics.single().lineLyrics)
    }

    @Test
    fun cancellationIsRethrownAndDoesNotBecomeEmptyLyrics() {
        try {
            LyricsTextParser.parseLyricText(normalLrc, LyricSource.SIDECAR) {
                throw CancellationException("cancelled-parse")
            }
            fail("CancellationException must be rethrown")
        } catch (error: CancellationException) {
            assertEquals("cancelled-parse", error.message)
        }
    }

    @Test
    fun unrecoverableOfficialFailureFallsBackToEmptyForSidecar() {
        val lyrics = LyricsTextParser.parseLyricText("[[[[", LyricSource.SIDECAR) {
            throw IllegalArgumentException("Failed requirement.")
        }
        assertTrue(lyrics.isEmpty())
    }

    @Test
    fun officialSuccessIsNotReplacedByLenientPath() {
        val lyrics = LyricsTextParser.parseLyricText(normalLrc, LyricSource.SIDECAR)
        assertFalse(lyrics.isEmpty())
        assertEquals(2, lyrics.size)
    }
}
