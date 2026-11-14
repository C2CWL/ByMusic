package com.sosauce.chocola.core.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.text.Charsets

class DiagLogFileSinkTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun rotatesWhenFileExceedsMaxSizeAndKeepsBound() {
        val dir = temp.newFolder("logs")
        val sink = DiagLogFileSink(dir, fileName = "diag.log", maxFileBytes = 80, maxFiles = 3)
        repeat(40) { index ->
            sink.appendLine("line-$index-xxxxxxxxxxxxxxxxxxxx")
        }
        sink.flush()
        val managed = dir.listFiles()!!.filter { it.name.startsWith("diag.log") }
        assertTrue("expected rotation, files=${managed.map { it.name }}", managed.size in 2..3)
        assertTrue(managed.size <= 3)
        assertTrue("total ${sink.totalBytes()} should stay near 3*80", sink.totalBytes() <= 80 * 3 + 80)
        val export = ByteArrayOutputStream()
        sink.export(export)
        val text = export.toString(Charsets.UTF_8.name())
        assertTrue(text.contains("line-39"))
        // Oldest lines may have been dropped by rotation past maxFiles.
        assertFalse("should have dropped the earliest lines after several rotations", text.contains("line-0-"))
        sink.close()
    }

    @Test
    fun cleanupDeletesExtraRotatedFiles() {
        val dir = temp.newFolder("logs")
        File(dir, "diag.log.9").writeText("stale-9\n")
        File(dir, "diag.log.8").writeText("stale-8\n")
        File(dir, "diag.log").writeText("current\n")
        val sink = DiagLogFileSink(dir, fileName = "diag.log", maxFileBytes = 200, maxFiles = 3)
        sink.appendLine("fresh")
        sink.flush()
        val names = dir.listFiles()!!.map { it.name }.toSet()
        assertFalse(names.contains("diag.log.9"))
        assertFalse(names.contains("diag.log.8"))
        assertTrue(names.contains("diag.log"))
        assertTrue(sink.managedFileCount() <= 3)
        sink.close()
    }

    @Test
    fun exportWritesFilesInChronologicalOrder() {
        val dir = temp.newFolder("logs")
        val sink = DiagLogFileSink(dir, fileName = "diag.log", maxFileBytes = 24, maxFiles = 3)
        sink.appendLine("aaaaaaaaaaaaaaaa") // force fill
        sink.appendLine("bbbbbbbbbbbbbbbb")
        sink.appendLine("cccccccccccccccc")
        sink.flush()
        val exported = ByteArrayOutputStream().also { sink.export(it) }.toString(Charsets.UTF_8.name())
        val first = exported.indexOf("aaaa")
        val last = exported.lastIndexOf("cccc")
        if (first >= 0 && last >= 0) {
            assertTrue("older content should appear before newer", first < last)
        } else {
            assertTrue("export should contain the newest chunk", exported.contains("cccc"))
        }
        sink.close()
    }
}
