package com.nityam.movsync.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
                title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            // App Updates Section
            Column {
                Text(
                    text = "Updates",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )
                
                GlassCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dynamic Icon based on state
                        val icon = if (isDownloading) Icons.Default.CloudDownload
                        else if (updateInfo?.isUpdateAvailable == true) Icons.Default.SystemUpdate
                        else Icons.Default.CheckCircle

                        val iconTint = if (updateInfo?.isUpdateAvailable == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary

                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(iconTint.copy(alpha = 0.1f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isChecking) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = iconTint)
                            } else {
                                Icon(icon, contentDescription = null, tint = iconTint)
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        // Status Text and Action Button
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "MovSync Updates",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            if (isDownloading) {
                                Text("Downloading... ${(downloadProgress * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(CircleShape)
                                )
                            } else if (updateInfo?.isUpdateAvailable == true) {
                                Text("Version ${updateInfo!!.latestVersion} is available", style = MaterialTheme.typography.bodyMedium)
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = viewModel::downloadUpdate,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("Update Now")
                                }
                            } else {
                                Text("App is up to date", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                                Spacer(modifier = Modifier.height(8.dp))
                                OutlinedButton(
                                    onClick = viewModel::checkForUpdates,
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text("Check for Updates")
                                }
                            }
                        }
                    }
                }
            }

            // Support & Info Section
            Column {
                Text(
                    text = "Support & About",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 8.dp, bottom = 8.dp)
                )
                
                GlassCard {
                    Column {
                        SettingsMenuItem(
                            icon = Icons.Default.Info,
                            title = "About MovSync",
                            onClick = onAboutClick
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        SettingsMenuItem(
                            icon = Icons.AutoMirrored.Filled.Help,
                            title = "Help & Instructions",
                            onClick = onHelpClick
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        SettingsMenuItem(
                            icon = Icons.Default.PrivacyTip,
                            title = "Privacy Policy",
                            onClick = onPrivacyClick
                        )
                    }
                }
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
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        }
    }
}
