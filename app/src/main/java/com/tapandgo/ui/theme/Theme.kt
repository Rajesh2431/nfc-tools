package com.tapandgo.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tapandgo.ui.theme.Brand as B

private val LightColors = lightColorScheme(
    primary = B.Blue40, onPrimary = B.N98, primaryContainer = B.Blue90, onPrimaryContainer = B.Blue10,
    secondary = B.Cyan40, onSecondary = B.N98, secondaryContainer = B.Cyan90, onSecondaryContainer = B.Cyan10,
    tertiary = B.Indigo40, onTertiary = B.N98, tertiaryContainer = B.Indigo90, onTertiaryContainer = B.Indigo10,
    error = B.Error40, onError = B.N98, errorContainer = B.Error90, onErrorContainer = B.Error10,
    background = B.N98, onBackground = B.N10, surface = B.N98, onSurface = B.N10,
    surfaceVariant = B.NV90, onSurfaceVariant = B.NV30, outline = B.NV50, outlineVariant = B.NV80,
    surfaceContainerLowest = androidx.compose.ui.graphics.Color.White, surfaceContainerLow = B.N96,
    surfaceContainer = B.N94, surfaceContainerHigh = B.N90, surfaceContainerHighest = B.NV90,
)

private val DarkColors = darkColorScheme(
    primary = B.Blue80, onPrimary = B.Blue20, primaryContainer = B.Blue30, onPrimaryContainer = B.Blue90,
    secondary = B.Cyan80, onSecondary = B.Cyan20, secondaryContainer = B.Cyan30, onSecondaryContainer = B.Cyan90,
    tertiary = B.Indigo80, onTertiary = B.Indigo20, tertiaryContainer = B.Indigo30, onTertiaryContainer = B.Indigo90,
    error = B.Error80, onError = B.Error20, errorContainer = B.Error30, onErrorContainer = B.Error90,
    background = B.N6, onBackground = B.N90, surface = B.N6, onSurface = B.N90,
    surfaceVariant = B.NV30, onSurfaceVariant = B.NV80, outline = B.NV50, outlineVariant = B.NV30,
    surfaceContainerLowest = B.N4, surfaceContainerLow = B.N10, surfaceContainer = B.N12,
    surfaceContainerHigh = B.N17, surfaceContainerHighest = B.N22,
)

private val TapAndGoShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Tap & Go theme. Brand colors by default; Material You dynamic color is an
 * opt-in (Settings › Appearance) on Android 12+.
 */
@Composable
fun TapAndGoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
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
    MaterialTheme(colorScheme = colorScheme, typography = NfcTypography, shapes = TapAndGoShapes, content = content)
}
