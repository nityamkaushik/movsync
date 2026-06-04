package com.nityam.movsync.ui.join

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nityam.movsync.ui.components.GradientButton
import com.nityam.movsync.ui.components.HashProgressIndicator
import com.nityam.movsync.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinRoomScreen(
    onBack: () -> Unit,
    onJoined: (String, Uri) -> Unit,
    viewModel: JoinRoomViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {}
            viewModel.joinWithFile(context, it)
        }
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.result) {
        val result = state.result
        if (result is JoinResultUi.Joined) {
            onJoined(result.room.code, result.uri)
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Scaffold(
        containerColor = CinemaBlack,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Join Room",
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.radialGradient(
                        colors = listOf(ElectricPurple.copy(alpha = 0.10f), Color.Transparent),
                        radius = 700f
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp)
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // Intro text
                Text(
                    "Enter the 6-character room code shared by the host",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Room code input
                OutlinedTextField(
                    value = state.code,
                    onValueChange = viewModel::updateCode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text("Room Code") },
                    placeholder = { Text("e.g. ABC123") },
                    leadingIcon = {
                        Icon(
                            Icons.Default.Tag,
                            contentDescription = null,
                            tint = ElectricPurple,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        keyboardType = KeyboardType.Ascii
                    ),
                    shape = RoundedCornerShape(16.dp),
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = androidx.compose.ui.unit.TextUnit(4f, androidx.compose.ui.unit.TextUnitType.Sp)
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                        focusedBorderColor = ElectricPurple,
                        focusedLabelColor = ElectricPurple,
                        cursorColor = ElectricPurple
                    )
                )

                GradientButton(
                    text = "Pick Matching Video",
                    icon = Icons.Default.FolderOpen,
                    enabled = state.code.length == 6 && !state.verifying,
                    onClick = { picker.launch(arrayOf("video/*")) }
                )

                // Loading state
                if (state.verifying) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        HashProgressIndicator(null)
                    }
                }

                // Error card
                state.error?.let { errMsg ->
                    StatusCard(isSuccess = false, message = errMsg)
                }

                // Result states
                when (val result = state.result) {
                    is JoinResultUi.Mismatch -> {
                        StatusCard(
                            isSuccess = false,
                            message = "This file does not match room ${result.room.code}"
                        )
                    }
                    is JoinResultUi.Joined -> {
                        StatusCard(
                            isSuccess = true,
                            message = "File verified successfully!"
                        )
                    }
                    null -> Unit
                }
            }
        }
    }
}

@Composable
private fun StatusCard(isSuccess: Boolean, message: String) {
    val tint = if (isSuccess) SuccessGreen else ErrorRed
    val bgColor = if (isSuccess) SuccessGreen.copy(alpha = 0.08f) else ErrorRed.copy(alpha = 0.08f)
    val icon = if (isSuccess) Icons.Default.CheckCircle else Icons.Default.Error

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor, RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
            fontWeight = FontWeight.Medium
        )
    }
}

