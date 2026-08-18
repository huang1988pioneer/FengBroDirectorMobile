package com.fengbro.director.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Scheme = darkColorScheme(
    primary = Primary,
    onPrimary = PrimaryInk,
    secondary = Gold,
    background = BgApp,
    surface = BgPanel,
    surfaceVariant = BgPanel2,
    onBackground = Ink,
    onSurface = Ink,
    onSurfaceVariant = Muted,
    outline = Line,
    error = Danger,
    onError = Color.White,
)

@Composable
fun FengBroDirectorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        content = content,
    )
}
