package com.sosauce.chocola.feature.recognition.data

import com.sosauce.chocola.feature.recognition.domain.FailureReason
import com.sosauce.chocola.feature.recognition.domain.PluginErrorKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RecognitionHistoryStoreTest {

    private val root: File = Files.createTempDirectory("recognition-history").toFile()
    private val file = File(root, "history.json")

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    private fun entry(ts: Long) = RecognitionHistoryEntry.failure(
        FailureReason.NoMatch, "p", "Sample", ts
    )!!

    @Test
    fun survivesReloadNewestFirst() {
        RecognitionHistoryStore(file).apply {
            add(entry(1))
            add(entry(2))
        }
        val reloaded = RecognitionHistoryStore(file).load()
        assertEquals(listOf(2L, 1L), reloaded.map { it.timestampMs })
    }

    @Test
    fun capsEntries() {
        val store = RecognitionHistoryStore(file, maxEntries = 3)
        (1L..5L).forEach { store.add(entry(it)) }
        assertEquals(listOf(5L, 4L, 3L), store.entries.value.map { it.timestampMs })
    }

    @Test
    fun idsStayUniqueForSameTimestamp() {
        val store = RecognitionHistoryStore(file)
        store.add(entry(7))
        store.add(entry(7))
        assertEquals(2, store.entries.value.map { it.id }.toSet().size)
    }

    @Test
    fun clearRemovesFile() {
        val store = RecognitionHistoryStore(file)
        store.add(entry(1))
        store.clear()
        assertTrue(store.entries.value.isEmpty())
        assertFalse(file.exists())
    }

    @Test
    fun corruptFileReadsAsEmpty() {
        file.writeText("{not json")
        assertTrue(RecognitionHistoryStore(file).load().isEmpty())
    }

    @Test
    fun failureMapping() {
        assertEquals(
            RecognitionHistoryStatus.NETWORK,
            RecognitionHistoryEntry.failure(FailureReason.Network, "p", "n", 1)!!.status
        )
        assertEquals(
            RecognitionHistoryStatus.PLUGIN_ERROR,
            RecognitionHistoryEntry.failure(
                FailureReason.Plugin(PluginErrorKind.SCRIPT_ERROR, null), "p", "n", 1
            )!!.status
        )
        assertNull(RecognitionHistoryEntry.failure(FailureReason.RecordingFailed, "p", "n", 1))
    }
}
