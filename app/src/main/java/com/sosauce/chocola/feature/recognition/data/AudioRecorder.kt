package com.sosauce.chocola.feature.recognition.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import com.sosauce.chocola.feature.recognition.domain.RecognitionAudio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile

/** Records a short clip into [target]. Cancelling the calling coroutine stops the microphone. */
interface AudioRecorder {
    suspend fun record(
        target: File,
        durationMs: Long,
        onProgress: (elapsedMs: Long, level: Float) -> Unit
    ): RecognitionAudio
}

class RecordingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Foreground-only microphone capture with [AudioRecord]: mono PCM16 at 44.1 kHz (the one
 * rate every Android device must support), written as a WAV file. Nothing is kept in memory
 * beyond one read buffer, and the microphone is released in every exit path.
 */
class AndroidAudioRecorder(private val context: Context) : AudioRecorder {

    @SuppressLint("MissingPermission") // checked right below
    override suspend fun record(
        target: File,
        durationMs: Long,
        onProgress: (elapsedMs: Long, level: Float) -> Unit
    ): RecognitionAudio = withContext(Dispatchers.IO) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            throw RecordingException("RECORD_AUDIO not granted")
        }

        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_MASK, ENCODING)
        if (minBuffer <= 0) throw RecordingException("unsupported audio config: $minBuffer")
        val bufferBytes = maxOf(minBuffer, SAMPLE_RATE / 10 * 2) // at least ~100 ms

        val record = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL_MASK, ENCODING, bufferBytes)
        } catch (e: Exception) {
            throw RecordingException("cannot open microphone", e)
        }

        var pcmBytes = 0L
        try {
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                throw RecordingException("microphone not initialized")
            }
            RandomAccessFile(target, "rw").use { out ->
                out.setLength(0)
                out.write(ByteArray(WavFormat.HEADER_SIZE)) // patched after recording
                val buffer = ShortArray(bufferBytes / 2)
                val bytes = ByteArray(buffer.size * 2)
                val targetBytes = durationMs * SAMPLE_RATE / 1000 * 2

                record.startRecording()
                if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                    throw RecordingException("microphone busy")
                }
                var lastReport = -PROGRESS_INTERVAL_MS
                while (pcmBytes < targetBytes) {
                    ensureActive()
                    val read = record.read(buffer, 0, buffer.size)
                    if (read < 0) throw RecordingException("read failed: $read")
                    if (read == 0) continue
                    for (i in 0 until read) {
                        val v = buffer[i].toInt()
                        bytes[i * 2] = (v and 0xFF).toByte()
                        bytes[i * 2 + 1] = (v shr 8 and 0xFF).toByte()
                    }
                    out.write(bytes, 0, read * 2)
                    pcmBytes += read * 2
                    val elapsed = WavFormat.durationMs(SAMPLE_RATE, 1, pcmBytes)
                    if (elapsed - lastReport >= PROGRESS_INTERVAL_MS || pcmBytes >= targetBytes) {
                        lastReport = elapsed
                        onProgress(elapsed.coerceAtMost(durationMs), WavFormat.level(buffer, read))
                    }
                }
                out.seek(0)
                out.write(WavFormat.header(SAMPLE_RATE, 1, pcmBytes))
            }
        } finally {
            runCatching { if (record.recordingState == AudioRecord.RECORDSTATE_RECORDING) record.stop() }
            record.release()
        }

        RecognitionAudio(
            file = target,
            mimeType = WavFormat.MIME_TYPE,
            sampleRateHz = SAMPLE_RATE,
            channelCount = 1,
            durationMs = WavFormat.durationMs(SAMPLE_RATE, 1, pcmBytes)
        )
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val CHANNEL_MASK = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val PROGRESS_INTERVAL_MS = 80L
    }
}
