package com.sosauce.chocola.core.data.diagnostics

import java.io.File
import java.io.OutputStream
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide diagnostic logger. Recording defaults to off. Safe to call before storage is
 * attached; messages are then dropped. [flushBestEffort] must not block a crash handler forever.
 */
object DiagLogger {
    const val BUILD_MARK = "local-diagnostic-logs"
    const val DIR_NAME = "diag"

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

    fun setProcessInfo(processInfo: ProcessInfo, zone: ZoneId = ZoneId.systemDefault()) {
        processInfoRef.set(processInfo)
        zoneRef.set(zone)
    }

    fun setRecordingEnabledFlag(enabled: Boolean) {
        recordingEnabled.set(enabled)
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
        attachStorage(sink, writer)
        setProcessInfo(processInfo, zone)
        this.recordingEnabled.set(recordingEnabled)
    }

    @Synchronized
    fun attachStorage(sink: DiagLogFileSink, writer: DiagLogWriter) {
        if (writerRef.get() != null) return
        sinkRef.set(sink)
        writerRef.set(writer)
    }

    fun isInstalled(): Boolean = writerRef.get() != null

    fun isRecordingEnabled(): Boolean = recordingEnabled.get()

    /**
     * Turns recording on or off immediately. Persistence and storage setup are handled by the caller.
     * Disabling stops new records but leaves existing files in place.
     */
    fun setRecordingEnabled(enabled: Boolean) {
        if (enabled) {
            if (recordingEnabled.compareAndSet(false, true)) {
                log("INFO", "DiagLog", "logging enabled", null, force = true)
            }
        } else if (recordingEnabled.get()) {
            log("INFO", "DiagLog", "logging disabled; existing logs kept until export or clear", null, force = true)
            flushBestEffort(500)
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

    fun flushBestEffort(timeoutMs: Long) {
        writerRef.get()?.flushBestEffort(timeoutMs)
    }

    fun export(out: OutputStream) {
        flushBestEffort(1_000)
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
}
