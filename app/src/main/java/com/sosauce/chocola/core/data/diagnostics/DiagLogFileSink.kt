package com.sosauce.chocola.core.data.diagnostics

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Bounded rotating file sink. All methods are unsynchronized; callers must serialize access.
 */
class DiagLogFileSink(
    private val dir: File,
    private val fileName: String = DEFAULT_FILE_NAME,
    private val maxFileBytes: Long = DEFAULT_MAX_FILE_BYTES,
    private val maxFiles: Int = DEFAULT_MAX_FILES
) {
    private var writer: BufferedWriter? = null
    private var stream: FileOutputStream? = null
    private var currentSize: Long = 0

    init {
        require(maxFileBytes > 0)
        require(maxFiles >= 1)
        if (!dir.exists()) dir.mkdirs()
        openCurrent(append = true)
        pruneExtras()
    }

    fun appendLine(line: String) {
        val toWrite = if (line.endsWith("\n")) line else "$line\n"
        val bytes = toWrite.toByteArray(StandardCharsets.UTF_8).size.toLong()
        if (currentSize > 0 && currentSize + bytes > maxFileBytes) {
            rotate()
        }
        ensureOpen()
        writer!!.write(toWrite)
        currentSize += bytes
    }

    fun flush() {
        writer?.flush()
        stream?.fd?.sync()
    }

    fun close() {
        try {
            flush()
        } finally {
            writer?.close()
            writer = null
            stream = null
        }
    }

    fun export(out: OutputStream) {
        flush()
        val files = filesOldestFirst()
        val buffer = ByteArray(DEFAULT_COPY_BUFFER)
        for ((index, file) in files.withIndex()) {
            if (index > 0) out.write('\n'.code)
            file.inputStream().use { input ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    out.write(buffer, 0, read)
                }
            }
        }
    }

    fun filesOldestFirst(): List<File> {
        val result = ArrayList<File>(maxFiles)
        for (index in (maxFiles - 1) downTo 1) {
            val file = rotatedFile(index)
            if (file.exists() && file.length() > 0) result.add(file)
        }
        val current = currentFile()
        if (current.exists() && current.length() > 0) result.add(current)
        return result
    }

    fun totalBytes(): Long = existingManagedFiles().sumOf { it.length() }

    fun managedFileCount(): Int = existingManagedFiles().size

    private fun rotate() {
        close()
        val last = rotatedFile(maxFiles - 1)
        if (last.exists()) last.delete()
        for (index in (maxFiles - 2) downTo 1) {
            val src = rotatedFile(index)
            if (src.exists()) {
                val dest = rotatedFile(index + 1)
                if (dest.exists()) dest.delete()
                src.renameTo(dest)
            }
        }
        val current = currentFile()
        if (current.exists()) {
            val dest = rotatedFile(1)
            if (dest.exists()) dest.delete()
            current.renameTo(dest)
        }
        pruneExtras()
        openCurrent(append = false)
    }

    private fun pruneExtras() {
        val allowed = managedFileNames()
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name.startsWith(fileName) && file.name !in allowed) {
                file.delete()
            }
        }
        // If somehow more than maxFiles remain, drop the oldest rotated ones.
        while (managedFileCount() > maxFiles) {
            val oldest = filesOldestFirst().firstOrNull() ?: break
            oldest.delete()
        }
    }

    private fun managedFileNames(): Set<String> {
        val names = mutableSetOf(fileName)
        for (index in 1 until maxFiles) names.add("$fileName.$index")
        return names
    }

    private fun existingManagedFiles(): List<File> =
        managedFileNames().map { File(dir, it) }.filter { it.exists() }

    private fun currentFile(): File = File(dir, fileName)

    private fun rotatedFile(index: Int): File = File(dir, "$fileName.$index")

    private fun ensureOpen() {
        if (writer == null) openCurrent(append = true)
    }

    private fun openCurrent(append: Boolean) {
        if (!dir.exists()) dir.mkdirs()
        val file = currentFile()
        val fos = FileOutputStream(file, append)
        stream = fos
        writer = BufferedWriter(OutputStreamWriter(fos, StandardCharsets.UTF_8), 8 * 1024)
        currentSize = file.length()
    }

    companion object {
        const val DEFAULT_FILE_NAME = "diag.log"
        const val DEFAULT_MAX_FILE_BYTES = 256 * 1024L
        const val DEFAULT_MAX_FILES = 4
        private const val DEFAULT_COPY_BUFFER = 8 * 1024
    }
}
