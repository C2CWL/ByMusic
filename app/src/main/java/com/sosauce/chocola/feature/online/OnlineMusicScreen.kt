package com.sosauce.chocola.feature.online

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import androidx.work.await
import coil3.compose.AsyncImage
import com.sosauce.chocola.R
import com.sosauce.chocola.core.data.download.OnlineDownloadWorker
import com.sosauce.chocola.core.domain.model.CuteTrack
import com.sosauce.chocola.core.domain.online.OnlineMediaId
import com.sosauce.chocola.core.domain.online.QualityRequest
import com.sosauce.chocola.feature.recognition.domain.RecognitionPluginInfo
import com.sosauce.chocola.feature.recognition.presentation.RecognitionEntryButton
import com.sosauce.chocola.feature.recognition.presentation.RecognitionEntrySection
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 在线音乐. Without an imported plugin it only explains how to import one (mockup 02) and
 * makes no request. With plugins it shows a search box and one chip per imported plugin
 * (platform names come only from the plugins themselves, mockup 01/03).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineMusicScreen(
    state: OnlineSearchUiState,
    onNavigateBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectPlugin: (String) -> Unit,
    onLoadMore: () -> Unit,
    onPlay: (track: CuteTrack, queue: List<CuteTrack>) -> Unit,
    onManagePlugins: () -> Unit,
    recognitionPlugins: List<RecognitionPluginInfo> = emptyList(),
    onStartRecognition: () -> Unit = {},
    onOpenRecognitionHistory: () -> Unit = {}
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.online_music)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(painterResource(R.drawable.back), stringResource(R.string.close))
                    }
                },
                actions = {
                    // Hidden until an imported plugin declares song recognition.
                    RecognitionEntryButton(recognitionPlugins, onStartRecognition)
                    IconButton(onClick = onManagePlugins) {
                        Icon(painterResource(R.drawable.extension), stringResource(R.string.online_manage_plugins))
                    }
                }
            )
        }
    ) { padding ->
        if (state.plugins.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(painterResource(R.drawable.cloud), null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.online_no_plugin_title),
                    modifier = Modifier.padding(top = 16.dp),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stringResource(R.string.online_no_plugin_body),
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.plugins_statement),
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FilledTonalButton(onClick = onManagePlugins, modifier = Modifier.padding(top = 20.dp)) {
                    Text(stringResource(R.string.online_go_import))
                }
                RecognitionEntrySection(
                    plugins = recognitionPlugins,
                    onStart = onStartRecognition,
                    onOpenHistory = onOpenRecognitionHistory,
                    modifier = Modifier.padding(top = 24.dp)
                )
            }
        } else {
            SearchContent(
                state = state,
                modifier = Modifier.fillMaxSize().padding(padding),
                onQueryChange = onQueryChange,
                onSearch = onSearch,
                onSelectPlugin = onSelectPlugin,
                onLoadMore = onLoadMore,
                onPlay = onPlay,
                onManagePlugins = onManagePlugins,
                recognitionPlugins = recognitionPlugins,
                onStartRecognition = onStartRecognition,
                onOpenRecognitionHistory = onOpenRecognitionHistory
            )
        }
    }
}

@Composable
private fun SearchContent(
    state: OnlineSearchUiState,
    modifier: Modifier,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectPlugin: (String) -> Unit,
    onLoadMore: () -> Unit,
    onPlay: (CuteTrack, List<CuteTrack>) -> Unit,
    onManagePlugins: () -> Unit,
    recognitionPlugins: List<RecognitionPluginInfo>,
    onStartRecognition: () -> Unit,
    onOpenRecognitionHistory: () -> Unit
) {
    val focus = LocalFocusManager.current
    val selected = state.selected
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        item {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                leadingIcon = { Icon(painterResource(R.drawable.search), null) },
                placeholder = { Text(stringResource(R.string.online_search_hint, selected?.name.orEmpty())) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focus.clearFocus()
                    onSearch()
                }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
        item {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.plugins.forEach { plugin ->
                    FilterChip(
                        selected = plugin.id == selected?.id,
                        onClick = { onSelectPlugin(plugin.id) },
                        label = { Text(plugin.name) }
                    )
                }
                AssistChip(onClick = onManagePlugins, label = { Text(stringResource(R.string.online_manage_plugins)) })
            }
        }
        item {
            RecognitionEntrySection(
                plugins = recognitionPlugins,
                onStart = onStartRecognition,
                onOpenHistory = onOpenRecognitionHistory,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        state.error?.let { message ->
            item {
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        if (state.searched && !state.isLoading && state.error == null && state.results.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.online_no_results),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
        items(state.results, key = { it.mediaId }) { track ->
            TrackRow(track = track, onClick = { onPlay(track, state.results) })
        }
        item {
            Box(Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                when {
                    state.isLoading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    !state.isEnd && state.results.isNotEmpty() ->
                        TextButton(onClick = onLoadMore) { Text(stringResource(R.string.online_load_more)) }
                }
            }
        }
        item {
            Text(
                stringResource(R.string.online_footer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 96.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun TrackRow(track: CuteTrack, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                if (track.artUri != null) {
                    AsyncImage(
                        model = track.artUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(painterResource(R.drawable.music_note), null, tint = colors.onSurfaceVariant)
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
            if (id != null) TrackDownloadButton(track, id)
        }
    }
}

@Composable
private fun TrackDownloadButton(track: CuteTrack, id: OnlineMediaId) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val workFlow = remember(context, id) { OnlineDownloadWorker.observe(context, id) }
    val work by workFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    var enqueuing by remember(id) { mutableStateOf(false) }
    val active = enqueuing || work.any { !it.state.isFinished }
    val saved = work.any { it.state == WorkInfo.State.SUCCEEDED }
    val failed = work.any { it.state == WorkInfo.State.FAILED }

    val label = when {
        active -> R.string.online_download_active
        saved -> R.string.online_download_saved
        failed -> R.string.online_download_retry
        else -> R.string.online_download_action
    }
    IconButton(
        enabled = !active && !saved,
        onClick = {
            enqueuing = true
            scope.launch {
                try {
                    OnlineDownloadWorker.enqueue(
                        context, id, track.title, track.durationMillis.takeIf { it > 0 },
                        QualityRequest.autoFromTop()
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
