package com.nityam.movsync.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureInPictureAlt
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
    roomCode: String,
    syncStatus: SyncStatus,
    isHost: Boolean,
    isPlaying: Boolean,
    position: Float,
    allowControls: Boolean,
    onToggleControls: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onAudioSelect: () -> Unit,
    onSubtitleSelect: () -> Unit,
    onEnterPip: () -> Unit,
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
            if (isHost) {
                AssistChip(
                    onClick = onToggleControls,
                    label = { Text(if (allowControls) "Controls: Shared" else "Controls: Host Only") },
                    leadingIcon = {
                        Icon(
                            if (allowControls) Icons.Default.LockOpen else Icons.Default.Lock,
                            contentDescription = null
                        )
                    }
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onEnterPip) {
                    Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Enter PiP", tint = Color.White)
                }
                IconButton(onClick = onLeave) {
                    Icon(Icons.Default.Close, contentDescription = "Leave", tint = Color.White)
                }
            }
        }
        
        // Center Play/Pause Button
        val controlsEnabled = isHost || allowControls
        if (controlsEnabled) {
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(80.dp)
                    .background(Color.Black.copy(alpha = 0.4f), shape = androidx.compose.foundation.shape.CircleShape)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play or pause",
                    tint = Color.White,
                    modifier = Modifier.size(48.dp)
                )
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
            val controlsEnabled = isHost || allowControls
            IconButton(onClick = onPlayPause, enabled = controlsEnabled) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play or pause",
                    tint = if (controlsEnabled) Color.White else Color.White.copy(alpha = 0.45f)
                )
            }
            com.nityam.movsync.ui.components.MinimalSeekBar(
                position = position,
                onSeek = onSeek,
                enabled = controlsEnabled,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onAudioSelect) {
                Icon(Icons.Default.Audiotrack, contentDescription = "Audio Tracks", tint = Color.White)
            }
            IconButton(onClick = onSubtitleSelect) {
                Icon(Icons.Default.Subtitles, contentDescription = "Subtitles", tint = Color.White)
            }
        }
    }
}
