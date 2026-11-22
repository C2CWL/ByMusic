package com.sosauce.chocola.feature.settings.presentation.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SettingsClickable(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    @DrawableRes icon: Int? = null,
    baseShape: Shape = RoundedCornerShape(16.dp),
) {
    SettingsRow(title, description, icon, modifier.clip(baseShape), onClick = onClick)
}

@Composable
internal fun SettingsSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    @DrawableRes icon: Int? = null,
    baseShape: Shape = RoundedCornerShape(16.dp),
) {
    SettingsRow(
        title, description, icon,
        modifier.clip(baseShape).toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange),
        endActions = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun SettingsRow(
    title: String,
    description: String?,
    @DrawableRes icon: Int?,
    modifier: Modifier,
    onClick: (() -> Unit)? = null,
    endActions: @Composable (RowScope.() -> Unit)? = null,
) {
    BasicComponent(
        modifier = modifier.background(MiuixTheme.colorScheme.surfaceContainer),
        title = title,
        summary = description,
        startAction = if (icon == null) null else {
            { Icon(painterResource(icon), null, Modifier.padding(end = 16.dp).size(24.dp)) }
        },
        endActions = endActions,
        onClick = onClick,
    )
}
