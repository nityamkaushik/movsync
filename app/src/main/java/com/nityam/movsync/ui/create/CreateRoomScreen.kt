package com.nityam.movsync.ui.create

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nityam.movsync.ui.components.GlassCard
import com.nityam.movsync.ui.components.GradientButton
import com.nityam.movsync.ui.components.HashProgressIndicator
import com.nityam.movsync.ui.components.RoomCodeDisplay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateRoomScreen(
    onBack: () -> Unit,
    onOpenLobby: (String, Uri) -> Unit,
    viewModel: CreateRoomViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            viewModel.createFromFile(context, it)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create Room") },
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
            when (val current = state) {
                CreateRoomUiState.SelectFile -> {
                    GlassCard {
                        Icon(Icons.Default.MovieCreation, contentDescription = null)
                        Text("Choose the movie file you will watch")
                    }
                    GradientButton(
                        text = "Pick Video",
                        onClick = { picker.launch(arrayOf("video/*")) }
                    )
                }
                is CreateRoomUiState.Hashing -> {
                    GlassCard {
                        Text("Fingerprinting video")
                        HashProgressIndicator(current.progress)
                    }
                }
                is CreateRoomUiState.RoomCreated -> {
                    RoomCodeDisplay(current.room.code, snackbarHostState)
                    GradientButton(
                        text = "Open Lobby",
                        onClick = { onOpenLobby(current.room.code, current.uri) }
                    )
                }
                is CreateRoomUiState.Error -> {
                    GlassCard { Text(current.message) }
                    GradientButton(text = "Try Again", onClick = viewModel::reset)
                }
            }
        }
    }
}
