package com.nityam.movsync.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nityam.movsync.ui.theme.CyanAccent
import com.nityam.movsync.ui.theme.ElectricPurple

@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.98f else 1f, label = "buttonScale")
    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 54.dp)
            .scale(scale)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(ElectricPurple, CyanAccent)))
                .padding(horizontal = 18.dp, vertical = 14.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
