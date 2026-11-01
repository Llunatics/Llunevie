package com.llunaticsys.llunevie.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix as AndroidMatrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.os.Build
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import java.io.File
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect as ComposeRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

object PageSnapshotBitmapRenderer {

    private val typefaceCache = mutableMapOf<String, Typeface>()

    fun getFontAssetPath(fontFamily: String): String? {
        return when (fontFamily) {
            "Literata" -> "fonts/Literata-Regular.ttf"
            "EB Garamond" -> "fonts/EBGaramond-Regular.ttf"
            "Source Serif 4" -> "fonts/SourceSerif4-Regular.ttf"
            "Newsreader" -> "fonts/Newsreader-Regular.ttf"
            "Lora" -> "fonts/Lora-Regular.ttf"
            "Merriweather" -> "fonts/Merriweather-Regular.ttf"
            "Crimson Pro" -> "fonts/CrimsonPro-Regular.ttf"
            "Playfair Display" -> "fonts/PlayfairDisplay-Regular.ttf"
            "PT Serif" -> "fonts/PTSerif-Regular.ttf"
            "Bitter" -> "fonts/Bitter-Regular.ttf"
            "Plus Jakarta Sans" -> "fonts/PlusJakartaSans-Regular.ttf"
            "Inter" -> "fonts/Inter-Regular.ttf"
            "DM Sans" -> "fonts/DMSans-Regular.ttf"
            "Nunito" -> "fonts/Nunito-Regular.ttf"
            "JetBrains Mono" -> "fonts/JetBrainsMono-Regular.ttf"
            else -> null
        }
    }

    fun getSystemTypeface(fontFamily: String, fontWeight: Int, context: Context? = null): Typeface {
        val key = "$fontFamily:$fontWeight"
        typefaceCache[key]?.let { return it }

        val style = if (fontWeight >= 600) Typeface.BOLD else Typeface.NORMAL
        val assetPath = getFontAssetPath(fontFamily)

        val fromAsset = if (context != null && assetPath != null) {
            runCatching { Typeface.createFromAsset(context.assets, assetPath) }.getOrNull()
        } else null

        val tf = fromAsset ?: runCatching {
            when (fontFamily) {
                "Literata" -> Typeface.create("serif", style)
                "EB Garamond" -> Typeface.create("serif-monospace", style)
                "Source Serif 4" -> Typeface.create("serif", style)
                "Newsreader" -> Typeface.create("times", style)
                "Lora" -> Typeface.create("georgia", style)
                "Merriweather" -> Typeface.create("serif", style)
                "Crimson Pro" -> Typeface.create("palatino", style)
                "Playfair Display" -> Typeface.create("casual", style)
                "PT Serif" -> Typeface.create("serif", style)
                "Bitter" -> Typeface.create("slab-serif", style)
                "Plus Jakarta Sans" -> Typeface.create("sans-serif-medium", style)
                "Inter" -> Typeface.create("sans-serif", style)
                "DM Sans" -> Typeface.create("sans-serif-light", style)
                "Nunito" -> Typeface.create("sans-serif-condensed", style)
                "JetBrains Mono" -> Typeface.create("monospace", style)
                else -> Typeface.create(fontFamily, style)
            }
        }.getOrNull() ?: (if (fontFamily == "JetBrains Mono") Typeface.MONOSPACE else if (fontFamily in setOf("Plus Jakarta Sans", "Inter", "DM Sans", "Nunito")) Typeface.SANS_SERIF else Typeface.SERIF)

        val finalTf = if (style != Typeface.NORMAL && fromAsset != null) Typeface.create(tf, style) else tf
        typefaceCache[key] = finalTf
        return finalTf
    }

    suspend fun renderSingleTextPageBitmapAsync(
        widthPx: Int,
        heightPx: Int,
        density: Float = 1f,
        scaledDensity: Float = 1f,
        chapterTitle: String,
        pageText: String,
        pageNumberLabel: String = "",
        bgColor: Color,
        textColor: Color,
        mutedColor: Color,
        fontSizePx: Float,
        lineSpacingMultiplier: Float,
        marginPx: Int = 0,
        fontFamily: String = "Literata",
        fontWeight: Int = 400,
        textAlign: String = "left",
        paraSpacingEm: Float = 1.1f,
        letterSpacingEm: Float = 0f,
        wordSpacingPx: Int = 0,
        paraIndent: Boolean = false,
        highlights: List<com.llunaticsys.llunevie.data.local.HighlightNoteEntity> = emptyList(),
        highlightBgMap: Map<String, Int> = emptyMap()
    ): ImageBitmap = withContext(Dispatchers.Default) {
        renderSingleTextPageBitmap(
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            scaledDensity = scaledDensity,
            chapterTitle = chapterTitle,
            pageText = pageText,
            pageNumberLabel = pageNumberLabel,
            bgColor = bgColor,
            textColor = textColor,
            mutedColor = mutedColor,
            fontSizePx = fontSizePx,
            lineSpacingMultiplier = lineSpacingMultiplier,
            marginPx = marginPx,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            textAlign = textAlign,
            paraSpacingEm = paraSpacingEm,
            letterSpacingEm = letterSpacingEm,
            wordSpacingPx = wordSpacingPx,
            paraIndent = paraIndent,
            highlights = highlights,
            highlightBgMap = highlightBgMap
        )
    }

    fun renderSingleTextPageBitmap(
        widthPx: Int,
        heightPx: Int,
        density: Float = 1f,
        scaledDensity: Float = 1f,
        chapterTitle: String,
        pageText: String,
        pageNumberLabel: String = "",
        bgColor: Color,
        textColor: Color,
        mutedColor: Color,
        fontSizePx: Float,
        lineSpacingMultiplier: Float,
        marginPx: Int = 0,
        fontFamily: String = "Literata",
        fontWeight: Int = 400,
        textAlign: String = "left",
        paraSpacingEm: Float = 1.1f,
        letterSpacingEm: Float = 0f,
        wordSpacingPx: Int = 0,
        paraIndent: Boolean = false,
        highlights: List<com.llunaticsys.llunevie.data.local.HighlightNoteEntity> = emptyList(),
        highlightBgMap: Map<String, Int> = emptyMap()
    ): ImageBitmap {
        val w = widthPx.coerceIn(320, 1920)
        val h = heightPx.coerceIn(420, 2560)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)
        canvas.drawColor(bgColor.toArgb())

