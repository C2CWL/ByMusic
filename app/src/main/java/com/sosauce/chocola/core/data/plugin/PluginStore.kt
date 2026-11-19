package com.sosauce.chocola.core.data.plugin

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest

/**
 * Plugins live only in the app's private storage:
 *
 *   <filesDir>/plugins/index.json      installed plugins (metadata, enabled, variables)
 *   <filesDir>/plugins/code/<hash>.js  plugin source, exactly as imported
 *
 * Plugin code never gets a path to these files; the host reads them and hands the text
 * to the sandbox. Blocking file I/O: call off the main thread.
 */
class PluginStore(private val root: File) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }
    private val index = File(root, "index.json")
    private val codeDir = File(root, "code")
    private val lock = Any()

    fun list(): List<InstalledPlugin> = synchronized(lock) {
        if (!index.isFile) return emptyList()
        runCatching {
            json.decodeFromString(ListSerializer(InstalledPlugin.serializer()), index.readText())
        }.getOrDefault(emptyList())
    }

    fun save(plugins: List<InstalledPlugin>) = synchronized(lock) {
        root.mkdirs()
        atomicWrite(index, json.encodeToString(ListSerializer(InstalledPlugin.serializer()), plugins))
    }

    fun readCode(plugin: InstalledPlugin): String? = synchronized(lock) {
        val f = codeFile(plugin.fileName) ?: return null
        if (f.isFile) f.readText() else null
    }

    fun writeCode(fileName: String, code: String) = synchronized(lock) {
        val f = codeFile(fileName) ?: error("bad plugin file name")
        codeDir.mkdirs()
        atomicWrite(f, code)
    }

    fun deleteCode(fileName: String) = synchronized(lock) {
        codeFile(fileName)?.delete()
    }

    /** Only names we generated ([fileNameFor]) are accepted, never a path. */
    private fun codeFile(fileName: String): File? {
        if (!FILE_NAME.matches(fileName)) return null
        return File(codeDir, fileName)
    }

    private fun atomicWrite(target: File, text: String) {
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
    }

    companion object {
        const val DIR_NAME = "plugins"
        private val FILE_NAME = Regex("^[0-9a-f]{40}\\.js$")

        fun fileNameFor(pluginId: String): String = sha1(pluginId) + ".js"

        fun sha256(text: String): String = digest("SHA-256", text)

        private fun sha1(text: String): String = digest("SHA-1", text)

        private fun digest(algorithm: String, text: String): String =
            MessageDigest.getInstance(algorithm).digest(text.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
    }
}

/** MusicFree compares versions part by part ("1.2.4" > "1.2.3", "2.0.0" > "1.99.99"). */
object PluginVersion {
    fun compare(a: String?, b: String?): Int {
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0L }
            val y = pb.getOrElse(i) { 0L }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private fun parts(v: String?): List<Long> =
        (v ?: "0.0.0").trim().removePrefix("v").substringBefore('-').substringBefore('+')
            .split('.').map { p -> p.takeWhile(Char::isDigit).toLongOrNull() ?: 0L }
}
