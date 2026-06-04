package com.nityam.movsync.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ElectricPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2D1B69),
    onPrimaryContainer = PurpleMid,
    secondary = CyanAccent,
    onSecondary = Color(0xFF0D3040),
    secondaryContainer = Color(0xFF0A2535),
    onSecondaryContainer = CyanAccent,
    tertiary = ReelGold,
    onTertiary = Color(0xFF2D1A00),
    background = CinemaBlack,
    onBackground = Color(0xFFF0EEFF),
    surface = DeepSurface,
    onSurface = Color(0xFFEAE8FF),
    surfaceVariant = SoftSurface,
    onSurfaceVariant = Color(0xFFBBB8D4),
    outline = Color(0xFF4D4B6B),
    outlineVariant = Color(0xFF2E2C46),
    error = ErrorRed,
    onError = Color.White,
    scrim = Color(0xFF000000)
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = LightPrimary,
    secondary = LightSecondary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = LightSecondary,
    tertiary = ReelGold,
    background = LightBackground,
    onBackground = Color(0xFF1C1B2E),
    surface = Color.White,
    onSurface = Color(0xFF1C1B2E),
    surfaceVariant = Color(0xFFEFEBFF),
    onSurfaceVariant = Color(0xFF49476A),
    outline = Color(0xFFBAB8D0),
    error = ErrorRed
)

@Composable
fun MovsyncTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false, // Keep false — our palette is intentional
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
