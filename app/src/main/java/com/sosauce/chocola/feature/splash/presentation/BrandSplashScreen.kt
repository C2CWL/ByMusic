package com.sosauce.chocola.feature.splash.presentation

import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.em
import androidx.compose.material3.Text
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.currentStateAsState
import com.sosauce.chocola.R
import com.sosauce.chocola.core.designsystem.theme.SystemUiController
import com.sosauce.chocola.feature.splash.BrandSplashGate
import com.sosauce.chocola.feature.splash.BrandSplashLayout
import kotlin.math.roundToInt

private val cormorantGaramond = FontFamily(
    Font(resId = R.font.cormorant_garamond, weight = FontWeight.Normal)
)

@Composable
fun BrandSplashScreen(
    modifier: Modifier = Modifier,
) {
    val activity = LocalActivity.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animatorScale = rememberAnimatorDurationScale()
    val animateRing = BrandSplashGate.shouldAnimateRing(animatorScale) &&
        lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            SystemUiController.setSystemBarsColors(window, isLight = false)
            window.decorView.setBackgroundColor(android.graphics.Color.BLACK)
        }
        onDispose { }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .semantics { contentDescription = "ByMusic" }
    ) {
        Image(
            painter = painterResource(R.drawable.splash_vinyl_bg),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopStart
        )
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            val density = LocalDensity.current
            val widthPx = with(density) { maxWidth.toPx() }
            val heightPx = with(density) { maxHeight.toPx() }
            val scale = (widthPx / BrandSplashLayout.PREVIEW_WIDTH_PX).coerceIn(0.85f, 1.25f)
            val fontPx = BrandSplashLayout.WORDMARK_FONT_PX_AT_1080 * scale
            val fontSp = with(density) { fontPx.toSp() }
            val ringRadius = with(density) {
                (BrandSplashLayout.RING_RADIUS_PX_AT_1080 * scale).toDp()
            }
            val ringStrokePx = (BrandSplashLayout.RING_STROKE_PX_AT_1080 * scale)
                .coerceAtLeast(BrandSplashLayout.RING_STROKE_MIN_PX)

            var baselineY = heightPx * BrandSplashLayout.WORDMARK_BASELINE_FRACTION
            var ringCenterY = heightPx * BrandSplashLayout.RING_CENTER_FRACTION
            val ringBottom = ringCenterY + with(density) { ringRadius.toPx() }
            if (ringBottom > heightPx) {
                val shift = ringBottom - heightPx
                baselineY -= shift
                ringCenterY -= shift
            }
            baselineY = baselineY.coerceAtLeast(fontPx)
            ringCenterY = ringCenterY.coerceAtLeast(baselineY + fontPx)

            Text(
                text = "ByMusic",
                color = BrandSplashLayout.wordmarkColor,
                fontSize = fontSp,
                fontFamily = cormorantGaramond,
                fontWeight = FontWeight.Normal,
                letterSpacing = BrandSplashLayout.WORDMARK_TRACKING_EM.em,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        IntOffset(0, (baselineY - fontPx * 0.82f).roundToInt())
                    }
            )
            BrandSplashRing(
                animate = animateRing,
                radius = ringRadius,
                strokePx = ringStrokePx,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        IntOffset(
                            0,
                            (ringCenterY - with(density) { ringRadius.toPx() }).roundToInt()
                        )
                    }
            )
        }
    }
}

@Composable
private fun BrandSplashRing(
    animate: Boolean,
    radius: Dp,
    strokePx: Float,
    modifier: Modifier = Modifier,
) {
    val rotation = if (animate) {
        val transition = rememberInfiniteTransition(label = "brand-splash-ring")
        val angle by transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = BrandSplashLayout.RING_ROTATION_MS,
                    easing = LinearEasing
                )
            ),
            label = "brand-splash-ring-angle"
        )
        angle
    } else {
        0f
    }

    val diameter = radius * 2
    Canvas(modifier = modifier.size(diameter)) {
        val inset = strokePx / 2f
        val arcSize = Size(size.width - strokePx, size.height - strokePx)
        val topLeft = Offset(inset, inset)
        drawArc(
            color = BrandSplashLayout.ringDimColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx)
        )
        drawArc(
            color = BrandSplashLayout.ringArcColor,
            startAngle = BrandSplashLayout.RING_ARC_START_DEG + rotation,
            sweepAngle = BrandSplashLayout.RING_ARC_SWEEP_DEG,
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = strokePx, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun rememberAnimatorDurationScale(): Float {
    val context = LocalContext.current
    return remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
    }
}
