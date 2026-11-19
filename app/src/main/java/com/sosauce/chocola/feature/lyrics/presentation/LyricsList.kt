package com.sosauce.chocola.feature.lyrics.presentation

import android.app.SearchManager
import android.content.ClipData
import android.content.Intent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sosauce.chocola.R
import com.sosauce.chocola.core.data.lyrics.LyricsSidecar
import com.sosauce.chocola.core.domain.model.LyricWord
import com.sosauce.chocola.core.domain.model.Lyrics
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.domain.player.PlayerActions
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.chocola.core.presentation.player.LyricsPositionClock
import com.sosauce.chocola.core.presentation.preferences.rememberLyricsAlignment
import com.sosauce.chocola.core.presentation.preferences.rememberLyricsFontSize
import com.sosauce.chocola.core.presentation.preferences.rememberLyricsShowPhonetic
import com.sosauce.chocola.core.presentation.preferences.rememberLyricsShowTranslation
import com.sosauce.chocola.core.presentation.util.toLyricsAlignment
import com.sosauce.nekobites.components.NoXFound
import com.sosauce.nekobites.components.Spacer
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Lyrics list shared by the lyrics page and the lyrics shown over the artwork / vinyl.
 *
 * Playback position comes from [rememberFramePositionMs]: it advances on every frame
 * and is only read in derivedStateOf (current line) and in draw code (word fill),
 * so the 50 ms player samples do not recompose the visible lines.
 */
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
    val positionMs = rememberFramePositionMs(
        sampleMs = musicState.position,
        isPlaying = musicState.isPlaying,
        speed = musicState.speed
    )
    // Stable wrappers so the content below is skipped when only the position changes.
    val latestActions by rememberUpdatedState(onHandlePlayerActions)
    val latestEmpty by rememberUpdatedState(emptyLyrics)
    val stableActions = remember { { action: PlayerActions -> latestActions(action) } }
    val stableEmpty = remember { @Composable { latestEmpty() } }

    LyricsListContent(
        modifier = modifier,
        lyrics = musicState.lyrics,
        positionMs = positionMs,
        onHandlePlayerActions = stableActions,
        contentPadding = contentPadding,
        textShadow = textShadow,
        horizontalPadding = horizontalPadding,
        focusCurrentLine = focusCurrentLine,
        emptyLyrics = stableEmpty
    )
}

