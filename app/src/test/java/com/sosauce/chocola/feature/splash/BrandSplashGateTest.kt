package com.sosauce.chocola.feature.splash

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BrandSplashGateTest {

    @Before
    fun resetSession() {
        BrandSplashSession.resetForTests()
    }

    @After
    fun cleanup() {
        BrandSplashSession.resetForTests()
    }

    @Test
    fun noPermissionSkipsOverlayAndDoesNotCompleteSession() {
        val decision = BrandSplashGate.decideInitial(
            hasMusicPermission = false,
            sessionAlreadyCompleted = false,
            initReady = false,
        )
        assertEquals(BrandSplashPhase.Hidden, decision.phase)
        assertEquals(BrandSplashReason.NoPermission, decision.reason)
        assertFalse(decision.markSessionComplete)
    }

    @Test
    fun noPermissionStillSkipsWhenInitAlreadyReady() {
        val decision = BrandSplashGate.decideInitial(
            hasMusicPermission = false,
            sessionAlreadyCompleted = false,
            initReady = true,
        )
        assertEquals(BrandSplashPhase.Hidden, decision.phase)
        assertEquals(BrandSplashReason.NoPermission, decision.reason)
        assertFalse(decision.markSessionComplete)
    }

    @Test
    fun coldStartWithPermissionShowsOverlay() {
        val decision = BrandSplashGate.decideInitial(
            hasMusicPermission = true,
            sessionAlreadyCompleted = false,
            initReady = false,
        )
        assertEquals(BrandSplashPhase.Visible, decision.phase)
        assertEquals(BrandSplashReason.ColdStart, decision.reason)
        assertFalse(decision.markSessionComplete)
    }

    @Test
    fun alreadyReadySkipsOverlayAndCompletesSession() {
        val decision = BrandSplashGate.decideInitial(
            hasMusicPermission = true,
            sessionAlreadyCompleted = false,
            initReady = true,
        )
        assertEquals(BrandSplashPhase.Hidden, decision.phase)
        assertEquals(BrandSplashReason.AlreadyReady, decision.reason)
        assertTrue(decision.markSessionComplete)
    }

    @Test
    fun sessionCompletedDoesNotShowAgainEvenIfInitNotReady() {
        BrandSplashSession.markCompleted()
        val decision = BrandSplashGate.decideInitial(
            hasMusicPermission = true,
            sessionAlreadyCompleted = BrandSplashSession.completed,
            initReady = false,
        )
        assertEquals(BrandSplashPhase.Hidden, decision.phase)
        assertEquals(BrandSplashReason.SessionAlreadyCompleted, decision.reason)
        assertFalse(decision.markSessionComplete)
    }

    @Test
    fun settleReadyFinishesAndCompletesSession() {
        val decision = BrandSplashGate.decideAfterSettle(BrandSplashSettle.Ready)
        assertEquals(BrandSplashPhase.Finished, decision.phase)
        assertEquals(BrandSplashReason.InitReady, decision.reason)
        assertTrue(decision.markSessionComplete)
    }

    @Test
    fun settleFailedDismissesSoErrorsAreNotCovered() {
        val decision = BrandSplashGate.decideAfterSettle(BrandSplashSettle.Failed)
        assertEquals(BrandSplashPhase.Finished, decision.phase)
        assertEquals(BrandSplashReason.InitFailed, decision.reason)
        assertTrue(decision.markSessionComplete)
    }

    @Test
    fun settleTimedOutDismissesAndDoesNotSpinForever() {
        val decision = BrandSplashGate.decideAfterSettle(BrandSplashSettle.TimedOut)
        assertEquals(BrandSplashPhase.Finished, decision.phase)
        assertEquals(BrandSplashReason.InitTimedOut, decision.reason)
        assertTrue(decision.markSessionComplete)
    }

    @Test
    fun settleCancelledDoesNotMarkSessionComplete() {
        val decision = BrandSplashGate.decideAfterSettle(BrandSplashSettle.Cancelled)
        assertEquals(BrandSplashPhase.Visible, decision.phase)
        assertFalse(decision.markSessionComplete)
        assertFalse(BrandSplashSession.completed)
    }

    @Test
    fun reentryAfterCompletionStaysHidden() {
        val first = BrandSplashGate.decideInitial(true, false, false)
        assertEquals(BrandSplashPhase.Visible, first.phase)
        val after = BrandSplashGate.decideAfterSettle(BrandSplashSettle.Ready)
        if (after.markSessionComplete) BrandSplashSession.markCompleted()
        val reentry = BrandSplashGate.decideInitial(
            hasMusicPermission = true,
            sessionAlreadyCompleted = BrandSplashSession.completed,
            initReady = false,
        )
        assertEquals(BrandSplashPhase.Hidden, reentry.phase)
        assertEquals(BrandSplashReason.SessionAlreadyCompleted, reentry.reason)
    }

    @Test
    fun ringIsStaticWhenAnimatorScaleIsZero() {
        assertFalse(BrandSplashGate.shouldAnimateRing(0f))
        assertTrue(BrandSplashGate.shouldAnimateRing(1f))
        assertTrue(BrandSplashGate.shouldAnimateRing(0.5f))
    }

    @Test
    fun maxWaitIsACapNotAMinimumDisplay() {
        assertTrue(BrandSplashLayout.MAX_WAIT_MS > 0L)
        // There is no MIN_DISPLAY_MS by design.
        val source = java.io.File("app/src/main/java/com/sosauce/chocola/feature/splash")
            .takeIf { it.isDirectory }
            ?: java.io.File("../app/src/main/java/com/sosauce/chocola/feature/splash")
        val texts = source.listFiles()?.filter { it.extension == "kt" }?.joinToString("\n") { it.readText() }
            ?: throw AssertionError("splash sources not found")
        assertFalse(texts.contains("MIN_DISPLAY"))
        assertFalse(texts.contains("minimumDisplay"))
        assertFalse(texts.contains("MIN_SHOW"))
    }
}
