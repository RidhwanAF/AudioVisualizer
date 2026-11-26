package com.raf.audiovisualizer.data.media

import android.graphics.Bitmap
import android.graphics.Color
import com.raf.audiovisualizer.domain.model.NowPlayingTrack
import kotlin.math.abs

/**
 * Pure first-party ambient color palette extractor for album artwork bitmaps.
 * Samples a downscaled grid into HSV hue buckets to pick a vibrant primary accent, a harmonious
 * secondary accent, and a deep ambient surface tint with zero external dependencies.
 */
object MediaArtworkColorExtractor {

    data class AmbientPalette(
        val primaryArgb: Int,
        val secondaryArgb: Int,
        val ambientSurfaceArgb: Int,
    )

    fun extract(bitmap: Bitmap?): AmbientPalette {
        if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
            return AmbientPalette(
                primaryArgb = NowPlayingTrack.DEFAULT_PRIMARY_ARGB,
                secondaryArgb = NowPlayingTrack.DEFAULT_SECONDARY_ARGB,
                ambientSurfaceArgb = NowPlayingTrack.DEFAULT_AMBIENT_ARGB,
            )
        }

        val scaled = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
        val pixels = IntArray(24 * 24)
        scaled.getPixels(pixels, 0, 24, 0, 0, 24, 24)
        if (scaled !== bitmap) scaled.recycle()

        val bucketWeights = FloatArray(HUE_BUCKETS)
        val bucketHueSum = FloatArray(HUE_BUCKETS)
        val bucketSatSum = FloatArray(HUE_BUCKETS)
        val bucketValSum = FloatArray(HUE_BUCKETS)
        val hsv = FloatArray(3)

        pixels.forEach { px ->
            if (Color.alpha(px) < 180) return@forEach
            Color.colorToHSV(px, hsv)
            val hue = hsv[0]
            val sat = hsv[1]
            val value = hsv[2]
            if (value < 0.14f) return@forEach

            val bucket = ((hue / 360f) * HUE_BUCKETS).toInt().coerceIn(0, HUE_BUCKETS - 1)
            val vibrancy = (0.25f + sat * 1.85f) * (0.35f + value * 0.65f)
            bucketWeights[bucket] += vibrancy
            bucketHueSum[bucket] += hue * vibrancy
            bucketSatSum[bucket] += sat * vibrancy
            bucketValSum[bucket] += value * vibrancy
        }

        var primaryIdx = -1
        var primaryWeight = 0f
        (0 until HUE_BUCKETS).forEach { i ->
            if (bucketWeights[i] > primaryWeight) {
                primaryWeight = bucketWeights[i]
                primaryIdx = i
            }
        }

        if (primaryIdx < 0 || primaryWeight <= 0.1f) {
            return AmbientPalette(
                primaryArgb = NowPlayingTrack.DEFAULT_PRIMARY_ARGB,
                secondaryArgb = NowPlayingTrack.DEFAULT_SECONDARY_ARGB,
                ambientSurfaceArgb = NowPlayingTrack.DEFAULT_AMBIENT_ARGB,
            )
        }

        val primaryHue = bucketHueSum[primaryIdx] / primaryWeight
        val primarySat = (bucketSatSum[primaryIdx] / primaryWeight).coerceIn(0.45f, 0.92f)
        val primaryVal = (bucketValSum[primaryIdx] / primaryWeight).coerceIn(0.68f, 0.98f)
        val primaryColor = Color.HSVToColor(floatArrayOf(primaryHue, primarySat, primaryVal))

        var secondaryIdx = -1
        var secondaryScore = 0f
        (0 until HUE_BUCKETS).forEach { i ->
            val w = bucketWeights[i]
            if (w <= 0.05f || i == primaryIdx) return@forEach
            val h = bucketHueSum[i] / w
            val hueDist = minOf(abs(h - primaryHue), 360f - abs(h - primaryHue))
            if (hueDist >= 28f) {
                val score = w * (0.6f + (hueDist / 180f))
                if (score > secondaryScore) {
                    secondaryScore = score
                    secondaryIdx = i
                }
            }
        }

        val secondaryColor = if (secondaryIdx >= 0) {
            val w = bucketWeights[secondaryIdx]
            val sHue = bucketHueSum[secondaryIdx] / w
            val sSat = (bucketSatSum[secondaryIdx] / w).coerceIn(0.45f, 0.92f)
            val sVal = (bucketValSum[secondaryIdx] / w).coerceIn(0.70f, 0.98f)
            Color.HSVToColor(floatArrayOf(sHue, sSat, sVal))
        } else {
            val shiftedHue = (primaryHue + 42f) % 360f
            Color.HSVToColor(floatArrayOf(shiftedHue, primarySat, 0.92f))
        }

        val ambientSurface = Color.HSVToColor(
            floatArrayOf(
                primaryHue,
                (primarySat * 0.72f).coerceIn(0.28f, 0.70f),
                0.22f,
            ),
        )

        return AmbientPalette(
            primaryArgb = primaryColor,
            secondaryArgb = secondaryColor,
            ambientSurfaceArgb = ambientSurface,
        )
    }

    private const val HUE_BUCKETS = 18
}
