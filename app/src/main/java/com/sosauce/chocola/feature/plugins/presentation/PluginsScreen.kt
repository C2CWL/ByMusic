package com.sosauce.chocola.feature.plugins.presentation

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sosauce.chocola.R
import com.sosauce.chocola.core.data.plugin.PluginCandidate
import com.sosauce.chocola.core.data.plugin.PluginCatalog
import com.sosauce.chocola.core.data.plugin.PluginCatalogEntry
import com.sosauce.chocola.core.data.plugin.PluginEntry
import com.sosauce.chocola.core.data.plugin.PluginManifest
import com.sosauce.chocola.core.data.plugin.PluginSourceKind
import com.sosauce.nekobites.helpers.ObserveAsEvents

/**
 * Settings → 插件. Layout follows the approved mockups 04 (plugin manager) and
 * 05 (import with risk confirmation). Minimal first version; colors come from the
 * app theme, so OLED black works as everywhere else.
 */
@Composable
fun PluginsRoute(
    viewModel: PluginsViewModel,
    contentPadding: PaddingValues
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.events) { event ->
        when (event) {
            is PluginsEvent.Message -> Toast.makeText(context, event.text, Toast.LENGTH_SHORT).show()
        }
    }
    PluginsScreen(state = state, viewModel = viewModel, contentPadding = contentPadding)
}

@Composable
private fun PluginsScreen(
    state: PluginsUiState,
    viewModel: PluginsViewModel,
    contentPadding: PaddingValues
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = contentPadding.calculateBottomPadding() + 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.plugins),
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(onClick = viewModel::openImport) {
                Icon(painterResource(R.drawable.add), null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.plugins_import))
            }
        }

        // The sentence the user asked for, always visible at the top of the page.
        InfoCard {
            Text(
                text = stringResource(R.string.plugins_statement),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.plugins_statement_detail),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant
            )
        }

        if (state.plugins.isEmpty()) {
            Text(
                text = stringResource(R.string.plugins_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp)
            )
        }

        state.plugins.forEach { entry ->
            PluginCard(
                entry = entry,
                checkingUpdate = entry.id in state.checkingUpdate,
                onToggle = { viewModel.setEnabled(entry.id, it) },
                onViewCode = { viewModel.viewCode(entry) },
                onCheckUpdate = { viewModel.checkUpdate(entry) },
                onVariables = { viewModel.editVariables(entry) },
                onDelete = { viewModel.askDelete(entry) }
            )
        }

        InfoCard {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    painterResource(R.drawable.info_rounded),
                    null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp).padding(top = 2.dp)
                )
                Spacer(Modifier.width(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.plugins_trust_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                    Text(stringResource(R.string.plugins_sandbox_note), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }

    state.sheet?.let { sheet -> ImportSheet(sheet, viewModel) }
    state.codeViewer?.let { CodeDialog(it, viewModel::closeCode) }
    state.variablesEditor?.let { VariablesDialog(it, viewModel) }
    state.confirmDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(stringResource(R.string.plugins_delete_title)) },
            text = { Text(stringResource(R.string.plugins_delete_body, entry.manifest.platform)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(R.string.plugins_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissDelete) { Text(stringResource(R.string.plugins_cancel)) } }
        )
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(20.dp))
            .background(colors.surfaceContainerLow, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PluginCard(
    entry: PluginEntry,
    checkingUpdate: Boolean,
    onToggle: (Boolean) -> Unit,
    onViewCode: () -> Unit,
    onCheckUpdate: () -> Unit,
    onVariables: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val plugin = entry.plugin
    val manifest = plugin.manifest
    val failed = entry.loadError != null
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(24.dp))
            .background(colors.surfaceContainer, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (failed) colors.errorContainer else colors.secondaryContainer,
                        RoundedCornerShape(14.dp)
                    )
            ) {
                Icon(
                    painterResource(if (failed) R.drawable.warning else R.drawable.extension),
                    null,
                    tint = if (failed) colors.onErrorContainer else colors.onSecondaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(manifest.platform, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val sub = buildString {
                    append("v").append(manifest.displayVersion)
                    manifest.author?.let { append(" · 作者：").append(it) }
                }
                Text(sub, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                entry.loadError?.let {
                    Text(stringResource(R.string.plugins_load_failed, it), style = MaterialTheme.typography.bodySmall, color = colors.error)
                }
            }
            Switch(checked = plugin.enabled, onCheckedChange = onToggle)
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            manifest.protocolMethods.forEach { Tag(methodLabel(it), filled = true) }
            Tag(stringResource(R.string.plugins_format_tag), filled = false)
        }

        if (manifest.missingModules.isNotEmpty()) {
            WarningBox(stringResource(R.string.plugins_partial, manifest.missingModules.joinToString("、")))
        }

        val source = if (plugin.sourceKind == PluginSourceKind.URL && plugin.sourceUrl != null) {
            stringResource(R.string.plugins_source_link, hostOf(plugin.sourceUrl))
        } else {
            stringResource(R.string.plugins_source_file)
        }
        Text(
            "$source · ${stringResource(R.string.plugins_hash_short, shortHash(plugin.sha256))} · ${formatSize(plugin.sizeBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onViewCode) { Text(stringResource(R.string.plugins_view_code)) }
            if (manifest.srcUrl != null || plugin.sourceUrl != null) {
                TextButton(onClick = onCheckUpdate, enabled = !checkingUpdate) {
                    if (checkingUpdate) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(stringResource(R.string.plugins_check_update))
                }
            }
            if (manifest.userVariables.isNotEmpty()) {
                TextButton(onClick = onVariables) { Text(stringResource(R.string.plugins_variables)) }
            }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.plugins_delete), color = colors.error) }
        }
    }
}

