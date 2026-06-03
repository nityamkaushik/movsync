package com.nityam.movsync.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nityam.movsync.ui.theme.SuccessGreen
import com.nityam.movsync.ui.theme.WarningYellow

@Composable
fun ParticipantAvatar(
    name: String,
    online: Boolean,
    modifier: Modifier = Modifier
) {
    BadgedBox(
        badge = {
            Badge(containerColor = if (online) SuccessGreen else WarningYellow)
        },
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}
