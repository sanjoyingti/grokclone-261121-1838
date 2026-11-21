package com.arin.grokclone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColors = darkColorScheme(
    primary = GrokWhite,
    onPrimary = GrokInkDark,
    primaryContainer = GrokUserBubbleDark,
    onPrimaryContainer = GrokWhite,
    secondary = GrokGreyLight,
    onSecondary = GrokInkDark,
    secondaryContainer = GrokSurfaceVariantDark,
    onSecondaryContainer = GrokWhite,
    tertiary = GrokGreyMid,
    onTertiary = GrokInkDark,
    background = GrokBlack,
    onBackground = GrokWhite,
    surface = GrokNearBlack,
    onSurface = GrokWhite,
    surfaceVariant = GrokSurfaceDark,
    onSurfaceVariant = GrokGreyLight,
    surfaceContainer = GrokSurfaceDark,
    surfaceContainerHigh = GrokSurfaceVariantDark,
    outline = GrokOutlineDark,
    outlineVariant = GrokOutlineDark,
    error = GrokErrorDark,
    onError = GrokInkDark,
    errorContainer = GrokUserBubbleDark,
    onErrorContainer = GrokErrorDark
)

private val LightColors = lightColorScheme(
    primary = GrokInkDark,
    onPrimary = GrokWhite,
    primaryContainer = GrokLightSurfaceVariant,
    onPrimaryContainer = GrokInkDark,
    secondary = GrokLightOnSurfaceVariant,
    onSecondary = GrokWhite,
    secondaryContainer = GrokLightSurfaceVariant,
    onSecondaryContainer = GrokInkDark,
    tertiary = GrokGreyMid,
    onTertiary = GrokWhite,
    background = GrokLightBackground,
    onBackground = GrokInkDark,
    surface = GrokLightSurface,
    onSurface = GrokInkDark,
    surfaceVariant = GrokLightSurfaceVariant,
    onSurfaceVariant = GrokLightOnSurfaceVariant,
    surfaceContainer = GrokLightSurfaceVariant,
    surfaceContainerHigh = GrokLightSurfaceVariant,
    outline = GrokLightOutline,
    outlineVariant = GrokLightOutline,
    error = GrokErrorLight,
    onError = GrokWhite,
    errorContainer = GrokLightSurfaceVariant,
    onErrorContainer = GrokErrorLight
)

@Composable
fun GrokCloneTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = GrokTypography,
        content = content
    )
}