@Composable
private fun Tag(text: String, filled: Boolean) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(8.dp)
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = if (filled) colors.onSecondaryContainer else colors.onSurfaceVariant,
        modifier = Modifier
            .then(if (filled) Modifier.background(colors.secondaryContainer, shape) else Modifier.border(1.dp, colors.outline, shape))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun WarningBox(text: String, title: String? = null, lines: List<String> = emptyList()) {
    val colors = MaterialTheme.colorScheme
    // Same amber family as the mockup; readable in light, dark and OLED themes.
    val container = colors.tertiaryContainer
    val onContainer = colors.onTertiaryContainer
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(container, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(painterResource(R.drawable.warning), null, tint = onContainer, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            title?.let { Text(it, style = MaterialTheme.typography.titleSmall, color = onContainer) }
            if (text.isNotEmpty()) Text(text, style = MaterialTheme.typography.bodySmall, color = onContainer)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = onContainer) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportSheet(sheet: ImportSheetState, viewModel: PluginsViewModel) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.readFile(uri)
    }
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(onDismissRequest = viewModel::closeSheet, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                stringResource(if (sheet.isUpdate) R.string.plugins_update_title else R.string.plugins_import_title),
                style = MaterialTheme.typography.headlineSmall
            )

            if (!sheet.isUpdate && sheet.catalog == null) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = sheet.tab == ImportTab.FILE,
                        onClick = { viewModel.selectTab(ImportTab.FILE) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)
                    ) { Text(stringResource(R.string.plugins_tab_file)) }
                    SegmentedButton(
                        selected = sheet.tab == ImportTab.LINK,
                        onClick = { viewModel.selectTab(ImportTab.LINK) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)
                    ) { Text(stringResource(R.string.plugins_tab_link)) }
                }

                when (sheet.tab) {
                    ImportTab.FILE -> OutlinedButton(
                        onClick = { picker.launch(arrayOf("*/*")) },
                        enabled = !sheet.isReading && !sheet.isInstalling,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(stringResource(R.string.plugins_pick_file)) }

                    ImportTab.LINK -> Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = sheet.url,
                            onValueChange = viewModel::setUrl,
                            label = { Text(stringResource(R.string.plugins_link_label)) },
                            placeholder = { Text(stringResource(R.string.plugins_link_hint)) },
                            singleLine = true,
                            enabled = !sheet.isReading && !sheet.isInstalling,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(onClick = viewModel::readLink, enabled = !sheet.isReading && !sheet.isInstalling && sheet.url.isNotBlank()) {
                            Text(stringResource(R.string.plugins_read))
                        }
                    }
                }
            }

            if (sheet.isReading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(R.string.plugins_reading), style = MaterialTheme.typography.bodyMedium)
                }
            }
            sheet.error?.let { Text(it, color = colors.error, style = MaterialTheme.typography.bodyMedium) }

            sheet.catalog?.let { catalog ->
                TextButton(
                    onClick = if (sheet.candidate != null) viewModel::backToCatalog else viewModel::resetImport,
                    enabled = !sheet.isReading && !sheet.isInstalling
                ) {
                    Text(stringResource(if (sheet.candidate != null) R.string.plugins_catalog_back else R.string.plugins_catalog_change))
                }
                if (sheet.candidate == null) {
                    CatalogEntries(catalog, !sheet.isReading, viewModel::readCatalogEntry)
                }
            }

            sheet.candidate?.let { candidate ->
                Text(stringResource(R.string.plugins_read_info), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                CandidateInfo(candidate)
                WarningBox(
                    text = "",
                    title = stringResource(R.string.plugins_risk_title),
                    lines = listOf(
                        stringResource(R.string.plugins_risk_1),
                        stringResource(R.string.plugins_risk_2),
                        stringResource(R.string.plugins_risk_3)
                    )
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = sheet.riskAccepted, onCheckedChange = viewModel::setRiskAccepted, enabled = !sheet.isInstalling)
                    Text(stringResource(R.string.plugins_risk_accept), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = viewModel::closeSheet, enabled = !sheet.isInstalling) {
                    Text(stringResource(R.string.plugins_cancel))
                }
                if (sheet.catalog == null || sheet.candidate != null) {
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = viewModel::install,
                        enabled = sheet.candidate != null && sheet.riskAccepted && !sheet.isInstalling
                    ) { Text(stringResource(R.string.plugins_install)) }
                }
            }
        }
    }
}

