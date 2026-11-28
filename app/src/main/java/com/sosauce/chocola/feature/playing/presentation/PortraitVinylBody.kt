package com.sosauce.chocola.feature.playing.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sosauce.chocola.R
import com.sosauce.chocola.core.designsystem.NowPlayingButton
import com.sosauce.chocola.core.designsystem.components.CuteSlider
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.domain.player.PlayerActions
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.chocola.core.presentation.player.LyricsPositionClock
import com.sosauce.chocola.core.presentation.preferences.rememberShowAlbumName
import com.sosauce.chocola.feature.lyrics.presentation.LyricsKaraoke
import com.sosauce.chocola.feature.playing.presentation.components.Artwork
import com.sosauce.chocola.feature.playing.presentation.components.LocalOpenVinylLyrics
import java.util.Locale
import kotlin.math.floor

private val audioExtensions = setOf(
    "mp3", "flac", "wav", "ogg", "opus", "m4a", "aac", "alac", "aiff", "wma", "ape", "amr"
)

@Composable
internal fun PortraitVinylBody(
    paddingValues: PaddingValues,
    musicState: MusicState,
    hiddenButtons: Set<NowPlayingButton>,
    onAction: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit,
    onClose: () -> Unit,
    onShowSpeedCard: () -> Unit
) {
    val path = musicState.track.path
    val format = remember(path) {
        path.substringAfterLast('.', "").lowercase(Locale.ROOT)
            .takeIf { it in audioExtensions }?.uppercase(Locale.ROOT)
    }
    val openLyrics = LocalOpenVinylLyrics.current ?: { onNavigate(Screen.Lyrics) }
    val lyricsEnabled = NowPlayingButton.LYRICS !in hiddenButtons
    val showAlbum by rememberShowAlbumName()
    val colors = MaterialTheme.colorScheme
    val sidePadding = Modifier.padding(horizontal = 20.dp)

    val actionBar: @Composable (List<List<NowPlayingButton>>, Modifier) -> Unit = { groups, modifier ->
        VinylControlBar(
            musicState = musicState,
            hiddenButtons = hiddenButtons,
            onHandlePlayerActions = onAction,
            onNavigate = onNavigate,
            onShrinkToSearchbar = onClose,
            onShowSpeedCard = onShowSpeedCard,
            modifier = modifier,
            groups = groups,
            plain = true
        )
    }
    Column(Modifier.fillMaxSize().padding(paddingValues).padding(bottom = 8.dp)) {
        Box(Modifier.fillMaxWidth().height(48.dp).then(sidePadding)) {
            if (NowPlayingButton.CLOSE !in hiddenButtons) {
                VinylIcon(NowPlayingButton.CLOSE, onClose, Modifier.align(Alignment.CenterStart))
            }
            if (lyricsEnabled) {
                Row(
                    Modifier.align(Alignment.Center)
                        .size(48.dp)
                        .clickable(
                            role = Role.Button,
                            onClickLabel = stringResource(R.string.lyrics),
                            onClick = openLyrics
                        ),
                    horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(12.dp, 4.dp).background(colors.onSurface, CircleShape))
                    Box(Modifier.size(4.dp).background(colors.onSurfaceVariant, CircleShape))
                }
            }
            val visibleTop = VinylLayout.PORTRAIT_TOP_ACTIONS.single().count { it !in hiddenButtons }
            if (visibleTop > 0) {
                actionBar(
                    VinylLayout.PORTRAIT_TOP_ACTIONS,
                    Modifier.align(Alignment.CenterEnd).size((48 * visibleTop).dp, 48.dp)
                )
            }
        }

        Box(
            Modifier.fillMaxWidth().weight(8f),
            contentAlignment = Alignment.BottomStart
        ) {
            Artwork(musicState = musicState, onHandlePlayerActions = onAction)
        }

        Column(Modifier.fillMaxWidth().then(sidePadding)) {
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = musicState.track.title,
                    modifier = Modifier.weight(1f).basicMarquee(),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1
                )
                val favoriteId = musicState.track.mediaId.toLongOrNull()
                if (favoriteId != null && NowPlayingButton.FAVORITE !in hiddenButtons) {
                    val favorite = musicState.track.isFavorite
                    IconButton(onClick = { onAction(PlayerActions.ToggleFavorite(favoriteId)) }) {
                        Icon(
                            painterResource(if (favorite) R.drawable.favorite_filled else R.drawable.favorite),
                            stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite),
                            tint = if (favorite) colors.primary else colors.onSurfaceVariant
                        )
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val track = musicState.track
                Text(
                    text = if (showAlbum && track.album.isNotBlank()) "${track.artist} · ${track.album}" else track.artist,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (format != null) {
                    Text(
                        text = format,
                        modifier = Modifier.background(colors.surfaceContainerHigh, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant
                    )
                }
            }
            if (lyricsEnabled) {
                VinylLyricPreview(musicState, openLyrics)
            }
        }

        Spacer(Modifier.weight(1.3f))
        Column(Modifier.fillMaxWidth().then(sidePadding)) {
            if (VinylLayout.PORTRAIT_QUICK_ACTIONS.single().any { it !in hiddenButtons }) {
                actionBar(VinylLayout.PORTRAIT_QUICK_ACTIONS, Modifier)
            }
            CuteSlider(musicState, onAction, compact = true)
            Spacer(Modifier.height(4.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val controlSlot = Modifier.weight(1f)
                Box(controlSlot, contentAlignment = Alignment.Center) {
                    if (NowPlayingButton.REPEAT !in hiddenButtons) actionBar(VinylLayout.PORTRAIT_REPEAT, Modifier)
                }
                Box(controlSlot, contentAlignment = Alignment.Center) {
                    if (NowPlayingButton.PREVIOUS !in hiddenButtons) {
                        VinylIcon(NowPlayingButton.PREVIOUS, { onAction(PlayerActions.SeekToPreviousMusic) })
                    }
                }
                FilledIconButton(
                    modifier = Modifier.size(64.dp),
                    onClick = { onAction(PlayerActions.PlayOrPause) },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = colors.primary,
                        contentColor = colors.onPrimary
                    )
                ) {
                    Icon(
                        painterResource(if (musicState.isPlaying) R.drawable.widget_pause else R.drawable.widget_play),
                        stringResource(
                            if (musicState.isPlaying) R.string.vinyl_pause else R.string.play
                        ),
                        modifier = Modifier.size(32.dp)
                    )
                }
                Box(controlSlot, contentAlignment = Alignment.Center) {
                    if (NowPlayingButton.NEXT !in hiddenButtons) {
                        VinylIcon(NowPlayingButton.NEXT, { onAction(PlayerActions.SeekToNextMusic) })
                    }
                }
                Box(controlSlot, contentAlignment = Alignment.Center) {
                    if (NowPlayingButton.QUEUE !in hiddenButtons) actionBar(VinylLayout.PORTRAIT_QUEUE, Modifier)
                }
            }
        }
    }
}

