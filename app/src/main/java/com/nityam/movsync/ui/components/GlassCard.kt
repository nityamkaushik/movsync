package com.nityam.movsync.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(18.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
        tonalElevation = 2.dp,
        content = {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.padding(contentPadding),
                content = content
            )
        }
    )
}
