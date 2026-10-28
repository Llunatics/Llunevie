package com.llunaticsys.llunevie.data.parser

import android.graphics.Bitmap
import android.graphics.Color
import androidx.palette.graphics.Palette

object SpineColorExtractor {

    fun extractSpineColors(bitmap: Bitmap?): Pair<Long, Long> {
        if (bitmap == null) return Pair(0xFF2B3A4AL, 0xFFFAF6EBL)
        return try {
            val palette = Palette.from(bitmap).generate()
            val rgb = palette.getDominantColor(0x2B3A4A)
            val r = Color.red(rgb)
            val g = Color.green(rgb)
            val b = Color.blue(rgb)
            val lum = 0.299 * r + 0.587 * g + 0.114 * b
            val spineHex = (0xFF000000L or ((r.toLong() and 0xFF) shl 16) or ((g.toLong() and 0xFF) shl 8) or (b.toLong() and 0xFF))
            val textHex = if (lum > 155.0) 0xFF1E1B18L else 0xFFFAF6EBL
            Pair(spineHex, textHex)
        } catch (_: Exception) {
            Pair(0xFF2B3A4AL, 0xFFFAF6EBL)
        }
    }
}
