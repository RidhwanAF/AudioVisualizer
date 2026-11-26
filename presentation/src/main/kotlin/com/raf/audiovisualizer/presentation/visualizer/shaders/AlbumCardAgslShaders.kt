package com.raf.audiovisualizer.presentation.visualizer.shaders

import org.intellij.lang.annotations.Language

/**
 * AGSL (`android.graphics.RuntimeShader`) source programs rendered strictly inside the top
 * album & ambient color card via `RenderEffect.createRuntimeShaderEffect(shader, "content")`.
 */
object AlbumCardAgslShaders {

    /**
     * 1. Harmonic Acoustic Ripple Shader (AGSL)
     * Concentric beat-reactive waves emanating inside the album & ambient card, refracting card
     * pixels and tinting wave crests with the album's primary and secondary ambient colors.
     */
    @Language("AGSL")
    const val RIPPLE_SHADER_SRC: String = """
        uniform shader content;
        uniform float2 uResolution;
        uniform float2 uOrigin;
        uniform float uTime;
        uniform float uIntensity;
        uniform float uSpeed;
        uniform float uBeat;
        uniform float4 uPrimaryColor;
        uniform float4 uSecondaryColor;

        half4 main(float2 fragCoord) {
            float2 res = max(uResolution, float2(1.0));
            float maxDim = max(res.x, res.y);
            float2 delta = fragCoord - uOrigin;
            float dist = length(delta);
            float normDist = dist / maxDim;
            float2 dir = dist > 0.001 ? (delta / dist) : float2(0.0, 0.0);

            float phase1 = normDist * 28.0 - uTime * (2.6 + uSpeed * 1.35);
            float phase2 = normDist * 48.0 - uTime * (4.2 + uSpeed * 1.95);
            float ringEnvelope = exp(-normDist * 2.15) * smoothstep(0.0, 0.035, normDist);

            float amp = (0.14 + uIntensity * 0.62 + uBeat * 0.42) * ringEnvelope;
            float wave = sin(phase1) * 0.72 + sin(phase2) * 0.28;
            float slope = cos(phase1) * 0.72 + cos(phase2) * 0.28;

            float2 displaced = clamp(
                fragCoord + dir * (slope * amp * 14.5),
                float2(0.5),
                res - float2(0.5)
            );
            half4 baseSample = content.eval(displaced);
            float3 baseRgb = float3(baseSample.rgb);
            float baseA = float(baseSample.a);

            float crest = pow(max(0.0, wave), 3.0) * amp;
            float trough = max(0.0, -wave) * amp * 0.24;
            float3 waveTint = mix(uPrimaryColor.rgb, uSecondaryColor.rgb, clamp(normDist * 1.4, 0.0, 1.0));

            float3 litRgb = baseRgb * (1.0 - trough) + (waveTint * 0.72 + float3(0.28)) * (crest * 0.70 * baseA);
            litRgb = clamp(litRgb, float3(0.0), float3(baseA));
            return half4(half3(litRgb), half(baseA));
        }
    """

