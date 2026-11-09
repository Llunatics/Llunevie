package com.llunaticsys.llunevie.ui.reader

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import com.llunaticsys.llunevie.model.extractCleanPageParagraphs
import com.llunaticsys.llunevie.model.isOrphanParagraphFragment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llunaticsys.llunevie.data.local.HighlightNoteEntity
import com.llunaticsys.llunevie.data.local.UserPreferencesState
import com.llunaticsys.llunevie.data.parser.EpubNativeParser
import com.llunaticsys.llunevie.model.HighlightColorKey
import com.llunaticsys.llunevie.ui.components.sanitizeBookOrChapterText
import com.llunaticsys.llunevie.ui.theme.LluneviePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToInt

val CURATED_READER_FONTS = listOf(
    "Literata" to "Literata",
    "EB Garamond" to "EB Garamond",
    "Source Serif 4" to "Source Serif",
    "Newsreader" to "Newsreader",
    "Lora" to "Lora",
    "Merriweather" to "Merriweather",
    "Crimson Pro" to "Crimson Pro",
    "Playfair Display" to "Playfair",
    "PT Serif" to "PT Serif",
    "Bitter" to "Bitter",
    "Plus Jakarta Sans" to "Plus Jakarta",
    "Inter" to "Inter",
    "DM Sans" to "DM Sans",
    "Nunito" to "Nunito",
    "JetBrains Mono" to "JetBrains Mono"
)

internal val SANS_FONTS = setOf("Plus Jakarta Sans", "Inter", "DM Sans", "Nunito")

/**
 * Draws a soft, natural, multi-layered Gaussian-feathered drop shadow around the book/page
 * without relying on hardware RenderNode spot shadows (which produce harsh edges or clip against bounds).
 */
internal fun Modifier.softBookShadow(
    cornerRadius: Dp = 6.dp,
    spreadRadius: Dp = 18.dp,
    offsetY: Dp = 3.dp,
    isDarkTheme: Boolean = false
): Modifier = this.drawBehind {
    val spreadPx = spreadRadius.toPx()
    val cornerPx = cornerRadius.toPx()
    val dyPx = offsetY.toPx()
    val baseMaxAlpha = if (isDarkTheme) 0.36f else 0.14f
    val steps = 16
    for (i in steps downTo 1) {
        val t = i.toFloat() / steps.toFloat()
        val expand = spreadPx * t
        val r = cornerPx + expand * 0.75f
        val stepAlpha = (baseMaxAlpha / steps.toFloat()) * (1.45f * (1f - t * 0.65f))
        drawRoundRect(
            color = Color.Black.copy(alpha = stepAlpha.coerceIn(0.002f, 0.05f)),
            topLeft = Offset(-expand, -expand * 0.85f + dyPx * t),
            size = Size(size.width + expand * 2f, size.height + expand * 1.85f),
            cornerRadius = CornerRadius(r, r)
        )
    }
}

private val fontFamiliesCache = mutableMapOf<String, FontFamily>()

fun getReaderFontFamily(context: Context? = null, name: String, fontWeight: Int = 400): FontFamily {
    val key = "$name:$fontWeight"
    return fontFamiliesCache.getOrPut(key) {
        val assetPath = PageSnapshotBitmapRenderer.getFontAssetPath(name)
        if (context != null && assetPath != null) {
            runCatching {
                FontFamily(androidx.compose.ui.text.font.Font(assetManager = context.assets, path = assetPath))
            }.getOrNull() ?: runCatching {
                val tf = PageSnapshotBitmapRenderer.getSystemTypeface(name, fontWeight, context)
                FontFamily(tf)
            }.getOrDefault(FontFamily.Default)
        } else {
            val tf = PageSnapshotBitmapRenderer.getSystemTypeface(name, fontWeight, context)
            FontFamily(tf)
        }
    }
}

// Byte-bounded in-memory snapshot bitmap cache for instant 0ms page turns (48 MB max, size in KB).
// A full-screen 1080x2400 ARGB_8888 ImageBitmap is ~10.1 MB (~10,125 KB), so 48 MB (49,152 KB)
// holds 4–5 full-screen snapshots (prev, current, next, plus recent turn) while strictly capping heap usage.
internal val epubSnapshotCache = object : LruCache<String, ImageBitmap>(48 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap): Int =
        (value.asAndroidBitmap().allocationByteCount / 1024).coerceAtLeast(1)
}

internal val snapshotRenderMutex = Mutex()

fun clearEpubSnapshotCache(evictAll: Boolean = true) {
    if (evictAll) {
        epubSnapshotCache.evictAll()
    } else {
        epubSnapshotCache.trimToSize(epubSnapshotCache.maxSize() / 2)
    }
}

