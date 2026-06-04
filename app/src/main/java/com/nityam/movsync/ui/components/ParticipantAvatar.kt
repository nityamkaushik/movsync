package com.nityam.movsync.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.nityam.movsync.ui.theme.CyanAccent
import com.nityam.movsync.ui.theme.ElectricPurple
import com.nityam.movsync.ui.theme.SuccessGreen
import com.nityam.movsync.ui.theme.SoftSurface

@Composable
fun ParticipantAvatar(
    name: String,
    online: Boolean,
    modifier: Modifier = Modifier
) {
    val badgeColor by animateColorAsState(
        targetValue = if (online) SuccessGreen else SoftSurface,
        animationSpec = tween(400),
        label = "badgeColor"
    )

    Box(modifier = modifier) {
        // Avatar with gradient background
        Surface(
            modifier = Modifier.size(46.dp),
            shape = CircleShape,
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        brush = Brush.linearGradient(
                            listOf(ElectricPurple, CyanAccent)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.firstOrNull()?.uppercase() ?: "?",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // Online badge
        Box(
            modifier = Modifier
                .size(14.dp)
                .offset(x = 32.dp, y = 32.dp)
                .background(badgeColor, CircleShape)
                .background(Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .align(Alignment.Center)
                    .background(badgeColor, CircleShape)
            )
        }
    }
}
