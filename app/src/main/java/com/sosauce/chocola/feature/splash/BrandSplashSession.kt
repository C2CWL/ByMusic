package com.sosauce.chocola.feature.splash

/**
 * Process-scoped flag so a configuration change or a return to an already
 * started activity does not show the in-app loading page again. A process
 * death starts a new session and may show it once more.
 */
object BrandSplashSession {
    @Volatile
    var completed: Boolean = false
        private set

    fun markCompleted() {
        completed = true
    }

    fun resetForTests() {
        completed = false
    }
}
