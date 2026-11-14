package com.sosauce.chocola.core.data.diagnostics

/**
 * Logs the uncaught exception, flushes the diagnostic sink, then always delegates to
 * the previous handler so the system crash dialog and process death still happen.
 */
class DiagExceptionHandler(
    private val previous: Thread.UncaughtExceptionHandler?,
    private val log: (String, String, Throwable) -> Unit = DiagLogger::e,
    private val flush: () -> Unit = { DiagLogger.flush() }
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            log(
                "Crash",
                "uncaught exception on thread=${thread.name} id=${thread.id}",
                throwable
            )
            flush()
        } catch (_: Throwable) {
            // Never interfere with the original handler.
        } finally {
            previous?.uncaughtException(thread, throwable)
        }
    }
}
