package com.nityam.movsync.ui.join

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
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Warning
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
import com.nityam.movsync.ui.theme.CinemaBlack
import com.nityam.movsync.ui.theme.ElectricPurple
import com.nityam.movsync.ui.theme.ErrorRed
import com.nityam.movsync.ui.theme.ReelGold
import com.nityam.movsync.ui.theme.SuccessGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinRoomScreen(
    onBack: () -> Unit,
    onJoined: (String, Uri) -> Unit,
    viewModel: JoinRoomViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.joinWithFile(context, it) }
    }

    LaunchedEffect(state.result) {
        val result = state.result
        if (result is JoinResultUi.Joined) {
            val selectedUri = state.selectedUri
            if (selectedUri != null) {
                onJoined(result.room.code, selectedUri)
            }
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
                Text(
                    "Enter the 6-character room code shared by the host.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = state.code,
                    onValueChange = viewModel::updateCode,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = { Text("Room Code") },
                    placeholder = { Text("ABCXYZ") },
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

                if (state.verifying) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            HashProgressIndicator(null)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Verifying file...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (state.result == null) {
                    GradientButton(
                        text = "Pick Matching Video",
                        icon = Icons.Default.CloudUpload,
                        enabled = state.code.length == 6 && !state.verifying,
                        onClick = { filePickerLauncher.launch(arrayOf("video/*", "application/octet-stream")) }
                    )
                }

                state.error?.let { message ->
                    StatusCard(message)
                }

                val result = state.result
                if (result is JoinResultUi.Joined) {
                    JoinedCard(result.room.code)
                }
                if (result is JoinResultUi.Mismatch) {
                    MismatchCard(result.room.code)
                }
            }
        }
    }
}

@Composable
private fun StatusCard(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ErrorRed.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(22.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = ErrorRed,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun JoinedCard(roomCode: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SuccessGreen.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(22.dp))
        Text(
            "Joined room $roomCode",
            style = MaterialTheme.typography.bodyMedium,
            color = SuccessGreen,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun MismatchCard(roomCode: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ReelGold.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = ReelGold, modifier = Modifier.size(22.dp))
        Column {
            Text(
                "Fingerprint Mismatch",
                style = MaterialTheme.typography.bodyMedium,
                color = ReelGold,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                "Your video doesn't match room $roomCode. Pick a different file.",
                style = MaterialTheme.typography.bodySmall,
                color = ReelGold.copy(alpha = 0.8f)
            )
        }
    }
}
