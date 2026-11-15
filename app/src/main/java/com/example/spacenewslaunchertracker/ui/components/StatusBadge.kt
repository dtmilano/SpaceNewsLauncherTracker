package com.example.spacenewslaunchertracker.ui.components

// Ported from the Space Composify catalog (com.example.spacecomposify.components).

import android.graphics.Matrix
import android.graphics.Path as AndroidPath
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.circle
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import com.example.spacenewslaunchertracker.ui.theme.LocalSpaceAccents

/*
 * A status glyph that *morphs between shapes* as the launch status changes, using
 * `androidx.graphics:graphics-shapes`.
 *
 * Interpolating between two outlines is not something you can do by animating a `Shape`:
 * a rounded rectangle and a star have no common parameterisation, so a naive crossfade
 * either pops or dissolves. `Morph` solves the hard part — it matches up the two polygons'
 * features and produces a continuous family of curves between them, so a single animated
 * float sweeps one shape smoothly into the other.
 *
 * The usual drawing-efficiency rule applies: the `Morph`, the
 * reusable `android.graphics.Path`, and the `Matrix` are all created once and reused, since
 * `Morph.toPath` happily writes into a path you already own.
 */

/** The three states a launch badge can be in, each with its own shape and colour. */
enum class LaunchStatus { GO, PENDING, DONE }

fun statusOf(statusIsGo: Boolean, hasLiftedOff: Boolean): LaunchStatus = when {
    hasLiftedOff -> LaunchStatus.DONE
    statusIsGo -> LaunchStatus.GO
    else -> LaunchStatus.PENDING
}

/**
 * Morphing status badge.
 *
 * `GO` is a sharp burst (a star), `PENDING` a soft blob, `DONE` a clean circle — so the
 * state is legible from the silhouette alone, not only from the colour. That matters: a
 * badge distinguished purely by hue is invisible to a red-green colour-blind viewer, and
 * this one still reads in greyscale.
 */
@Composable
fun MorphingStatusBadge(
    status: LaunchStatus,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val accents = LocalSpaceAccents.current
    val scheme = MaterialTheme.colorScheme

    // Each shape is normalised to a unit circle at the origin, so one matrix scales any of
    // them to the drawing bounds.
    val shapes = remember {
        mapOf(
            LaunchStatus.GO to RoundedPolygon.star(
                numVerticesPerRadius = 7,
                innerRadius = 0.62f,
                rounding = CornerRounding(0.22f),
                innerRounding = CornerRounding(0.16f),
            ),
            LaunchStatus.PENDING to RoundedPolygon(
                numVertices = 6,
                rounding = CornerRounding(0.48f),
            ),
            LaunchStatus.DONE to RoundedPolygon.circle(numVertices = 12),
        )
    }

    // One Morph per adjacent pair, built once. The badge animates a single progress value
    // across the chain GO (0) -> PENDING (1) -> DONE (2).
    val morphs = remember(shapes) {
        listOf(
            Morph(shapes.getValue(LaunchStatus.GO), shapes.getValue(LaunchStatus.PENDING)),
            Morph(shapes.getValue(LaunchStatus.PENDING), shapes.getValue(LaunchStatus.DONE)),
        )
    }

    val target = when (status) {
        LaunchStatus.GO -> 0f
        LaunchStatus.PENDING -> 1f
        LaunchStatus.DONE -> 2f
    }
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 520),
        label = "status-badge-morph",
    )
    val color by animateColorAsState(
        targetValue = when (status) {
            LaunchStatus.GO -> accents.clockLive
            LaunchStatus.PENDING -> scheme.tertiary
            LaunchStatus.DONE -> scheme.onSurfaceVariant
        },
        animationSpec = tween(durationMillis = 520),
        label = "status-badge-color",
    )

    // Scratch objects reused every frame.
    val scratchPath = remember { AndroidPath() }
    val scratchMatrix = remember { Matrix() }

    Box(
        modifier = modifier
            .size(size)
            .clearAndSetSemantics { }
            .drawWithCache {
                onDrawBehind {
                    val index = progress.toInt().coerceIn(0, morphs.lastIndex)
                    val local = (progress - index).coerceIn(0f, 1f)

                    scratchPath.rewind()
                    morphs[index].toPath(progress = local, path = scratchPath)

                    // Shapes are authored in a unit space centred on the origin; scale to
                    // half the node and translate to its centre.
                    val radius = this.size.minDimension / 2f
                    scratchMatrix.setScale(radius, radius)
                    scratchMatrix.postTranslate(this.size.width / 2f, this.size.height / 2f)
                    scratchPath.transform(scratchMatrix)

                    drawPath(path = scratchPath.asComposePath(), color = color)
                }
            },
    )
}

/** Badge plus its label, the form the launch cards actually use. */
@Composable
fun StatusChip(
    status: LaunchStatus,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.clearAndSetSemantics {
            // One node for the pair: a screen reader should say "Go for Launch", not
            // "image, Go for Launch".
            contentDescription = label
        },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MorphingStatusBadge(status, size = 16.dp)
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