internal fun buildSnapshotCacheKey(
    bookId: String,
    pageZero: Int,
    isSpread: Boolean,
    widthPx: Int,
    heightPx: Int,
    bgArgb: Int,
    textArgb: Int,
    prefs: UserPreferencesState,
    highlightsHash: Int,
    translationSuffix: String = ""
): String {
    return "$bookId:$pageZero:$isSpread:${widthPx}x${heightPx}:$bgArgb:$textArgb:" +
        "${prefs.fontSizeSp}:${prefs.lineHeightMultiplier}:${prefs.fontFamily}:" +
        "${prefs.fontWeight}:${prefs.paraSpacingEm}:${prefs.wordSpacingPx}:" +
        "${prefs.pageMargin}:${prefs.paraIndent}:${prefs.letterSpacingEm}:${prefs.textAlign}:" +
        "${prefs.appTheme}:${prefs.followDeviceTheme}:${prefs.amoledBlack}:${prefs.pdfThemeTintEnabled}:${prefs.pdfProtectIllustrations}:$highlightsHash$translationSuffix"
}

internal fun buildTranslatedPageRaw(
    originalPageRaw: String,
    translatedParagraphs: List<String>?,
    displayMode: String
): String {
    if (translatedParagraphs.isNullOrEmpty()) return originalPageRaw
    val rawParas = extractCleanPageParagraphs(originalPageRaw)
    if (displayMode == "replace") {
        var tIdx = 0
        return rawParas.joinToString("\n\n") { para ->
            val trimmed = para.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("[ILLUST:") || trimmed.startsWith("[PDF_PAGE:")) {
                para
            } else {
                val rep = translatedParagraphs.getOrNull(tIdx) ?: para
                tIdx++
                rep
            }
        }
    } else if (displayMode == "bilingual") {
        var tIdx = 0
        return rawParas.joinToString("\n\n") { para ->
            val trimmed = para.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("[ILLUST:") || trimmed.startsWith("[PDF_PAGE:")) {
                para
            } else {
                val rep = translatedParagraphs.getOrNull(tIdx)?.trim().orEmpty()
                tIdx++
                if (rep.isNotBlank() && rep != trimmed) {
                    "$trimmed\n\n$rep"
                } else {
                    trimmed
                }
            }
        }
    }
    return originalPageRaw
}

@JvmName("buildTranslatedPageRawFromPairs")
internal fun buildTranslatedPageRaw(
    originalPageRaw: String,
    translatedPairs: List<Pair<String, String?>>?,
    displayMode: String
): String {
    if (translatedPairs.isNullOrEmpty()) return originalPageRaw
    val filteredPairs = translatedPairs.filterNot { isOrphanParagraphFragment(it.first) }
    val modeNorm = displayMode.trim().lowercase()
    if (modeNorm == "replace") {
        return filteredPairs.joinToString("\n\n") { (orig, trans) ->
            val trimmedOrig = orig.trim()
            if (trimmedOrig.isEmpty() || trimmedOrig.startsWith("[ILLUST:") || trimmedOrig.startsWith("[PDF_PAGE:")) {
                trimmedOrig
            } else {
                val trimmedTrans = trans?.trim()
                if (!trimmedTrans.isNullOrBlank()) trimmedTrans else trimmedOrig
            }
        }
    } else if (modeNorm == "bilingual") {
        return filteredPairs.joinToString("\n\n") { (orig, trans) ->
            val trimmedOrig = orig.trim()
            if (trimmedOrig.isEmpty() || trimmedOrig.startsWith("[ILLUST:") || trimmedOrig.startsWith("[PDF_PAGE:")) {
                trimmedOrig
            } else {
                val trimmedTrans = trans?.trim()
                if (!trimmedTrans.isNullOrBlank() && !trimmedTrans.equals(trimmedOrig, ignoreCase = true)) {
                    "$trimmedOrig\n\n$trimmedTrans"
                } else {
                    trimmedOrig
                }
            }
        }
    }
    return originalPageRaw
}