    /**
     * 2. Liquid Water Drop Shader (AGSL)
     * Simulates up to 4 concurrent water droplet impacts inside the album & ambient card with
     * expanding capillary wave packets, central rebound crown, 2D surface-normal Snell refraction,
     * trough shadowing, and glossy Blinn-Phong specular caustics.
     */
    @Language("AGSL")
    const val WATER_DROP_SHADER_SRC: String = """
        uniform shader content;
        uniform float2 uResolution;
        uniform float4 uDrop0;
        uniform float4 uDrop1;
        uniform float4 uDrop2;
        uniform float4 uDrop3;
        uniform float uAmbientRipple;
        uniform float uTime;
        uniform float4 uPrimaryColor;
        uniform float4 uSecondaryColor;

        float singleDropHeight(float2 p, float4 drop, float maxDim) {
            if (drop.w <= 0.001 || drop.z < 0.0) {
                return 0.0;
            }
            float2 d = p - drop.xy;
            float r = length(d) / maxDim;
            float age = drop.z;
            float strength = drop.w;

            float front = age * 0.44;
            float distToFront = r - front;
            float packet = exp(-distToFront * distToFront * 115.0) * smoothstep(0.0, 0.012, r);
            float decay = exp(-age * 1.38) / (1.0 + r * 5.5);
            float ringWave = -sin(distToFront * 92.0) * packet * decay * strength;

            float crown = exp(-r * r * 720.0) * cos(age * 24.0) * exp(-age * 6.2) * strength * 0.58;
            return ringWave + crown;
        }

        float waterHeight(float2 p, float maxDim) {
            float h = singleDropHeight(p, uDrop0, maxDim)
                + singleDropHeight(p, uDrop1, maxDim)
                + singleDropHeight(p, uDrop2, maxDim)
                + singleDropHeight(p, uDrop3, maxDim);

            // Subtle micro-surface tension shimmer scaled by live audio intensity
            float2 uv = p / maxDim;
            float micro = sin(uv.x * 22.0 + uTime * 2.1) * cos(uv.y * 19.0 - uTime * 1.7);
            return h + micro * uAmbientRipple * 0.012;
        }

        half4 main(float2 fragCoord) {
            float2 res = max(uResolution, float2(1.0));
            float maxDim = max(res.x, res.y);
            float eps = 1.6;

            float h0 = waterHeight(fragCoord, maxDim);
            float hx = waterHeight(fragCoord + float2(eps, 0.0), maxDim);
            float hy = waterHeight(fragCoord + float2(0.0, eps), maxDim);
            float2 grad = float2(hx - h0, hy - h0);

            float2 refracted = clamp(
                fragCoord + grad * 88.0,
                float2(0.5),
                res - float2(0.5)
            );
            half4 baseSample = content.eval(refracted);
            float3 baseRgb = float3(baseSample.rgb);
            float baseA = float(baseSample.a);

            float3 normal = normalize(float3(-grad.x * 22.0, -grad.y * 22.0, 1.0));
            float3 lightDir = normalize(float3(-0.36, -0.58, 0.73));
            float3 halfVec = normalize(lightDir + float3(0.0, 0.0, 1.0));

            float spec = pow(max(0.0, dot(normal, halfVec)), 44.0);
            float rim = clamp(length(grad) * 16.0, 0.0, 1.0);
            float shade = clamp(1.0 + h0 * 0.20, 0.85, 1.14);

            float3 causticTint = mix(uPrimaryColor.rgb, uSecondaryColor.rgb, 0.45);
            float3 litRgb = baseRgb * shade
                + causticTint * (rim * 0.22 * baseA)
                + float3(1.0, 0.99, 1.0) * (spec * 0.48 * baseA);

            litRgb = clamp(litRgb, float3(0.0), float3(baseA));
            return half4(half3(litRgb), half(baseA));
        }
    """

