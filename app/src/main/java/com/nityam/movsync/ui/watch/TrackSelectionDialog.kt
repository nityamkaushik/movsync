package com.nityam.movsync.ui.watch

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun TrackSelectionDialog(
    player: Player,
    trackType: Int,
    onDismiss: () -> Unit
) {
    val title = if (trackType == C.TRACK_TYPE_AUDIO) "Select Audio Track" else "Select Subtitles"
    val tracks = player.currentTracks
    val groups = tracks.groups.filter { it.type == trackType }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn {
                item {
                    TrackOption(
                        name = "None / Default",
                        selected = !groups.any { it.isSelected },
                        onClick = {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .clearOverridesOfType(trackType)
                                .apply {
                                    if (trackType == C.TRACK_TYPE_TEXT) {
                                        setIgnoredTextSelectionFlags(C.SELECTION_FLAG_DEFAULT.inv())
                                    }
                                }
                                .build()
                            onDismiss()
                        }
                    )
                }
                groups.forEach { group ->
                    itemsIndexed(
                        items = List(group.length) { it },
                        key = { index, _ -> "${group.mediaTrackGroup.hashCode()}_$index" }
                    ) { _, trackIndex ->
                        val format = group.getTrackFormat(trackIndex)
                        val label = format.label?.takeIf { it.isNotBlank() }
                        val language = format.language?.takeIf { it.isNotBlank() }
                        val name = if (label != null && language != null) {
                            if (label.contains(language, ignoreCase = true)) label else "$label [$language]"
                        } else {
                            label ?: language ?: "Track ${trackIndex + 1}"
                        }
                        TrackOption(
                            name = name,
                            selected = group.isTrackSelected(trackIndex),
                            onClick = {
                                player.trackSelectionParameters = player.trackSelectionParameters
                                    .buildUpon()
                                    .clearOverridesOfType(trackType)
                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
                                    .build()
                                onDismiss()
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun TrackOption(
    name: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
