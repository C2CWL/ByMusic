package com.sosauce.chocola.core.data.lyrics

import com.sosauce.chocola.core.domain.model.Lyrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class LyricsTranslationMergerTest {

    private fun line(t: Int, text: String, translation: String? = null) =
        Lyrics(timestamp = t, lineLyrics = text, id = t, translation = translation)

    @Test
    fun attachesByClosestTimestamp() {
        val original = listOf(line(1000, "Hello"), line(5000, "World"), line(9000, "Bye"))
        val translation = listOf(line(1000, "你好"), line(5120, "世界"), line(20_000, "太远"))
        val merged = LyricsTranslationMerger.merge(original, translation)
        assertEquals(listOf("你好", "世界", null), merged.map { it.translation })
        assertEquals(original.map { it.lineLyrics }, merged.map { it.lineLyrics })
        assertEquals(original.map { it.timestamp }, merged.map { it.timestamp })
    }

    @Test
    fun keepsExistingTranslationsAndSkipsPlaceholders() {
        val original = listOf(line(0, "A", translation = "已有"), line(2000, "B"), line(4000, "C"))
        val translation = listOf(line(0, "新的"), line(2000, "//"), line(4000, "C"))
        val merged = LyricsTranslationMerger.merge(original, translation)
        assertEquals("已有", merged[0].translation)
        assertNull(merged[1].translation)
        // Same text as the original is not shown twice.
        assertNull(merged[2].translation)
    }

    @Test
    fun eachTranslationLineIsUsedOnce() {
        val original = listOf(line(1000, "X"), line(1100, "Y"))
        val merged = LyricsTranslationMerger.merge(original, listOf(line(1050, "只有一行")))
        assertEquals(1, merged.count { it.translation == "只有一行" })
    }

    @Test
    fun emptyInputsReturnTheOriginal() {
        val original = listOf(line(0, "A"))
        assertSame(original, LyricsTranslationMerger.merge(original, emptyList()))
        assertEquals(emptyList<Lyrics>(), LyricsTranslationMerger.merge(emptyList(), original))
    }
}
