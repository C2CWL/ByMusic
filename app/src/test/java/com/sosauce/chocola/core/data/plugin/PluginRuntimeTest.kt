package com.sosauce.chocola.core.data.plugin

import com.sosauce.chocola.core.data.plugin.PluginTestSupport.fictionalPlugin
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PluginRuntimeTest {

    private fun <T> withPlugin(
        code: String = fictionalPlugin,
        host: PluginHost = PluginTestSupport.host(),
        vars: Map<String, String> = emptyMap(),
        block: suspend (PluginRuntime) -> T
    ): T = runBlocking {
        val runtime = PluginRuntime.load(code, "plugin.js", host, vars)
        try {
            block(runtime)
        } finally {
            runtime.close()
        }
    }

    private fun JsonObject.str(key: String) = this[key]?.jsonPrimitive?.contentOrNull

    private suspend fun expectError(type: PluginErrorType?, block: suspend () -> Unit): PluginException {
        try {
            block()
        } catch (e: PluginException) {
            if (type != null) assertEquals(e.message, type, e.type)
            return e
        }
        fail("expected PluginException")
        error("unreachable")
    }

    @Test
    fun readsManifestAndReportsMissingModules() = withPlugin { rt ->
        val m = rt.manifest
        assertEquals("虚构音源", m.platform)
        assertEquals("1.2.0", m.version)
        assertEquals("ByMusic tests", m.author)
        assertEquals("https://plugins.example.com/fictional.js", m.srcUrl)
        assertEquals(listOf("id"), m.effectivePrimaryKey)
        assertEquals(listOf("music", "album"), m.supportedSearchType)
        assertEquals(listOf("token"), m.userVariables.map { it.key })
        assertTrue(m.canSearchMusic)
        assertTrue(m.has("getMediaSource"))
        assertFalse(m.has("getTopLists"))
        assertEquals(listOf("search", "getMediaSource", "getLyric"), m.protocolMethods)
        assertEquals(listOf("webdav"), m.missingModules)
        assertTrue("crypto-js" in m.requiredModules)
    }

    @Test
    fun searchGoesThroughHostHttpWithParamsAndUserVariables() {
        val http = PluginTestSupport.FakeHttp()
        withPlugin(host = PluginTestSupport.host(http), vars = mapOf("token" to "abc")) { rt ->
            val result = rt.call(
                "search",
                listOf(JsonPrimitive("周"), JsonPrimitive(1), JsonPrimitive("music"))
            ).jsonObject
            val data = result["data"]!!.jsonArray
            assertEquals(2, data.size)
            assertEquals("晴&天", data[0].jsonObject.str("title"))
            val req = http.requests.single()
            assertEquals("GET", req.method)
            assertEquals("https://api.example.com/search?q=%E5%91%A8&page=1&type=music", req.url)
            assertEquals("abc", req.headers["X-Token"])

            rt.setUserVariables(mapOf("token" to "new"))
            rt.call("search", listOf(JsonPrimitive("x"), JsonPrimitive(2), JsonPrimitive("music")))
            assertEquals("new", http.requests.last().headers["X-Token"])
        }
    }

    @Test
    fun previewRuntimeCannotReachTheNetwork() {
        val http = PluginTestSupport.FakeHttp()
        withPlugin(host = PluginTestSupport.host(http).withNetwork(false)) { rt ->
            // The call fails, but that is the point: nothing may leave the phone before the
            // user has seen the risk sheet and accepted the plugin.
            runCatching {
                rt.call("search", listOf(JsonPrimitive("周"), JsonPrimitive(1), JsonPrimitive("music")))
            }
            assertTrue("预览插件时不能发网络请求", http.requests.isEmpty())
        }
    }

    @Test
    fun postSendsJsonBody() = withPlugin { rt ->
        val out = rt.call("post", listOf(PluginTestSupport.obj("""{"a":1}"""))).jsonObject
        assertEquals(200, out["status"]!!.jsonPrimitive.int)
        assertEquals(1, out["data"]!!.jsonObject["a"]!!.jsonPrimitive.int)
    }

    @Test
    fun bundledLibrariesWork() = withPlugin { rt ->
        val out = rt.call("libs").jsonObject
        assertEquals("647fa0e73c6cb77ef3a66eb691217ae2", out.str("md5"))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", out.str("sha256"))
        assertEquals("秘密", out.str("aes"))
        assertEquals(86_400_000L, out["date"]!!.jsonPrimitive.long)
        assertEquals("a=1&b[]=x&b[]=y", out.str("qs"))
        assertEquals("&#x3C;a&#x3E;", out.str("he"))
        assertEquals("一,二", out.str("cheerio"))
        assertEquals("246913578024691357802469135780", out.str("big"))
        assertEquals("6Z+z5LmQ", out.str("base64"))
        assertEquals("YWJj", out.str("btoa"))
    }

    @Test
    fun pluginCannotReachFilesOrHostInternals() = withPlugin { rt ->
        val out = rt.call("sandbox").jsonObject
        assertEquals("undefined", out.str("std"))
        assertEquals("undefined", out.str("os"))
        assertEquals("undefined", out.str("process"))
        assertEquals("undefined", out.str("globalRequire"))
        assertEquals("undefined", out.str("hostHttp"))
        assertTrue(out.str("fsError")!!.contains("fs"))
    }

    @Test
    fun timersWork() = withPlugin { rt ->
        assertEquals("done", rt.call("wait", listOf(JsonPrimitive(150))).jsonPrimitive.content)
    }

    @Test
    fun missingMethodIsReported() = withPlugin { rt ->
        val e = expectError(PluginErrorType.METHOD_MISSING) { rt.call("getTopLists") }
        assertTrue(e.message!!.contains("getTopLists"))
    }

    @Test
    fun scriptErrorsAreReported() = withPlugin { rt ->
        val e = expectError(PluginErrorType.SCRIPT) {
            rt.call("getMediaSource", listOf(PluginTestSupport.obj("""{"id":"1"}"""), JsonPrimitive("super")))
        }
        assertTrue(e.message, e.message!!.contains("super"))
    }

    @Test
    fun busyLoopIsStopped() = withPlugin(
        host = PluginTestSupport.host(limits = PluginLimits(cpuMillisPerEvaluation = 300, callTimeoutMillis = 3_000))
    ) { rt ->
        val started = System.nanoTime()
        expectError(PluginErrorType.TIMEOUT) { rt.call("spin") }
        assertTrue((System.nanoTime() - started) / 1_000_000 < 5_000)
    }

    @Test
    fun slowCallTimesOut() = withPlugin { rt ->
        expectError(PluginErrorType.TIMEOUT) { rt.call("wait", listOf(JsonPrimitive(5_000)), timeoutMillis = 400) }
        Unit
    }

    @Test
    fun memoryLimitIsEnforced() = withPlugin(
        host = PluginTestSupport.host(limits = PluginLimits(memoryBytes = 24L * 1024 * 1024))
    ) { rt ->
        expectError(null) { rt.call("hog") }
        Unit
    }

    @Test
    fun rejectsBadPlugins() = runBlocking {
        val host = PluginTestSupport.host()
        expectError(PluginErrorType.LOAD) {
            PluginRuntime.load("module.exports = { platform: '本地', search() {} };", "p.js", host).close()
        }
        expectError(PluginErrorType.LOAD) {
            PluginRuntime.load("module.exports = { version: '1' };", "p.js", host).close()
        }
        expectError(PluginErrorType.LOAD) {
            PluginRuntime.load("this is not javascript", "p.js", host).close()
        }
        expectError(PluginErrorType.LOAD) {
            PluginRuntime.load("for(;;){}", "p.js", PluginTestSupport.host(limits = PluginLimits(cpuMillisPerEvaluation = 300))).close()
        }
        Unit
    }

    @Test
    fun undefinedResultIsNull() = runBlocking {
        val rt = PluginRuntime.load(
            "module.exports = { platform: 'p', async getLyric() { return undefined; } };",
            "p.js",
            PluginTestSupport.host()
        )
        try {
            assertEquals(kotlinx.serialization.json.JsonNull, rt.call("getLyric"))
        } finally {
            rt.close()
        }
        val closedError = expectError(PluginErrorType.CLOSED) { rt.call("getLyric") }
        assertNull(closedError.cause)
    }
}
