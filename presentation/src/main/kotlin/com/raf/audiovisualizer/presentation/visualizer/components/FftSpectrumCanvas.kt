package com.raf.audiovisualizer.presentation.visualizer.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.raf.audiovisualizer.domain.model.AudioTelemetry
import com.raf.audiovisualizer.presentation.R
import kotlinx.coroutines.isActive
import java.util.Locale

/**
 * 60fps interpolated 48-band logarithmic FFT spectrum visualizer with peak-hold caps, ambient
 * album-color gradient bars, subtle mirrored floor reflection, and real-time band pills.
 */
@Composable
fun FftSpectrumCanvas(
    modifier: Modifier = Modifier,
    telemetry: AudioTelemetry,
    primaryColor: Color,
    secondaryColor: Color,
) {
    val latestTelemetry by rememberUpdatedState(telemetry)
    val displayBars = remember { FloatArray(AudioTelemetry.FFT_BAR_COUNT) }
    val displayPeaks = remember { FloatArray(AudioTelemetry.FFT_BAR_COUNT) }
    val frameTick = remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { now ->
                val targetBars = latestTelemetry.fftBars
                val targetPeaks = latestTelemetry.peakHoldBars
                val count = minOf(displayBars.size, targetBars.size)
                (0 until count).forEach { i ->
                    val target = targetBars[i]
                    val cur = displayBars[i]
                    val alpha = if (target >= cur) 0.48f else 0.22f
                    displayBars[i] = cur + (target - cur) * alpha

                    val peakTarget = targetPeaks.getOrElse(i) { target }
                    val curPeak = displayPeaks[i]
                    displayPeaks[i] = if (peakTarget >= curPeak) {
                        peakTarget
                    } else {
                        (curPeak - 0.010f).coerceAtLeast(displayBars[i])
                    }
                }
                frameTick.longValue = now
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xFF10101A).copy(alpha = 0.78f))
            .border(
                width = 1.dp,
                color = primaryColor.copy(alpha = 0.22f),
                shape = RoundedCornerShape(28.dp),
            )
            .padding(18.dp),
    ) {
        Text(
            text = stringResource(R.string.title_fft_spectrum),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            text = stringResource(R.string.subtitle_fft_spectrum),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.62f),
        )

        Spacer(modifier = Modifier.height(14.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Read frame tick so Compose redraws smoothly at 60fps without recomposing parents
                frameTick.longValue

                val w = size.width
                val h = size.height
                if (w <= 1f || h <= 1f) return@Canvas

                val baselineY = h * 0.80f
                val maxBarH = baselineY - 8.dp.toPx()
                val reflectionH = (h - baselineY - 6.dp.toPx()).coerceAtLeast(8f)

                // Subtle horizontal dB reference grid lines
                listOf(0.25f, 0.50f, 0.75f, 1.0f).forEach { frac ->
                    val y = baselineY - maxBarH * frac
                    drawLine(
                        color = Color.White.copy(alpha = 0.06f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                val barCount = displayBars.size
                val slotW = w / barCount.toFloat()
                val gap = (slotW * 0.28f).coerceIn(1.5f, 4.dp.toPx())
                val barW = (slotW - gap).coerceAtLeast(2f)
                val radius = CornerRadius(barW * 0.5f, barW * 0.5f)
                val capH = 2.4.dp.toPx()

                (0 until barCount).forEach { i ->
                    val frac = i.toFloat() / (barCount - 1).coerceAtLeast(1)
                    val level = displayBars[i].coerceIn(0f, 1f)
                    val peak = displayPeaks[i].coerceIn(level, 1f)

                    val barH = (level * maxBarH).coerceAtLeast(3.dp.toPx())
                    val x = i * slotW + gap * 0.5f
                    val topY = baselineY - barH

                    val barBaseColor = lerp(primaryColor, secondaryColor, frac)
                    val barTipColor = lerp(barBaseColor, Color.White, 0.36f)

                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(barTipColor, barBaseColor, barBaseColor.copy(alpha = 0.45f)),
                            startY = topY,
                            endY = baselineY,
                        ),
                        topLeft = Offset(x, topY),
                        size = Size(barW, barH),
                        cornerRadius = radius,
                    )

                    // Peak-hold cap floating above each bar
                    if (peak > 0.03f) {
                        val peakY = (baselineY - peak * maxBarH - capH - 2.dp.toPx()).coerceAtLeast(2f)
                        drawRoundRect(
                            color = lerp(barTipColor, Color.White, 0.55f).copy(alpha = 0.88f),
                            topLeft = Offset(x, peakY),
                            size = Size(barW, capH),
                            cornerRadius = radius,
                        )
                    }

                    // Soft mirrored floor reflection
                    val reflH = (level * reflectionH).coerceAtLeast(2f)
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(barBaseColor.copy(alpha = 0.28f), Color.Transparent),
                            startY = baselineY + 2.dp.toPx(),
                            endY = baselineY + 2.dp.toPx() + reflH,
                        ),
                        topLeft = Offset(x, baselineY + 2.dp.toPx()),
                        size = Size(barW, reflH),
                        cornerRadius = radius,
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        ) {
            listOf("25Hz", "120Hz", "500Hz", "2kHz", "6kHz", "14kHz").forEach { label ->
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.48f),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            QuickStatTile(
                label = "Bass",
                value = String.format(Locale.US, "%.2f", telemetry.bass.normalized),
                percent = "${telemetry.bass.percentage}%",
                accent = primaryColor,
                modifier = Modifier.weight(1f),
            )
            QuickStatTile(
                label = "Mid",
                value = String.format(Locale.US, "%.2f", telemetry.mid.normalized),
                percent = "${telemetry.mid.percentage}%",
                accent = lerp(primaryColor, secondaryColor, 0.5f),
                modifier = Modifier.weight(1f),
            )
            QuickStatTile(
                label = "Treble",
                value = String.format(Locale.US, "%.2f", telemetry.treble.normalized),
                percent = "${telemetry.treble.percentage}%",
                accent = secondaryColor,
                modifier = Modifier.weight(1f),
            )
            QuickStatTile(
                label = "Pulse",
                value = String.format(Locale.US, "%.2f", telemetry.beatPulse),
                percent = "${(telemetry.beatPulse * 100f).toInt()}%",
                accent = Color.White,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
internal fun QuickStatTile(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    percent: String,
    accent: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.06f))
            .padding(vertical = 8.dp, horizontal = 6.dp),
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.60f),
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = accent,
        )
        Text(
            text = percent,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.72f),
        )
    }
}
