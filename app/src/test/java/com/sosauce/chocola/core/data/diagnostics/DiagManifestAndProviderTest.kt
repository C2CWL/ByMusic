package com.sosauce.chocola.core.data.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DiagManifestAndProviderTest {

    @Test
    fun fileProviderIsPrivateAndOnlySharesCacheDiag() {
        val manifest = locate("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("androidx.core.content.FileProvider"))
        assertTrue(manifest.contains("\${applicationId}.diag.fileprovider"))
        val providerBlock = manifest.substring(
            manifest.indexOf("androidx.core.content.FileProvider")
        ).substringBefore("</provider>")
        assertTrue(providerBlock.contains("android:exported=\"false\""))
        assertTrue(providerBlock.contains("android:grantUriPermissions=\"true\""))
        assertTrue(providerBlock.contains("@xml/diag_logs_paths"))
        assertFalse(
            "log files directory must not be published by the provider",
            providerBlock.contains("files-path")
        )

        val paths = locate("src/main/res/xml/diag_logs_paths.xml").readText()
        assertTrue(paths.contains("<cache-path"))
        assertTrue(paths.contains("path=\"diag/\""))
        assertFalse(paths.contains("files-path"))
        assertFalse(paths.contains("external-path"))
        assertFalse(paths.contains("root-path"))
    }

    @Test
    fun shareIntentUsesReadUriPermissionFlag() {
        // Intent.FLAG_GRANT_READ_URI_PERMISSION is 0x1; we only expose the cache file via FileProvider.
        val shareSource = locate(
            "src/main/java/com/sosauce/chocola/core/data/diagnostics/DiagShare.kt"
        ).readText()
        assertTrue(shareSource.contains("Intent.FLAG_GRANT_READ_URI_PERMISSION"))
        assertTrue(shareSource.contains("FileProvider.getUriForFile"))
        assertTrue(shareSource.contains("Intent.ACTION_SEND"))
        assertFalse(shareSource.contains("FLAG_GRANT_WRITE_URI_PERMISSION"))
    }

    private fun locate(relative: String): File {
        val candidates = listOf(
            File(relative),
            File("app/$relative"),
            File("../$relative")
        )
        return candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("missing $relative, cwd=${File(".").canonicalPath}")
    }
}
