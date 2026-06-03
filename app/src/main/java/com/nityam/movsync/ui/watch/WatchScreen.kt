package com.nityam.movsync.ui.watch

import android.app.Activity
import android.net.Uri
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
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
    var controlsVisible by remember { mutableStateOf(true) }
    var confirmLeave by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val player = remember(videoUri) {
        ExoPlayer.Builder(context).build().apply {
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

    DisposableEffect(Unit) {
        val controller = activity?.window?.insetsController
        controller?.hide(WindowInsets.Type.systemBars())
        controller?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            viewModel.stop()
            player.release()
            controller?.show(WindowInsets.Type.systemBars())
        }
    }

    BackHandler {
        confirmLeave = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable { controlsVisible = !controlsVisible }
    ) {
        VideoPlayerComposable(player = player, modifier = Modifier.fillMaxSize())
        if (controlsVisible) {
            SyncOverlay(
                syncStatus = syncStatus,
                isHost = isHost,
                isPlaying = isPlaying,
                position = progress,
                onPlayPause = {
                    if (player.isPlaying) player.pause() else player.play()
                },
                onSeek = { value ->
                    val duration = player.duration.takeIf { it > 0L } ?: 0L
                    player.seekTo((duration * value).toLong())
                },
                onLeave = onLeave,
                modifier = Modifier.fillMaxSize()
            )
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
