package com.sosauce.chocola.feature.playing.presentation.expand

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.animation.core.FloatTweenSpec
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSheetStateTest {
    @Test
    fun newerRouteInstanceOwnsTheDockBeforeAndAfterItMeasures() {
        val registry = PlayerDockRegistry<String>(mutableMapOf())
        val outgoing = registry.newEntry("library")
        registry.attach(outgoing)
        registry.update(outgoing, "old bounds")
        val incoming = registry.newEntry("library")
        registry.attach(incoming)

        assertSame(incoming, registry.active("library"))
        assertNull(registry.descriptor(registry.active("library")))
        assertNull(registry.active("queue"))
        registry.update(incoming, "new bounds")
        registry.detach(outgoing)
        registry.update(outgoing, "late bounds")
        assertEquals("new bounds", registry.descriptor(registry.active("library")))
        registry.update(incoming, null)
        assertNull(registry.descriptor(registry.active("library")))
    }

    @Test
    fun reversalUsesTheCurrentValueAndSignedVelocity() {
        val opening = FloatTweenSpec(PlayerExpandMath.EXPAND_MS, easing = Easing(PlayerExpandMath::tapEasing))
        val closing = FloatSpringSpec(stiffness = PlayerExpandMath.RELEASE_STIFFNESS, visibilityThreshold = .001f)
        val interruption = 135_000_000L
        val position = opening.getValueFromNanos(interruption, 0f, 1f, 0f)
        val velocity = opening.getVelocityFromNanos(interruption, 0f, 1f, 0f)
        assertTrue(velocity > 0f)
        assertEquals(position, closing.getValueFromNanos(0, position, 0f, velocity), 0f)
        assertEquals(velocity, closing.getVelocityFromNanos(0, position, 0f, velocity), 0f)
        assertTrue(closing.getValueFromNanos(1_000_000, position, 0f, velocity) > position)
        assertTrue(closing.getValueFromNanos(100_000_000, position, 0f, velocity) < position)
    }

    @Test
    fun reversalAndPredictiveBackSampleTheSameContentMapping() {
        for (step in 0..100) {
            val opening = step / 100f
            // A back gesture from full can land at any of the same g values.
            val backing = PlayerExpandMath.backProgress(1f, 1f - opening)
            assertEquals(PlayerExpandMath.miniFaceAlpha(opening), PlayerExpandMath.miniFaceAlpha(backing), .00001f)
            assertEquals(PlayerExpandMath.chromeAlpha(opening), PlayerExpandMath.chromeAlpha(backing), .00001f)
            assertEquals(PlayerExpandMath.settleOffsetDp(opening), PlayerExpandMath.settleOffsetDp(backing), .00001f)
            assertEquals(VinylExpandMath.backdropAlpha(opening), VinylExpandMath.backdropAlpha(backing), .00001f)
        }
        assertEquals(1f, PlayerExpandMath.miniFaceAlpha(0f), 0f)
        assertEquals(0f, PlayerExpandMath.miniFaceAlpha(.12f), 0f)
        assertEquals(0f, PlayerExpandMath.chromeAlpha(.72f), 0f)
        assertEquals(1f, PlayerExpandMath.chromeAlpha(1f), 0f)
        assertEquals(26f, PlayerExpandMath.settleOffsetDp(.6f), 0f)
        assertEquals(0f, PlayerExpandMath.settleOffsetDp(1f), 0f)
        assertTrue(abs(PlayerExpandMath.chromeAlpha(.721f) - PlayerExpandMath.chromeAlpha(.719f)) < .001f)
    }

    @Test
    fun predictiveBackUsesTheInterruptedProgressAndHasExactEndpoints() {
        assertEquals(.64f, PlayerExpandMath.backProgress(.64f, 0f), 0f)
        assertEquals(.32f, PlayerExpandMath.backProgress(.64f, .5f), 0f)
        assertEquals(0f, PlayerExpandMath.backProgress(.64f, 1f), 0f)
    }
}
