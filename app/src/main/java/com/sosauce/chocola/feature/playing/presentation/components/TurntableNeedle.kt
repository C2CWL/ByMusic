package com.sosauce.chocola.feature.playing.presentation.components

/**
 * How the tonearm should move to its next target. Durations are the design
 * values for the vinyl page; they are not measured from another app.
 */
internal enum class NeedleMove(val durationMs: Int) {
    /** A track change: swing out to the rest angle, quick out and slow stop. */
    Lift(TurntableNeedle.LIFT_MS),

    /** Skipping has settled and playback is on: land on the new record. */
    Drop(TurntableNeedle.DROP_MS),

    /** Plain play / pause, unchanged from before. */
    PlayPause(TurntableMotion.ARM_ANIMATION_DURATION_MS),
}

internal object TurntableNeedle {
    const val LIFT_MS = 250
    const val DROP_MS = 450

    /** No further skip for this long counts as "the user stopped skipping". */
    const val SKIP_SETTLE_MS = 350L

    /**
     * A track change makes the player go "buffering -> playing" for a moment.
     * While the user still wants playback (playWhenReady), a not-playing blip
     * shorter than this is ignored so the arm does not react to it.
     */
    const val BUFFER_GRACE_MS = 300L
}

/**
 * Pure tonearm state machine, driven by explicit timestamps so it can be unit
 * tested on the JVM. The arm only follows two facts:
 *  - has the current track settled (no skip for [settleMs]), and
 *  - is playback on (user intent, with buffering blips swallowed).
 *
 * A skip lifts the arm once; more skips while it is lifted only restart the
 * settle timer. A paused skip leaves the arm out until play is pressed.
 */
internal class NeedleDirector(
    isPlaying: Boolean,
    playWhenReady: Boolean,
    private val settleMs: Long = TurntableNeedle.SKIP_SETTLE_MS,
    private val bufferGraceMs: Long = TurntableNeedle.BUFFER_GRACE_MS,
) {
    private var isPlaying = isPlaying
    private var playWhenReady = playWhenReady

    /** Last skip time while waiting for the skipping to settle, else null. */
    private var lastSkipAtMs: Long? = null

    /** A skip lifted the arm and it has not landed again since. */
    private var awaitingDrop = false

    /** When playback stopped while the user still wanted it to play. */
    private var stallSinceMs: Long? = null

    /** Whether playback was effectively on before the current stall. */
    private var wasPlaying = isPlaying && playWhenReady

    private var shownTarget: Float = TurntableMotion.playPauseArmTarget(wasPlaying)

    /** The angle the arm is at, or heading to. */
    val currentTarget: Float get() = shownTarget

    fun onTrackChanged(nowMs: Long) {
        lastSkipAtMs = nowMs
    }

    fun onPlayback(isPlaying: Boolean, playWhenReady: Boolean, nowMs: Long) {
        val before = effectivePlaying(nowMs)
        this.isPlaying = isPlaying
        this.playWhenReady = playWhenReady
        stallSinceMs =
            when {
                isPlaying || !playWhenReady -> null
                stallSinceMs != null -> stallSinceMs
                before -> nowMs
                else -> null
            }
        if (isPlaying && playWhenReady) wasPlaying = true
        if (!playWhenReady) {
            wasPlaying = false
            // Landing after a user pause is a plain play/pause move.
            awaitingDrop = false
        }
    }

    /** Playback as the arm sees it: user intent, buffering blips swallowed. */
    fun effectivePlaying(nowMs: Long): Boolean {
        if (!playWhenReady) return false
        if (isPlaying) return true
        val since = stallSinceMs ?: return false
        return wasPlaying && nowMs - since < bufferGraceMs
    }

    fun isSettling(nowMs: Long): Boolean {
        val at = lastSkipAtMs ?: return false
        return nowMs - at < settleMs
    }

    fun target(nowMs: Long): Float =
        if (isSettling(nowMs) || !effectivePlaying(nowMs)) {
            TurntableMotion.ARM_REST_DEGREES
        } else {
            TurntableMotion.ARM_ON_DISC_DEGREES
        }

    /**
     * Computes the target for [nowMs]. Returns the move to start when the
     * target changed, or null when the arm should keep doing what it does.
     */
    fun step(nowMs: Long): NeedleMove? {
        if (lastSkipAtMs != null && !isSettling(nowMs)) lastSkipAtMs = null
        val stall = stallSinceMs
        if (stall != null && nowMs - stall >= bufferGraceMs) {
            stallSinceMs = null
            wasPlaying = false
        }
        val next = target(nowMs)
        if (next == shownTarget) return null
        shownTarget = next
        return if (next == TurntableMotion.ARM_REST_DEGREES) {
            if (lastSkipAtMs != null) {
                awaitingDrop = true
                NeedleMove.Lift
            } else {
                NeedleMove.PlayPause
            }
        } else {
            val move = if (awaitingDrop) NeedleMove.Drop else NeedleMove.PlayPause
            awaitingDrop = false
            move
        }
    }

    /** The next time [step] may produce a change on its own, or null. */
    fun nextDeadlineMs(): Long? {
        val settle = lastSkipAtMs?.plus(settleMs)
        val grace = stallSinceMs?.plus(bufferGraceMs)
        return listOfNotNull(settle, grace).minOrNull()
    }
}
