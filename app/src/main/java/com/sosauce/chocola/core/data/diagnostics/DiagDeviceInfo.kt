package com.sosauce.chocola.core.data.diagnostics

import android.content.Context
import android.os.Build
import android.os.Process
import java.time.ZoneId

object DiagDeviceInfo {
    fun exportMeta(context: Context, nowMillis: Long = System.currentTimeMillis()): DiagExportMeta {
        val process = DiagLogger.currentProcessInfo()
        val pkg = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionCode = pkg.longVersionCode
        return DiagExportMeta(
            exportedAtMillis = nowMillis,
            zone = ZoneId.systemDefault(),
            versionName = pkg.versionName ?: "unknown",
            versionCode = versionCode,
            packageName = context.packageName,
            processName = process.processName,
            pid = if (process.pid > 0) process.pid else Process.myPid(),
            manufacturer = Build.MANUFACTURER.orEmpty(),
            brand = Build.BRAND.orEmpty(),
            model = Build.MODEL.orEmpty(),
            device = Build.DEVICE.orEmpty(),
            androidRelease = Build.VERSION.RELEASE.orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            display = Build.DISPLAY.orEmpty(),
            incremental = Build.VERSION.INCREMENTAL.orEmpty(),
            securityPatch = Build.VERSION.SECURITY_PATCH.orEmpty()
        )
    }
}
