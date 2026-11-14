package com.sosauce.chocola.core.data.diagnostics

/**
 * When recording is enabled, logs the uncaught exception and flushes the sink.
 * Always delegates to the previous handler so the system crash dialog still happens.
 */
class DiagExceptionHandler(
    private val previous: Thread.UncaughtExceptionHandler?,
    private val log: (String, String, Throwable) -> Unit = DiagLogger::e,
    private val flush: () -> Unit = { DiagLogger.flush() },
    private val isRecordingEnabled: () -> Boolean = { DiagLogger.isRecordingEnabled() }
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            if (isRecordingEnabled()) {
                log(
                    "Crash",
                    "uncaught exception on thread=${thread.name} id=${thread.id}",
                    throwable
                )
                flush()
            }
        } catch (_: Throwable) {
            // Never interfere with the original handler.
        } finally {
            previous?.uncaughtException(thread, throwable)
        }
    }
}