        val illustMatch = Regex("""^\[ILLUST:([^|\]]+)""").find(pageText.trim())
        if (illustMatch != null && (chapterTitle.equals("Sampul", true) || chapterTitle.equals("Cover", true) || !pageText.contains("\n\n"))) {
            val imgPath = illustMatch.groupValues[1]
            val imgFile = File(imgPath)
            if (imgFile.exists()) {
                val coverBmp = runCatching { BitmapFactory.decodeFile(imgFile.absolutePath) }.getOrNull()
                if (coverBmp != null) {
                    val scale = minOf(w.toFloat() / coverBmp.width.toFloat(), h.toFloat() / coverBmp.height.toFloat())
                    val dstW = (coverBmp.width * scale).toInt().coerceAtLeast(1)
                    val dstH = (coverBmp.height * scale).toInt().coerceAtLeast(1)
                    val dstX = (w - dstW) / 2
                    val dstY = (h - dstH) / 2
                    val dst = Rect(dstX, dstY, dstX + dstW, dstY + dstH)
                    canvas.drawBitmap(coverBmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                    return bmp.asImageBitmap()
                }
            }
        }

        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor.toArgb()
            textSize = fontSizePx.coerceIn(12f, 120f)
            typeface = getSystemTypeface(fontFamily, fontWeight)
            letterSpacing = letterSpacingEm
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && wordSpacingPx > 0) {
                wordSpacing = wordSpacingPx.toFloat()
            }
            if (fontWeight == 500) {
                style = Paint.Style.FILL_AND_STROKE
                strokeWidth = 0.45f * density
            }
        }

        // Exact horizontal padding (22.dp * density) and vertical padding (20.dp * density top, 24.dp * density bottom)
        val padX = (22f * density).toInt().coerceIn(16, w / 4)
        val padTop = (20f * density).toInt().coerceIn(16, h / 4)
        val padBottom = (24f * density).toInt().coerceIn(16, h / 4)
        val contentW = (w - padX * 2).coerceAtLeast(120)
        val availableBodyH = (h - padTop - padBottom).toFloat().coerceAtLeast(100f)

        val rawClean = pageText
            .replace(Regex("\\[ILLUST:[^]]+]"), "")
            .replace(Regex("\\[PDF_PAGE:[^]]+]"), "")
            .trim()
        val cleanText = if (paraIndent) {
            rawClean.split("\n\n").joinToString("\n\n") { "\u2003\u2003$it" }
        } else rawClean

        val spannable = SpannableString(cleanText)
        highlights.forEach { hl ->
            val quote = hl.quoteText.trim()
            if (quote.isNotBlank() && cleanText.contains(quote, ignoreCase = true)) {
                val colorArgb = highlightBgMap[hl.colorKey] ?: 0x55D4A74A
                var startIndex = 0
                while (startIndex < cleanText.length) {
                    val found = cleanText.indexOf(quote, startIndex, ignoreCase = true)
                    if (found < 0) break
                    spannable.setSpan(
                        BackgroundColorSpan(colorArgb),
                        found,
                        found + quote.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    startIndex = found + quote.length
                }
            }
        }

        val align = when (textAlign.lowercase()) {
            "center" -> Layout.Alignment.ALIGN_CENTER
            else -> Layout.Alignment.ALIGN_NORMAL
        }

        val bodyLayoutBuilder = StaticLayout.Builder
            .obtain(spannable, 0, spannable.length, bodyPaint, contentW)
            .setAlignment(align)
            .setLineSpacing(0f, lineSpacingMultiplier.coerceIn(1.1f, 2.5f))
            .setIncludePad(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && textAlign.equals("justify", ignoreCase = true)) {
            bodyLayoutBuilder.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD)
        }

        val bodyLayout = bodyLayoutBuilder.build()
        var fittedLines = bodyLayout.lineCount
        for (line in 0 until bodyLayout.lineCount) {
            if (bodyLayout.getLineBottom(line) > availableBodyH) {
                fittedLines = line
                break
            }
        }

        canvas.save()
        canvas.translate(padX.toFloat(), padTop.toFloat())
        canvas.clipRect(0f, 0f, contentW.toFloat(), availableBodyH)

        if (fittedLines in 1 until bodyLayout.lineCount) {
            val endOffset = bodyLayout.getLineEnd(fittedLines - 1)
            val subSpannable = spannable.subSequence(0, endOffset)
            val fittedLayout = StaticLayout.Builder
                .obtain(subSpannable, 0, subSpannable.length, bodyPaint, contentW)
                .setAlignment(align)
                .setLineSpacing(0f, lineSpacingMultiplier.coerceIn(1.1f, 2.5f))
                .setIncludePad(false)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && textAlign.equals("justify", ignoreCase = true)) {
                        setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD)
                    }
                }
                .build()
            fittedLayout.draw(canvas)
        } else {
            bodyLayout.draw(canvas)
        }
        canvas.restore()

        return bmp.asImageBitmap()
    }

    suspend fun renderTwoPageSpreadBitmapAsync(
        widthPx: Int,
        heightPx: Int,
        leftTitle: String,
        leftText: String,
        leftPageNum: String,
        rightTitle: String,
        rightText: String,
        rightPageNum: String,
        leftPdfBitmap: Bitmap?,
        rightPdfBitmap: Bitmap?,
        bgColor: Color,
        textColor: Color,
        mutedColor: Color,
        fontSizePx: Float,
        lineSpacingMultiplier: Float,
        fontFamily: String = "Literata",
        fontWeight: Int = 400,
        textAlign: String = "left",
        paraSpacingEm: Float = 1.1f,
        letterSpacingEm: Float = 0f,
        wordSpacingPx: Int = 0,
        paraIndent: Boolean = false,
        highlights: List<com.llunaticsys.llunevie.data.local.HighlightNoteEntity> = emptyList(),
        highlightBgMap: Map<String, Int> = emptyMap()
    ): ImageBitmap = withContext(Dispatchers.Default) {
        renderTwoPageSpreadBitmap(
            widthPx = widthPx,
            heightPx = heightPx,
            leftTitle = leftTitle,
            leftText = leftText,
            leftPageNum = leftPageNum,
            rightTitle = rightTitle,
            rightText = rightText,
            rightPageNum = rightPageNum,
            leftPdfBitmap = leftPdfBitmap,
            rightPdfBitmap = rightPdfBitmap,
            bgColor = bgColor,
            textColor = textColor,
            mutedColor = mutedColor,
            fontSizePx = fontSizePx,
            lineSpacingMultiplier = lineSpacingMultiplier,
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            textAlign = textAlign,
            paraSpacingEm = paraSpacingEm,
            letterSpacingEm = letterSpacingEm,
            wordSpacingPx = wordSpacingPx,
            paraIndent = paraIndent,
            highlights = highlights,
            highlightBgMap = highlightBgMap
        )
    }

    fun renderTwoPageSpreadBitmap(
        widthPx: Int,
        heightPx: Int,
        leftTitle: String,
        leftText: String,
        leftPageNum: String,
        rightTitle: String,
        rightText: String,
        rightPageNum: String,
        leftPdfBitmap: Bitmap?,
        rightPdfBitmap: Bitmap?,
        bgColor: Color,
        textColor: Color,
        mutedColor: Color,
        fontSizePx: Float,
        lineSpacingMultiplier: Float,
        fontFamily: String = "Literata",
        fontWeight: Int = 400,
        textAlign: String = "left",
        paraSpacingEm: Float = 1.1f,
        letterSpacingEm: Float = 0f,
        wordSpacingPx: Int = 0,
        paraIndent: Boolean = false,
        highlights: List<com.llunaticsys.llunevie.data.local.HighlightNoteEntity> = emptyList(),
        highlightBgMap: Map<String, Int> = emptyMap()
    ): ImageBitmap {
        val w = widthPx.coerceIn(640, 1920)
        val h = heightPx.coerceIn(400, 1440)
        val halfW = w / 2
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)
        val isPdf = leftPdfBitmap != null || rightPdfBitmap != null
        canvas.drawColor(if (isPdf) android.graphics.Color.WHITE else bgColor.toArgb())

        fun drawLeaf(
            offsetX: Int,
            title: String,
            text: String,
            pageNum: String,
            pdfBmp: Bitmap?
        ) {
            if (pdfBmp != null && !pdfBmp.isRecycled) {
                val dst = Rect(offsetX, 0, offsetX + halfW, h)
                canvas.drawBitmap(pdfBmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                return
            }

            val illustMatch = Regex("""^\[ILLUST:([^|\]]+)""").find(text.trim())
            if (illustMatch != null && (title.equals("Sampul", true) || title.equals("Cover", true) || !text.contains("\n\n"))) {
                val imgPath = illustMatch.groupValues[1]
                val imgFile = File(imgPath)
                if (imgFile.exists()) {
                    val coverBmp = runCatching { BitmapFactory.decodeFile(imgFile.absolutePath) }.getOrNull()
                    if (coverBmp != null) {
                        val scale = minOf(halfW.toFloat() / coverBmp.width.toFloat(), h.toFloat() / coverBmp.height.toFloat())
                        val dstW = (coverBmp.width * scale).toInt().coerceAtLeast(1)
                        val dstH = (coverBmp.height * scale).toInt().coerceAtLeast(1)
                        val dstX = offsetX + (halfW - dstW) / 2
                        val dstY = (h - dstH) / 2
                        val dst = Rect(dstX, dstY, dstX + dstW, dstY + dstH)
                        canvas.drawBitmap(coverBmp, null, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                        return
                    }
                }
            }
            val pad = 32
            val padTop = 20
            val padBottom = 24
            val leafW = (halfW - pad * 2).coerceAtLeast(100)
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor.toArgb()
                textSize = (fontSizePx * 0.84f).coerceIn(12f, 80f)
                typeface = getSystemTypeface(fontFamily, fontWeight)
                letterSpacing = letterSpacingEm
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && wordSpacingPx > 0) {
                    wordSpacing = wordSpacingPx.toFloat()
                }
                if (fontWeight == 500) {
                    style = Paint.Style.FILL_AND_STROKE
                    strokeWidth = 0.45f * density
                }
            }

            val rawClean = text
                .replace(Regex("\\[ILLUST:[^]]+]"), "")
                .replace(Regex("\\[PDF_PAGE:[^]]+]"), "")
                .trim()
            val clean = if (paraIndent) {
                rawClean.split("\n\n").joinToString("\n\n") { "\u2003\u2003$it" }
            } else rawClean
                .replace(Regex("\\[PDF_PAGE:[^]]+]"), "")
                .trim()

            val spannable = SpannableString(clean)
            highlights.forEach { hl ->
                val quote = hl.quoteText.trim()
                if (quote.isNotBlank() && clean.contains(quote, ignoreCase = true)) {
                    val colorArgb = highlightBgMap[hl.colorKey] ?: 0x55D4A74A
                    var startIndex = 0
                    while (startIndex < clean.length) {
                        val found = clean.indexOf(quote, startIndex, ignoreCase = true)
                        if (found < 0) break
                        spannable.setSpan(
                            BackgroundColorSpan(colorArgb),
                            found,
                            found + quote.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        startIndex = found + quote.length
                    }
                }
            }

            val align = when (textAlign.lowercase()) {
                "center" -> Layout.Alignment.ALIGN_CENTER
                else -> Layout.Alignment.ALIGN_NORMAL
            }

            val builder = StaticLayout.Builder
                .obtain(spannable, 0, spannable.length, bodyPaint, leafW)
                .setAlignment(align)
                .setLineSpacing(0f, lineSpacingMultiplier.coerceIn(1.1f, 2.5f))
                .setIncludePad(false)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && textAlign.equals("justify", ignoreCase = true)) {
                builder.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD)
            }

            val fullLayout = builder.build()
            val availableBodyH = (h - padTop - padBottom).toFloat().coerceAtLeast(100f)

            var fittedLines = fullLayout.lineCount
            for (line in 0 until fullLayout.lineCount) {
                if (fullLayout.getLineBottom(line) > availableBodyH) {
                    fittedLines = line
                    break
                }
            }

            canvas.save()
            canvas.translate((offsetX + pad).toFloat(), padTop.toFloat())
            canvas.clipRect(0f, 0f, leafW.toFloat(), availableBodyH)

            if (fittedLines in 1 until fullLayout.lineCount) {
                val endOffset = fullLayout.getLineEnd(fittedLines - 1)
                val subSpannable = spannable.subSequence(0, endOffset)
                val fittedLayout = StaticLayout.Builder
                    .obtain(subSpannable, 0, subSpannable.length, bodyPaint, leafW)
                    .setAlignment(align)
                    .setLineSpacing(0f, lineSpacingMultiplier.coerceIn(1.1f, 2.5f))
                    .setIncludePad(false)
                    .apply {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && textAlign.equals("justify", ignoreCase = true)) {
                            setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD)
                        }
                    }
                    .build()
                fittedLayout.draw(canvas)
            } else {
                fullLayout.draw(canvas)
            }
            canvas.restore()
        }

        drawLeaf(0, leftTitle, leftText, leftPageNum, leftPdfBitmap)
        drawLeaf(halfW, rightTitle, rightText, rightPageNum, rightPdfBitmap)

        val spinePaint = Paint().apply {
            color = android.graphics.Color.argb(45, 0, 0, 0)
            strokeWidth = 3f
        }
        canvas.drawLine(halfW.toFloat(), 0f, halfW.toFloat(), h.toFloat(), spinePaint)

        return bmp.asImageBitmap()
    }
}

