package com.sosauce.chocola.feature.recognition.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RecognitionAudioFilesTest {

    private val root: File = Files.createTempDirectory("recognition-files").toFile()
    private val files = RecognitionAudioFiles(root)

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun clipsLiveInPrivateSubfolder() {
        val clip = files.create()
        assertEquals(File(root, RecognitionAudioFiles.DIRECTORY_NAME), clip.parentFile)
        assertTrue(clip.name.endsWith(".wav"))
    }

    @Test
    fun deleteRemovesTheClip() {
        val clip = files.create().apply { writeBytes(ByteArray(16)) }
        assertTrue(files.delete(clip))
        assertFalse(clip.exists())
        assertTrue(files.delete(clip)) // idempotent
        assertTrue(files.delete(null))
    }

    @Test
    fun purgeRemovesLeftoversFromEarlierRuns() {
        repeat(3) { files.create().writeBytes(ByteArray(8)) }
        files.purgeAll()
        assertTrue(files.directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun purgeLeavesOtherCacheFilesAlone() {
        val other = File(root, "other.bin").apply { writeBytes(ByteArray(4)) }
        files.create()
        files.purgeAll()
        assertTrue(other.exists())
    }

    @Test
    fun purgeOnceDoesNotTouchClipsCreatedLater() {
        files.create().writeBytes(ByteArray(8))
        files.purgeLeftoversOnce()
        assertTrue(files.directory.listFiles().orEmpty().isEmpty())
        val active = files.create().apply { writeBytes(ByteArray(8)) }
        files.purgeLeftoversOnce()
        assertTrue("an in-progress clip must survive a second screen opening", active.exists())
    }
}
