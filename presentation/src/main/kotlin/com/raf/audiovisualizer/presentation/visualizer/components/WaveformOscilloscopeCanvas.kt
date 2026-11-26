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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.raf.audiovisualizer.domain.model.AudioTelemetry
import com.raf.audiovisualizer.presentation.R
import kotlinx.coroutines.isActive
import java.util.Locale

/**
 * 60fps interpolated time-domain PCM oscilloscope and mirrored ambient ribbon visualizer driven by
 * live `onWaveFormDataCapture` samples + beat pulse glow.
 */
@Composable
fun WaveformOscilloscopeCanvas(
    modifier: Modifier = Modifier,
    telemetry: AudioTelemetry,
    primaryColor: Color,
    secondaryColor: Color,
) {
    val latestTelemetry by rememberUpdatedState(telemetry)
    val displayWave = remember { FloatArray(AudioTelemetry.WAVEFORM_SAMPLE_COUNT) }
    val frameTick = remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        while (isActive) {
            withFrameNanos { now ->
                val target = latestTelemetry.waveformSamples
                val count = minOf(displayWave.size, target.size)
                (0 until count).forEach { i ->
                    displayWave[i] = displayWave[i] * 0.55f + target[i] * 0.45f
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
                color = secondaryColor.copy(alpha = 0.22f),
                shape = RoundedCornerShape(28.dp),
            )
            .padding(18.dp),
    ) {
        Text(
            text = stringResource(R.string.title_waveform_scope),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
        )
        Text(
            text = stringResource(R.string.subtitle_waveform_scope),
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
                frameTick.longValue
                val w = size.width
                val h = size.height
                if (w <= 1f || h <= 1f) return@Canvas

                val centerY = h * 0.5f
                val maxAmpPx = h * 0.42f

                // Reference guide lines (+1.0, 0.0, -1.0)
                listOf(centerY - maxAmpPx * 0.5f, centerY, centerY + maxAmpPx * 0.5f).forEachIndexed { idx, y ->
                    drawLine(
                        color = Color.White.copy(alpha = if (idx == 1) 0.14f else 0.06f),
                        start = Offset(0f, y),
                        end = Offset(w, y),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                val count = displayWave.size
                val stepX = w / (count - 1).coerceAtLeast(1)

                val wavePath = Path()
                val fillPath = Path()
                var prevX = 0f
                var prevY = centerY - displayWave[0] * maxAmpPx
                wavePath.moveTo(prevX, prevY)
                fillPath.moveTo(0f, centerY)
                fillPath.lineTo(prevX, prevY)

                (1 until count).forEach { i ->
                    val x = i * stepX
                    val y = centerY - displayWave[i].coerceIn(-1f, 1f) * maxAmpPx
                    val midX = (prevX + x) * 0.5f
                    val midY = (prevY + y) * 0.5f
                    wavePath.quadraticTo(prevX, prevY, midX, midY)
                    fillPath.quadraticTo(prevX, prevY, midX, midY)
                    prevX = x
                    prevY = y
                }
                wavePath.lineTo(w, prevY)
                fillPath.lineTo(w, prevY)
                fillPath.lineTo(w, centerY)
                fillPath.close()

                // Ambient ribbon fill under the waveform curve
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.34f),
                            secondaryColor.copy(alpha = 0.12f),
                            primaryColor.copy(alpha = 0.34f),
                        ),
                        startY = centerY - maxAmpPx,
                        endY = centerY + maxAmpPx,
                    ),
                )

                val pulse = latestTelemetry.beatPulse
                val strokeBrush = Brush.horizontalGradient(
                    colors = listOf(primaryColor, lerp(primaryColor, secondaryColor, 0.5f), secondaryColor),
                )
                // Soft outer neon glow pass
                drawPath(
                    path = wavePath,
                    brush = strokeBrush,
                    alpha = (0.28f + pulse * 0.35f).coerceIn(0.20f, 0.70f),
                    style = Stroke(
                        width = (5.5f + pulse * 4f).dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
                // Crisp oscilloscope core trace
                drawPath(
                    path = wavePath,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            lerp(primaryColor, Color.White, 0.25f),
                            Color.White.copy(alpha = 0.94f),
                            lerp(secondaryColor, Color.White, 0.25f),
                        ),
                    ),
                    style = Stroke(
                        width = (2.0f + pulse * 1.1f).dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            QuickStatTile(
                label = "RMS",
                value = String.format(Locale.US, "%.2f", telemetry.waveformRms),
                percent = "${(telemetry.waveformRms * 100f).toInt()}%",
                accent = primaryColor,
                modifier = Modifier.weight(1f),
            )
            QuickStatTile(
                label = "Peak",
                value = String.format(Locale.US, "%.2f", telemetry.waveformPeak),
                percent = "${(telemetry.waveformPeak * 100f).toInt()}%",
                accent = secondaryColor,
                modifier = Modifier.weight(1f),
            )
            QuickStatTile(
                label = "ZCR",
                value = String.format(Locale.US, "%.2f", telemetry.zeroCrossingRate),
                percent = "${(telemetry.zeroCrossingRate * 100f).toInt()}%",
                accent = lerp(primaryColor, secondaryColor, 0.5f),
                modifier = Modifier.weight(1f),
            )
            QuickStatTile(
                label = "Crest",
                value = String.format(Locale.US, "%.2f", telemetry.crestFactor),
                percent = "${(telemetry.crestFactor * 100f).toInt()}%",
                accent = Color.White,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
