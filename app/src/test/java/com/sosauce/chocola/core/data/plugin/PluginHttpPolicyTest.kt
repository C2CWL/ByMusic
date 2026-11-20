package com.sosauce.chocola.core.data.plugin

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PluginHttpPolicyTest {

    private fun check(url: String, method: String = "GET") =
        PluginHttpPolicy.check(PluginHttpRequest(method = method, url = url))

    @Test
    fun publicHttpsIsAllowed() {
        assertNull(check("https://api.example.com/search?q=1"))
        assertNull(check("https://example.org/first.js"))
        assertNull(check("https://93.184.216.34/"))
    }

    @Test
    fun localAndPrivateAddressesAreBlocked() {
        listOf(
            "http://localhost/",
            "https://foo.localhost/",
            "https://printer.local/",
            "https://local/",
            "https://127.0.0.1/",
            "https://10.1.2.3/",
            "https://172.16.0.1/",
            "https://192.168.1.1/",
            "https://169.254.169.254/latest/meta-data",
            "https://100.64.0.1/",
            "https://0.0.0.0/",
            "https://[::1]/",
            "https://[fd00::1]/",
            "https://[fe80::1]/",
            "https://[::ffff:192.168.0.1]/"
        ).forEach { assertNotNull(it, check(it)) }
    }

    @Test
    fun otherSchemesAndMethodsAreBlocked() {
        assertNotNull(check("file:///data/data/com.sosauce.chocola/files/x"))
        assertNotNull(check("content://media/external"))
        assertNotNull(check("ftp://example.com/"))
        assertNotNull(check("https://example.com/", method = "CONNECT"))
    }

    @Test
    fun timeoutsAreCapped() {
        assertEquals(PluginHttpPolicy.DEFAULT_TIMEOUT_MS, PluginHttpPolicy.timeoutOf(PluginHttpRequest(url = "https://a.b/")))
        assertEquals(
            PluginHttpPolicy.MAX_TIMEOUT_MS,
            PluginHttpPolicy.timeoutOf(PluginHttpRequest(url = "https://a.b/", timeoutMs = 600_000))
        )
    }

    @Test
    fun trackIdUsesPrimaryKeys() {
        val item = buildJsonObject {
            put("id", 7)
            put("mid", "m")
        }
        assertEquals("7", MusicFreeItems.trackIdOf(item, listOf("id")))
        assertEquals("7", MusicFreeItems.trackIdOf(item, emptyList()))
        assertEquals("""[7,"m"]""", MusicFreeItems.trackIdOf(item, listOf("id", "mid")))
        assertNull(MusicFreeItems.trackIdOf(buildJsonObject { put("title", JsonPrimitive("x")) }, listOf("id")))
    }
}
