package com.nityam.movsync.ui.lobby

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nityam.movsync.data.model.PresenceUser
import com.nityam.movsync.ui.chat.ChatUI
import com.nityam.movsync.ui.chat.ChatViewModel
import com.nityam.movsync.ui.components.ParticipantAvatar
import com.nityam.movsync.ui.components.RoomCodeDisplay
import com.nityam.movsync.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LobbyScreen(
    roomCode: String,
    isHost: Boolean,
    videoUri: Uri?,
    onBack: () -> Unit,
    onStartWatching: (Uri) -> Unit,
    viewModel: LobbyViewModel = viewModel(),
    fileShareViewModel: LobbyFileShareViewModel = viewModel()
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    val participants by viewModel.participants.collectAsStateWithLifecycle()
    val started by viewModel.started.collectAsStateWithLifecycle()
    val fileShareState by fileShareViewModel.state.collectAsStateWithLifecycle()
    val verifiedVideoUri by fileShareViewModel.videoUri.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val pulse by rememberInfiniteTransition(label = "waitingPulse").animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
        label = "waitingAlpha"
    )

    val chatViewModel: ChatViewModel = viewModel()
    val chatMessages by chatViewModel.messages.collectAsStateWithLifecycle()
    val currentUserId by chatViewModel.currentUserId.collectAsStateWithLifecycle()
    var showChat by remember(roomCode) { mutableStateOf(false) }
    var seenMessageIds by remember(roomCode) { mutableStateOf<Set<String>>(emptySet()) }
    var messageBaselineReady by remember(roomCode) { mutableStateOf(false) }
    var contentVisible by remember(roomCode) { mutableStateOf(false) }

    val unreadCount = if (messageBaselineReady) {
        chatMessages.count { it.senderId != currentUserId && it.messageId !in seenMessageIds }
    } else {
        0
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Some providers grant only transient access; verification can still proceed.
            }
            fileShareViewModel.selectFile(context, it, roomCode)
        }
    }

    LaunchedEffect(roomCode, isHost) {
        contentVisible = false
        viewModel.observe(roomCode, isHost)
        chatViewModel.start(roomCode)
        fileShareViewModel.observeFileShare(roomCode, isHost)
        contentVisible = true
    }

    LaunchedEffect(chatMessages, showChat, currentUserId) {
        val ids = chatMessages.map { it.messageId }.toSet()
        if (!messageBaselineReady) {
            seenMessageIds = ids
            messageBaselineReady = true
        } else if (showChat) {
            seenMessageIds = ids
        }
    }

    LaunchedEffect(started) {
        if (started && !isHost) {
            val watchUri = verifiedVideoUri ?: videoUri
            if (watchUri != null) {
                onStartWatching(watchUri)
            } else {
                snackbarHostState.showSnackbar("Select or download the movie file first")
            }
        }
    }

    DisposableEffect(roomCode) {
        onDispose {
            fileShareViewModel.cleanup()
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
                        radius = 900f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 92.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                AnimatedLobbySection(visible = contentVisible, delayMillis = 40) {
                    RoomCodeDisplay(roomCode, snackbarHostState)
                }

                AnimatedLobbySection(visible = contentVisible, delayMillis = 100) {
                    FileShareSection(
                        isHost = isHost,
                        fileShareState = fileShareState,
                        onShareClick = {
                            val uri = videoUri
                            if (uri != null) {
                                fileShareViewModel.startSharing(roomCode, uri)
                            } else {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Host file unavailable in this session")
                                }
                            }
                        },
                        onDownloadClick = { fileShareViewModel.startDownload(roomCode) },
                        onSelectFileClick = { filePicker.launch(arrayOf("video/*")) },
                        onCancelDownload = fileShareViewModel::cancelDownload
                    )
                }

                AnimatedLobbySection(visible = contentVisible, delayMillis = 160) {
                    LobbyActionCard(
                        isHost = isHost,
                        participants = participants,
                        pulse = pulse,
                        onStartClick = {
                            val watchUri = videoUri ?: verifiedVideoUri
                            if (watchUri != null) {
                                viewModel.startRoom(roomCode)
                                onStartWatching(watchUri)
                            } else {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Host file unavailable in this session")
                                }
                            }
                        }
                    )
                }

                AnimatedLobbySection(
                    visible = contentVisible,
                    delayMillis = 220,
                    modifier = Modifier.weight(1f)
                ) {
                    ParticipantsCard(participants = participants, isHost = isHost)
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 22.dp, bottom = 22.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        showChat = true
                        seenMessageIds = chatMessages.map { it.messageId }.toSet()
                    },
                    containerColor = ElectricPurple,
                    contentColor = Color.White
                ) {
                    BadgedBox(
                        badge = {
                            if (unreadCount > 0) {
                                Badge(containerColor = ErrorRed, contentColor = Color.White) {
                                    Text(unreadCount.coerceAtMost(99).toString())
                                }
                            }
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = "Open chat")
                    }
                }
            }

            if (showChat) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(0.62f)
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 12.dp, vertical = 88.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black.copy(alpha = 0.78f))
                ) {
                    ChatUI(
                        messages = chatMessages,
                        currentUserId = currentUserId,
                        onSendMessage = { chatViewModel.sendMessage(it) },
                        onClose = { showChat = false },
                        backgroundColor = Color.Transparent,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedLobbySection(
    visible: Boolean,
    delayMillis: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(animationSpec = tween(340, delayMillis = delayMillis)) +
            slideInVertically(
                animationSpec = tween(340, delayMillis = delayMillis),
                initialOffsetY = { it / 5 }
            )
    ) {
        content()
    }
}

@Composable
private fun LobbyActionCard(
    isHost: Boolean,
    participants: List<PresenceUser>,
    pulse: Float,
    onStartClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SoftSurface.copy(alpha = 0.58f)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isHost) {
                val verifiedCount = participants.count { it.verified }
                val canStart = verifiedCount >= 2
                val playScale by animateFloatAsState(
                    if (canStart) 1.05f else 1.0f,
                    animationSpec = tween(160),
                    label = "playScale"
                )

                IconButton(
                    onClick = { if (canStart) onStartClick() },
                    enabled = canStart,
                    modifier = Modifier
                        .size(58.dp)
                        .scale(playScale)
                        .background(
                            brush = if (canStart) {
                                Brush.linearGradient(listOf(ElectricPurple, CyanAccent))
                            } else {
                                Brush.linearGradient(
                                    listOf(Color.Gray.copy(alpha = 0.3f), Color.Gray.copy(alpha = 0.3f))
                                )
                            },
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Start Watching",
                        tint = if (canStart) Color.White else Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(28.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = if (canStart) "Start Watching" else "Waiting for participants... ($verifiedCount/2 joined)",
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
}

@Composable
private fun ParticipantsCard(participants: List<PresenceUser>, isHost: Boolean) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = SoftSurface.copy(alpha = 0.48f)),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            val verifiedCount = participants.count { it.verified }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Participants",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(participants, key = { it.userId }) { participant ->
                    ParticipantListItem(participant)
                }
            }
        }
    }
}

@Composable
private fun ParticipantListItem(participant: PresenceUser) {
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
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                when {
                    participant.isHost -> "Host"
                    participant.verified -> "Verified"
                    else -> "Waiting..."
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
