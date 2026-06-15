package com.nityam.movsync.ui.lobby

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nityam.movsync.ui.components.GradientButton
import com.nityam.movsync.ui.theme.CyanAccent
import com.nityam.movsync.ui.theme.ErrorRed
import com.nityam.movsync.ui.theme.SoftSurface
import com.nityam.movsync.ui.theme.SuccessGreen

@Composable
fun FileShareSection(
    isHost: Boolean,
    fileShareState: FileShareUiState,
    onShareClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onSelectFileClick: () -> Unit,
    onCancelDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SoftSurface.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isHost) "Movie Sharing" else "Movie File",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = subtitleFor(isHost, fileShareState),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusPill(fileShareState)
            }

            when (fileShareState) {
                FileShareUiState.NoFileShared -> {
                    if (isHost) {
                        GradientButton(
                            text = "Share File",
                            icon = Icons.Default.Share,
                            onClick = onShareClick
                        )
                    } else {
                        Text(
                            "Waiting for host to share a movie...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = onSelectFileClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Select File")
                        }
                    }
                }

                is FileShareUiState.Uploading -> {
                    val progress = if (fileShareState.totalBytes > 0L) {
                        fileShareState.bytesUploaded.toFloat() / fileShareState.totalBytes.toFloat()
                    } else {
                        0f
                    }
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = CyanAccent,
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )
                    Text(
                        "Uploading ${formatBytes(fileShareState.bytesUploaded)} / ${formatBytes(fileShareState.totalBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is FileShareUiState.Sharing -> {
                    Text(
                        "Guests can download directly from this device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SuccessGreen
                    )
                }

                is FileShareUiState.FileAvailable -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GradientButton(
                            text = "Download",
                            icon = Icons.Default.Download,
                            onClick = onDownloadClick,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = onSelectFileClick,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Select")
                        }
                    }
                }

                is FileShareUiState.Downloading -> {
                    val progress = if (fileShareState.totalBytes > 0L) {
                        fileShareState.bytesReceived.toFloat() / fileShareState.totalBytes.toFloat()
                    } else {
                        0f
                    }
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = CyanAccent,
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${formatBytes(fileShareState.bytesReceived)} / ${formatBytes(fileShareState.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        TextButton(onClick = onCancelDownload) {
                            Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Cancel")
                        }
                    }
                }

                is FileShareUiState.Downloaded -> {
                    Text(
                        "Download complete. Verifying saved file...",
                        style = MaterialTheme.typography.bodySmall,
                        color = CyanAccent
                    )
                }

                is FileShareUiState.Verifying -> {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = CyanAccent,
                        trackColor = Color.White.copy(alpha = 0.12f)
                    )
                    Text(
                        "Computing fingerprint...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FileShareUiState.Verified -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SuccessGreen)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "File verified. Ready to watch.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SuccessGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                is FileShareUiState.Error -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = ErrorRed)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            fileShareState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = ErrorRed,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onSelectFileClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Select File")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(state: FileShareUiState) {
    val (text, color) = when (state) {
        FileShareUiState.Verified -> "Verified" to SuccessGreen
        is FileShareUiState.Sharing -> "Live" to SuccessGreen
        is FileShareUiState.Uploading -> "Uploading" to CyanAccent
        is FileShareUiState.Downloading -> "Downloading" to CyanAccent
        is FileShareUiState.Verifying -> "Verifying" to CyanAccent
        is FileShareUiState.Error -> "Error" to ErrorRed
        else -> "Needed" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

private fun subtitleFor(isHost: Boolean, state: FileShareUiState): String {
    return when (state) {
        FileShareUiState.NoFileShared -> if (isHost) "Share the selected movie from this device." else "You can download from the host or select a local copy."
        is FileShareUiState.Sharing -> "${state.fileName} (${formatBytes(state.fileSize)})"
        is FileShareUiState.FileAvailable -> "${state.fileName} (${formatBytes(state.fileSize)})"
        is FileShareUiState.Uploading -> "Uploading to cloud…"
        is FileShareUiState.Downloading -> "Receiving movie from host"
        is FileShareUiState.Downloaded -> state.fileName
        is FileShareUiState.Verifying -> "Checking this file against the room fingerprint"
        FileShareUiState.Verified -> "This device has the correct movie file"
        is FileShareUiState.Error -> "Could not verify or download the file"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    val decimals = if (value >= 10.0 || unit == 0) 0 else 1
    return "%.${decimals}f %s".format(value, units[unit])
}
