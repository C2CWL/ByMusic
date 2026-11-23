package com.sosauce.chocola.core.designsystem.components

import androidx.compose.runtime.compositionLocalOf
import com.skydoves.cloudy.Sky

/** Backdrop source shared by the root navigation content and the mini player overlay. */
val LocalMiniPlayerSky = compositionLocalOf<Sky?> { null }
