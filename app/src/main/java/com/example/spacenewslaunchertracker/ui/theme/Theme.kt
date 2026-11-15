package com.example.spacenewslaunchertracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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

/**
 * Design tokens Material's `ColorScheme` has no slot for, read by the components in
 * `ui.components` (lifted from the Space Composify catalog). Static, because the value never
 * changes after the theme is installed.
 */
@Immutable
data class SpaceAccents(
    val aurora: Color,
    val solarFlare: Color,
    val magenta: Color,
    val clockLive: Color,
    val clockPassed: Color,
)

val LocalSpaceAccents: ProvidableCompositionLocal<SpaceAccents> = staticCompositionLocalOf {
    error("No SpaceAccents provided — wrap the content in SpaceNewsLauncherTrackerTheme")
}

/**
 * Whether decorative, always-running animations (the starfield, the ignition glow) should
 * run. Off when the user has asked the system to remove animations.
 */
val LocalAmbientMotion: ProvidableCompositionLocal<Boolean> = compositionLocalOf { true }

private val DeepSpaceAccents = SpaceAccents(
    aurora = AuroraGreen,
    solarFlare = SolarFlare,
    magenta = DeepSpaceMagenta,
    clockLive = PlasmaCyan,
    clockPassed = IgnitionAmber,
)

@Composable
fun SpaceNewsLauncherTrackerTheme(
    ambientMotion: Boolean = true,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSpaceAccents provides DeepSpaceAccents,
        LocalAmbientMotion provides ambientMotion,
    ) {
        MaterialTheme(
            colorScheme = DeepSpaceColors,
            typography = Typography,
            content = content,
        )
    }
}
