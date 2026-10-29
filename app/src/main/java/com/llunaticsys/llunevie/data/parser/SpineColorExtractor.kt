package com.llunaticsys.llunevie.data.parser

import android.graphics.Bitmap
import android.graphics.Color

object SpineColorExtractor {

    private class HueBucket {
        var count = 0
        var sumR = 0L
        var sumG = 0L
        var sumB = 0L
        var sumSat = 0f
    }

    fun extractDominantSpineHex(bitmap: Bitmap?): Long {
        if (bitmap == null) return 0xFF1E3A5FL
        return try {
            val w = bitmap.width
            val h = bitmap.height
            if (w <= 0 || h <= 0) return 0xFF1E3A5FL

            val startX = (w * 0.20f).toInt().coerceIn(0, w - 1)
            val endX = (w * 0.80f).toInt().coerceIn(startX + 1, w)
            val startY = (h * 0.20f).toInt().coerceIn(0, h - 1)
            val endY = (h * 0.80f).toInt().coerceIn(startY + 1, h)

            // 12 hue buckets (0..11) + 1 neutral/low-sat bucket (12)
            val buckets = Array(13) { HueBucket() }
            val hsv = FloatArray(3)

            val stepX = ((endX - startX) / 28).coerceAtLeast(1)
            val stepY = ((endY - startY) / 36).coerceAtLeast(1)

            for (y in startY until endY step stepY) {
                for (x in startX until endX step stepX) {
                    val pixel = bitmap.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)

                    if (r > 230 && g > 230 && b > 230) continue
                    if (r < 20 && g < 20 && b < 20) continue

                    Color.RGBToHSV(r, g, b, hsv)
                    val sat = hsv[1]
                    val bIndex = if (sat < 0.18f) 12 else ((hsv[0] / 30f).toInt() % 12).coerceIn(0, 11)

                    val bucket = buckets[bIndex]
                    bucket.count++
                    bucket.sumR += r
                    bucket.sumG += g
                    bucket.sumB += b
                    bucket.sumSat += sat
                }
            }

            var bestBucket: HueBucket? = null
            var bestScore = -1f

            for (i in 0 until 13) {
                val b = buckets[i]
                if (b.count > 0) {
                    val meanSat = b.sumSat / b.count
                    val score = b.count * (1f + meanSat * 1.5f)
                    if (score > bestScore) {
                        bestScore = score
                        bestBucket = b
                    }
                }
            }

            if (bestBucket == null || bestBucket.count == 0) return 0xFF1E3A5FL

            val avgR = (bestBucket.sumR / bestBucket.count).toInt().coerceIn(0, 255)
            val avgG = (bestBucket.sumG / bestBucket.count).toInt().coerceIn(0, 255)
            val avgB = (bestBucket.sumB / bestBucket.count).toInt().coerceIn(0, 255)

            Color.RGBToHSV(avgR, avgG, avgB, hsv)
            hsv[1] = hsv[1].coerceIn(0.25f, 0.78f)
            hsv[2] = hsv[2].coerceIn(0.28f, 0.62f)

            val finalColor = Color.HSVToColor(hsv)
            (0xFF000000L or (finalColor.toLong() and 0x00FFFFFFL))
        } catch (_: Exception) {
            0xFF1E3A5FL
        }
    }

    fun extractSpineColors(bitmap: Bitmap?): Pair<Long, Long> {
        if (bitmap == null) return Pair(0xFF1E3A5FL, 0xFFFAF6EBL)
        val spineHex = extractDominantSpineHex(bitmap)
        val r = ((spineHex shr 16) and 0xFF).toInt()
        val g = ((spineHex shr 8) and 0xFF).toInt()
        val b = (spineHex and 0xFF).toInt()
        val lum = 0.299 * r + 0.587 * g + 0.114 * b
        val textHex = if (lum > 155.0) 0xFF1E1B18L else 0xFFFAF6EBL
        return Pair(spineHex, textHex)
    }
}