@Composable
private fun VinylIcon(button: NowPlayingButton, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier) {
        Icon(painterResource(button.icon), stringResource(button.label), tint = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun VinylLyricPreview(musicState: MusicState, onOpenLyrics: () -> Unit) {
    val lyrics = musicState.lyrics
    // The player already samples every 50 ms, enough for character-by-character color changes.
    val timestamps = remember(lyrics) { lyrics.map { it.timestamp } }
    val line = lyrics.getOrNull(LyricsPositionClock.currentLineIndex(musicState.position, timestamps))
    val text = line?.lineLyrics?.takeIf { it.isNotBlank() } ?: musicState.track.album
    val ranges = remember(line) { line?.let { LyricsKaraoke.wordCharRanges(it.lineLyrics, it.words) }.orEmpty() }
    val highlightUntil = when {
        line == null || line.lineLyrics.isBlank() -> 0
        !LyricsKaraoke.canFill(ranges) -> text.length
        else -> floor(LyricsKaraoke.filledChars(text.length, line.words, ranges, musicState.position)).toInt()
    }
    val colors = MaterialTheme.colorScheme
    val preview = remember(text, highlightUntil, colors.primary) {
        buildAnnotatedString {
            append(text)
            if (highlightUntil > 0) addStyle(SpanStyle(color = colors.primary), 0, highlightUntil)
        }
    }
    Text(
        text = preview,
        modifier = Modifier.fillMaxWidth()
            .then(if (text.isNotBlank()) Modifier.clickable(
                role = Role.Button,
                onClickLabel = stringResource(R.string.lyrics),
                onClick = onOpenLyrics
            ) else Modifier)
            .padding(vertical = 10.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
