package com.murmur.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.murmur.app.R


// Make sure the file exists at: app/src/main/res/font/lexend_variablefont_wght.ttf (or .otf)
val Lexend = FontFamily(
    Font(R.font.lexend_variablefont_wght)
)

// Helper: apply a font family to every Material3 default style
private fun Typography.withFontFamily(family: FontFamily): Typography = Typography(
    displayLarge   = displayLarge.copy(fontFamily = family),
    displayMedium  = displayMedium.copy(fontFamily = family),
    displaySmall   = displaySmall.copy(fontFamily = family),

    headlineLarge  = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall  = headlineSmall.copy(fontFamily = family),

    titleLarge     = titleLarge.copy(fontFamily = family),
    titleMedium    = titleMedium.copy(fontFamily = family),
    titleSmall     = titleSmall.copy(fontFamily = family),

    bodyLarge      = bodyLarge.copy(fontFamily = family),
    bodyMedium     = bodyMedium.copy(fontFamily = family),
    bodySmall      = bodySmall.copy(fontFamily = family),

    labelLarge = labelLarge.copy(
        fontFamily = family,
        fontSize = 18.sp,
        fontWeight = FontWeight.Normal
    ),
    labelMedium    = labelMedium.copy(fontFamily = family),
    labelSmall     = labelSmall.copy(fontFamily = family),
)

// This is what Theme.kt uses
val AppTypography: Typography = Typography().withFontFamily(Lexend)
