package com.nityam.movsync.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nityam.movsync.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = CinemaBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Help & Instructions",
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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 22.dp)
                .padding(top = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            HelpItem(
                number = "1",
                title = "Getting Started",
                body = "Both you and your friends must have the exact same video file downloaded on your devices. Ensure the file names and sizes match to avoid sync issues."
            )
            HelpItem(
                number = "2",
                title = "Hosting a Room",
                body = "Tap 'Create Room' on the home screen. Select your video file. You will be given a Room Code — share this code with your friends."
            )
            HelpItem(
                number = "3",
                title = "Joining a Room",
                body = "Tap 'Join Room' and enter the Room Code given by the host. Select the identical video file on your device to enter the lobby."
            )
            HelpItem(
                number = "4",
                title = "Playback Controls",
                body = "Tap the center of the screen to show or hide controls. The Host can toggle 'Shared Controls' to allow everyone to play, pause, and seek.\n\nSwipe vertically on the right side for volume, and left side for brightness."
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HelpItem(number: String, title: String, body: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SoftSurface, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    Brush.linearGradient(listOf(ElectricPurple, CyanAccent)),
                    RoundedCornerShape(8.dp)
                )
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(androidx.compose.ui.Alignment.Center)
            )
        }
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            Spacer(Modifier.height(6.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
