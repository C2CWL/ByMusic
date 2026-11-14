package com.sosauce.chocola.core.data.diagnostics

import android.content.Context

/**
 * Persistent recording switch. Default is off. Uses a dedicated SharedPreferences file
 * so [DiagBootstrap] can read it synchronously during [android.app.Application.attachBaseContext]
 * before any process-start or crash logging.
 */
class DiagLogPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, DEFAULT_ENABLED)

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).commit()
    }

    companion object {
        const val PREFS_NAME = "bymusic_diag"
        const val KEY_ENABLED = "logging_enabled"
        const val DEFAULT_ENABLED = false
    }
}
