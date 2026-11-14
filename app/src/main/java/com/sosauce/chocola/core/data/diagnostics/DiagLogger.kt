package com.sosauce.chocola.core.data.diagnostics

import java.io.File
import java.io.OutputStream
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide diagnostic logger. Safe to call before [install]; messages are then dropped.
 */
object DiagLogger {
    const val BUILD_MARK = "local-diagnostic-logs"

    private val writerRef = AtomicReference<DiagLogWriter?>()
    private val sinkRef = AtomicReference<DiagLogFileSink?>()
    private val processInfoRef = AtomicReference<ProcessInfo>(ProcessInfo.UNKNOWN)
    private val zoneRef = AtomicReference(ZoneId.systemDefault())

    data class ProcessInfo(val pid: Int, val processName: String) {
        companion object {
            val UNKNOWN = ProcessInfo(pid = -1, processName = "unknown")
        }
    }

    @Synchronized
    fun install(
        sink: DiagLogFileSink,
        writer: DiagLogWriter,
        processInfo: ProcessInfo,
        zone: ZoneId = ZoneId.systemDefault()
    ) {
        writerRef.get()?.close()
        sinkRef.set(sink)
        writerRef.set(writer)
        processInfoRef.set(processInfo)
        zoneRef.set(zone)
    }

    fun isInstalled(): Boolean = writerRef.get() != null

    fun i(tag: String, message: String) = log("INFO", tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable? = null) =
        log("WARN", tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) =
        log("ERROR", tag, message, throwable)

    fun flush() {
        writerRef.get()?.flushSync()
    }

    fun export(out: OutputStream) {
        flush()
        sinkRef.get()?.export(out)
    }

    fun sinkOrNull(): DiagLogFileSink? = sinkRef.get()

    fun currentProcessInfo(): ProcessInfo = processInfoRef.get()

    internal fun resetForTests() {
        writerRef.getAndSet(null)?.close()
        sinkRef.set(null)
        processInfoRef.set(ProcessInfo.UNKNOWN)
    }

    private fun log(level: String, tag: String, message: String, throwable: Throwable?) {
        val writer = writerRef.get() ?: return
        val process = processInfoRef.get()
        val thread = Thread.currentThread()
        val line = DiagLogFormatter.format(
            DiagLogEvent(
                timestampMillis = System.currentTimeMillis(),
                zone = zoneRef.get(),
                level = level,
                tag = tag,
                pid = process.pid,
                tid = thread.id.toInt(),
                threadName = thread.name,
                message = message,
                throwable = throwable
            )
        )
        writer.enqueue(line)
    }

    fun defaultDir(filesDir: File): File = File(filesDir, DIR_NAME)

    const val DIR_NAME = "diag"
}
