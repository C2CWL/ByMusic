@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.sosauce.chocola.feature.lyrics.presentation

import androidx.compose.foundation.background
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
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sosauce.chocola.R
import com.sosauce.chocola.core.designsystem.components.PlayPauseButton
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.domain.player.PlayerActions
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.chocola.core.presentation.preferences.rememberIsLandscape
import com.sosauce.chocola.core.presentation.util.selfAlignHorizontally
import com.sosauce.chocola.feature.playing.presentation.components.TurntableTheme

@Composable
fun LyricsScreen(
    onNavigateBack: () -> Unit,
    onNavigate: (Screen) -> Unit,
    musicState: MusicState,
    onHandlePlayerActions: (PlayerActions) -> Unit
) {
    val isLandscape = rememberIsLandscape()
    val artworkSize = if (isLandscape) LyricsLayout.CompactHeaderArtworkDp.dp else LyricsLayout.HeaderArtworkDp.dp
    val fadeHeight = LyricsLayout.FadeHeightDp.dp

    TurntableTheme(enabled = true, artworkUri = musicState.track.artUri) {
        val surface = MaterialTheme.colorScheme.surface
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                        .clip(RoundedCornerShape(8.dp))
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
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                LyricsList(
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
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(fadeHeight)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(surface.copy(alpha = 0.45f), Color.Transparent)
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(fadeHeight)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, surface.copy(alpha = 0.55f))
                            )
                        )
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
