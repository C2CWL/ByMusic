package com.sosauce.chocola.core.data.lyrics

import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.core.model.synced.mapper.toSyncedLine
import com.mocharealm.accompanist.lyrics.core.parser.AutoParser
import com.mocharealm.accompanist.lyrics.core.parser.EnhancedLrcParser
import com.mocharealm.accompanist.lyrics.core.parser.TTMLParser
import com.sosauce.chocola.core.data.diagnostics.DiagLogger
import com.sosauce.chocola.core.domain.model.LyricWord
import com.sosauce.chocola.core.domain.model.Lyrics
import java.nio.charset.StandardCharsets
import kotlin.coroutines.cancellation.CancellationException

internal enum class LyricSource {
    SIDECAR,
    EMBEDDED
}

/**
 * Parses lyric text without touching song paths or Android APIs.
 *
 * lyrics-core 0.5.0 throws [IllegalArgumentException] ("Failed requirement.") when it
 * builds a line or syllable with end < start. That happens for recoverable, if messy,
 * LRC: out-of-order line timestamps, or enhanced syllable tags that go backwards.
 * This parser retries after normalizing those cases, then falls back to a lenient LRC
 * reader. Only text that still cannot yield lines is treated as unrecoverable.
 */
internal object LyricsTextParser {

    private val autoParser = AutoParser()
    private val leadingTagRegex = Regex("""\[(.*?)\]""")
    private val timestampPattern = Regex("""\d+([:.]\d+)+""")
    private val angleSyllableRegex = Regex("""<([^>]+)>([^<]*)""")
    private val squareSyllableRegex = Regex("""\[([^\]]+)\]([^\[]*)""")
    private val metadataRegex = Regex("""^\[([a-zA-Z]+):\s*(.*)]\s*$""")
    private val metadataTags = setOf("ar", "ti", "al", "offset", "length")

