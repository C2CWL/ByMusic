package com.sosauce.chocola.core.data.plugin

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream

/** Reads `assets/plugin-runtime/<path>`; only plain relative names, never `..`. */
class AssetPluginLibrarySource(private val context: Context) : PluginLibrarySource {
    override fun read(path: String): String? {
        if (!SAFE_PATH.matches(path)) return null
        return try {
            context.assets.open("plugin-runtime/$path").bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private companion object {
        val SAFE_PATH = Regex("^(lib/)?[a-z0-9-]+\\.js$")
    }
}

object PluginLogcat {
    private const val TAG = "ByMusicPlugin"

    /** Plugin console output goes to logcat only, never to the diagnostics export. */
    fun log(pluginName: String, level: String, text: String) {
        val line = "[$pluginName] $text"
        when (level) {
            "error" -> Log.e(TAG, line)
            "warn" -> Log.w(TAG, line)
            "debug" -> Log.d(TAG, line)
            else -> Log.i(TAG, line)
        }
    }
}

/** Reads a plugin file the user picked (SAF). Size-capped, UTF-8, BOM removed. */
object PluginFileReader {
    fun read(context: Context, uri: Uri, maxBytes: Int = PluginLimits().maxSourceBytes): String {
        val input = context.contentResolver.openInputStream(uri)
            ?: throw PluginException(PluginErrorType.LOAD, "打不开这个文件")
        val bytes = input.use { stream ->
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val n = stream.read(buf)
                if (n < 0) break
                total += n
                if (total > maxBytes) throw PluginException(PluginErrorType.LOAD, "插件文件太大（超过 2MB）")
                out.write(buf, 0, n)
            }
            out.toByteArray()
        }
        return String(bytes, Charsets.UTF_8).removePrefix("\uFEFF")
    }
}
