@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class,
)

package com.sosauce.chocola.feature.library.presentation.main.components

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.TextButton
import com.sosauce.chocola.core.designsystem.components.libraryGlass
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sosauce.chocola.R
import com.sosauce.chocola.core.presentation.components.CuteSearchbarDefaults
import com.sosauce.chocola.core.presentation.navigation.Screen
import com.sosauce.nekobites.animations.AnimatedDrawable
import com.sosauce.nekobites.animations.AnimatedDrawableFile

@Composable
fun LibraryHeader(
    handleBack: Boolean,
    textFieldState: TextFieldState,
    count: Int,
    onNavigate: (Screen) -> Unit,
    onShuffle: () -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(textFieldState.text.isNotEmpty()) }
    var navigationExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
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
    LaunchedEffect(searching) {
        if (searching) focusRequester.requestFocus()
    }
    Box(Modifier.fillMaxWidth().clipToBounds()) {
        Column(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp)
                .libraryGlass(RoundedCornerShape(28.dp)),
        ) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(
                        onClick = { navigationExpanded = true },
                        modifier = Modifier.libraryGlass(RoundedCornerShape(50))
                    ) {
                        Icon(painterResource(R.drawable.library), stringResource(R.string.navigation))
                        if (!searching) Text(stringResource(R.string.navigation), Modifier.padding(start = 6.dp))
                    }
                    DropdownMenu(
                        modifier = Modifier.widthIn(min = 180.dp),
                        shape = RoundedCornerShape(28.dp),
                        expanded = navigationExpanded,
                        onDismissRequest = { navigationExpanded = false }
                    ) {
                        listOf(
                            Screen.Main to R.string.tracks,
                            Screen.Albums to R.string.albums,
                            Screen.Artists to R.string.artists,
                            Screen.Playlists to R.string.playlists,
                            Screen.Settings() to R.string.settings,
                        ).forEach { (screen, label) ->
                            DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = {
                                navigationExpanded = false
                                closeSearch()
                                onNavigate(screen)
                            })
                        }
                    }
                }
                if (searching) {
                    TextField(
                        state = textFieldState,
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        placeholder = { Text(stringResource(R.string.search_here)) },
                        lineLimits = TextFieldLineLimits.SingleLine,
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                    )
                } else {
                    Text(stringResource(R.string.tracks), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                }
                IconButton(onClick = {
                    if (searching) closeSearch() else searching = true
                }) {
                    Icon(
                        painterResource(if (searching) R.drawable.close else R.drawable.search),
                        stringResource(if (searching) R.string.close else R.string.search_here),
                    )
                }
            }
            Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onShuffle, enabled = count > 0) {
                    Icon(painterResource(R.drawable.shuffle), stringResource(R.string.shuffle))
                }
                Text(count.toString(), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Box {
                    TextButton(
                        onClick = { sortExpanded = true },
                        modifier = Modifier.libraryGlass(RoundedCornerShape(50))
                    ) {
                        AnimatedDrawable(drawable = AnimatedDrawableFile.SORT, atEnd = sortExpanded)
                        Text(stringResource(R.string.library_sort), Modifier.padding(start = 6.dp))
                    }
                    DropdownMenuPopup(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                        CuteSearchbarDefaults.TrackSortPopupContent()
                    }
                }
            }
        }
    }
}
