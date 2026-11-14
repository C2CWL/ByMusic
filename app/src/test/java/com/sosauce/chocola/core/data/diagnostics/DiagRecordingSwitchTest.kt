package com.sosauce.chocola.core.data.diagnostics

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.text.Charsets

class DiagRecordingSwitchTest {

    @get:Rule
    val temp = TemporaryFolder()

    @After
    fun tearDown() {
        DiagLogger.resetForTests()
    }

    private fun install(enabled: Boolean = false): DiagLogFileSink {
        val dir = temp.newFolder("logs-${System.nanoTime()}")
        val sink = DiagLogFileSink(dir, maxFileBytes = 16 * 1024, maxFiles = 3)
        val writer = DiagLogWriter(sink)
        DiagLogger.install(
            sink = sink,
            writer = writer,
            processInfo = DiagLogger.ProcessInfo(pid = 9, processName = "c2cw.bymusic"),
            zone = ZoneOffset.UTC,
            recordingEnabled = enabled
        )
        return sink
    }

    private fun exported(): String {
        DiagLogger.flush()
        return ByteArrayOutputStream().also { DiagLogger.export(it) }.toString(Charsets.UTF_8.name())
    }

    @Test
    fun defaultAndExplicitOffDoNotRecord() {
        val sink = install(enabled = false)
        assertFalse(DiagLogger.isRecordingEnabled())
        assertFalse(DiagLogPreferences.DEFAULT_ENABLED)
        DiagLogger.i("Process", "start should not be recorded")
        DiagLogger.e("Crash", "uncaught should not be recorded", RuntimeException("boom"))
        DiagLogger.flush()
        assertEquals(0, sink.totalBytes())
        assertFalse(exported().contains("should not be recorded"))
    }

    @Test
    fun enablingStartsRecordingAndDisablingStopsWhileKeepingOldLogs() {
        install(enabled = false)
        DiagLogger.i("Process", "before-enable hidden")
        DiagLogger.setRecordingEnabled(true)
        assertTrue(DiagLogger.isRecordingEnabled())
        DiagLogger.i("Process", "after-enable visible")
        DiagLogger.i("ExitReasons", "historical exit CRASH")
        DiagLogger.flush()
        val enabledText = exported()
        assertTrue(enabledText.contains("logging enabled"))
        assertTrue(enabledText.contains("after-enable visible"))
        assertTrue(enabledText.contains("historical exit CRASH"))
        assertFalse(enabledText.contains("before-enable hidden"))

        DiagLogger.setRecordingEnabled(false)
        assertFalse(DiagLogger.isRecordingEnabled())
        DiagLogger.i("Process", "after-disable hidden")
        DiagLogger.e("Crash", "after-disable crash hidden", RuntimeException("nope"))
        val disabledText = exported()
        assertTrue(disabledText.contains("after-enable visible"))
        assertTrue(disabledText.contains("logging disabled"))
        assertFalse(disabledText.contains("after-disable hidden"))
        assertFalse(disabledText.contains("after-disable crash hidden"))
    }

    @Test
    fun clearRemovesFilesButKeepsSwitchState() {
        install(enabled = true)
        DiagLogger.i("Process", "to-be-cleared")
        DiagLogger.flush()
        assertTrue(exported().contains("to-be-cleared"))
        DiagLogger.clearLogs()
        assertTrue(DiagLogger.isRecordingEnabled())
        val cleared = exported()
        assertFalse(cleared.contains("to-be-cleared"))
        DiagLogger.i("Process", "after-clear")
        assertTrue(exported().contains("after-clear"))
    }

    @Test
    fun exceptionHandlerSkipsLoggingWhenDisabledButStillChains() {
        install(enabled = false)
        val previousCalls = AtomicInteger(0)
        val logged = AtomicBoolean(false)
        val handler = DiagExceptionHandler(
            previous = { _, _ -> previousCalls.incrementAndGet() },
            isRecordingEnabled = { DiagLogger.isRecordingEnabled() },
            log = { _, _, _ -> logged.set(true) },
            flushBestEffort = { }
        )
        handler.uncaughtException(Thread.currentThread(), RuntimeException("off"))
        assertEquals(1, previousCalls.get())
        assertFalse(logged.get())
        assertFalse(exported().contains("uncaught exception"))
    }

    @Test
    fun exceptionHandlerRecordsWhenEnabled() {
        install(enabled = true)
        val previousCalls = AtomicInteger(0)
        val handler = DiagExceptionHandler(
            previous = { _, _ -> previousCalls.incrementAndGet() },
            isRecordingEnabled = { DiagLogger.isRecordingEnabled() }
        )
        handler.uncaughtException(Thread("t"), IllegalStateException("on"))
        assertEquals(1, previousCalls.get())
        val text = exported()
        assertTrue(text.contains("uncaught exception"))
        assertTrue(text.contains("IllegalStateException"))
    }
}
