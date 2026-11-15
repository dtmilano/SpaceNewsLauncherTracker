package com.example.spacenewslaunchertracker.ui.components

// Ported from the Space Composify catalog (com.example.spacecomposify.components).

import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import com.example.spacenewslaunchertracker.ui.theme.LocalAmbientMotion

/**
 * A monotonically increasing "seconds since this composable appeared", advanced once per
 * frame, for shader uniforms and other continuous animations.
 *
 * Why [withInfiniteAnimationFrameMillis] rather than a `delay` loop or
 * `rememberInfiniteTransition`:
 *
 *  - it is driven by the composition's `MonotonicFrameClock`, so the value changes exactly
 *    once per frame — never more often than the display can show, and never drifting;
 *  - the clock stops when the host stops producing frames (the activity goes to the
 *    background), so an always-on animation cannot quietly burn battery off-screen;
 *  - under an `InfiniteAnimationPolicy` — which Compose's test harness installs — it simply
 *    never resumes, so a screenshot test does not race a running animation.
 *
 * That last point is also a trap: a test that *waits* for this value would hang. Hence
 * [LocalAmbientMotion]: when motion is off the state stays at `0f` and the coroutine is
 * never started, so callers degrade to a static first frame instead of stalling.
 *
 * @param enabled when false, returns a state that is permanently `0f`.
 */
@Composable
fun rememberFrameTimeSeconds(enabled: Boolean = LocalAmbientMotion.current): State<Float> =
    produceState(initialValue = 0f, enabled) {
        if (!enabled) {
            value = 0f
            return@produceState
        }
        var origin = Long.MIN_VALUE
        while (true) {
            withInfiniteAnimationFrameMillis { frameTimeMillis ->
                if (origin == Long.MIN_VALUE) origin = frameTimeMillis
                value = (frameTimeMillis - origin) / 1_000f
            }
        }
    }
