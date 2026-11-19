package com.sosauce.chocola.core.data.download

import kotlinx.serialization.Serializable

enum class DownloadStatus {
    /** Waiting for WorkManager (network constraint, other downloads). */
    QUEUED,

    /** Asking the plugin for a link (getMediaSource). */
    RESOLVING,
    DOWNLOADING,

    /** Downloaded to the private temp folder, now decoding to check it. */
    VERIFYING,

    /** Checked; writing tags and copying into Music/ByMusic. */
    SAVING,
    COMPLETED,
    FAILED,
    CANCELLED;

    val isActive: Boolean get() = this == QUEUED || this == RESOLVING || this == DOWNLOADING || this == VERIFYING || this == SAVING
}

/** One download, as shown in 下载管理 (mockup 09). Stored in the app's private files. */
@Serializable
data class DownloadTask(
    val id: String,
    /** `online:<plugin>:<trackId>` */
    val mediaId: String,
    val pluginId: String,
    val title: String,
    val artist: String = "",
    val album: String = "",
    val artworkUrl: String? = null,
    /** From the plugin's search result; 0 when unknown. */
    val durationMs: Long = 0,
    /** MusicFree key the user picked, or null for "自动（从最高档开始）". */
    val requestedKey: String? = null,
    val autoDowngrade: Boolean = true,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val createdAtMs: Long = 0,
    val updatedAtMs: Long = 0,
    val bytesDone: Long = 0,
    /** -1 when the server did not say. */
    val bytesTotal: Long = -1,
    val bytesPerSecond: Long = 0,
    /** Which MusicFree key the kept file counts as. */
    val servedKey: String? = null,
    /** True when the kept file is lower than what was asked for. */
    val downgraded: Boolean = false,
    /** Measured, e.g. "FLAC 16bit/44.1kHz" or "MP3 320k". */
    val formatLabel: String? = null,
    val sizeBytes: Long = 0,
    /** Why it failed, written for people. */
    val error: String? = null,
    /** Lower MusicFree key offered as "改下 …" after a failure. */
    val suggestedKey: String? = null,
    /** content:// (or file:// on Android 9) of the saved file. */
    val savedUri: String? = null,
    val displayName: String? = null,
    /** One line per tier tried, for "详情". */
    val attempts: List<String> = emptyList()
)