    fun decodeLyricBytes(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, StandardCharsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, StandardCharsets.UTF_16BE)
        }
        return String(bytes, StandardCharsets.UTF_8)
    }

    fun splitLines(raw: String): List<String> {
        if (raw.isEmpty()) return emptyList()
        val text = raw.trimStart('\uFEFF').replace("\r\n", "\n").replace('\r', '\n')
        return text.split('\n')
    }

    fun parseLyricText(
        raw: String,
        source: LyricSource,
        officialParse: (List<String>) -> List<Lyrics> = { officialParseOrThrow(source, it) }
    ): List<Lyrics> {
        val originalLines = splitLines(raw).map(::preserveUntimedPrefix)
        val officialFirst = runOfficial(originalLines, officialParse)
        if (officialFirst is OfficialResult.Success && officialFirst.lyrics.isNotEmpty()) {
            return officialFirst.lyrics
        }

        val normalizedLines = normalizeLyricLines(originalLines)
        if (normalizedLines != originalLines) {
            val officialNormalized = runOfficial(normalizedLines, officialParse)
            if (officialNormalized is OfficialResult.Success && officialNormalized.lyrics.isNotEmpty()) {
                logRecovered(source, raw, originalLines, cause = (officialFirst as? OfficialResult.Failed)?.error, how = "normalized")
                return officialNormalized.lyrics
            }
            if (officialNormalized is OfficialResult.Failed && officialFirst !is OfficialResult.Failed) {
                // Keep going to the lenient path.
            }
        }

        val lenient = parseLenientLrc(normalizedLines)
        if (lenient.isNotEmpty()) {
            logRecovered(source, raw, originalLines, cause = (officialFirst as? OfficialResult.Failed)?.error, how = "lenient")
            return lenient
        }

        if (source == LyricSource.EMBEDDED && raw.isNotBlank() && officialFirst is OfficialResult.Success) {
            // Same as the previous behavior: unsynced embedded text is shown as one block.
            return listOf(Lyrics(lineLyrics = raw))
        }

        if (officialFirst is OfficialResult.Failed) {
            logUnrecoverable(source, raw, originalLines, officialFirst.error)
        }
        return emptyList()
    }

    internal fun officialParseOrThrow(source: LyricSource, lines: List<String>): List<Lyrics> {
        val parsed = if (source == LyricSource.SIDECAR) {
            EnhancedLrcParser.parse(lines)
        } else {
            autoParser.parse(lines.joinToString("\n"))
        }
        return mapLines(parsed.lines)
    }

    internal fun <T> runCancellableParse(block: () -> T): T {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        }
        // Other exceptions propagate to the caller so it can choose recovery or fallback.
    }

    private fun runOfficial(lines: List<String>, officialParse: (List<String>) -> List<Lyrics>): OfficialResult {
        return try {
            OfficialResult.Success(runCancellableParse { officialParse(lines) })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            OfficialResult.Failed(e)
        }
    }

    internal fun mapLines(lines: List<ISyncedLine>): List<Lyrics> {
        return lines.mapNotNull { line ->
            when (line) {
                is SyncedLine -> Lyrics(
                    timestamp = line.start,
                    lineLyrics = line.content,
                    endTimestamp = line.end,
                    translation = line.translation.cleanExtra(),
                    phonetic = line.phonetic.cleanExtra()
                )
                is KaraokeLine -> {
                    // Line text stays exactly what the old flattening produced.
                    val synced = line.toSyncedLine()
                    Lyrics(
                        timestamp = synced.start,
                        lineLyrics = synced.content,
                        endTimestamp = line.end,
                        words = line.syllables
                            .filter { it.content.isNotEmpty() }
                            .map { LyricWord(it.content, it.start, it.end, it.phonetic.cleanExtra()) },
                        translation = line.translation.cleanExtra(),
                        phonetic = line.phonetic.cleanExtra(),
                        isAccompaniment = line is KaraokeLine.AccompanimentKaraokeLine
                    )
                }
                else -> null
            }
        }
    }

    private fun String?.cleanExtra(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private val ttmlParser = TTMLParser()

    fun looksLikeTtml(raw: String): Boolean = ttmlParser.canParse(raw)

    /**
     * Parses a TTML sidecar or imported file. Text that is not TTML (for example an LRC
     * file saved with a .ttml name) goes through the normal LRC path instead.
     */
    fun parseTtmlText(
        raw: String,
        officialParse: (String) -> List<Lyrics> = { mapLines(ttmlParser.parse(it).lines) }
    ): List<Lyrics> {
        val text = raw.trimStart('\uFEFF')
        if (!looksLikeTtml(text)) return parseLyricText(text, LyricSource.SIDECAR)
        return try {
            runCancellableParse { officialParse(text) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagLogger.w(
                "Lyrics",
                "ttml parse failed chars=${text.length} error=${e::class.java.simpleName}: ${e.message ?: ""}",
                e
            )
            emptyList()
        }
    }

    /**
     * Makes LRC timestamps monotonic so lyrics-core's require(end >= start) can succeed:
     * sort timestamped line groups, and clamp enhanced syllable times that go backwards.
     */
    fun normalizeLyricLines(lines: List<String>): List<String> {
        val cleaned = lines.map { it.trimStart('\uFEFF') }
        val prefix = mutableListOf<String>()
        val groups = mutableListOf<TimedGroup>()
        var current: TimedGroup? = null

        for (line in cleaned) {
            when {
                isMetadataLine(line) || (line.isBlank() && current == null) -> {
                    if (current == null) prefix.add(line) else current.lines.add(line)
                }
                firstLineTimestamp(line) != null -> {
                    current = TimedGroup(firstLineTimestamp(line)!!, mutableListOf(clampSyllableTimes(line)))
                    groups.add(current)
                }
                else -> {
                    if (current != null) {
                        current.lines.add(clampSyllableTimes(line))
                    } else {
                        prefix.add(line)
                    }
                }
            }
        }

        val sorted = groups.withIndex().sortedWith(compareBy<IndexedValue<TimedGroup>> { it.value.start }.thenBy { it.index })
        return prefix + sorted.flatMap { it.value.lines }
    }

    fun parseLenientLrc(lines: List<String>): List<Lyrics> {
        val result = ArrayList<Lyrics>()
        for (line in lines) {
            if (isMetadataLine(line) || line.isBlank()) continue
            val extracted = extractTimedTexts(line)
            for ((start, text) in extracted) {
                if (text.isNotBlank()) {
                    result.add(Lyrics(timestamp = start, lineLyrics = text))
                }
            }
        }
        return result.withIndex()
            .sortedWith(compareBy<IndexedValue<Lyrics>> { it.value.timestamp }.thenBy { it.index })
            .map { it.value }
    }

    fun hasInvertedLineTimes(lines: List<String>): Boolean {
        var previous: Int? = null
        for (line in lines) {
            val time = firstLineTimestamp(line) ?: continue
            if (previous != null && time < previous) return true
            previous = time
        }
        return false
    }

    fun hasInvertedSyllableTimes(lines: List<String>): Boolean {
        return lines.any { lineHasInvertedSyllables(it) }
    }

    internal fun logUnrecoverable(source: LyricSource, raw: String, lines: List<String>, error: Throwable) {
        DiagLogger.w(
            "Lyrics",
            "unrecoverable lyrics parse source=${source.name.lowercase()} " +
                "chars=${raw.length} lines=${lines.size} " +
                "invertedLineTimes=${hasInvertedLineTimes(lines)} " +
                "invertedSyllableTimes=${hasInvertedSyllableTimes(lines)} " +
                "error=${error::class.java.simpleName}: ${error.message ?: ""}",
            error
        )
    }

    private fun logRecovered(
        source: LyricSource,
        raw: String,
        lines: List<String>,
        cause: Throwable?,
        how: String
    ) {
        DiagLogger.i(
            "Lyrics",
            "recovered lyrics parse source=${source.name.lowercase()} how=$how " +
                "chars=${raw.length} lines=${lines.size} " +
                "invertedLineTimes=${hasInvertedLineTimes(lines)} " +
                "invertedSyllableTimes=${hasInvertedSyllableTimes(lines)} " +
                "cause=${cause?.let { "${it::class.java.simpleName}: ${it.message ?: ""}" } ?: "empty-official"}"
        )
    }

    private fun isMetadataLine(line: String): Boolean {
        val match = metadataRegex.find(line.trim()) ?: return false
        return match.groupValues[1] in metadataTags
    }

    private fun firstLineTimestamp(line: String): Int? {
        val leading = leadingTags(line)
        for (tag in leading) {
            if (isTimestamp(tag)) return parseAsTime(tag)
        }
        return null
    }

    private fun leadingTags(line: String): List<String> {
        val tags = mutableListOf<String>()
        var lastEnd = 0
        for (match in leadingTagRegex.findAll(line)) {
            val prefix = line.substring(lastEnd, match.range.first)
            if (prefix.isBlank()) {
                tags.add(match.groupValues[1].trim())
                lastEnd = match.range.last + 1
            } else {
                break
            }
        }
        return tags
    }

    private fun contentAfterLeadingTags(line: String): Pair<Int, String> {
        var lastEnd = 0
        for (match in leadingTagRegex.findAll(line)) {
            val prefix = line.substring(lastEnd, match.range.first)
            if (prefix.isBlank()) {
                lastEnd = match.range.last + 1
            } else {
                break
            }
        }
        val content = if (lastEnd < line.length) line.substring(lastEnd) else ""
        return lastEnd to content
    }

    private fun clampSyllableTimes(line: String): String {
        if (!lineHasInvertedSyllables(line)) return line
        val (contentStart, content) = contentAfterLeadingTags(line)
        if (content.isEmpty()) return line
        val rewritten = rewriteSyllableContent(content) ?: return line
        return line.substring(0, contentStart) + rewritten
    }

    private fun lineHasInvertedSyllables(line: String): Boolean {
        val content = contentAfterLeadingTags(line).second
        val times = syllableTimes(content)
        for (i in 1 until times.size) {
            if (times[i] < times[i - 1]) return true
        }
        return false
    }

    private fun syllableTimes(content: String): List<Int> {
        val regex = syllableRegex(content)
        val times = mutableListOf<Int>()
        for (match in regex.findAll(content)) {
            val ts = match.groupValues[1].trim()
            if (isTimestamp(ts)) times.add(parseAsTime(ts))
        }
        return times
    }

    private fun rewriteSyllableContent(content: String): String? {
        val regex = syllableRegex(content)
        var last = 0
        var changed = false
        val builder = StringBuilder()
        var cursor = 0
        for (match in regex.findAll(content)) {
            val rawTs = match.groupValues[1]
            val ts = rawTs.trim()
            if (!isTimestamp(ts)) continue
            val time = parseAsTime(ts)
            val clamped = if (time < last) last else time
            if (clamped != time) changed = true
            last = clamped
            builder.append(content, cursor, match.groups[1]!!.range.first)
            builder.append(formatTime(clamped, ts))
            cursor = match.groups[1]!!.range.last + 1
        }
        if (!changed) return null
        builder.append(content, cursor, content.length)
        return builder.toString()
    }

    private fun syllableRegex(content: String): Regex {
        return if (content.contains(Regex("<\\d+"))) angleSyllableRegex else squareSyllableRegex
    }

    private fun extractTimedTexts(line: String): List<Pair<Int, String>> {
        val leading = leadingTags(line)
        val starts = leading.mapNotNull { tag -> if (isTimestamp(tag)) parseAsTime(tag) else null }
        if (starts.isEmpty()) return emptyList()
        val content = contentAfterLeadingTags(line).second.trim()
        val text = stripSyllableTags(content).ifBlank { content }.trim()
        return starts.map { it to text }
    }

    /** Keep text before the first syllable tag; lyrics-core otherwise drops it. */
    private fun preserveUntimedPrefix(line: String): String {
        val start = firstLineTimestamp(line) ?: return line
        val (contentStart, content) = contentAfterLeadingTags(line)
        val firstSyllable = syllableRegex(content).findAll(content)
            .firstOrNull { isTimestamp(it.groupValues[1]) } ?: return line
        if (content.substring(0, firstSyllable.range.first).isBlank()) return line
        val timestamp = formatTime(start, "00:00.000")
        // An extra square tag at the beginning would be read as another line
        // timestamp, so normalize square syllables to angle tags first.
        val timedContent = if (firstSyllable.value.startsWith("[")) {
            squareSyllableRegex.replace(content) { match ->
                if (isTimestamp(match.groupValues[1])) {
                    "<${match.groupValues[1]}>${match.groupValues[2]}"
                } else match.value
            }
        } else content
        return line.substring(0, contentStart) + "<$timestamp>" + timedContent
    }

    private fun stripSyllableTags(content: String): String {
        return syllableRegex(content).replace(content) { match ->
            if (isTimestamp(match.groupValues[1])) match.groupValues[2] else match.value
        }
    }

    private fun isTimestamp(value: String): Boolean = timestampPattern.matches(value.trim())

    /**
     * Matches lyrics-core TimeUtils.parseAsTime so rewritten tags stay consistent.
     */
    internal fun parseAsTime(value: String): Int {
        val text = value.trim()
        if (text.isEmpty()) return 0
        val firstColon = text.indexOf(':')
        if (firstColon == -1) return parseSecondsAndMillis(text, 0, text.length)
        val lastColon = text.lastIndexOf(':')
        return if (firstColon == lastColon) {
            digitsToInt(text, 0, firstColon) * 60_000 +
                parseSecondsAndMillis(text, firstColon + 1, text.length)
        } else {
            digitsToInt(text, 0, firstColon) * 3_600_000 +
                digitsToInt(text, firstColon + 1, lastColon) * 60_000 +
                parseSecondsAndMillis(text, lastColon + 1, text.length)
        }
    }

    private fun parseSecondsAndMillis(text: String, start: Int, end: Int): Int {
        var dot = -1
        for (i in start until end) {
            if (text[i] == '.') {
                dot = i
                break
            }
        }
        if (dot == -1) return digitsToInt(text, start, end) * 1000
        val seconds = digitsToInt(text, start, dot) * 1000
        val millisStart = dot + 1
        val millisLen = end - millisStart
        if (millisLen <= 0) return seconds
        val take = if (millisLen >= 3) 3 else millisLen
        var millis = digitsToInt(text, millisStart, millisStart + take)
        if (millisLen == 1) millis *= 100 else if (millisLen == 2) millis *= 10
        return seconds + millis
    }

    private fun digitsToInt(text: String, start: Int, end: Int): Int {
        var value = 0
        for (i in start until end) {
            val ch = text[i]
            if (ch in '0'..'9') value = value * 10 + (ch - '0')
        }
        return value
    }

    private fun formatTime(millis: Int, sample: String): String {
        val safe = millis.coerceAtLeast(0)
        val totalSeconds = safe / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val rest = safe % 1000
        val fraction = sample.substringAfter('.', missingDelimiterValue = "")
        val body = minutes.toString().padStart(2, '0') + ":" + seconds.toString().padStart(2, '0')
        if (fraction.isEmpty()) return body
        val width = fraction.length.coerceIn(2, 3)
        val fracValue = if (width == 2) rest / 10 else rest
        return "$body.${fracValue.toString().padStart(width, '0')}"
    }

    private data class TimedGroup(val start: Int, val lines: MutableList<String>)

    private sealed class OfficialResult {
        data class Success(val lyrics: List<Lyrics>) : OfficialResult()
        data class Failed(val error: Throwable) : OfficialResult()
    }
}
