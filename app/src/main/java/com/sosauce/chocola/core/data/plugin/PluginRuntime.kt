package com.sosauce.chocola.core.data.plugin

import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.QuickJsInterruptedException
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import java.io.Closeable
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.cancellation.CancellationException

/** Resource limits for one plugin sandbox. */
data class PluginLimits(
    /** QuickJS heap limit for this plugin. */
    val memoryBytes: Long = 64L * 1024 * 1024,
    /** QuickJS stack limit; the runtime thread has a larger native stack. */
    val stackBytes: Long = 1024L * 1024,
    /** Max time spent running JavaScript in one evaluation (busy loops are interrupted). */
    val cpuMillisPerEvaluation: Long = 10_000,
    /** Max wall time to load a plugin (including requests made while loading). */
    val loadTimeoutMillis: Long = 15_000,
    /** Default max wall time for one plugin call (including its network requests). */
    val callTimeoutMillis: Long = 20_000,
    /** Plugin source size limit. */
    val maxSourceBytes: Int = 2 * 1024 * 1024
)

/** Reads files under `assets/plugin-runtime/` (prelude.js and lib/<name>.js). */
fun interface PluginLibrarySource {
    fun read(path: String): String?
}

/** Everything a plugin can reach, all provided by the host. */
class PluginHost(
    val libraries: PluginLibrarySource,
    val http: PluginHttpClient,
    val log: (pluginName: String, level: String, text: String) -> Unit = { _, _, _ -> },
    val limits: PluginLimits = PluginLimits(),
    val appVersion: String = "0.0.0",
    /**
     * false while a plugin is only being inspected: its top level still runs (that is how the
     * manifest is read) but every host request fails instead of reaching the network.
     */
    val networkEnabled: Boolean = true
) {
    private val cache = ConcurrentHashMap<String, String>()

    internal fun library(path: String): String? = cache[path] ?: libraries.read(path)?.also { cache[path] = it }

    /** Same host with plugin network access switched on or off. */
    fun withNetwork(enabled: Boolean): PluginHost =
        PluginHost(libraries, http, log, limits, appVersion, enabled)
}

enum class PluginErrorType { LOAD, METHOD_MISSING, TIMEOUT, SCRIPT, CLOSED }

class PluginException(
    val type: PluginErrorType,
    message: String,
    cause: Throwable? = null
) : Exception(message, cause)

/**
 * One MusicFree-format plugin running in its own QuickJS runtime.
 *
 * Sandbox: QuickJS without its std/os modules, so there is no file system, process or
 * native access. The only host capabilities are the `__bm_host_*` functions defined here
 * (console, secure random, whitelisted bundled libraries, http(s) through
 * [PluginHttpClient], sleep). Memory, stack, CPU time and wall time are limited
 * by [PluginLimits].
 *
 * Calls are serialized per plugin. All JavaScript runs on one dedicated thread with
 * an 8 MB native stack; network requests run on the IO dispatcher.
 */
