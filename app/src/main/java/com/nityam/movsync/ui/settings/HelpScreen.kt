package com.nityam.movsync.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Help & Instructions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("How to use MovieSync", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            
            Text("1. Getting Started", style = MaterialTheme.typography.titleLarge)
            Text("Both you and your friends must have the exact same video file downloaded on your devices. Ensure the file names and sizes match to avoid synchronization issues.")
            Spacer(Modifier.height(12.dp))
            
            Text("2. Hosting a Room", style = MaterialTheme.typography.titleLarge)
            Text("Tap 'Create Room' on the home screen. Select your video file. You will be given a Room Code. Share this code with your friends.")
            Spacer(Modifier.height(12.dp))
            
            Text("3. Joining a Room", style = MaterialTheme.typography.titleLarge)
            Text("Tap 'Join Room' and enter the Room Code given by the host. Select the identical video file on your device to enter the lobby.")
            Spacer(Modifier.height(12.dp))
            
            Text("4. Playback Controls", style = MaterialTheme.typography.titleLarge)
            Text("Tap the center of the screen to reveal or hide controls. The Host can toggle 'Shared Controls' to allow everyone to play, pause, and seek. Swipe vertically on the right for volume, and on the left for brightness.")
        }
    }
}
