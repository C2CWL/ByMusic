package com.sosauce.chocola.feature.playing.presentation.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TurntableMotionTest {

    @Test
    fun playPauseUsesTheExistingOnDiscAndRestAngles() {
        assertEquals(0f, TurntableMotion.ARM_ON_DISC_DEGREES)
        assertEquals(-19f, TurntableMotion.ARM_REST_DEGREES)
        assertEquals(650, TurntableMotion.ARM_ANIMATION_DURATION_MS)
        assertEquals(0f, TurntableMotion.playPauseArmTarget(isPlaying = true))
        assertEquals(-19f, TurntableMotion.playPauseArmTarget(isPlaying = false))
    }

    @Test
    fun starRadiusGrowsTowardTheRimAndAlphaFadesOverLife() {
        val birth = 0.6f
        assertEquals(birth, TurntableMotion.starRadius(birth, 0f), 1e-5f)
        assertTrue(TurntableMotion.starRadius(birth, 0.5f) > birth)
        assertEquals(TurntableMotion.STAR_END_RADIUS, TurntableMotion.starRadius(birth, 1f), 1e-5f)
        assertEquals(0.3f, TurntableMotion.starAlpha(0.3f, 0f), 1e-5f)
        assertTrue(TurntableMotion.starAlpha(0.3f, 0.5f) < 0.3f)
        assertEquals(0f, TurntableMotion.starAlpha(0.3f, 1f), 1e-5f)
        assertFalse(TurntableMotion.shouldRespawn(0.99f))
        assertTrue(TurntableMotion.shouldRespawn(1f))
    }

    @Test
    fun starFieldStaysBoundedAndRespawnsIntoTheSameSlots() {
        val field = TurntableStarField(count = 32, seed = 7)
        val identities = System.identityHashCode(field.life)
        repeat(40) { field.update(0.2f) }
        assertEquals(32, field.count)
        assertEquals(32, field.life.size)
        assertEquals(identities, System.identityHashCode(field.life))
        assertTrue(field.life.all { it >= 0f && it < 1f })
        assertTrue((0 until field.count).all { field.radius(it) <= TurntableMotion.STAR_END_RADIUS + 1e-4f })
        assertTrue((0 until field.count).all { field.alpha(it) >= 0f })
    }

    @Test
    fun pausedUpdatesDoNotAdvanceWhenDeltaIsZero() {
        val field = TurntableStarField(count = 8, seed = 1)
        val before = field.life.copyOf()
        field.update(0f)
        assertTrue(before.contentEquals(field.life))
    }
}