class PluginRuntime private constructor(
    private val quickJs: QuickJs,
    val manifest: PluginManifest,
    private val limits: PluginLimits,
    private val resultSlot: ResultSlot
) : Closeable {

    private val mutex = Mutex()
    private val closed = AtomicBoolean(false)

    val isClosed: Boolean get() = closed.get()

    /**
     * Calls `instance[method](...args)` and returns its result as JSON (`null` for
     * undefined). Throws [PluginException].
     */
    suspend fun call(
        method: String,
        args: List<JsonElement> = emptyList(),
        timeoutMillis: Long = limits.callTimeoutMillis
    ): JsonElement = mutex.withLock {
        if (closed.get()) throw PluginException(PluginErrorType.CLOSED, "插件已关闭")
        val argsJson = JSON.encodeToString(JsonArray.serializer(), JsonArray(args))
        val code = "await __bm_call(${jsString(method)}, ${jsString(argsJson)}).then(__bm_host_return); void 0;"
        val raw = run(code, "bymusic-call-$method.js", timeoutMillis)
        if (raw == null) JsonNull else JSON.parseToJsonElement(raw)
    }

    /** Updates the values returned by `env.getUserVariables()`. */
    suspend fun setUserVariables(values: Map<String, String>) = mutex.withLock {
        if (closed.get()) return@withLock
        val json = JSON.encodeToString(MapSerializer(String.serializer(), String.serializer()), values)
        run("__bm_set_user_variables(${jsString(json)}); void 0;", "bymusic-vars.js", limits.loadTimeoutMillis)
    }

    private suspend fun run(code: String, fileName: String, timeoutMillis: Long): String? =
        withContext(PluginThread.dispatcher) {
            resultSlot.value = null
            try {
                withTimeout(timeoutMillis) { quickJs.evaluate<Any?>(code, fileName) }
                resultSlot.value
            } catch (e: TimeoutCancellationException) {
                throw PluginException(PluginErrorType.TIMEOUT, "插件响应超时", e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: QuickJsInterruptedException) {
                throw PluginException(PluginErrorType.TIMEOUT, "插件运行太久，已被停止", e)
            } catch (e: QuickJsException) {
                throw mapScriptError(e)
            } finally {
                resultSlot.value = null
            }
        }

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                quickJs.close()
            } catch (_: Throwable) {
                // already closed
            }
        }
    }

    internal class ResultSlot {
        @Volatile
        var value: String? = null
    }

    companion object {
        internal val JSON = Json { ignoreUnknownKeys = true; explicitNulls = false }
        private val random = SecureRandom()
        private const val METHOD_MISSING = "BM_METHOD_MISSING:"

        /**
         * Runs [code] once and reads what it exports. The returned runtime keeps the plugin
         * loaded (MusicFree plugins may keep state between calls); [close] it when done.
         */
        suspend fun load(
            code: String,
            fileName: String,
            host: PluginHost,
            userVariables: Map<String, String> = emptyMap()
        ): PluginRuntime {
            val limits = host.limits
            if (code.toByteArray(Charsets.UTF_8).size > limits.maxSourceBytes) {
                throw PluginException(PluginErrorType.LOAD, "插件文件太大（超过 2MB）")
            }
            val prelude = host.library("prelude.js")
                ?: throw PluginException(PluginErrorType.LOAD, "插件运行环境缺失")
            return withContext(PluginThread.dispatcher) {
                val quickJs = QuickJs.create(jobDispatcher = PluginThread.dispatcher)
                val slot = ResultSlot()
                var nameForLogs = fileName
                try {
                    quickJs.memoryLimit = limits.memoryBytes
                    quickJs.maxStackSize = limits.stackBytes
                    quickJs.evaluationTimeoutMillis = limits.cpuMillisPerEvaluation
                    defineHostFunctions(quickJs, host, slot) { nameForLogs }

                    val manifestJson = withTimeout(limits.loadTimeoutMillis) {
                        quickJs.evaluate<Any?>(prelude + "\n;void 0;", "bymusic-prelude.js")
                        // Header on the same line so error line numbers match the plugin file.
                        quickJs.evaluate<Any?>(
                            "globalThis.__bm_plugin_factory = (function (module, exports, require, env) {" +
                                code + "\n}); void 0;",
                            fileName
                        )
                        val env = JSON.encodeToString(
                            PluginEnvPayload.serializer(),
                            PluginEnvPayload(userVariables = userVariables, appVersion = host.appVersion)
                        )
                        quickJs.evaluate<Any?>(
                            "__bm_host_return(__bm_init(${jsString(env)})); void 0;",
                            "bymusic-init.js"
                        )
                        slot.value
                    } ?: throw PluginException(PluginErrorType.LOAD, "插件没有返回信息")
                    slot.value = null
                    val manifest = try {
                        JSON.decodeFromString(PluginManifest.serializer(), manifestJson)
                    } catch (e: Exception) {
                        throw PluginException(PluginErrorType.LOAD, "插件信息读不出来", e)
                    }
                    if (manifest.platform.isBlank()) {
                        throw PluginException(PluginErrorType.LOAD, "插件没有填写名称（platform）")
                    }
                    if (manifest.platform == "本地") {
                        throw PluginException(PluginErrorType.LOAD, "插件名称不能叫“本地”")
                    }
                    nameForLogs = manifest.platform
                    PluginRuntime(quickJs, manifest, limits, slot)
                } catch (e: Throwable) {
                    try {
                        quickJs.close()
                    } catch (_: Throwable) {
                    }
                    throw when (e) {
                        is PluginException -> e
                        is TimeoutCancellationException ->
                            PluginException(PluginErrorType.LOAD, "插件加载超时", e)
                        is CancellationException -> e
                        is QuickJsInterruptedException ->
                            PluginException(PluginErrorType.LOAD, "插件加载运行太久，已被停止", e)
                        is QuickJsException ->
                            PluginException(PluginErrorType.LOAD, "加载失败：${cleanMessage(e.message)}", e)
                        else -> PluginException(PluginErrorType.LOAD, "加载失败：${e.message ?: e::class.java.simpleName}", e)
                    }
                }
            }
        }

        private fun defineHostFunctions(
            quickJs: QuickJs,
            host: PluginHost,
            slot: ResultSlot,
            pluginName: () -> String
        ) {
            quickJs.function("__bm_host_log") { args ->
                host.log(pluginName(), args.getOrNull(0)?.toString() ?: "log", args.getOrNull(1)?.toString() ?: "")
                null as String?
            }
            quickJs.function("__bm_host_random") { args ->
                val n = (args.getOrNull(0) as? Number)?.toInt()?.coerceIn(0, 65536) ?: 0
                val bytes = ByteArray(n).also(random::nextBytes)
                bytes.joinToString("") { "%02x".format(it.toInt() and 0xFF) }
            }
            quickJs.function("__bm_host_lib") { args ->
                val name = args.getOrNull(0)?.toString()
                if (name != null && name in BUNDLED_LIBRARIES) host.library("lib/$name.js") else null
            }
            quickJs.function("__bm_host_return") { args ->
                slot.value = args.getOrNull(0)?.toString()
                null as String?
            }
            quickJs.asyncFunction("__bm_host_http") { args ->
                val response = if (!host.networkEnabled) {
                    // Inspect only reads the manifest. Nothing the plugin's top level does may
                    // reach the network before the user has seen and accepted the risk.
                    PluginHttpResponse.failure("确认插件信息时不会联网", "ERR_BLOCKED")
                } else {
                    try {
                        val request = JSON.decodeFromString(PluginHttpRequest.serializer(), args[0].toString())
                        host.http.execute(request)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        PluginHttpResponse.failure("请求失败：${e.message ?: e::class.java.simpleName}")
                    }
                }
                JSON.encodeToString(PluginHttpResponse.serializer(), response)
            }
            quickJs.asyncFunction("__bm_host_sleep") { args ->
                val ms = (args.getOrNull(0) as? Number)?.toLong()?.coerceIn(0, 30_000) ?: 0
                delay(ms)
                null as String?
            }
        }

        /** Libraries the prelude may ask for; must match prelude.js BUNDLED. */
        val BUNDLED_LIBRARIES = setOf("crypto-js", "dayjs", "qs", "he", "big-integer", "cheerio")

        private fun mapScriptError(e: QuickJsException): PluginException {
            val msg = e.message.orEmpty()
            val idx = msg.indexOf(METHOD_MISSING)
            if (idx >= 0) {
                val name = msg.substring(idx + METHOD_MISSING.length).takeWhile { it.isLetterOrDigit() || it == '_' }
                return PluginException(PluginErrorType.METHOD_MISSING, "插件没有提供 $name", e)
            }
            return PluginException(PluginErrorType.SCRIPT, cleanMessage(msg), e)
        }

        internal fun cleanMessage(message: String?): String {
            val first = message.orEmpty().lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim()
            return first.take(300).ifEmpty { "插件出错" }
        }

        /** A JSON string literal is a valid JavaScript string literal. */
        internal fun jsString(value: String): String =
            JSON.encodeToString(JsonPrimitive.serializer(), JsonPrimitive(value))
    }
}

@kotlinx.serialization.Serializable
internal data class PluginEnvPayload(
    val userVariables: Map<String, String>,
    val appVersion: String
)

/** The single thread all plugin JavaScript runs on. Big native stack for QuickJS. */
object PluginThread {
    val dispatcher: CoroutineDispatcher by lazy {
        Executors.newSingleThreadExecutor { r ->
            Thread(null, r, "bymusic-plugin-js", 8L * 1024 * 1024).apply { isDaemon = true }
        }.asCoroutineDispatcher()
    }
}
