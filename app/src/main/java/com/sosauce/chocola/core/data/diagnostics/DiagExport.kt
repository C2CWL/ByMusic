package com.sosauce.chocola.core.data.diagnostics

import java.io.File
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class DiagExportMeta(
    val exportedAtMillis: Long,
    val zone: ZoneId,
    val versionName: String,
    val versionCode: Long,
    val packageName: String,
    val processName: String,
    val pid: Int,
    val manufacturer: String,
    val brand: String,
    val model: String,
    val device: String,
    val androidRelease: String,
    val sdkInt: Int,
    val display: String,
    val incremental: String,
    val securityPatch: String,
    val buildMark: String = DiagLogger.BUILD_MARK
)

object DiagExport {
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
    private val FILE_STAMP: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

    const val INSTRUCTIONS = """ByMusic 诊断日志
================
这是一份本地诊断日志，用来排查偶发的「已停止运行」提示。
这份文件只保存在手机里，导出时通过系统分享发送，不会自动上传。
日志已尽量去掉歌曲名称、完整文件路径、媒体地址、账号和密钥。
请在出现弹窗之后（即使当时不在 ByMusic 里），打开「设置 → 诊断日志 → 导出诊断日志」，把文件发给需要查看的人。
这是诊断信息，不是问题已经修复的证明。
"""

    fun header(meta: DiagExportMeta): String {
        val exported = TIME.format(Instant.ofEpochMilli(meta.exportedAtMillis).atZone(meta.zone))
        return buildString {
            append(INSTRUCTIONS)
            appendLine()
            appendLine("导出时间: $exported")
            appendLine("应用版本: ${meta.versionName} (${meta.versionCode})")
            appendLine("诊断标记: ${meta.buildMark}")
            appendLine("包名: ${meta.packageName}")
            appendLine("进程: ${meta.processName} pid=${meta.pid}")
            appendLine("设备: ${meta.manufacturer} ${meta.model} (brand=${meta.brand}, device=${meta.device})")
            appendLine("系统: Android ${meta.androidRelease} (API ${meta.sdkInt})")
            appendLine("系统显示版本: ${meta.display}")
            appendLine("内部版本: ${meta.incremental}")
            appendLine("安全补丁: ${meta.securityPatch}")
            appendLine()
            appendLine("----- 日志 -----")
        }
    }

    fun exportFileName(exportedAtMillis: Long, zone: ZoneId): String {
        val stamp = FILE_STAMP.format(Instant.ofEpochMilli(exportedAtMillis).atZone(zone))
        return "ByMusic-diag-$stamp.txt"
    }

    fun writeExport(
        outputDir: File,
        meta: DiagExportMeta,
        sink: DiagLogFileSink?,
        exportedAtMillis: Long = meta.exportedAtMillis,
        logDir: File? = null
    ): File {
        if (!outputDir.exists()) outputDir.mkdirs()
        outputDir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith("ByMusic-diag-") && file.name.endsWith(".txt")) {
                file.delete()
            }
        }
        val file = File(outputDir, exportFileName(exportedAtMillis, meta.zone))
        file.outputStream().use { out ->
            writeExport(out, meta, sink, logDir)
        }
        return file
    }

    fun writeExport(
        out: OutputStream,
        meta: DiagExportMeta,
        sink: DiagLogFileSink?,
        logDir: File? = null
    ) {
        val header = header(meta).toByteArray(Charsets.UTF_8)
        out.write(header)
        when {
            sink != null -> sink.export(out)
            logDir != null && DiagLogFileSink.hasExistingLogs(logDir) ->
                DiagLogFileSink.exportFromDir(logDir, out)
            else -> out.write("（还没有日志记录）\n".toByteArray(Charsets.UTF_8))
        }
        out.flush()
    }
}
