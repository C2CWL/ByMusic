package com.sosauce.chocola.core.domain.online

import kotlin.coroutines.cancellation.CancellationException

/**
 * Real audio quality tiers, ordered from lowest to highest.
 *
 * These describe what a file actually is (to be measured in a later stage), not what a
 * plugin claims. [musicFreeKey] is only the name used when asking a MusicFree-style
 * plugin for a tier; several tiers share one key because MusicFree has just four.
 */
enum class QualityTier(val musicFreeKey: String) {
    LOSSY_LOW("low"),
    LOSSY_128("low"),
    LOSSY_192("standard"),
    LOSSY_320("standard"),
    LOSSLESS("high"),
    HIRES("super"),
    SPATIAL("super");

    companion object {
        /** Default request tier for each MusicFree key (low→128k, standard→320k, high→lossless, super→Hi-Res). */
        fun fromMusicFreeKey(key: String?): QualityTier? = when (key) {
            "low" -> LOSSY_128
            "standard" -> LOSSY_320
            "high" -> LOSSLESS
            "super" -> HIRES
            else -> null
        }
    }
}

/**
 * Which tiers to try, in order. The first entry is the requested tier; the rest is the
 * downgrade chain used when a tier fails ("自动请求音质，失败就从顶部往下来").
 */
data class QualityRequest(
    val chain: List<QualityTier>,
    val mode: Mode
) {
    enum class Mode { AUTO_FROM_TOP, PREFERRED_THEN_DOWN, EXACT }

    init {
        require(chain.isNotEmpty()) { "quality chain must not be empty" }
        require(chain.toSet().size == chain.size) { "quality chain must not repeat tiers" }
    }

    val requested: QualityTier get() = chain.first()

    /** MusicFree keys to ask for, in order, without repeats. */
    fun musicFreeKeyChain(): List<String> = chain.map { it.musicFreeKey }.distinct()

    companion object {
        /**
         * Default for playback and download: start from the highest available tier and
         * walk down. Spatial audio is skipped unless asked for, because whether it plays
         * depends on the phone's decoders.
         */
        fun autoFromTop(
            available: Collection<QualityTier> = QualityTier.entries,
            includeSpatial: Boolean = false
        ): QualityRequest {
            val tiers = available.toSet()
                .filter { includeSpatial || it != QualityTier.SPATIAL }
                .sortedDescending()
            return QualityRequest(tiers.ifEmpty { listOf(QualityTier.LOSSY_128) }, Mode.AUTO_FROM_TOP)
        }

        /** Try [tier] first, then (optionally) every lower tier, highest first. */
        fun preferred(tier: QualityTier, allowDowngrade: Boolean = true): QualityRequest {
            if (!allowDowngrade) return QualityRequest(listOf(tier), Mode.EXACT)
            val lower = QualityTier.entries
                .filter { it < tier && it != QualityTier.SPATIAL }
                .sortedDescending()
            return QualityRequest(listOf(tier) + lower, Mode.PREFERRED_THEN_DOWN)
        }
    }
}

data class FallbackAttempt(val tier: QualityTier, val error: String?)

data class FallbackOutcome<T>(
    val tier: QualityTier,
    val value: T,
    val attempts: List<FallbackAttempt>
)

/**
 * Runs [attempt] for each tier in order until one returns non-null. Failures (null or
 * an exception) are recorded and the next tier is tried. Coroutine cancellation is
 * never swallowed. Returns null when every tier failed.
 */
suspend fun <T : Any> QualityRequest.tryInOrder(
    attempt: suspend (QualityTier) -> T?
): FallbackOutcome<T>? {
    val attempts = ArrayList<FallbackAttempt>(chain.size)
    for (tier in chain) {
        val result = try {
            attempt(tier)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            attempts += FallbackAttempt(tier, e.message ?: e::class.java.simpleName)
            continue
        }
        if (result != null) {
            attempts += FallbackAttempt(tier, null)
            return FallbackOutcome(tier, result, attempts)
        }
        attempts += FallbackAttempt(tier, "no result")
    }
    return null
}
