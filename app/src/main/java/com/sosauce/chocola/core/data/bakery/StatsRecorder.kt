package com.sosauce.chocola.core.data.bakery

import com.sosauce.chocola.core.data.bakery.entity.BakeryListenEntity
import com.sosauce.chocola.core.data.bakery.entity.BakeryStreakEntity
import com.sosauce.chocola.core.data.bakery.entity.BakeryTrackEntity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.time.Duration.Companion.milliseconds

/**
 * Immutable playback values captured by PlaybackService on the main thread.
 *
 * [durationMillis] smaller than or equal to zero means an unknown duration and is treated as a
 * long track.
 */
data class PlaybackSnapshot(
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String,
    val artworkUri: String?,
    val durationMillis: Long,
    val positionMillis: Long,
    val playbackSpeed: Float,
    val isPlaying: Boolean
)

/**
 * Records Bakery listens and advances the listening streak.
 *
 * Event methods must be called from the main thread. Playback timers and session state live on a
 * single main-thread scope; only DAO work is dispatched to [ioDispatcher].
 */
class StatsRecorder(
    private val dao: BakeryDao,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {

    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val streakMutex = Mutex()

    private var session: Session? = null
    private var nextSessionId = 0L
    private var lastEvaluatedStreakEpochDay: Long? = null

    fun onIsPlayingChanged(snapshot: PlaybackSnapshot) {
        if (snapshot.isPlaying) {
            evaluateStreak()
        }

        val active = session ?: return
        if (active.snapshot.mediaId != snapshot.mediaId) {
            cancelTimer(active)
            return
        }

        active.snapshot = snapshot
        if (snapshot.isPlaying) {
            scheduleCount(active)
        } else {
            cancelTimer(active)
        }
    }

    /**
     * Starts a new session for a new media item or a repeat-one restart.
     */
    fun onMediaItemTransition(snapshot: PlaybackSnapshot) {
        cancelTimer(session)

        val newSession = Session(
            id = ++nextSessionId,
            snapshot = snapshot
        )
        session = newSession
        scheduleCount(newSession)
    }

    fun onSeek(snapshot: PlaybackSnapshot) {
        refreshSession(snapshot)
    }

    fun onPlaybackSpeedChanged(snapshot: PlaybackSnapshot) {
        refreshSession(snapshot)
    }

    fun onPlaybackEnded() {
        cancelTimer(session)
    }

    fun release() {
        cancelTimer(session)
        session = null
        scope.cancel()
    }

    private fun refreshSession(snapshot: PlaybackSnapshot) {
        val active = session ?: return
        if (active.snapshot.mediaId != snapshot.mediaId) {
            cancelTimer(active)
            return
        }

        active.snapshot = snapshot
        scheduleCount(active)
    }

    private fun scheduleCount(session: Session) {
        cancelTimer(session)
        if (session.id != this.session?.id || session.counted) {
            return
        }

        val snapshot = session.snapshot
        if (!snapshot.isPlaying) {
            return
        }

        if (snapshot.durationMillis in 1 until SHORT_TRACK_MILLIS) {
            count(session, snapshot)
            return
        }

        val positionMillis = snapshot.positionMillis.coerceAtLeast(0L)
        if (positionMillis >= LISTEN_THRESHOLD_MILLIS) {
            count(session, snapshot)
            return
        }

        val speed = normalizedSpeed(snapshot.playbackSpeed)
        val delayMillis = ceil(
            (LISTEN_THRESHOLD_MILLIS - positionMillis).toDouble() / speed
        ).toLong().coerceAtLeast(1L)

        session.timer = scope.launch {
            delay(delayMillis.milliseconds)
            if (session.id != this@StatsRecorder.session?.id || session.counted) {
                return@launch
            }
            session.timer = null
            count(session, snapshot)
        }
    }

    private fun count(session: Session, snapshot: PlaybackSnapshot) {
        if (session.id != this.session?.id || session.counted) {
            return
        }

        session.counted = true
        session.timer = null

        val track = BakeryTrackEntity(
            mediaId = snapshot.mediaId,
            title = snapshot.title,
            artist = snapshot.artist,
            album = snapshot.album,
            artworkUri = snapshot.artworkUri,
            durationMillis = snapshot.durationMillis
        )
        val listen = BakeryListenEntity(
            mediaId = snapshot.mediaId,
            listenedAtMillis = clock.millis(),
            epochDay = LocalDate.now(clock).toEpochDay()
        )

        launchSafely {
            safeDbCall { dao.recordListen(track, listen) }
        }
        println("StatsRecorder - Counted!")
    }

    private fun evaluateStreak() {
        val today = LocalDate.now(clock).toEpochDay()
        if (lastEvaluatedStreakEpochDay == today) {
            return
        }

        launchSafely {
            streakMutex.withLock {
                if (lastEvaluatedStreakEpochDay == today) {
                    return@withLock
                }

                val streakResult = safeDbCall { dao.getStreak() }
                if (streakResult.isFailure) {
                    return@withLock
                }

                val streak = streakResult.getOrNull()
                val updated = when {
                    streak == null -> BakeryStreakEntity(
                        currentStreak = 1,
                        bestStreak = 1,
                        lastActiveEpochDay = today
                    )

                    streak.lastActiveEpochDay == today -> null
                    streak.lastActiveEpochDay > today -> null

                    streak.lastActiveEpochDay == today - 1 -> {
                        val current = streak.currentStreak + 1
                        streak.copy(
                            currentStreak = current,
                            bestStreak = maxOf(streak.bestStreak, current),
                            lastActiveEpochDay = today
                        )
                    }

                    else -> streak.copy(
                        currentStreak = 1,
                        bestStreak = maxOf(streak.bestStreak, 1),
                        lastActiveEpochDay = today
                    )
                }

                if (updated != null) {
                    val writeResult = safeDbCall { dao.upsertStreak(updated) }
                    if (writeResult.isFailure) {
                        return@withLock
                    }
                }

                lastEvaluatedStreakEpochDay = today
            }
        }
    }

    private fun cancelTimer(session: Session?) {
        session?.timer?.cancel()
        session?.timer = null
    }

    private fun normalizedSpeed(speed: Float): Double {
        if (!speed.isFinite()) {
            return 1.0
        }
        return speed.coerceAtLeast(MIN_PLAYBACK_SPEED).toDouble()
    }

    private fun launchSafely(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }
                .onFailure { error ->
                    if (error is CancellationException) {
                        throw error
                    }
                }
        }
    }

    private suspend fun <T> safeDbCall(block: suspend () -> T): Result<T> =
        runCatching { withContext(ioDispatcher) { block() } }
            .onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
            }

    private class Session(
        val id: Long,
        var snapshot: PlaybackSnapshot,
        var counted: Boolean = false
    ) {
        var timer: Job? = null
    }

    private companion object {
        const val LISTEN_THRESHOLD_MILLIS = 10_000L
        const val SHORT_TRACK_MILLIS = 20_000L
        const val MIN_PLAYBACK_SPEED = 0.5f
    }
}
