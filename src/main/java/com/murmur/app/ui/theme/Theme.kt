package com.murmur.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.staticCompositionLocalOf

@Suppress("ComposeUnstableClass", "SomeOtherWarning")
val LocalChatBackground = staticCompositionLocalOf { Color.Unspecified }

val ColorScheme.logoTintBackground: Color
    @Composable
    get() = if (isSystemInDarkTheme()) Color(0xFF4066B0).copy(alpha = 1.0f) else Color(0xFF4066B0).copy(alpha = 0.3f)

private val DarkColorScheme = darkColorScheme(
    primary = MurmurWhite,
    onPrimary = MurmurBlack,
    secondary = MurmurGray,
    onSecondary = MurmurBlack,
    background = MurmurBlack,
    onBackground = MurmurWhite,
    surface = MurmurSurfaceDark,
    onSurface = MurmurOnSurfaceDark,
    primaryContainer = MurmurDarkGray
)

private val LightColorScheme = lightColorScheme(
    primary = MurmurBlack,
    onPrimary = MurmurWhite,
    secondary = MurmurDarkGray,
    onSecondary = MurmurWhite,
    background = MurmurWhite,
    onBackground = MurmurBlack,
    surface = MurmurSurfaceLight,
    onSurface = MurmurOnSurfaceLight,
    primaryContainer = MurmurLightGray,
)

@Composable
fun MurmurTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val chatBackground = if (darkTheme) ChatBackgroundDark else ChatBackgroundLight

    androidx.compose.runtime.CompositionLocalProvider(LocalChatBackground provides chatBackground) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}