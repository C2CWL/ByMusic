package com.sosauce.chocola.core.data.plugin

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import java.io.File
import java.util.Collections

/** Test helpers: real prelude/libraries from src/main/assets, fake network, fictional plugin. */
internal object PluginTestSupport {

    private fun locate(relative: String): File {
        val candidates = listOf(File(relative), File("app/$relative"), File("../$relative"))
        return candidates.firstOrNull { it.exists() }
            ?: error("cannot find $relative from ${File(".").absolutePath}")
    }

    /** Reads `src/main/assets/plugin-runtime/` like the app's AssetPluginLibrarySource. */
    val assets = PluginLibrarySource { path ->
        val root = locate("src/main/assets/plugin-runtime")
        File(root, path).takeIf { it.isFile }?.readText()
    }

    /** The fictional plugin (made-up hosts, no real source). */
    val fictionalPlugin: String by lazy {
        requireNotNull(PluginTestSupport::class.java.getResource("/plugins/fictional-plugin.js")) {
            "fictional-plugin.js missing from test resources"
        }.readText()
    }

    fun fictionalPlugin(version: String): String =
        fictionalPlugin.replace("version: '1.2.0'", "version: '$version'")

    /** Answers the fictional hosts; records every request. */
    class FakeHttp : PluginHttpClient {
        val requests: MutableList<PluginHttpRequest> = Collections.synchronizedList(mutableListOf())

        override suspend fun execute(request: PluginHttpRequest): PluginHttpResponse {
            requests += request
            PluginHttpPolicy.check(request)?.let { return PluginHttpResponse.failure(it) }
            return when {
                request.url.startsWith("https://api.example.com/search") -> json(
                    """{"isEnd":false,"songs":[
                        {"id":1,"name":"晴&amp;天","singer":"甲","album":"乙","duration":200,"pic":"https://img.example.com/1.jpg"},
                        {"id":2,"name":"雨","singer":"丙","album":"","duration":"185"}
                    ]}"""
                )
                request.url == "https://api.example.com/echo" -> json(request.body ?: "null")
                else -> PluginHttpResponse(status = 404, statusText = "Not Found", body = "")
            }
        }

        private fun json(body: String) = PluginHttpResponse(
            status = 200,
            statusText = "OK",
            headers = mapOf("content-type" to "application/json"),
            body = body
        )
    }

    class LogSink {
        val lines: MutableList<String> = Collections.synchronizedList(mutableListOf())
        val log: (String, String, String) -> Unit = { _, _, text -> lines += text }
    }

    fun host(
        http: PluginHttpClient = FakeHttp(),
        limits: PluginLimits = PluginLimits(),
        logs: LogSink = LogSink()
    ) = PluginHost(libraries = assets, http = http, log = logs.log, limits = limits, appVersion = "9.9.9")

    fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
}
