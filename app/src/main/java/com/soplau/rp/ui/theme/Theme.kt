package com.soplau.rp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.soplau.rp.data.settings.AppTheme

val PinkStart = Color(0xFFFF6FB5)
val PinkEnd = Color(0xFFC2185B)
val PinkDeep = Color(0xFF2B0A1C)
val PinkSurface = Color(0xFF3A1027)
val BlackBg = Color(0xFF000000)
val BlackSurface = Color(0xFF121212)
val BlackSurfaceHigh = Color(0xFF1E1E1E)

/** Full-screen background brush: a gradient for the pink theme, flat black for the black theme. */
val LocalAppBackground = compositionLocalOf<Brush> { Brush.verticalGradient(listOf(BlackBg, BlackBg)) }

/** Accent colour for *actions* (the text between asterisks). */
val LocalActionColor = compositionLocalOf { PinkStart }

private val PinkColors = darkColorScheme(
    primary = PinkStart,
    onPrimary = Color.Black,
    primaryContainer = PinkEnd,
    onPrimaryContainer = Color.White,
    secondary = Color(0xFFFFB3D9),
    onSecondary = Color.Black,
    background = PinkDeep,
    onBackground = Color(0xFFFFE6F2),
    surface = PinkSurface,
    onSurface = Color(0xFFFFE6F2),
    surfaceVariant = Color(0xFF4A1A36),
    onSurfaceVariant = Color(0xFFFFCCE5),
    outline = PinkEnd
)

private val BlackColors = darkColorScheme(
    primary = Color(0xFFFF4FA3),
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF3D0F26),
    onPrimaryContainer = Color(0xFFFFD6EA),
    secondary = Color(0xFFBBBBBB),
    onSecondary = Color.Black,
    background = BlackBg,
    onBackground = Color(0xFFEDEDED),
    surface = BlackSurface,
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = BlackSurfaceHigh,
    onSurfaceVariant = Color(0xFFCFCFCF),
    outline = Color(0xFF444444)
)

@Composable
fun SoPlauTheme(themeChoice: AppTheme?, content: @Composable () -> Unit) {
    val choice = themeChoice ?: AppTheme.PINK_GRADIENT
    val isPink = choice == AppTheme.PINK_GRADIENT

    val background = if (isPink) {
        Brush.verticalGradient(listOf(PinkDeep, PinkEnd.copy(alpha = 0.55f), PinkDeep))
    } else {
        Brush.verticalGradient(listOf(BlackBg, BlackBg))
    }
    val actionColor = if (isPink) PinkStart else Color(0xFFFF4FA3)

    CompositionLocalProvider(
        LocalAppBackground provides background,
        LocalActionColor provides actionColor
    ) {
        MaterialTheme(
            colorScheme = if (isPink) PinkColors else BlackColors,
            typography = MaterialTheme.typography,
            content = content
        )
    }
}
