package com.sosauce.chocola.feature.splash

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandSplashAwaiterTest {

    @Test
    fun readyWhenPrefsAndPlayerCompleteImmediately() = runBlocking {
        val result = BrandSplashAwaiter.await(
            readPrefs = {},
            awaitPlayer = {},
            timeoutMs = 1_000,
        )
        assertEquals(BrandSplashSettle.Ready, result)
    }

    @Test
    fun readyAfterBothSidesFinishWithNoMinimumDelay() = runBlocking {
        val started = System.nanoTime()
        val result = BrandSplashAwaiter.await(
            readPrefs = { delay(15) },
            awaitPlayer = { delay(25) },
            timeoutMs = 1_000,
        )
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals(BrandSplashSettle.Ready, result)
        assertTrue("should not wait for a display floor, elapsed=$elapsedMs", elapsedMs < 400)
    }

    @Test
    fun timedOutIfPlayerNeverConnects() = runBlocking {
        val result = BrandSplashAwaiter.await(
            readPrefs = {},
            awaitPlayer = { delay(10_000) },
            timeoutMs = 40,
        )
        assertEquals(BrandSplashSettle.TimedOut, result)
    }

    @Test
    fun failedIfPrefsThrow() = runBlocking {
        val result = BrandSplashAwaiter.await(
            readPrefs = { error("prefs unavailable") },
            awaitPlayer = {},
            timeoutMs = 1_000,
        )
        assertEquals(BrandSplashSettle.Failed, result)
    }

    @Test
    fun cancellationPropagatesAndIsNotReportedAsFailed() = runBlocking {
        val deferred = async {
            BrandSplashAwaiter.await(
                readPrefs = { delay(10_000) },
                awaitPlayer = { delay(10_000) },
                timeoutMs = 5_000,
            )
        }
        delay(20)
        deferred.cancel(CancellationException("leave composition"))
        val thrown = runCatching { deferred.await() }.exceptionOrNull()
        assertTrue(
            "expected cancellation, got $thrown",
            thrown is CancellationException
        )
    }

    @Test
    fun withTimeoutDoesNotHangTheTestHarness() = runBlocking {
        withTimeout(500) {
            val result = BrandSplashAwaiter.await(
                readPrefs = { delay(10_000) },
                awaitPlayer = { delay(10_000) },
                timeoutMs = 30,
            )
            assertEquals(BrandSplashSettle.TimedOut, result)
        }
    }
}
