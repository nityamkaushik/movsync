package com.nityam.movsync.ui.watch

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nityam.movsync.data.sync.SyncStatus

private fun formatTime(ms: Long): String {
    if (ms < 0) return "00:00"
    val totalSeconds = ms / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncOverlay(
    roomCode: String,
    syncStatus: SyncStatus,
    isHost: Boolean,
    isPlaying: Boolean,
    position: Float,
    currentPositionMs: Long,
    durationMs: Long,
    allowControls: Boolean,
    hasUnread: Boolean,
    voiceChatState: VoiceChatState,
    onMicClick: () -> Unit,
    onToggleControls: () -> Unit,
    onToggleChat: () -> Unit,
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
            androidx.compose.material3.AssistChip(
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
                androidx.compose.material3.AssistChip(
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
                IconButton(onClick = onToggleChat) {
                    BadgedBox(
                        badge = {
                            if (hasUnread) {
                                Badge { Text("!") }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Chat, contentDescription = "Chat", tint = Color.White)
                    }
                }
                val micTransition = rememberInfiniteTransition(label = "voice mic")
                val connectingAlpha by micTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 0.3f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 600),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "voice mic alpha"
                )
                val micTint = when (voiceChatState) {
                    VoiceChatState.Disconnected -> Color.White
                    VoiceChatState.Connecting -> Color.Green.copy(alpha = connectingAlpha)
                    VoiceChatState.Connected -> Color.Red
                }
                val micDescription = when (voiceChatState) {
                    VoiceChatState.Disconnected -> "Join voice chat"
                    VoiceChatState.Connecting -> "Connecting to voice chat"
                    VoiceChatState.Connected -> "Disconnect voice chat"
                }
                IconButton(
                    onClick = onMicClick,
                    enabled = voiceChatState != VoiceChatState.Connecting
                ) {
                    Icon(
                        Icons.Default.Mic,
                        contentDescription = micDescription,
                        tint = micTint
                    )
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
            
            Column(modifier = Modifier.weight(1f)) {
                var showRemainingTime by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = formatTime(currentPositionMs), color = Color.White, fontSize = 12.sp)
                    Text(
                        text = if (showRemainingTime) "-${formatTime(durationMs - currentPositionMs)}" else formatTime(durationMs),
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { showRemainingTime = !showRemainingTime }
                    )
                }
                com.nityam.movsync.ui.components.MinimalSeekBar(
                    position = position,
                    onSeek = onSeek,
                    enabled = controlsEnabled,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            IconButton(onClick = onAudioSelect) {
                Icon(Icons.Default.Audiotrack, contentDescription = "Audio Tracks", tint = Color.White)
            }
            IconButton(onClick = onSubtitleSelect) {
                Icon(Icons.Default.Subtitles, contentDescription = "Subtitles", tint = Color.White)
            }
        }
    }
}
