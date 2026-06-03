package com.nityam.movsync.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nityam.movsync.BuildConfig
import com.nityam.movsync.ui.components.GlassCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onAboutClick: () -> Unit,
    onHelpClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val isChecking by viewModel.isChecking.collectAsStateWithLifecycle()
    val updateInfo by viewModel.updateInfo.collectAsStateWithLifecycle()
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()
    val downloadProgress by viewModel.downloadProgress.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GlassCard {
                Text("App Updates", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("Current Version: ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))

                if (isChecking) {
                    CircularProgressIndicator()
                } else if (isDownloading) {
                    Text("Downloading Update...")
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("${(downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                } else {
                    if (updateInfo != null) {
                        if (updateInfo!!.isUpdateAvailable) {
                            Text("A new version (${updateInfo!!.latestVersion}) is available!", color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = viewModel::downloadUpdate) {
                                Text("Download & Install")
                            }
                        } else {
                            Text("You are on the latest version.", color = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = viewModel::checkForUpdates) {
                                Text("Check for Updates")
                            }
                        }
                    } else {
                        Button(onClick = viewModel::checkForUpdates) {
                            Text("Check for Updates")
                        }
                    }
                }
            }
            
            GlassCard {
                Text("App Information", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                
                SettingsMenuItem(
                    icon = Icons.Default.Info,
                    title = "About",
                    onClick = onAboutClick
                )
                SettingsMenuItem(
                    icon = Icons.AutoMirrored.Filled.Help,
                    title = "Help & Instructions",
                    onClick = onHelpClick
                )
                SettingsMenuItem(
                    icon = Icons.Default.PrivacyTip,
                    title = "Privacy Policy",
                    onClick = onPrivacyClick
                )
            }
        }
    }
}

@Composable
private fun SettingsMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
