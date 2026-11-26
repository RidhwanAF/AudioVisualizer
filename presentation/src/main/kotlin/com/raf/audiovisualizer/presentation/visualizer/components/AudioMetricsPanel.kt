package com.raf.audiovisualizer.presentation.visualizer.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.audiovisualizer.domain.model.AudioAnimationParams
import com.raf.audiovisualizer.domain.model.AudioTelemetry
import com.raf.audiovisualizer.domain.model.FrequencyBandLevel
import com.raf.audiovisualizer.presentation.R
import java.util.Locale

/**
 * Detailed real-time telemetry breakdown displaying every extracted audio feature and synthesized
 * [AudioAnimationParams] with BOTH:
 * - Raw measurement (FFT magnitude, dB, Hz, BPM, speed multiplier, attack/release ms)
 * - Formatted normalized value (`0.00 – 1.00`) and percentage (`0% – 100%`) with animated meter.
 */
@Composable
fun AudioMetricsPanel(
    modifier: Modifier = Modifier,
    telemetry: AudioTelemetry,
    animationParams: AudioAnimationParams,
    primaryColor: Color,
    secondaryColor: Color,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "animation_engine") {
            MetricSectionCard(
                title = stringResource(R.string.section_animation_engine),
                accent = lerp(primaryColor, Color.White, 0.25f),
            ) {
                HardwareInfoRow(
                    label = stringResource(R.string.metric_motion_mood),
                    value = animationParams.mood.name,
                )
                FormattedMetricRow(
                    label = stringResource(R.string.metric_speed_multiplier),
                    rawText = String.format(Locale.US, "%.2fx speed", animationParams.speedMultiplier),
                    normalized = animationParams.speedNormalized,
                    accent = primaryColor,
                )
                FormattedMetricRow(
                    label = stringResource(R.string.metric_scale_pulse),
                    rawText = String.format(
                        Locale.US,
                        "%.3fx (in %dms / out %dms)",
                        animationParams.scaleFactor,
                        animationParams.scaleAttackMs,
                        animationParams.scaleReleaseMs,
                    ),
                    normalized = animationParams.scaleNormalized,
                    accent = lerp(primaryColor, secondaryColor, 0.35f),
                )
                FormattedMetricRow(
                    label = stringResource(R.string.metric_ripple_drive),
                    rawText = String.format(Locale.US, "trig %.2f", animationParams.rippleTriggerStrength),
                    normalized = animationParams.rippleIntensity,
                    accent = secondaryColor,
                )
                FormattedMetricRow(
                    label = stringResource(R.string.metric_snake_speed),
                    rawText = String.format(Locale.US, "%.0f°/s", animationParams.snakeSpeedDegPerSec),
                    normalized = ((animationParams.snakeSpeedDegPerSec - 24f) / 296f).coerceIn(0f, 1f),
                    accent = primaryColor,
                )
                FormattedMetricRow(
                    label = stringResource(R.string.metric_snake_glow),
                    rawText = String.format(
                        Locale.US,
                        "tail %d%% • aura %d%%",
                        (animationParams.snakeTailLengthFraction * 100f).toInt(),
                        (animationParams.ambientGlowIntensity * 100f).toInt(),
                    ),
                    normalized = animationParams.snakeGlowThickness,
                    accent = lerp(primaryColor, secondaryColor, 0.65f),
                )
            }
        }

        item(key = "freq_bands") {
            MetricSectionCard(
                title = stringResource(R.string.section_frequency_bands),
                accent = primaryColor,
            ) {
                BandMetricRow(stringResource(R.string.metric_sub_bass), telemetry.subBass, primaryColor)
                BandMetricRow(stringResource(R.string.metric_bass), telemetry.bass, lerp(primaryColor, secondaryColor, 0.16f))
                BandMetricRow(stringResource(R.string.metric_low_mid), telemetry.lowMid, lerp(primaryColor, secondaryColor, 0.32f))
                BandMetricRow(stringResource(R.string.metric_mid), telemetry.mid, lerp(primaryColor, secondaryColor, 0.50f))
                BandMetricRow(stringResource(R.string.metric_high_mid), telemetry.highMid, lerp(primaryColor, secondaryColor, 0.68f))
                BandMetricRow(stringResource(R.string.metric_treble), telemetry.treble, lerp(primaryColor, secondaryColor, 0.84f))
                BandMetricRow(stringResource(R.string.metric_brilliance), telemetry.brilliance, secondaryColor)
            }
        }

        item(key = "rhythm_dynamics") {
            MetricSectionCard(
                title = stringResource(R.string.section_rhythm_dynamics),
                accent = secondaryColor,
            ) {
                FormattedMetricRow(
                    label = stringResource(R.string.metric_beat_pulse),
                    rawText = String.format(Locale.US, "raw %.3f", telemetry.beatPulse),
                    normalized = telemetry.beatPulse,
                    accent = primaryColor,
                )
                FormattedMetricRow(
                    label = stringResource(R.string.metric_energy),
                    rawText = String.format(Locale.US, "raw %.3f", telemetry.energy),
                    normalized = telemetry.energy,
                    accent = lerp(primaryColor, secondaryColor, 0.35f),
                )
                FormattedMetricRow(
                    label = stringResource(R.string.metric_spectral_flux),
                    rawText = String.format(Locale.US, "Δ %.3f", telemetry.spectralFluxRaw),
                    normalized = telemetry.spectralFluxNormalized,
                    accent = lerp(primaryColor, secondaryColor, 0.65f),
                )
                FormattedMetricRow(
                    label = stringResource(R.string.metric_tempo_bpm),
                    rawText = if (telemetry.estimatedBpm > 0) "${telemetry.estimatedBpm} BPM" else "-- BPM",
                    normalized = telemetry.bpmNormalized,
                    accent = secondaryColor,
                )
                FormattedMetricRow(
                    label = stringResource(R.string.metric_rhythm_density),
                    rawText = String.format(Locale.US, "%.2f beats/s", telemetry.rhythmDensity * 3.5f),
                    normalized = telemetry.rhythmDensity,
                    accent = primaryColor,
                )
                FormattedMetricRow(
                    label = stringResource(R.string.metric_spectral_centroid),
                    rawText = String.format(Locale.US, "%.0f Hz", telemetry.spectralCentroidHz),
                    normalized = telemetry.brightness,
                    accent = secondaryColor,
                )
            }
        }

        item(key = "waveform_hardware") {
            MetricSectionCard(
                title = stringResource(R.string.section_waveform_hardware),
                accent = lerp(primaryColor, secondaryColor, 0.5f),
            ) {
                FormattedMetricRow(
                    label = stringResource(R.string.metric_waveform_rms),
                    rawText = String.format(Locale.US, "amp %.3f", telemetry.waveformRms),
                    normalized = telemetry.waveformRms,
                    accent = primaryColor,
                )
                FormattedMetricRow(
                    label = stringResource(R.string.metric_waveform_peak),
                    rawText = String.format(Locale.US, "peak %.3f", telemetry.waveformPeak),
                    normalized = telemetry.waveformPeak,
                    accent = secondaryColor,
                )
                FormattedMetricRow(
                    label = stringResource(R.string.metric_zero_crossing),
                    rawText = String.format(Locale.US, "zcr %.3f", telemetry.zeroCrossingRate),
                    normalized = telemetry.zeroCrossingRate,
                    accent = lerp(primaryColor, secondaryColor, 0.5f),
                )
                FormattedMetricRow(
                    label = stringResource(R.string.metric_loudness_gain),
                    rawText = String.format(Locale.US, "%.1f dBFS", telemetry.estimatedSourceDb),
                    normalized = telemetry.loudnessGain,
                    accent = primaryColor,
                )
                FormattedMetricRow(
                    label = stringResource(R.string.metric_crest_factor),
                    rawText = String.format(Locale.US, "%.1f dB", (telemetry.peakDb - telemetry.rmsDb).coerceAtLeast(0f)),
                    normalized = telemetry.crestFactor,
                    accent = secondaryColor,
                )
                HardwareInfoRow(
                    label = stringResource(R.string.metric_raw_rms_peak),
                    value = String.format(Locale.US, "%.1f dB / %.1f dB", telemetry.rmsDb, telemetry.peakDb),
                )
                HardwareInfoRow(
                    label = stringResource(R.string.metric_volume_comp),
                    value = String.format(Locale.US, "%.1f dB", telemetry.volumeAttenuationDb),
                )
                HardwareInfoRow(
                    label = stringResource(R.string.metric_sample_rate),
                    value = "${telemetry.samplingRateHz} Hz • ${telemetry.captureSize} bins",
                )
            }
        }
    }
}

