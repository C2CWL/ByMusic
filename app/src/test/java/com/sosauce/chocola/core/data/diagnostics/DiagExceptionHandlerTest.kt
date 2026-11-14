package com.sosauce.chocola.core.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.text.Charsets

class DiagExceptionHandlerTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun persistsSanitizedExceptionAndChainsPreviousHandler() {
        val dir = temp.newFolder("logs")
        val sink = DiagLogFileSink(dir, maxFileBytes = 16 * 1024, maxFiles = 3)
        val writer = DiagLogWriter(sink)
        val previousCalls = AtomicInteger(0)
        var previousThrowable: Throwable? = null
        val previous = Thread.UncaughtExceptionHandler { _, error ->
            previousCalls.incrementAndGet()
            previousThrowable = error
        }
        val handler = DiagExceptionHandler(
            previous = previous,
            isRecordingEnabled = { true },
            log = { tag, message, throwable ->
                val line = DiagLogFormatter.format(
                    DiagLogEvent(
                        timestampMillis = 0L,
                        zone = ZoneOffset.UTC,
                        level = "ERROR",
                        tag = tag,
                        pid = 11,
                        tid = 22,
                        threadName = "crash-thread",
                        message = message,
                        throwable = throwable
                    )
                )
                writer.enqueue(line)
            },
            flush = { writer.flushSync() }
        )

        val boom = IllegalStateException("failed to open /storage/emulated/0/Music/hidden.mp3")
        val thread = Thread("crash-thread")
        handler.uncaughtException(thread, boom)

        assertEquals(1, previousCalls.get())
        assertEquals(boom, previousThrowable)

        val exported = ByteArrayOutputStream().also { sink.export(it) }.toString(Charsets.UTF_8.name())
        assertTrue(exported.contains("uncaught exception"))
        assertTrue(exported.contains("IllegalStateException"))
        assertTrue(exported.contains("<redacted-path>"))
        assertFalse(exported.contains("hidden.mp3"))
        assertFalse(exported.contains("/storage/emulated"))
        writer.close()
    }

    @Test
    fun stillChainsWhenLoggingFails() {
        val previousCalls = AtomicInteger(0)
        val previous = Thread.UncaughtExceptionHandler { _, _ -> previousCalls.incrementAndGet() }
        val handler = DiagExceptionHandler(
            previous = previous,
            isRecordingEnabled = { true },
            log = { _, _, _ -> throw RuntimeException("logger down") },
            flush = { throw IllegalStateException("flush down") }
        )
        handler.uncaughtException(Thread.currentThread(), RuntimeException("boom"))
        assertEquals(1, previousCalls.get())
    }
}
