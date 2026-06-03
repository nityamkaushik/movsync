package com.nityam.movsync.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nityam.movsync.data.sync.SyncStatus

@Composable
fun SyncOverlay(
    syncStatus: SyncStatus,
    isHost: Boolean,
    isPlaying: Boolean,
    position: Float,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        when (syncStatus) {
                            SyncStatus.Synced -> "Synced"
                            SyncStatus.Correcting -> "Correcting"
                            SyncStatus.Reconnecting -> "Reconnecting"
                        }
                    )
                }
            )
            IconButton(onClick = onLeave) {
                Icon(Icons.Default.Close, contentDescription = "Leave", tint = Color.White)
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onPlayPause, enabled = isHost) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play or pause",
                    tint = if (isHost) Color.White else Color.White.copy(alpha = 0.45f)
                )
            }
            Slider(
                value = position,
                onValueChange = onSeek,
                enabled = isHost,
                modifier = Modifier.weight(1f)
            )
        }
    }
}
