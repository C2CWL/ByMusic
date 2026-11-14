package com.sosauce.chocola.core.data.diagnostics

import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Pure formatting for [android.app.ApplicationExitInfo] values so it can be unit tested
 * without Android framework APIs. Reason codes match the public ApplicationExitInfo constants.
 */
data class DiagExitSnapshot(
    val timestampMillis: Long,
    val pid: Int,
    val processName: String?,
    val reasonCode: Int,
    val status: Int,
    val importance: Int,
    val pssKb: Long?,
    val description: String?,
    val traceExcerpt: String?
)

object DiagExitReasons {
    const val REASON_UNKNOWN = 0
    const val REASON_EXIT_SELF = 1
    const val REASON_SIGNALED = 2
    const val REASON_LOW_MEMORY = 3
    const val REASON_CRASH = 4
    const val REASON_CRASH_NATIVE = 5
    const val REASON_ANR = 6
    const val REASON_INITIALIZATION_FAILURE = 7
    const val REASON_PERMISSION_CHANGE = 8
    const val REASON_EXCESSIVE_RESOURCE_USAGE = 9
    const val REASON_USER_REQUESTED = 10
    const val REASON_USER_STOPPED = 11
    const val REASON_DEPENDENCY_DIED = 12
    const val REASON_OTHER = 13
    const val REASON_FREEZER = 14
    const val REASON_PACKAGE_STATE_CHANGE = 15
    const val REASON_PACKAGE_UPDATED = 16

    const val MAX_TRACE_CHARS = 4000

    fun reasonLabel(code: Int): String = when (code) {
        REASON_UNKNOWN -> "UNKNOWN"
        REASON_EXIT_SELF -> "EXIT_SELF"
        REASON_SIGNALED -> "SIGNALED"
        REASON_LOW_MEMORY -> "LOW_MEMORY"
        REASON_CRASH -> "CRASH"
        REASON_CRASH_NATIVE -> "CRASH_NATIVE"
        REASON_ANR -> "ANR"
        REASON_INITIALIZATION_FAILURE -> "INITIALIZATION_FAILURE"
        REASON_PERMISSION_CHANGE -> "PERMISSION_CHANGE"
        REASON_EXCESSIVE_RESOURCE_USAGE -> "EXCESSIVE_RESOURCE_USAGE"
        REASON_USER_REQUESTED -> "USER_REQUESTED"
        REASON_USER_STOPPED -> "USER_STOPPED"
        REASON_DEPENDENCY_DIED -> "DEPENDENCY_DIED"
        REASON_OTHER -> "OTHER"
        REASON_FREEZER -> "FREEZER"
        REASON_PACKAGE_STATE_CHANGE -> "PACKAGE_STATE_CHANGE"
        REASON_PACKAGE_UPDATED -> "PACKAGE_UPDATED"
        else -> "REASON_$code"
    }

    fun format(snapshot: DiagExitSnapshot, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        val time = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
            .format(java.time.Instant.ofEpochMilli(snapshot.timestampMillis).atZone(zone))
        val desc = snapshot.description?.let { DiagLogSanitizer.sanitize(it) } ?: "-"
        val pss = snapshot.pssKb?.let { "${it}kB" } ?: "-"
        val builder = StringBuilder()
        builder.append("time=").append(time)
            .append(" pid=").append(snapshot.pid)
            .append(" process=").append(snapshot.processName ?: "-")
            .append(" reason=").append(reasonLabel(snapshot.reasonCode))
            .append("(").append(snapshot.reasonCode).append(")")
            .append(" status=").append(snapshot.status)
            .append(" importance=").append(snapshot.importance)
            .append(" pss=").append(pss)
            .append(" description=").append(desc)
        val trace = snapshot.traceExcerpt?.let { DiagLogSanitizer.sanitize(it) }
        if (!trace.isNullOrBlank()) {
            builder.append("\n  trace:\n").append(trace.trim().prependIndent("  "))
        }
        return builder.toString()
    }

    fun readBounded(stream: InputStream, maxChars: Int = MAX_TRACE_CHARS): String {
        val reader = InputStreamReader(stream, StandardCharsets.UTF_8)
        val buffer = CharArray(1024.coerceAtMost(maxChars.coerceAtLeast(1)))
        val out = StringBuilder(maxChars.coerceAtMost(1024))
        while (out.length < maxChars) {
            val remaining = maxChars - out.length
            val read = reader.read(buffer, 0, buffer.size.coerceAtMost(remaining))
            if (read <= 0) break
            out.append(buffer, 0, read)
        }
        if (out.length >= maxChars) out.append("\n<truncated>")
        return out.toString()
    }

    fun truncateTrace(raw: String?, maxChars: Int = MAX_TRACE_CHARS): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        if (trimmed.length <= maxChars) return trimmed
        return trimmed.take(maxChars) + "\n<truncated>"
    }
}
