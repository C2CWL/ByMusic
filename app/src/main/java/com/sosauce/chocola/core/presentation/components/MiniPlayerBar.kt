package com.sosauce.chocola.core.presentation.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sosauce.chocola.R
import com.sosauce.chocola.core.designsystem.components.LocalGlassBackdrop
import com.sosauce.chocola.core.designsystem.components.PlayPauseButton
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.domain.player.PlayerActions
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.chocola.feature.playing.presentation.expand.LocalPlayerDockEntry
import com.sosauce.chocola.feature.playing.presentation.expand.LocalPlayerSheet
import com.sosauce.chocola.feature.playing.presentation.expand.PlayerDock
import com.sosauce.chocola.feature.playing.presentation.expand.SheetRect

internal object MiniPlayerDefaults {
    const val CORNER_DP = 28f
    const val HEIGHT_DP = 64f
    const val ELEVATION_DP = 6f
    const val ART_CORNER_DP = 10f
    const val GLASS_ART_DP = 48f
    const val FLOATING_ART_DP = 42f
    const val FLOATING_HEIGHT_DP = 54f
}

@Composable
internal fun MiniPlayerFace(
    musicState: MusicState,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier.padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.size(MiniPlayerDefaults.GLASS_ART_DP.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = musicState.track.title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = musicState.track.artist,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        PlayPauseButton(
            isPlaying = musicState.isPlaying,
            onHandlePlayerActions = onHandlePlayerActions
        )

        IconButton(onClick = { onNavigate(Screen.Queue) }) {
            Icon(painterResource(R.drawable.queue), stringResource(R.string.queue))
        }
    }
}

@Composable
internal fun MiniPlayerBarHost(
    musicState: MusicState,
    modifier: Modifier = Modifier
) {
    if (!musicState.isPlayerReady) return
    PlayerDockPlaceholder(
        modifier = modifier.fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .navigationBarsPadding()
            .height(MiniPlayerDefaults.HEIGHT_DP.dp),
        glass = true,
        bottomCornerDp = MiniPlayerDefaults.CORNER_DP
    )
}

@Composable
internal fun PlayerDockPlaceholder(
    modifier: Modifier,
    glass: Boolean,
    bottomCornerDp: Float,
    dragDown: ((Float) -> Unit)? = null,
    stopDragDown: ((Boolean) -> Unit)? = null
) {
    val sheet = LocalPlayerSheet.current
    val entry = LocalPlayerDockEntry.current
    val backdrop = LocalGlassBackdrop.current
    val measured = remember(entry) { mutableStateOf<SheetRect?>(null) }
    val register: (SheetRect) -> Unit = { bounds ->
        if (sheet != null && entry != null) {
            sheet.register(entry, PlayerDock(bounds, glass, backdrop, bottomCornerDp, dragDown, stopDragDown))
        }
    }
    DisposableEffect(sheet, entry) {
        onDispose { if (entry != null) sheet?.unregister(entry) }
    }
    // Refresh style or backdrop even when the location itself did not change.
    SideEffect { measured.value?.let(register) }
    Box(modifier.onGloballyPositioned { coordinates ->
        val origin = coordinates.localToWindow(Offset.Zero)
        if (origin.x.isFinite() && origin.y.isFinite() &&
            coordinates.size.width > 0 && coordinates.size.height > 0
        ) {
            val bounds = SheetRect(
                origin.x, origin.y,
                origin.x + coordinates.size.width,
                origin.y + coordinates.size.height
            )
            measured.value = bounds
            register(bounds)
        }
    })
}
