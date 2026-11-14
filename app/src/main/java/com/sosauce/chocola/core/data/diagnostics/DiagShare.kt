package com.sosauce.chocola.core.data.diagnostics

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.sosauce.chocola.R
import java.io.File

object DiagShare {
    const val AUTHORITY_SUFFIX = ".diag.fileprovider"
    const val EXPORT_CACHE_SUBDIR = "diag"
    const val SHARE_TYPE = "text/plain"

    fun authority(packageName: String): String = packageName + AUTHORITY_SUFFIX

    fun exportCacheDir(cacheDir: File): File = File(cacheDir, EXPORT_CACHE_SUBDIR)

    fun share(context: Context) {
        try {
            DiagBootstrap.ensureReady(context)
            DiagLogger.flushBestEffort(1_000)
            val file = DiagExport.writeExport(
                outputDir = exportCacheDir(context.cacheDir),
                meta = DiagDeviceInfo.exportMeta(context),
                sink = DiagLogger.sinkOrNull(),
                logDir = DiagBootstrap.logDirectory(context)
            )
            val uri = FileProvider.getUriForFile(context, authority(context.packageName), file)
            context.startActivity(createShareIntent(uri, context))
        } catch (t: Throwable) {
            DiagLogger.e("DiagShare", "failed to export diagnostic log", t)
            DiagLogger.flush()
            Toast.makeText(context, context.getString(R.string.diag_export_failed), Toast.LENGTH_LONG).show()
        }
    }

    fun createShareIntent(uri: android.net.Uri, context: Context): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = SHARE_TYPE
            putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.diag_export_subject))
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newRawUri("ByMusic-diag", uri)
        }
        return Intent.createChooser(send, context.getString(R.string.export_diag_logs)).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}
