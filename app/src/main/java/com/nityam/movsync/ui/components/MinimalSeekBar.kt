package com.nityam.movsync.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

@Composable
fun MinimalSeekBar(
    position: Float, // 0f to 1f
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.3f),
    thumbRadius: Float = 14f,
    trackHeight: Float = 6f
) {
    Canvas(
        modifier = modifier
            .height(24.dp)
            .fillMaxWidth()
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        val width = size.width
        val cy = size.height / 2f
        val currentX = width * position.coerceIn(0f, 1f)

        // Inactive Track
        drawRoundRect(
            color = inactiveColor,
            topLeft = Offset(0f, cy - trackHeight / 2f),
            size = Size(width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )

        // Active Track
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(0f, cy - trackHeight / 2f),
            size = Size(currentX, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )

        // Thumb Dot
        drawCircle(
            color = activeColor,
            radius = thumbRadius,
            center = Offset(currentX.coerceIn(thumbRadius, width - thumbRadius), cy)
        )
    }
}
