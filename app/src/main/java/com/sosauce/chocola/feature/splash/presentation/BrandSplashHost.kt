package com.sosauce.chocola.feature.splash.presentation

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import com.sosauce.chocola.feature.splash.BrandSplashLayout
import com.sosauce.chocola.feature.splash.BrandSplashPhase
import com.sosauce.chocola.feature.splash.BrandSplashSession
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first

@Composable
fun BrandSplashHost(
    hasMusicPermission: Boolean,
    isControllerConnected: StateFlow<Boolean>,
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
        val settle = BrandSplashAwaiter.await(
            readPrefs = { context.dataStore.data.first() },
            awaitPlayer = { isControllerConnected.first { it } },
            timeoutMs = BrandSplashLayout.MAX_WAIT_MS,
        )
        val decision = BrandSplashGate.decideAfterSettle(settle)
        if (decision.markSessionComplete) {
            BrandSplashSession.markCompleted()
        }
        phase = decision.phase
    }

    val showOverlay = phase == BrandSplashPhase.Visible && hasMusicPermission
    RestoreSystemBarsAfterSplash(showOverlay = showOverlay)

    val releaseSystemSplash = Modifier.onGloballyPositioned { onLeaveSystemSplash() }
    if (showOverlay) {
        BrandSplashScreen(modifier = releaseSystemSplash)
    } else {
        androidx.compose.foundation.layout.Box(modifier = releaseSystemSplash) {
            content()
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
