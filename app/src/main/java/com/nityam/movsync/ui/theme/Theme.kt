package com.nityam.movsync.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ElectricPurple,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = CyanAccent,
    tertiary = ReelGold,
    background = CinemaBlack,
    onBackground = androidx.compose.ui.graphics.Color.White,
    surface = DeepSurface,
    onSurface = androidx.compose.ui.graphics.Color.White,
    surfaceVariant = SoftSurface,
    error = ErrorRed
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    secondary = LightSecondary,
    tertiary = ReelGold,
    background = LightBackground,
    surface = androidx.compose.ui.graphics.Color.White,
    error = ErrorRed
)

@Composable
fun MovsyncTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
