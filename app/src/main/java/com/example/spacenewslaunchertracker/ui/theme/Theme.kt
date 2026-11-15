package com.example.spacenewslaunchertracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

/**
 * Deep-space scheme, applied unconditionally.
 *
 * Dynamic colour is off on purpose: the launch-clock and "in plain English" accents carry
 * meaning, and letting the device wallpaper recolour them would undo that.
 */
private val DeepSpaceColors = darkColorScheme(
    primary = PlasmaCyan,
    onPrimary = PlasmaCyanDeep,
    primaryContainer = PlasmaCyanContainer,
    onPrimaryContainer = PlasmaCyan,

    secondary = NebulaViolet,
    onSecondary = NebulaVioletDeep,
    secondaryContainer = NebulaVioletContainer,
    onSecondaryContainer = StarlightHigh,

    tertiary = IgnitionAmber,
    onTertiary = IgnitionAmberDeep,
    tertiaryContainer = IgnitionAmberContainer,
    onTertiaryContainer = IgnitionAmber,

    background = SpaceVoid,
    onBackground = StarlightHigh,
    surface = SpaceSurface,
    onSurface = StarlightHigh,
    surfaceVariant = SpaceSurfaceVariant,
    onSurfaceVariant = StarlightMuted,
    surfaceContainer = SpaceSurfaceContainer,
    surfaceContainerHigh = SpaceSurfaceContainerHigh,
    surfaceContainerHighest = SpaceSurfaceVariant,

    outline = SpaceOutline,
    outlineVariant = SpaceOutlineVariant,

    error = ReentryRed,
    onError = SpaceVoid,
    errorContainer = ReentryRedContainer,
    onErrorContainer = StarlightHigh,
)

@Composable
fun SpaceNewsLauncherTrackerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DeepSpaceColors,
        typography = Typography,
        content = content,
    )
}