fun reflectPointAcrossLine(pt: Offset, a: Offset, b: Offset): Offset {
    val vx = b.x - a.x
    val vy = b.y - a.y
    val lenSq = (vx * vx + vy * vy).coerceAtLeast(1e-4f)
    val u = ((pt.x - a.x) * vx + (pt.y - a.y) * vy) / lenSq
    val projX = a.x + u * vx
    val projY = a.y + u * vy
    return Offset(2f * projX - pt.x, 2f * projY - pt.y)
}

fun computePageRect(
    bitmap: ImageBitmap,
    isPdfBook: Boolean,
    isTwoPageSpread: Boolean,
    canvasW: Float,
    canvasH: Float
): ComposeRect {
    if (isPdfBook && !isTwoPageSpread) {
        val bmpRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
        val canvasRatio = canvasW / canvasH
        val dstW: Float
        val dstH: Float
        if (bmpRatio > canvasRatio) {
            dstW = canvasW
            dstH = canvasW / bmpRatio
        } else {
            dstH = canvasH
            dstW = canvasH * bmpRatio
        }
        val offsetX = (canvasW - dstW) / 2f
        val offsetY = (canvasH - dstH) / 2f
        return ComposeRect(offsetX, offsetY, offsetX + dstW, offsetY + dstH)
    } else {
        return ComposeRect(0f, 0f, canvasW, canvasH)
    }
}

