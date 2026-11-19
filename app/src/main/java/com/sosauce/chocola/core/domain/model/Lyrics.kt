package com.sosauce.chocola.core.domain.model

import kotlinx.serialization.Serializable
import kotlin.random.Random

/**
 * One timed piece of a lyric line (a character, syllable or word).
 * Times are in milliseconds from the start of the track.
 */
@Serializable
data class LyricWord(
    val text: String,
    val start: Int,
    val end: Int,
    val phonetic: String? = null
)

/**
 * A lyric line. [timestamp] and [lineLyrics] keep their original meaning so existing
 * screens keep working. The other fields are optional extras kept from the parser:
 * [words] holds word-by-word timing when the source has it (enhanced LRC, TTML, ...),
 * [translation] and [phonetic] hold a translated or romanized line when present.
 */
@Serializable
data class Lyrics(
    val timestamp: Int = 0,
    val lineLyrics: String = "",
    val id: Int = Random.nextInt(),
    val endTimestamp: Int? = null,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val phonetic: String? = null,
    val isAccompaniment: Boolean = false
) {
    val hasWordTiming: Boolean get() = words.isNotEmpty()

    /** Drops data that is no longer true after the line text or start time is edited by hand. */
    fun withoutTimedExtras(): Lyrics = copy(words = emptyList(), endTimestamp = null)
}
