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

    fun getSystemTypeface(fontFamily: String, fontWeight: Int, context: Context? = null): Typeface {
        val key = "$fontFamily:$fontWeight"
        typefaceCache[key]?.let { return it }

        val style = if (fontWeight >= 600) Typeface.BOLD else Typeface.NORMAL
        val assetPath = when (fontFamily) {
            "Literata" -> "fonts/literata.ttf"
            "EB Garamond" -> "fonts/eb_garamond.ttf"
            "Source Serif 4" -> "fonts/source_serif.ttf"
            "Plus Jakarta Sans" -> "fonts/plus_jakarta_sans.ttf"
            "Inter" -> "fonts/inter.ttf"
            "JetBrains Mono" -> "fonts/jetbrains_mono.ttf"
            else -> null
        }

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
        chapterTitle: String,
        pageText: String,
        pageNumberLabel: String,
        bgColor: Color,
        textColor: Color,
        mutedColor: Color,
        fontSizePx: Float,
        lineSpacingMultiplier: Float,
        marginPx: Int,
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
        chapterTitle: String,
        pageText: String,
        pageNumberLabel: String,
        bgColor: Color,
        textColor: Color,
        mutedColor: Color,
        fontSizePx: Float,
        lineSpacingMultiplier: Float,
        marginPx: Int,
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
        val w = widthPx.coerceIn(320, 1440)
        val h = heightPx.coerceIn(420, 2400)
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
                    val src = Rect(0, 0, coverBmp.width, coverBmp.height)
                    val dst = Rect(0, 0, w, h)
                    canvas.drawBitmap(coverBmp, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                    return bmp.asImageBitmap()
                }
            }
        }

        val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mutedColor.toArgb()
            textSize = (fontSizePx * 0.56f).coerceAtLeast(22f)
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = textColor.toArgb()
            textSize = fontSizePx.coerceIn(24f, 76f)
            typeface = getSystemTypeface(fontFamily, fontWeight)
            letterSpacing = letterSpacingEm
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && wordSpacingPx > 0) {
                wordSpacing = wordSpacingPx.toFloat()
            }
        }

        val pad = marginPx.coerceIn(24, w / 4)
        val contentW = (w - pad * 2).coerceAtLeast(120)

        canvas.drawText(chapterTitle.take(42).uppercase(), pad.toFloat(), pad + 28f, headerPaint)

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
        val bodyStartY = pad + 64f
        val footerY = (h - pad / 2).toFloat()
        val availableBodyH = (footerY - 20f - bodyStartY).coerceAtLeast(100f)

        var fittedLines = bodyLayout.lineCount
        for (line in 0 until bodyLayout.lineCount) {
            if (bodyLayout.getLineBottom(line) > availableBodyH) {
                fittedLines = line
                break
            }
        }

        canvas.save()
        canvas.translate(pad.toFloat(), bodyStartY)
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

        canvas.drawText(pageNumberLabel, pad.toFloat(), footerY, headerPaint)

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
                        val src = Rect(0, 0, coverBmp.width, coverBmp.height)
                        val dst = Rect(offsetX, 0, offsetX + halfW, h)
                        canvas.drawBitmap(coverBmp, src, dst, Paint(Paint.FILTER_BITMAP_FLAG))
                        return
                    }
                }
            }
            val pad = 36
            val leafW = (halfW - pad * 2).coerceAtLeast(100)
            val headerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = mutedColor.toArgb()
                textSize = 22f
                typeface = Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL)
            }
            val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = textColor.toArgb()
                textSize = (fontSizePx * 0.84f).coerceIn(20f, 64f)
                typeface = getSystemTypeface(fontFamily, fontWeight)
                letterSpacing = letterSpacingEm
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && wordSpacingPx > 0) {
                    wordSpacing = wordSpacingPx.toFloat()
                }
            }

            canvas.drawText(title.take(32).uppercase(), (offsetX + pad).toFloat(), 40f, headerPaint)
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

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && textAlign.equals("justify", ignoreCase = true)) {
                builder.setJustificationMode(LineBreaker.JUSTIFICATION_MODE_INTER_WORD)
            }

            val fullLayout = builder.build()
            val availableBodyH = (h - 24 - 16 - 68).toFloat()

            var fittedLines = fullLayout.lineCount
            for (line in 0 until fullLayout.lineCount) {
                if (fullLayout.getLineBottom(line) > availableBodyH) {
                    fittedLines = line
                    break
                }
            }

            canvas.save()
            canvas.translate((offsetX + pad).toFloat(), 68f)
            canvas.clipRect(0f, 0f, leafW.toFloat(), availableBodyH)

            if (fittedLines in 1 until fullLayout.lineCount) {
                val endOffset = fullLayout.getLineEnd(fittedLines - 1)
                val subSpannable = spannable.subSequence(0, endOffset)
                val fittedLayout = StaticLayout.Builder
                    .obtain(subSpannable, 0, subSpannable.length, bodyPaint, leafW)
                    .setAlignment(align)
                    .setLineSpacing(0f, lineSpacingMultiplier.coerceIn(1.1f, 2.5f))
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

            canvas.drawText(pageNum, (offsetX + pad).toFloat(), (h - 24).toFloat(), headerPaint)
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
    isTwoPageSpread: Boolean
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
            filterQuality = FilterQuality.Medium
        )
    } else {
        drawImage(
            image = bitmap,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(canvasW, canvasH),
            filterQuality = FilterQuality.Medium
        )
    }
}

