package com.docwarp.scanner.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = PrecisionEmerald,
    onPrimary = CanvasBlack,
    primaryContainer = CharcoalGlass,
    onPrimaryContainer = PrecisionEmerald,
    secondary = NeonCyan,
    onSecondary = CanvasBlack,
    surface = CharcoalGlass,
    onSurface = TextPrimary,
    background = CanvasBlack,
    onBackground = TextPrimary,
    error = CoralCrimson,
    onError = CanvasBlack
)

@Composable
fun DocWarpTheme(
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = CanvasBlack.toArgb()
            window.navigationBarColor = CanvasBlack.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DocWarpTypography,
        content = content
    )
}
