package com.sosauce.chocola.core.data.plugin

import android.content.Context

/** Remembers MusicFree-style subscription URLs the user chose to keep. No auto-install. */
class PluginSubscriptionStore(context: Context) {
    private val prefs = context.getSharedPreferences("plugin_subscriptions", Context.MODE_PRIVATE)

    fun list(): List<String> = (prefs.getStringSet(KEY, emptySet()) ?: emptySet()).toList().sorted()

    fun add(url: String) {
        val clean = url.trim()
        if (!clean.startsWith("https://", ignoreCase = true)) return
        prefs.edit().putStringSet(KEY, (list() + clean).toSet()).apply()
    }

    fun remove(url: String) {
        prefs.edit().putStringSet(KEY, (list() - url.trim()).toSet()).apply()
    }

    private companion object { const val KEY = "urls" }
}