@Composable
private fun CatalogEntries(catalog: PluginCatalog, enabled: Boolean, onSelect: (PluginCatalogEntry) -> Unit) {
    Text(stringResource(R.string.plugins_catalog_title, catalog.entries.size), style = MaterialTheme.typography.titleSmall)
    if (catalog.skippedCount > 0) {
        Text(stringResource(R.string.plugins_catalog_skipped, catalog.skippedCount), style = MaterialTheme.typography.bodySmall)
    }
    LazyColumn(
        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(catalog.entries, key = { it.url }) { entry ->
            OutlinedButton(onClick = { onSelect(entry) }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth()) {
                    val label = listOfNotNull(entry.name, entry.version).joinToString(" · ")
                    if (label.isNotEmpty()) Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(entry.url, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun CandidateInfo(candidate: PluginCandidate) {
    val colors = MaterialTheme.colorScheme
    val manifest = candidate.manifest
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainerHigh, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        InfoRow(stringResource(R.string.plugins_field_name), manifest.platform)
        InfoRow(
            stringResource(R.string.plugins_field_version),
            manifest.displayVersion + (manifest.author?.let { " · 作者 $it" } ?: "")
        )
        InfoRow(stringResource(R.string.plugins_field_size), formatSize(candidate.sizeBytes) + " · 格式 MusicFree")
        InfoRow(stringResource(R.string.plugins_field_hash), "SHA-256 " + shortHash(candidate.sha256))
        InfoRow(
            stringResource(R.string.plugins_field_methods),
            manifest.protocolMethods.joinToString("、") { methodLabel(it) }.ifEmpty { "（无）" }
        )
        InfoRow(
            stringResource(R.string.plugins_field_compat),
            compatText(manifest),
            valueColor = if (manifest.missingModules.isEmpty()) colors.primary else colors.error
        )
        candidate.existing?.let { existing ->
            val v = existing.manifest.displayVersion
            val text = when {
                (candidate.versionVsInstalled ?: 0) > 0 -> stringResource(R.string.plugins_replace_newer, v)
                (candidate.versionVsInstalled ?: 0) < 0 -> stringResource(R.string.plugins_replace_older, v)
                else -> stringResource(R.string.plugins_replace_same, v)
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun compatText(manifest: PluginManifest): String =
    if (manifest.missingModules.isEmpty()) stringResource(R.string.plugins_compat_all)
    else stringResource(R.string.plugins_compat_missing, manifest.missingModules.joinToString("、"))

@Composable
private fun InfoRow(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(76.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor)
    }
}

@Composable
private fun CodeDialog(state: CodeViewerState, onClose: () -> Unit) {
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text(state.title) },
        text = {
            Box(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState())) {
                SelectionContainer {
                    Text(state.code, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.plugins_close)) } }
    )
}

@Composable
private fun VariablesDialog(state: VariablesEditorState, viewModel: PluginsViewModel) {
    AlertDialog(
        onDismissRequest = viewModel::closeVariables,
        title = { Text(state.plugin.manifest.platform) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.plugins_variables_hint), style = MaterialTheme.typography.bodySmall)
                state.plugin.manifest.userVariables.forEach { v ->
                    OutlinedTextField(
                        value = state.values[v.key].orEmpty(),
                        onValueChange = { viewModel.setVariable(v.key, it) },
                        label = { Text(v.name ?: v.key) },
                        placeholder = { v.hint?.let { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = viewModel::saveVariables) { Text(stringResource(R.string.plugins_save)) } },
        dismissButton = { TextButton(onClick = viewModel::closeVariables) { Text(stringResource(R.string.plugins_cancel)) } }
    )
}

internal fun methodLabel(method: String): String = when (method) {
    "search" -> "搜索"
    "getMediaSource" -> "播放地址"
    "getLyric" -> "歌词"
    "getMusicInfo" -> "歌曲信息"
    "getAlbumInfo" -> "专辑"
    "getMusicSheetInfo" -> "歌单"
    "getArtistWorks" -> "歌手作品"
    "importMusicItem" -> "导入单曲"
    "importMusicSheet" -> "导入歌单"
    "getTopLists" -> "排行榜"
    "getTopListDetail" -> "榜单详情"
    "getRecommendSheetTags" -> "推荐分类"
    "getRecommendSheetsByTag" -> "推荐歌单"
    "getMusicComments" -> "评论"
    else -> method
}

private fun shortHash(sha256: String): String =
    if (sha256.length > 12) sha256.take(4) + "…" + sha256.takeLast(4) else sha256

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

private fun hostOf(url: String): String = runCatching { java.net.URI(url).host }.getOrNull() ?: url
