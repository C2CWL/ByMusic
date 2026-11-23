package com.sosauce.chocola.core.data.download

import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sosauce.chocola.core.data.diagnostics.DiagLogger
import com.sosauce.chocola.core.domain.online.OnlineMediaId
import com.sosauce.chocola.core.domain.online.QualityRequest
import com.sosauce.chocola.core.domain.online.QualityTier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import java.security.MessageDigest

/** WorkManager restores jobs after process death. Media URLs are resolved at execution time. */
class OnlineDownloadWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters), KoinComponent {
    private val pipeline: OnlineDownloadPipeline by inject()

    override suspend fun doWork(): Result {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return Result.failure(problem("unsupported_android"))
        val id = OnlineMediaId.parse(inputData.getString(KEY_MEDIA_ID)) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val metadata = OnlineDownloadMetadata(
            title = title,
            artist = inputData.getString(KEY_ARTIST),
            album = inputData.getString(KEY_ALBUM),
            artUri = inputData.getString(KEY_ART_URI),
            lyrics = inputData.getString(KEY_LYRICS)
        )
        val duration = inputData.getLong(KEY_DURATION, 0L).takeIf { it > 0 }
        val quality = decodeQuality(inputData) ?: return Result.failure()
        return try {
            when (val outcome = withTimeoutOrNull(TIMEOUT_MILLIS) { pipeline.download(id, metadata, duration, quality) }) {
                is OnlineDownloadResult.Saved -> Result.success(workDataOf(
                    KEY_SAVED_URI to outcome.uri.toString(),
                    KEY_MEASURED_QUALITY to outcome.quality.name,
                    KEY_DOWNGRADED to outcome.downgraded
                ))
                is OnlineDownloadResult.Rejected -> {
                    val failures = outcome.failures.joinToString { "${it.key}:${it.reason}" }
                    DiagLogger.w(DIAG_TAG, "no_verified_audio attempts=$failures")
                    Result.failure(problem("no_verified_audio"))
                }
                OnlineDownloadResult.UnsupportedAndroid -> Result.failure(problem("unsupported_android"))
                null -> {
                    DiagLogger.w(DIAG_TAG, "timed_out")
                    Result.failure(problem("timed_out"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DiagLogger.w(DIAG_TAG, "download_failed type=${e.javaClass.simpleName}")
            Result.failure(problem("download_failed"))
        }
    }

    private fun problem(code: String) = workDataOf(KEY_ERROR to code)

    private fun decodeQuality(data: Data): QualityRequest? {
        val names = data.getStringArray(KEY_QUALITY) ?: return null
        val tiers = names.map { name -> QualityTier.entries.firstOrNull { it.name == name } ?: return null }
        if (tiers.isEmpty() || tiers.toSet().size != tiers.size) return null
        val modeName = data.getString(KEY_MODE)
        val mode = QualityRequest.Mode.entries.firstOrNull { it.name == modeName } ?: return null
        return QualityRequest(tiers, mode)
    }

    companion object {
        private const val DIAG_TAG = "OnlineDownload"
        internal const val TAG = "online-audio-download"
        internal const val KEY_MEDIA_ID = "media_id"
        internal const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_ALBUM = "album"
        private const val KEY_ART_URI = "art_uri"
        private const val KEY_LYRICS = "lyrics"
        internal const val KEY_SAVED_URI = "saved_uri"
        internal const val KEY_MEASURED_QUALITY = "measured_quality"
        internal const val KEY_DOWNGRADED = "downgraded"
        internal const val KEY_ERROR = "error"
        private const val KEY_DURATION = "duration_ms"
        private const val KEY_QUALITY = "quality_tiers"
        private const val KEY_MODE = "quality_mode"
        // Ordinary WorkManager workers have a ten-minute execution window.
        private const val TIMEOUT_MILLIS = 8L * 60 * 1_000

        internal fun enqueue(
            context: Context,
            id: OnlineMediaId,
            title: String,
            durationMs: Long?,
            quality: QualityRequest,
            artist: String? = null,
            album: String? = null,
            artUri: String? = null,
            lyrics: String? = null
        ): androidx.work.Operation {
            require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            val encodedId = id.encode()
            require(encodedId.length <= 1024)
            val inputData = Data.Builder()
                .putString(KEY_MEDIA_ID, encodedId)
                .putString(KEY_TITLE, title.take(80))
                .putLong(KEY_DURATION, durationMs ?: 0L)
                .putStringArray(KEY_QUALITY, quality.chain.map { it.name }.toTypedArray())
                .putString(KEY_MODE, quality.mode.name)
                .apply {
                    artist?.take(200)?.let { putString(KEY_ARTIST, it) }
                    album?.take(200)?.let { putString(KEY_ALBUM, it) }
                    artUri?.take(1024)?.let { putString(KEY_ART_URI, it) }
                    lyrics?.take(8_000)?.let { putString(KEY_LYRICS, it) }
                }
                .build()
            val request = OneTimeWorkRequestBuilder<OnlineDownloadWorker>()
                .setInputData(inputData)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .addTag(TAG)
                .build()
            // A SUCCEEDED work info is not proof that the file still exists: the user can
            // delete it from the library. KEEP would leave that terminal record forever
            // and make the next tap a no-op, so every explicit retry must replace it.
            return WorkManager.getInstance(context)
                .enqueueUniqueWork(workName(id), ExistingWorkPolicy.REPLACE, request)
        }

        internal fun cancel(context: Context, id: OnlineMediaId) =
            WorkManager.getInstance(context).cancelUniqueWork(workName(id))

        internal fun observe(context: Context, id: OnlineMediaId) =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(workName(id))

        private fun workName(id: OnlineMediaId): String {
            val digest = MessageDigest.getInstance("SHA-256").digest(id.encode().toByteArray(Charsets.UTF_8))
            return "$TAG-${digest.joinToString("") { "%02x".format(it) }}"
        }
    }
}
