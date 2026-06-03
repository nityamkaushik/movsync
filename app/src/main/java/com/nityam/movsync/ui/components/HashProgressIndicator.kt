package com.nityam.movsync.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HashProgressIndicator(progress: Float?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(92.dp), contentAlignment = Alignment.Center) {
        if (progress == null) {
            CircularProgressIndicator()
        } else {
            CircularProgressIndicator(progress = { progress.coerceIn(0f, 1f) })
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
