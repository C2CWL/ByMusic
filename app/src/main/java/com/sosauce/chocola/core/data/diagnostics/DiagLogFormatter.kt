package com.sosauce.chocola.core.data.diagnostics

import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class DiagLogEvent(
    val timestampMillis: Long,
    val zone: ZoneId,
    val level: String,
    val tag: String,
    val pid: Int,
    val tid: Int,
    val threadName: String,
    val message: String,
    val throwable: Throwable? = null
)

object DiagLogFormatter {
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")

    fun format(event: DiagLogEvent): String {
        val time = TIME.format(Instant.ofEpochMilli(event.timestampMillis).atZone(event.zone))
        val message = DiagLogSanitizer.sanitize(event.message)
        val header =
            "$time ${event.level} pid=${event.pid} tid=${event.tid} thread=${event.threadName} tag=${event.tag} $message"
        val throwable = event.throwable ?: return header
        return header + "\n" + DiagLogSanitizer.sanitize(stackTraceOf(throwable))
    }

    fun stackTraceOf(throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return writer.toString().trimEnd()
    }
}
