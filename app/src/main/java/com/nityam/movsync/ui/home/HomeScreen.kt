package com.nityam.movsync.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nityam.movsync.ui.components.GlassCard
import com.nityam.movsync.ui.components.GradientButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text("MovieSync", style = MaterialTheme.typography.displayMedium)
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            GlassCard {
                Text("Watch the same local movie together", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = displayName,
                    onValueChange = viewModel::updateDisplayName,
                    label = { Text("Display name") },
                    singleLine = true
                )
            }
            GradientButton(text = "Create Room", onClick = onCreateRoom)
            FilledTonalButton(onClick = onJoinRoom) {
                Icon(Icons.Default.GroupAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Join Room")
            }
            FilledTonalButton(onClick = onCreateRoom) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Pick Movie")
            }
        }
    }
}