    /**
     * 3. Reverse Dust Shader — Thanos Snap Reversed (AGSL)
     * Materializes the card from complete nothingness (`uProgress = 0.0`) via multi-scale swirling
     * cosmic/ambient dust particles that fly inward and coalesce seamlessly into the exact rounded
     * card silhouette (`uProgress = 1.0`).
     */
    @Language("AGSL")
    const val REVERSE_DUST_SHADER_SRC: String = """
        uniform shader content;
        uniform float2 uResolution;
        uniform float uProgress;
        uniform float uTime;
        uniform float uAudioGlow;
        uniform float4 uPrimaryColor;
        uniform float4 uSecondaryColor;

        float2 hash22(float2 p) {
            float3 p3 = fract(float3(p.xyx) * float3(0.1031, 0.1030, 0.0973));
            p3 += dot(p3, p3.yzx + 33.33);
            return fract((p3.xx + p3.yz) * p3.zy);
        }

        float valueNoise(float2 p) {
            float2 i = floor(p);
            float2 f = fract(p);
            float2 u = f * f * (3.0 - 2.0 * f);
            float a = hash22(i).x;
            float b = hash22(i + float2(1.0, 0.0)).x;
            float c = hash22(i + float2(0.0, 1.0)).x;
            float d = hash22(i + float2(1.0, 1.0)).x;
            return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
        }

        float4 dustLayer(
            float2 fragCoord,
            float2 res,
            float cellSize,
            float regionAssemble,
            float swirlSeed
        ) {
            float scatter = (1.0 - regionAssemble) * (1.0 - regionAssemble);
            float angle = scatter * 2.8 + swirlSeed * 4.2 + uTime * 0.35 * scatter;
            float2 windOffset = float2(105.0, -54.0) * scatter
                + float2(cos(angle), sin(angle)) * (28.0 * scatter);

            float2 shifted = fragCoord - windOffset;
            float2 cell = floor(shifted / cellSize);
            float2 localUv = fract(shifted / cellSize) - float2(0.5);
            float2 rnd = hash22(cell + float2(swirlSeed * 17.0));

            float birth = rnd.x * 0.72;
            float grainLife = clamp((regionAssemble - birth * 0.55) / (1.0 - birth * 0.55), 0.0, 1.0);
            if (grainLife <= 0.001) {
                return float4(0.0);
            }

            float dist = length(localUv);
            float maxRadius = mix(0.24 + rnd.y * 0.20, 0.86, smoothstep(0.35, 0.98, grainLife));
            float mask = 1.0 - smoothstep(maxRadius * 0.62, maxRadius, dist);

            float2 homeCoord = fragCoord + (rnd - float2(0.5)) * 18.0 * scatter;
            if (homeCoord.x < 0.0 || homeCoord.y < 0.0 || homeCoord.x > res.x || homeCoord.y > res.y) {
                return float4(0.0);
            }
            half4 homeSample = content.eval(clamp(homeCoord, float2(0.5), res - float2(0.5)));
            float homeA = float(homeSample.a);
            if (homeA <= 0.002) {
                return float4(0.0);
            }

            float3 homeRgb = float3(homeSample.rgb) / max(homeA, 0.001);
            float3 emberTint = mix(uSecondaryColor.rgb, uPrimaryColor.rgb, rnd.x) * (1.15 + uAudioGlow * 0.55)
                + float3(0.22, 0.18, 0.14) * (1.0 - grainLife);
            float3 grainRgb = mix(emberTint, homeRgb, smoothstep(0.22, 0.90, grainLife));
            float alpha = mask * smoothstep(0.0, 0.20, grainLife) * homeA;

            return float4(clamp(grainRgb, 0.0, 1.0) * alpha, alpha);
        }

        half4 main(float2 fragCoord) {
            float2 res = max(uResolution, float2(1.0));
            if (uProgress <= 0.001) {
                return half4(0.0);
            }
            if (uProgress >= 0.999) {
                return content.eval(fragCoord);
            }

            float2 uv = fragCoord / res;
            float n = valueNoise(uv * 6.5);
            float sweepCoord = uv.x * 0.72 + (1.0 - uv.y) * 0.28 + n * 0.15;
            float front = uProgress * 1.66 - 0.24;
            float regionAssemble = clamp((front - sweepCoord) / 0.48, 0.0, 1.0);

            if (regionAssemble >= 0.998) {
                return content.eval(fragCoord);
            }

            float4 fine = dustLayer(fragCoord, res, 4.2, regionAssemble, 1.0);
            float4 coarse = dustLayer(fragCoord, res, 8.5, regionAssemble * 0.94, 2.7);

            float4 combined = fine + coarse * (1.0 - fine.a);
            half4 solidSample = content.eval(fragCoord);
            float solidBlend = smoothstep(0.76, 0.995, regionAssemble);

            float3 outRgb = mix(combined.rgb, float3(solidSample.rgb), solidBlend);
            float outA = mix(combined.a, float(solidSample.a), solidBlend);
            outRgb = clamp(outRgb, float3(0.0), float3(outA));
            return half4(half3(outRgb), half(outA));
        }
    """
}
