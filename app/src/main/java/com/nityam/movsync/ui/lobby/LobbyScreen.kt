package com.nityam.movsync.ui.lobby

import android.net.Uri
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nityam.movsync.ui.components.GradientButton
import com.nityam.movsync.ui.components.ParticipantAvatar
import com.nityam.movsync.ui.components.RoomCodeDisplay

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
    val snackbarHostState = remember { SnackbarHostState() }
    val pulse by rememberInfiniteTransition(label = "waitingPulse").animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "waitingAlpha"
    )

    LaunchedEffect(roomCode) {
        viewModel.observe(roomCode)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lobby") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            RoomCodeDisplay(roomCode, snackbarHostState)
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(participants, key = { it.userId }) { participant ->
                    ListItem(
                        headlineContent = { Text(participant.displayName) },
                        supportingContent = {
                            Text(
                                when {
                                    participant.isHost -> "Host"
                                    participant.verified -> "Verified"
                                    else -> "Waiting"
                                }
                            )
                        },
                        leadingContent = {
                            ParticipantAvatar(participant.displayName, participant.online)
                        }
                    )
                }
            }
            if (isHost) {
                GradientButton(
                    text = "Start Watching",
                    enabled = participants.count { it.verified } >= 2,
                    onClick = onStartWatching
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(pulse),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Text("Waiting for host to start")
                }
            }
        }
    }
}
