@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.sosauce.chocola.feature.library.presentation.main.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sosauce.chocola.R
import com.sosauce.chocola.core.presentation.components.CuteSearchbarDefaults
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.chocola.feature.settings.presentation.components.SettingsScreen

private data class DrawerDestination(val label: Int, val icon: Int, val screen: Screen)

@Composable
fun LibraryTopBar(
    handleBack: Boolean,
    textFieldState: TextFieldState,
    onOpenDrawer: () -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(textFieldState.text.isNotEmpty()) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun closeSearch() {
        textFieldState.edit { replace(0, length, "") }
        searching = false
        focusManager.clearFocus()
        keyboard?.hide()
    }
    BackHandler(enabled = handleBack && (searching || textFieldState.text.isNotEmpty())) { closeSearch() }
    LaunchedEffect(searching) { if (searching) focusRequester.requestFocus() }
    SmallTopAppBar(
        title = stringResource(R.string.tracks),
        navigationIcon = {
            IconButton(onClick = onOpenDrawer) {
                Icon(painterResource(R.drawable.menu), stringResource(R.string.navigation))
            }
        },
        bottomContent = {
            if (searching) {
                TextField(
                    state = textFieldState,
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    label = stringResource(R.string.search_here),
                    useLabelAsPlaceholder = true,
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
            }
        },
        actions = {
            IconButton(onClick = { if (searching) closeSearch() else searching = true }) {
                Icon(
                    painterResource(if (searching) R.drawable.close else R.drawable.search),
                    stringResource(if (searching) R.string.close else R.string.search_here),
                )
            }
        },
    )
}

@Composable
fun LibraryDrawerSheet(
    trackCount: Int,
    onShuffle: () -> Unit,
    onNavigate: (Screen) -> Unit,
    onClose: () -> Unit,
) {
    var sortExpanded by remember { mutableStateOf(false) }
    val library = listOf(
        DrawerDestination(R.string.tracks, R.drawable.music_note, Screen.Main),
        DrawerDestination(R.string.albums, R.drawable.album_filled, Screen.Albums),
        DrawerDestination(R.string.artists, R.drawable.artists_filled, Screen.Artists),
        DrawerDestination(R.string.playlists, R.drawable.queue_music_rounded, Screen.Playlists),
        DrawerDestination(R.string.online_music, R.drawable.cloud, Screen.Online),
    )
    val tools = listOf(
        DrawerDestination(R.string.library_setup, R.drawable.folder_rounded, Screen.Settings(SettingsScreen.Library)),
        DrawerDestination(R.string.bakery, R.drawable.calendar_filled, Screen.Settings(SettingsScreen.Bakery)),
        DrawerDestination(R.string.settings, R.drawable.settings_filled, Screen.Settings()),
    )
    ModalDrawerSheet(drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)) {
        Column(Modifier.widthIn(max = 320.dp).verticalScroll(rememberScrollState()).padding(vertical = 12.dp)) {
            Text(
                stringResource(R.string.app_name),
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 16.dp),
                style = MaterialTheme.typography.titleLarge,
            )
            library.forEach { item ->
                NavigationDrawerItem(
                    label = { Text(stringResource(item.label)) },
                    icon = { Icon(painterResource(item.icon), null) },
                    selected = item.screen == Screen.Main,
                    onClick = { onClose(); if (item.screen != Screen.Main) onNavigate(item.screen) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            NavigationDrawerItem(
                label = { Text("${stringResource(R.string.shuffle)} · $trackCount") },
                icon = { Icon(painterResource(R.drawable.shuffle), null) },
                selected = false,
                onClick = {
                    if (trackCount > 0) {
                        onClose()
                        onShuffle()
                    }
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.library_sort)) },
                icon = { Icon(painterResource(R.drawable.shapes), null) },
                selected = sortExpanded,
                onClick = { sortExpanded = true },
                modifier = Modifier.padding(horizontal = 12.dp).heightIn(min = 48.dp),
            )
            DropdownMenuPopup(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                CuteSearchbarDefaults.TrackSortPopupContent()
            }
            HorizontalDivider(Modifier.padding(horizontal = 28.dp, vertical = 8.dp))
            tools.forEach { item ->
                NavigationDrawerItem(
                    label = { Text(stringResource(item.label)) },
                    icon = { Icon(painterResource(item.icon), null) },
                    selected = false,
                    onClick = { onClose(); onNavigate(item.screen) },
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}
