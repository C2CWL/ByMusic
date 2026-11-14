package com.sosauce.chocola.core.data.diagnostics

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.Application.ActivityLifecycleCallbacks
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.Process
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

object DiagBootstrap {
    @Volatile
    private var installed = false

    fun install(app: Application) {
        synchronized(this) {
            if (installed) return
            try {
                val sink = DiagLogFileSink(DiagLogger.defaultDir(app.filesDir))
                val writer = DiagLogWriter(sink)
                val processName = runCatching { Application.getProcessName() }
                    .getOrElse { app.packageName }
                DiagLogger.install(
                    sink = sink,
                    writer = writer,
                    processInfo = DiagLogger.ProcessInfo(
                        pid = Process.myPid(),
                        processName = processName
                    )
                )
                val previous = Thread.getDefaultUncaughtExceptionHandler()
                Thread.setDefaultUncaughtExceptionHandler(DiagExceptionHandler(previous))
                logProcessStart(app, processName)
                logHistoricalExits(app)
                app.registerActivityLifecycleCallbacks(activityCallbacks)
                app.registerComponentCallbacks(componentCallbacks)
                installed = true
            } catch (t: Throwable) {
                try {
                    android.util.Log.e("ByMusicDiag", "failed to install diagnostic logging", t)
                } catch (_: Throwable) {
                }
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
                        InputStreamReader(stream, StandardCharsets.UTF_8).readText()
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
            DiagLogger.flush()
        }

        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

        override fun onActivityDestroyed(activity: Activity) {
            DiagLogger.i("Lifecycle", "activity destroyed ${activity.javaClass.simpleName}")
            DiagLogger.flush()
        }
    }

    private val componentCallbacks = object : ComponentCallbacks2 {
        override fun onConfigurationChanged(newConfig: Configuration) {
            DiagLogger.i(
                "Process",
                "configuration changed orientation=${newConfig.orientation} densityDpi=${newConfig.densityDpi}"
            )
        }

        override fun onLowMemory() {
            DiagLogger.w("Process", "onLowMemory")
            DiagLogger.flush()
        }

        override fun onTrimMemory(level: Int) {
            DiagLogger.w("Process", "onTrimMemory level=$level label=${trimLabel(level)}")
            DiagLogger.flush()
        }
    }

    private fun trimLabel(level: Int): String = when (level) {
        ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> "COMPLETE"
        ComponentCallbacks2.TRIM_MEMORY_MODERATE -> "MODERATE"
        ComponentCallbacks2.TRIM_MEMORY_BACKGROUND -> "BACKGROUND"
        ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> "UI_HIDDEN"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL -> "RUNNING_CRITICAL"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> "RUNNING_LOW"
        ComponentCallbacks2.TRIM_MEMORY_RUNNING_MODERATE -> "RUNNING_MODERATE"
        else -> "LEVEL_$level"
    }
}
