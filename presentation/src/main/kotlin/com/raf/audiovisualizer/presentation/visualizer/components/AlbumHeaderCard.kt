package com.raf.audiovisualizer.presentation.visualizer.components

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.graphics.SweepGradient
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.audiovisualizer.domain.model.AudioAnimationParams
import com.raf.audiovisualizer.domain.model.AudioTelemetry
import com.raf.audiovisualizer.domain.model.CardVisualEffect
import com.raf.audiovisualizer.domain.model.MotionMood
import com.raf.audiovisualizer.domain.model.NowPlayingTrack
import com.raf.audiovisualizer.presentation.R
import com.raf.audiovisualizer.presentation.visualizer.shaders.AlbumCardAgslShaders
import kotlinx.coroutines.isActive
import java.util.Locale
import kotlin.random.Random

private class WaterDropState {
    val x = FloatArray(DROP_COUNT)
    val y = FloatArray(DROP_COUNT)
    val age = FloatArray(DROP_COUNT) { -1f }
    val strength = FloatArray(DROP_COUNT)
    var nextSlot = 0

    fun spawn(px: Float, py: Float, power: Float) {
        val i = nextSlot
        x[i] = px
        y[i] = py
        age[i] = 0f
        strength[i] = power.coerceIn(0.22f, 1f)
        nextSlot = (nextSlot + 1) % DROP_COUNT
    }

    fun step(dt: Float, speedScale: Float) {
        age.indices.forEach { i ->
            if (age[i] >= 0f) {
                age[i] += dt * speedScale
                if (age[i] > 2.35f) {
                    age[i] = -1f
                    strength[i] = 0f
                }
            }
        }
    }

    companion object {
        const val DROP_COUNT = 4
    }
}

/**
 * Top album artwork & ambient color showcase card confining all selectable AGSL & rhythm border
 * animations strictly inside this card:
 * - [CardVisualEffect.OFF]: Static clean card (no shader, no border glow, no album bounce).
 * - [CardVisualEffect.RIPPLE]: AGSL concentric harmonic wave ripple inside the card.
 * - [CardVisualEffect.WATER_DROP]: AGSL multi-droplet liquid surface splash & refraction inside the card.
 * - [CardVisualEffect.REVERSE_DUST]: AGSL Thanos-snap-reversed dust coalescence from nothing into the card shape.
 * - [CardVisualEffect.BORDER_GLOW]: Rhythm-synced glowing perimeter border pulsing with audio rhythm.
 */
