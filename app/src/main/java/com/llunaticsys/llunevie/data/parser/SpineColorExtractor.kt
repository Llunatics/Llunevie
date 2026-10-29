package com.llunaticsys.llunevie.data.parser

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

object SpineColorExtractor {

    fun extractDominantSpineHex(bitmap: Bitmap?): Long {
        if (bitmap == null) return 0xFF1E3A5FL
        return try {
            val scaled = Bitmap.createScaledBitmap(bitmap, 24, 24, true)
            var bestWeight = -1.0
            var bestR = 30
            var bestG = 58
            var bestB = 95 // Fallback #1E3A5F
            val hsv = FloatArray(3)
            for (y in 0 until 24) {
                for (x in 0 until 24) {
                    val pixel = scaled.getPixel(x, y)
                    val r = Color.red(pixel)
                    val g = Color.green(pixel)
                    val b = Color.blue(pixel)
                    if (r > 235 && g > 235 && b > 235) continue
                    if (r < 18 && g < 18 && b < 18) continue
                    Color.RGBToHSV(r, g, b, hsv)
                    val sat = hsv[1]
                    val value = hsv[2]
                    // Weight pixels by saturation + mid-tone luminance so vibrant/characteristic colors win
                    val lumWeight = 1.0f - abs(value - 0.6f)
                    val weight = (sat * 1.5 + lumWeight * 1.0).toDouble()
                    if (weight > bestWeight) {
                        bestWeight = weight
                        bestR = r
                        bestG = g
                        bestB = b
                    }
                }
            }
            if (scaled != bitmap) scaled.recycle()

            // Slightly darken overly bright colors so white spine text stays readable
            val lum = 0.299 * bestR + 0.587 * bestG + 0.114 * bestB
            if (lum > 175.0) {
                val factor = 175.0 / lum
                bestR = (bestR * factor).toInt().coerceIn(0, 255)
                bestG = (bestG * factor).toInt().coerceIn(0, 255)
                bestB = (bestB * factor).toInt().coerceIn(0, 255)
            }
            (0xFF000000L or ((bestR.toLong() and 0xFF) shl 16) or ((bestG.toLong() and 0xFF) shl 8) or (bestB.toLong() and 0xFF))
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
