package com.sosauce.chocola.feature.recognition.presentation

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import top.yukonga.miuix.kmp.basic.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.sosauce.chocola.R
import com.sosauce.chocola.feature.recognition.domain.FailureReason
import com.sosauce.chocola.feature.recognition.domain.PluginErrorKind
import com.sosauce.chocola.feature.recognition.domain.RecognitionDefaults
import com.sosauce.chocola.feature.recognition.domain.RecognitionPluginInfo
import com.sosauce.chocola.feature.recognition.domain.RecognitionState
import com.sosauce.chocola.feature.recognition.domain.RecognizedSong

private val DEFAULT_SECONDS = (RecognitionDefaults.DEFAULT_RECORD_MS / 1000).toInt()

private fun Context.hasMicPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

/**
 * Wires the view model to the screen. Recording only ever starts from a user tap
 * ([autoStart] is set when the user tapped "开始识别" on the entry), and is cancelled as
 * soon as the screen leaves the foreground.
 */
@Composable
fun RecognitionRoute(
    viewModel: RecognitionViewModel,
    autoStart: Boolean,
    onNavigateBack: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val plugins by viewModel.plugins.collectAsStateWithLifecycle()
    var permissionRequestInFlight by rememberSaveable { mutableStateOf(false) }
    var autoStartConsumed by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionRequestInFlight = false
        viewModel.onPermissionResult(granted)
    }

    val start = { viewModel.start(permissionGranted = context.hasMicPermission()) }

    LaunchedEffect(Unit) {
        if (autoStart && !autoStartConsumed) {
            autoStartConsumed = true
            start()
        }
    }

    // Foreground only: leaving the screen (home button, screen off) stops the microphone.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        if (viewModel.state.value is RecognitionState.Recording) viewModel.cancel()
    }

    RecognitionScreen(
        state = state,
        plugins = plugins,
        showPermissionRationale = state is RecognitionState.PermissionRationale &&
            !permissionRequestInFlight,
        onNavigateBack = {
            viewModel.cancel()
            onNavigateBack()
        },
        onOpenHistory = {
            // Leaving for the history page stops any recording or pending identification.
            viewModel.cancel()
            onOpenHistory()
        },
        onStart = start,
        onCancel = viewModel::cancel,
        onDone = viewModel::reset,
        onSelectPlugin = viewModel::selectPlugin,
        onAllowPermission = {
            permissionRequestInFlight = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        },
        onDenyPermission = viewModel::onRationaleDismissed,
        onOpenSystemSettings = {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                "package:${context.packageName}".toUri()
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(intent) }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecognitionScreen(
    state: RecognitionState,
    plugins: List<RecognitionPluginInfo>,
    showPermissionRationale: Boolean,
    onNavigateBack: () -> Unit,
    onOpenHistory: () -> Unit,
    onStart: () -> Unit,
    onCancel: () -> Unit,
    onDone: () -> Unit,
    onSelectPlugin: (String) -> Unit,
    onAllowPermission: () -> Unit,
    onDenyPermission: () -> Unit,
    onOpenSystemSettings: () -> Unit
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = stringResource(R.string.recognition_title),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(painterResource(R.drawable.back), stringResource(R.string.recognition_back))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            painterResource(R.drawable.time_filled),
                            stringResource(R.string.recognition_history)
                        )
                    }
                }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = state,
            contentKey = { it::class },
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            label = "recognitionState"
        ) { current ->
            when (current) {
                RecognitionState.Idle,
                is RecognitionState.PermissionRationale -> IdleContent(
                    plugins = plugins,
                    selectedId = (current as? RecognitionState.PermissionRationale)?.pluginId,
                    onStart = onStart,
                    onSelectPlugin = onSelectPlugin
                )

                RecognitionState.NoPlugin -> StatusContent(
                    icon = R.drawable.mic,
                    title = stringResource(R.string.recognition_no_plugin_title),
                    lines = listOf(stringResource(R.string.recognition_no_plugin_body)),
                    primaryLabel = stringResource(R.string.recognition_back),
                    onPrimary = onNavigateBack
                )

                RecognitionState.PermissionDenied -> StatusContent(
                    icon = R.drawable.mic,
                    title = stringResource(R.string.recognition_permission_denied_title),
                    lines = listOf(stringResource(R.string.recognition_permission_denied_body)),
                    primaryLabel = stringResource(R.string.recognition_open_settings),
                    onPrimary = onOpenSystemSettings,
                    secondaryLabel = stringResource(R.string.recognition_back),
                    onSecondary = onDone
                )

                is RecognitionState.Recording -> RecordingContent(
                    state = current,
                    plugins = plugins,
                    onSelectPlugin = onSelectPlugin,
                    onCancel = onCancel
                )

                is RecognitionState.Identifying -> IdentifyingContent(
                    pluginName = plugins.nameOf(current.pluginId),
                    onCancel = onCancel
                )

                is RecognitionState.Success -> ResultContent(
                    song = current.song,
                    onAgain = onStart,
                    onDone = onNavigateBack
                )

                is RecognitionState.Failure -> FailureContent(
                    state = current,
                    plugins = plugins,
                    onBack = onDone,
                    onRetry = onStart,
                    onSwitchPlugin = { id ->
                        onSelectPlugin(id)
                        onStart()
                    }
                )
            }
        }
    }

    if (showPermissionRationale) {
        PermissionRationaleDialog(onAllow = onAllowPermission, onDeny = onDenyPermission)
    }
}

