package com.murmur.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class MurmurExtraColors(
    val proGold: Color,
    val onProGold: Color,
    val proGoldContainer: Color,
    val onProGoldContainer: Color,
)

val LocalMurmurExtraColors = staticCompositionLocalOf {
    // Safe defaults if not provided
    MurmurExtraColors(
        proGold = Color(0xFFFFD700),
        onProGold = Color(0xFF000000),
        proGoldContainer = Color(0xFFFFF4CC),
        onProGoldContainer = Color(0xFF3A2C00)
    )
}

// Nice ergonomic access: MaterialTheme.extraColors.proGold, etc.
val MaterialTheme.extraColors: MurmurExtraColors
    @Composable get() = LocalMurmurExtraColors.current
