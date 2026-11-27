package com.sosauce.chocola.feature.playing.presentation.expand

import androidx.compose.animation.core.FloatSpringSpec
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageDragCloseTest {
    @Test
    fun dragUsesTopEdgeTravelAndKeepsTheSameOriginAcrossBothBounds() {
        val travel = PlayerExpandMath.travelPx(710f, 10f)
        assertEquals(700f, travel, 0f)
        assertEquals(1f, PlayerExpandMath.travelPx(10f, 10f), 0f)
        assertEquals(0f, PlayerExpandMath.dragProgress(.5f, 900f, travel), 0f)
        assertEquals(1f, PlayerExpandMath.dragProgress(.5f, -900f, travel), 0f)
        assertEquals(.7f, PlayerExpandMath.dragProgress(.5f, -140f, travel), .00001f)
        assertEquals(.8f, PlayerExpandMath.dragProgress(1f, 140f, travel), .00001f)
    }

    @Test
    fun shortPullFromExpandedClosesEarlierAndFlingWins() {
        assertEquals(1f, PlayerExpandMath.settleTarget(.79f, 0f), 0f)
        assertEquals(0f, PlayerExpandMath.settleTarget(.79f, 0f, startedExpanded = true), 0f)
        assertEquals(1f, PlayerExpandMath.settleTarget(.8f, 0f, startedExpanded = true), 0f)
        assertEquals(1f, PlayerExpandMath.settleTarget(.3f, -800f), 0f)
        assertEquals(0f, PlayerExpandMath.settleTarget(.9f, 800f), 0f)
    }

    @Test
    fun releaseCarriesFingerVelocityAndStaysInsideBounds() {
        val release = FloatSpringSpec(stiffness = PlayerExpandMath.RELEASE_STIFFNESS, visibilityThreshold = .001f)
        val start = .71f
        val velocity = PlayerExpandMath.progressVelocity(1050f, 700f)
        assertEquals(-1.5f, velocity, 0f)
        assertEquals(velocity, PlayerExpandMath.boundReleaseVelocity(start, 0f, velocity), 0f)
        assertTrue(
            release.getValueFromNanos(16_666_667L, start, 0f, velocity) <
                release.getValueFromNanos(16_666_667L, start, 0f, 0f)
        )
        for (from in listOf(0f, .01f, .3f, .5f, .97f, 1f)) {
            for (to in listOf(0f, 1f)) {
                for (finger in listOf(-6f, -1.5f, 0f, 1.5f, 6f)) {
                    val initial = PlayerExpandMath.boundReleaseVelocity(from, to, finger)
                    var previous = from
                    for (frame in 1..60) {
                        val position = release.getValueFromNanos(frame * 16_666_667L, from, to, initial)
                        assertTrue(position in 0f..1f)
                        assertTrue(abs(position - previous) < .15f)
                        previous = position
                    }
                }
            }
        }
    }
}
