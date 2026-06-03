package com.nityam.movsync.ui.home

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nityam.movsync.BuildConfig
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nityam.movsync.ui.components.GlassCard
import com.nityam.movsync.ui.components.GradientButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onCreateRoom: () -> Unit,
    onJoinRoom: () -> Unit,
    onLocalPlay: (Uri) -> Unit,
    navController: NavController,
    viewModel: HomeViewModel = viewModel()
) {
    val context = LocalContext.current
    var menuExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) {
                // Ignore, emulator or non-persistable URI
            }
            onLocalPlay(it)
        }
    }
    val displayName by viewModel.displayName.collectAsStateWithLifecycle()
    val updateAvailable by viewModel.updateAvailable.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Text("MovieSync", style = MaterialTheme.typography.displayMedium)
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            onClick = { menuExpanded = false; navController.navigate("settings") }
                        )
                    }
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
            if (updateAvailable) {
                GlassCard {
                    Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Update Available!", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        androidx.compose.material3.TextButton(onClick = { navController.navigate("settings") }) {
                            Text("Update")
                        }
                    }
                }
            }
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
            FilledTonalButton(onClick = { pickerLauncher.launch(arrayOf("video/*")) }) {
                Icon(Icons.Default.VideoLibrary, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Play Local Video")
            }
            Spacer(Modifier.weight(1f))
            
            Column(
                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                val versionStr = BuildConfig.VERSION_NAME
                val displayVersion = if (versionStr.startsWith("v", ignoreCase = true)) versionStr else "v$versionStr"
                Text("App Version $displayVersion", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Team Nityam", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            }
        }
    }
}
