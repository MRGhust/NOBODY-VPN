package com.nobodyiran.nobodyvpn.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkScheme = darkColorScheme(
    primary = NeonGreen,
    onPrimary = Color(0xFF06240F),
    primaryContainer = DarkGreenContainer,
    onPrimaryContainer = DarkGreenOnContainer,
    secondary = NeonGreenDim,
    onSecondary = Color(0xFF06240F),
    secondaryContainer = Color(0xFF1B2A20),
    onSecondaryContainer = Color(0xFFB7E4C7),
    tertiary = AmberWarn,
    background = CoalBg,
    onBackground = CoalText,
    surface = CoalSurface,
    onSurface = CoalText,
    surfaceVariant = CoalSurfaceHigh,
    onSurfaceVariant = CoalTextDim,
    surfaceContainer = CoalSurface,
    surfaceContainerHigh = CoalSurfaceHigh,
    surfaceContainerLow = CoalBg,
    outline = CoalOutline,
    outlineVariant = CoalOutline,
    error = DangerRed,
    onError = Color(0xFF2B0808),
    errorContainer = Color(0xFF3A1414),
    onErrorContainer = Color(0xFFFCA5A5),
    inverseSurface = Color(0xFFE7EAEE),
    inverseOnSurface = Color(0xFF171A1E)
)

private val LightScheme = lightColorScheme(
    primary = NeonGreenDim,
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = LightGreenContainer,
    onPrimaryContainer = LightGreenOnContainer,
    secondary = Color(0xFF15803D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE3F3E8),
    onSecondaryContainer = Color(0xFF14432A),
    tertiary = AmberWarn,
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceHigh,
    onSurfaceVariant = LightTextDim,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceHigh,
    surfaceContainerLow = LightBg,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDE8E8),
    onErrorContainer = Color(0xFF7F1D1D)
)

@Composable
fun NobodyTheme(
    themeMode: Int,          // 0 system, 1 light, 2 dark
    dynamicColors: Boolean,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val context = LocalContext.current
    val scheme = if (dynamicColors && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) DarkScheme else LightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AppTypography,
        content = content
    )
}
