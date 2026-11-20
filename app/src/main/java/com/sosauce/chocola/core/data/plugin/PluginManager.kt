package com.sosauce.chocola.core.data.plugin

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URI

/** An installed plugin plus what happened the last time it was loaded. */
data class PluginEntry(
    val plugin: InstalledPlugin,
    /** Set when the last load failed; the plugin then is not used. */
    val loadError: String? = null
) {
    val id: String get() = plugin.id
    val manifest: PluginManifest get() = plugin.manifest
    val isUsable: Boolean get() = plugin.enabled && loadError == null
}

/** A plugin that was read and test-loaded, but not installed yet. */
data class PluginCandidate(
    val code: String,
    val manifest: PluginManifest,
    val sha256: String,
    val sizeBytes: Long,
    val sourceKind: PluginSourceKind,
    val sourceUrl: String?,
    /** The installed plugin with the same `platform`, if any. */
    val existing: InstalledPlugin?
) {
    /** >0 newer than installed, 0 same, <0 older. Null when not installed yet. */
    val versionVsInstalled: Int?
        get() = existing?.let { PluginVersion.compare(manifest.version, it.manifest.version) }
}

/** Downloads plugin source text (import from link, update from srcUrl). */
fun interface PluginSourceFetcher {
    suspend fun fetch(url: String): String
}

/** Uses the plugin HTTP client, so the same https/private-address rules apply. */
class HttpPluginSourceFetcher(
    private val http: PluginHttpClient,
    private val maxBytes: Int = PluginLimits().maxSourceBytes
) : PluginSourceFetcher {
    override suspend fun fetch(url: String): String {
        val response = http.execute(
            PluginHttpRequest(
                method = "GET",
                url = url.trim(),
                headers = mapOf("Accept" to "*/*"),
                timeoutMs = PluginHttpPolicy.MAX_TIMEOUT_MS
            )
        )
        response.error?.let { throw PluginException(PluginErrorType.LOAD, "下载失败：$it") }
        if (response.status !in 200..299) {
            throw PluginException(PluginErrorType.LOAD, "下载失败：服务器返回 ${response.status}")
        }
        val body = response.body.orEmpty()
        if (body.toByteArray(Charsets.UTF_8).size > maxBytes) {
            throw PluginException(PluginErrorType.LOAD, "插件文件太大（超过 2MB）")
        }
        return body
    }
}

/**
 * Installs, enables, updates and runs MusicFree-format plugins.
 *
 * ByMusic ships no plugin and no subscription address: every plugin comes from a file
 * or a link the user picks. Runtimes are created lazily for enabled plugins and kept
 * alive (plugins may keep state between calls) until the plugin is disabled, updated
 * or deleted.
 */
