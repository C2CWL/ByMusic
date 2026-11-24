package com.sosauce.chocola.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * A surface for how all list items should look like
 */
@Composable
fun CuteListItem(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    backgroundColor: Color = Color.Transparent,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)? = null,
    leadingContent: @Composable () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null,
    verticalPadding: Dp = 12.dp,
    horizontalPadding: Dp = 8.dp,
    content: @Composable ColumnScope.() -> Unit
) {

    val container = modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        .clip(shape)
        .background(backgroundColor)
    // BasicComponent handles taps; a combined gesture owns both actions for selectable rows.
    top.yukonga.miuix.kmp.basic.BasicComponent(
        modifier = if (onLongClick == null) container else container.combinedClickable(
            onClick = { onClick?.invoke() }, onLongClick = onLongClick
        ),
        onClick = if (onLongClick == null) onClick else null,
        insideMargin = androidx.compose.foundation.layout.PaddingValues(horizontal = horizontalPadding, vertical = verticalPadding),
        startAction = leadingContent,
        endActions = trailingContent?.let { trailing -> { trailing() } },
    ) { Column(Modifier.padding(horizontal = 12.dp), content = content) }
}
