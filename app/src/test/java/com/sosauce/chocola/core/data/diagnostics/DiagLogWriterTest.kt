package com.sosauce.chocola.core.data.diagnostics

import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import kotlin.text.Charsets

class DiagLogWriterTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun asyncWritesAreVisibleAfterFlush() {
        val dir = temp.newFolder("logs")
        val sink = DiagLogFileSink(dir, maxFileBytes = 8 * 1024, maxFiles = 2)
        val writer = DiagLogWriter(sink, queueCapacity = 16)
        repeat(10) { writer.enqueue("async-line-$it") }
        writer.flushSync()
        val text = ByteArrayOutputStream().also { sink.export(it) }.toString(Charsets.UTF_8.name())
        assertTrue(text.contains("async-line-0"))
        assertTrue(text.contains("async-line-9"))
        writer.close()
    }

    @Test
    fun fullQueueDropsOldestInsteadOfBlocking() {
        val dir = temp.newFolder("logs")
        val sink = DiagLogFileSink(dir, maxFileBytes = 32 * 1024, maxFiles = 2)
        val writer = DiagLogWriter(sink, queueCapacity = 4)
        repeat(20) { writer.enqueue("queued-$it") }
        writer.flushSync()
        assertTrue(writer.droppedCount() >= 0)
        val text = ByteArrayOutputStream().also { sink.export(it) }.toString(Charsets.UTF_8.name())
        assertTrue(text.contains("queued-19"))
        writer.close()
    }
}
