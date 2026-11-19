package com.sosauce.chocola.feature.lyrics.presentation

import com.sosauce.chocola.core.domain.model.LyricWord

/**
 * Pure helpers for word-by-word fill. A line's words are mapped onto character
 * ranges of the displayed line text; the fill position is a fractional character
 * index that grows smoothly with time inside the word being sung.
 */
object LyricsKaraoke {

    /** Character range [start, end) of each word inside [text]; null when a word cannot be placed. */
    fun wordCharRanges(text: String, words: List<LyricWord>): List<IntRange?> {
        var cursor = 0
        return words.map { word ->
            val needle = word.text.trim()
            if (needle.isEmpty()) return@map null
            val at = text.indexOf(needle, cursor)
            if (at < 0) {
                null
            } else {
                cursor = at + needle.length
                at until cursor
            }
        }
    }

    /**
     * Fractional character index reached at [positionMs]: everything before it is sung.
     * Returns 0 before the first word and the text length after the last one.
     */
    fun filledChars(
        textLength: Int,
        words: List<LyricWord>,
        ranges: List<IntRange?>,
        positionMs: Long
    ): Float {
        if (textLength <= 0 || words.isEmpty()) return 0f
        var filled = 0f
        for (i in words.indices) {
            val range = ranges.getOrNull(i) ?: continue
            val word = words[i]
            if (positionMs < word.start) break
            val length = (range.last + 1 - range.first).toFloat()
            filled = if (positionMs >= word.end || word.end <= word.start) {
                (range.last + 1).toFloat()
            } else {
                val progress = (positionMs - word.start).toFloat() / (word.end - word.start).toFloat()
                range.first + length * progress.coerceIn(0f, 1f)
            }
        }
        return filled.coerceIn(0f, textLength.toFloat())
    }

    /** Whether word fill can be drawn for this line at all. */
    fun canFill(ranges: List<IntRange?>): Boolean = ranges.any { it != null }
}
