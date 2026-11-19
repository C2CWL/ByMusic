package com.sosauce.chocola.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.splashscreen.SplashScreenViewProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.sosauce.chocola.app.navigation.Nav
import com.sosauce.chocola.core.data.library.FoldersClassifier
import com.sosauce.chocola.core.data.local.PlaylistCleanup
import com.sosauce.chocola.core.designsystem.components.SelectOverlayHost
import com.sosauce.chocola.core.designsystem.components.rememberSelectOverlayState
import com.sosauce.chocola.core.designsystem.theme.ChocolaTheme
import com.sosauce.chocola.core.presentation.player.MusicViewModel
import com.sosauce.chocola.core.presentation.util.hasMusicPermission
import com.sosauce.chocola.feature.setup.presentation.SetupScreen
import com.sosauce.chocola.feature.splash.BrandSplashHandoff
import com.sosauce.chocola.feature.splash.SplashIconBounds
import com.sosauce.chocola.feature.splash.presentation.BrandSplashHost
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {

    private val playlistCleanup by inject<PlaylistCleanup>()
    private val foldersClassifier by inject<FoldersClassifier>()

    @Volatile
    private var keepSystemSplash = true

    private val splashHandoff = BrandSplashHandoff()

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        splash.setKeepOnScreenCondition { keepSystemSplash }
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            // Recreated activity: there is no system splash to wait for.
            splashHandoff.released = true
        }
        enableEdgeToEdge()

        setContent {
            val musicViewModel = koinViewModel<MusicViewModel>()

            ChocolaTheme(
                artImageBitmap = musicViewModel.artworkImageBitmap
            ) {

                var canProceedToApp by remember { mutableStateOf(hasMusicPermission()) }

                BrandSplashHost(
                    hasMusicPermission = canProceedToApp,
                    isControllerConnected = musicViewModel.isControllerConnected,
                    handoff = splashHandoff,
                    onLeaveSystemSplash = { keepSystemSplash = false }
                ) {
                    if (canProceedToApp) {

                        val lifecycleOwner = LocalLifecycleOwner.current

                        // don't run cleanup jobs in setup
                        LaunchedEffect(Unit) {
                            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                                launch { playlistCleanup.startCleanup() }
                                launch { foldersClassifier.start() }
                                launch {
                                    // recheck has permissions when app comes back to foreground
                                    canProceedToApp = hasMusicPermission()
                                }
                            }
                        }

                        SelectOverlayHost(state = rememberSelectOverlayState()) {
                            Nav(
                                musicViewModel = musicViewModel
                            )
                        }
                    } else {
                        SetupScreen { canProceedToApp = true }
                    }
                }
            }
        }

        // No default system exit animation: the in-app first frame is identical to
        // the system frame, so the system splash is simply removed on top of it.
        // Registered after setContent, as the compat splash (API < 31) needs the content view.
        splash.setOnExitAnimationListener { provider -> handOffSystemSplash(provider) }
    }

    /**
     * Measure where the system drew the disc so the in-app first frame redraws it
     * at exactly that place, let Compose draw one frame with it, then remove the
     * system splash. Only after removal does the disc start to grow.
     */
    private fun handOffSystemSplash(provider: SplashScreenViewProvider) {
        val iconView = runCatching { provider.iconView }.getOrNull()
        if (iconView != null && iconView.isAttachedToWindow && iconView.width > 0 && iconView.height > 0) {
            val location = IntArray(2)
            iconView.getLocationInWindow(location)
            splashHandoff.iconBounds = SplashIconBounds(
                left = location[0].toFloat(),
                top = location[1].toFloat(),
                width = iconView.width.toFloat(),
                height = iconView.height.toFloat()
            )
        }
        val splashView = provider.view
        splashView.postOnAnimation {
            splashView.postOnAnimation {
                runCatching { provider.remove() }
                splashHandoff.released = true
            }
        }
    }
}