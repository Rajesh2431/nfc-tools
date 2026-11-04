package com.tapandgo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = NfcPrimary,
    onPrimary = NfcOnPrimary,
    secondary = NfcPrimary,
    onSecondary = NfcOnPrimary,
    background = NfcSurfaceLight,
    onBackground = NfcOnSurfaceLight,
    surface = NfcSurfaceLight,
    onSurface = NfcOnSurfaceLight,
    surfaceContainer = NfcSurfaceContainerLight,
    outlineVariant = NfcOutlineVariantLight,
    error = NfcErrorLight,
)

private val DarkColors = darkColorScheme(
    primary = NfcPrimaryDim,
    onPrimary = NfcPrimary,
    secondary = NfcPrimaryDim,
    onSecondary = NfcPrimary,
    background = NfcSurfaceDark,
    onBackground = NfcOnSurfaceDark,
    surface = NfcSurfaceDark,
    onSurface = NfcOnSurfaceDark,
    surfaceContainer = NfcSurfaceContainerDark,
    outlineVariant = NfcOutlineVariantDark,
    error = NfcErrorDark,
)

/**
 * Application theme. Follows the system dark/light setting; uses dynamic
 * wallpaper-based colors on Android 12+ since no brand override is required.
 */
@Composable
fun TapAndGoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = NfcTypography,
        content = content,
    )
}