internal fun buildEpubPageAnnotatedString(
    textParagraphs: List<String>,
    prefs: UserPreferencesState,
    fontScale: Float = 1f,
    resolvedTextAlign: TextAlign,
    bookHighlights: List<HighlightNoteEntity>,
    currentChapterIndex: Int,
    currentPageInChapter: Int,
    displayPageIndex: Int = -1,
    palette: LluneviePalette
): AnnotatedString {
    val lineHeightSp = (prefs.fontSizeSp * fontScale * prefs.lineHeightMultiplier).sp
    val paraGapSp = (prefs.fontSizeSp * fontScale * prefs.paraSpacingEm.coerceIn(0.2f, 2.5f) * 0.85f).sp
    val wordExtraSp = (prefs.wordSpacingPx * 0.28f).sp
    val spaceSpan = if (prefs.wordSpacingPx > 0) SpanStyle(letterSpacing = wordExtraSp) else null
    val indentSpec = if (prefs.paraIndent) {
        TextIndent(firstLine = (prefs.fontSizeSp * fontScale * 1.6f).sp)
    } else {
        TextIndent.None
    }

    return buildAnnotatedString {
        textParagraphs.forEachIndexed { idx, rawPara ->
            val cleanPara = sanitizeBookOrChapterText(rawPara)
            val paraStart = length
            withStyle(
                ParagraphStyle(
                    textAlign = resolvedTextAlign,
                    lineHeight = lineHeightSp,
                    textIndent = indentSpec
                )
            ) {
                append(cleanPara)
            }
            val paraEnd = length

            if (spaceSpan != null) {
                for (cIdx in paraStart until paraEnd) {
                    if (cleanPara[cIdx - paraStart] == ' ') {
                        addStyle(spaceSpan, cIdx, cIdx + 1)
                    }
                }
            }

            if (idx < textParagraphs.lastIndex) {
                withStyle(ParagraphStyle(lineHeight = paraGapSp)) {
                    withStyle(SpanStyle(fontSize = paraGapSp)) {
                        append("\n")
                    }
                }
            }
        }

        val fullText = toAnnotatedString().text
        val pageHls = bookHighlights.filter { hl ->
            (hl.chapterIndex == currentChapterIndex && hl.pageIndex == currentPageInChapter) ||
                (displayPageIndex >= 0 && hl.pageIndex == displayPageIndex) ||
                (hl.quoteText.isNotBlank() && fullText.contains(hl.quoteText.trim(), ignoreCase = true))
        }
        pageHls.forEach { hl ->
            val hlStart = if (hl.startCharIndex in 0 until fullText.length &&
                hl.endCharIndex in (hl.startCharIndex + 1)..fullText.length
            ) {
                hl.startCharIndex
            } else {
                val quote = hl.quoteText.trim()
                if (quote.isNotBlank()) fullText.indexOf(quote, ignoreCase = true) else -1
            }
            val hlEnd = if (hl.startCharIndex in 0 until fullText.length &&
                hl.endCharIndex in (hl.startCharIndex + 1)..fullText.length
            ) {
                hl.endCharIndex
            } else if (hlStart >= 0) {
                hlStart + hl.quoteText.trim().length
            } else -1

            if (hlStart in 0 until fullText.length && hlEnd in (hlStart + 1)..fullText.length) {
                addStyle(
                    SpanStyle(
                        color = Color(0xFF18181B),
                        fontWeight = FontWeight.Medium
                    ),
                    hlStart,
                    hlEnd
                )
            }
        }
    }
}

internal data class OnePageFittedLayout(
    val fitScale: Float,
    val annotatedString: AnnotatedString,
    val textLayout: TextLayoutResult?,
    val titleLayout: TextLayoutResult?
)

internal fun computeOnePageFittedLayout(
    textMeasurer: TextMeasurer,
    widthPx: Int,
    heightPx: Int,
    density: Density,
    chapterTitle: String,
    pageText: String,
    baseTextStyle: TextStyle,
    padX: Float,
    padTop: Float,
    padBottom: Float,
    showChapterHeader: Boolean,
    isCover: Boolean,
    highlights: List<HighlightNoteEntity>,
    chapterIndex: Int,
    pageIndex: Int,
    palette: LluneviePalette,
    prefs: UserPreferencesState
): OnePageFittedLayout {
    val rawParagraphs = pageText.split("\n\n").map { it.trim() }.filter { it.isNotBlank() && !it.startsWith("[PDF_PAGE:") }
    val isCoverActual = isCover ||
        ((chapterTitle.equals("Sampul", ignoreCase = true) || chapterTitle.equals("Cover", ignoreCase = true)) && rawParagraphs.any { it.startsWith("[ILLUST:") }) ||
        (rawParagraphs.size == 1 && rawParagraphs[0].startsWith("[ILLUST:"))

    if (isCoverActual) {
        return OnePageFittedLayout(1f, AnnotatedString(""), null, null)
    }

    val availableH = (heightPx - padTop - padBottom - with(density) { 8.dp.toPx() }).coerceAtLeast(140f)
    val contentW = (widthPx - padX * 2).roundToInt().coerceAtLeast(100)

    var titleLayout: TextLayoutResult? = null
    var headerH = 0f
    if (showChapterHeader && chapterTitle.isNotBlank()) {
        titleLayout = textMeasurer.measure(
            text = AnnotatedString(chapterTitle),
            style = TextStyle(
                color = palette.textMain,
                fontSize = ((prefs.fontSizeSp) + 6).sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            ),
            constraints = Constraints(maxWidth = contentW)
        )
        headerH = titleLayout.size.height + with(density) { 16.dp.toPx() }
    }

    val textParagraphs = rawParagraphs.filter { !it.startsWith("[ILLUST:") }
    if (textParagraphs.isEmpty()) {
        return OnePageFittedLayout(1f, AnnotatedString(""), null, titleLayout)
    }

    val resolvedTextAlign = when (prefs.textAlign.lowercase()) {
        "justify" -> TextAlign.Justify
        "center" -> TextAlign.Center
        else -> TextAlign.Start
    }

    fun measureCandidate(candidateScale: Float): Pair<AnnotatedString, TextLayoutResult> {
        val annotatedString = buildEpubPageAnnotatedString(
            textParagraphs = textParagraphs,
            prefs = prefs,
            fontScale = candidateScale,
            resolvedTextAlign = resolvedTextAlign,
            bookHighlights = highlights,
            currentChapterIndex = chapterIndex,
            currentPageInChapter = pageIndex,
            displayPageIndex = -1,
            palette = palette
        )
        val scaledStyle = baseTextStyle.copy(
            fontSize = (prefs.fontSizeSp * candidateScale).sp,
            lineHeight = (prefs.fontSizeSp * prefs.lineHeightMultiplier * candidateScale).sp
        )
        val layout = textMeasurer.measure(
            text = annotatedString,
            style = scaledStyle,
            constraints = Constraints(maxWidth = contentW)
        )
        return annotatedString to layout
    }

    // Fast path: 1.0f fits without scaling
    val (annotatedAtOne, layoutAtOne) = measureCandidate(1f)
    if ((headerH + layoutAtOne.size.height) <= availableH) {
        return OnePageFittedLayout(1f, annotatedAtOne, layoutAtOne, titleLayout)
    }

    // 4-step binary search over [0.52f, 1.0f]
    var low = 0.52f
    var high = 1.0f
    var bestScale = 0.52f
    var bestPair: Pair<AnnotatedString, TextLayoutResult>? = null
    repeat(4) {
        val mid = (low + high) * 0.5f
        val pair = measureCandidate(mid)
        if ((headerH + pair.second.size.height) <= availableH) {
            bestScale = mid
            bestPair = pair
            low = mid
        } else {
            high = mid
        }
    }

    val finalScale = bestScale.coerceAtLeast(0.52f)
    val finalPair = bestPair ?: measureCandidate(finalScale)
    return OnePageFittedLayout(finalScale, finalPair.first, finalPair.second, titleLayout)
}

