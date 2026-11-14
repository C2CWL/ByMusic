package com.sosauce.chocola.core.data.diagnostics

import java.util.concurrent.atomic.AtomicBoolean

/**
 * When recording is enabled, best-effort logs the uncaught exception without blocking forever.
 * Always delegates to the previous handler so the system crash dialog still happens.
 * Re-entrancy is ignored so logging cannot recurse into itself.
 */
class DiagExceptionHandler(
    private val previous: Thread.UncaughtExceptionHandler?,
    private val log: (String, String, Throwable) -> Unit = DiagLogger::e,
    private val flushBestEffort: () -> Unit = { DiagLogger.flushBestEffort(250) },
    private val isRecordingEnabled: () -> Boolean = { DiagLogger.isRecordingEnabled() }
) : Thread.UncaughtExceptionHandler {

    private val handling = AtomicBoolean(false)

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        if (!handling.compareAndSet(false, true)) {
            return
        }
        try {
            if (isRecordingEnabled()) {
                log(
                    "Crash",
                    "uncaught exception on thread=${thread.name} id=${thread.id}",
                    throwable
                )
                flushBestEffort()
            }
        } catch (_: Throwable) {
            // Never interfere with the original handler.
        } finally {
            previous?.uncaughtException(thread, throwable)
        }
    }
}
