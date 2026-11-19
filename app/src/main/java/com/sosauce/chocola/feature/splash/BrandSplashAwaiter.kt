package com.sosauce.chocola.feature.splash

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout

/**
 * Waits for necessary startup work only: preference store first read and
 * player/session connection. Does not wait for a full library scan or network,
 * and does not impose a minimum on-screen time.
 */
object BrandSplashAwaiter {

    suspend fun await(
        readPrefs: suspend () -> Unit,
        awaitPlayer: suspend () -> Unit,
        timeoutMs: Long = BrandSplashLayout.MAX_WAIT_MS,
    ): BrandSplashSettle {
        return try {
            withTimeout(timeoutMs) {
                coroutineScope {
                    val prefs = async { readPrefs() }
                    val player = async { awaitPlayer() }
                    prefs.await()
                    player.await()
                }
            }
            BrandSplashSettle.Ready
        } catch (_: TimeoutCancellationException) {
            BrandSplashSettle.TimedOut
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            BrandSplashSettle.Failed
        }
    }
}