@Composable
fun AlbumHeaderCard(
    modifier: Modifier = Modifier,
    track: NowPlayingTrack,
    telemetry: AudioTelemetry,
    animationParams: AudioAnimationParams,
    selectedEffect: CardVisualEffect,
    dustTriggerCount: Int,
    primaryColor: Color,
    secondaryColor: Color,
    hasNotificationAccess: Boolean,
    onSelectEffect: (CardVisualEffect) -> Unit,
    onRequestNotificationAccess: () -> Unit,
) {
    val isAnimationActive = selectedEffect != CardVisualEffect.OFF
    val isPlayingLive = telemetry.isLive && telemetry.energy > 0.008f

    val artworkBitmap: ImageBitmap? = remember(track.artworkBytes) {
        track.artworkBytes?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        }
    }

    // AGSL RuntimeShaders (Android 13+ / minSdk 33 native)
    val rippleShader = remember { RuntimeShader(AlbumCardAgslShaders.RIPPLE_SHADER_SRC) }
    val waterDropShader = remember { RuntimeShader(AlbumCardAgslShaders.WATER_DROP_SHADER_SRC) }
    val reverseDustShader = remember { RuntimeShader(AlbumCardAgslShaders.REVERSE_DUST_SHADER_SRC) }

    val cardSize = remember { arrayOf(IntSize(1, 1)) }
    val rippleTouchOrigin = remember { arrayOf<Offset?>(null) }
    val waterDrops = remember { WaterDropState() }

    // Asymmetric album-card scale: disabled cleanly when selectedEffect == OFF
    val albumScale = remember { Animatable(1f) }
    LaunchedEffect(animationParams.scaleFactor, telemetry.isLive, isAnimationActive) {
        val target = if (isAnimationActive && telemetry.isLive) animationParams.scaleFactor else 1f
        val isExpanding = target > albumScale.value
        val durationMs = if (isExpanding) animationParams.scaleAttackMs else animationParams.scaleReleaseMs
        val easing = if (isExpanding) FastOutSlowInEasing else LinearOutSlowInEasing
        albumScale.animateTo(
            targetValue = target,
            animationSpec = tween(durationMillis = durationMs, easing = easing),
        )
    }

    // Reverse Dust ("Thanos snap reversed"): from 0.0 (nothing) -> 1.0 (full card shape)
    val dustProgress = remember { Animatable(1f) }
    LaunchedEffect(selectedEffect, dustTriggerCount, track.title) {
        if (selectedEffect == CardVisualEffect.REVERSE_DUST) {
            dustProgress.snapTo(0f)
            dustProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 1_850, easing = FastOutSlowInEasing),
            )
        } else {
            dustProgress.snapTo(1f)
        }
    }

    val latestParams by rememberUpdatedState(animationParams)
    val latestTelemetry by rememberUpdatedState(telemetry)
    val shaderTimeSec = remember { mutableFloatStateOf(0f) }
    val snakeAngleDeg = remember { mutableFloatStateOf(0f) }
    val rimAngleDeg = remember { mutableFloatStateOf(180f) }
    val frameTick = remember { mutableLongStateOf(0L) }

    LaunchedEffect(selectedEffect, telemetry.isLive) {
        if (selectedEffect == CardVisualEffect.OFF) return@LaunchedEffect

        // Seed an immediate central splash when switching into WATER_DROP so user sees it right away
        if (selectedEffect == CardVisualEffect.WATER_DROP) {
            val w = cardSize[0].width.coerceAtLeast(300).toFloat()
            val h = cardSize[0].height.coerceAtLeast(160).toFloat()
            waterDrops.spawn(px = w * 0.48f, py = h * 0.40f, power = 0.78f)
        }

        var lastNanos = 0L
        var dropCooldownSec = 0.25f

        while (isActive) {
            withFrameNanos { nowNanos ->
                if (lastNanos != 0L) {
                    val dt = ((nowNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    val params = latestParams
                    val tele = latestTelemetry

                    shaderTimeSec.floatValue = (shaderTimeSec.floatValue + dt) % 3_600f
                    if (tele.isLive) {
                        snakeAngleDeg.floatValue = (snakeAngleDeg.floatValue + dt * params.snakeSpeedDegPerSec) % 360f
                        rimAngleDeg.floatValue = (rimAngleDeg.floatValue - dt * params.snakeSpeedDegPerSec * 0.65f + 360f) % 360f
                    }

                    if (selectedEffect == CardVisualEffect.WATER_DROP) {
                        val speedScale = 0.82f + params.speedNormalized * 0.65f
                        waterDrops.step(dt = dt, speedScale = speedScale)
                        dropCooldownSec += dt

                        val beatSplash = tele.isLive && params.rippleTriggerStrength >= 0.30f && dropCooldownSec >= 0.22f
                        val calmDrip = tele.isLive && params.rippleIntensity >= 0.08f && dropCooldownSec >= 0.92f
                        val idleDemoDrip = !tele.isLive && dropCooldownSec >= 1.85f

                        if (beatSplash || calmDrip || idleDemoDrip) {
                            val w = cardSize[0].width.coerceAtLeast(240).toFloat()
                            val h = cardSize[0].height.coerceAtLeast(140).toFloat()
                            val px = w * (0.16f + Random.nextFloat() * 0.70f)
                            val py = h * (0.18f + Random.nextFloat() * 0.54f)
                            val power = when {
                                beatSplash -> (0.48f + params.rippleTriggerStrength * 0.52f).coerceIn(0.45f, 1f)
                                calmDrip -> (0.30f + params.rippleIntensity * 0.42f).coerceIn(0.28f, 0.72f)
                                else -> 0.42f
                            }
                            waterDrops.spawn(px = px, py = py, power = power)
                            dropCooldownSec = 0f
                        }
                    }

                    frameTick.longValue = nowNanos
                }
                lastNanos = nowNanos
            }
        }
    }

    val glow = if (isAnimationActive) animationParams.snakeGlowThickness.coerceIn(0.15f, 1f) else 0f
    val tailLen = animationParams.snakeTailLengthFraction.coerceIn(0.22f, 0.62f)
    val ambientGlow = if (isAnimationActive) animationParams.ambientGlowIntensity.coerceIn(0.15f, 1f) else 0.15f
    val highlightColor = lerp(primaryColor, Color.White, 0.58f)
    val snakeRotMatrix = remember { Matrix() }
    val rimRotMatrix = remember { Matrix() }
    val cardShape = RoundedCornerShape(28.dp)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { size ->
                if (size.width > 0 && size.height > 0) {
                    cardSize[0] = size
                }
            }
            .drawBehind {
                val cornerPx = 28.dp.toPx()

                if (selectedEffect == CardVisualEffect.BORDER_GLOW) {
                    frameTick.longValue
                    val rhythmBeat = if (telemetry.isLive) {
                        (telemetry.beatPulse * 0.55f + telemetry.bass.normalized * 0.30f + telemetry.rhythmDensity * 0.15f)
                            .coerceIn(0f, 1f)
                    } else {
                        0f
                    }

                    // Rhythmic full-perimeter breathing neon base
                    drawRoundRect(
                        color = primaryColor.copy(alpha = (0.18f + rhythmBeat * 0.36f).coerceIn(0.14f, 0.56f)),
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        style = Stroke(width = (1.4f + rhythmBeat * 3.2f).dp.toPx()),
                    )

                    val tailStart = (0.94f - tailLen).coerceIn(0.22f, 0.72f)
                    val midTail = tailStart + tailLen * 0.42f
                    val hotBody = tailStart + tailLen * 0.78f
                    val headPeak = 0.94f

                    snakeRotMatrix.reset()
                    snakeRotMatrix.setRotate(snakeAngleDeg.floatValue, center.x, center.y)
                    val snakeShader = SweepGradient(
                        center.x,
                        center.y,
                        intArrayOf(
                            Color.Transparent.toArgb(),
                            Color.Transparent.toArgb(),
                            secondaryColor.copy(alpha = 0.30f + glow * 0.26f).toArgb(),
                            primaryColor.copy(alpha = 0.80f + glow * 0.20f).toArgb(),
                            highlightColor.toArgb(),
                            Color.Transparent.toArgb(),
                        ),
                        floatArrayOf(0.0f, tailStart, midTail, hotBody, headPeak, 1.0f),
                    ).apply {
                        setLocalMatrix(snakeRotMatrix)
                    }
                    val snakeBrush = ShaderBrush(snakeShader)
                    val beatBoost = 1f + rhythmBeat * 0.45f

                    drawRoundRect(
                        brush = snakeBrush,
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        alpha = (0.10f + glow * 0.16f).coerceIn(0.08f, 0.28f),
                        style = Stroke(width = ((16f + glow * 15f) * beatBoost).dp.toPx()),
                    )
                    drawRoundRect(
                        brush = snakeBrush,
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        alpha = (0.22f + glow * 0.24f).coerceIn(0.18f, 0.48f),
                        style = Stroke(width = ((9f + glow * 9f) * beatBoost).dp.toPx()),
                    )
                    drawRoundRect(
                        brush = snakeBrush,
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        alpha = (0.45f + glow * 0.30f).coerceIn(0.40f, 0.78f),
                        style = Stroke(width = ((4.8f + glow * 4.2f) * beatBoost).dp.toPx()),
                    )
                    drawRoundRect(
                        brush = snakeBrush,
                        topLeft = Offset.Zero,
                        size = size,
                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                        alpha = (0.86f + glow * 0.14f).coerceIn(0.82f, 1.0f),
                        style = Stroke(width = (2.8f + glow * 2.2f).dp.toPx()),
                    )
                } else {
                    // Clean static subtle border when BORDER_GLOW is not active (and hidden until dust finishes forming)
                    val borderVisibility = if (selectedEffect == CardVisualEffect.REVERSE_DUST) {
                        ((dustProgress.value - 0.92f) / 0.08f).coerceIn(0f, 1f)
                    } else {
                        1f
                    }
                    if (borderVisibility > 0.02f) {
                        drawRoundRect(
                            color = primaryColor.copy(alpha = 0.22f * borderVisibility),
                            topLeft = Offset.Zero,
                            size = size,
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                            style = Stroke(width = 1.dp.toPx()),
                        )
                    }
                }
            },
    ) {
        // Inner card container strictly hosting the AGSL RuntimeShader effects inside the card bounds
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    val w = size.width.coerceAtLeast(1f)
                    val h = size.height.coerceAtLeast(1f)
                    clip = selectedEffect != CardVisualEffect.REVERSE_DUST
                    shape = cardShape

                    renderEffect = when (selectedEffect) {
                        CardVisualEffect.OFF,
                        CardVisualEffect.BORDER_GLOW,
                        -> null

                        CardVisualEffect.RIPPLE -> {
                            frameTick.longValue
                            val defaultOrigin = Offset(x = 62.dp.toPx(), y = 62.dp.toPx())
                            val origin = rippleTouchOrigin[0] ?: defaultOrigin
                            val liveIntensity = if (telemetry.isLive) animationParams.rippleIntensity else 0.18f
                            val liveBeat = if (telemetry.isLive) telemetry.beatPulse else 0f

                            rippleShader.setFloatUniform("uResolution", w, h)
                            rippleShader.setFloatUniform("uOrigin", origin.x, origin.y)
                            rippleShader.setFloatUniform("uTime", shaderTimeSec.floatValue)
                            rippleShader.setFloatUniform("uIntensity", liveIntensity)
                            rippleShader.setFloatUniform("uSpeed", animationParams.speedMultiplier)
                            rippleShader.setFloatUniform("uBeat", liveBeat)
                            rippleShader.setFloatUniform(
                                "uPrimaryColor",
                                primaryColor.red,
                                primaryColor.green,
                                primaryColor.blue,
                                1f,
                            )
                            rippleShader.setFloatUniform(
                                "uSecondaryColor",
                                secondaryColor.red,
                                secondaryColor.green,
                                secondaryColor.blue,
                                1f,
                            )
                            RenderEffect
                                .createRuntimeShaderEffect(rippleShader, "content")
                                .asComposeRenderEffect()
                        }

                        CardVisualEffect.WATER_DROP -> {
                            frameTick.longValue
                            waterDropShader.setFloatUniform("uResolution", w, h)
                            waterDropShader.setFloatUniform(
                                "uDrop0",
                                waterDrops.x[0],
                                waterDrops.y[0],
                                waterDrops.age[0],
                                waterDrops.strength[0],
                            )
                            waterDropShader.setFloatUniform(
                                "uDrop1",
                                waterDrops.x[1],
                                waterDrops.y[1],
                                waterDrops.age[1],
                                waterDrops.strength[1],
                            )
                            waterDropShader.setFloatUniform(
                                "uDrop2",
                                waterDrops.x[2],
                                waterDrops.y[2],
                                waterDrops.age[2],
                                waterDrops.strength[2],
                            )
                            waterDropShader.setFloatUniform(
                                "uDrop3",
                                waterDrops.x[3],
                                waterDrops.y[3],
                                waterDrops.age[3],
                                waterDrops.strength[3],
                            )
                            waterDropShader.setFloatUniform(
                                "uAmbientRipple",
                                if (telemetry.isLive) animationParams.rippleIntensity else 0.12f,
                            )
                            waterDropShader.setFloatUniform("uTime", shaderTimeSec.floatValue)
                            waterDropShader.setFloatUniform(
                                "uPrimaryColor",
                                primaryColor.red,
                                primaryColor.green,
                                primaryColor.blue,
                                1f,
                            )
                            waterDropShader.setFloatUniform(
                                "uSecondaryColor",
                                secondaryColor.red,
                                secondaryColor.green,
                                secondaryColor.blue,
                                1f,
                            )
                            RenderEffect
                                .createRuntimeShaderEffect(waterDropShader, "content")
                                .asComposeRenderEffect()
                        }

                        CardVisualEffect.REVERSE_DUST -> {
                            frameTick.longValue
                            val progress = dustProgress.value
                            reverseDustShader.setFloatUniform("uResolution", w, h)
                            reverseDustShader.setFloatUniform("uProgress", progress)
                            reverseDustShader.setFloatUniform("uTime", shaderTimeSec.floatValue)
                            reverseDustShader.setFloatUniform("uAudioGlow", ambientGlow)
                            reverseDustShader.setFloatUniform(
                                "uPrimaryColor",
                                primaryColor.red,
                                primaryColor.green,
                                primaryColor.blue,
                                1f,
                            )
                            reverseDustShader.setFloatUniform(
                                "uSecondaryColor",
                                secondaryColor.red,
                                secondaryColor.green,
                                secondaryColor.blue,
                                1f,
                            )
                            RenderEffect
                                .createRuntimeShaderEffect(reverseDustShader, "content")
                                .asComposeRenderEffect()
                        }
                    }
                }
                .clip(cardShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.24f + ambientGlow * 0.18f),
                            secondaryColor.copy(alpha = 0.16f + ambientGlow * 0.12f),
                            Color(0xFF12121D).copy(alpha = 0.92f),
                        ),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp),
            ) {
                // Top Album & Metadata Header (supports non-consuming tap-to-splash in RIPPLE / WATER_DROP)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(selectedEffect) {
                            if (selectedEffect != CardVisualEffect.RIPPLE &&
                                selectedEffect != CardVisualEffect.WATER_DROP
                            ) {
                                return@pointerInput
                            }
                            awaitEachGesture {
                                val down = awaitFirstDown(
                                    requireUnconsumed = false,
                                    pass = PointerEventPass.Initial,
                                )
                                if (selectedEffect == CardVisualEffect.WATER_DROP) {
                                    waterDrops.spawn(
                                        px = down.position.x,
                                        py = down.position.y,
                                        power = 0.92f,
                                    )
                                } else {
                                    rippleTouchOrigin[0] = down.position
                                }
                            }
                        }
                        .padding(horizontal = 16.dp),
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(92.dp)
                            .drawBehind {
                                val cornerPx = 22.dp.toPx()
                                if (!isAnimationActive) {
                                    drawRoundRect(
                                        color = primaryColor.copy(alpha = 0.26f),
                                        topLeft = Offset.Zero,
                                        size = size,
                                        cornerRadius = CornerRadius(cornerPx, cornerPx),
                                        style = Stroke(width = 1.2.dp.toPx()),
                                    )
                                    return@drawBehind
                                }

                                val pulse = animationParams.scaleNormalized
                                val strokePx = (1.6f + glow * 1.8f).dp.toPx()
                                val half = strokePx * 0.5f
                                val rimCorner = cornerPx - half
                                val rimAlpha = (0.32f + glow * 0.65f).coerceIn(0.28f, 1f)

                                val glowRadius = size.minDimension * (0.56f + pulse * 0.22f)
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            primaryColor.copy(alpha = 0.20f + pulse * 0.26f),
                                            Color.Transparent,
                                        ),
                                        center = center,
                                        radius = glowRadius,
                                    ),
                                    radius = glowRadius,
                                    center = center,
                                )

                                if (selectedEffect == CardVisualEffect.BORDER_GLOW) {
                                    rimRotMatrix.reset()
                                    rimRotMatrix.setRotate(rimAngleDeg.floatValue, center.x, center.y)
                                    val rimShader = SweepGradient(
                                        center.x,
                                        center.y,
                                        intArrayOf(
                                            Color.Transparent.toArgb(),
                                            secondaryColor.copy(alpha = rimAlpha * 0.6f).toArgb(),
                                            primaryColor.copy(alpha = rimAlpha).toArgb(),
                                            highlightColor.copy(alpha = rimAlpha).toArgb(),
                                            Color.Transparent.toArgb(),
                                        ),
                                        floatArrayOf(0.0f, 0.35f, 0.68f, 0.90f, 1.0f),
                                    ).apply {
                                        setLocalMatrix(rimRotMatrix)
                                    }
                                    drawRoundRect(
                                        brush = ShaderBrush(rimShader),
                                        topLeft = Offset(half, half),
                                        size = Size(size.width - strokePx, size.height - strokePx),
                                        cornerRadius = CornerRadius(rimCorner, rimCorner),
                                        style = Stroke(width = strokePx),
                                    )
                                } else {
                                    drawRoundRect(
                                        color = primaryColor.copy(alpha = 0.30f + pulse * 0.30f),
                                        topLeft = Offset(half, half),
                                        size = Size(size.width - strokePx, size.height - strokePx),
                                        cornerRadius = CornerRadius(rimCorner, rimCorner),
                                        style = Stroke(width = 1.4.dp.toPx()),
                                    )
                                }
                            }
                            .padding(4.dp),
                    ) {
                        val artScale = if (isAnimationActive) albumScale.value else 1f
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = artScale
                                    scaleY = artScale
                                }
                                .clip(RoundedCornerShape(18.dp))
                                .background(primaryColor.copy(alpha = 0.22f)),
                        ) {
                            if (artworkBitmap != null) {
                                Image(
                                    bitmap = artworkBitmap,
                                    contentDescription = stringResource(R.string.desc_album_art),
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.GraphicEq,
                                    contentDescription = stringResource(R.string.desc_album_art),
                                    tint = primaryColor,
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            LiveStatusPill(
                                isLive = isPlayingLive,
                                accent = primaryColor,
                            )
                            MetricMiniChip(
                                text = animationParams.mood.name,
                                accent = moodAccentColor(animationParams.mood, primaryColor, secondaryColor),
                            )
                            MetricMiniChip(
                                text = if (telemetry.estimatedBpm > 0) {
                                    stringResource(R.string.label_bpm_format, telemetry.estimatedBpm)
                                } else {
                                    stringResource(R.string.label_bpm_detecting)
                                },
                                accent = secondaryColor,
                            )
                            if (telemetry.isLive && telemetry.estimatedSourceDb > -90f) {
                                MetricMiniChip(
                                    text = String.format(Locale.US, "%.0f dB", telemetry.estimatedSourceDb),
                                    accent = Color.White.copy(alpha = 0.85f),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = if (track.hasTrack) track.title else stringResource(R.string.label_listening_audio),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        val subtitle = when {
                            track.hasTrack && track.artist.isNotBlank() ->
                                if (track.album.isNotBlank()) "${track.artist} • ${track.album}" else track.artist
                            !hasNotificationAccess -> stringResource(R.string.label_grant_notification_hint)
                            else -> stringResource(R.string.label_play_music_hint)
                        }
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.74f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        if (!hasNotificationAccess) {
                            Spacer(modifier = Modifier.height(8.dp))
                            AssistChip(
                                onClick = onRequestNotificationAccess,
                                label = {
                                    Text(
                                        text = stringResource(R.string.action_grant_media_access),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Rounded.NotificationsActive,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = primaryColor.copy(alpha = 0.22f),
                                    labelColor = Color.White,
                                    leadingIconContentColor = primaryColor,
                                ),
                                border = null,
                                modifier = Modifier.height(28.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider(
                    thickness = 1.dp,
                    color = Color.White.copy(alpha = 0.08f),
                    modifier = Modifier.padding(horizontal = 16.dp),
                )

                Spacer(modifier = Modifier.height(10.dp))

                CardEffectSelectorRow(
                    selectedEffect = selectedEffect,
                    primaryColor = primaryColor,
                    onSelectEffect = onSelectEffect,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private fun moodAccentColor(
    mood: MotionMood,
    primaryColor: Color,
    secondaryColor: Color,
): Color = when (mood) {
    MotionMood.CALM -> Color.White.copy(alpha = 0.72f)
    MotionMood.GROOVE -> secondaryColor
    MotionMood.ENERGETIC -> primaryColor
    MotionMood.DROP -> lerp(primaryColor, Color.White, 0.55f)
}

@Composable
private fun LiveStatusPill(
    modifier: Modifier = Modifier,
    isLive: Boolean,
    accent: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(if (isLive) accent.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (isLive) Color(0xFF4ADE80) else Color.White.copy(alpha = 0.45f)),
        )
        Text(
            text = stringResource(if (isLive) R.string.label_live else R.string.label_idle),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
    }
}

@Composable
private fun MetricMiniChip(
    modifier: Modifier = Modifier,
    text: String,
    accent: Color,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            maxLines = 1,
        )
    }
}
