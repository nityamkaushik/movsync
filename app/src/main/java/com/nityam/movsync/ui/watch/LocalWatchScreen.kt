package com.nityam.movsync.ui.watch

import android.app.Activity
import android.net.Uri
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
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

@Composable
fun LocalWatchScreen(
    videoUri: Uri,
    onLeave: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val pipController = rememberPipController()
    val isInPipMode by rememberIsInPipMode()
    var controlsVisible by remember { mutableStateOf(true) }
    LaunchedEffect(controlsVisible) {
        if (controlsVisible) {
            delay(3000)
            controlsVisible = false
        }
    }
    var showAudioDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var currentPositionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    
    var showVolumeIndicator by remember { mutableStateOf(false) }
    var showBrightnessIndicator by remember { mutableStateOf(false) }
    var currentVolume by remember { mutableFloatStateOf(0f) }
    var currentBrightness by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentVolume) {
        if (showVolumeIndicator) {
            kotlinx.coroutines.delay(1000)
            showVolumeIndicator = false
        }
    }
    LaunchedEffect(currentBrightness) {
        if (showBrightnessIndicator) {
            kotlinx.coroutines.delay(1000)
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
            playWhenReady = true // Auto-play local videos
        }
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

    LaunchedEffect(isPlaying) {
        pipController.updatePipParams(isPlaying)
    }

    PipActionReceiver {
        if (player.isPlaying) player.pause() else player.play()
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
                android.util.Log.e("MovSync", "Local Player Error", error)
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
            player.release()
            pipController.clearPipParams()
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler {
        if (pipController.isPipSupported) {
            pipController.enterPip()
        } else {
            onLeave()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val duration = player.duration.takeIf { it > 0L } ?: return@detectTapGestures
                        val current = player.currentPosition
                        if (offset.x < size.width / 2) {
                            player.seekTo((current - 10000L).coerceAtLeast(0L))
                        } else {
                            player.seekTo((current + 10000L).coerceAtMost(duration))
                        }
                    },
                    onTap = { controlsVisible = !controlsVisible }
                )
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = { },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val duration = player.duration.takeIf { it > 0L } ?: return@detectHorizontalDragGestures
                        // 1 pixel drag = 100ms seek
                        val newPos = player.currentPosition + (dragAmount * 100L).toLong()
                        player.seekTo(newPos.coerceIn(0L, duration))
                    }
                )
            }
            .pointerInput(Unit) {
                var isRightSide = false
                var ignoreDrag = false
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        isRightSide = offset.x > size.width / 2
                        ignoreDrag = offset.y < size.height * 0.15f || offset.y > size.height * 0.85f
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (!ignoreDrag) {
                            change.consume()
                            val delta = -(dragAmount / size.height.toFloat())
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
    ) {
        VideoPlayerComposable(player = player, modifier = Modifier.fillMaxSize())
        if (controlsVisible && !isInPipMode) {
            LocalPlayerOverlay(
                isPlaying = isPlaying,
                position = progress,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
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
                onEnterPip = { pipController.enterPip() },
                modifier = Modifier.fillMaxSize()
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
}

@Composable
private fun LocalPlayerOverlay(
    isPlaying: Boolean,
    position: Float,
    currentPositionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onSeek: (Float) -> Unit,
    onLeave: () -> Unit,
    onAudioSelect: () -> Unit,
    onSubtitleSelect: () -> Unit,
    onEnterPip: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = onEnterPip) {
                    Icon(Icons.Default.PictureInPictureAlt, contentDescription = "Enter PiP", tint = Color.White)
                }
                IconButton(onClick = onLeave) {
                    Icon(Icons.Default.Close, contentDescription = "Leave", tint = Color.White)
                }
            }
        }
        
        // Center Play/Pause Button
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
        
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.42f))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(onClick = onPlayPause) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = "Play or pause",
                    tint = Color.White
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