private fun List<RecognitionPluginInfo>.nameOf(id: String): String =
    firstOrNull { it.id == id }?.name ?: id

@Composable
private fun PermissionRationaleDialog(onAllow: () -> Unit, onDeny: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDeny,
        icon = { StatusBadge(icon = R.drawable.mic, size = 56.dp) },
        title = { Text(stringResource(R.string.recognition_permission_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.recognition_permission_body_record, DEFAULT_SECONDS))
                Text(stringResource(R.string.recognition_permission_body_plugin))
                Text(stringResource(R.string.recognition_permission_body_settings))
            }
        },
        confirmButton = {
            Button(onClick = onAllow) { Text(stringResource(R.string.recognition_permission_allow)) }
        },
        dismissButton = {
            TextButton(onClick = onDeny) { Text(stringResource(R.string.recognition_permission_deny)) }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginPicker(
    plugins: List<RecognitionPluginInfo>,
    selectedId: String?,
    onSelectPlugin: (String) -> Unit
) {
    if (plugins.isEmpty()) return
    val effective = selectedId ?: plugins.first().id
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.recognition_use_plugin),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            plugins.forEach { plugin ->
                val selected = plugin.id == effective
                FilterChip(
                    selected = selected,
                    onClick = { onSelectPlugin(plugin.id) },
                    label = { Text(plugin.name) },
                    leadingIcon = if (selected) {
                        { Icon(painterResource(R.drawable.check), null, Modifier.size(18.dp)) }
                    } else {
                        null
                    }
                )
            }
        }
    }
}

@Composable
private fun IdleContent(
    plugins: List<RecognitionPluginInfo>,
    selectedId: String?,
    onStart: () -> Unit,
    onSelectPlugin: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        StatusBadge(icon = R.drawable.mic)
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.recognition_idle_body, DEFAULT_SECONDS),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onStart) {
            Icon(painterResource(R.drawable.mic), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.recognition_start))
        }
        Spacer(Modifier.weight(1f))
        PluginPicker(plugins, selectedId, onSelectPlugin)
    }
}

@Composable
private fun RecordingContent(
    state: RecognitionState.Recording,
    plugins: List<RecognitionPluginInfo>,
    onSelectPlugin: (String) -> Unit,
    onCancel: () -> Unit
) {
    val seconds = (state.totalMs / 1000).toInt()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))
        CountdownRing(
            progress = state.progress,
            secondsLeft = state.remainingSeconds,
            caption = stringResource(R.string.recognition_seconds_left, state.remainingSeconds)
        )
        Spacer(Modifier.height(24.dp))
        RecognitionWaveform(levels = state.levels, modifier = Modifier.padding(horizontal = 24.dp))
        Spacer(Modifier.height(24.dp))
        Text(stringResource(R.string.recognition_listening), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.recognition_listening_hint, seconds),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.weight(1f))
        PluginPicker(plugins, state.pluginId, onSelectPlugin)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onCancel, modifier = Modifier.width(180.dp)) {
            Icon(painterResource(R.drawable.close), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.recognition_cancel))
        }
    }
}

@Composable
private fun IdentifyingContent(pluginName: String, onCancel: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = recognitionCardBorder()
            ) {}
            CircularProgressIndicator(modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(24.dp))
        StatusTexts(
            stringResource(R.string.recognition_identifying),
            stringResource(R.string.recognition_identifying_with, pluginName)
        )
        Spacer(Modifier.height(24.dp))
        RecognitionCard {
            Text(
                text = stringResource(R.string.recognition_identifying_note),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.weight(1f))
        OutlinedButton(onClick = onCancel, modifier = Modifier.width(180.dp)) {
            Icon(painterResource(R.drawable.close), null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.recognition_cancel))
        }
    }
}

