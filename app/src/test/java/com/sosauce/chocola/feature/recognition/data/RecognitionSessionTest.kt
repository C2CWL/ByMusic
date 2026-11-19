package com.sosauce.chocola.feature.recognition.data

import com.sosauce.chocola.feature.recognition.domain.FailureReason
import com.sosauce.chocola.feature.recognition.domain.PluginErrorKind
import com.sosauce.chocola.feature.recognition.domain.RecognitionAudio
import com.sosauce.chocola.feature.recognition.domain.RecognitionEvent
import com.sosauce.chocola.feature.recognition.domain.RecognitionOutcome
import com.sosauce.chocola.feature.recognition.domain.RecognitionPluginInfo
import com.sosauce.chocola.feature.recognition.domain.RecognitionPluginRegistry
import com.sosauce.chocola.feature.recognition.domain.RecognitionState
import com.sosauce.chocola.feature.recognition.domain.RecognizedSong
import com.sosauce.chocola.feature.recognition.domain.SongRecognitionPlugin
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class RecognitionSessionTest {

    private lateinit var root: File
    private lateinit var files: RecognitionAudioFiles
    private lateinit var history: RecognitionHistoryStore

    @Before
    fun setUp() {
        root = Files.createTempDirectory("recognition-test").toFile()
        files = RecognitionAudioFiles(File(root, "cache"))
        history = RecognitionHistoryStore(File(root, "files/history.json"))
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    /** Writes a few bytes and reports progress, like the real recorder but instantly. */
    private class FakeRecorder(
        private val fail: Boolean = false,
        private val block: Boolean = false
    ) : AudioRecorder {
        val started = CompletableDeferred<File>()
        override suspend fun record(
            target: File,
            durationMs: Long,
            onProgress: (Long, Float) -> Unit
        ): RecognitionAudio {
            target.writeBytes(ByteArray(64) { 1 })
            started.complete(target)
            if (fail) throw RecordingException("mic busy")
            if (block) awaitCancellation()
            onProgress(durationMs / 2, 0.5f)
            onProgress(durationMs, 0.7f)
            return RecognitionAudio(target, WavFormat.MIME_TYPE, 44_100, 1, durationMs)
        }
    }

    private class FakePlugin(
        override val info: RecognitionPluginInfo,
        private val answer: suspend (RecognitionAudio) -> RecognitionOutcome
    ) : SongRecognitionPlugin {
        var sawFileExisting = false
        override suspend fun recognize(audio: RecognitionAudio): RecognitionOutcome {
            sawFileExisting = audio.file.isFile && audio.file.length() > 0
            return answer(audio)
        }
    }

    private class Registry(vararg plugins: SongRecognitionPlugin) : RecognitionPluginRegistry {
        override val plugins: StateFlow<List<SongRecognitionPlugin>> = MutableStateFlow(plugins.toList())
    }

    private val info = RecognitionPluginInfo("p1", "Sample plugin")

    private fun song() = RecognizedSong(
        title = "Sample title",
        artist = "Sample artist",
        album = "Sample album",
        platform = "sample",
        platformSongId = "42",
        sourcePluginId = info.id,
        sourcePluginName = info.name
    )

    private fun session(recorder: AudioRecorder, plugin: SongRecognitionPlugin?, timeoutMs: Long = 5_000) =
        RecognitionSession(
            recorder = recorder,
            audioFiles = files,
            registry = if (plugin == null) Registry() else Registry(plugin),
            history = history,
            identifyTimeoutMs = timeoutMs,
            clock = { 1_000L }
        ).also {
            it.dispatch(
                RecognitionEvent.StartRequested(
                    plugins = listOf(info),
                    preferredPluginId = null,
                    permissionGranted = true
                )
            )
        }

    private fun assertNoClipsLeft(session: RecognitionSession) {
        val clip = session.lastClip
        assertNotNull(clip)
        assertFalse("clip must be deleted", clip!!.exists())
        assertTrue(files.directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun matchDeletesClipAndLogsHistory() = runBlocking {
        val plugin = FakePlugin(info) { RecognitionOutcome.Match(song()) }
        val s = session(FakeRecorder(), plugin)
        s.run()
        assertTrue("plugin must get the clip while it runs", plugin.sawFileExisting)
        assertEquals(RecognitionState.Success(song()), s.state.value)
        assertNoClipsLeft(s)
        val entry = history.entries.value.single()
        assertEquals(RecognitionHistoryStatus.MATCH, entry.status)
        assertEquals("42", entry.platformSongId)
    }

    @Test
    fun noMatchDeletesClip() = runBlocking {
        val s = session(FakeRecorder(), FakePlugin(info) { RecognitionOutcome.NoMatch })
        s.run()
        assertEquals(RecognitionState.Failure("p1", FailureReason.NoMatch), s.state.value)
        assertNoClipsLeft(s)
        assertEquals(RecognitionHistoryStatus.NO_MATCH, history.entries.value.single().status)
    }

    @Test
    fun pluginExceptionBecomesPluginErrorAndDeletesClip() = runBlocking {
        val s = session(FakeRecorder(), FakePlugin(info) { error("script broke") })
        s.run()
        val state = s.state.value as RecognitionState.Failure
        assertEquals(PluginErrorKind.SCRIPT_ERROR, (state.reason as FailureReason.Plugin).kind)
        assertNoClipsLeft(s)
    }

    @Test
    fun timeoutBecomesNetworkErrorAndDeletesClip() = runBlocking {
        val s = session(FakeRecorder(), FakePlugin(info) { awaitCancellation() }, timeoutMs = 50)
        s.run()
        assertEquals(RecognitionState.Failure("p1", FailureReason.Network), s.state.value)
        assertNoClipsLeft(s)
    }

    @Test
    fun recordingFailureDeletesClipAndIsNotLogged() = runBlocking {
        val s = session(FakeRecorder(fail = true), FakePlugin(info) { RecognitionOutcome.NoMatch })
        s.run()
        assertEquals(RecognitionState.Failure("p1", FailureReason.RecordingFailed), s.state.value)
        assertNoClipsLeft(s)
        assertTrue(history.entries.value.isEmpty())
    }

    @Test
    fun cancelWhileRecordingDeletesClip() = runBlocking {
        val recorder = FakeRecorder(block = true)
        val s = session(recorder, FakePlugin(info) { RecognitionOutcome.NoMatch })
        val job = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) { s.run() }
        val clip = recorder.started.await()
        assertTrue(clip.exists())
        job.cancel()
        s.dispatch(RecognitionEvent.Cancel)
        job.join()
        assertEquals(RecognitionState.Idle, s.state.value)
        assertNoClipsLeft(s)
    }

    @Test
    fun cancelWhileIdentifyingDeletesClipAndLogsNothing() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val plugin = FakePlugin(info) {
            entered.complete(Unit)
            awaitCancellation()
        }
        val s = session(FakeRecorder(), plugin)
        val job = launch(Dispatchers.Default) { s.run() }
        entered.await()
        assertTrue(s.state.value is RecognitionState.Identifying)
        job.cancel()
        s.dispatch(RecognitionEvent.Cancel)
        job.join()
        assertEquals(RecognitionState.Idle, s.state.value)
        assertNoClipsLeft(s)
        assertTrue(history.entries.value.isEmpty())
    }

    @Test
    fun pluginRemovedMidwayIsReportedAsPluginError() = runBlocking {
        val s = session(FakeRecorder(), plugin = null)
        s.run()
        val state = s.state.value as RecognitionState.Failure
        assertTrue(state.reason is FailureReason.Plugin)
        assertNoClipsLeft(s)
    }

    @Test
    fun runDoesNothingUnlessRecording() = runBlocking {
        val s = RecognitionSession(FakeRecorder(), files, Registry(), history)
        s.run()
        assertEquals(RecognitionState.Idle, s.state.value)
        assertEquals(null, s.lastClip)
    }
}
