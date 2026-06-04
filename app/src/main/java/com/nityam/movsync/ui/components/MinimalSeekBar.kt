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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.nityam.movsync.ui.theme.CyanAccent
import com.nityam.movsync.ui.theme.ElectricPurple

@Composable
fun MinimalSeekBar(
    position: Float, // 0f to 1f
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.18f),
    thumbRadius: Float = 14f,
    trackHeight: Float = 5f
) {
    Canvas(
        modifier = modifier
            .height(28.dp)
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
        val currentX = (width * position.coerceIn(0f, 1f))

        // Inactive track (background)
        drawRoundRect(
            color = inactiveColor,
            topLeft = Offset(0f, cy - trackHeight / 2f),
            size = Size(width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2f)
        )

        // Active track with gradient
        if (currentX > 0f) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(ElectricPurple, CyanAccent),
                    startX = 0f,
                    endX = currentX.coerceAtLeast(1f)
                ),
                topLeft = Offset(0f, cy - trackHeight / 2f),
                size = Size(currentX, trackHeight),
                cornerRadius = CornerRadius(trackHeight / 2f)
            )
        }

        // Thumb
        val thumbX = currentX.coerceIn(thumbRadius, width - thumbRadius)
        // Shadow/glow
        drawCircle(
            color = Color.White.copy(alpha = 0.2f),
            radius = thumbRadius + 5f,
            center = Offset(thumbX, cy)
        )
        drawCircle(
            color = activeColor,
            radius = thumbRadius,
            center = Offset(thumbX, cy)
        )
    }
}