@Composable
private fun ResultContent(song: RecognizedSong, onAgain: () -> Unit, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SongCover(artworkUrl = song.artworkUrl)
        Spacer(Modifier.height(20.dp))
        Text(song.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            song.artist,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        val meta = listOfNotNull(
            song.album?.takeIf { it.isNotBlank() }?.let { stringResource(R.string.recognition_album, it) },
            song.durationMs?.takeIf { it > 0 }?.let(::formatDuration)
        ).joinToString(" · ")
        if (meta.isNotEmpty()) {
            Text(
                meta,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(8.dp))
        AssistChip(
            onClick = {},
            enabled = false,
            label = { Text(stringResource(R.string.recognition_from_plugin, song.sourcePluginName)) },
            leadingIcon = { Icon(painterResource(R.drawable.mic), null, Modifier.size(16.dp)) }
        )
        Spacer(Modifier.height(16.dp))
        // The three follow-up actions need the library matcher and the music-source plugin
        // runtime; they are shown disabled until those land in a later PR.
        RecognitionCard {
            RecognitionRow(
                icon = R.drawable.library,
                title = stringResource(R.string.recognition_action_local),
                subtitle = stringResource(R.string.recognition_action_later),
                enabled = false,
                showDivider = true
            )
            RecognitionRow(
                icon = R.drawable.search,
                title = stringResource(R.string.recognition_action_source),
                subtitle = stringResource(R.string.recognition_action_later),
                enabled = false,
                showDivider = true
            )
            RecognitionRow(
                icon = R.drawable.add_to_queue,
                title = stringResource(R.string.recognition_action_queue),
                subtitle = stringResource(R.string.recognition_action_later),
                enabled = false
            )
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onAgain) {
                Icon(painterResource(R.drawable.mic), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.recognition_again))
            }
            TextButton(onClick = onDone) { Text(stringResource(R.string.recognition_done)) }
        }
    }
}

@Composable
private fun SongCover(artworkUrl: String?) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier = Modifier
            .size(188.dp)
            .clip(shape),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            shape = shape,
            color = MaterialTheme.colorScheme.primaryContainer,
            border = recognitionCardBorder()
        ) {}
        Icon(
            painter = painterResource(R.drawable.music_note),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(72.dp)
        )
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun FailureContent(
    state: RecognitionState.Failure,
    plugins: List<RecognitionPluginInfo>,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSwitchPlugin: (String) -> Unit
) {
    val pluginName = plugins.nameOf(state.pluginId)
    val (icon, title, lines) = when (val reason = state.reason) {
        FailureReason.NoMatch -> Triple(
            R.drawable.search,
            stringResource(R.string.recognition_fail_nomatch_title),
            listOf(stringResource(R.string.recognition_fail_nomatch_body))
        )

        FailureReason.Network -> Triple(
            R.drawable.cloud,
            stringResource(R.string.recognition_fail_network_title),
            listOf(stringResource(R.string.recognition_fail_network_body))
        )

        is FailureReason.Plugin -> Triple(
            R.drawable.info_rounded,
            stringResource(R.string.recognition_fail_plugin_title),
            listOf(
                stringResource(
                    if (reason.kind == PluginErrorKind.QUOTA_EXHAUSTED) R.string.recognition_fail_plugin_quota
                    else R.string.recognition_fail_plugin_script,
                    pluginName
                ),
                stringResource(R.string.recognition_fail_plugin_body)
            )
        )

        FailureReason.RecordingFailed -> Triple(
            R.drawable.mic,
            stringResource(R.string.recognition_fail_record_title),
            listOf(stringResource(R.string.recognition_fail_record_body))
        )
    }
    val alternative = if (state.reason is FailureReason.Plugin) {
        plugins.firstOrNull { it.id != state.pluginId }
    } else {
        null
    }
    val switchButton: (@Composable () -> Unit)? = if (alternative != null) {
        {
            OutlinedButton(onClick = { onSwitchPlugin(alternative.id) }) {
                Text(stringResource(R.string.recognition_switch_to, alternative.name))
            }
        }
    } else {
        null
    }
    StatusContent(
        icon = icon,
        title = title,
        lines = lines,
        iconTint = if (state.reason is FailureReason.Plugin) MaterialTheme.colorScheme.error
        else MaterialTheme.colorScheme.primary,
        primaryLabel = stringResource(R.string.recognition_retry),
        onPrimary = onRetry,
        secondaryLabel = stringResource(R.string.recognition_back),
        onSecondary = onBack,
        extra = switchButton
    )
}

@Composable
private fun StatusContent(
    @DrawableRes icon: Int,
    title: String,
    lines: List<String>,
    primaryLabel: String,
    onPrimary: () -> Unit,
    iconTint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
    extra: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(64.dp))
        StatusBadge(icon = icon, tint = iconTint)
        Spacer(Modifier.height(28.dp))
        StatusTexts(title, *lines.toTypedArray())
        Spacer(Modifier.weight(1f))
        if (extra != null) {
            extra()
            Spacer(Modifier.height(12.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            if (secondaryLabel != null && onSecondary != null) {
                OutlinedButton(onClick = onSecondary, modifier = Modifier.weight(1f)) {
                    Text(secondaryLabel)
                }
            }
            Button(onClick = onPrimary, modifier = Modifier.weight(1f)) {
                Text(primaryLabel)
            }
        }
    }
}

internal fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
