package com.sosauce.chocola.feature.splash.presentation

import android.os.SystemClock
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sosauce.chocola.core.data.datastore.dataStore
import com.sosauce.chocola.core.designsystem.theme.SystemUiController
import com.sosauce.chocola.core.designsystem.theme.isEffectiveDark
import com.sosauce.chocola.core.designsystem.theme.shouldApplyOledBlack
import com.sosauce.chocola.core.presentation.preferences.rememberAppTheme
import com.sosauce.chocola.core.presentation.preferences.rememberOledBlack
import com.sosauce.chocola.feature.splash.BrandSplashAwaiter
import com.sosauce.chocola.feature.splash.BrandSplashGate
import com.sosauce.chocola.feature.splash.BrandSplashHandoff
import com.sosauce.chocola.feature.splash.BrandSplashLayout
import com.sosauce.chocola.feature.splash.BrandSplashMotion
import com.sosauce.chocola.feature.splash.BrandSplashPhase
import com.sosauce.chocola.feature.splash.BrandSplashSession
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/** Visual stage of the in-app splash while [BrandSplashPhase.Visible]. */
private enum class SplashStage { WaitingHandoff, Growing, Formed, Exiting }

@Composable
fun BrandSplashHost(
    hasMusicPermission: Boolean,
    isControllerConnected: StateFlow<Boolean>,
    handoff: BrandSplashHandoff,
    onLeaveSystemSplash: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val controllerConnected by isControllerConnected.collectAsStateWithLifecycle()
    var phase by remember {
        mutableStateOf(
            BrandSplashGate.decideInitial(
                hasMusicPermission = hasMusicPermission,
                sessionAlreadyCompleted = BrandSplashSession.completed,
                initReady = false,
            ).phase
        )
    }
    val animate = BrandSplashMotion.shouldAnimate(rememberAnimatorDurationScale())
    val grow = remember { Animatable(0f) }
    val chrome = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }
    var stage by remember { mutableStateOf(SplashStage.WaitingHandoff) }

    LaunchedEffect(hasMusicPermission, controllerConnected) {
        if (phase == BrandSplashPhase.Finished) return@LaunchedEffect
        if (phase == BrandSplashPhase.Visible) return@LaunchedEffect
        val decision = BrandSplashGate.decideInitial(
            hasMusicPermission = hasMusicPermission,
            sessionAlreadyCompleted = BrandSplashSession.completed,
            initReady = false,
        )
        if (decision.markSessionComplete) {
            BrandSplashSession.markCompleted()
        }
        phase = decision.phase
    }

    LaunchedEffect(phase, hasMusicPermission) {
        if (phase != BrandSplashPhase.Visible) return@LaunchedEffect
        if (!hasMusicPermission) {
            phase = BrandSplashPhase.Hidden
            return@LaunchedEffect
        }
        // Necessary init runs in parallel with the visuals; it keeps its own 8 s cap.
        val settle = async {
            BrandSplashAwaiter.await(
                readPrefs = { context.dataStore.data.first() },
                awaitPlayer = { isControllerConnected.first { it } },
                timeoutMs = BrandSplashLayout.MAX_WAIT_MS,
            )
        }

        // 1. Stay identical to the system frame until the system splash is gone.
        withTimeoutOrNull(BrandSplashMotion.HANDOFF_FALLBACK_MS) {
            snapshotFlow { handoff.released }.first { it }
        }

        // 2. The disc grows into the artwork.
        stage = SplashStage.Growing
        if (animate) {
            grow.animateTo(
                targetValue = 1f,
                animationSpec = tween(BrandSplashMotion.GROW_MS, easing = FastOutSlowInEasing)
            )
        } else {
            grow.snapTo(1f)
        }

        // 3. Fully formed: wordmark and ring fade in; main content starts composing
        //    underneath so it is ready by the time the page fades out.
        stage = SplashStage.Formed
        val formedAt = SystemClock.uptimeMillis()
        if (animate) {
            chrome.animateTo(
                targetValue = 1f,
                animationSpec = tween(BrandSplashMotion.CHROME_FADE_MS, easing = LinearEasing)
            )
        } else {
            chrome.snapTo(1f)
        }

        // 4. Hold at least MIN_HOLD_MS, and until init is ready (max 8 s overall).
        val result = settle.await()
        val remaining = BrandSplashMotion.holdRemainingMs(SystemClock.uptimeMillis() - formedAt)
        if (remaining > 0L) delay(remaining)

        // 5. Fade out to the main content.
        stage = SplashStage.Exiting
        if (animate) {
            overlayAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(BrandSplashMotion.EXIT_FADE_MS, easing = LinearEasing)
            )
        }

        val decision = BrandSplashGate.decideAfterSettle(result)
        if (decision.markSessionComplete) {
            BrandSplashSession.markCompleted()
        }
        phase = decision.phase
    }

    val showOverlay = phase == BrandSplashPhase.Visible && hasMusicPermission
    RestoreSystemBarsAfterSplash(showOverlay = showOverlay)

    // Content keeps the same slot whether or not the overlay is on top, so it is
    // not recreated when the overlay goes away.
    val composeContent = !showOverlay || stage >= SplashStage.Formed
    Box(modifier = Modifier.onGloballyPositioned { onLeaveSystemSplash() }) {
        if (composeContent) {
            content()
        }
        if (showOverlay) {
            BrandSplashScreen(
                modifier = Modifier
                    .graphicsLayer { alpha = overlayAlpha.value }
                    .pointerInput(Unit) {
                        // The page under the splash must not take blind taps.
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    },
                grow = { grow.value },
                chrome = { chrome.value },
                ringActive = stage >= SplashStage.Formed,
                systemIcon = handoff.iconBounds
            )
        }
    }
}

@Composable
private fun RestoreSystemBarsAfterSplash(showOverlay: Boolean) {
    val activity = LocalActivity.current
    val theme by rememberAppTheme()
    val oled by rememberOledBlack()
    val systemDark = isSystemInDarkTheme()
    val isDark = isEffectiveDark(theme, systemDark)
    val applyOled = shouldApplyOledBlack(theme, oled, systemDark)
    LaunchedEffect(showOverlay, isDark, applyOled, activity) {
        if (showOverlay) return@LaunchedEffect
        val window = activity?.window ?: return@LaunchedEffect
        SystemUiController.setSystemBarsColors(window, isLight = !isDark)
        window.decorView.setBackgroundColor(
            if (applyOled) android.graphics.Color.BLACK else android.graphics.Color.TRANSPARENT
        )
    }
}
