package com.example.spacenewslaunchertracker.ui.components

// Ported from the Space Composify catalog (com.example.spacecomposify.components).

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/*
 * A pulsing "ignition" bloom around a node — and an excuse to show the modern, low-level
 * way to write a modifier.
 *
 * The easy version of this is a @Composable modifier factory holding an
 * `InfiniteTransition`. It works, but it costs a composition slot per use site and it
 * recomposes the calling composable on every frame of the pulse. A `Modifier.Node` costs
 * neither: the element is a cheap, comparable data holder, the node is a long-lived object
 * that owns its own coroutine, and the animation never touches composition at all.
 *
 * Three details worth copying:
 *
 *  - **The element is a value.** `equals`/`hashCode` over its parameters is what lets
 *    Compose diff `Modifier` chains. Get it wrong and you either recreate nodes needlessly
 *    or miss updates. Hence `update()`, which mutates the existing node in place.
 *  - **The node owns the animation.** `coroutineScope` is tied to the node's lifetime, so
 *    the pulse starts on attach and is cancelled on detach with no `DisposableEffect`.
 *  - **Reading animation state inside `draw()` is enough.** Draw passes are snapshot-
 *    observed, so writing `pulse.value` invalidates the draw and nothing else — no
 *    recomposition, no relayout. `invalidateDraw()` is only needed for state Compose
 *    cannot see, such as a parameter changed from `update()`.
 */

/**
 * Draws a soft, slowly pulsing bloom just outside this node's bounds.
 *
 * @param color bloom colour.
 * @param active when false the bloom is drawn at rest and no coroutine runs.
 * @param cornerRadius corner radius of the content being wrapped, so the bloom follows it.
 */
fun Modifier.ignitionGlow(
    color: Color,
    active: Boolean = true,
    cornerRadius: Dp = 18.dp,
): Modifier = this then IgnitionGlowElement(color, active, cornerRadius)

private data class IgnitionGlowElement(
    private val color: Color,
    private val active: Boolean,
    private val cornerRadius: Dp,
) : ModifierNodeElement<IgnitionGlowNode>() {

    override fun create(): IgnitionGlowNode = IgnitionGlowNode(color, active, cornerRadius)

    override fun update(node: IgnitionGlowNode) {
        val activeChanged = node.active != active
        node.color = color
        node.cornerRadius = cornerRadius
        node.active = active
        // Only the pulse coroutine needs restarting, and only when it was switched.
        if (activeChanged) node.restartPulse()
        node.invalidateDraw()
    }

    /** Shows up in the Layout Inspector's modifier list — worth two lines. */
    override fun InspectorInfo.inspectableProperties() {
        name = "ignitionGlow"
        properties["color"] = color
        properties["active"] = active
        properties["cornerRadius"] = cornerRadius
    }
}

private class IgnitionGlowNode(
    var color: Color,
    var active: Boolean,
    var cornerRadius: Dp,
) : Modifier.Node(), DrawModifierNode {

    private val pulse = Animatable(0f)
    private var pulseJob: Job? = null

    override fun onAttach() {
        restartPulse()
    }

    override fun onDetach() {
        pulseJob?.cancel()
        pulseJob = null
    }

    fun restartPulse() {
        pulseJob?.cancel()
        pulseJob = null
        if (!active) {
            // Settle back to rest. `snapTo` is a suspend call, so it needs a scope too.
            pulseJob = coroutineScope.launch { pulse.snapTo(0f) }
            return
        }
        pulseJob = coroutineScope.launch {
            pulse.animateTo(
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 1_500, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
            )
        }
    }

    override fun ContentDrawScope.draw() {
        // This read is the whole subscription: when the Animatable advances, Compose
        // invalidates this draw node and nothing above it.
        val p = pulse.value
        val base = cornerRadius.toPx()
        val layers = 4

        repeat(layers) { index ->
            val t = (index + 1) / layers.toFloat()
            val grow = (2.dp.toPx() + 9.dp.toPx() * p) * t
            drawRoundRect(
                color = color.copy(
                    alpha = (0.26f * (1f - t) + 0.04f) * (0.45f + 0.55f * p),
                ),
                topLeft = Offset(-grow, -grow),
                size = Size(size.width + grow * 2f, size.height + grow * 2f),
                cornerRadius = CornerRadius(base + grow),
                style = Stroke(width = 1.5.dp.toPx() + grow * 0.5f),
            )
        }

        drawContent()
    }
}
