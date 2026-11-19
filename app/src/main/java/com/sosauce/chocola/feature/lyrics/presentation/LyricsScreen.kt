@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.sosauce.chocola.feature.lyrics.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sosauce.chocola.R
import com.sosauce.chocola.core.designsystem.components.PlayPauseButton
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.domain.player.PlayerActions
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.chocola.core.designsystem.theme.OledPageBackgroundTheme
import com.sosauce.chocola.core.designsystem.theme.shouldApplyOledBlack
import com.sosauce.chocola.core.presentation.preferences.rememberAppTheme
import com.sosauce.chocola.core.presentation.preferences.rememberIsLandscape
import com.sosauce.chocola.core.presentation.preferences.rememberLyricsShowPhonetic
import com.sosauce.chocola.core.presentation.preferences.rememberLyricsShowTranslation
import com.sosauce.chocola.core.presentation.preferences.rememberOledBlack
import com.sosauce.chocola.core.presentation.util.selfAlignHorizontally
import com.sosauce.chocola.feature.playing.presentation.components.TurntableTheme

@Composable
fun LyricsScreen(
    onNavigateBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
    musicState: MusicState,
    onHandlePlayerActions: (PlayerActions) -> Unit
) {
    TurntableTheme(enabled = true, artworkUri = musicState.track.artUri) {
        LyricsPage(
            onNavigateBack = onNavigateBack,
            onNavigate = onNavigate,
            musicState = musicState,
            onHandlePlayerActions = onHandlePlayerActions
        )
    }
}

/**
 * How much of each part of the lyrics page is shown. [Settled] is the plain
 * page; the turntable page's lyrics layer passes in-between values while the
 * record flies into the header (see VinylLyricsMath).
 */
data class LyricsPageReveal(
    val background: Float = 1f,
    val header: Float = 1f,
    val headerArtwork: Float = 1f,
    val lyrics: Float = 1f,
    val lyricsOffsetDp: Float = 0f,
    val toolbar: Float = 1f
) {
    companion object {
        val Settled = LyricsPageReveal()
    }
}

/**
 * Lyrics page body without its own color theme or backdrop. [LyricsScreen]
 * wraps it in the turntable theme; the turntable page's lyrics layer draws it
 * inside the overlay's theme, over the backdrop it already has.
 */
@Composable
internal fun LyricsPage(
    onNavigateBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
    musicState: MusicState,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    reveal: LyricsPageReveal = LyricsPageReveal.Settled,
    onHeaderArtworkBounds: ((Rect) -> Unit)? = null
) {
    val isLandscape = rememberIsLandscape()
    val artworkSize = if (isLandscape) LyricsLayout.CompactHeaderArtworkDp.dp else LyricsLayout.HeaderArtworkDp.dp
    val fadeHeight = LyricsLayout.FadeHeightDp.dp
    val theme by rememberAppTheme()
    val oledBlack by rememberOledBlack()
    val applyOled = shouldApplyOledBlack(theme, oledBlack, isSystemInDarkTheme())
    var showTranslation by rememberLyricsShowTranslation()
    var showPhonetic by rememberLyricsShowPhonetic()
    val hasTranslation = remember(musicState.lyrics) { musicState.lyrics.any { it.translation != null } }
    val hasPhonetic = remember(musicState.lyrics) { musicState.lyrics.any { it.phonetic != null } }
    val descTranslation = stringResource(R.string.lyrics_translation_desc)
    val descPhonetic = stringResource(R.string.lyrics_phonetic_desc)

      OledPageBackgroundTheme(enabled = applyOled) {
        val oledBackground = MaterialTheme.colorScheme.background
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    // OLED: a true-black page; accents still follow the cover colors.
                    if (applyOled) {
                        Modifier.drawBehind { drawRect(oledBackground, alpha = reveal.background.coerceIn(0f, 1f)) }
                    } else {
                        Modifier
                    }
                )
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = reveal.header }
                    .padding(
                        start = 4.dp,
                        end = LyricsLayout.HorizontalPaddingDp.dp,
                        top = LyricsLayout.HeaderVerticalPaddingDp.dp,
                        bottom = LyricsLayout.HeaderVerticalPaddingDp.dp
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        painter = painterResource(R.drawable.back),
                        contentDescription = stringResource(R.string.close)
                    )
                }
                AsyncImage(
                    model = musicState.track.artUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(artworkSize)
                        .onGloballyPositioned { coordinates ->
                            onHeaderArtworkBounds?.invoke(coordinates.boundsInWindow())
                        }
                        .graphicsLayer { alpha = reveal.headerArtwork }
                        .clip(RoundedCornerShape(LyricsLayout.HeaderArtworkCornerDp.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = musicState.track.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = musicState.track.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (hasTranslation) {
                    FilterChip(
                        selected = showTranslation,
                        onClick = { showTranslation = !showTranslation },
                        label = {
                            Text(
                                text = stringResource(R.string.lyrics_translation_short),
                                modifier = Modifier.semantics {
                                    contentDescription = descTranslation
                                }
                            )
                        }
                    )
                }
                if (hasPhonetic) {
                    Spacer(modifier = Modifier.width(6.dp))
                    FilterChip(
                        selected = showPhonetic,
                        onClick = { showPhonetic = !showPhonetic },
                        label = {
                            Text(
                                text = stringResource(R.string.lyrics_phonetic_short),
                                modifier = Modifier.semantics {
                                    contentDescription = descPhonetic
                                }
                            )
                        }
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = reveal.lyrics
                        translationY = reveal.lyricsOffsetDp.dp.toPx()
                    }
            ) {
                LyricsList(
                    modifier = Modifier
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            // Soft top/bottom edges that work on any background, including OLED black.
                            val fade = (fadeHeight.toPx() / size.height).coerceIn(0f, 0.5f)
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    fade to Color.Black,
                                    1f - fade to Color.Black,
                                    1f to Color.Transparent
                                ),
                                blendMode = BlendMode.DstIn
                            )
                        },
                    musicState = musicState,
                    onHandlePlayerActions = onHandlePlayerActions,
                    horizontalPadding = LyricsLayout.HorizontalPaddingDp.dp,
                    focusCurrentLine = true,
                    emptyLyrics = {
                        DefaultEmptyLyricsScreen(
                            onNavigate = onNavigate,
                            musicState = musicState,
                            onHandlePlayerActions = onHandlePlayerActions
                        )
                    }
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        top = 8.dp,
                        bottom = LyricsLayout.ControlBarBottomGapDp.dp
                    )
                    .graphicsLayer { alpha = reveal.toolbar }
            ) {
                HorizontalFloatingToolbar(
                    expanded = true,
                    colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                    modifier = Modifier.selfAlignHorizontally()
                ) {
                    IconButton(
                        onClick = { onHandlePlayerActions(PlayerActions.SeekToPreviousMusic) },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.skip_previous),
                            contentDescription = stringResource(R.string.previous_song)
                        )
                    }
                    PlayPauseButton(
                        isPlaying = musicState.isPlaying,
                        onHandlePlayerActions = onHandlePlayerActions
                    )
                    IconButton(
                        onClick = { onHandlePlayerActions(PlayerActions.SeekToNextMusic) },
                        shapes = IconButtonDefaults.shapes()
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.skip_next),
                            contentDescription = stringResource(R.string.next_song)
                        )
                    }
                }
            }
        }
      }
}
