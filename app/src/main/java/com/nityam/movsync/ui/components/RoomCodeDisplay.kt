package com.nityam.movsync.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun RoomCodeDisplay(
    code: String,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable {
                clipboard.setText(AnnotatedString(code))
                scope.launch { snackbarHostState.showSnackbar("Room code copied") }
            }
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = code.chunked(3).joinToString(" "),
                style = MaterialTheme.typography.displayMedium,
                fontFamily = FontFamily.Monospace
            )
            Text(text = "Tap to copy", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
