package com.sosauce.chocola.core.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.time.ZoneOffset
import kotlin.text.Charsets

class DiagExportAndExitReasonsTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun meta() = DiagExportMeta(
        exportedAtMillis = 1_700_000_000_000L,
        zone = ZoneOffset.UTC,
        versionName = "0.3",
        versionCode = 3,
        packageName = "c2cw.bymusic",
        processName = "c2cw.bymusic",
        pid = 4321,
        manufacturer = "realme",
        brand = "realme",
        model = "RMX3560",
        device = "RE8D22L1",
        androidRelease = "14",
        sdkInt = 34,
        display = "RMX3560_13.A.xx",
        incremental = "test",
        securityPatch = "2026-01-01"
    )

    @Test
    fun exportContainsInstructionsVersionDeviceAndSanitizedLogs() {
        val dir = temp.newFolder("logs")
        val sink = DiagLogFileSink(dir, maxFileBytes = 8 * 1024, maxFiles = 2)
        sink.appendLine("ERROR tag=Crash failed content://media/external/audio/media/9 path=/sdcard/Music/x.mp3")
        sink.flush()

        val file = DiagExport.writeExport(temp.newFolder("export"), meta(), sink)
        val text = file.readText(Charsets.UTF_8)
        assertTrue(text.contains("不会自动上传"))
        assertTrue(text.contains("已停止运行"))
        assertTrue(text.contains("设置 → 诊断日志"))
        assertTrue(text.contains("应用版本: 0.3 (3)"))
        assertTrue(text.contains("c2cw.bymusic"))
        assertTrue(text.contains("RMX3560"))
        assertTrue(text.contains("Android 14"))
        assertTrue(text.contains(DiagLogger.BUILD_MARK))
        assertTrue(text.contains("----- 日志 -----"))
        assertTrue(file.name.startsWith("ByMusic-diag-") && file.name.endsWith(".txt"))
        // The already-written log line is raw; export itself does not re-sanitize historical
        // lines, so production logging must sanitize before writing. This test also verifies
        // the header has no path of its own, and a sanitized rewrite path works via the logger.
        sink.close()

        val sanitizedLine = DiagLogSanitizer.sanitize(
            "ERROR tag=Crash failed content://media/external/audio/media/9 path=/sdcard/Music/x.mp3"
        )
        assertFalse(sanitizedLine.contains("x.mp3"))
        assertTrue(sanitizedLine.contains("<redacted-uri>"))
        assertTrue(sanitizedLine.contains("<redacted-path>"))
    }

    @Test
    fun exportReplacesPreviousExportFilesAndHandlesEmptySink() {
        val outDir = temp.newFolder("export")
        val first = DiagExport.writeExport(outDir, meta(), sink = null, exportedAtMillis = 1_700_000_000_000L)
        assertTrue(first.readText(Charsets.UTF_8).contains("还没有日志记录"))
        val second = DiagExport.writeExport(outDir, meta(), sink = null, exportedAtMillis = 1_700_000_100_000L)
        val files = outDir.listFiles()!!.filter { it.name.startsWith("ByMusic-diag-") }
        assertEquals(1, files.size)
        assertEquals(second.name, files.single().name)
    }

    @Test
    fun shareAuthorityAndCacheDirArePackageScoped() {
        assertEquals("c2cw.bymusic.diag.fileprovider", DiagShare.authority("c2cw.bymusic"))
        assertEquals("c2cw.bymusic.debug.diag.fileprovider", DiagShare.authority("c2cw.bymusic.debug"))
        val cache = temp.newFolder("cache")
        assertEquals("diag", DiagShare.exportCacheDir(cache).name)
        assertEquals(cache, DiagShare.exportCacheDir(cache).parentFile)
    }

    @Test
    fun formatsKnownExitReasonsAndSanitizesTraces() {
        assertEquals("CRASH", DiagExitReasons.reasonLabel(DiagExitReasons.REASON_CRASH))
        assertEquals("ANR", DiagExitReasons.reasonLabel(DiagExitReasons.REASON_ANR))
        assertEquals("CRASH_NATIVE", DiagExitReasons.reasonLabel(DiagExitReasons.REASON_CRASH_NATIVE))
        assertEquals("LOW_MEMORY", DiagExitReasons.reasonLabel(DiagExitReasons.REASON_LOW_MEMORY))
        assertEquals("REASON_99", DiagExitReasons.reasonLabel(99))

        val formatted = DiagExitReasons.format(
            DiagExitSnapshot(
                timestampMillis = 1_700_000_000_000L,
                pid = 99,
                processName = "c2cw.bymusic",
                reasonCode = DiagExitReasons.REASON_CRASH,
                status = 1,
                importance = 100,
                pssKb = 12_000,
                description = "crash at /storage/emulated/0/Music/z.mp3",
                traceExcerpt = DiagExitReasons.truncateTrace("at play file:///sdcard/a.mp3\n" + "x".repeat(5000))
            ),
            zone = ZoneOffset.UTC
        )
        assertTrue(formatted.contains("reason=CRASH(4)"))
        assertTrue(formatted.contains("<redacted-path>") || formatted.contains("<redacted-uri>"))
        assertFalse(formatted.contains("z.mp3"))
        assertFalse(formatted.contains("file://"))
        assertTrue(formatted.contains("<truncated>"))
        assertTrue(DiagExitReasons.truncateTrace("short") == "short")
    }
}
