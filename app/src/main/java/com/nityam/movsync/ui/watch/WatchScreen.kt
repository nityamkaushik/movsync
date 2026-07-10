package com.nityam.movsync.ui.watch

import android.app.Activity
import android.net.Uri
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import com.google.firebase.auth.FirebaseAuth
import com.nityam.movsync.ui.chat.ChatUI
import com.nityam.movsync.ui.chat.ChatViewModel
import kotlinx.coroutines.delay

@Composable
fun WatchScreen(
    roomCode: String,
    isHost: Boolean,
    videoUri: Uri,
    onLeave: () -> Unit,
    viewModel: WatchViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val pipController = rememberPipController()
    val isInPipMode by rememberIsInPipMode()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val allowControls by viewModel.allowControls.collectAsStateWithLifecycle()
    
    val chatViewModel: ChatViewModel = viewModel()
    val chatMessages by chatViewModel.messages.collectAsStateWithLifecycle()
    var showChat by remember { mutableStateOf(false) }
    var lastReadMessageCount by remember { mutableIntStateOf(0) }
    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    val currentUserDisplayName = remember { FirebaseAuth.getInstance().currentUser?.displayName ?: "User" }

    val voiceChatViewModel: VoiceChatViewModel = viewModel()
    val voiceChatState by voiceChatViewModel.voiceChatState.collectAsStateWithLifecycle()
    val voicePermissionNeeded by voiceChatViewModel.voicePermissionNeeded.collectAsStateWithLifecycle()
    val peerConnected by voiceChatViewModel.peerConnected.collectAsStateWithLifecycle()
    val isRemoteSpeaking by voiceChatViewModel.isRemoteSpeaking.collectAsStateWithLifecycle()
    val headphonesConnected by voiceChatViewModel.headphonesConnected.collectAsStateWithLifecycle()

    var voiceHasBeenConnected by remember { mutableStateOf(false) }
    var lastHeadphonesState by remember { mutableStateOf(headphonesConnected) }
    var previousPeerConnected by remember { mutableStateOf(false) }
    var launchVoiceAfterPermission by remember { mutableStateOf(false) }
    var permissionPromptFromVoiceActive by remember { mutableStateOf(false) }

    LaunchedEffect(roomCode, currentUserDisplayName, currentUserId) {
        voiceChatViewModel.prefetchToken(
            roomCode,
            currentUserDisplayName,
            currentUserId
        )
        voiceChatViewModel.observeVoiceActive(roomCode)
    }

    LaunchedEffect(voiceChatState, headphonesConnected) {
        if (voiceChatState == VoiceChatState.Connected) {
            if (!voiceHasBeenConnected) {
                voiceHasBeenConnected = true
                val message = if (headphonesConnected) {
                    "Voice connected through headphones"
                } else {
                    "Voice connected through the speaker"
                }
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            } else if (headphonesConnected != lastHeadphonesState) {
                val message = if (headphonesConnected) {
                    "Headphones connected! Reconnect to voice chat for high-fidelity mode."
                } else {
                    "Headphones disconnected! Switched to echo-cancelled speaker mode."
                }
                android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
            }
        } else {
            voiceHasBeenConnected = false
        }
        lastHeadphonesState = headphonesConnected
    }

    LaunchedEffect(peerConnected, voiceChatState) {
        if (voiceChatState != VoiceChatState.Connected) {
            previousPeerConnected = false
        } else {
            if (previousPeerConnected && !peerConnected) {
                android.widget.Toast.makeText(
                    context,
                    "Peer disconnected from voice chat",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            previousPeerConnected = peerConnected
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val shouldStartVoice = launchVoiceAfterPermission
        launchVoiceAfterPermission = false
        permissionPromptFromVoiceActive = false
        voiceChatViewModel.clearVoicePermissionRequest()
        if (isGranted && shouldStartVoice) {
            voiceChatViewModel.startVoiceForAll()
        }
    }

    LaunchedEffect(voicePermissionNeeded) {
        if (!voicePermissionNeeded) {
            if (permissionPromptFromVoiceActive) {
                launchVoiceAfterPermission = false
                permissionPromptFromVoiceActive = false
            }
            return@LaunchedEffect
        }

        if (
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            voiceChatViewModel.clearVoicePermissionRequest()
            voiceChatViewModel.startVoiceForAll()
        } else {
            launchVoiceAfterPermission = true
            permissionPromptFromVoiceActive = true
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(chatMessages.size, showChat) {
        if (showChat) {
            lastReadMessageCount = chatMessages.size
        }
    }
    val hasUnread = chatMessages.size > lastReadMessageCount

    var showControls by remember { mutableStateOf(true) }
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3000)
            showControls = false
        }
    }
    var confirmLeave by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var currentVolume by remember { mutableFloatStateOf(1f) }
    var currentBrightness by remember { mutableFloatStateOf(0f) }
    var volumeSwipeGeneration by remember { mutableIntStateOf(0) }
    var brightnessSwipeGeneration by remember { mutableIntStateOf(0) }

    LaunchedEffect(volumeSwipeGeneration) {
        delay(1000)
        showVolumeIndicator = false
    }
    LaunchedEffect(brightnessSwipeGeneration) {
        delay(1000)
        showBrightnessIndicator = false
    }



    val player = remember(videoUri) {
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
        }
        ExoPlayer.Builder(context, renderersFactory).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(isRemoteSpeaking, currentVolume) {
        if (isRemoteSpeaking) {
            player.volume = currentVolume * 0.3f
        } else {
            player.volume = currentVolume
        }
    }

    LaunchedEffect(roomCode, isHost, player) {
        viewModel.start(roomCode, isHost, player)
        chatViewModel.start(roomCode)
    }

    LaunchedEffect(player) {
        while (true) {
            isPlaying = player.isPlaying
            currentPositionMs = player.currentPosition
            durationMs = player.duration.coerceAtLeast(0L)
            progress = if (durationMs > 0L) {
                currentPositionMs.toFloat() / durationMs.toFloat()
            } else {
                0f
            }
            delay(400L)
        }
    }

    val controlsEnabled = isHost || allowControls
    LaunchedEffect(isPlaying, controlsEnabled) {
        pipController.updatePipParams(isPlaying, controlsEnabled)
    }

    PipActionReceiver {
        if (controlsEnabled) {
            if (isPlaying) viewModel.userPause() else viewModel.userPlay()
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    var wasPlaying by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                val isPip = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && activity?.isInPictureInPictureMode == true
                if (!isPip) {
                    wasPlaying = player.isPlaying
                    player.pause()
                }
            } else if (event == Lifecycle.Event.ON_STOP) {
                if (player.isPlaying) wasPlaying = true
                player.pause()
            } else if (event == Lifecycle.Event.ON_RESUME) {
                if (wasPlaying) {
                    player.play()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("MovSync", "Player Error", error)
                android.widget.Toast.makeText(context, "Player Error: ${error.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }

    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = if (window != null) WindowCompat.getInsetsController(window, window.decorView) else null
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            viewModel.stop()
            player.release()
            pipController.clearPipParams()
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler {
        if (pipController.isPipSupported) {
            pipController.enterPip()
        } else {
            confirmLeave = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VideoPlayerComposable(player = player, modifier = Modifier.fillMaxSize())
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isHost, allowControls) {
                    detectTapGestures(
                        onTap = { showControls = !showControls },
                        onDoubleTap = { offset ->
                            if (isHost || allowControls) {
                                val width = size.width
                                val duration = player.duration.takeIf { it > 0L } ?: return@detectTapGestures
                                if (offset.x > width / 2) {
                                    val newPos = (player.currentPosition + 10_000).coerceAtMost(duration)
                                    viewModel.userSeek(newPos)
                                } else {
                                    val newPos = (player.currentPosition - 10_000).coerceAtLeast(0)
                                    viewModel.userSeek(newPos)
                                }
                            }
                        }
                    )
                }
                .pointerInput(isHost, allowControls) {
                    var dragPosition: Long? = null
                    detectHorizontalDragGestures(
                        onDragEnd = { 
                            dragPosition?.let { viewModel.userSeek(it) }
                            dragPosition = null
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            if (isHost || allowControls) {
                                change.consume()
                                val duration = player.duration.takeIf { it > 0L } ?: return@detectHorizontalDragGestures
                                // 1 pixel drag = 100ms seek
                                val newPos = player.currentPosition + (dragAmount * 100L).toLong()
                                val coercedPos = newPos.coerceIn(0L, duration)
                                player.seekTo(coercedPos)
                                dragPosition = coercedPos
                            }
                        }
                    )
                }
                .pointerInput(isHost, allowControls) {
                    var isRightSide = false
                    var ignoreDrag = false
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            isRightSide = offset.x > size.width / 2
                            ignoreDrag = offset.y < size.height * 0.15f || offset.y > size.height * 0.85f
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (!ignoreDrag && (isHost || allowControls)) {
                                change.consume()
                                val delta = -(dragAmount / size.height.toFloat()) // Dragging up increases value
                                if (isRightSide) {
                                    val newVolume = (player.volume + delta).coerceIn(0f, 1f)
                                    player.volume = newVolume
                                    currentVolume = newVolume
                                    volumeSwipeGeneration++
                                    showVolumeIndicator = true
                                    showBrightnessIndicator = false
                                } else {
                                    val window = activity?.window
                                    if (window != null) {
                                        val attributes = window.attributes
                                        val cB = if (attributes.screenBrightness < 0f) 0.5f else attributes.screenBrightness
                                        val newBrightness = (cB + delta).coerceIn(0.01f, 1f)
                                        attributes.screenBrightness = newBrightness
                                        window.attributes = attributes
                                        currentBrightness = newBrightness
                                        brightnessSwipeGeneration++
                                        showBrightnessIndicator = true
                                        showVolumeIndicator = false
                                    }
                                }
                            }
                        }
                    )
                }
        )

        if (showControls && !isInPipMode) {
            SyncOverlay(
                roomCode = roomCode,
                syncStatus = syncStatus,
                isHost = isHost,
                isPlaying = isPlaying,
                position = progress,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                allowControls = allowControls,
                hasUnread = hasUnread,
                voiceChatState = voiceChatState,
                onMicClick = {
                    when (voiceChatState) {
                        VoiceChatState.Disconnected -> {
                            voiceChatViewModel.prefetchToken(
                                roomCode,
                                currentUserDisplayName,
                                currentUserId
                            )
                            if (
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                voiceChatViewModel.startVoiceForAll()
                            } else {
                                launchVoiceAfterPermission = true
                                permissionPromptFromVoiceActive = false
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                        VoiceChatState.Connecting -> Unit
                        VoiceChatState.Connected -> voiceChatViewModel.stopVoiceForAll()
                    }
                },
                onToggleControls = viewModel::toggleControls,
                onToggleChat = { showChat = !showChat },
                onPlayPause = {
                    if (isPlaying) viewModel.userPause() else viewModel.userPlay()
                },
                onSeek = { value ->
                    val duration = player.duration.takeIf { it > 0L } ?: 0L
                    viewModel.userSeek((duration * value).toLong())
                },
                onAudioSelect = { showAudioDialog = true },
                onSubtitleSelect = { showSubtitleDialog = true },
                onEnterPip = { pipController.enterPip() },
                onLeave = { confirmLeave = true },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (showChat && !isInPipMode) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.35f)
                    .align(Alignment.CenterEnd)
                    .imePadding()
                    .background(Color.Black.copy(alpha = 0.75f))
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

        if (!showControls && hasUnread && !isInPipMode && !showChat) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(32.dp)
                    .size(12.dp)
                    .background(Color.Red, shape = CircleShape)
            )
        }

        if (!isInPipMode) {
            if (showVolumeIndicator) {
            com.nityam.movsync.ui.components.GestureIndicator(
                icon = androidx.compose.material.icons.Icons.Default.VolumeUp,
                value = currentVolume,
                modifier = Modifier.align(Alignment.Center)
            )
        } else if (showBrightnessIndicator) {
            com.nityam.movsync.ui.components.GestureIndicator(
                icon = androidx.compose.material.icons.Icons.Default.BrightnessMedium,
                value = currentBrightness,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        }
    }

    if (showAudioDialog) {
        TrackSelectionDialog(player = player, trackType = C.TRACK_TYPE_AUDIO) {
            showAudioDialog = false
        }
    }
    if (showSubtitleDialog) {
        TrackSelectionDialog(player = player, trackType = C.TRACK_TYPE_TEXT) {
            showSubtitleDialog = false
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Leave watch session?") },
            text = { Text("Playback sync will stop on this device.") },
            confirmButton = {
                TextButton(onClick = onLeave) {
                    Text("Leave")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) {
                    Text("Stay")
                }
            }
        )
    }
}
