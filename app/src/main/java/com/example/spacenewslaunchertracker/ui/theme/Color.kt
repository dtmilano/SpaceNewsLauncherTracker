package com.example.spacenewslaunchertracker.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * "Deep Space" palette.
 *
 * Near-black navy backgrounds stand in for the void, plasma cyan carries interactive
 * elements and live countdowns, nebula violet marks model-generated content, and ignition
 * amber flags anything transient (a passed liftoff, a rate limit). Everything is tuned for
 * a dark surface — the app does not offer a light scheme, because a launch console
 * shouldn't have one.
 */

val SpaceVoid = Color(0xFF04050E)
val SpaceSurface = Color(0xFF0A0D1C)
val SpaceSurfaceContainer = Color(0xFF111631)
val SpaceSurfaceContainerHigh = Color(0xFF171D3C)
val SpaceSurfaceVariant = Color(0xFF232A4D)
val SpaceOutline = Color(0xFF3A4370)
val SpaceOutlineVariant = Color(0xFF262D50)

val PlasmaCyan = Color(0xFF6FE6FF)
val PlasmaCyanDeep = Color(0xFF00394A)
val PlasmaCyanContainer = Color(0xFF00506A)

val NebulaViolet = Color(0xFFBFA9FF)
val NebulaVioletDeep = Color(0xFF291D52)
val NebulaVioletContainer = Color(0xFF322668)

val IgnitionAmber = Color(0xFFFFB95C)
val IgnitionAmberDeep = Color(0xFF3F2600)
val IgnitionAmberContainer = Color(0xFF5A3800)

val ReentryRed = Color(0xFFFF8F84)
val ReentryRedContainer = Color(0xFF5C1611)

val StarlightHigh = Color(0xFFE8ECFA)
val StarlightMuted = Color(0xFFA7B0D0)

/** Extra hues the Material scheme has no slot for, exposed through `LocalSpaceAccents`. */
val AuroraGreen = Color(0xFF7CF5C4)
val SolarFlare = Color(0xFFFF6E6E)
val DeepSpaceMagenta = Color(0xFFFF8AD8)
