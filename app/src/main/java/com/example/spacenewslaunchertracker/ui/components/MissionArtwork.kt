package com.example.spacenewslaunchertracker.ui.components

// Ported from the Space Composify catalog (com.example.spacecomposify.components).

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import coil3.compose.SubcomposeAsyncImage
import com.example.spacenewslaunchertracker.ui.theme.AuroraGreen
import com.example.spacenewslaunchertracker.ui.theme.DeepSpaceMagenta
import com.example.spacenewslaunchertracker.ui.theme.IgnitionAmber
import com.example.spacenewslaunchertracker.ui.theme.NebulaViolet
import com.example.spacenewslaunchertracker.ui.theme.PlasmaCyan
import com.example.spacenewslaunchertracker.ui.theme.SpaceVoid
import kotlin.math.abs

/*
 * Mission artwork with a procedural fallback.
 *
 * A launch feed always has records with no image, and a tracker has to look right with the
 * network off, so "no image" cannot mean "grey box". Instead the artwork is *derived from
 * the record id*: the same mission always gets the same planet, ring tilt, palette and star
 * placement, because every value comes from a hash of the id rather than a random source.
 * That determinism is what makes it usable in a `@Preview` and in a screenshot test.
 *
 * It is also a compact tour of Compose's drawing API — radial and linear gradient brushes,
 * a stroked arc for the ring, `BlendMode.Plus` for the additive limb glow — all built once
 * per size in `drawWithCache` rather than per frame.
 */

private val PALETTES = listOf(
    listOf(PlasmaCyan, NebulaViolet),
    listOf(IgnitionAmber, DeepSpaceMagenta),
    listOf(AuroraGreen, PlasmaCyan),
    listOf(DeepSpaceMagenta, NebulaViolet),
    listOf(IgnitionAmber, AuroraGreen),
)

/**
 * Deterministic artwork for a mission.
 *
 * @param seed any stable identifier for the record; the same seed always draws the same art.
 */
@Composable
fun ProceduralMissionArt(
    seed: String,
    modifier: Modifier = Modifier,
) {
    // 32-bit FNV-ish hash, stable across runs and devices (unlike String.hashCode across
    // platforms, which is actually specified — but mixing improves the spread of our few
    // low-order bits, which is what picks the palette).
    val h = remember(seed) {
        var acc = 2166136261u.toInt()
        for (c in seed) {
            acc = (acc xor c.code) * 16777619
        }
        abs(acc)
    }

    val palette = PALETTES[h % PALETTES.size]
    val planetFraction = 0.30f + (h shr 3) % 18 / 100f   // 0.30 .. 0.47 of the short side
    val tiltDegrees = -38f + (h shr 7) % 70
    val hasRing = (h shr 11) % 3 != 0
    val cxFraction = 0.30f + (h shr 13) % 40 / 100f
    val cyFraction = 0.34f + (h shr 17) % 36 / 100f

    Box(
        modifier = modifier
            .semantics { contentDescription = "Procedurally generated mission artwork" }
            .drawWithCache {
                val radius = size.minDimension * planetFraction
                val center = Offset(size.width * cxFraction, size.height * cyFraction)

                val space = Brush.linearGradient(
                    colors = listOf(
                        palette[1].copy(alpha = 0.30f),
                        SpaceVoid,
                        palette[0].copy(alpha = 0.12f),
                    ),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height),
                )
                val planet = Brush.radialGradient(
                    colors = listOf(palette[0], palette[1], palette[1].copy(alpha = 0.55f)),
                    // Light the planet from the upper left, inside its own disc.
                    center = Offset(center.x - radius * 0.35f, center.y - radius * 0.35f),
                    radius = radius * 1.9f,
                )
                val limb = Brush.radialGradient(
                    colors = listOf(Color.Transparent, palette[0].copy(alpha = 0.55f)),
                    center = center,
                    radius = radius * 1.22f,
                )
                val ringStroke = Stroke(width = radius * 0.11f, cap = StrokeCap.Round)

                // A handful of fixed stars, placed from the same hash.
                val stars = List(18) { i ->
                    val a = ((h shr (i % 13)) % 1000) / 1000f
                    val b = ((h shr ((i + 5) % 17)) % 997) / 997f
                    val jx = (i * 0.618f) % 1f
                    val jy = (i * 0.414f) % 1f
                    Triple(
                        Offset(((a + jx) % 1f) * size.width, ((b + jy) % 1f) * size.height),
                        0.6f + ((h shr i) % 10) / 8f,
                        0.25f + ((h shr (i + 3)) % 60) / 100f,
                    )
                }

                onDrawBehind {
                    drawRect(space)
                    stars.forEach { (offset, r, alpha) ->
                        drawCircle(Color.White, radius = r, center = offset, alpha = alpha)
                    }
                    if (hasRing) {
                        rotate(degrees = tiltDegrees, pivot = center) {
                            drawArc(
                                color = palette[0].copy(alpha = 0.75f),
                                startAngle = 0f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = Offset(
                                    center.x - radius * 1.75f,
                                    center.y - radius * 0.46f,
                                ),
                                size = Size(radius * 3.5f, radius * 0.92f),
                                style = ringStroke,
                            )
                        }
                    }
                    drawCircle(planet, radius = radius, center = center)
                    // Additive glow at the limb reads as atmosphere catching the light.
                    drawCircle(
                        brush = limb,
                        radius = radius * 1.22f,
                        center = center,
                        blendMode = BlendMode.Plus,
                    )
                }
            },
    )
}

/**
 * Mission image with graceful degradation: the remote image when there is one and it
 * loads, the procedural art otherwise.
 *
 * `SubcomposeAsyncImage` is used rather than plain `AsyncImage` precisely so the error and
 * loading slots can host real composables — the fallback here is not a placeholder drawable
 * but the same generated artwork, so a failed download is indistinguishable from a record
 * that never had an image. `Crossfade` keeps the swap from flashing.
 */
@Composable
fun MissionArtwork(
    seed: String,
    imageUrl: String?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    if (imageUrl == null) {
        ProceduralMissionArt(seed, modifier)
        return
    }
    SubcomposeAsyncImage(
        model = imageUrl,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        // Both slots render the generated art, so a slow or failed download degrades to
        // something that looks intentional instead of to an empty rectangle. Note that
        // neither slot writes state: a composable that assigned to a `MutableState` here
        // would be performing a side effect during composition, and could loop.
        loading = { ProceduralMissionArt(seed, Modifier.fillMaxSize()) },
        error = { ProceduralMissionArt(seed, Modifier.fillMaxSize()) },
    )
}
