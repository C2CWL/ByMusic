package com.sosauce.chocola.feature.playing.presentation.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import coil3.compose.AsyncImage
import com.skydoves.cloudy.cloudy
import com.sosauce.chocola.R
import com.sosauce.chocola.core.domain.player.MusicState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
internal fun TurntableDisc(
    musicState: MusicState,
    blur: Int,
) {
    val accent = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val metal = MaterialTheme.colorScheme.secondary
    val rotation = remember { Animatable(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(musicState.isPlaying, lifecycle) {
        // Stop producing frames when the host lifecycle leaves the foreground.
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            if (musicState.isPlaying) {
                while (true) {
                    val start = rotation.value % 360f
                    rotation.snapTo(start)
                    rotation.animateTo(start + 360f, tween(20000, easing = LinearEasing))
                }
            }
        }
    }
    val armAngle by animateFloatAsState(
        targetValue = if (musicState.isPlaying) 0f else -19f,
        animationSpec = tween(650),
        label = "Tonearm lift",
    )
    // A fixed seed keeps the star field steady across playback-position updates.
    val stars =
        remember {
            val random = Random(73)
            List(180) {
                floatArrayOf(
                    random.nextFloat() * 6.283185f,
                    0.58f + random.nextFloat() * 0.37f,
                    0.35f + random.nextFloat() * 0.8f,
                    0.08f + random.nextFloat() * 0.34f,
                )
            }
        }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width * 0.46f, size.height * 0.52f)
            drawCircle(
                brush =
                    Brush.radialGradient(
                        listOf(accent.copy(alpha = 0.19f), Color.Transparent),
                        center = center,
                        radius = size.minDimension * 0.53f,
                    ),
                radius = size.minDimension * 0.53f,
                center = center,
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize(0.91f)
                    .align(Alignment.CenterStart)
                    .graphicsLayer { rotationZ = rotation.value }
                    .clip(CircleShape)
                    .drawWithCache {
                        val radius = size.minDimension / 2f
                        val base = lerp(surface, accent, 0.24f)
                        val vinyl =
                            Brush.radialGradient(
                                0f to surface,
                                0.49f to base,
                                0.64f to lerp(surface, accent, 0.48f),
                                0.85f to base,
                                1f to surface,
                                radius = radius,
                            )
                        val sheen =
                            Brush.sweepGradient(
                                listOf(
                                    Color.Transparent,
                                    accent.copy(alpha = 0.23f),
                                    Color.Transparent,
                                    Color.Transparent,
                                    accent.copy(alpha = 0.16f),
                                    Color.Transparent,
                                ),
                            )
                        onDrawBehind {
                            drawCircle(vinyl)
                            drawCircle(sheen)
                            for (index in 0..64) {
                                drawCircle(
                                    color = accent.copy(alpha = if (index % 5 == 0) 0.16f else 0.065f),
                                    radius = radius * (0.555f + index * 0.0065f),
                                    style = Stroke(width = if (index % 5 == 0) 0.65.dp.toPx() else 0.35.dp.toPx()),
                                )
                            }
                            stars.forEachIndexed { index, star ->
                                val point =
                                    center +
                                        Offset(
                                            cos(star[0]) * radius * star[1],
                                            sin(star[0]) * radius * star[1],
                                        )
                                val color = lerp(accent, Color.White, 0.65f).copy(alpha = star[3])
                                val dot = star[2] * 0.7.dp.toPx()
                                drawCircle(color, dot, point)
                                if (index % 19 == 0) {
                                    drawLine(
                                        color.copy(alpha = 0.22f),
                                        point - Offset(dot * 3f, 0f),
                                        point + Offset(dot * 3f, 0f),
                                        0.5.dp.toPx(),
                                    )
                                    drawLine(
                                        color.copy(alpha = 0.22f),
                                        point - Offset(0f, dot * 3f),
                                        point + Offset(0f, dot * 3f),
                                        0.5.dp.toPx(),
                                    )
                                }
                            }
                            drawCircle(accent.copy(alpha = 0.2f), radius * 0.99f, style = Stroke(0.7.dp.toPx()))
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(0.55f).clip(CircleShape).background(surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.fillMaxSize(0.35f),
                )
                AsyncImage(
                    model = musicState.track.artUri,
                    contentDescription = stringResource(R.string.artwork),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().cloudy(radius = blur, enabled = blur > 0),
                )
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.minDimension
            val pivot = Offset(size.width * 0.88f, size.height * 0.13f)
            rotate(armAngle, pivot) {
                val arm =
                    Path().apply {
                        moveTo(pivot.x, pivot.y)
                        cubicTo(unit * 0.87f, unit * 0.36f, unit * 0.82f, unit * 0.61f, unit * 0.69f, unit * 0.74f)
                    }
                drawPath(arm, surface.copy(alpha = 0.7f), style = Stroke(unit * 0.023f, cap = StrokeCap.Round))
                drawPath(arm, lerp(surface, metal, 0.6f), style = Stroke(unit * 0.013f, cap = StrokeCap.Round))
                drawPath(arm, metal.copy(alpha = 0.55f), style = Stroke(unit * 0.003f, cap = StrokeCap.Round))
                rotate(39f, Offset(unit * 0.69f, unit * 0.74f)) {
                    drawRoundRect(
                        brush = Brush.linearGradient(listOf(metal, lerp(surface, metal, 0.5f))),
                        topLeft = Offset(unit * 0.661f, unit * 0.702f),
                        size = Size(unit * 0.058f, unit * 0.12f),
                        cornerRadius =
                            androidx.compose.ui.geometry
                                .CornerRadius(unit * 0.012f),
                    )
                    drawLine(metal, Offset(unit * 0.68f, unit * 0.822f), Offset(unit * 0.68f, unit * 0.84f), unit * 0.005f)
                }
                drawCircle(surface, unit * 0.066f, pivot)
                drawCircle(
                    Brush.radialGradient(listOf(metal, lerp(surface, metal, 0.6f)), center = pivot, radius = unit * 0.05f),
                    unit * 0.05f,
                    pivot,
                )
                drawCircle(metal.copy(alpha = 0.45f), unit * 0.024f, pivot, style = Stroke(unit * 0.002f))
            }
        }
    }
}
