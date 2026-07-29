package com.autotransfer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val SithColorScheme = darkColorScheme(
    primary = SithRed,
    onPrimary = SithOnPrimary,
    primaryContainer = SithDimRed,
    onPrimaryContainer = SithTextPrimary,
    secondary = SithDeepRed,
    onSecondary = SithOnPrimary,
    secondaryContainer = SithCard,
    onSecondaryContainer = SithTextPrimary,
    tertiary = SithHighlight,
    onTertiary = SithOnPrimary,
    background = SithBlack,
    onBackground = SithTextPrimary,
    surface = SithSurface,
    onSurface = SithTextPrimary,
    surfaceVariant = SithCard,
    onSurfaceVariant = SithTextSecondary,
    error = SithError,
    onError = SithOnPrimary,
    errorContainer = SithDimRed,
    onErrorContainer = SithTextPrimary,
    outline = SithDivider,
    outlineVariant = SithDivider,
    inverseSurface = SithTextPrimary,
    inverseOnSurface = SithBlack,
    inversePrimary = SithDeepRed,
    surfaceTint = SithRed
)

@Composable
fun SithTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SithColorScheme
    ) {
        SithBackground {
            content()
        }
    }
}
