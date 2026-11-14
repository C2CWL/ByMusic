package com.sosauce.chocola.core.data.diagnostics

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Process
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight at process start: when recording is off (the default) this only reads the
 * switch and installs a chaining crash handler. Log files, writer threads, and historical
 * exit-reason I/O start only after recording is enabled, and then only on a background thread.
 */
object DiagBootstrap {
    @Volatile
    private var installed = false

    @Volatile
    private var appRef: Application? = null

    private val storageOpened = AtomicBoolean(false)

    private val background = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "bymusic-diag-bg").apply { isDaemon = true }
    }

    fun install(app: Application) {
        synchronized(this) {
            if (installed) return
            try {
                appRef = app
                val recordingEnabled = runCatching { DiagLogPreferences(app).isEnabled() }
                    .getOrDefault(DiagLogPreferences.DEFAULT_ENABLED)
                val processName = runCatching { Application.getProcessName() }
                    .getOrElse { app.packageName }
                DiagLogger.setProcessInfo(
                    DiagLogger.ProcessInfo(pid = Process.myPid(), processName = processName)
                )
                DiagLogger.setRecordingEnabledFlag(recordingEnabled)
                val previous = Thread.getDefaultUncaughtExceptionHandler()
                if (previous !is DiagExceptionHandler) {
                    Thread.setDefaultUncaughtExceptionHandler(DiagExceptionHandler(previous))
                }
                app.registerActivityLifecycleCallbacks(activityCallbacks)
                app.registerComponentCallbacks(componentCallbacks)
                installed = true
                if (recordingEnabled) {
                    openStorage(app)
                    scheduleBackgroundSnapshot(app, processName)
                }
            } catch (t: Throwable) {
                runCatching { android.util.Log.e("ByMusicDiag", "failed to install diagnostic logging", t) }
            }
        }
    }

    /**
     * Called from settings. Enabling opens storage and starts background collection.
     * Disabling only stops new records; existing files are kept.
     */
    fun setRecordingEnabledFromUser(context: Context, enabled: Boolean) {
        runCatching { DiagLogPreferences(context).setEnabled(enabled) }
        val app = appRef ?: context.applicationContext as? Application
        if (enabled) {
            if (app != null) openStorage(app)
            DiagLogger.setRecordingEnabled(true)
            if (app != null) {
                val processName = DiagLogger.currentProcessInfo().processName
                scheduleBackgroundSnapshot(app, processName)
            }
        } else {
            DiagLogger.setRecordingEnabled(false)
        }
    }

    private fun openStorage(app: Application) {
        if (!storageOpened.compareAndSet(false, true)) return
        try {
            val sink = DiagLogFileSink(DiagLogger.defaultDir(app.filesDir))
            val writer = DiagLogWriter(sink)
            DiagLogger.attachStorage(sink, writer)
        } catch (t: Throwable) {
            storageOpened.set(false)
            runCatching { android.util.Log.e("ByMusicDiag", "failed to open diagnostic log storage", t) }
        }
    }

    private fun scheduleBackgroundSnapshot(app: Application, processName: String) {
        background.execute {
            try {
                if (!DiagLogger.isRecordingEnabled()) return@execute
                logProcessStart(app, processName)
                logHistoricalExits(app)
                DiagLogger.flushBestEffort(1_000)
            } catch (t: Throwable) {
                runCatching { DiagLogger.w("Process", "background diagnostic snapshot failed", t) }
            }
        }
    }

    private fun logProcessStart(app: Application, processName: String) {
        val meta = DiagDeviceInfo.exportMeta(app)
        val runtime = Runtime.getRuntime()
        val availMb = runtime.maxMemory() / (1024 * 1024)
        val usedMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        DiagLogger.i(
            "Process",
            "start mark=${DiagLogger.BUILD_MARK} package=${meta.packageName} " +
                "version=${meta.versionName} (${meta.versionCode}) " +
                "process=$processName pid=${Process.myPid()} " +
                "device=${meta.manufacturer} ${meta.model} android=${meta.androidRelease} " +
                "api=${meta.sdkInt} display=${meta.display} incremental=${meta.incremental} " +
                "patch=${meta.securityPatch} heapUsedMb=$usedMb heapMaxMb=$availMb"
        )
    }

    private fun logHistoricalExits(app: Application) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            DiagLogger.i(
                "ExitReasons",
                "historical exit reasons require API 30+ (this device is API ${Build.VERSION.SDK_INT})"
            )
            return
        }
        try {
            val manager = app.getSystemService(ActivityManager::class.java) ?: return
            val exits = manager.getHistoricalProcessExitReasons(app.packageName, 0, 8)
            DiagLogger.i("ExitReasons", "historical process exits: ${exits.size}")
            for (info in exits) {
                val trace = runCatching {
                    info.traceInputStream?.use { stream ->
                        DiagExitReasons.readBounded(stream, DiagExitReasons.MAX_TRACE_CHARS)
                    }
                }.getOrNull()
                val snapshot = DiagExitSnapshot(
                    timestampMillis = info.timestamp,
                    pid = info.pid,
                    processName = info.processName,
                    reasonCode = info.reason,
                    status = info.status,
                    importance = info.importance,
                    pssKb = info.pss.takeIf { it > 0 },
                    description = info.description,
                    traceExcerpt = DiagExitReasons.truncateTrace(trace)
                )
                DiagLogger.i("ExitReasons", DiagExitReasons.format(snapshot))
            }
        } catch (t: Throwable) {
            DiagLogger.w("ExitReasons", "unable to read historical exit reasons", t)
        }
    }

    private val activityCallbacks = object : ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
            DiagLogger.i(
                "Lifecycle",
                "activity created ${activity.javaClass.simpleName} hasState=${savedInstanceState != null}"
            )
        }

        override fun onActivityStarted(activity: Activity) {
            DiagLogger.i("Lifecycle", "activity started ${activity.javaClass.simpleName}")
        }

        override fun onActivityResumed(activity: Activity) {
            DiagLogger.i("Lifecycle", "activity resumed ${activity.javaClass.simpleName}")
        }

        override fun onActivityPaused(activity: Activity) {
            DiagLogger.i("Lifecycle", "activity paused ${activity.javaClass.simpleName}")
        }

        override fun onActivityStopped(activity: Activity) {
            DiagLogger.i("Lifecycle", "activity stopped ${activity.javaClass.simpleName}")
            DiagLogger.flushBestEffort(250)
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) {
            DiagLogger.i("Lifecycle", "activity destroyed ${activity.javaClass.simpleName}")
            DiagLogger.flushBestEffort(250)
        }
    }

    private val componentCallbacks = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) {
            DiagLogger.i(
                "Process",
                "configuration changed orientation=${newConfig.orientation} densityDpi=${newConfig.densityDpi}"
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onLowMemory() {
            DiagLogger.w("Process", "onLowMemory")
            DiagLogger.flushBestEffort(250)
        }

        override fun onTrimMemory(level: Int) {
            DiagLogger.w("Process", "onTrimMemory level=$level label=${trimLabel(level)}")
            DiagLogger.flushBestEffort(250)
        }
    }

    private fun trimLabel(level: Int): String = when (level) {
        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
        ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
        ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE"
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
        else -> "LEVEL_$level"
    }
}
