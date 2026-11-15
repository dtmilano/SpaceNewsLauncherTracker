package com.example.spacenewslaunchertracker.ui.components

// Ported from the Space Composify catalog (com.example.spacecomposify.components).

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.spacenewslaunchertracker.ui.theme.LocalSpaceAccents

/*
 * A circular launch clock: progress ring, tick marks, and the countdown rendered *inside*
 * the canvas rather than stacked on top of it as a `Text`.
 *
 * Two things make this worth reading as an example:
 *
 * 1. Everything that survives a frame is built in the `drawWithCache` cache block — the
 *    sweep-gradient brushes, the stroke objects, and the laid-out text. The per-frame
 *    `onDrawBehind` block only issues draw commands. Allocating a `Brush` or measuring text
 *    inside the draw lambda would do that work 60 times a second and churn the heap.
 *
 * 2. Text is measured with a `TextMeasurer` and drawn with `drawText`. Compose's `Canvas`
 *    has no concept of text; `TextMeasurer` is how you get a real, font-aware,
 *    bidi-correct layout you can place at an arbitrary offset — far better than a `Box`
 *    with a `Text` centred over the ring, which would have to re-measure the ring's size
 *    into dp first.
 */

/**
 * @param progress fraction of the countdown window elapsed, 0f..1f. Animated internally.
 * @param countdown the value to render in the middle of the ring.
 * @param caption small label under the digits, e.g. the provider name.
 */
@Composable
fun CountdownRing(
    progress: Float,
    countdown: Countdown,
    modifier: Modifier = Modifier,
    diameter: Dp = 176.dp,
    caption: String? = null,
) {
    val accents = LocalSpaceAccents.current
    val scheme = MaterialTheme.colorScheme
    val measurer = rememberTextMeasurer()

    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 900),
        label = "countdown-ring-progress",
    )

    val arcColor = if (countdown.hasLiftedOff) accents.clockPassed else accents.clockLive
    val digits = countdown.toPlainString()
    val spoken = countdown.toSpokenString()

    Box(
        modifier = modifier
            .size(diameter)
            .clearAndSetSemantics { contentDescription = spoken }
            .drawWithCache {
                val strokeWidth = size.minDimension * 0.065f
                val inset = strokeWidth / 2f + size.minDimension * 0.02f
                val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
                val topLeft = Offset(inset, inset)

                val trackStroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                val arcStroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)

                // Sweep gradient rotated so its seam sits at the 12 o'clock start point.
                val sweep = Brush.sweepGradient(
                    0f to arcColor.copy(alpha = 0.25f),
                    0.5f to arcColor,
                    1f to arcColor.copy(alpha = 0.25f),
                )
                val glow = Brush.radialGradient(
                    colors = listOf(arcColor.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.minDimension / 2f,
                )

                val digitsLayout = measurer.measure(
                    text = digits,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = (size.minDimension * 0.105f).toSp(),
                        color = arcColor,
                        textAlign = TextAlign.Center,
                    ),
                    constraints = Constraints(
                        maxWidth = (size.width * 0.86f).toInt(),
                    ),
                    maxLines = 1,
                )
                val captionLayout = caption?.let {
                    measurer.measure(
                        text = it,
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        ),
                        constraints = Constraints(
                            maxWidth = (size.width * 0.8f).toInt(),
                        ),
                        maxLines = 1,
                    )
                }

                onDrawBehind {
                    drawCircle(glow, radius = size.minDimension / 2f)

                    drawArc(
                        color = scheme.surfaceVariant,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = trackStroke,
                    )

                    if (animatedProgress > 0f) {
                        drawArc(
                            brush = sweep,
                            startAngle = -90f,
                            sweepAngle = 360f * animatedProgress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = arcStroke,
                        )
                    }

                    // Minute ticks: 24 marks, every third one longer.
                    val tickOuter = size.minDimension / 2f - strokeWidth * 1.6f
                    repeat(24) { index ->
                        val long = index % 3 == 0
                        val length = if (long) strokeWidth * 0.9f else strokeWidth * 0.45f
                        rotate(degrees = index * 15f) {
                            drawLine(
                                color = scheme.outline.copy(alpha = if (long) 0.8f else 0.4f),
                                start = Offset(size.width / 2f, size.height / 2f - tickOuter),
                                end = Offset(
                                    size.width / 2f,
                                    size.height / 2f - tickOuter + length,
                                ),
                                strokeWidth = if (long) 2f else 1f,
                                cap = StrokeCap.Round,
                            )
                        }
                    }

                    val totalTextHeight = digitsLayout.size.height +
                        (captionLayout?.size?.height ?: 0)
                    var y = (size.height - totalTextHeight) / 2f
                    drawText(
                        textLayoutResult = digitsLayout,
                        topLeft = Offset((size.width - digitsLayout.size.width) / 2f, y),
                    )
                    y += digitsLayout.size.height
                    captionLayout?.let {
                        drawText(
                            textLayoutResult = it,
                            topLeft = Offset((size.width - it.size.width) / 2f, y),
                        )
                    }
                }
            },
    )
}

/** Plain (un-styled) rendering of the clock, for canvas text and tests. */
fun Countdown.toPlainString(): String = when {
    isToBeAnnounced -> "T-??"
    days > 0 -> "%s%02dd:%02dh:%02dm".format(sign(), days, hours, minutes)
    else -> "%s%02d:%02d:%02d".format(sign(), hours, minutes, seconds)
}

private fun Countdown.sign(): String = if (hasLiftedOff) "T+" else "T-"
