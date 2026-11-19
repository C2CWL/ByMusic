package com.sosauce.chocola.core.data.lyrics

import com.sosauce.chocola.core.domain.model.Lyrics
import kotlin.math.abs

/**
 * Puts a separate translation LRC (MusicFree `getLyric().translation`) onto the matching
 * original lines, so the lyrics page's "译" switch can show it. Pure Kotlin.
 *
 * A translation line belongs to the original line with the closest start time, at most
 * [toleranceMs] away; each translation line is used once. Lines that already carry a
 * translation (e.g. a two-language LRC) keep theirs. Placeholder lines such as "//" are
 * ignored.
 */
object LyricsTranslationMerger {
    const val DEFAULT_TOLERANCE_MS = 300

    private val PLACEHOLDERS = setOf("//", "/", "-", "—")

    fun merge(
        original: List<Lyrics>,
        translation: List<Lyrics>,
        toleranceMs: Int = DEFAULT_TOLERANCE_MS
    ): List<Lyrics> {
        if (original.isEmpty() || translation.isEmpty()) return original
        val candidates = translation
            .map { it.timestamp to it.lineLyrics.trim() }
            .filter { (_, text) -> text.isNotEmpty() && text !in PLACEHOLDERS }
            .sortedBy { it.first }
        if (candidates.isEmpty()) return original
        val used = BooleanArray(candidates.size)
        return original.map { line ->
            if (line.translation != null || line.lineLyrics.isBlank()) return@map line
            var best = -1
            var bestDistance = Int.MAX_VALUE
            for (i in candidates.indices) {
                if (used[i]) continue
                val d = abs(candidates[i].first - line.timestamp)
                if (d < bestDistance) {
                    bestDistance = d
                    best = i
                }
                if (candidates[i].first > line.timestamp + toleranceMs) break
            }
            if (best < 0 || bestDistance > toleranceMs) return@map line
            val text = candidates[best].second
            if (text == line.lineLyrics.trim()) return@map line
            used[best] = true
            line.copy(translation = text)
        }
    }
}