internal fun computeOnePageFitScale(
    textMeasurer: TextMeasurer,
    widthPx: Int,
    heightPx: Int,
    density: Density,
    chapterTitle: String,
    pageText: String,
    baseTextStyle: TextStyle,
    padX: Float,
    padTop: Float,
    padBottom: Float,
    showChapterHeader: Boolean,
    isCover: Boolean,
    highlights: List<HighlightNoteEntity>,
    chapterIndex: Int,
    pageIndex: Int,
    palette: LluneviePalette,
    prefs: UserPreferencesState
): Float = computeOnePageFittedLayout(
    textMeasurer = textMeasurer,
    widthPx = widthPx,
    heightPx = heightPx,
    density = density,
    chapterTitle = chapterTitle,
    pageText = pageText,
    baseTextStyle = baseTextStyle,
    padX = padX,
    padTop = padTop,
    padBottom = padBottom,
    showChapterHeader = showChapterHeader,
    isCover = isCover,
    highlights = highlights,
    chapterIndex = chapterIndex,
    pageIndex = pageIndex,
    palette = palette,
    prefs = prefs
).fitScale

internal suspend fun renderCachedEpubPageSnapshot(
    cacheKey: String?,
    textMeasurer: TextMeasurer,
    widthPx: Int,
    heightPx: Int,
    density: Density,
    chapterTitle: String,
    pageText: String,
    bgColor: Color,
    pageTextStyle: TextStyle,
    padX: Float,
    padTop: Float,
    padBottom: Float = 0f,
    showChapterHeader: Boolean = false,
    isCover: Boolean,
    highlights: List<HighlightNoteEntity>,
    chapterIndex: Int,
    pageIndex: Int,
    palette: LluneviePalette,
    prefs: UserPreferencesState? = null
): ImageBitmap {
    if (cacheKey != null) {
        epubSnapshotCache.get(cacheKey)?.let { return it }
    }
    return withContext(Dispatchers.Default) {
        snapshotRenderMutex.withLock {
            if (cacheKey != null) {
                epubSnapshotCache.get(cacheKey)?.let { return@withLock it }
            }
            renderEpubPageSnapshot(
                textMeasurer = textMeasurer,
                widthPx = widthPx,
                heightPx = heightPx,
                density = density,
                chapterTitle = chapterTitle,
                pageText = pageText,
                bgColor = bgColor,
                pageTextStyle = pageTextStyle,
                padX = padX,
                padTop = padTop,
                padBottom = padBottom,
                showChapterHeader = showChapterHeader,
                isCover = isCover,
                highlights = highlights,
                chapterIndex = chapterIndex,
                pageIndex = pageIndex,
                palette = palette,
                prefs = prefs
            ).also { rendered ->
                if (cacheKey != null) {
                    epubSnapshotCache.put(cacheKey, rendered)
                }
            }
        }
    }
}

