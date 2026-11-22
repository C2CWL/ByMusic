package com.sosauce.chocola.core.data.download

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.kyant.taglib.Picture
import com.kyant.taglib.TagLib
import com.sosauce.chocola.core.data.diagnostics.DiagLogger
import com.sosauce.chocola.core.data.plugin.PluginException
import com.sosauce.chocola.core.data.plugin.PluginMusicService
import com.sosauce.chocola.core.domain.online.OnlineMediaId
import com.sosauce.chocola.core.domain.online.QualityRequest
import com.sosauce.chocola.core.domain.online.QualityTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

internal sealed interface OnlineDownloadResult {
    data class Saved(val uri: Uri, val quality: QualityTier, val downgraded: Boolean) : OnlineDownloadResult
    data class Rejected(val failures: List<DownloadFailure>) : OnlineDownloadResult
    data object UnsupportedAndroid : OnlineDownloadResult
}

internal data class OnlineDownloadMetadata(
    val title: String,
    val artist: String?,
    val album: String?,
    val artUri: String?,
    val lyrics: String?
)

internal class OnlineDownloadPipeline(
    private val context: Context,
    private val music: PluginMusicService,
    private val transfer: DownloadTransfer
) {
    suspend fun download(
        id: OnlineMediaId,
        metadata: OnlineDownloadMetadata,
        expectedDurationMs: Long?,
        quality: QualityRequest
    ): OnlineDownloadResult = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return@withContext OnlineDownloadResult.UnsupportedAndroid
        }
        music.ensureUsable(id.pluginId)
        val temporary = File.createTempFile("online-download-", ".part", context.cacheDir)
        try {
            val result = validateDownload(quality, expectedDurationMs, discard = { temporary.outputStream().close() }) { key ->
                val source = try {
                    music.mediaSource(id, key, SOURCE_TIMEOUT_MS)
                } catch (_: PluginException) {
                    null
                }
                if (source == null) {
                    DiagLogger.w(TAG, "tier=$key stage=resolve no_source")
                    return@validateDownload DownloadAttempt.Failed(DownloadRejection.UNAVAILABLE)
                }
                if (!transfer.fetch(source, temporary)) {
                    DiagLogger.w(TAG, "tier=$key stage=transfer failed")
                    return@validateDownload DownloadAttempt.Failed(DownloadRejection.UNAVAILABLE)
                }
                val verified = decodeDownloadAudio(temporary)
                    ?: return@validateDownload DownloadAttempt.Failed(DownloadRejection.INVALID_AUDIO)
                DownloadAttempt.Decoded(verified.audio, verified.container)
            }
            when (result) {
                is DownloadValidation.Rejected -> OnlineDownloadResult.Rejected(result.failures)
                is DownloadValidation.Accepted -> OnlineDownloadResult.Saved(
                    publish(temporary, metadata, result.value), result.measuredQuality, result.downgraded
                )
            }
        } finally {
            temporary.delete()
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun publish(file: File, metadata: OnlineDownloadMetadata, container: AudioContainer): Uri {
        val job = currentCoroutineContext()
        job.ensureActive()
        DiagLogger.i(TAG, "stage=publish")
        val resolver = context.contentResolver
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val safeTitle = metadata.title.map { if (it.isISOControl() || it == '/' || it == '\\') '_' else it }
            .joinToString("").trim(' ', '.').take(80).ifBlank { "ByMusic" }
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "$safeTitle.${requireNotNull(container.extension)}")
            put(MediaStore.Audio.Media.MIME_TYPE, requireNotNull(container.mimeType))
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/ByMusic")
            put(MediaStore.Audio.Media.TITLE, metadata.title.take(200))
            metadata.artist?.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.Media.ARTIST, it.take(200)) }
            metadata.album?.takeIf { it.isNotBlank() }?.let { put(MediaStore.Audio.Media.ALBUM, it.take(200)) }
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("MediaStore insert failed")
        var published = false
        try {
            val output = resolver.openOutputStream(uri) ?: throw IOException("MediaStore output missing")
            output.use {
                file.inputStream().use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        job.ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        output.write(buffer, 0, count)
                    }
                }
            }
            job.ensureActive()
            writeTags(resolver, uri, metadata)
            job.ensureActive()
            val ready = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            if (resolver.update(uri, ready, null, null) != 1) throw IOException("MediaStore publish failed")
            published = true
            return uri
        } finally {
            if (!published) runCatching { resolver.delete(uri, null, null) }
        }
    }

    private fun writeTags(resolver: ContentResolver, uri: Uri, metadata: OnlineDownloadMetadata) {
        val descriptor = resolver.openFileDescriptor(uri, "rw")
        if (descriptor == null) {
            Log.w(TAG, "tag write skipped: no rw descriptor")
            return
        }
        descriptor.use {
            val properties = HashMap<String, Array<String>>()
            properties["TITLE"] = arrayOf(metadata.title.take(200))
            metadata.artist?.takeIf { it.isNotBlank() }?.let { properties["ARTIST"] = arrayOf(it.take(200)) }
            metadata.album?.takeIf { it.isNotBlank() }?.let { properties["ALBUM"] = arrayOf(it.take(200)) }
            metadata.lyrics?.takeIf { it.isNotBlank() }?.let { properties["LYRICS"] = arrayOf(it.take(8_000)) }
            runCatching { TagLib.savePropertyMap(it.dup().detachFd(), properties) }
                .onFailure { e -> Log.w(TAG, "save tags failed: ${e.message}") }
            readArtwork(resolver, metadata.artUri)?.let { (bytes, mimeType) ->
                runCatching {
                    TagLib.savePictures(it.dup().detachFd(), arrayOf(Picture(bytes, "Front cover", "Front Cover", mimeType)))
                }.onFailure { e -> Log.w(TAG, "save artwork failed: ${e.message}") }
            }
        }
    }

    private fun readArtwork(resolver: ContentResolver, artUri: String?): Pair<ByteArray, String>? {
        if (artUri.isNullOrBlank()) return null
        return try {
            when {
                artUri.startsWith("http://", ignoreCase = true) || artUri.startsWith("https://", ignoreCase = true) -> {
                    val connection = URL(artUri).openConnection() as HttpURLConnection
                    connection.connectTimeout = SOURCE_TIMEOUT_MS.toInt()
                    connection.readTimeout = SOURCE_TIMEOUT_MS.toInt()
                    connection.inputStream.use { stream ->
                        val mime = connection.contentType?.substringBefore(';')?.trim().orEmpty()
                            .ifBlank { "image/jpeg" }
                        stream.readLimited(MAX_ART_BYTES) to mime
                    }
                }
                else -> {
                    val uri = Uri.parse(artUri)
                    val mime = resolver.getType(uri)?.substringBefore(';')?.trim().orEmpty()
                        .ifBlank { "image/jpeg" }
                    resolver.openInputStream(uri)?.use { stream -> stream.readLimited(MAX_ART_BYTES) to mime }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "read artwork failed: ${e.message}")
            null
        }
    }

    private fun InputStream.readLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (total < limit) {
            val count = read(buffer, 0, minOf(buffer.size, limit - total))
            if (count <= 0) break
            output.write(buffer, 0, count)
            total += count
        }
        return output.toByteArray()
    }

    private companion object {
        const val TAG = "OnlineDownload"
        const val SOURCE_TIMEOUT_MS = 8_000L
        const val MAX_ART_BYTES = 2_000_000
    }
}
