package com.sosauce.chocola.feature.online

import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.ExperimentalMaterial3Api
import top.yukonga.miuix.kmp.basic.Button
import androidx.compose.material3.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import androidx.compose.material3.MaterialTheme
import top.yukonga.miuix.kmp.basic.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import com.sosauce.chocola.core.designsystem.components.LocalGlassBackdrop
import com.sosauce.chocola.core.designsystem.components.glassBackdropSource
import com.sosauce.chocola.core.designsystem.components.rememberGlassBackdrop
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.await
import coil3.compose.AsyncImage
import com.sosauce.chocola.R
import com.sosauce.chocola.core.data.download.OnlineDownloadWorker
import com.sosauce.chocola.core.domain.model.CuteTrack
import com.sosauce.chocola.core.domain.online.OnlineLyricsProvider
import com.sosauce.chocola.core.domain.online.OnlineMediaId
import com.sosauce.chocola.core.domain.online.QualityRequest
import com.sosauce.chocola.core.domain.player.MusicState
import com.sosauce.chocola.core.presentation.components.MiniPlayerBarHost
import com.sosauce.chocola.feature.recognition.domain.RecognitionPluginInfo
import com.sosauce.chocola.feature.recognition.presentation.RecognitionEntryButton
import com.sosauce.chocola.feature.recognition.presentation.RecognitionEntrySection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun SharedTransitionScope.OnlineMusicScreen(
    state: OnlineSearchUiState,
    onlineLyrics: OnlineLyricsProvider,
    onNavigateBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onRetry: () -> Unit,
    onSelectPlugin: (String) -> Unit,
    onLoadMore: () -> Unit,
    onPlay: (track: CuteTrack, queue: List<CuteTrack>) -> Unit,
    musicState: MusicState,
    onManagePlugins: () -> Unit,
    recognitionPlugins: List<RecognitionPluginInfo> = emptyList(),
    onStartRecognition: () -> Unit = {},
    onOpenRecognitionHistory: () -> Unit = {}
) {
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    val selected = state.selected
    val submittedQuery = state.submittedQuery
    val canSearch = selected != null && state.query.isNotBlank() &&
        !(state.isLoading && state.query.trim() == submittedQuery)
    val submit = {
        if (canSearch) {
            focus.clearFocus()
            onSearch()
        }
    }
    // Liquid-glass mini player samples the result list scrolling under it.
    val glassBackdrop = rememberGlassBackdrop()
    val layoutDirection = LocalLayoutDirection.current
    CompositionLocalProvider(LocalGlassBackdrop provides glassBackdrop) {
    Scaffold(
        containerColor = colors.surfaceContainerLow,
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.online_music),
                color = colors.surfaceContainerLow,
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(painterResource(R.drawable.back), stringResource(R.string.online_back))
                    }
                },
                actions = {
                    RecognitionEntryButton(recognitionPlugins, onStartRecognition)
                    IconButton(onClick = onManagePlugins) {
                        Icon(painterResource(R.drawable.extension), stringResource(R.string.online_manage_plugins))
                    }
                }
            )
        },
        bottomBar = {
            MiniPlayerBarHost(musicState = musicState)
        }
    ) { padding ->
        // Bottom inset goes into contentPadding so results scroll under the glass bar.
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .glassBackdropSource(glassBackdrop, baseColor = colors.surfaceContainerLow)
                .padding(
                    top = padding.calculateTopPadding(),
                    start = padding.calculateStartPadding(layoutDirection),
                    end = padding.calculateEndPadding(layoutDirection)
                ),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.Top
        ) {
            item {
                Text(
                    stringResource(R.string.online_music),
                    modifier = Modifier.padding(start = 4.dp, bottom = 16.dp).semantics { heading() },
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            when {
                state.isLoadingPlugins -> item { SearchProgress() }
                selected == null -> item {
                    OnlineNotice(
                        title = stringResource(
                            if (state.hasAnyPlugin) R.string.online_no_search_source else R.string.online_no_plugin_title
                        ),
                        body = stringResource(
                            if (state.hasAnyPlugin) R.string.online_no_search_source_body else R.string.online_setup_body
                        )
                    ) {
                        Button(onClick = onManagePlugins) {
                            Text(
                                stringResource(
                                    if (state.hasAnyPlugin) R.string.online_manage_plugins else R.string.online_go_import
                                )
                            )
                        }
                    }
                }
                else -> {
                    item {
                        SearchPanel(
                            query = state.query,
                            selected = selected,
                            plugins = state.plugins,
                            onQueryChange = onQueryChange,
                            onSelectPlugin = { id ->
                                focus.clearFocus()
                                onSelectPlugin(id)
                            },
                            canSearch = canSearch,
                            onSearch = submit
                        )
                    }
                    if (submittedQuery != null) {
                        item {
                            Text(
                                stringResource(R.string.online_results_for, submittedQuery),
                                modifier = Modifier.padding(start = 4.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        itemsIndexed(state.results, key = { _, track -> track.mediaId }) { index, track ->
                            TrackRow(
                                track = track,
                                onlineLyrics = onlineLyrics,
                                first = index == 0,
                                last = index == state.results.lastIndex,
                                onClick = {
                                    focus.clearFocus()
                                    onPlay(track, state.results)
                                }
                            )
                        }
                        when {
                            state.isLoading -> item { SearchProgress() }
                            state.error != null -> item {
                                Box(Modifier.padding(top = if (state.results.isEmpty()) 0.dp else 8.dp)) {
                                    OnlineNotice(stringResource(R.string.online_search_failed), state.error) {
                                        Button(onClick = onRetry) {
                                            Text(stringResource(R.string.online_retry))
                                        }
                                    }
                                }
                            }
                            state.results.isEmpty() && state.isEnd -> item {
                                OnlineNotice(stringResource(R.string.online_no_results), stringResource(R.string.online_no_results_body))
                            }
                            !state.isEnd -> item {
                                Button(onClick = onLoadMore, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                                    Text(stringResource(R.string.online_load_more))
                                }
                            }
                        }
                    }
                }
            }
            item {
                RecognitionEntrySection(
                    plugins = recognitionPlugins,
                    onStart = onStartRecognition,
                    onOpenHistory = onOpenRecognitionHistory,
                    modifier = Modifier.padding(top = 16.dp)
                )
            }
            item {
                Text(
                    stringResource(if (selected == null) R.string.plugins_statement else R.string.online_footer),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 16.dp)
                )
            }
        }
    }
    }
}

@Composable
private fun SearchPanel(
    query: String,
    selected: SearchablePlugin,
    plugins: List<SearchablePlugin>,
    onQueryChange: (String) -> Unit,
    onSelectPlugin: (String) -> Unit,
    canSearch: Boolean,
    onSearch: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val searchLabel = stringResource(R.string.online_search_placeholder)
    var expanded by remember { mutableStateOf(false) }
    Surface(color = colors.surface, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = searchLabel
                },
                decorationBox = { inner ->
                    Row(
                        Modifier.clip(RoundedCornerShape(16.dp)).background(colors.surfaceContainer).heightIn(min = 52.dp)
                            .padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(painterResource(R.drawable.search), null, tint = colors.onSurfaceVariant)
                        Box(Modifier.weight(1f)) {
                            if (query.isEmpty()) {
                                Text(
                                    stringResource(R.string.online_search_placeholder),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurfaceVariant
                                )
                            }
                            inner()
                        }
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(painterResource(R.drawable.close), stringResource(R.string.online_clear_query))
                            }
                        } else {
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                }
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    if (plugins.size > 1) {
                        TextButton(onClick = { expanded = true }) {
                            Text(
                                stringResource(R.string.online_search_source, selected.name),
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(painterResource(R.drawable.arrow_down), null, modifier = Modifier.padding(start = 4.dp).size(16.dp))
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            plugins.forEach { plugin ->
                                DropdownMenuItem(
                                    text = { Text(plugin.name) },
                                    trailingIcon = if (plugin.id == selected.id) {
                                        { Icon(painterResource(R.drawable.check_circle), null, tint = colors.primary) }
                                    } else null,
                                    modifier = Modifier.semantics { this.selected = plugin.id == selected.id },
                                    onClick = {
                                        expanded = false
                                        onSelectPlugin(plugin.id)
                                    }
                                )
                            }
                        }
                    } else {
                        Text(
                            stringResource(R.string.online_search_source, selected.name),
                            modifier = Modifier.padding(start = 8.dp),
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Button(onClick = onSearch, enabled = canSearch) { Text(stringResource(R.string.online_search)) }
            }
        }
    }
}

@Composable
private fun OnlineNotice(title: String, body: String, action: @Composable () -> Unit = {}) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            action()
        }
    }
}

