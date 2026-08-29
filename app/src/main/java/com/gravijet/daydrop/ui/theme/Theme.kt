package com.gravijet.daydrop.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.gravijet.daydrop.data.model.DropType

// DayDrop is a night-time reading surface: near-black paper, one saturated
// accent per card type, nothing else competing for attention.
val Ink = Color(0xFF08080E)
val InkElevated = Color(0xFF14141F)
val InkBorder = Color(0xFF262636)
val Chalk = Color(0xFFF4F3FB)
val ChalkDim = Color(0xFF9E9DB4)
val Accent = Color(0xFFFFB347)

private val DayDropColors = darkColorScheme(
    primary = Accent,
    onPrimary = Ink,
    secondary = Color(0xFF7B8CFF),
    background = Ink,
    onBackground = Chalk,
    surface = InkElevated,
    onSurface = Chalk,
    surfaceVariant = InkElevated,
    onSurfaceVariant = ChalkDim,
    outline = InkBorder,
    error = Color(0xFFFF6B6B)
)

/** The two-stop gradient that gives every card type its own identity. */
data class DropPalette(val top: Color, val bottom: Color, val glow: Color) {
    val brush: Brush get() = Brush.linearGradient(listOf(top, bottom))
}

fun paletteFor(type: DropType): DropPalette = when (type) {
    DropType.TODAY_IS -> DropPalette(Color(0xFFFFC46B), Color(0xFFE0543A), Color(0xFFFFB347))
    DropType.HISTORY -> DropPalette(Color(0xFF8E9DFF), Color(0xFF2E2470), Color(0xFF8E9DFF))
    DropType.FACT -> DropPalette(Color(0xFFFF6FAE), Color(0xFF6E1550), Color(0xFFFF6FAE))
    DropType.DID_YOU_KNOW -> DropPalette(Color(0xFF3FE0AC), Color(0xFF0B4E55), Color(0xFF3FE0AC))
    DropType.SCIENCE -> DropPalette(Color(0xFF5CC8FF), Color(0xFF16307E), Color(0xFF5CC8FF))
    DropType.POP_CULTURE -> DropPalette(Color(0xFFFF8A6B), Color(0xFF7C1550), Color(0xFFFF8A6B))
    DropType.QUIZ -> DropPalette(Color(0xFFBE9BFF), Color(0xFF3F2280), Color(0xFFBE9BFF))
    DropType.RANDOM -> DropPalette(Color(0xFFCBFF5E), Color(0xFF265C1B), Color(0xFFCBFF5E))
}

private val DayDropTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Black,
        fontSize = 44.sp,
        lineHeight = 48.sp,
        letterSpacing = (-1.4).sp,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.None
        )
    ),
    displayMedium = TextStyle(
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        lineHeight = 39.sp,
        letterSpacing = (-1.0).sp
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.5).sp
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        lineHeight = 23.sp
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.1.sp
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.6.sp
    ),
    labelMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        lineHeight = 17.sp
    )
)

@Composable
fun DayDropTheme(
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // Deliberately dark in both system modes - the cards are built around
    // saturated gradients that only hold up on a dark ground.
    MaterialTheme(
        colorScheme = DayDropColors,
        typography = DayDropTypography,
        content = content
    )
}
