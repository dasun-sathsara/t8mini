package dev.dasun.t8mini.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.dasun.t8mini.R
import dev.dasun.t8mini.protocol.LedMode

val Bg = Color(0xFF0C0D12)
val Surface1 = Color(0xFF151720)
val Surface2 = Color(0xFF20232E)
val Line = Color(0xFF303441)
val TextMain = Color(0xFFECECF1)
val TextDim = Color(0xFFA4A9B8)
val TextMuted = Color(0xFF55555F)

val Feixen = FontFamily(
    Font(R.font.feixen_regular, FontWeight.Normal),
    Font(R.font.feixen_bold, FontWeight.Bold),
)

val Geist = FontFamily(Font(R.font.geist_regular, FontWeight.Normal))

/** Used while the light is off, so the page does not glow in an unused colour. */
val AccentOff = Color(0xFFB9C9E8)

fun accentFor(mode: LedMode, lightOn: Boolean = true): Color = when {
    !lightOn -> AccentOff
    mode == LedMode.FASHION_PARTY -> Color(0xFFFF2D78)
    mode == LedMode.DEEP_BREATH -> Color(0xFF22D3EE)
    else -> Color(0xFFF59E0B)
}

fun accentAltFor(mode: LedMode): Color = when (mode) {
    LedMode.FASHION_PARTY -> Color(0xFF7C5CFF)
    LedMode.DEEP_BREATH -> Color(0xFF3B82F6)
    LedMode.CAROUSEL -> Color(0xFFEF4444)
}

private val T8Typography = Typography(
    displaySmall = TextStyle(
        fontFamily = Feixen,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.02).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Feixen,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.01).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Feixen,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Feixen,
        fontWeight = FontWeight.Normal,
        fontSize = 14.5.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Feixen,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = Feixen,
        fontWeight = FontWeight.Bold,
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.9.sp,
    ),
)

@Composable
fun T8Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFFF2D78),
            onPrimary = Color.White,
            background = Bg,
            onBackground = TextMain,
            surface = Surface1,
            onSurface = TextMain,
            surfaceVariant = Surface2,
            onSurfaceVariant = TextDim,
            outline = Line,
        ),
        typography = T8Typography,
    ) {
        // MaterialTheme does not set LocalContentColor; without this every Text
        // that omits a colour falls back to black.
        CompositionLocalProvider(LocalContentColor provides TextMain, content = content)
    }
}
