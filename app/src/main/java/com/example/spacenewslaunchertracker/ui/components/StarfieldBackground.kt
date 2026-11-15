package com.example.spacenewslaunchertracker.ui.components

// Ported from the Space Composify catalog (com.example.spacecomposify.components).

import android.graphics.RuntimeShader
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.platform.LocalInspectionMode
import com.example.spacenewslaunchertracker.ui.theme.NebulaViolet
import com.example.spacenewslaunchertracker.ui.theme.SpaceVoid

/*
 * An animated nebula-and-starfield backdrop drawn by a fragment shader written in AGSL
 * (Android Graphics Shading Language), the SkSL dialect Android exposes through
 * `RuntimeShader`.
 *
 * Why a shader at all? The alternative — a few hundred `drawCircle` calls per frame, each
 * with its own twinkle animation — costs a display list the size of the star count and
 * recomposes or re-records on every frame. This runs entirely on the GPU: the whole
 * background is one `drawRect`, with per-pixel work the CPU never sees. Nothing recomposes
 * while it animates; only the draw lambda re-runs.
 *
 * `RuntimeShader` needs API 33, which is this module's `minSdk`, so no version guard is
 * needed here. It does, however, require a *hardware-accelerated* canvas — which a
 * `@Preview` and some screenshot paths do not provide — so [starfield] falls back to a
 * static gradient under `LocalInspectionMode`.
 */

private const val STARFIELD_AGSL = """
uniform float2 uResolution;
uniform float uTime;
uniform float3 uNebula;
uniform float3 uStar;
uniform float uDensity;

// Cheap hash: deterministic pseudo-random in [0,1) from a 2D cell coordinate.
float hash21(float2 p) {
    float2 q = fract(p * float2(123.34, 456.21));
    q = q + dot(q, q + 45.32);
    return fract(q.x * q.y);
}

// Smoothed value noise over the unit grid.
float valueNoise(float2 p) {
    float2 i = floor(p);
    float2 f = fract(p);
    float2 u = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + float2(1.0, 0.0));
    float c = hash21(i + float2(0.0, 1.0));
    float d = hash21(i + float2(1.0, 1.0));
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// Five octaves of value noise: the cloudy structure of the nebula.
float fbm(float2 p) {
    float sum = 0.0;
    float amp = 0.5;
    float2 q = p;
    for (int i = 0; i < 5; i++) {
        sum = sum + amp * valueNoise(q);
        q = q * 2.03;
        amp = amp * 0.5;
    }
    return sum;
}

half4 main(float2 fragCoord) {
    // Normalise by height so the field does not stretch with aspect ratio.
    float2 uv = fragCoord / max(uResolution.y, 1.0);

    // The nebula drifts slowly and diagonally; raising it to a power keeps the dark
    // regions genuinely dark instead of a uniform haze.
    float2 drift = float2(uTime * 0.010, uTime * -0.005);
    float neb = fbm(uv * 2.2 + drift);
    neb = pow(clamp(neb, 0.0, 1.0), 2.4);
    float3 col = float3(0.015, 0.019, 0.055) + uNebula * neb * 0.85;

    // Two star layers at different scales, so the field does not read as a grid.
    float stars = 0.0;
    for (int layer = 0; layer < 2; layer++) {
        float fl = float(layer);
        float scale = 34.0 + fl * 46.0;
        float2 grid = uv * scale;
        float2 cell = floor(grid);
        float2 local = fract(grid) - 0.5;
        float r = hash21(cell + float2(fl * 17.0, 0.0));
        float present = step(1.0 - uDensity, r);
        float2 jitter = (float2(hash21(cell + 3.7), hash21(cell + 11.3)) - 0.5) * 0.6;
        float d = length(local - jitter);
        float twinkle = 0.55 + 0.45 * sin(uTime * (0.8 + r * 2.6) + r * 37.0);
        float core = smoothstep(0.075, 0.0, d);
        stars = stars + present * core * twinkle * (0.35 + 0.65 * r) / (1.0 + fl);
    }
    col = col + uStar * stars;

    return half4(half3(clamp(col, float3(0.0), float3(1.0))), 1.0);
}
"""

/**
 * Draws an animated starfield behind the content of this node.
 *
 * @param nebula tint of the gas clouds.
 * @param star colour of the point stars.
 * @param density roughly the fraction of grid cells that hold a star, 0f..1f.
 * @param speed multiplier on the drift and twinkle rate; `0f` freezes the first frame.
 */
@Composable
fun Modifier.starfield(
    nebula: Color = NebulaViolet,
    star: Color = Color.White,
    density: Float = 0.14f,
    speed: Float = 1f,
): Modifier {
    // A software canvas cannot run a RuntimeShader; previews get a static stand-in.
    if (LocalInspectionMode.current) {
        return this.then(staticStarfieldFallback(nebula))
    }

    // `RuntimeShader` compiles the AGSL in its constructor and throws on a syntax error or
    // on a driver that rejects the program. A background is not worth a crash, so a failure
    // degrades to the same static gradient the preview uses.
    val shader = remember { runCatching { RuntimeShader(STARFIELD_AGSL) }.getOrNull() }
        ?: return this.then(staticStarfieldFallback(nebula))

    val timeSeconds by rememberFrameTimeSeconds()
    val brush = remember(shader) { ShaderBrush(shader) }

    return this.drawWithCache {
        // Uniforms that depend only on size belong in the cache block: it re-runs when the
        // node is measured, not when the animation ticks.
        shader.setFloatUniform("uResolution", size.width, size.height)
        shader.setFloatUniform("uNebula", nebula.red, nebula.green, nebula.blue)
        shader.setFloatUniform("uStar", star.red, star.green, star.blue)
        shader.setFloatUniform("uDensity", density.coerceIn(0f, 1f))

        onDrawBehind {
            // Reading `timeSeconds` *inside* the draw lambda is what makes this animate:
            // draw lambdas are snapshot-observed, so a new value invalidates the draw pass
            // only — no recomposition, no relayout.
            shader.setFloatUniform("uTime", timeSeconds * speed)
            drawRect(brush)
        }
    }
}

/** Static gradient stand-in with the same visual weight, for previews and inspection. */
private fun staticStarfieldFallback(nebula: Color): Modifier = Modifier.drawWithCache {
    val brush = Brush.radialGradient(
        colors = listOf(nebula.copy(alpha = 0.35f), SpaceVoid),
        center = Offset(size.width * 0.3f, size.height * 0.25f),
        radius = size.maxDimension * 0.9f,
    )
    onDrawBehind { drawRect(brush) }
}