internal fun renderEpubPageSnapshot(
    textMeasurer: TextMeasurer,
    widthPx: Int,
    heightPx: Int,
    density: Density,
    chapterTitle: String,
    pageText: String,
    bgColor: Color,
    pageTextStyle: TextStyle,
    padX: Float,
    padTop: Float,
    padBottom: Float = 0f,
    showChapterHeader: Boolean = false,
    isCover: Boolean,
    highlights: List<HighlightNoteEntity>,
    chapterIndex: Int,
    pageIndex: Int,
    palette: LluneviePalette,
    prefs: UserPreferencesState? = null
): ImageBitmap {
    val bitmap = ImageBitmap(widthPx.coerceAtLeast(100), heightPx.coerceAtLeast(100))
    val canvas = Canvas(bitmap)
    val drawScope = CanvasDrawScope()

    drawScope.draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = Size(widthPx.toFloat(), heightPx.toFloat())
    ) {
        drawRect(color = bgColor)

        val rawParagraphs = pageText.split("\n\n")
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("[PDF_PAGE:") }

        val isCoverActual = isCover || (
            (chapterTitle.equals("Sampul", ignoreCase = true) || chapterTitle.equals("Cover", ignoreCase = true)) &&
                rawParagraphs.any { it.startsWith("[ILLUST:") }
            ) || (rawParagraphs.size == 1 && rawParagraphs[0].startsWith("[ILLUST:"))

        if (isCoverActual) {
            val illustPara = rawParagraphs.firstOrNull { it.startsWith("[ILLUST:") }
            val imgPath = illustPara?.removePrefix("[ILLUST:")?.substringBefore("|")?.substringBefore("]")
            if (imgPath != null && File(imgPath).exists()) {
                val coverBmp = EpubNativeParser.decodeFullResImage(imgPath, maxDimPx = 1440)
                if (coverBmp != null) {
                    val scale = minOf(size.width / coverBmp.width.toFloat(), size.height / coverBmp.height.toFloat())
                    val dstW = (coverBmp.width * scale).coerceAtLeast(1f)
                    val dstH = (coverBmp.height * scale).coerceAtLeast(1f)
                    val dstX = (size.width - dstW) / 2f
                    val dstY = (size.height - dstH) / 2f
                    drawImage(
                        image = coverBmp.asImageBitmap(),
                        dstOffset = IntOffset(dstX.roundToInt(), dstY.roundToInt()),
                        dstSize = IntSize(dstW.roundToInt(), dstH.roundToInt())
                    )
                    return@draw
                }
            }
        }

        val effectivePrefs = prefs ?: UserPreferencesState()
        val fitted = computeOnePageFittedLayout(
            textMeasurer = textMeasurer,
            widthPx = widthPx,
            heightPx = heightPx,
            density = density,
            chapterTitle = chapterTitle,
            pageText = pageText,
            baseTextStyle = pageTextStyle,
            padX = padX,
            padTop = padTop,
            padBottom = padBottom,
            showChapterHeader = showChapterHeader,
            isCover = isCover,
            highlights = highlights,
            chapterIndex = chapterIndex,
            pageIndex = pageIndex,
            palette = palette,
            prefs = effectivePrefs
        )

        var currentY = padTop
        val titleLayout = fitted.titleLayout
        if (titleLayout != null) {
            drawText(titleLayout, topLeft = Offset(padX, currentY))
            currentY += titleLayout.size.height + with(density) { 16.dp.toPx() }
        }

        val layout = fitted.textLayout ?: return@draw
        val annotatedString = fitted.annotatedString

        val snapshotFullText = annotatedString.text
        val pageHls = highlights.filter { hl ->
            (hl.chapterIndex == chapterIndex && hl.pageIndex == pageIndex) ||
                (hl.quoteText.isNotBlank() && snapshotFullText.contains(hl.quoteText.trim(), ignoreCase = true))
        }
        pageHls.forEach { hl ->
            val colorKey = HighlightColorKey.fromKey(hl.colorKey)
            val pastelBg = palette.highlightBg(colorKey)
            val hlStart = if (hl.startCharIndex in 0 until snapshotFullText.length &&
                hl.endCharIndex in (hl.startCharIndex + 1)..snapshotFullText.length
            ) {
                hl.startCharIndex
            } else {
                val q = hl.quoteText.trim()
                if (q.isNotBlank()) snapshotFullText.indexOf(q, ignoreCase = true) else -1
            }
            val hlEnd = if (hl.startCharIndex in 0 until snapshotFullText.length &&
                hl.endCharIndex in (hl.startCharIndex + 1)..snapshotFullText.length
            ) {
                hl.endCharIndex
            } else if (hlStart >= 0) {
                hlStart + hl.quoteText.trim().length
            } else -1

            if (hlStart in 0 until snapshotFullText.length && hlEnd in (hlStart + 1)..snapshotFullText.length) {
                val sLine = layout.getLineForOffset(hlStart)
                val eLine = layout.getLineForOffset(hlEnd)
                val sX = layout.getHorizontalPosition(hlStart, usePrimaryDirection = true)
                val eX = layout.getHorizontalPosition(hlEnd, usePrimaryDirection = true)

                for (line in sLine..eLine) {
                    val lineLeft = if (line == sLine) sX else layout.getLineLeft(line)
                    val lineRight = if (line == eLine) eX else layout.getLineRight(line)
                    val rawTop = layout.getLineTop(line)
                    val rawBottom = layout.getLineBottom(line)
                    val lineH = rawBottom - rawTop
                    val tightTop = rawTop + lineH * 0.06f
                    val tightBottom = rawBottom - lineH * 0.14f
                    if (lineRight > lineLeft) {
                        drawRoundRect(
                            color = pastelBg,
                            topLeft = Offset(padX + lineLeft, currentY + tightTop),
                            size = Size(lineRight - lineLeft, (tightBottom - tightTop).coerceAtLeast(10f)),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                }
            }
        }

        drawText(layout, topLeft = Offset(padX, currentY))
        if (pageTextStyle.fontWeight == FontWeight.Medium) {
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(padX, currentY),
                color = pageTextStyle.color,
                drawStyle = Stroke(width = 0.45f * density.density)
            )
        }
    }
    return bitmap
}

