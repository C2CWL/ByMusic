package com.sosauce.chocola.feature.settings.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sosauce.chocola.R
import com.sosauce.chocola.core.designsystem.BYMUSIC_GITHUB
import com.sosauce.chocola.core.presentation.util.appVersion

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsAboutCard(
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val versionName = context.appVersion.orEmpty()
    val versionLabel = when {
        versionName.isEmpty() -> ""
        versionName.startsWith("v") -> versionName
        else -> "v$versionName"
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, colors.outlineVariant, RoundedCornerShape(24.dp)),
        shape = RoundedCornerShape(24.dp),
        color = colors.surfaceContainerHigh,
        contentColor = contentColorFor(colors.surfaceContainerHigh)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(16.dp)
        ) {
            Image(
                painter = painterResource(R.drawable.bymusic_04_geometric_light),
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLargeEmphasized
                )
                if (versionLabel.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.secondaryContainer,
                        contentColor = contentColorFor(colors.secondaryContainer),
                        modifier = Modifier.padding(top = 6.dp)
                    ) {
                        Text(
                            text = versionLabel,
                            style = MaterialTheme.typography.labelMediumEmphasized,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            IconButton(onClick = { uriHandler.openUri(BYMUSIC_GITHUB) }) {
                Icon(
                    painter = painterResource(R.drawable.github),
                    contentDescription = stringResource(R.string.github),
                    tint = colors.onSurface
                )
            }
        }
    }
}
