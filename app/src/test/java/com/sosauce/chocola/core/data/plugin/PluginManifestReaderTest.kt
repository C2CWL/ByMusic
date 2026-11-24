package com.sosauce.chocola.core.data.plugin

import com.sosauce.chocola.core.data.plugin.PluginTestSupport.fictionalPlugin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginManifestReaderTest {

    @Test
    fun readsFictionalPluginWithoutEvaluating() {
        val m = PluginManifestReader.read(fictionalPlugin)
        assertEquals("虚构音源", m.platform)
        assertEquals("1.2.0", m.version)
        assertEquals("ByMusic tests", m.author)
        assertEquals("https://plugins.example.com/fictional.js", m.srcUrl)
        assertEquals(listOf("id"), m.primaryKey)
        assertEquals(listOf("music", "album"), m.supportedSearchType)
        assertEquals(1, m.userVariables.size)
        assertEquals("token", m.userVariables.single().key)
        assertTrue(m.has("search"))
        assertTrue(m.has("getMediaSource"))
        assertTrue(m.has("getLyric"))
        assertTrue("webdav" in m.missingModules)
        assertTrue("axios" in m.requiredModules)
        // Must not need a runtime: no network, no QuickJS.
        assertFalse(m.has("recognizeSong"))
    }

    @Test
    fun objectAssignExportIsAccepted() {
        val code = """
            'use strict';
            Object.assign(module.exports, {
              platform: "指派",
              version: "0.1",
              async search() {}
            });
        """.trimIndent()
        val m = PluginManifestReader.read(code)
        assertEquals("指派", m.platform)
        assertEquals("0.1", m.version)
        assertTrue(m.has("search"))
    }

    @Test
    fun rejectsMissingPlatformAndNonObjectExport() {
        for (code in listOf(
            "module.exports = 42;",
            "module.exports = { version: '1' };",
            "module.exports = { platform: '本地' };",
            ""
        )) {
            try {
                PluginManifestReader.read(code)
                throw AssertionError("should reject: $code")
            } catch (e: PluginException) {
                assertEquals(PluginErrorType.LOAD, e.type)
            }
        }
    }
}