private fun DrawScope.drawPageBitmapFitted(
    bitmap: ImageBitmap,
    isPdfBook: Boolean,
    isTwoPageSpread: Boolean,
    colorFilter: ColorFilter? = null
) {
    val canvasW = size.width.roundToInt().coerceAtLeast(1)
    val canvasH = size.height.roundToInt().coerceAtLeast(1)
    if (isPdfBook && !isTwoPageSpread) {
        val bmpRatio = bitmap.width.toFloat() / bitmap.height.coerceAtLeast(1).toFloat()
        val canvasRatio = canvasW.toFloat() / canvasH.toFloat()
        val dstW: Int
        val dstH: Int
        if (bmpRatio > canvasRatio) {
            dstW = canvasW
            dstH = (canvasW / bmpRatio).roundToInt().coerceAtLeast(1)
        } else {
            dstH = canvasH
            dstW = (canvasH * bmpRatio).roundToInt().coerceAtLeast(1)
        }
        val offsetX = (canvasW - dstW) / 2
        val offsetY = (canvasH - dstH) / 2
        drawImage(
            image = bitmap,
            dstOffset = IntOffset(offsetX, offsetY),
            dstSize = IntSize(dstW, dstH),
            filterQuality = FilterQuality.Medium,
            colorFilter = colorFilter
        )
    } else {
        drawImage(
            image = bitmap,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(canvasW, canvasH),
            filterQuality = FilterQuality.Medium,
            colorFilter = colorFilter
        )
    }
}

/**
 * 1:1 Partitioned 3-Region Opaque Cylindrical Flipbook Engine (`CylindricalFlipbookCanvas`)
 * Fixes:
 * 1. PDF pages always keep a natural white paper background (`#FFFFFF`) and warm paper back-curl (`#F2EFE9`)
 *    even when the app UI theme is Black or AMOLED.
 * 2. Zero flicker/blink when completing a page turn: synchronously swaps internal bitmaps before
 *    snapping progressAnim to 0f, ensuring zero blink!
 */