internal suspend fun renderCachedTwoPageSpreadSnapshot(
    cacheKey: String?,
    textMeasurer: TextMeasurer,
    widthPx: Int,
    heightPx: Int,
    density: Density,
    leftPageIndex: Int,
    rightPageIndex: Int,
    leftHeader: String,
    rightHeader: String,
    leftText: String,
    rightText: String,
    leftPdfBitmap: Bitmap?,
    rightPdfBitmap: Bitmap?,
    bgColor: Color,
    pageTextStyle: TextStyle,
    palette: LluneviePalette,
    prefs: UserPreferencesState? = null,
    leftColorFilter: android.graphics.ColorMatrixColorFilter? = null,
    rightColorFilter: android.graphics.ColorMatrixColorFilter? = null
): ImageBitmap {
    if (cacheKey != null) {
        epubSnapshotCache.get(cacheKey)?.let { return it }
    }
    return withContext(Dispatchers.Default) {
        snapshotRenderMutex.withLock {
            if (cacheKey != null) {
                epubSnapshotCache.get(cacheKey)?.let { return@withLock it }
            }
            renderTwoPageSpreadSnapshot(
                textMeasurer = textMeasurer,
                widthPx = widthPx,
                heightPx = heightPx,
                density = density,
                leftPageIndex = leftPageIndex,
                rightPageIndex = rightPageIndex,
                leftHeader = leftHeader,
                rightHeader = rightHeader,
                leftText = leftText,
                rightText = rightText,
                leftPdfBitmap = leftPdfBitmap,
                rightPdfBitmap = rightPdfBitmap,
                bgColor = bgColor,
                pageTextStyle = pageTextStyle,
                palette = palette,
                prefs = prefs,
                leftColorFilter = leftColorFilter,
                rightColorFilter = rightColorFilter
            ).also { rendered ->
                if (cacheKey != null) {
                    epubSnapshotCache.put(cacheKey, rendered)
                }
            }
        }
    }
}

