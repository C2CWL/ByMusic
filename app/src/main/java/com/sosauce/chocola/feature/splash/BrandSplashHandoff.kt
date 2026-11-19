package com.sosauce.chocola.feature.splash

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Hand-off from the system splash to the in-app splash.
 *
 * [iconBounds] is the measured system icon view (window pixels) so the in-app
 * first frame redraws the disc exactly there. [released] flips once the system
 * splash view has been removed; only then does the disc start to grow.
 */
@Stable
class BrandSplashHandoff {
    var iconBounds by mutableStateOf<SplashIconBounds?>(null)
    var released by mutableStateOf(false)
}