@Composable
private fun SearchProgress() {
    Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun TrackRow(track: CuteTrack, onlineLyrics: OnlineLyricsProvider, first: Boolean, last: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val top = if (first) 20.dp else 0.dp
    val bottom = if (last) 20.dp else 0.dp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom))
            .background(colors.surface)
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.music_note), null, tint = colors.onSurfaceVariant)
                if (track.artUri != null) {
                    AsyncImage(
                        model = track.artUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = listOf(track.artist, track.album).filter { it.isNotBlank() }.joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (track.durationMillis > 0) {
                Spacer(Modifier.width(8.dp))
                val total = track.durationMillis / 1000
                Text(
                    "%d:%02d".format(total / 60, total % 60),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant
                )
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val id = remember(track.mediaId) { OnlineMediaId.parse(track.mediaId) }
            if (id != null) TrackDownloadButton(track, id, onlineLyrics)
        }
    }
}

@Composable
private fun TrackDownloadButton(track: CuteTrack, id: OnlineMediaId, onlineLyrics: OnlineLyricsProvider) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workFlow = remember(context, id) { OnlineDownloadWorker.observe(context, id) }
    val work by workFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var enqueuing by remember(id) { mutableStateOf(false) }
    val active = enqueuing || work.any { !it.state.isFinished }
    // WorkManager keeps SUCCEEDED after the file is deleted. Only treat it as saved
    // when the URI written by the worker can still be opened.
    val saved = work.any { info ->
        info.state == WorkInfo.State.SUCCEEDED && savedUriStillExists(context, info)
    }
    val failed = work.any { it.state == WorkInfo.State.FAILED }

    val label = when {
        active -> R.string.online_download_active
        saved -> R.string.online_download_saved
        failed -> R.string.online_download_retry
        else -> R.string.online_download_action
    }
    if (failed && !active && !saved) {
        val error = work.firstOrNull { it.state == WorkInfo.State.FAILED }
            ?.outputData?.getString(OnlineDownloadWorker.KEY_ERROR)
        Text(
            text = stringResource(when (error) {
                "no_verified_audio" -> R.string.online_download_no_audio
                "timed_out" -> R.string.online_download_timeout
                else -> R.string.online_download_failed
            }),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error
        )
    }
    IconButton(
        enabled = !active && !saved,
        onClick = {
            enqueuing = true
            scope.launch {
                try {
                    val lyrics = runCatching { onlineLyrics.lyrics(id)?.raw }.getOrNull()
                    OnlineDownloadWorker.enqueue(
                        context, id, track.title, track.durationMillis.takeIf { it > 0 },
                        QualityRequest.autoFromTop(),
                        artist = track.artist,
                        album = track.album,
                        artUri = track.artUri,
                        lyrics = lyrics
                    ).await()
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    Toast.makeText(context, R.string.online_download_enqueue_failed, Toast.LENGTH_SHORT).show()
                } finally {
                    enqueuing = false
                }
            }
        }
    ) {
        if (active) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp).semantics { contentDescription = context.getString(label) },
                strokeWidth = 2.dp
            )
        } else {
            Icon(
                painter = painterResource(if (saved) R.drawable.check_circle else R.drawable.down),
                contentDescription = stringResource(label),
                tint = if (failed && !saved) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun savedUriStillExists(context: Context, info: WorkInfo): Boolean {
    val raw = info.outputData.getString(OnlineDownloadWorker.KEY_SAVED_URI) ?: return false
    val uri = runCatching { Uri.parse(raw) }.getOrNull()?.takeUnless { it == Uri.EMPTY } ?: return false
    return runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { true } == true
    }.getOrDefault(false)
}
