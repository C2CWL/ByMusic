package com.sosauce.chocola.feature.splash

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandSplashMotionTest {

    private val eps = 1e-3f

    @Test
    fun timelineMatchesConfirmedPreview() {
        assertEquals(550, BrandSplashMotion.GROW_MS)
        assertEquals(250, BrandSplashMotion.CHROME_FADE_MS)
        assertEquals(700L, BrandSplashMotion.MIN_HOLD_MS)
        assertEquals(250, BrandSplashMotion.EXIT_FADE_MS)
        assertEquals(8_000L, BrandSplashLayout.MAX_WAIT_MS)
        assertEquals(112f, BrandSplashMotion.DISC_DP)
    }

    @Test
    fun holdIsAtLeastSevenHundredMilliseconds() {
        assertEquals(700L, BrandSplashMotion.holdRemainingMs(0L))
        assertEquals(450L, BrandSplashMotion.holdRemainingMs(250L))
        assertEquals(0L, BrandSplashMotion.holdRemainingMs(700L))
        // init that took longer than the minimum adds no extra wait
        assertEquals(0L, BrandSplashMotion.holdRemainingMs(5_000L))
    }

    @Test
    fun animationsAreSkippedWhenSystemAnimationsAreOff() {
        assertFalse(BrandSplashMotion.shouldAnimate(0f))
        assertTrue(BrandSplashMotion.shouldAnimate(0.5f))
        assertTrue(BrandSplashMotion.shouldAnimate(1f))
    }

    @Test
    fun recordCenterIsTheFittedArtworkCenterOnTheReferenceScreen() {
        val g = BrandSplashMotion.recordGeometry(1080f, 1920f)
        assertEquals(-402f, g.centerX, eps)
        assertEquals(-456f, g.centerY, eps)
        assertEquals(1650f, g.radius, eps)
    }

    @Test
    fun recordGeometryFollowsCropTopStartOnTallerAndWiderScreens() {
        // 1080x2400: Crop scales by 2400/1920 = 1.25, anchored top-left
        val tall = BrandSplashMotion.recordGeometry(1080f, 2400f)
        assertEquals(1.25f, BrandSplashMotion.artScale(1080f, 2400f), eps)
        assertEquals(-402f * 1.25f, tall.centerX, eps)
        assertEquals(-456f * 1.25f, tall.centerY, eps)
        assertEquals(1650f * 1.25f, tall.radius, eps)
        // landscape 2400x1080: scale by width
        assertEquals(2400f / 1080f, BrandSplashMotion.artScale(2400f, 1080f), eps)
        // the record centre is always outside the screen (top-left)
        for ((w, h) in listOf(1080f to 1920f, 1080f to 2400f, 1440f to 3200f, 2400f to 1080f)) {
            val g = BrandSplashMotion.recordGeometry(w, h)
            assertTrue(g.centerX < 0f && g.centerY < 0f)
        }
    }

    @Test
    fun defaultStartIsScreenCentre112dp() {
        val s = BrandSplashMotion.defaultStart(1080f, 1920f, density = 2.625f)
        assertEquals(540f, s.centerX, eps)
        assertEquals(960f, s.centerY, eps)
        assertEquals(147f, s.radius, eps)
    }

    @Test
    fun startFromStandardIconViewMatchesDefault() {
        // 288 dp icon view centred on a 1080x1920 screen at 2.625 density
        val side = 288f * 2.625f
        val bounds = SplashIconBounds(540f - side / 2f, 960f - side / 2f, side, side)
        val s = BrandSplashMotion.startFromIcon(bounds)!!
        val d = BrandSplashMotion.defaultStart(1080f, 1920f, 2.625f)
        assertEquals(d.centerX, s.centerX, eps)
        assertEquals(d.centerY, s.centerY, eps)
        assertEquals(d.radius, s.radius, eps)
    }

    @Test
    fun startFromIconFollowsAnOemSizedIconView() {
        // e.g. a 192 dp icon view placed slightly off-centre
        val bounds = SplashIconBounds(300f, 700f, 504f, 504f)
        val s = BrandSplashMotion.startFromIcon(bounds)!!
        assertEquals(552f, s.centerX, eps)
        assertEquals(952f, s.centerY, eps)
        assertEquals(504f * 56f / 288f, s.radius, eps)
        assertNull(BrandSplashMotion.startFromIcon(SplashIconBounds(0f, 0f, 0f, 0f)))
    }

    @Test
    fun discMeetsExactEndpointsAndGrowsMonotonically() {
        val start = BrandSplashMotion.defaultStart(1080f, 1920f, 2.625f)
        val end = BrandSplashMotion.recordGeometry(1080f, 1920f)
        assertEquals(start, BrandSplashMotion.discAt(0f, start, end))
        val atEnd = BrandSplashMotion.discAt(1f, start, end)
        assertEquals(end.centerX, atEnd.centerX, eps)
        assertEquals(end.centerY, atEnd.centerY, eps)
        assertEquals(end.radius, atEnd.radius, 0.05f)
        var last = start.radius
        for (i in 1..20) {
            val r = BrandSplashMotion.discAt(i / 20f, start, end).radius
            assertTrue(r > last)
            last = r
        }
        // clamped outside 0..1
        assertEquals(start, BrandSplashMotion.discAt(-1f, start, end))
    }

    @Test
    fun firstFrameShowsOnlyTheDiscAndLastFrameOnlyTheArtwork() {
        assertEquals(1f, BrandSplashMotion.discAlpha(0f), eps)
        assertEquals(0f, BrandSplashMotion.artAlpha(0f), eps)
        assertEquals(0f, BrandSplashMotion.discAlpha(1f), eps)
        assertEquals(1f, BrandSplashMotion.artAlpha(1f), eps)
        // the artwork is already coming in while the disc is still fully visible
        assertTrue(BrandSplashMotion.artAlpha(0.5f) > 0f)
        assertEquals(1f, BrandSplashMotion.discAlpha(0.5f), eps)
    }
}
