package com.sosauce.chocola.feature.recognition.domain

import java.io.File

/**
 * Song recognition is provided only by plugins the user imports. ByMusic ships no
 * recognition service, no API key and no built-in plugin.
 *
 * Flow: the host records a short clip into a temporary file, hands it to the plugin
 * the user picked, and deletes the file right after the plugin answers (or the user
 * cancels). Whatever the plugin does with the clip is between the plugin and the
 * service it declares; the host never uploads anything by itself.
 */
interface SongRecognitionPlugin {
    val info: RecognitionPluginInfo

    /**
     * Identify [audio]. Implementations must not keep a reference to [RecognitionAudio.file]
     * after returning: the host deletes it as soon as this call ends.
     *
     * Expected failures should be returned as [RecognitionOutcome] values. Any exception
     * thrown here (other than cancellation) is reported to the user as a plugin error.
     */
    suspend fun recognize(audio: RecognitionAudio): RecognitionOutcome
}

/** What a plugin declares about itself. Only plugins that declare recognition get here. */
data class RecognitionPluginInfo(
    /** Stable id of the imported plugin (same id the plugin manager uses). */
    val id: String,
    /** Display name chosen by the plugin author. */
    val name: String,
    /** Optional platform tag the plugin claims, used later to pick a matching music source. */
    val platform: String? = null
)

/** The clip handed to a plugin. Always a temporary file owned by the host. */
data class RecognitionAudio(
    val file: File,
    val mimeType: String,
    val sampleRateHz: Int,
    val channelCount: Int,
    val durationMs: Long
)

/** A plugin's answer when it found a song. */
data class RecognizedSong(
    val title: String,
    val artist: String,
    val album: String? = null,
    val artworkUrl: String? = null,
    val durationMs: Long? = null,
    /** Platform tag the id below belongs to, as declared by the plugin. */
    val platform: String? = null,
    /** The song's id on [platform], so a matching music-source plugin can look it up later. */
    val platformSongId: String? = null,
    val sourcePluginId: String,
    val sourcePluginName: String
)

sealed interface RecognitionOutcome {
    data class Match(val song: RecognizedSong) : RecognitionOutcome
    data object NoMatch : RecognitionOutcome
    data class NetworkError(val detail: String? = null) : RecognitionOutcome
    data class PluginError(
        val kind: PluginErrorKind,
        val detail: String? = null
    ) : RecognitionOutcome
}

enum class PluginErrorKind {
    /** The plugin's service says the free quota / key limit is used up. */
    QUOTA_EXHAUSTED,

    /** The plugin script failed or returned something the host could not read. */
    SCRIPT_ERROR
}
