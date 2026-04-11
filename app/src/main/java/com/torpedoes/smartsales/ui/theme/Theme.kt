package com.torpedoes.smartsales.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SmartSalesDarkColors = darkColorScheme(
    primary        = BrandOrange,
    onPrimary      = OnSurfaceLight,
    secondary      = BrandAmber,
    onSecondary    = SurfaceDark,
    background     = SurfaceDark,
    onBackground   = OnSurfaceLight,
    surface        = SurfaceMid,
    onSurface      = OnSurfaceLight,
    surfaceVariant = SurfaceCard,
    error          = ErrorRed,
    onError        = OnSurfaceLight,
)

@Composable
fun SmartSalesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SmartSalesDarkColors,
        typography  = Typography,
        content     = content
    )
}