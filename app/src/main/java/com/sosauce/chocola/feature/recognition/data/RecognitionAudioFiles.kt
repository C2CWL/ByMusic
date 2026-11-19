package com.sosauce.chocola.feature.recognition.data

import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Owns the temporary clips. Everything lives in one private cache sub-folder so a crash
 * mid-recognition leaves nothing behind for long: [purgeAll] runs whenever the feature starts.
 */
class RecognitionAudioFiles(cacheRoot: File) {

    val directory: File = File(cacheRoot, DIRECTORY_NAME)
    private val purged = AtomicBoolean(false)

    fun create(): File {
        if (!directory.isDirectory) directory.mkdirs()
        return File.createTempFile(PREFIX, SUFFIX, directory)
    }

    /** Returns true when the file no longer exists afterwards. */
    fun delete(file: File?): Boolean {
        if (file == null) return true
        if (file.exists()) file.delete()
        return !file.exists()
    }

    /**
     * Purge leftovers once per process (this object is an app-wide single), so a second
     * screen opening later can never delete a clip that is still being recorded.
     */
    fun purgeLeftoversOnce() {
        if (purged.compareAndSet(false, true)) purgeAll()
    }

    /** Remove any clip left over from an earlier run. */
    fun purgeAll() {
        directory.listFiles()?.forEach { it.delete() }
    }

    companion object {
        const val DIRECTORY_NAME = "recognition"
        private const val PREFIX = "clip_"
        private const val SUFFIX = ".wav"
    }
}
