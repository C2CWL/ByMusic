package com.sosauce.chocola.feature.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sosauce.chocola.R
import com.sosauce.chocola.feature.recognition.domain.RecognitionPluginInfo
import com.sosauce.chocola.feature.recognition.presentation.RecognitionEntryButton
import com.sosauce.chocola.feature.recognition.presentation.RecognitionEntrySection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnlineMusicScreen(
    onNavigateBack: () -> Unit,
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
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(painterResource(R.drawable.cloud), null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = stringResource(R.string.online_music_pending),
                modifier = Modifier.padding(top = 16.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            RecognitionEntrySection(
                plugins = recognitionPlugins,
                onStart = onStartRecognition,
                onOpenHistory = onOpenRecognitionHistory,
                modifier = Modifier.padding(top = 24.dp)
            )
        }
    }
}
