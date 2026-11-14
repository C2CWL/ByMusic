package com.sosauce.chocola.core.data.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagLogSanitizerTest {

    @Test
    fun redactsContentAndFileUris() {
        val input = "open content://media/external/audio/media/42 then file:///storage/emulated/0/Music/secret.mp3"
        val out = DiagLogSanitizer.sanitize(input)
        assertFalse(out.contains("secret.mp3"))
        assertFalse(out.contains("content://"))
        assertFalse(out.contains("file://"))
        assertEquals(2, "<redacted-uri>".toRegex().findAll(out).count())
    }

    @Test
    fun redactsHttpUris() {
        val out = DiagLogSanitizer.sanitize("cover https://cdn.example/album/art.jpg?token=abc")
        assertFalse(out.contains("token=abc"))
        assertFalse(out.contains("https://"))
        assertTrue(out.contains("<redacted-uri>"))
    }

    @Test
    fun redactsAbsoluteUnixPaths() {
        val input = listOf(
            "failed /storage/emulated/0/Music/foo.mp3",
            "read /sdcard/Download/bar.flac",
            "app /data/user/0/c2cw.bymusic/files/cache/x",
            "from /mnt/media_rw/XXXX/Songs/a.wav"
        )
        for (line in input) {
            val out = DiagLogSanitizer.sanitize(line)
            assertFalse("still contained a path: $out from $line", out.contains("/storage") || out.contains("/sdcard") || out.contains("/data/user") || out.contains("/mnt/"))
            assertTrue(out.contains("<redacted-path>"))
        }
    }

    @Test
    fun redactsWindowsPathsAndSecrets() {
        val out = DiagLogSanitizer.sanitize("""C:\Music\demo.wav password=hunter2 api_key=abcd""")
        assertFalse(out.contains("demo.wav"))
        assertFalse(out.contains("hunter2"))
        assertFalse(out.contains("abcd"))
        assertTrue(out.contains("<redacted-path>"))
        assertTrue(out.contains("password=<redacted>"))
        assertTrue(out.contains("api_key=<redacted>"))
    }

    @Test
    fun keepsPackageAndClassNames() {
        val input = "com.sosauce.chocola.app.PlaybackService.onCreate (PlaybackService.kt:120)"
        assertEquals(input, DiagLogSanitizer.sanitize(input))
    }

    @Test
    fun redactsPathsInsideExceptionTextButKeepsMessageShell() {
        val out = DiagLogSanitizer.sanitize(
            "java.io.FileNotFoundException: /storage/emulated/0/Music/private.mp3: open failed"
        )
        assertFalse(out.contains("private.mp3"))
        assertTrue(out.contains("FileNotFoundException"))
        assertTrue(out.contains("open failed"))
    }
}