class PluginManager(
    private val store: PluginStore,
    private val host: PluginHost,
    private val fetcher: PluginSourceFetcher,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private val _plugins = MutableStateFlow<List<PluginEntry>>(emptyList())
    val plugins: StateFlow<List<PluginEntry>> = _plugins.asStateFlow()

    private val stateLock = Mutex()
    private val runtimeLock = Mutex()
    private val runtimes = HashMap<String, PluginRuntime>()
    private var loaded = false

    /** Reads the installed list from disk once. Safe to call repeatedly. */
    suspend fun ensureLoaded() {
        if (loaded) return
        stateLock.withLock {
            if (loaded) return
            val list = withContext(Dispatchers.IO) { store.list() }
            _plugins.value = list.map { PluginEntry(it) }
            loaded = true
        }
    }

    // ---------------------------------------------------------------- import

    suspend fun inspectFromUrl(url: String): PluginCandidate {
        val clean = url.trim()
        val code = fetcher.fetch(clean)
        return inspect(code, PluginSourceKind.URL, clean)
    }

    /** Reads a single script or a MusicFree catalog without fetching any of its entries. */
    suspend fun readImportFile(source: String, expectJson: Boolean): PluginImport =
        readImport(source, PluginSourceKind.LOCAL_FILE, null, expectJson)

    suspend fun readImportUrl(url: String): PluginImport {
        val clean = url.trim()
        val source = fetcher.fetch(clean)
        val isJson = URI(clean).path?.endsWith(".json", ignoreCase = true) == true
        return readImport(source, PluginSourceKind.URL, clean, isJson)
    }

    private suspend fun readImport(
        source: String,
        kind: PluginSourceKind,
        sourceUrl: String?,
        expectJson: Boolean
    ): PluginImport {
        val catalog = withContext(Dispatchers.Default) { PluginCatalog.readOrNull(source, expectJson) }
        if (catalog != null) return PluginImport.Catalog(catalog)
        return PluginImport.Script(inspect(source.removePrefix("\uFEFF"), kind, sourceUrl))
    }

    /** Test-loads [code] in a throwaway sandbox and reads its manifest. Nothing is saved. */
    suspend fun inspect(code: String, kind: PluginSourceKind, sourceUrl: String? = null): PluginCandidate {
        ensureLoaded()
        if (code.isBlank()) throw PluginException(PluginErrorType.LOAD, "文件是空的")
        val runtime = PluginRuntime.load(code, "plugin.js", host)
        val manifest = try {
            runtime.manifest
        } finally {
            runtime.close()
        }
        return PluginCandidate(
            code = code,
            manifest = manifest,
            sha256 = PluginStore.sha256(code),
            sizeBytes = code.toByteArray(Charsets.UTF_8).size.toLong(),
            sourceKind = kind,
            sourceUrl = sourceUrl,
            existing = _plugins.value.firstOrNull { it.id == manifest.platform }?.plugin
        )
    }

    /** Installs (or replaces) the plugin. Enabled state and user variables are kept on replace. */
    suspend fun install(candidate: PluginCandidate): InstalledPlugin {
        ensureLoaded()
        val id = candidate.manifest.platform
        val installed = stateLock.withLock {
            val previous = _plugins.value.firstOrNull { it.id == id }?.plugin
            val plugin = InstalledPlugin(
                id = id,
                fileName = PluginStore.fileNameFor(id),
                manifest = candidate.manifest,
                sha256 = candidate.sha256,
                sizeBytes = candidate.sizeBytes,
                sourceKind = candidate.sourceKind,
                sourceUrl = candidate.sourceUrl ?: previous?.sourceUrl,
                enabled = previous?.enabled ?: true,
                installedAtMs = clock(),
                userVariables = previous?.userVariables.orEmpty()
            )
            val next = _plugins.value.filterNot { it.id == id } + PluginEntry(plugin)
            withContext(Dispatchers.IO) {
                store.writeCode(plugin.fileName, candidate.code)
                store.save(next.map { it.plugin })
            }
            _plugins.value = next
            plugin
        }
        closeRuntime(id)
        return installed
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        updatePlugin(id) { it.copy(enabled = enabled) }
        if (!enabled) closeRuntime(id)
        else _plugins.update { list -> list.map { if (it.id == id) it.copy(loadError = null) else it } }
    }

    suspend fun setUserVariables(id: String, values: Map<String, String>) {
        val clean = values.mapValues { it.value.take(4096) }
        updatePlugin(id) { it.copy(userVariables = clean) }
        val runtime = runtimeLock.withLock { runtimes[id] }
        runtime?.setUserVariables(clean)
    }

    suspend fun delete(id: String) {
        ensureLoaded()
        closeRuntime(id)
        stateLock.withLock {
            val target = _plugins.value.firstOrNull { it.id == id } ?: return
            val next = _plugins.value.filterNot { it.id == id }
            withContext(Dispatchers.IO) {
                store.deleteCode(target.plugin.fileName)
                store.save(next.map { it.plugin })
            }
            _plugins.value = next
        }
    }

    /** Where "检查更新" looks: the plugin's own srcUrl, else the link it was imported from. */
    fun updateUrlOf(plugin: InstalledPlugin): String? =
        plugin.manifest.srcUrl?.takeIf { it.isNotBlank() } ?: plugin.sourceUrl

    /**
     * Downloads the plugin's update address and test-loads it. Returns the candidate when
     * it is newer than (or the same version but different code from) the installed one,
     * or null when there is nothing to update. Installing still needs [install].
     */
    suspend fun checkUpdate(id: String): PluginCandidate? {
        ensureLoaded()
        val plugin = _plugins.value.firstOrNull { it.id == id }?.plugin
            ?: throw PluginException(PluginErrorType.LOAD, "插件不存在")
        val url = updateUrlOf(plugin)
            ?: throw PluginException(PluginErrorType.LOAD, "这个插件没有提供更新地址")
        val candidate = inspectFromUrl(url)
        if (candidate.manifest.platform != plugin.id) {
            throw PluginException(PluginErrorType.LOAD, "更新地址里是另一个插件（${candidate.manifest.platform}）")
        }
        val cmp = PluginVersion.compare(candidate.manifest.version, plugin.manifest.version)
        return if (cmp > 0 || (cmp == 0 && candidate.sha256 != plugin.sha256)) candidate else null
    }

    /** Plugin source as installed, for "查看代码". */
    suspend fun readCode(id: String): String? {
        ensureLoaded()
        val plugin = _plugins.value.firstOrNull { it.id == id }?.plugin ?: return null
        return withContext(Dispatchers.IO) { store.readCode(plugin) }
    }

    // --------------------------------------------------------------- running

    /** The running sandbox of an enabled plugin, loading it if needed. */
    suspend fun runtime(id: String): PluginRuntime {
        ensureLoaded()
        val entry = _plugins.value.firstOrNull { it.id == id }
            ?: throw PluginException(PluginErrorType.LOAD, "没有找到插件“$id”，可能已经删除")
        if (!entry.plugin.enabled) throw PluginException(PluginErrorType.LOAD, "插件“$id”已停用")
        return runtimeLock.withLock {
            runtimes[id]?.takeIf { !it.isClosed }?.let { return@withLock it }
            val code = withContext(Dispatchers.IO) { store.readCode(entry.plugin) }
                ?: throw PluginException(PluginErrorType.LOAD, "插件文件丢失，请重新导入").also { markError(id, it.message) }
            val runtime = try {
                PluginRuntime.load(code, "plugin.js", host, entry.plugin.userVariables)
            } catch (e: CancellationException) {
                throw e
            } catch (e: PluginException) {
                markError(id, e.message)
                throw e
            }
            markError(id, null)
            runtimes[id] = runtime
            runtime
        }
    }

    /** Calls a protocol method on an enabled plugin. */
    suspend fun call(
        id: String,
        method: String,
        args: List<kotlinx.serialization.json.JsonElement>,
        timeoutMillis: Long = host.limits.callTimeoutMillis
    ): kotlinx.serialization.json.JsonElement = runtime(id).call(method, args, timeoutMillis)

    fun usablePlugins(): List<PluginEntry> = _plugins.value.filter { it.isUsable }

    suspend fun closeAll() {
        runtimeLock.withLock {
            runtimes.values.forEach { it.close() }
            runtimes.clear()
        }
    }

    private suspend fun closeRuntime(id: String) {
        runtimeLock.withLock { runtimes.remove(id) }?.close()
    }

    private fun markError(id: String, message: String?) {
        _plugins.update { list -> list.map { if (it.id == id) it.copy(loadError = message) else it } }
    }

    private suspend fun updatePlugin(id: String, change: (InstalledPlugin) -> InstalledPlugin) {
        ensureLoaded()
        stateLock.withLock {
            val next = _plugins.value.map { if (it.id == id) it.copy(plugin = change(it.plugin)) else it }
            withContext(Dispatchers.IO) { store.save(next.map { it.plugin }) }
            _plugins.value = next
        }
    }
}
