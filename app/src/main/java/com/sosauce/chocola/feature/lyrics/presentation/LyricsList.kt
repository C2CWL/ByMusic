package com.sosauce.chocola.feature.lyrics.presentation

import android.app.SearchManager
import android.content.ClipData
import android.content.Intent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sosauce.chocola.R
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.domain.player.PlayerActions
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.chocola.core.presentation.preferences.rememberLyricsAlignment
import com.sosauce.chocola.core.presentation.preferences.rememberLyricsFontSize
import com.sosauce.chocola.core.presentation.util.toLyricsAlignment
import com.sosauce.nekobites.components.NoXFound
import com.sosauce.nekobites.components.Spacer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LyricsList(
    modifier: Modifier = Modifier,
    musicState: MusicState,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    textShadow: Boolean = false,
    horizontalPadding: Dp = LyricsLayout.HorizontalPaddingDp.dp,
    focusCurrentLine: Boolean = true,
    emptyLyrics: @Composable () -> Unit
) {
    val clipboardManager = LocalClipboard.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val lyricsAlignment by rememberLyricsAlignment()
    val lyricsFontSize by rememberLyricsFontSize()
    val currentLyricIndex by remember(musicState.position, musicState.lyrics) {
        derivedStateOf {
            LyricsLayout.currentLineIndex(
                positionMs = musicState.position,
                timestampsMs = musicState.lyrics.map { it.timestamp }
            )
        }
    }
    val lazyListState = rememberLazyListState()
    var suppressAutoScroll by remember { mutableStateOf(false) }
    var programmaticScroll by remember { mutableStateOf(false) }

    val viewportHeightPx = lazyListState.layoutInfo.viewportSize.height
    val measuredLineHeightPx = lazyListState.layoutInfo.visibleItemsInfo
        .firstOrNull { it.size > 0 }
        ?.size
    val fallbackLineHeightPx = with(density) { (lyricsFontSize.sp * 2.2f).roundToPx() }
    val lineHeightPx = measuredLineHeightPx ?: fallbackLineHeightPx
    val extraTopPx = if (focusCurrentLine) {
        LyricsLayout.leadContentPaddingPx(viewportHeightPx, lineHeightPx)
    } else 0
    val extraBottomPx = if (focusCurrentLine) {
        LyricsLayout.trailContentPaddingPx(viewportHeightPx, lineHeightPx)
    } else 0
    val mergedPadding = PaddingValues(
        start = horizontalPadding,
        end = horizontalPadding,
        top = contentPadding.calculateTopPadding() + with(density) { extraTopPx.toDp() },
        bottom = contentPadding.calculateBottomPadding() + with(density) { extraBottomPx.toDp() }
    )
    val scrollOffset = if (focusCurrentLine) {
        LyricsLayout.focusScrollOffsetPx(viewportHeightPx, lineHeightPx)
    } else 0

    LaunchedEffect(currentLyricIndex, scrollOffset, viewportHeightPx, suppressAutoScroll) {
        if (currentLyricIndex != -1 && !suppressAutoScroll && viewportHeightPx > 0) {
            programmaticScroll = true
            try {
                lazyListState.animateScrollToItem(currentLyricIndex, scrollOffset)
            } finally {
                programmaticScroll = false
            }
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.isScrollInProgress }
            .collect { inProgress ->
                if (inProgress && !programmaticScroll) {
                    suppressAutoScroll = true
                }
            }
    }

    LaunchedEffect(suppressAutoScroll) {
        if (suppressAutoScroll) {
            delay(LyricsLayout.AutoScrollResumeMs)
            suppressAutoScroll = false
        }
    }

    DisposableEffect(Unit) {
        val window = activity?.window

        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally,
        state = lazyListState,
        contentPadding = mergedPadding
    ) {
        if (musicState.lyrics.isEmpty()) {
            item(key = "empty-lyrics") { emptyLyrics() }
        } else {
            itemsIndexed(
                items = musicState.lyrics,
                key = { _, lyric -> lyric.id }
            ) { index, lyric ->

                val isCurrentLyric = index == currentLyricIndex

                val distance = if (currentLyricIndex < 0) 2 else kotlin.math.abs(index - currentLyricIndex)
                val color by animateColorAsState(
                    targetValue = when (distance) {
                        0 -> MaterialTheme.colorScheme.primary
                        1 -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
                val lineAlpha = when (distance) {
                    0 -> 1f
                    1 -> 0.72f
                    else -> 0.38f
                }
                val animatedScale by animateFloatAsState(
                    if (isCurrentLyric) 1.04f else 1f
                )
                Column(
                    modifier = Modifier
                        .animateItem()
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .combinedClickable(
                            onClick = {
                                onHandlePlayerActions(
                                    PlayerActions.SeekToSlider(
                                        lyric.timestamp.toLong()
                                    )
                                )
                            },
                            onLongClick = {
                                scope.launch {
                                    clipboardManager.setClipEntry(
                                        ClipEntry(
                                            ClipData.newPlainText(
                                                "Lyrics",
                                                lyric.lineLyrics
                                            )
                                        )
                                    )
                                }
                            }
                        )
                ) {
                    Text(
                        text = lyric.lineLyrics,
                        softWrap = true,
                        maxLines = Int.MAX_VALUE,
                        overflow = TextOverflow.Clip,
                        style = MaterialTheme.typography.titleLargeEmphasized.copy(
                            color = color,
                            fontSize = lyricsFontSize.sp,
                            lineHeight = (lyricsFontSize * 1.4f).sp,
                            textAlign = lyricsAlignment.toLyricsAlignment(),
                            shadow = if (textShadow) Shadow(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                offset = Offset(10f, 5f),
                                blurRadius = 10f
                            ) else null
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                alpha = lineAlpha
                                scaleX = animatedScale
                                scaleY = animatedScale
                            }
                    )
                }
            }
        }
    }
}

@Composable
fun DefaultEmptyLyricsScreen(
    musicState: MusicState,
    onNavigate: (Screen) -> Unit,
    onHandlePlayerActions: (PlayerActions) -> Unit
) {
    val context = LocalContext.current
    val resources = LocalResources.current

    val lyricFilePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->

            if (uri == null) return@rememberLauncherForActivityResult

            if (!uri.toString().endsWith(".lrc")) {
                Toast.makeText(
                    context,
                    resources.getString(R.string.not_a_lyric_file),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                onHandlePlayerActions(PlayerActions.LoadLyrics(uri))
            }
        }


    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = LyricsLayout.HorizontalPaddingDp.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        NoXFound(
            headlineText = R.string.no_lyrics_note,
            bodyText = R.string.no_lyrics_desc,
            icon = R.drawable.lyrics_filled
        )
        Spacer(10.dp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            FilledIconButton(
                onClick = {
                    val query =
                        "${musicState.track.title} ${musicState.track.artist} lyrics"
                    val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                        putExtra(SearchManager.QUERY, query)
                    }
                    context.startActivity(intent)
                },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide))
            ) {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null
                )
            }
            FilledIconButton(
                onClick = {
                    onNavigate(
                        Screen.MetadataEditor(
                            trackPath = musicState.track.path,
                            trackUri = musicState.track.uri.toString()
                        )
                    )
                },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide))
            ) {
                Icon(
                    painter = painterResource(R.drawable.edit_filled),
                    contentDescription = null
                )
            }
            FilledIconButton(
                onClick = { lyricFilePicker.launch(arrayOf("*/*")) },
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(IconButtonDefaults.mediumContainerSize(IconButtonDefaults.IconButtonWidthOption.Wide))
            ) {
                Icon(
                    painter = painterResource(R.drawable.resource_import),
                    contentDescription = null
                )
            }
        }
    }
}