@Composable
fun CylindricalFlipbookCanvas(
    currentPageBitmap: ImageBitmap,
    nextPageBitmap: ImageBitmap?,
    prevPageBitmap: ImageBitmap?,
    themeBgColor: Color,
    backPaperColor: Color,
    isPdfBook: Boolean = false,
    isTwoPageSpread: Boolean = false,
    curPageColorFilter: ColorFilter? = null,
    nextPageColorFilter: ColorFilter? = null,
    prevPageColorFilter: ColorFilter? = null,
    pdfTintedPaperColor: Color? = null,
    externalTriggerDir: Int = 0,
    onExternalTriggerConsumed: () -> Unit = {},
    manualProgress: Float? = null,
    manualCornerYNorm: Float? = null,
    manualTurnDir: Int? = null,
    onPageTurnCommitted: (direction: Int) -> Unit,
    onCenterTap: () -> Unit = {},
    onFlipStateChanged: (Boolean) -> Unit = {},
    enableInternalGestures: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val progressAnim = remember { Animatable(0f) }
    var turnDir by remember { mutableIntStateOf(1) }
    var cornerYNorm by remember { mutableFloatStateOf(0.76f) }
    var isDragging by remember { mutableStateOf(false) }

    val effectiveTurnDir = manualTurnDir ?: turnDir
    val effectiveCornerYNorm = manualCornerYNorm ?: cornerYNorm

    // Internal MutableState bitmap references preventing 1-frame flashes on page completion
    var displayedCurrentBmp by remember { mutableStateOf(currentPageBitmap) }
    var displayedNextBmp by remember { mutableStateOf(nextPageBitmap) }
    var displayedPrevBmp by remember { mutableStateOf(prevPageBitmap) }
    var isWaitingForParentPageSync by remember { mutableStateOf(false) }

    // Internal color filter state synchronized with bitmaps
    var displayedCurrFilter by remember { mutableStateOf(curPageColorFilter) }
    var displayedNextFilter by remember { mutableStateOf(nextPageColorFilter) }
    var displayedPrevFilter by remember { mutableStateOf(prevPageColorFilter) }

    val isFlipActive = (manualProgress != null && manualProgress > 0.001f) || isDragging || progressAnim.value > 0.001f || isWaitingForParentPageSync
    LaunchedEffect(isFlipActive) {
        onFlipStateChanged(isFlipActive)
    }

    // For PDF books with theme tinting, use the tinted paper color; otherwise use white or theme color
    val pagePaperColor = if (isPdfBook && pdfTintedPaperColor != null) pdfTintedPaperColor else if (isPdfBook) Color.White else themeBgColor
    val effectiveBackPaperColor = if (isPdfBook && pdfTintedPaperColor != null) pdfTintedPaperColor else if (isPdfBook) Color(0xFFF2EFE9) else backPaperColor

    val paperBleedFilter = remember {
        val cm = ColorMatrix().apply { setToSaturation(0.35f) }
        ColorFilter.colorMatrix(cm)
    }

    // Sync from parent props: update nextPageBitmap and prevPageBitmap immediately so next spread is never stuck!
    LaunchedEffect(nextPageBitmap) {
        if (nextPageBitmap != null) displayedNextBmp = nextPageBitmap
    }
    LaunchedEffect(prevPageBitmap) {
        if (prevPageBitmap != null) displayedPrevBmp = prevPageBitmap
    }
    LaunchedEffect(currentPageBitmap) {
        // Only update displayedCurrentBmp when idle and when currentPageBitmap is NOT the old previous page:
        if (currentPageBitmap != null && progressAnim.value == 0f && currentPageBitmap !== displayedPrevBmp) {
            displayedCurrentBmp = currentPageBitmap
            isWaitingForParentPageSync = false
        }
    }

    // Sync color filters alongside bitmaps
    LaunchedEffect(curPageColorFilter) {
        displayedCurrFilter = curPageColorFilter
    }
    LaunchedEffect(nextPageColorFilter) {
        if (nextPageColorFilter != null) displayedNextFilter = nextPageColorFilter
    }
    LaunchedEffect(prevPageColorFilter) {
        if (prevPageColorFilter != null) displayedPrevFilter = prevPageColorFilter
    }

    LaunchedEffect(manualProgress) {
        if (manualProgress != null && manualProgress >= 0.999f) {
            val dir = effectiveTurnDir
            if (dir > 0 && (displayedNextBmp ?: nextPageBitmap) != null) {
                displayedPrevBmp = displayedCurrentBmp
                displayedCurrentBmp = (displayedNextBmp ?: nextPageBitmap)!!
            } else if (dir < 0 && (displayedPrevBmp ?: prevPageBitmap) != null) {
                displayedNextBmp = displayedCurrentBmp
                displayedCurrentBmp = (displayedPrevBmp ?: prevPageBitmap)!!
            }
            isWaitingForParentPageSync = true
        }
    }

    suspend fun animateCommitTurn(dir: Int, destBmp: ImageBitmap?, durationMs: Int = 380) {
        progressAnim.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = durationMs,
                easing = CubicBezierEasing(0.18f, 0.88f, 0.24f, 1f)
            )
        )
        val completedDir = dir
        val targetBmp = if (completedDir > 0) (displayedNextBmp ?: destBmp) else (displayedPrevBmp ?: destBmp)
        if (completedDir > 0 && targetBmp != null) {
            displayedPrevBmp = displayedCurrentBmp
            displayedCurrentBmp = targetBmp
        } else if (completedDir < 0 && targetBmp != null) {
            displayedNextBmp = displayedCurrentBmp
            displayedCurrentBmp = targetBmp
        }
        isWaitingForParentPageSync = true
        turnDir = 0
        progressAnim.snapTo(0f)
        onPageTurnCommitted(completedDir)
    }

    LaunchedEffect(externalTriggerDir) {
        if (externalTriggerDir != 0 && !progressAnim.isRunning) {
            val destBmp = if (externalTriggerDir > 0) (displayedNextBmp ?: nextPageBitmap) else (displayedPrevBmp ?: prevPageBitmap)
            if (destBmp != null) {
                turnDir = externalTriggerDir
                cornerYNorm = 0.78f
                progressAnim.snapTo(0.01f)
                animateCommitTurn(externalTriggerDir, destBmp, 380)
            } else {
                onPageTurnCommitted(externalTriggerDir)
            }
            onExternalTriggerConsumed()
        }
    }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (enableInternalGestures) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (progressAnim.isRunning || isWaitingForParentPageSync) return@detectTapGestures
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val relX = offset.x / w
                            val nextB = displayedNextBmp ?: nextPageBitmap
                            val prevB = displayedPrevBmp ?: prevPageBitmap
                            if (relX >= 0.76f && nextB != null) {
                                turnDir = 1
                                cornerYNorm = 0.78f
                                scope.launch {
                                    progressAnim.snapTo(0.01f)
                                    animateCommitTurn(1, nextB, 380)
                                }
                            } else if (relX <= 0.24f && prevB != null) {
                                turnDir = -1
                                cornerYNorm = 0.78f
                                scope.launch {
                                    progressAnim.snapTo(0.01f)
                                    animateCommitTurn(-1, prevB, 380)
                                }
                            } else {
                                onCenterTap()
                            }
                        }
                    }
                } else {
                    Modifier
                }
            )
            .then(
                if (enableInternalGestures) {
                    Modifier.pointerInput(Unit) {
                        val velocityTracker = VelocityTracker()
                        var dragStartX = 0f
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                if (progressAnim.isRunning) {
                                    scope.launch {
                                        progressAnim.snapTo(0f)
                                    }
                                }
                                isWaitingForParentPageSync = false
                                velocityTracker.resetTracking()
                                dragStartX = startOffset.x
                                cornerYNorm = (startOffset.y / size.height.toFloat().coerceAtLeast(1f)).coerceIn(0.05f, 0.95f)
                                isDragging = false
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                velocityTracker.addPosition(change.uptimeMillis, change.position)
                                val w = size.width.toFloat().coerceAtLeast(1f)
                                val h = size.height.toFloat().coerceAtLeast(1f)
                                val totalDx = change.position.x - dragStartX

                                if (!isDragging) {
                                    if (abs(totalDx) < 6f) return@detectDragGestures
                                    val candidateDir = if (totalDx < 0f) 1 else -1
                                    val nextB = displayedNextBmp ?: nextPageBitmap
                                    val prevB = displayedPrevBmp ?: prevPageBitmap
                                    if (candidateDir > 0 && nextB == null) return@detectDragGestures
                                    if (candidateDir < 0 && prevB == null) return@detectDragGestures
                                    turnDir = candidateDir
                                    isDragging = true
                                }

                                val signedDelta = if (turnDir > 0) (dragStartX - change.position.x) else (change.position.x - dragStartX)
                                val liveP = (signedDelta / (w * 0.82f)).coerceIn(0.01f, 0.985f)
                                val liveY = (change.position.y / h).coerceIn(0.05f, 0.95f)
                                cornerYNorm = cornerYNorm * 0.3f + liveY * 0.7f
                                scope.launch { progressAnim.snapTo(liveP) }
                            },
                            onDragEnd = {
                                if (!isDragging) return@detectDragGestures
                                isDragging = false
                                val velX = velocityTracker.calculateVelocity().x
                                val speedInDir = if (turnDir > 0) -velX else velX
                                val curP = progressAnim.value
                                val shouldCommit = curP >= 0.30f || (curP >= 0.05f && speedInDir > 380f)
                                val destBmp = if (turnDir > 0) (displayedNextBmp ?: nextPageBitmap) else (displayedPrevBmp ?: prevPageBitmap)
                                val flingDuration = if (speedInDir > 900f) 220 else if (speedInDir > 500f) 300 else 380

                                scope.launch {
                                    if (shouldCommit && destBmp != null) {
                                        animateCommitTurn(turnDir, destBmp, flingDuration)
                                    } else {
                                        progressAnim.animateTo(
                                            targetValue = 0f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            )
                                        )
                                    }
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                                scope.launch { progressAnim.animateTo(0f) }
                            }
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        val w = size.width
        val h = size.height

        val curBmp = displayedCurrentBmp
        val p = manualProgress?.coerceIn(0f, 1f) ?: progressAnim.value.coerceIn(0f, 1f)
        val pageRect = computePageRect(curBmp, isPdfBook, isTwoPageSpread, w, h)

        if (p <= 0.002f) {
            drawRect(color = themeBgColor, size = size)
            clipRect(pageRect.left, pageRect.top, pageRect.right, pageRect.bottom) {
                drawRect(
                    color = pagePaperColor,
                    topLeft = Offset(pageRect.left, pageRect.top),
                    size = Size(pageRect.width, pageRect.height)
                )
                drawPageBitmapFitted(curBmp, isPdfBook, isTwoPageSpread, displayedCurrFilter)
                if (isTwoPageSpread) {
                    val spineX = (pageRect.left + pageRect.right) / 2f
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.10f),
                                Color.Transparent
                            ),
                            startX = spineX - 24f,
                            endX = spineX + 24f
                        ),
                        topLeft = Offset(spineX - 24f, pageRect.top),
                        size = Size(48f, pageRect.height)
                    )
                }
            }
            return@Canvas
        }

        drawRect(color = themeBgColor, size = size)

        val destBitmap = if (effectiveTurnDir > 0) (displayedNextBmp ?: nextPageBitmap) else (displayedPrevBmp ?: prevPageBitmap)
        val bell = sin(p * PI.toFloat())

        // Strict Page-Frame Clipping: page turn effect is strictly bounded to pageRect
        clipRect(pageRect.left, pageRect.top, pageRect.right, pageRect.bottom) {
            if (isTwoPageSpread) {
                val spineX = (pageRect.left + pageRect.right) / 2f
                val halfW = pageRect.width * 0.5f
                val pageH = pageRect.height

                if (effectiveTurnDir > 0) {
                    // Forward Turn (Right leaf turns from right edge across spine to left)
                    val foldX = pageRect.left + pageRect.width * (1f - 0.5f * p)
                    val leadX = pageRect.left + pageRect.width * (1f - p)

                    // 1. Region 1 (pageRect.left .. leadX): Draw currentPageBitmap (full spread)
                    val r1Right = min(pageRect.right, leadX)
                    if (r1Right > pageRect.left) {
                        clipRect(pageRect.left, pageRect.top, r1Right, pageRect.bottom) {
                            drawImage(
                                image = curBmp,
                                dstOffset = IntOffset(pageRect.left.roundToInt(), pageRect.top.roundToInt()),
                                dstSize = IntSize(pageRect.width.roundToInt(), pageH.roundToInt())
                            )
                        }
                    }

                    // Soft left cast shadow on Region 1: (leadX - 14.dp .. leadX)
                    val shadowLeft = max(pageRect.left, leadX - 14.dp.toPx())
                    if (leadX > shadowLeft) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f * bell)),
                                startX = shadowLeft,
                                endX = leadX
                            ),
                            topLeft = Offset(shadowLeft, pageRect.top),
                            size = Size(leadX - shadowLeft, pageH)
                        )
                    }

                    // 2. Region 2 (foldX .. pageRect.right): Draw nextPageBitmap (full spread)
                    val r2Left = max(pageRect.left, foldX)
                    if (pageRect.right > r2Left && destBitmap != null) {
                        clipRect(r2Left, pageRect.top, pageRect.right, pageRect.bottom) {
                            drawImage(
                                image = destBitmap,
                                dstOffset = IntOffset(pageRect.left.roundToInt(), pageRect.top.roundToInt()),
                                dstSize = IntSize(pageRect.width.roundToInt(), pageH.roundToInt())
                            )
                            // Soft crease shadow (width = 16.dp, Black(alpha = 0.14f) -> Transparent) from foldX to foldX + 16.dp
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.14f), Color.Transparent),
                                    startX = r2Left,
                                    endX = min(pageRect.right, r2Left + 16.dp.toPx())
                                ),
                                topLeft = Offset(r2Left, pageRect.top),
                                size = Size(min(pageRect.right - r2Left, 16.dp.toPx()), pageH)
                            )
                        }
                    }

                    // Static spine shadow on underlying spread
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            startX = spineX - 22f,
                            endX = spineX + 22f
                        ),
                        topLeft = Offset(spineX - 22f, pageRect.top),
                        size = Size(44f, pageH)
                    )

                    // 3. Region 3 (leadX .. foldX — The Turning Sheet Back / New Left Page):
                    val leafLeft = max(pageRect.left, leadX)
                    val leafRight = min(pageRect.right, foldX)
                    if (leafRight > leafLeft) {
                        clipRect(leafLeft, pageRect.top, leafRight, pageRect.bottom) {
                            drawRect(
                                color = if (isPdfBook) Color(0xFFFAF8F4) else effectiveBackPaperColor,
                                topLeft = Offset(leafLeft, pageRect.top),
                                size = Size(leafRight - leafLeft, pageH),
                                alpha = 1f
                            )
                            if (destBitmap != null) {
                                drawImage(
                                    image = destBitmap,
                                    srcOffset = IntOffset(0, 0),
                                    srcSize = IntSize(destBitmap.width / 2, destBitmap.height),
                                    dstOffset = IntOffset(leadX.roundToInt(), pageRect.top.roundToInt()),
                                    dstSize = IntSize(halfW.roundToInt(), pageH.roundToInt()),
                                    alpha = 1f
                                )
                            }
                            // Subtle 3D paper-curl gradient across [leadX, foldX]
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.06f)),
                                    startX = leafLeft,
                                    endX = leafRight
                                ),
                                topLeft = Offset(leafLeft, pageRect.top),
                                size = Size(leafRight - leafLeft, pageH)
                            )
                        }
                        // Crisp 1.dp paper edge line at leadX
                        if (leadX in pageRect.left..pageRect.right) {
                            drawLine(
                                color = Color.Black.copy(alpha = 0.15f),
                                start = Offset(leadX, pageRect.top),
                                end = Offset(leadX, pageRect.bottom),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        // Crisp 1.dp fold crease line at foldX
                        if (foldX in pageRect.left..pageRect.right) {
                            drawLine(
                                color = Color.Black.copy(alpha = 0.12f),
                                start = Offset(foldX, pageRect.top),
                                end = Offset(foldX, pageRect.bottom),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                } else {
                    // Backward Turn (Left leaf turns from left edge across spine to right)
                    val foldX = pageRect.left + pageRect.width * (0.5f * p)
                    val leadX = pageRect.left + pageRect.width * p

                    // 1. Region 1 (leadX .. pageRect.right): Draw currentPageBitmap (full spread)
                    val r1Left = max(pageRect.left, leadX)
                    if (pageRect.right > r1Left) {
                        clipRect(r1Left, pageRect.top, pageRect.right, pageRect.bottom) {
                            drawImage(
                                image = curBmp,
                                dstOffset = IntOffset(pageRect.left.roundToInt(), pageRect.top.roundToInt()),
                                dstSize = IntSize(pageRect.width.roundToInt(), pageH.roundToInt())
                            )
                        }
                    }

                    // Soft right cast shadow on Region 1: (leadX .. leadX + 14.dp)
                    val shadowRight = min(pageRect.right, leadX + 14.dp.toPx())
                    if (shadowRight > leadX) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.12f * bell), Color.Transparent),
                                startX = leadX,
                                endX = shadowRight
                            ),
                            topLeft = Offset(leadX, pageRect.top),
                            size = Size(shadowRight - leadX, pageH)
                        )
                    }

                    // 2. Region 2 (pageRect.left .. foldX): Draw prevPageBitmap (full spread)
                    val r2Right = min(pageRect.right, foldX)
                    if (r2Right > pageRect.left && destBitmap != null) {
                        clipRect(pageRect.left, pageRect.top, r2Right, pageRect.bottom) {
                            drawImage(
                                image = destBitmap,
                                dstOffset = IntOffset(pageRect.left.roundToInt(), pageRect.top.roundToInt()),
                                dstSize = IntSize(pageRect.width.roundToInt(), pageH.roundToInt())
                            )
                            // Soft crease shadow to the left of foldX
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.14f)),
                                    startX = max(pageRect.left, r2Right - 16.dp.toPx()),
                                    endX = r2Right
                                ),
                                topLeft = Offset(max(pageRect.left, r2Right - 16.dp.toPx()), pageRect.top),
                                size = Size(min(r2Right - pageRect.left, 16.dp.toPx()), pageH)
                            )
                        }
                    }

                    // Static spine shadow on underlying spread
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.22f),
                                Color.White.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            startX = spineX - 22f,
                            endX = spineX + 22f
                        ),
                        topLeft = Offset(spineX - 22f, pageRect.top),
                        size = Size(44f, pageH)
                    )

                    // 3. Region 3 (foldX .. leadX — The Turning Sheet Back / New Right Page):
                    val leafLeft = max(pageRect.left, foldX)
                    val leafRight = min(pageRect.right, leadX)
                    if (leafRight > leafLeft) {
                        clipRect(leafLeft, pageRect.top, leafRight, pageRect.bottom) {
                            drawRect(
                                color = if (isPdfBook) Color(0xFFFAF8F4) else effectiveBackPaperColor,
                                topLeft = Offset(leafLeft, pageRect.top),
                                size = Size(leafRight - leafLeft, pageH),
                                alpha = 1f
                            )
                            if (destBitmap != null) {
                                drawImage(
                                    image = destBitmap,
                                    srcOffset = IntOffset(destBitmap.width / 2, 0),
                                    srcSize = IntSize(destBitmap.width / 2, destBitmap.height),
                                    dstOffset = IntOffset((leadX - halfW).roundToInt(), pageRect.top.roundToInt()),
                                    dstSize = IntSize(halfW.roundToInt(), pageH.roundToInt()),
                                    alpha = 1f
                                )
                            }
                            // Subtle 3D paper-curl gradient across [foldX, leadX]
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.06f), Color.Transparent),
                                    startX = leafLeft,
                                    endX = leafRight
                                ),
                                topLeft = Offset(leafLeft, pageRect.top),
                                size = Size(leafRight - leafLeft, pageH)
                            )
                        }
                        // Crisp 1.dp paper edge line at leadX
                        if (leadX in pageRect.left..pageRect.right) {
                            drawLine(
                                color = Color.Black.copy(alpha = 0.15f),
                                start = Offset(leadX, pageRect.top),
                                end = Offset(leadX, pageRect.bottom),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                        // Crisp 1.dp fold crease line at foldX
                        if (foldX in pageRect.left..pageRect.right) {
                            drawLine(
                                color = Color.Black.copy(alpha = 0.12f),
                                start = Offset(foldX, pageRect.top),
                                end = Offset(foldX, pageRect.bottom),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                }
            } else {
                // Single-Page Mode: Mathematically exact 2D Fold Reflection & Congruent Flap Polygon
                val slantPx = ((effectiveCornerYNorm - 0.5f).coerceIn(-0.45f, 0.45f) * 0.14f * bell * pageRect.width)

                if (effectiveTurnDir > 0) {
                    // Turn Next: foldMidX moves from pageRect.right to pageRect.left
                    val foldMidX = pageRect.left + pageRect.width * (1f - p)
                    val foldTopX = (foldMidX - slantPx).coerceIn(pageRect.left + 4f, pageRect.right - 4f)
                    val foldBotX = (foldMidX + slantPx).coerceIn(pageRect.left + 4f, pageRect.right - 4f)
                    val fTop = Offset(foldTopX, pageRect.top)
                    val fBot = Offset(foldBotX, pageRect.bottom)

                    val rTop = reflectPointAcrossLine(Offset(pageRect.right, pageRect.top), fTop, fBot)
                    val rBot = reflectPointAcrossLine(Offset(pageRect.right, pageRect.bottom), fTop, fBot)

                    val dyR = (rBot.y - rTop.y).let { if (kotlin.math.abs(it) < 1e-3f) 1e-3f else it }
                    val dxR = rBot.x - rTop.x
                    val leadTopX = rTop.x + (pageRect.top - rTop.y) * (dxR / dyR)
                    val leadBotX = rTop.x + (pageRect.bottom - rTop.y) * (dxR / dyR)
                    val leadTop = Offset(leadTopX, pageRect.top)
                    val leadBot = Offset(leadBotX, pageRect.bottom)

                    val dxFold = foldBotX - foldTopX
                    val dyFold = pageRect.height.coerceAtLeast(1f)
                    val thetaRad = kotlin.math.atan2(dxFold, dyFold)
                    val thetaDeg = Math.toDegrees(thetaRad.toDouble()).toFloat()

                    // 1. Right of crease line (nextPoly): uncovered next page
                    val nextPoly = Path().apply {
                        moveTo(foldTopX, pageRect.top)
                        lineTo(pageRect.right, pageRect.top)
                        lineTo(pageRect.right, pageRect.bottom)
                        lineTo(foldBotX, pageRect.bottom)
                        close()
                    }
                    clipPath(nextPoly) {
                        drawRect(color = pagePaperColor, topLeft = Offset(pageRect.left, pageRect.top), size = Size(pageRect.width, pageRect.height))
                        if (destBitmap != null) {
                            drawPageBitmapFitted(destBitmap, isPdfBook, false)
                        }
                    }

                    // 2. Left of crease line (currPoly): unpeeled current page + shadow + folded paper flap (backPoly)
                    val currPoly = Path().apply {
                        moveTo(pageRect.left, pageRect.top)
                        lineTo(foldTopX, pageRect.top)
                        lineTo(foldBotX, pageRect.bottom)
                        lineTo(pageRect.left, pageRect.bottom)
                        close()
                    }
                    val backPoly = Path().apply {
                        moveTo(foldTopX, pageRect.top)
                        lineTo(leadTopX, pageRect.top)
                        lineTo(leadBotX, pageRect.bottom)
                        lineTo(foldBotX, pageRect.bottom)
                        close()
                    }

                    clipPath(currPoly) {
                        // Draw flat underlying current page up to crease line F_top -> F_bot
                        drawRect(color = pagePaperColor, topLeft = Offset(pageRect.left, pageRect.top), size = Size(pageRect.width, pageRect.height))
                        drawPageBitmapFitted(curBmp, isPdfBook, false)

                        // Drop shadow strictly along left leading edge leadTop -> leadBot of backPoly onto currPoly
                        val rdx = leadBot.x - leadTop.x
                        val rdy = leadBot.y - leadTop.y
                        val rLen = kotlin.math.hypot(rdx, rdy).coerceAtLeast(1f)
                        val ux = rdx / rLen
                        val uy = rdy / rLen
                        // Normal pointing to the left (into currPoly)
                        val nx = -uy
                        val ny = ux
                        val shadowW = 12.dp.toPx() * bell
                        if (shadowW > 0.5f) {
                            val ext = 60f
                            val pA = Offset(leadTop.x - ux * ext, leadTop.y - uy * ext)
                            val pB = Offset(leadBot.x + ux * ext, leadBot.y + uy * ext)
                            val pC = Offset(pB.x + nx * shadowW, pB.y + ny * shadowW)
                            val pD = Offset(pA.x + nx * shadowW, pA.y + ny * shadowW)
                            val shadowPath = Path().apply {
                                moveTo(pA.x, pA.y)
                                lineTo(pB.x, pB.y)
                                lineTo(pC.x, pC.y)
                                lineTo(pD.x, pD.y)
                                close()
                            }
                            val midEdge = Offset((leadTop.x + leadBot.x) * 0.5f, (leadTop.y + leadBot.y) * 0.5f)
                            val midOuter = Offset(midEdge.x + nx * shadowW, midEdge.y + ny * shadowW)
                            drawPath(
                                path = shadowPath,
                                brush = Brush.linearGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f * bell)),
                                    start = midOuter,
                                    end = midEdge
                                )
                            )
                        }

                        // Draw folded paper flap (backPoly) clipped to currPoly (intersection)
                        clipPath(backPoly) {
                            drawPath(backPoly, color = if (isPdfBook) Color(0xFFFAF8F4) else effectiveBackPaperColor, alpha = 1f)
                            drawIntoCanvas { canvas ->
                                val nc = canvas.nativeCanvas
                                nc.save()
                                nc.translate(fTop.x, fTop.y)
                                nc.rotate(-2f * thetaDeg)
                                nc.scale(-1f, 1f)
                                nc.translate(-fTop.x, -fTop.y)

                                val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                                    alpha = (0.09f * 255).roundToInt()
                                }
                                val dstRect = android.graphics.RectF(pageRect.left, pageRect.top, pageRect.right, pageRect.bottom)
                                nc.drawBitmap(curBmp.asAndroidBitmap(), null, dstRect, paint)
                                nc.restore()
                            }
                        }

                        // Hairline along outer edge leadTop -> leadBot
                        drawLine(
                            color = Color.Black.copy(alpha = 0.07f),
                            start = leadTop,
                            end = leadBot,
                            strokeWidth = 0.75.dp.toPx()
                        )
                    }

                    // Crease line along F_top -> F_bot
                    drawLine(
                        color = Color.Black.copy(alpha = 0.07f),
                        start = fTop,
                        end = fBot,
                        strokeWidth = 0.75.dp.toPx()
                    )
                } else {
                    // Turn Prev: foldMidX moves from pageRect.left to pageRect.right
                    val foldMidX = pageRect.left + pageRect.width * p
                    val foldTopX = (foldMidX + slantPx).coerceIn(pageRect.left + 4f, pageRect.right - 4f)
                    val foldBotX = (foldMidX - slantPx).coerceIn(pageRect.left + 4f, pageRect.right - 4f)
                    val fTop = Offset(foldTopX, pageRect.top)
                    val fBot = Offset(foldBotX, pageRect.bottom)

                    val rTop = reflectPointAcrossLine(Offset(pageRect.left, pageRect.top), fTop, fBot)
                    val rBot = reflectPointAcrossLine(Offset(pageRect.left, pageRect.bottom), fTop, fBot)

                    val dyR = (rBot.y - rTop.y).let { if (kotlin.math.abs(it) < 1e-3f) 1e-3f else it }
                    val dxR = rBot.x - rTop.x
                    val leadTopX = rTop.x + (pageRect.top - rTop.y) * (dxR / dyR)
                    val leadBotX = rTop.x + (pageRect.bottom - rTop.y) * (dxR / dyR)
                    val leadTop = Offset(leadTopX, pageRect.top)
                    val leadBot = Offset(leadBotX, pageRect.bottom)

                    val dxFold = foldBotX - foldTopX
                    val dyFold = pageRect.height.coerceAtLeast(1f)
                    val thetaRad = kotlin.math.atan2(dxFold, dyFold)
                    val thetaDeg = Math.toDegrees(thetaRad.toDouble()).toFloat()

                    // 1. Left of crease line (prevPoly): uncovered prev page
                    val prevPoly = Path().apply {
                        moveTo(pageRect.left, pageRect.top)
                        lineTo(foldTopX, pageRect.top)
                        lineTo(foldBotX, pageRect.bottom)
                        lineTo(pageRect.left, pageRect.bottom)
                        close()
                    }
                    clipPath(prevPoly) {
                        drawRect(color = pagePaperColor, topLeft = Offset(pageRect.left, pageRect.top), size = Size(pageRect.width, pageRect.height))
                        if (destBitmap != null) {
                            drawPageBitmapFitted(destBitmap, isPdfBook, false)
                        }
                    }

                    // 2. Right of crease line (currPoly): unpeeled current page + shadow + folded paper flap (backPoly)
                    val currPoly = Path().apply {
                        moveTo(foldTopX, pageRect.top)
                        lineTo(pageRect.right, pageRect.top)
                        lineTo(pageRect.right, pageRect.bottom)
                        lineTo(foldBotX, pageRect.bottom)
                        close()
                    }
                    val backPoly = Path().apply {
                        moveTo(foldTopX, pageRect.top)
                        lineTo(leadTopX, pageRect.top)
                        lineTo(leadBotX, pageRect.bottom)
                        lineTo(foldBotX, pageRect.bottom)
                        close()
                    }

                    clipPath(currPoly) {
                        // Draw flat underlying current page up to crease line F_top -> F_bot
                        drawRect(color = pagePaperColor, topLeft = Offset(pageRect.left, pageRect.top), size = Size(pageRect.width, pageRect.height))
                        drawPageBitmapFitted(curBmp, isPdfBook, false)

                        // Drop shadow strictly along right leading edge leadTop -> leadBot of backPoly onto currPoly
                        val rdx = leadBot.x - leadTop.x
                        val rdy = leadBot.y - leadTop.y
                        val rLen = kotlin.math.hypot(rdx, rdy).coerceAtLeast(1f)
                        val ux = rdx / rLen
                        val uy = rdy / rLen
                        // Normal pointing to the right (into currPoly)
                        val nx = uy
                        val ny = -ux
                        val shadowW = 12.dp.toPx() * bell
                        if (shadowW > 0.5f) {
                            val ext = 60f
                            val pA = Offset(leadTop.x - ux * ext, leadTop.y - uy * ext)
                            val pB = Offset(leadBot.x + ux * ext, leadBot.y + uy * ext)
                            val pC = Offset(pB.x + nx * shadowW, pB.y + ny * shadowW)
                            val pD = Offset(pA.x + nx * shadowW, pA.y + ny * shadowW)
                            val shadowPath = Path().apply {
                                moveTo(pA.x, pA.y)
                                lineTo(pB.x, pB.y)
                                lineTo(pC.x, pC.y)
                                lineTo(pD.x, pD.y)
                                close()
                            }
                            val midEdge = Offset((leadTop.x + leadBot.x) * 0.5f, (leadTop.y + leadBot.y) * 0.5f)
                            val midOuter = Offset(midEdge.x + nx * shadowW, midEdge.y + ny * shadowW)
                            drawPath(
                                path = shadowPath,
                                brush = Brush.linearGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f * bell)),
                                    start = midOuter,
                                    end = midEdge
                                )
                            )
                        }

                        // Draw folded paper flap (backPoly) clipped to currPoly (intersection)
                        clipPath(backPoly) {
                            drawPath(backPoly, color = if (isPdfBook) Color(0xFFFAF8F4) else effectiveBackPaperColor, alpha = 1f)
                            drawIntoCanvas { canvas ->
                                val nc = canvas.nativeCanvas
                                nc.save()
                                nc.translate(fTop.x, fTop.y)
                                nc.rotate(-2f * thetaDeg)
                                nc.scale(-1f, 1f)
                                nc.translate(-fTop.x, -fTop.y)

                                val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                                    alpha = (0.09f * 255).roundToInt()
                                }
                                val dstRect = android.graphics.RectF(pageRect.left, pageRect.top, pageRect.right, pageRect.bottom)
                                nc.drawBitmap(curBmp.asAndroidBitmap(), null, dstRect, paint)
                                nc.restore()
                            }
                        }

                        // Hairline along outer edge leadTop -> leadBot
                        drawLine(
                            color = Color.Black.copy(alpha = 0.07f),
                            start = leadTop,
                            end = leadBot,
                            strokeWidth = 0.75.dp.toPx()
                        )
                    }

                    // Crease line along F_top -> F_bot
                    drawLine(
                        color = Color.Black.copy(alpha = 0.07f),
                        start = fTop,
                        end = fBot,
                        strokeWidth = 0.75.dp.toPx()
                    )
                }
            }
        }
    }
}
