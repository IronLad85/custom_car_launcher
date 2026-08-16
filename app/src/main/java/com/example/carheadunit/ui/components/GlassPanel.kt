package com.example.carheadunit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.carheadunit.ui.theme.GlassBorder
import com.example.carheadunit.ui.theme.GlassFill

/** Glass panel per the design tokens: rgba(28,31,38,0.8) fill, 1px outline-variant border, 12dp corners. */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    contentPadding: Dp = 0.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .background(GlassFill, RoundedCornerShape(cornerRadius))
            .border(1.dp, GlassBorder, RoundedCornerShape(cornerRadius))
            .padding(contentPadding),
        content = content,
    )
}
