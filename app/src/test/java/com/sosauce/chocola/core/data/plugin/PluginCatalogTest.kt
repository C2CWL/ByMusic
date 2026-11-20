package com.sosauce.chocola.core.data.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PluginCatalogTest {
    @Test
    fun keepsUniqueHttpsEntriesInOrderAndSkipsInvalidEntries() {
        val source = """
            {"desc":"MusicFree","plugins":[
                {"name":" 第一项 ","url":" https://example.org/first.js ","version":"1.2"},
                {"name":"重复","url":"https://example.org/first.js"},
                {"url":"http://example.org/insecure.js"},
                {"url":"https://127.0.0.1/private.js"},
                {"url":"https://user@example.org/with-user-info.js"},
                {"url":"https:///no-host.js"},
                {"url":123},
                null,
                {"name":false,"url":"https://example.org/second?raw=1"}
            ]}
        """.trimIndent()
        val catalog = requireNotNull(PluginCatalog.readOrNull(source, expectJson = false))
        assertEquals(
            listOf(
                PluginCatalogEntry("https://example.org/first.js", "第一项", "1.2"),
                PluginCatalogEntry("https://example.org/second?raw=1", null, null)
            ),
            catalog.entries
        )
        assertEquals(7, catalog.skippedCount)
    }

    @Test
    fun rejectsInvalidCatalogsWithoutTreatingThemAsScripts() {
        for (source in listOf("", "{", "{}", "[]", """{"plugins":{}}""", """{"plugins":[]}""")) {
            assertThrows(PluginCatalogException::class.java) { PluginCatalog.readOrNull(source, expectJson = true) }
        }
        assertThrows(PluginCatalogException::class.java) {
            PluginCatalog.readOrNull("""{"plugins":[{"url":"./plugin.js"}]}""", expectJson = false)
        }
        assertNull(PluginCatalog.readOrNull("{ module.exports = plugin; }", expectJson = false))
    }
}
