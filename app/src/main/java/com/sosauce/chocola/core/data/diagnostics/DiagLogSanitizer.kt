package com.sosauce.chocola.core.data.diagnostics

/**
 * Redacts file paths, content/file URIs and obvious secrets from diagnostic text.
 * Song titles cannot be reliably detected in arbitrary exception strings; we never
 * log media metadata ourselves, and paths/URIs are stripped here as a backstop.
 */
object DiagLogSanitizer {

    private val SCHEME_URI = Regex(
        """(?i)\b(?:content|file|http|https|rtsp|rtmp)://[^\s\]\)'<>\";,]+"""
    )
    private val ABS_UNIX_PATH = Regex(
        """(?<![\w.])(/(?:storage|sdcard|data|mnt|system|vendor|metadata|product|obb|apex)[^\s\]\)'<>\";,]*)"""
    )
    private val WINDOWS_PATH = Regex(
        """(?i)\b[A-Z]:\\[^\s\]\)'<>\";,]+"""
    )
    private val SECRET_ASSIGNMENT = Regex(
        """(?i)\b(password|passwd|pwd|secret|token|api[_-]?key|keystore|key[_-]?alias|authorization|bearer)\s*[=:]\s*\S+"""
    )

    fun sanitize(input: String): String {
        var text = input
        text = SCHEME_URI.replace(text, "<redacted-uri>")
        text = ABS_UNIX_PATH.replace(text, "<redacted-path>")
        text = WINDOWS_PATH.replace(text, "<redacted-path>")
        text = SECRET_ASSIGNMENT.replace(text) { match ->
            val key = match.groupValues[1]
            "$key=<redacted>"
        }
        return text
    }
}
