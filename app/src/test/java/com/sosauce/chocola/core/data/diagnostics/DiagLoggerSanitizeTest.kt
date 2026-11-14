package com.sosauce.chocola.core.data.diagnostics

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.time.ZoneOffset
import kotlin.text.Charsets

class DiagLoggerSanitizeTest {

    @get:Rule
    val temp = TemporaryFolder()

    @After
    fun tearDown() {
        DiagLogger.resetForTests()
    }

    @Test
    fun loggerPersistsSanitizedMessagesAndStacks() {
        val dir = temp.newFolder("logs")
        val sink = DiagLogFileSink(dir, maxFileBytes = 16 * 1024, maxFiles = 2)
        val writer = DiagLogWriter(sink)
        DiagLogger.install(
            sink = sink,
            writer = writer,
            processInfo = DiagLogger.ProcessInfo(pid = 7, processName = "c2cw.bymusic"),
            zone = ZoneOffset.UTC,
            recordingEnabled = true
        )
        DiagLogger.e(
            "Crash",
            "uncaught while playing content://media/external/audio/media/44",
            IllegalStateException("path=/storage/emulated/0/Music/secret-title.mp3")
        )
        DiagLogger.flush()
        val text = ByteArrayOutputStream().also { DiagLogger.export(it) }.toString(Charsets.UTF_8.name())
        assertTrue(text.contains("tag=Crash"))
        assertTrue(text.contains("<redacted-uri>") || text.contains("<redacted-path>"))
        assertFalse(text.contains("secret-title.mp3"))
        assertFalse(text.contains("content://"))
        assertFalse(text.contains("/storage/emulated"))
    }
}
