package com.sosauce.chocola.feature.playing.presentation.components

import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * Record sheen, drawn from scratch for this app: light comes from the top
 * left, so two opposite fan-shaped highlights sit on the grooves at fixed
 * screen angles (they do not spin with the record). Angles follow Compose's
 * sweep gradient: 0 degrees is 3 o'clock and angles grow clockwise.
 */
internal object TurntableSheen {
    /** Main highlight, toward the light (top left). */
    const val KEY_LOBE_DEGREES = 225f

    /** Weaker opposite highlight (bottom right). */
    const val FILL_LOBE_DEGREES = 45f
    const val FILL_LOBE_STRENGTH = 0.55f

    /** Half-widths of the soft wide layer and the narrow bright layer, in turns. */
    const val WIDE_HALF_WIDTH = 0.10f
    const val NARROW_HALF_WIDTH = 0.035f

    /** OLED black: highlights about 20% weaker and a fainter halo. */
    const val OLED_SHEEN_SCALE = 0.8f
    const val OLED_HALO_SCALE = 0.6f

    /** Surface darker than this luminance is treated as OLED black. */
    const val OLED_LUMINANCE = 0.02f

    fun intensity(oled: Boolean): Float = if (oled) OLED_SHEEN_SCALE else 1f

    fun haloIntensity(oled: Boolean): Float = if (oled) OLED_HALO_SCALE else 1f

    /**
     * Sweep-gradient stop positions and relative strengths (0..1) for one
     * highlight layer of [halfWidth] turns, both lobes, ascending in 0..1.
     */
    fun lobeStops(halfWidth: Float): List<Pair<Float, Float>> {
        val h = halfWidth.coerceIn(0.005f, 0.12f)
        val fill = FILL_LOBE_DEGREES / 360f
        val key = KEY_LOBE_DEGREES / 360f
        fun lobe(center: Float, strength: Float) =
            listOf(
                center - h to 0f,
                center - h / 2f to strength * 0.45f,
                center to strength,
                center + h / 2f to strength * 0.45f,
                center + h to 0f,
            )
        return listOf(0f to 0f) + lobe(fill, FILL_LOBE_STRENGTH) + lobe(key, 1f) + listOf(1f to 0f)
    }
}

/**
 * At most three soft glints sliding slowly along the highlights, one cycle
 * about every five seconds. Pure math; the phase is advanced by the page only
 * while the record plays, and is never advanced when animations are off.
 */
internal object TurntableGlint {
    const val COUNT = 3
    const val CYCLE_SECONDS = 5f
    const val MAX_ALPHA = 0.5f

    /** How far one glint travels along its highlight, in degrees. */
    const val TRAVEL_DEGREES = 48f

    private val OFFSETS = floatArrayOf(0f, 0.37f, 0.69f)
    private val RADII = floatArrayOf(0.70f, 0.86f, 0.78f)
    private val LOBES = floatArrayOf(TurntableSheen.KEY_LOBE_DEGREES, TurntableSheen.KEY_LOBE_DEGREES, TurntableSheen.FILL_LOBE_DEGREES)
    private val STRENGTH = floatArrayOf(1f, 0.75f, 0.6f)

    fun advance(phase: Float, deltaSeconds: Float): Float {
        val dt = deltaSeconds.coerceIn(0f, 0.05f)
        val next = phase + dt / CYCLE_SECONDS
        return next - next.toInt()
    }

    private fun local(index: Int, phase: Float): Float {
        val t = phase + OFFSETS[index]
        return t - t.toInt()
    }

    /** Fraction of the record radius the glint sits at. */
    fun radiusFraction(index: Int): Float = RADII[index]

    fun angleDegrees(index: Int, phase: Float): Float =
        LOBES[index] + (local(index, phase) - 0.5f) * TRAVEL_DEGREES

    /** Fades in and out over its pass, so glints never pop. */
    fun alpha(index: Int, phase: Float): Float {
        val s = sin(PI * local(index, phase)).toFloat()
        return (MAX_ALPHA * STRENGTH[index] * s * s).coerceIn(0f, 1f)
    }
}

/**
 * Fine grooves with uneven brightness, plus very faint dust. Generated once
 * from a fixed seed, so every page and the expand transition draw the same
 * record and nothing is allocated while drawing.
 */
internal object TurntableGrooves {
    const val COUNT = 110
    const val DUST_COUNT = 70
    const val INNER = 0.575f
    const val OUTER = 0.985f
    private const val SEED = 41

    val radius = FloatArray(COUNT)
    val alpha = FloatArray(COUNT)
    val dustX = FloatArray(DUST_COUNT)
    val dustY = FloatArray(DUST_COUNT)
    val dustSize = FloatArray(DUST_COUNT)
    val dustAlpha = FloatArray(DUST_COUNT)

    init {
        val random = Random(SEED)
        val step = (OUTER - INNER) / (COUNT - 1)
        for (i in 0 until COUNT) {
            radius[i] = INNER + step * i + (random.nextFloat() - 0.5f) * step * 0.6f
            val bright = i % 9 == 0
            alpha[i] = if (bright) 0.10f + random.nextFloat() * 0.04f else 0.025f + random.nextFloat() * 0.06f
        }
        for (i in 0 until DUST_COUNT) {
            val angle = random.nextFloat() * 2f * PI.toFloat()
            val r = INNER + random.nextFloat() * (OUTER - INNER)
            dustX[i] = kotlin.math.cos(angle) * r
            dustY[i] = sin(angle) * r
            dustSize[i] = 0.3f + random.nextFloat() * 0.5f
            dustAlpha[i] = 0.05f + random.nextFloat() * 0.12f
        }
    }
}
