package com.sosauce.chocola.core.data.diagnostics

import java.io.File
import java.io.OutputStream
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide diagnostic logger. Recording is off until [setRecordingEnabled] is true.
 * Safe to call before [install]; messages are then dropped.
 */
object DiagLogger {
    const val BUILD_MARK = "local-diagnostic-logs"

    private val writerRef = AtomicReference<DiagLogWriter?>()
    private val sinkRef = AtomicReference<DiagLogFileSink?>()
    private val processInfoRef = AtomicReference<ProcessInfo>(ProcessInfo.UNKNOWN)
    private val zoneRef = AtomicReference(ZoneId.systemDefault())
    private val recordingEnabled = AtomicBoolean(false)

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
        zone: ZoneId = ZoneId.systemDefault(),
        recordingEnabled: Boolean = false
    ) {
        writerRef.get()?.close()
        sinkRef.set(sink)
        writerRef.set(writer)
        processInfoRef.set(processInfo)
        zoneRef.set(zone)
        this.recordingEnabled.set(recordingEnabled)
    }

    fun isInstalled(): Boolean = writerRef.get() != null

    fun isRecordingEnabled(): Boolean = recordingEnabled.get()

    /**
     * Turns recording on or off immediately. Persistence is handled by the caller.
     * Disabling stops new records but leaves existing files in place.
     */
    fun setRecordingEnabled(enabled: Boolean) {
        if (enabled) {
            if (recordingEnabled.compareAndSet(false, true)) {
                log("INFO", "DiagLog", "logging enabled", null, force = true)
            }
        } else if (recordingEnabled.get()) {
            log("INFO", "DiagLog", "logging disabled; existing logs kept until export or clear", null, force = true)
            flush()
            recordingEnabled.set(false)
        }
    }

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

    fun clearLogs() {
        writerRef.get()?.discardPendingAndClear(sinkRef.get())
            ?: sinkRef.get()?.clear()
    }

    fun sinkOrNull(): DiagLogFileSink? = sinkRef.get()

    fun currentProcessInfo(): ProcessInfo = processInfoRef.get()

    internal fun resetForTests() {
        writerRef.getAndSet(null)?.close()
        sinkRef.set(null)
        processInfoRef.set(ProcessInfo.UNKNOWN)
        recordingEnabled.set(false)
    }

    private fun log(
        level: String,
        tag: String,
        message: String,
        throwable: Throwable?,
        force: Boolean = false
    ) {
        if (!force && !recordingEnabled.get()) return
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
