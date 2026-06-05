package com.nityam.movsync.ui.lobby

import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import com.nityam.movsync.ui.chat.ChatUI
import com.nityam.movsync.ui.chat.ChatViewModel
import com.nityam.movsync.ui.components.ParticipantAvatar
import com.nityam.movsync.ui.components.RoomCodeDisplay
import com.nityam.movsync.ui.theme.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.scale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(
    roomCode: String,
    isHost: Boolean,
    videoUri: Uri,
    onBack: () -> Unit,
    onStartWatching: () -> Unit,
    viewModel: LobbyViewModel = viewModel()
) {
    val participants by viewModel.participants.collectAsStateWithLifecycle()
    val started by viewModel.started.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val pulse by rememberInfiniteTransition(label = "waitingPulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "waitingAlpha"
    )

    val chatViewModel: ChatViewModel = viewModel()
    val chatMessages by chatViewModel.messages.collectAsStateWithLifecycle()
    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }

    LaunchedEffect(roomCode) {
        viewModel.observe(roomCode)
        chatViewModel.start(roomCode)
    }

    LaunchedEffect(started) {
        if (started && !isHost) {
            onStartWatching()
        }
    }

    Scaffold(
        containerColor = CinemaBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Lobby",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = Color.White
                )
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = SoftSurface,
                    contentColor = Color.White,
                    actionColor = CyanAccent
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .background(
                    Brush.radialGradient(
                        colors = listOf(ElectricPurple.copy(alpha = 0.08f), Color.Transparent),
                        radius = 800f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                RoomCodeDisplay(roomCode, snackbarHostState)

                // Start button / Waiting Card (Permanently above participants, directly below the room code)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SoftSurface.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (isHost) {
                            val verifiedCount = participants.count { it.verified }
                            val canStart = verifiedCount >= 2
                            
                            val playScale by animateFloatAsState(if (canStart) 1.05f else 1.0f, label = "playScale")
                            
                            IconButton(
                                onClick = {
                                    if (canStart) {
                                        viewModel.startRoom(roomCode)
                                        onStartWatching()
                                    }
                                },
                                enabled = canStart,
                                modifier = Modifier
                                    .size(72.dp)
                                    .scale(playScale)
                                    .background(
                                        brush = if (canStart) {
                                            Brush.linearGradient(listOf(ElectricPurple, CyanAccent))
                                        } else {
                                            Brush.linearGradient(listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f)))
                                        },
                                        shape = CircleShape
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Start Watching",
                                    tint = if (canStart) Color.White else Color.White.copy(alpha = 0.4f),
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (canStart) "Start Watching" else "Waiting for participants… ($verifiedCount/2 joined)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (canStart) CyanAccent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .alpha(pulse),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Waiting for host to start",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Participant count badge
                val verifiedCount = participants.count { it.verified }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Participants",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (isHost) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(ElectricPurple.copy(alpha = 0.18f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "$verifiedCount verified",
                                style = MaterialTheme.typography.labelSmall,
                                color = PurpleMid
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 320.dp)
                ) {
                    items(participants, key = { it.userId }) { participant ->
                        ParticipantListItem(participant)
                    }
                }
            }

            ChatUI(
                messages = chatMessages,
                currentUserId = currentUserId,
                onSendMessage = { chatViewModel.sendMessage(it) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(300.dp)
            )
        }
    }
}

@Composable
private fun ParticipantListItem(participant: com.nityam.movsync.data.model.PresenceUser) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SoftSurface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ParticipantAvatar(participant.displayName, participant.online)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                participant.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Text(
                when {
                    participant.isHost -> "Host"
                    participant.verified -> "Verified ✓"
                    else -> "Waiting…"
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    participant.isHost -> CyanAccent
                    participant.verified -> SuccessGreen
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        if (participant.isHost) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CyanAccent.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    "HOST",
                    style = MaterialTheme.typography.labelSmall,
                    color = CyanAccent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
