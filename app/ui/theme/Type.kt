package com.murmur.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.Font
import com.murmur.app.R


val LexendFont = FontFamily(
    Font(R.font.lexend_variablefont_wght),
)

// Set of Material typography styles to start with
val Typography = Typography(


    displayLarge = TextStyle(
        fontFamily = LexendFont,
        fontSize = 57.sp
    ),
    displayMedium = TextStyle(
        fontFamily = LexendFont,
        fontSize = 45.sp
    ),
    displaySmall = TextStyle(
        fontFamily = LexendFont,
        fontSize = 36.sp
    ),
    titleLarge = TextStyle(
        fontFamily = LexendFont,
        fontSize = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = LexendFont,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    labelLarge = TextStyle(
        fontFamily = LexendFont,
        fontSize = 14.sp
    )
    /* Other default text styles to override
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
    */
)