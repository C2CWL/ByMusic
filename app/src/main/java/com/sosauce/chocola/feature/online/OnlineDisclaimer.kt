package com.sosauce.chocola.feature.online

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sosauce.chocola.R
import com.sosauce.chocola.core.data.datastore.PreferencesKeys
import com.sosauce.chocola.core.data.datastore.dataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * "用在线音乐前，先看这几条" (mockup 06). Shown the first time the online page opens; after
 * "我知道了" it is not shown again and can be reread from Settings → 在线与下载.
 *
 * @param onDecline null when reread from settings (only one button then).
 */
@Composable
fun OnlineDisclaimerDialog(
    onAccept: () -> Unit,
    onDecline: (() -> Unit)?
) {
    AlertDialog(
        onDismissRequest = { onDecline?.invoke() ?: onAccept() },
        icon = { Icon(painterResource(R.drawable.shield), null) },
        title = { Text(stringResource(R.string.online_disclaimer_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val first = stringResource(R.string.online_disclaimer_1_lead)
                val firstBold = stringResource(R.string.online_disclaimer_1_bold)
                val firstTail = stringResource(R.string.online_disclaimer_1_tail)
                Text(
                    buildAnnotatedString {
                        append("1. ")
                        append(first)
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(firstBold) }
                        append(firstTail)
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                listOf(
                    R.string.online_disclaimer_2,
                    R.string.online_disclaimer_3,
                    R.string.online_disclaimer_4,
                    R.string.online_disclaimer_5
                ).forEachIndexed { index, res ->
                    Text("${index + 2}. " + stringResource(res), style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = onAccept) { Text(stringResource(R.string.online_disclaimer_accept)) }
        },
        dismissButton = if (onDecline != null) {
            { TextButton(onClick = onDecline) { Text(stringResource(R.string.online_disclaimer_decline)) } }
        } else {
            null
        }
    )
}

/** null until the stored value is read, so the dialog never flashes for people who accepted. */
@Composable
fun rememberOnlineDisclaimerAccepted(): Boolean? {
    val context = LocalContext.current
    val flow: Flow<Boolean?> = remember(context) {
        context.dataStore.data.map { it[PreferencesKeys.ONLINE_DISCLAIMER_ACCEPTED] ?: false }
    }
    val accepted by flow.collectAsStateWithLifecycle(initialValue = null)
    return accepted
}

suspend fun android.content.Context.acceptOnlineDisclaimer() {
    dataStore.edit { it[PreferencesKeys.ONLINE_DISCLAIMER_ACCEPTED] = true }
}