internal fun renderTwoPageSpreadSnapshot(
    textMeasurer: TextMeasurer,
    widthPx: Int,
    heightPx: Int,
    density: Density,
    leftPageIndex: Int,
    rightPageIndex: Int,
    leftHeader: String,
    rightHeader: String,
    leftText: String,
    rightText: String,
    leftPdfBitmap: Bitmap?,
    rightPdfBitmap: Bitmap?,
    bgColor: Color,
    pageTextStyle: TextStyle,
    palette: LluneviePalette,
    prefs: UserPreferencesState? = null,
    leftColorFilter: android.graphics.ColorMatrixColorFilter? = null,
    rightColorFilter: android.graphics.ColorMatrixColorFilter? = null
): ImageBitmap {
    val bitmap = ImageBitmap(widthPx.coerceAtLeast(100), heightPx.coerceAtLeast(100))
    val canvas = Canvas(bitmap)
    val drawScope = CanvasDrawScope()

    drawScope.draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = canvas,
        size = Size(widthPx.toFloat(), heightPx.toFloat())
    ) {
        drawRect(color = bgColor)

        val halfW = size.width / 2f
        val spineX = halfW

        // 1. Center spine divider line
        drawLine(
            color = palette.borderSoft.copy(alpha = 0.6f),
            start = Offset(spineX, 0f),
            end = Offset(spineX, size.height),
            strokeWidth = with(density) { 1.dp.toPx() }
        )

        val (hMarginDp, vMarginDp) = when (prefs?.pageMargin) {
            0 -> 10.dp to 10.dp
            2 -> 28.dp to 20.dp
            else -> 18.dp to 14.dp
        }
        val colPadHoriz = with(density) { hMarginDp.toPx() }
        val colPadVert = with(density) { vMarginDp.toPx() }
        val headerH = with(density) { 22.dp.toPx() }
        val footerH = with(density) { 20.dp.toPx() }
        val colInnerW = (halfW - colPadHoriz * 2).roundToInt().coerceAtLeast(100)
        val colInnerH = (size.height - headerH - footerH - colPadVert * 2).coerceAtLeast(100f)
        val textTop = headerH + colPadVert
        val clipBottom = size.height - footerH - colPadVert

        val headerStyle = TextStyle(
            color = palette.textMuted,
            fontSize = with(density) { 8.5.sp },
            fontFamily = FontFamily.Monospace,
            platformStyle = PlatformTextStyle(includeFontPadding = false)
        )

        // Left Header & Footer
        if (leftHeader.isNotBlank()) {
            val hLayout = textMeasurer.measure(
                text = AnnotatedString(leftHeader.take(32).uppercase()),
                style = headerStyle,
                constraints = Constraints(maxWidth = colInnerW)
            )
            drawText(hLayout, topLeft = Offset(colPadHoriz, 6f + colPadVert * 0.5f))
        }
        val leftPageNum = textMeasurer.measure(
            text = AnnotatedString("${leftPageIndex + 1}"),
            style = headerStyle,
            constraints = Constraints(maxWidth = colInnerW)
        )
        drawText(leftPageNum, topLeft = Offset(colPadHoriz, size.height - footerH))

        // Right Header & Footer
        if (rightHeader.isNotBlank()) {
            val rhLayout = textMeasurer.measure(
                text = AnnotatedString(rightHeader.take(32).uppercase()),
                style = headerStyle,
                constraints = Constraints(maxWidth = colInnerW)
            )
            drawText(rhLayout, topLeft = Offset(halfW + colPadHoriz, 6f + colPadVert * 0.5f))
        }
        if (rightPageIndex == leftPageIndex || rightText.isNotBlank() || rightPdfBitmap != null || rightHeader.isNotBlank()) {
            val rightPageNum = textMeasurer.measure(
                text = AnnotatedString("${rightPageIndex + 1}"),
                style = headerStyle,
                constraints = Constraints(maxWidth = colInnerW)
            )
            drawText(rightPageNum, topLeft = Offset(size.width - colPadHoriz - rightPageNum.size.width, size.height - footerH))
        }

        fun extractStandaloneIllustPath(text: String): String? {
            val paras = text.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
            val illustPara = paras.firstOrNull { it.startsWith("[ILLUST:") } ?: return null
            val nonIllust = paras.filter { !it.startsWith("[ILLUST:") && !it.startsWith("[PDF_PAGE:") }
            if (nonIllust.isNotEmpty()) return null
            return illustPara.removePrefix("[ILLUST:").substringBefore("|").substringBefore("]").trim().takeIf { it.isNotBlank() }
        }

        fun buildSpreadColumnAnnotated(
            text: String,
            effectiveFontSizeSp: Float
        ): AnnotatedString {
            val clean = text
                .replace(Regex("\\[ILLUST:[^]]+]"), "")
                .replace(Regex("\\[PDF_PAGE:[^]]+]"), "")
                .trim()
            val rawParas = clean.split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
            val paraIndent = prefs?.paraIndent ?: false
            val paraSpacingEm = prefs?.paraSpacingEm ?: 1.1f
            val wordSpacingPx = prefs?.wordSpacingPx ?: 0
            val textAlign = pageTextStyle.textAlign ?: TextAlign.Start

            val indentSpec = if (paraIndent) TextIndent(firstLine = (effectiveFontSizeSp * 1.5f).sp) else TextIndent.None
            val paraGapSp = (effectiveFontSizeSp * paraSpacingEm.coerceIn(0.2f, 2.5f) * 0.85f).sp
            val wordExtraSp = (wordSpacingPx * 0.28f).sp
            val spaceSpan = if (wordSpacingPx > 0) SpanStyle(letterSpacing = wordExtraSp) else null

            return buildAnnotatedString {
                rawParas.forEachIndexed { idx, pText ->
                    val pStart = length
                    withStyle(
                        ParagraphStyle(
                            textAlign = textAlign,
                            textIndent = indentSpec
                        )
                    ) {
                        append(pText)
                    }
                    val pEnd = length
                    if (spaceSpan != null) {
                        for (i in pStart until pEnd) {
                            if (pText[i - pStart] == ' ') {
                                addStyle(spaceSpan, i, i + 1)
                            }
                        }
                    }
                    if (idx < rawParas.lastIndex) {
                        withStyle(ParagraphStyle(lineHeight = paraGapSp)) {
                            withStyle(SpanStyle(fontSize = paraGapSp)) {
                                append("\n")
                            }
                        }
                    }
                }
            }
        }

        fun measureFittedSpreadColumn(text: String): TextLayoutResult {
            val baseFontSize = pageTextStyle.fontSize.value
            val baseLineHeight = pageTextStyle.lineHeight.value
            val constraints = Constraints(maxWidth = colInnerW)

            fun measureAt(scale: Float): TextLayoutResult {
                val style = pageTextStyle.copy(
                    fontSize = (baseFontSize * scale).sp,
                    lineHeight = (baseLineHeight * scale).sp
                )
                val annotated = buildSpreadColumnAnnotated(text, baseFontSize * scale)
                return textMeasurer.measure(
                    text = annotated,
                    style = style,
                    constraints = constraints
                )
            }

            // Fast path at 1.0f (same machinery as computeOnePageFitScale)
            val layoutAtOne = measureAt(1.0f)
            if (layoutAtOne.size.height <= colInnerH) {
                return layoutAtOne
            }

            val minScale = 0.36f
            var low = minScale
            var high = 1.0f
            var bestLayout: TextLayoutResult? = null
            repeat(6) {
                val mid = (low + high) * 0.5f
                val candidate = measureAt(mid)
                if (candidate.size.height <= colInnerH) {
                    bestLayout = candidate
                    low = mid
                } else {
                    high = mid
                }
            }
            return bestLayout ?: measureAt(minScale)
        }

        val leftIllustPath = if (leftPdfBitmap == null) extractStandaloneIllustPath(leftText) else null
        val leftIllustBmp = leftIllustPath?.let { path ->
            if (File(path).exists()) EpubNativeParser.decodeFullResImage(path, maxDimPx = 1080) else null
        }

        // Left Page Content
        if (leftPdfBitmap != null) {
            val scale = minOf(halfW / leftPdfBitmap.width.toFloat(), size.height / leftPdfBitmap.height.toFloat())
            val dstW = leftPdfBitmap.width * scale
            val dstH = leftPdfBitmap.height * scale
            val androidCanvas = drawContext.canvas.nativeCanvas
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = leftColorFilter
            }
            val srcRect = android.graphics.Rect(0, 0, leftPdfBitmap.width, leftPdfBitmap.height)
            val dstRect = android.graphics.RectF(
                ((halfW - dstW) / 2),
                ((size.height - dstH) / 2),
                ((halfW - dstW) / 2) + dstW,
                ((size.height - dstH) / 2) + dstH
            )
            androidCanvas.drawBitmap(leftPdfBitmap, srcRect, dstRect, paint)
        } else if (leftIllustBmp != null) {
            val scale = minOf(colInnerW.toFloat() / leftIllustBmp.width.toFloat(), colInnerH / leftIllustBmp.height.toFloat())
            val dstW = (leftIllustBmp.width * scale).roundToInt().coerceAtLeast(1)
            val dstH = (leftIllustBmp.height * scale).roundToInt().coerceAtLeast(1)
            val dstX = (colPadHoriz + (colInnerW - dstW) / 2f).roundToInt()
            val dstY = (textTop + (colInnerH - dstH) / 2f).roundToInt()
            drawImage(
                image = leftIllustBmp.asImageBitmap(),
                dstOffset = IntOffset(dstX, dstY),
                dstSize = IntSize(dstW, dstH)
            )
        } else if (leftText.isNotBlank()) {
            val leftLayout = measureFittedSpreadColumn(leftText)
            clipRect(colPadHoriz, textTop, colPadHoriz + colInnerW, clipBottom) {
                drawText(leftLayout, topLeft = Offset(colPadHoriz, textTop))
                if (prefs?.fontWeight == 500) {
                    drawText(
                        textLayoutResult = leftLayout,
                        topLeft = Offset(colPadHoriz, textTop),
                        color = pageTextStyle.color,
                        drawStyle = Stroke(width = 0.45f * density.density)
                    )
                }
            }
        }

        val rightIllustPath = if (rightPdfBitmap == null) extractStandaloneIllustPath(rightText) else null
        val rightIllustBmp = rightIllustPath?.let { path ->
            if (File(path).exists()) EpubNativeParser.decodeFullResImage(path, maxDimPx = 1080) else null
        }

        // Right Page Content
        if (rightPdfBitmap != null) {
            val scale = minOf(halfW / rightPdfBitmap.width.toFloat(), size.height / rightPdfBitmap.height.toFloat())
            val dstW = rightPdfBitmap.width * scale
            val dstH = rightPdfBitmap.height * scale
            val androidCanvas = drawContext.canvas.nativeCanvas
            val paint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = rightColorFilter
            }
            val srcRect = android.graphics.Rect(0, 0, rightPdfBitmap.width, rightPdfBitmap.height)
            val dstRect = android.graphics.RectF(
                (halfW + (halfW - dstW) / 2),
                ((size.height - dstH) / 2),
                (halfW + (halfW - dstW) / 2) + dstW,
                ((size.height - dstH) / 2) + dstH
            )
            androidCanvas.drawBitmap(rightPdfBitmap, srcRect, dstRect, paint)
        } else if (rightIllustBmp != null) {
            val scale = minOf(colInnerW.toFloat() / rightIllustBmp.width.toFloat(), colInnerH / rightIllustBmp.height.toFloat())
            val dstW = (rightIllustBmp.width * scale).roundToInt().coerceAtLeast(1)
            val dstH = (rightIllustBmp.height * scale).roundToInt().coerceAtLeast(1)
            val dstX = (halfW + colPadHoriz + (colInnerW - dstW) / 2f).roundToInt()
            val dstY = (textTop + (colInnerH - dstH) / 2f).roundToInt()
            drawImage(
                image = rightIllustBmp.asImageBitmap(),
                dstOffset = IntOffset(dstX, dstY),
                dstSize = IntSize(dstW, dstH)
            )
        } else if (rightText.isNotBlank()) {
            val rightLayout = measureFittedSpreadColumn(rightText)
            clipRect(halfW + colPadHoriz, textTop, halfW + colPadHoriz + colInnerW, clipBottom) {
                drawText(rightLayout, topLeft = Offset(halfW + colPadHoriz, textTop))
                if (prefs?.fontWeight == 500) {
                    drawText(
                        textLayoutResult = rightLayout,
                        topLeft = Offset(halfW + colPadHoriz, textTop),
                        color = pageTextStyle.color,
                        drawStyle = Stroke(width = 0.45f * density.density)
                    )
                }
            }
        }
    }
    return bitmap
}