@Composable
private fun LyricsListContent(
    modifier: Modifier,
    lyrics: List<Lyrics>,
    positionMs: LongState,
    onHandlePlayerActions: (PlayerActions) -> Unit,
    contentPadding: PaddingValues,
    textShadow: Boolean,
    horizontalPadding: Dp,
    focusCurrentLine: Boolean,
    emptyLyrics: @Composable () -> Unit
) {
    val clipboardManager = LocalClipboard.current
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val lyricsAlignment by rememberLyricsAlignment()
    val lyricsFontSize by rememberLyricsFontSize()
    val showTranslation by rememberLyricsShowTranslation()
    val showPhonetic by rememberLyricsShowPhonetic()
    val textAlign = lyricsAlignment.toLyricsAlignment()
    val timestamps = remember(lyrics) { lyrics.map { it.timestamp } }
    val currentLyricIndex by remember(timestamps) {
        derivedStateOf {
            LyricsPositionClock.currentLineIndex(
                positionMs = positionMs.longValue,
                timestampsMs = timestamps
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
        if (lyrics.isEmpty()) {
            item(key = "empty-lyrics") { emptyLyrics() }
        } else {
            itemsIndexed(
                items = lyrics,
                key = { _, lyric -> lyric.id }
            ) { index, lyric ->
                LyricLine(
                    lyric = lyric,
                    distance = if (currentLyricIndex < 0) LyricsLineStyle.FarDistance else abs(index - currentLyricIndex),
                    passed = currentLyricIndex >= 0 && index < currentLyricIndex,
                    positionMs = positionMs,
                    fontSizeSp = lyricsFontSize,
                    textAlign = textAlign,
                    onArtwork = textShadow,
                    showTranslation = showTranslation,
                    showPhonetic = showPhonetic,
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
                    },
                    modifier = Modifier.animateItem()
                )
            }
        }
    }
}

/**
 * One lyric line. Changes between lines are plain eased fades and a size change
 * without overshoot; nothing bounces, floats or glows.
 */
@Composable
private fun LyricLine(
    lyric: Lyrics,
    distance: Int,
    passed: Boolean,
    positionMs: LongState,
    fontSizeSp: Int,
    textAlign: TextAlign,
    onArtwork: Boolean,
    showTranslation: Boolean,
    showPhonetic: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCurrent = distance == 0
    val transition = tween<Float>(LyricsLineStyle.TransitionMs)
    val alpha by animateFloatAsState(
        targetValue = LyricsLineStyle.alphaFor(distance, passed, onArtwork),
        animationSpec = transition,
        label = "lyricAlpha"
    )
    val scale by animateFloatAsState(
        targetValue = if (isCurrent) 1f else LyricsLineStyle.inactiveScale(onArtwork),
        animationSpec = transition,
        label = "lyricScale"
    )
    val blurRadius by animateDpAsState(
        targetValue = LyricsLineStyle.blurFor(distance, onArtwork).dp,
        animationSpec = tween(LyricsLineStyle.TransitionMs),
        label = "lyricBlur"
    )
    val pivotX = when (textAlign) {
        TextAlign.Center -> 0.5f
        TextAlign.End, TextAlign.Right -> 1f
        else -> 0f
    }
    val contentColor = MaterialTheme.colorScheme.onSurface
    val mainSize = fontSizeSp * LyricsLineStyle.currentSizeFactor(onArtwork)
    val mainStyle = MaterialTheme.typography.titleLargeEmphasized.copy(
        fontSize = mainSize.sp,
        lineHeight = (mainSize * 1.3f).sp,
        textAlign = textAlign
    )
    val shadow = if (onArtwork) Shadow(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        offset = Offset(10f, 5f),
        blurRadius = 10f
    ) else null
    val ranges = remember(lyric) { LyricsKaraoke.wordCharRanges(lyric.lineLyrics, lyric.words) }
    val wordFill = isCurrent && lyric.hasWordTiming && LyricsKaraoke.canFill(ranges)
    val extraStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = (fontSizeSp * LyricsLineStyle.ExtraSizeFactor).sp,
        lineHeight = (fontSizeSp * LyricsLineStyle.ExtraSizeFactor * 1.35f).sp,
        textAlign = textAlign,
        color = contentColor.copy(alpha = LyricsLineStyle.ExtraAlpha),
        shadow = shadow
    )
    val phonetic = lyric.phonetic?.takeIf { showPhonetic }
    val translation = lyric.translation?.takeIf { showTranslation }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp)
            .graphicsLayer {
                this.alpha = alpha
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(pivotX, 0.5f)
            }
            .then(
                if (blurRadius > 0.dp) Modifier.blur(blurRadius, BlurredEdgeTreatment.Unbounded)
                else Modifier
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = lyric.lineLyrics,
                softWrap = true,
                maxLines = Int.MAX_VALUE,
                overflow = TextOverflow.Clip,
                style = mainStyle.copy(
                    color = if (wordFill) contentColor.copy(alpha = LyricsLineStyle.UnsungAlpha) else contentColor,
                    shadow = shadow
                ),
                modifier = Modifier.fillMaxWidth()
            )
            if (wordFill) {
                WordFillOverlay(
                    text = lyric.lineLyrics,
                    style = mainStyle.copy(color = contentColor),
                    words = lyric.words,
                    ranges = ranges,
                    positionMs = positionMs
                )
            }
        }
        if (phonetic != null) {
            Text(text = phonetic, style = extraStyle, modifier = Modifier.fillMaxWidth())
        }
        if (translation != null) {
            Text(text = translation, style = extraStyle, modifier = Modifier.fillMaxWidth())
        }
    }
}

/**
 * The sung part of the current line: the same text drawn bright and masked from the
 * left up to the fill position, with a short soft edge. Only the draw phase reads
 * the frame clock, so this redraws every frame without recomposing.
 */
@Composable
private fun WordFillOverlay(
    text: String,
    style: TextStyle,
    words: List<LyricWord>,
    ranges: List<IntRange?>,
    positionMs: LongState
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val edgePx = with(LocalDensity.current) { LyricsLineStyle.FillEdgeDp.dp.toPx() }
    Text(
        text = text,
        softWrap = true,
        maxLines = Int.MAX_VALUE,
        overflow = TextOverflow.Clip,
        style = style,
        onTextLayout = { layout = it },
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                val result = layout ?: return@drawWithContent
                val filled = LyricsKaraoke.filledChars(text.length, words, ranges, positionMs.longValue)
                if (filled <= 0f) return@drawWithContent
                drawContent()
                if (filled >= text.length) return@drawWithContent
                val charIndex = filled.toInt().coerceIn(0, text.length - 1)
                val box = result.getBoundingBox(charIndex)
                val fillX = box.left + box.width * (filled - charIndex)
                val fillLine = result.getLineForOffset(charIndex)
                val lastLine = result.lineCount - 1
                for (line in 0..lastLine) {
                    if (line < fillLine) continue
                    val top = if (line == 0) 0f else result.getLineTop(line)
                    val bottom = if (line == lastLine) size.height else result.getLineBottom(line)
                    val bandTopLeft = Offset(0f, top)
                    val bandSize = Size(size.width, (bottom - top).coerceAtLeast(0f))
                    if (line > fillLine) {
                        drawRect(
                            color = Color.Black,
                            topLeft = bandTopLeft,
                            size = bandSize,
                            blendMode = BlendMode.Clear
                        )
                    } else {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Black, Color.Transparent),
                                startX = fillX - edgePx / 2f,
                                endX = fillX + edgePx / 2f
                            ),
                            topLeft = bandTopLeft,
                            size = bandSize,
                            blendMode = BlendMode.DstIn
                        )
                    }
                }
            }
    )
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

            if (!LyricsSidecar.isSupportedLyricsName(uri.toString())) {
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