@Composable
private fun MetricSectionCard(
    modifier: Modifier = Modifier,
    title: String,
    accent: Color,
    content: @Composable () -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF10101A).copy(alpha = 0.80f))
            .border(
                width = 1.dp,
                color = accent.copy(alpha = 0.20f),
                shape = RoundedCornerShape(24.dp),
            )
            .padding(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        content()
    }
}

@Composable
private fun BandMetricRow(
    label: String,
    band: FrequencyBandLevel,
    accent: Color,
) {
    FormattedMetricRow(
        label = label,
        rawText = String.format(Locale.US, "%.1f mag (pk %.0f)", band.rawMagnitude, band.peakMagnitude),
        normalized = band.normalized,
        accent = accent,
    )
}

@Composable
private fun FormattedMetricRow(
    modifier: Modifier = Modifier,
    label: String,
    rawText: String,
    normalized: Float,
    accent: Color,
) {
    val clamped = normalized.coerceIn(0f, 1f)
    val animatedFill by animateFloatAsState(
        targetValue = clamped,
        animationSpec = tween(durationMillis = 65),
        label = "metricFill",
    )
    val percent = (clamped * 100f).toInt()

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
                Text(
                    text = rawText,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color.White.copy(alpha = 0.58f),
                )
            }
            Text(
                text = String.format(Locale.US, "%.2f  (%d%%)", clamped, percent),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = accent,
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = animatedFill.coerceIn(0.01f, 1f))
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(accent.copy(alpha = 0.72f), lerp(accent, Color.White, 0.30f)),
                        ),
                    ),
            )
        }
    }
}

@Composable
private fun HardwareInfoRow(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.70f),
        )
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace,
            color = Color.White.copy(alpha = 0.92f),
        )
    }
}
