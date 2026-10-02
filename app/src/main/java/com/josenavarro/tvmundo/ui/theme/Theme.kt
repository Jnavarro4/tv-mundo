package com.josenavarro.tvmundo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

object TvColors {
    val Background = Color(0xFF0E1116)
    val Surface = Color(0xFF1A1F27)
    val SurfaceFocused = Color(0xFF2A3240)
    val Accent = Color(0xFFFFB300)
    val OnSurface = Color(0xFFECEFF4)
    val OnSurfaceDim = Color(0xFF9AA4B2)
    val FocusBorder = Color(0xFFFFFFFF)
    val Error = Color(0xFFFF6B6B)

    /** Fondo detrás de los logos: tono medio para que se vean tanto logos oscuros como claros. */
    val LogoBackground = Color(0xFF9AA5B4)
}

@Composable
fun TvMundoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = TvColors.Accent,
            onPrimary = Color.Black,
            secondary = TvColors.Accent,
            background = TvColors.Background,
            onBackground = TvColors.OnSurface,
            surface = TvColors.Surface,
            onSurface = TvColors.OnSurface,
            surfaceVariant = TvColors.SurfaceFocused,
            onSurfaceVariant = TvColors.OnSurfaceDim,
            border = TvColors.FocusBorder,
            error = TvColors.Error,
        ),
    ) {
        CompositionLocalProvider(LocalContentColor provides TvColors.OnSurface, content = content)
    }
}
