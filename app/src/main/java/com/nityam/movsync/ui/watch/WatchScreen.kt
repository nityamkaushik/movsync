package com.nityam.movsync.ui.watch

import android.app.Activity
import android.net.Uri
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
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
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val allowControls by viewModel.allowControls.collectAsStateWithLifecycle()
    var showControls by remember { mutableStateOf(true) }
    var confirmLeave by remember { mutableStateOf(false) }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var currentVolume by remember { mutableFloatStateOf(0f) }
    var currentBrightness by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentVolume) {
        if (showVolumeIndicator) {
            delay(1000)
            showVolumeIndicator = false
        }
    }
    LaunchedEffect(currentBrightness) {
        if (showBrightnessIndicator) {
            delay(1000)
            showBrightnessIndicator = false
        }
    }

    val player = remember(videoUri) {
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setEnableDecoderFallback(true)
        }
        ExoPlayer.Builder(context, renderersFactory).build().apply {
            setMediaItem(MediaItem.fromUri(videoUri))
            prepare()
        }
    }

    LaunchedEffect(roomCode, isHost, player) {
        viewModel.start(roomCode, isHost, player)
    }

    LaunchedEffect(player) {
        while (true) {
            isPlaying = player.isPlaying
            progress = if (player.duration > 0L) {
                player.currentPosition.toFloat() / player.duration.toFloat()
            } else {
                0f
            }
            delay(400L)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
                player.pause()
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
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler {
        confirmLeave = true
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
                                    player.seekTo((player.currentPosition + 10_000).coerceAtMost(duration))
                                } else {
                                    player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0))
                                }
                            }
                        }
                    )
                }
                .pointerInput(isHost, allowControls) {
                    detectHorizontalDragGestures(
                        onDragEnd = { },
                        onHorizontalDrag = { change, dragAmount ->
                            if (isHost || allowControls) {
                                change.consume()
                                val duration = player.duration.takeIf { it > 0L } ?: return@detectHorizontalDragGestures
                                // 1 pixel drag = 100ms seek
                                val newPos = player.currentPosition + (dragAmount * 100L).toLong()
                                player.seekTo(newPos.coerceIn(0L, duration))
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
                                        showBrightnessIndicator = true
                                        showVolumeIndicator = false
                                    }
                                }
                            }
                        }
                    )
                }
        )

        if (showControls) {
            SyncOverlay(
                roomCode = roomCode,
                syncStatus = syncStatus,
                isHost = isHost,
                isPlaying = isPlaying,
                position = progress,
                allowControls = allowControls,
                onToggleControls = { showControls = !showControls },
                onPlayPause = {
                    if (player.isPlaying) player.pause() else player.play()
                },
                onSeek = { value ->
                    val duration = player.duration.takeIf { it > 0L } ?: 0L
                    player.seekTo((duration * value).toLong())
                },
                onLeave = onLeave,
                onAudioSelect = { showAudioDialog = true },
                onSubtitleSelect = { showSubtitleDialog = true },
                modifier = Modifier.fillMaxSize()
            )
        }

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
