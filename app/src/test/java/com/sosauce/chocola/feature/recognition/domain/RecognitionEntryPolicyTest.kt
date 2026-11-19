package com.sosauce.chocola.feature.recognition.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RecognitionEntryPolicyTest {

    @Test
    fun entryHiddenWithoutPlugins() {
        assertFalse(RecognitionEntryPolicy.isEntryVisible(emptyList()))
    }

    @Test
    fun entryShownWhenAPluginDeclaresRecognition() {
        assertTrue(
            RecognitionEntryPolicy.isEntryVisible(listOf(RecognitionPluginInfo("p", "Sample")))
        )
    }

    @Test
    fun shippedRegistryIsEmptySoNoEntryIsVisible() {
        val plugins = EmptyRecognitionPluginRegistry.plugins.value
        assertTrue(plugins.isEmpty())
        assertFalse(RecognitionEntryPolicy.isEntryVisible(plugins.map { it.info }))
        assertTrue(EmptyRecognitionPluginRegistry.find("anything") == null)
    }

    @Test
    fun appWiresTheEmptyRegistry() {
        val module = locate("src/main/java/com/sosauce/chocola/app/di/AppModule.kt").readText()
        assertTrue(module.contains("single<RecognitionPluginRegistry> { EmptyRecognitionPluginRegistry }"))
    }

    @Test
    fun entryComposablesAreGatedByThePolicy() {
        val entry = locate(
            "src/main/java/com/sosauce/chocola/feature/recognition/presentation/RecognitionEntry.kt"
        ).readText()
        // Both the mic button and the section return early when no plugin declares recognition.
        assertTrue(
            Regex("""if \(!RecognitionEntryPolicy\.isEntryVisible\(plugins\)\) return""")
                .findAll(entry).count() == 2
        )
    }

    @Test
    fun manifestDeclaresMicrophoneButNoBackgroundCapture() {
        val manifest = locate("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android.permission.RECORD_AUDIO"))
        assertFalse(manifest.contains("FOREGROUND_SERVICE_MICROPHONE"))
        assertFalse(manifest.contains("foregroundServiceType=\"microphone"))
    }

    private fun locate(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"), File("../$relative"))
        return candidates.firstOrNull { it.isFile }
            ?: throw AssertionError("missing $relative, cwd=${File(".").canonicalPath}")
    }
}
