package com.sosauce.chocola.feature.playing.presentation.components

import kotlin.random.Random

/**
 * Motion constants taken from the existing turntable, plus a bounded star-field
 * model. The arm rest angle and play/pause duration are the values already used
 * by [TurntableDisc]; they are not new measurements from a recording.
 */
internal object TurntableMotion {
    const val ARM_ON_DISC_DEGREES = 0f
    const val ARM_REST_DEGREES = -19f
    const val ARM_ANIMATION_DURATION_MS = 650

    const val STAR_COUNT = 180
    const val STAR_SEED = 73
    const val STAR_MIN_BIRTH_RADIUS = 0.58f
    const val STAR_MAX_BIRTH_RADIUS = 0.95f

    /** Disc rim in the same units as birth radius. Stars vanish as they reach it. */
    const val STAR_END_RADIUS = 1f

    fun playPauseArmTarget(isPlaying: Boolean): Float =
        if (isPlaying) ARM_ON_DISC_DEGREES else ARM_REST_DEGREES

    /**
     * Track changes reuse the existing rest angle, then return to the disc only
     * if playback is still active. A paused change stays at rest so the arm does
     * not land on the incoming record.
     */
    fun trackChangeArmTargets(isPlaying: Boolean): List<Float> =
        if (isPlaying) {
            listOf(ARM_REST_DEGREES, ARM_ON_DISC_DEGREES)
        } else {
            listOf(ARM_REST_DEGREES)
        }

    fun starRadius(birthRadius: Float, life: Float, endRadius: Float = STAR_END_RADIUS): Float {
        val t = life.coerceIn(0f, 1f)
        return birthRadius + (endRadius - birthRadius).coerceAtLeast(0f) * t
    }

    fun starAlpha(baseAlpha: Float, life: Float): Float {
        val t = life.coerceIn(0f, 1f)
        return (baseAlpha * (1f - t)).coerceIn(0f, 1f)
    }

    fun shouldRespawn(life: Float): Boolean = life >= 1f
}

/**
 * Fixed-capacity star field. [update] only mutates existing slots; it never
 * grows the arrays or allocates per frame.
 */
internal class TurntableStarField(
    val count: Int = TurntableMotion.STAR_COUNT,
    seed: Int = TurntableMotion.STAR_SEED,
) {
    private val random = Random(seed)
    val angle = FloatArray(count)
    val birthRadius = FloatArray(count)
    val size = FloatArray(count)
    val baseAlpha = FloatArray(count)
    val life = FloatArray(count)
    val lifePerSecond = FloatArray(count)

    init {
        for (index in 0 until count) {
            spawn(index, initialLife = random.nextFloat())
        }
    }

    fun update(deltaSeconds: Float) {
        val dt = deltaSeconds.coerceIn(0f, 0.05f)
        if (dt <= 0f) return
        for (index in 0 until count) {
            life[index] += lifePerSecond[index] * dt
            if (TurntableMotion.shouldRespawn(life[index])) {
                spawn(index, initialLife = 0f)
            }
        }
    }

    fun radius(index: Int): Float =
        TurntableMotion.starRadius(birthRadius[index], life[index])

    fun alpha(index: Int): Float =
        TurntableMotion.starAlpha(baseAlpha[index], life[index])

    private fun spawn(index: Int, initialLife: Float) {
        angle[index] = random.nextFloat() * 6.283185f
        birthRadius[index] =
            TurntableMotion.STAR_MIN_BIRTH_RADIUS +
                random.nextFloat() * (TurntableMotion.STAR_MAX_BIRTH_RADIUS - TurntableMotion.STAR_MIN_BIRTH_RADIUS)
        size[index] = 0.35f + random.nextFloat() * 0.8f
        baseAlpha[index] = 0.08f + random.nextFloat() * 0.34f
        life[index] = initialLife.coerceIn(0f, 0.99f)
        // About two to five seconds to travel; this is an implementation bound, not a filmed timing.
        lifePerSecond[index] = 1f / (2f + random.nextFloat() * 3f)
    }
}
