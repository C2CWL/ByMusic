package com.sosauce.chocola.core.designsystem.theme

import android.view.Window
import androidx.core.view.WindowCompat

object SystemUiController {
    fun setSystemBarsColors(
        window: Window,
        isLight: Boolean
    ) {
        WindowCompat
            .getInsetsController(window, window.decorView)
            .apply {
                isAppearanceLightStatusBars = isLight
                isAppearanceLightNavigationBars = isLight
            }
    }

}