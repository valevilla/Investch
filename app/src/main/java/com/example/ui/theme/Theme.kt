package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ScholarlyBlueDark,
    onPrimary = OnScholarBlueDark,
    primaryContainer = ScholarBlueContainerDark,
    onPrimaryContainer = SoftBlueContainer,
    secondary = DarkInactiveText,
    onSecondary = DarkOverlayBackground,
    secondaryContainer = DarkBorder,
    onSecondaryContainer = DarkTextOnSurface,
    background = DarkOverlayBackground,
    onBackground = DarkTextOnSurface,
    surface = DarkCardSurface,
    onSurface = DarkTextOnSurface,
    surfaceVariant = DarkBorder,
    onSurfaceVariant = DarkInactiveText,
    outline = DarkInactiveText,
    outlineVariant = DarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = ScholarBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = SoftBlueContainer,
    onPrimaryContainer = DarkScholarBlue,
    secondary = InactiveTextSlate,
    onSecondary = androidx.compose.ui.graphics.Color.White,
    secondaryContainer = InactiveSurfaceGray,
    onSecondaryContainer = DarkGreyOnSurface,
    background = LightScreenBackground,
    onBackground = DarkGreyOnSurface,
    surface = CardSurfaceWhite,
    onSurface = DarkGreyOnSurface,
    surfaceVariant = InactiveSurfaceGray,
    onSurfaceVariant = InactiveTextSlate,
    outline = InactiveTextSlate,
    outlineVariant = CardOutlineLavenderGray
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Set dynamicColor to false by default to ensure Clean Minimalism colors apply exactly
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