/**
 * 1:1 Partitioned 3-Region Opaque Cylindrical Flipbook Engine (`CylindricalFlipbookCanvas`)
 * Fixes:
 * 1. PDF pages always keep a natural white paper background (`#FFFFFF`) and warm paper back-curl (`#F2EFE9`)
 *    even when the app UI theme is Black or AMOLED.
 * 2. Zero flicker/blink when completing a page turn: holds `committedTargetBitmap` while waiting for
 *    `currentPageBitmap` to update, and resets `progressAnim` to `0f` in the exact frame `currentPageBitmap` updates!
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
    externalTriggerDir: Int = 0,
    onExternalTriggerConsumed: () -> Unit = {},
    onPageTurnCommitted: (direction: Int) -> Unit,
    onCenterTap: () -> Unit = {},
    onFlipStateChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val progressAnim = remember { Animatable(0f) }
    var turnDir by remember { mutableIntStateOf(1) }
    var cornerYNorm by remember { mutableFloatStateOf(0.76f) }
    var isDragging by remember { mutableStateOf(false) }

    // Holds the destination bitmap at the end of a committed turn until currentPageBitmap updates,
    // eliminating the 1-frame flash back to the previous page!
    var pendingCommittedBitmap by remember { mutableStateOf<ImageBitmap?>(null) }

    val isFlipActive = isDragging || progressAnim.value > 0.001f || pendingCommittedBitmap != null
    LaunchedEffect(isFlipActive) {
        onFlipStateChanged(isFlipActive)
    }

    // For PDF books, the paper surface is always crisp white (#FFFFFF) and the curling paper back is warm white (#F2EFE9),
    // never black paper. Only EPUB/TXT adapts paper color to the theme!
    val effectivePageBgColor = if (isPdfBook) Color.White else themeBgColor
    val effectiveBackPaperColor = if (isPdfBook) Color(0xFFF2EFE9) else backPaperColor

    val paperBleedFilter = remember {
        val cm = ColorMatrix().apply { setToSaturation(0.35f) }
        ColorFilter.colorMatrix(cm)
    }

    // As soon as currentPageBitmap updates after a page turn commit, clear pendingCommittedBitmap and reset progressAnim to 0f atomically
    LaunchedEffect(currentPageBitmap) {
        if (pendingCommittedBitmap != null) {
            pendingCommittedBitmap = null
            progressAnim.snapTo(0f)
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
        pendingCommittedBitmap = destBmp
        onPageTurnCommitted(dir)
    }

    LaunchedEffect(externalTriggerDir) {
        if (externalTriggerDir != 0 && !progressAnim.isRunning) {
            val destBmp = if (externalTriggerDir > 0) nextPageBitmap else prevPageBitmap
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
            .pointerInput(currentPageBitmap, nextPageBitmap, prevPageBitmap) {
                detectTapGestures { offset ->
                    if (progressAnim.isRunning || pendingCommittedBitmap != null) return@detectTapGestures
                    val w = size.width.toFloat().coerceAtLeast(1f)
                    val relX = offset.x / w
                    if (relX >= 0.76f && nextPageBitmap != null) {
                        turnDir = 1
                        cornerYNorm = 0.78f
                        scope.launch {
                            progressAnim.snapTo(0.01f)
                            animateCommitTurn(1, nextPageBitmap, 380)
                        }
                    } else if (relX <= 0.24f && prevPageBitmap != null) {
                        turnDir = -1
                        cornerYNorm = 0.78f
                        scope.launch {
                            progressAnim.snapTo(0.01f)
                            animateCommitTurn(-1, prevPageBitmap, 380)
                        }
                    } else {
                        onCenterTap()
                    }
                }
            }
            .pointerInput(currentPageBitmap, nextPageBitmap, prevPageBitmap) {
                val velocityTracker = VelocityTracker()
                var dragStartX = 0f
                detectDragGestures(
                    onDragStart = { startOffset ->
                        if (progressAnim.isRunning) {
                            scope.launch {
                                progressAnim.snapTo(0f)
                            }
                        }
                        pendingCommittedBitmap = null
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
                            if (candidateDir > 0 && nextPageBitmap == null) return@detectDragGestures
                            if (candidateDir < 0 && prevPageBitmap == null) return@detectDragGestures
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
                        val destBmp = if (turnDir > 0) nextPageBitmap else prevPageBitmap
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
    ) {
        val w = size.width
        val h = size.height

        // If a page turn just committed and we are waiting for currentPageBitmap to swap, draw the committed bitmap seamlessly!
        val heldBitmap = pendingCommittedBitmap
        if (heldBitmap != null) {
            val pageRect = computePageRect(heldBitmap, isPdfBook, isTwoPageSpread, w, h)
            drawRect(color = effectivePageBgColor, size = size)
            clipRect(pageRect.left, pageRect.top, pageRect.right, pageRect.bottom) {
                drawPageBitmapFitted(heldBitmap, isPdfBook, isTwoPageSpread)
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

        val p = progressAnim.value.coerceIn(0f, 1f)
        val pageRect = computePageRect(currentPageBitmap, isPdfBook, isTwoPageSpread, w, h)

        if (p <= 0.002f) {
            drawRect(color = effectivePageBgColor, size = size)
            clipRect(pageRect.left, pageRect.top, pageRect.right, pageRect.bottom) {
                drawPageBitmapFitted(currentPageBitmap, isPdfBook, isTwoPageSpread)
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

        drawRect(color = effectivePageBgColor, size = size)

        val destBitmap = if (turnDir > 0) nextPageBitmap else prevPageBitmap
        val bell = sin(p * PI.toFloat())

        // Strict Page-Frame Clipping: page turn effect is strictly bounded to pageRect
        clipRect(pageRect.left, pageRect.top, pageRect.right, pageRect.bottom) {
            if (isTwoPageSpread) {
                val spineX = (pageRect.left + pageRect.right) / 2f
                val halfW = pageRect.width * 0.5f
                val pageH = pageRect.height

                if (turnDir > 0) {
                    // Forward Turn (Right leaf turns from right edge across spine to left)
                    val foldX = pageRect.left + pageRect.width * (1f - 0.5f * p)
                    val leadX = pageRect.left + pageRect.width * (1f - p)

                    // 1. Region 1 (pageRect.left .. leadX): Draw currentPageBitmap (full spread)
                    val r1Right = min(pageRect.right, leadX)
                    if (r1Right > pageRect.left) {
                        clipRect(pageRect.left, pageRect.top, r1Right, pageRect.bottom) {
                            drawPageBitmapFitted(currentPageBitmap, isPdfBook, true)
                        }
                    }

                    // 2. Region 2 (foldX .. pageRect.right): Draw nextPageBitmap (full spread)
                    val r2Left = max(pageRect.left, foldX)
                    if (pageRect.right > r2Left) {
                        clipRect(r2Left, pageRect.top, pageRect.right, pageRect.bottom) {
                            if (destBitmap != null) {
                                drawPageBitmapFitted(destBitmap, isPdfBook, true)
                            }
                            // Soft crease shadow to the right of foldX
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.20f * bell), Color.Transparent),
                                    startX = r2Left,
                                    endX = min(pageRect.right, r2Left + 20.dp.toPx())
                                ),
                                topLeft = Offset(r2Left, pageRect.top),
                                size = Size(min(pageRect.right - r2Left, 20.dp.toPx()), pageH)
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

                    // 3. Region 3 (leadX .. foldX): Turning Leaf with 100% solid opaque paper fill
                    val leafLeft = max(pageRect.left, leadX)
                    val leafRight = min(pageRect.right, foldX)
                    if (leafRight > leafLeft) {
                        clipRect(leafLeft, pageRect.top, leafRight, pageRect.bottom) {
                            drawRect(
                                color = if (isPdfBook) Color(0xFFFAF8F4) else effectivePageBgColor,
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
                            // Soft planar shadow near foldX
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.16f * bell)),
                                    startX = max(leafLeft, leafRight - 18.dp.toPx()),
                                    endX = leafRight
                                ),
                                topLeft = Offset(max(leafLeft, leafRight - 18.dp.toPx()), pageRect.top),
                                size = Size(min(leafRight - leafLeft, 18.dp.toPx()), pageH)
                            )
                        }
                        // Crisp 1.dp paper edge at leadX
                        if (leadX in pageRect.left..pageRect.right) {
                            drawLine(
                                color = Color.Black.copy(alpha = 0.22f * bell),
                                start = Offset(leadX, pageRect.top),
                                end = Offset(leadX, pageRect.bottom),
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
                            drawPageBitmapFitted(currentPageBitmap, isPdfBook, true)
                        }
                    }

                    // 2. Region 2 (pageRect.left .. foldX): Draw prevPageBitmap (full spread)
                    val r2Right = min(pageRect.right, foldX)
                    if (r2Right > pageRect.left) {
                        clipRect(pageRect.left, pageRect.top, r2Right, pageRect.bottom) {
                            if (destBitmap != null) {
                                drawPageBitmapFitted(destBitmap, isPdfBook, true)
                            }
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.20f * bell)),
                                    startX = max(pageRect.left, r2Right - 20.dp.toPx()),
                                    endX = r2Right
                                ),
                                topLeft = Offset(max(pageRect.left, r2Right - 20.dp.toPx()), pageRect.top),
                                size = Size(min(r2Right - pageRect.left, 20.dp.toPx()), pageH)
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

                    // 3. Region 3 (foldX .. leadX): Turning Leaf with 100% solid opaque paper fill
                    val leafLeft = max(pageRect.left, foldX)
                    val leafRight = min(pageRect.right, leadX)
                    if (leafRight > leafLeft) {
                        clipRect(leafLeft, pageRect.top, leafRight, pageRect.bottom) {
                            drawRect(
                                color = if (isPdfBook) Color(0xFFFAF8F4) else effectivePageBgColor,
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
                            // Soft planar shadow near foldX
                            drawRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.16f * bell), Color.Transparent),
                                    startX = leafLeft,
                                    endX = min(leafRight, leafLeft + 18.dp.toPx())
                                ),
                                topLeft = Offset(leafLeft, pageRect.top),
                                size = Size(min(leafRight - leafLeft, 18.dp.toPx()), pageH)
                            )
                        }
                        // Crisp 1.dp paper edge at leadX
                        if (leadX in pageRect.left..pageRect.right) {
                            drawLine(
                                color = Color.Black.copy(alpha = 0.22f * bell),
                                start = Offset(leadX, pageRect.top),
                                end = Offset(leadX, pageRect.bottom),
                                strokeWidth = 1.dp.toPx()
                            )
                        }
                    }
                }
            } else {
                // Single-Page Mode: Flat Planar Diagonal Crease Page Turn (Exact Flat Paper Conservation Law)
                val slantPx = (cornerYNorm - 0.5f).coerceIn(-0.45f, 0.45f) * 0.18f * bell * pageRect.width

                if (turnDir > 0) {
                    // Turn Next: foldMidX moves from pageRect.right to pageRect.left
                    val foldMidX = pageRect.left + pageRect.width * (1f - p)
                    val foldTopX = (foldMidX - slantPx).coerceIn(pageRect.left, pageRect.right)
                    val foldBotX = (foldMidX + slantPx).coerceIn(pageRect.left, pageRect.right)

                    // Conservation Law: paper crossed fold is w * p, so leadMidX = foldMidX - w * p = w * (1 - 2p)
                    val leadMidX = pageRect.left + pageRect.width * (1f - 2f * p)
                    val leadTopX = (leadMidX - slantPx).coerceIn(pageRect.left, pageRect.right)
                    val leadBotX = (leadMidX + slantPx).coerceIn(pageRect.left, pageRect.right)

                    val dxFold = foldBotX - foldTopX
                    val dyFold = pageRect.height.coerceAtLeast(1f)
                    val thetaRad = kotlin.math.atan2(dxFold, dyFold)
                    val thetaDeg = Math.toDegrees(thetaRad.toDouble()).toFloat()
                    val diagLen = kotlin.math.hypot(dxFold, dyFold) + pageRect.width

                    // Region A (pageRect.left .. max(pageRect.left, leadX)): Unpeeled left polygon
                    val curPath = Path().apply {
                        moveTo(pageRect.left, pageRect.top)
                        lineTo(leadTopX, pageRect.top)
                        lineTo(leadBotX, pageRect.bottom)
                        lineTo(pageRect.left, pageRect.bottom)
                        close()
                    }
                    clipPath(curPath) {
                        drawRect(color = effectivePageBgColor, topLeft = Offset(pageRect.left, pageRect.top), size = Size(pageRect.width, pageRect.height))
                        drawPageBitmapFitted(currentPageBitmap, isPdfBook, false)

                        // Leading-Edge Cast Shadow rotated parallel to lead line
                        withTransform({
                            translate(left = leadTopX, top = pageRect.top)
                            rotate(degrees = -thetaDeg, pivot = Offset.Zero)
                        }) {
                            val castW = 18.dp.toPx() * bell
                            if (castW > 0.5f) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.14f * bell)),
                                        startX = -castW,
                                        endX = 0f
                                    ),
                                    topLeft = Offset(-castW, -pageRect.width * 0.5f),
                                    size = Size(castW, diagLen * 1.5f)
                                )
                            }
                        }
                    }

                    // Region B (foldX .. pageRect.right): Uncovered right polygon of nextPageBitmap
                    val destPath = Path().apply {
                        moveTo(foldTopX, pageRect.top)
                        lineTo(pageRect.right, pageRect.top)
                        lineTo(pageRect.right, pageRect.bottom)
                        lineTo(foldBotX, pageRect.bottom)
                        close()
                    }
                    clipPath(destPath) {
                        drawRect(color = effectivePageBgColor, topLeft = Offset(pageRect.left, pageRect.top), size = Size(pageRect.width, pageRect.height))
                        if (destBitmap != null) {
                            drawPageBitmapFitted(destBitmap, isPdfBook, false)
                        }
                        // Crease shadow rotated parallel to fold line
                        withTransform({
                            translate(left = foldTopX, top = pageRect.top)
                            rotate(degrees = -thetaDeg, pivot = Offset.Zero)
                        }) {
                            val creaseW = 22.dp.toPx() * bell
                            if (creaseW > 0.5f) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Black.copy(alpha = 0.16f * bell), Color.Transparent),
                                        startX = 0f,
                                        endX = creaseW
                                    ),
                                    topLeft = Offset(0f, -pageRect.width * 0.5f),
                                    size = Size(creaseW, diagLen * 1.5f)
                                )
                            }
                        }
                    }

                    // Region C (max(pageRect.left, leadX) .. foldX): Folded back-of-page polygon
                    val flapPath = Path().apply {
                        moveTo(leadTopX, pageRect.top)
                        lineTo(foldTopX, pageRect.top)
                        lineTo(foldBotX, pageRect.bottom)
                        lineTo(leadBotX, pageRect.bottom)
                        close()
                    }
                    clipPath(flapPath) {
                        // 1. FIRST fill 100% solid opaque paper to prevent ANY bleedthrough!
                        drawPath(flapPath, color = if (isPdfBook) Color(0xFFFAF8F4) else effectiveBackPaperColor, alpha = 1f)

                        // 2. True 2D Geometric reflection of currentPageBitmap across tilted crease line
                        withTransform({
                            translate(left = foldTopX, top = pageRect.top)
                            rotate(degrees = -thetaDeg, pivot = Offset.Zero)
                            scale(scaleX = -1f, scaleY = 1f, pivot = Offset.Zero)
                            rotate(degrees = thetaDeg, pivot = Offset.Zero)
                            translate(left = -foldTopX, top = -pageRect.top)
                        }) {
                            drawImage(
                                image = currentPageBitmap,
                                dstOffset = IntOffset(pageRect.left.roundToInt(), pageRect.top.roundToInt()),
                                dstSize = IntSize(pageRect.width.roundToInt(), pageRect.height.roundToInt()),
                                alpha = 0.08f
                            )
                        }

                        // 3. Subtle rotated paper-curl shading along crease
                        withTransform({
                            translate(left = foldTopX, top = pageRect.top)
                            rotate(degrees = -thetaDeg, pivot = Offset.Zero)
                        }) {
                            val curlW = 24.dp.toPx() * bell
                            if (curlW > 0.5f) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Black.copy(alpha = 0.12f * bell), Color.Transparent),
                                        startX = -curlW,
                                        endX = 0f
                                    ),
                                    topLeft = Offset(-curlW, -pageRect.width * 0.5f),
                                    size = Size(curlW, diagLen * 1.5f)
                                )
                            }
                        }
                    }

                    // Thin 1.dp crisp paper edge stroke along (leadTopX, top) -> (leadBotX, bottom)
                    drawLine(
                        color = Color.Black.copy(alpha = 0.12f),
                        start = Offset(leadTopX, pageRect.top),
                        end = Offset(leadBotX, pageRect.bottom),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Crisp crease line along fold
                    drawLine(
                        color = Color.Black.copy(alpha = 0.10f),
                        start = Offset(foldTopX, pageRect.top),
                        end = Offset(foldBotX, pageRect.bottom),
                        strokeWidth = 1.dp.toPx()
                    )
                } else {
                    // Turn Prev: foldMidX moves from pageRect.left to pageRect.right
                    val foldMidX = pageRect.left + pageRect.width * p
                    val foldTopX = (foldMidX + slantPx).coerceIn(pageRect.left, pageRect.right)
                    val foldBotX = (foldMidX - slantPx).coerceIn(pageRect.left, pageRect.right)

                    // Conservation Law: paper crossed fold is w * p, so leadMidX = foldMidX + w * p = w * 2p
                    val leadMidX = pageRect.left + pageRect.width * (2f * p)
                    val leadTopX = (leadMidX + slantPx).coerceIn(pageRect.left, pageRect.right)
                    val leadBotX = (leadMidX - slantPx).coerceIn(pageRect.left, pageRect.right)

                    val dxFold = foldBotX - foldTopX
                    val dyFold = pageRect.height.coerceAtLeast(1f)
                    val thetaRad = kotlin.math.atan2(dxFold, dyFold)
                    val thetaDeg = Math.toDegrees(thetaRad.toDouble()).toFloat()
                    val diagLen = kotlin.math.hypot(dxFold, dyFold) + pageRect.width

                    // Region B (pageRect.left .. foldX): Uncovered left polygon of prevPageBitmap
                    val destPath = Path().apply {
                        moveTo(pageRect.left, pageRect.top)
                        lineTo(foldTopX, pageRect.top)
                        lineTo(foldBotX, pageRect.bottom)
                        lineTo(pageRect.left, pageRect.bottom)
                        close()
                    }
                    clipPath(destPath) {
                        drawRect(color = effectivePageBgColor, topLeft = Offset(pageRect.left, pageRect.top), size = Size(pageRect.width, pageRect.height))
                        if (destBitmap != null) {
                            drawPageBitmapFitted(destBitmap, isPdfBook, false)
                        }
                        withTransform({
                            translate(left = foldTopX, top = pageRect.top)
                            rotate(degrees = -thetaDeg, pivot = Offset.Zero)
                        }) {
                            val creaseW = 22.dp.toPx() * bell
                            if (creaseW > 0.5f) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.16f * bell)),
                                        startX = -creaseW,
                                        endX = 0f
                                    ),
                                    topLeft = Offset(-creaseW, -pageRect.width * 0.5f),
                                    size = Size(creaseW, diagLen * 1.5f)
                                )
                            }
                        }
                    }

                    // Region A (min(pageRect.right, leadX) .. pageRect.right): Unpeeled right polygon
                    val curPath = Path().apply {
                        moveTo(leadTopX, pageRect.top)
                        lineTo(pageRect.right, pageRect.top)
                        lineTo(pageRect.right, pageRect.bottom)
                        lineTo(leadBotX, pageRect.bottom)
                        close()
                    }
                    clipPath(curPath) {
                        drawRect(color = effectivePageBgColor, topLeft = Offset(pageRect.left, pageRect.top), size = Size(pageRect.width, pageRect.height))
                        drawPageBitmapFitted(currentPageBitmap, isPdfBook, false)

                        withTransform({
                            translate(left = leadTopX, top = pageRect.top)
                            rotate(degrees = -thetaDeg, pivot = Offset.Zero)
                        }) {
                            val castW = 18.dp.toPx() * bell
                            if (castW > 0.5f) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Black.copy(alpha = 0.14f * bell), Color.Transparent),
                                        startX = 0f,
                                        endX = castW
                                    ),
                                    topLeft = Offset(0f, -pageRect.width * 0.5f),
                                    size = Size(castW, diagLen * 1.5f)
                                )
                            }
                        }
                    }

                    // Region C (foldX .. min(pageRect.right, leadX)): Folded back-of-page polygon
                    val flapPath = Path().apply {
                        moveTo(foldTopX, pageRect.top)
                        lineTo(leadTopX, pageRect.top)
                        lineTo(leadBotX, pageRect.bottom)
                        lineTo(foldBotX, pageRect.bottom)
                        close()
                    }
                    clipPath(flapPath) {
                        // 1. FIRST fill 100% solid opaque paper to prevent ANY bleedthrough!
                        drawPath(flapPath, color = if (isPdfBook) Color(0xFFFAF8F4) else effectiveBackPaperColor, alpha = 1f)

                        withTransform({
                            translate(left = foldTopX, top = pageRect.top)
                            rotate(degrees = -thetaDeg, pivot = Offset.Zero)
                            scale(scaleX = -1f, scaleY = 1f, pivot = Offset.Zero)
                            rotate(degrees = thetaDeg, pivot = Offset.Zero)
                            translate(left = -foldTopX, top = -pageRect.top)
                        }) {
                            drawImage(
                                image = currentPageBitmap,
                                dstOffset = IntOffset(pageRect.left.roundToInt(), pageRect.top.roundToInt()),
                                dstSize = IntSize(pageRect.width.roundToInt(), pageRect.height.roundToInt()),
                                alpha = 0.08f
                            )
                        }

                        withTransform({
                            translate(left = foldTopX, top = pageRect.top)
                            rotate(degrees = -thetaDeg, pivot = Offset.Zero)
                        }) {
                            val curlW = 24.dp.toPx() * bell
                            if (curlW > 0.5f) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f * bell)),
                                        startX = 0f,
                                        endX = curlW
                                    ),
                                    topLeft = Offset(0f, -pageRect.width * 0.5f),
                                    size = Size(curlW, diagLen * 1.5f)
                                )
                            }
                        }
                    }

                    // Thin 1.dp crisp paper edge stroke along (leadTopX, top) -> (leadBotX, bottom)
                    drawLine(
                        color = Color.Black.copy(alpha = 0.12f),
                        start = Offset(leadTopX, pageRect.top),
                        end = Offset(leadBotX, pageRect.bottom),
                        strokeWidth = 1.dp.toPx()
                    )

                    // Crisp crease line along fold
                    drawLine(
                        color = Color.Black.copy(alpha = 0.10f),
                        start = Offset(foldTopX, pageRect.top),
                        end = Offset(foldBotX, pageRect.bottom),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
        }
    }
}
