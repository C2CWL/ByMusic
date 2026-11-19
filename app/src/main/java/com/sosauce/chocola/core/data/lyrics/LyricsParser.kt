package com.sosauce.chocola.core.data.lyrics

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import com.kyant.taglib.TagLib
import com.sosauce.chocola.core.data.diagnostics.DiagLogger
import com.sosauce.chocola.core.domain.model.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException
import kotlin.coroutines.cancellation.CancellationException

class LyricsParser(private val context: Context) {

    suspend fun parseLyrics(
        path: String
    ): List<Lyrics> = withContext(Dispatchers.IO) {
        try {
            ensureActive()
            val sidecar = LyricsSidecar.find(path) { File(it).exists() }

            when (sidecar?.kind) {
                LyricsSidecar.Kind.LRC -> {
                    val raw = LyricsTextParser.decodeLyricBytes(File(sidecar.path).readBytes())
                    LyricsTextParser.parseLyricText(raw, LyricSource.SIDECAR)
                }
                LyricsSidecar.Kind.TTML -> {
                    val raw = LyricsTextParser.decodeLyricBytes(File(sidecar.path).readBytes())
                    LyricsTextParser.parseTtmlText(raw)
                }
                null -> {
                    val embeddedLyrics = loadEmbeddedLyrics(path) ?: return@withContext emptyList()
                    LyricsTextParser.parseLyricText(embeddedLyrics, LyricSource.EMBEDDED)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Do not include the song path. The sanitizer also redacts paths in the stack.
            DiagLogger.w(
                "Lyrics",
                "lyrics load failed error=${e::class.java.simpleName}: ${e.message ?: ""}",
                e
            )
            emptyList()
        }
    }

    /**
     * Reads a lyrics file the user picked (LRC or TTML) through the content resolver,
     * so picker URIs that are not plain file paths also work.
     */
    suspend fun parseLyricsUri(uri: Uri): List<Lyrics> = withContext(Dispatchers.IO) {
        try {
            ensureActive()
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext emptyList()
            val raw = LyricsTextParser.decodeLyricBytes(bytes)
            if (LyricsTextParser.looksLikeTtml(raw)) {
                LyricsTextParser.parseTtmlText(raw)
            } else {
                LyricsTextParser.parseLyricText(raw, LyricSource.SIDECAR)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagLogger.w(
                "Lyrics",
                "lyrics import failed error=${e::class.java.simpleName}: ${e.message ?: ""}",
                e
            )
            emptyList()
        }
    }

    private fun loadEmbeddedLyrics(path: String): String? {
        val fd = getFileDescriptorFromPath(context, path) ?: return null
        return fd.dup().detachFd()
            .let { TagLib.getMetadata(it)?.propertyMap?.get("LYRICS")?.getOrNull(0) ?: "" }
            .takeIf { it.isNotEmpty() }
    }

    @SuppressLint("Range")
    private fun getFileDescriptorFromPath(
        context: Context,
        filePath: String,
        mode: String = "r"
    ): ParcelFileDescriptor? {
        val resolver = context.contentResolver
        val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(MediaStore.Files.FileColumns._ID)
        val selection = "${MediaStore.Files.FileColumns.DATA}=?"
        val selectionArgs = arrayOf(filePath)

        resolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val fileId = cursor.getInt(cursor.getColumnIndex(MediaStore.Files.FileColumns._ID))
                if (fileId == -1) {
                    return null
                } else {
                    val fileUri = Uri.withAppendedPath(uri, fileId.toString())
                    try {
                        return resolver.openFileDescriptor(fileUri, mode)
                    } catch (e: FileNotFoundException) {
                        Log.e("MediaStoreReceiver", "File not found: ${e.message}")
                    }
                }
            }
        }

        return null
    }
}
