package com.llunaticsys.llunevie.ui.reader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.llunaticsys.llunevie.LlunevieUiState
import com.llunaticsys.llunevie.LlunevieViewModel
import com.llunaticsys.llunevie.data.local.HighlightNoteEntity
import com.llunaticsys.llunevie.data.local.UserPreferencesState
import com.llunaticsys.llunevie.data.parser.EpubNativeParser
import com.llunaticsys.llunevie.data.parser.PdfNativeRenderer
import com.llunaticsys.llunevie.model.HighlightColorKey
import com.llunaticsys.llunevie.model.isPageTranslationComplete
import com.llunaticsys.llunevie.ui.components.sanitizeBookOrChapterText
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette
import java.io.File
import kotlin.math.roundToInt

/**
 * Renders a single PDF page inside `LazyColumn` for `ReadingFlowMode.SCROLL` and `ReadingFlowMode.WEBTOON`.
 * Uses `PdfNativeRenderer`'s LruCache and cached aspect ratio so vertical PDF scrolling is 100% glitch-free.
 */
@Composable
internal fun PdfVerticalPageItem(
    pdfFilePath: String,
    pageIndex: Int,
    targetWidthPx: Int,
    isWebtoon: Boolean,
    colorFilter: ColorFilter? = null,
    surfaceColor: Color = Color.White,
    overrideBitmap: Bitmap? = null
) {
    val pageBmp by produceState<Bitmap?>(
        initialValue = overrideBitmap ?: PdfNativeRenderer.getCachedBitmap(pdfFilePath, pageIndex),
        pdfFilePath, pageIndex, overrideBitmap
    ) {
        if (overrideBitmap != null) {
            value = overrideBitmap
        } else if (value == null) {
            value = PdfNativeRenderer.renderPageBitmap(pdfFilePath, pageIndex, targetWidthPx)
        }
    }
    val bmp = overrideBitmap ?: pageBmp
    val ratio = if (bmp != null && bmp.height > 0) {
        (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.45f, 1.6f)
    } else {
        PdfNativeRenderer.getCachedPageAspectRatio(pdfFilePath)
    }

    val palette = LocalLluneviePalette.current
    val isDarkSurface = palette.bgApp.red < 0.25f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .then(
                if (isWebtoon) Modifier
                else Modifier
                    .softBookShadow(
                        cornerRadius = 6.dp,
                        spreadRadius = 12.dp,
                        offsetY = 2.dp,
                        isDarkTheme = isDarkSurface
                    )
                    .clip(RoundedCornerShape(6.dp))
                    .border(0.75.dp, palette.borderSoft, RoundedCornerShape(6.dp))
            )
            .background(surfaceColor, if (isWebtoon) RoundedCornerShape(0.dp) else RoundedCornerShape(6.dp))
    ) {
        if (bmp != null && !bmp.isRecycled) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                colorFilter = colorFilter,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(surfaceColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${pageIndex + 1}",
                    color = Color(0xFF94A3B8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
internal fun InteractiveTextPageContent(
    modifier: Modifier = Modifier,
    chapterTitle: String,
    pageText: String,
    currentChapterIndex: Int = 0,
    currentPageInChapter: Int = 0,
    bookId: String = "",
    displayPageIndex: Int = 0,
    showChapterHeader: Boolean,
    scrollable: Boolean,
    prefs: UserPreferencesState,
    bookHighlights: List<HighlightNoteEntity>,
    onParagraphClicked: (String, Boolean) -> Unit,
    onIllustrationClicked: (String) -> Unit,
    onSaveHighlightNote: (String, String, HighlightColorKey, String?, Int, Int) -> Unit,
    onDeleteHighlightNote: (String) -> Unit = {},
    onSearchInBook: (String) -> Unit,
    isCoverPage: Boolean = false,
    bookTitle: String = "",
    fontScale: Float = 1f,
    transparentTextForFlipbook: Boolean = false,
    topClearanceDp: Dp = 0.dp,
    bottomClearanceDp: Dp = 0.dp,
    activeSelectionPageIndex: Int = -1,
    clearSelectionTrigger: Int = 0,
    onSelectionActiveChanged: (Boolean, Offset, Offset) -> Unit = { _, _, _ -> },
    onCenterTap: (() -> Unit)? = null,
    isPageTranslationActive: Boolean = false,
    translatedParagraphs: List<Pair<String, String?>>? = null,
    viewModel: LlunevieViewModel? = null,
    uiState: LlunevieUiState? = null
) {
    val palette = LocalLluneviePalette.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val clipboardManager = LocalClipboardManager.current

    val rawParagraphs = remember(pageText) {
        pageText.split("\n\n")
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("[PDF_PAGE:") }
    }

    val isCover = isCoverPage || (
        (chapterTitle.equals("Sampul", ignoreCase = true) || chapterTitle.equals("Cover", ignoreCase = true)) &&
            rawParagraphs.any { it.startsWith("[ILLUST:") }
        ) || (rawParagraphs.size == 1 && rawParagraphs[0].startsWith("[ILLUST:"))

    // 1. Standalone / Full-Page EPUB Illustration & Cover (1:1 with Flipbook Canvas!)
    if (isCover) {
        val illustPara = rawParagraphs.firstOrNull { it.startsWith("[ILLUST:") }
        val imgPath = illustPara?.removePrefix("[ILLUST:")?.substringBefore("|")?.substringBefore("]")
        if (imgPath != null && File(imgPath).exists()) {
            if (transparentTextForFlipbook) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onIllustrationClicked(imgPath) }
                )
                return
            }
            val coverBitmap = remember(imgPath) {
                EpubNativeParser.decodeFullResImage(imgPath, maxDimPx = 1440)
            }
            if (coverBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onIllustrationClicked(imgPath) },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = bookTitle.ifBlank { "Illustration" },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                return
            }
        }
    }

    val textParagraphs = remember(rawParagraphs) {
        rawParagraphs.filter { !it.startsWith("[ILLUST:") }
    }

    val isTrans = (uiState?.isTranslationActive ?: isPageTranslationActive) &&
        !(uiState?.translationMode ?: prefs.translatorDisplayMode).equals("original", ignoreCase = true)
    val targetLang = uiState?.translationTargetLang ?: prefs.translatorTargetLang
    val transMode = uiState?.translationMode ?: prefs.translatorDisplayMode
    val transRev = uiState?.translationRevision ?: 0

    fun hasValidTranslatedPairs(pairs: List<Pair<String, String?>>?): Boolean {
        if (pairs.isNullOrEmpty()) return false
        return pairs.any { (orig, trans) ->
            !orig.startsWith("[ILLUST:") && !orig.startsWith("[PDF_PAGE:") &&
                !trans.isNullOrBlank() && !trans.trim().equals(orig.trim(), ignoreCase = true)
        }
    }

    var currentTranslatedParas by remember(
        bookId, displayPageIndex, pageText, isTrans, transMode, targetLang, translatedParagraphs, transRev
    ) {
        mutableStateOf(
            if (isTrans && displayPageIndex >= 0) {
                val cached = viewModel?.getCachedTranslatedParagraphs(bookId, displayPageIndex, targetLang, pageText.hashCode())
                when {
                    isPageTranslationComplete(cached) -> cached
                    hasValidTranslatedPairs(cached) -> cached
                    isPageTranslationComplete(translatedParagraphs) -> translatedParagraphs
                    hasValidTranslatedPairs(translatedParagraphs) -> translatedParagraphs
                    else -> null
                }
            } else null
        )
    }

    LaunchedEffect(bookId, displayPageIndex, pageText, isTrans, transMode, targetLang, transRev) {
        if (isTrans && displayPageIndex >= 0 && !pageText.startsWith("[PDF_PAGE:")) {
            val cached = viewModel?.getCachedTranslatedParagraphs(bookId, displayPageIndex, targetLang, pageText.hashCode())
            if (isPageTranslationComplete(cached)) {
                currentTranslatedParas = cached
            } else if (!isPageTranslationComplete(currentTranslatedParas)) {
                if (hasValidTranslatedPairs(cached) && currentTranslatedParas == null) {
                    currentTranslatedParas = cached
                }
                val res = runCatching {
                    withContext(Dispatchers.IO) {
                        if (viewModel != null) {
                            viewModel.loadOrTranslatePageParagraphs(
                                bookId = bookId,
                                pageIndex = displayPageIndex,
                                pageRaw = pageText,
                                targetLang = targetLang
                            )
                        } else {
                            com.llunaticsys.llunevie.data.remote.TranslationRepository.getInstance(context)
                                .translatePageParagraphs(pageText, prefs, context, targetLang)
                                .getOrNull()
                        }
                    }
                }.getOrNull()
                if (hasValidTranslatedPairs(res)) {
                    currentTranslatedParas = res
                }
            }
        }
    }

    val activeTranslatedParagraphs = if (hasValidTranslatedPairs(currentTranslatedParas)) {
        currentTranslatedParas
    } else if (hasValidTranslatedPairs(translatedParagraphs)) {
        translatedParagraphs
    } else {
        null
    }
    val isBilingual = isTrans && transMode.equals("bilingual", ignoreCase = true) && !transparentTextForFlipbook
    val isReplace = isTrans && transMode.equals("replace", ignoreCase = true) && !activeTranslatedParagraphs.isNullOrEmpty()
    val effectiveFontScale = if (isBilingual) 1.0f else fontScale

    val effectiveParagraphs = remember(textParagraphs, isReplace, activeTranslatedParagraphs) {
        if (isReplace && !activeTranslatedParagraphs.isNullOrEmpty()) {
            activeTranslatedParagraphs.map { (orig, trans) ->
                val cleanTrans = trans?.trim()
                if (!cleanTrans.isNullOrBlank()) cleanTrans else orig
            }.filter { !it.startsWith("[ILLUST:") && !it.startsWith("[PDF_PAGE:") }
        } else {
            textParagraphs
        }
    }

    val resolvedTextAlign = when (prefs.textAlign.lowercase()) {
        "justify" -> TextAlign.Justify
        "center" -> TextAlign.Center
        else -> TextAlign.Start
    }
    val resolvedFontFamily = remember(prefs.fontFamily, prefs.fontWeight) {
        getReaderFontFamily(context, prefs.fontFamily, prefs.fontWeight)
    }
    val resolvedFontWeight = when {
        prefs.fontWeight >= 600 -> FontWeight.Bold
        prefs.fontWeight == 500 -> FontWeight.Medium
        else -> FontWeight.Normal
    }

    val pageHighlights = remember(bookHighlights, currentChapterIndex, currentPageInChapter, displayPageIndex, pageText) {
        val clean = sanitizeBookOrChapterText(pageText)
        bookHighlights.filter { hl ->
            (hl.chapterIndex == currentChapterIndex && hl.pageIndex == currentPageInChapter) ||
                (displayPageIndex >= 0 && hl.pageIndex == displayPageIndex) ||
                (hl.quoteText.isNotBlank() && clean.contains(hl.quoteText.trim(), ignoreCase = true))
        }
    }

    val annotatedString = remember(
        effectiveParagraphs, prefs.paraSpacingEm, prefs.wordSpacingPx, prefs.paraIndent,
        prefs.pageMargin, prefs.letterSpacingEm, prefs.lineHeightMultiplier, prefs.fontSizeSp,
        effectiveFontScale, prefs.fontWeight, prefs.textAlign, prefs.fontFamily, pageHighlights,
        currentChapterIndex, currentPageInChapter, displayPageIndex, palette
    ) {
        buildEpubPageAnnotatedString(
            textParagraphs = effectiveParagraphs,
            prefs = prefs,
            fontScale = effectiveFontScale,
            resolvedTextAlign = resolvedTextAlign,
            bookHighlights = pageHighlights,
            currentChapterIndex = currentChapterIndex,
            currentPageInChapter = currentPageInChapter,
            displayPageIndex = displayPageIndex,
            palette = palette
        )
    }

    val fullPageText = annotatedString.text

    var selStart by remember(pageText) { mutableIntStateOf(-1) }
    var selEnd by remember(pageText) { mutableIntStateOf(-1) }
    var activeDraggingHandle by remember { mutableStateOf<Int?>(null) } // 0 = start handle, 1 = end handle
    var selectedHighlightId by remember(pageText) { mutableStateOf<String?>(null) }

    LaunchedEffect(clearSelectionTrigger) {
        if (clearSelectionTrigger > 0) {
            selStart = -1
            selEnd = -1
            selectedHighlightId = null
        }
    }

    LaunchedEffect(activeSelectionPageIndex) {
        if (activeSelectionPageIndex != -1 && activeSelectionPageIndex != displayPageIndex) {
            if (selStart != -1 || selEnd != -1 || selectedHighlightId != null) {
                selStart = -1
                selEnd = -1
                selectedHighlightId = null
            }
        }
    }
    val hasActiveSelection = selStart >= 0 && selEnd > selStart && selEnd <= fullPageText.length
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val pageTextStyle = TextStyle(
        color = if (transparentTextForFlipbook) Color.Transparent else palette.textMain,
        fontSize = (prefs.fontSizeSp * effectiveFontScale).sp,
        letterSpacing = (prefs.letterSpacingEm * prefs.fontSizeSp * effectiveFontScale).sp,
        fontFamily = resolvedFontFamily,
        fontWeight = resolvedFontWeight,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    val layout = textLayoutResult
    val horizontalMarginDp = when (prefs.pageMargin) {
        0 -> 12.dp
        2 -> 36.dp
        else -> 22.dp
    }
    val verticalMarginDp = when (prefs.pageMargin) {
        0 -> 12.dp
        2 -> 26.dp
        else -> 18.dp
    }

    val topPadDp = if (topClearanceDp > 0.dp) maxOf(topClearanceDp, verticalMarginDp) else verticalMarginDp
    val bottomPadDp = if (bottomClearanceDp > 0.dp) maxOf(bottomClearanceDp, verticalMarginDp + 4.dp) else verticalMarginDp + 4.dp

    val padX = with(density) { horizontalMarginDp.toPx() }
    val padTop = with(density) { topPadDp.toPx() }

    LaunchedEffect(hasActiveSelection, layout, selStart, selEnd) {
        if (hasActiveSelection && layout != null && fullPageText.isNotEmpty()) {
            val safeStart = selStart.coerceIn(0, fullPageText.lastIndex)
            val safeEnd = (selEnd - 1).coerceIn(safeStart, fullPageText.lastIndex)
            val sRect = layout.getCursorRect(safeStart)
            val eRect = layout.getCursorRect((safeEnd + 1).coerceAtMost(fullPageText.length))
            val hRadius = with(density) { 7.dp.toPx() }
            val startAnchor = Offset(sRect.left + padX, sRect.bottom + padTop + hRadius)
            val endAnchor = Offset(eRect.left + padX, eRect.bottom + padTop + hRadius)
            onSelectionActiveChanged(true, startAnchor, endAnchor)
        } else {
            onSelectionActiveChanged(false, Offset.Zero, Offset.Zero)
        }
    }

    val effectiveScrollable = scrollable
    val outerScrollModifier = if (effectiveScrollable) {
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    } else {
        Modifier
            .fillMaxWidth()
    }

    val currentOnCenterTap by rememberUpdatedState(onCenterTap)

    Box(
        modifier = modifier
            .then(outerScrollModifier)
            .background(if (transparentTextForFlipbook) Color.Transparent else palette.bgApp)
            .padding(start = horizontalMarginDp, end = horizontalMarginDp, top = topPadDp, bottom = bottomPadDp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            if (showChapterHeader && chapterTitle.isNotBlank()) {
                Text(
                    text = chapterTitle,
                    color = if (transparentTextForFlipbook) Color.Transparent else palette.textMain,
                    fontSize = (prefs.fontSizeSp + 6).sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )
            }

            if (!transparentTextForFlipbook) {
                rawParagraphs.filter { it.startsWith("[ILLUST:") && it.endsWith("]") }.forEach { para ->
                    val inner = para.removePrefix("[ILLUST:").removeSuffix("]")
                    val path = inner.substringBefore("|")
                    val caption = inner.substringAfter("|", "")
                    Surface(
                        onClick = { onIllustrationClicked(path) },
                        shape = RoundedCornerShape(16.dp),
                        color = palette.bgCard,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 14.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AsyncImage(
                                model = File(path),
                                contentDescription = caption,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp)
                            )
                            if (caption.isNotBlank()) {
                                Text(
                                    text = caption,
                                    color = palette.textMuted,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }

            val currentTextLayout by rememberUpdatedState(textLayoutResult)
            val currentFullText by rememberUpdatedState(fullPageText)
            val currentPageHighlights by rememberUpdatedState(pageHighlights)

            Box(modifier = Modifier.fillMaxWidth()) {
                if (isBilingual) {
                    val bilingualPairs = activeTranslatedParagraphs ?: textParagraphs.map { it to null }
                    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                        bilingualPairs.forEach { (origPara, transPara) ->
                            val cleanOrig = sanitizeBookOrChapterText(origPara).trim()
                            val cleanTrans = transPara?.let { sanitizeBookOrChapterText(it).trim() }
                            if (cleanOrig.isNotBlank() && !cleanOrig.startsWith("[ILLUST:") && !cleanOrig.startsWith("[PDF_PAGE:")) {
                                Text(
                                    text = cleanOrig,
                                    fontFamily = resolvedFontFamily,
                                    fontSize = prefs.fontSizeSp.sp,
                                    lineHeight = (prefs.fontSizeSp * prefs.lineHeightMultiplier).sp,
                                    textAlign = resolvedTextAlign,
                                    color = palette.textMain,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onParagraphClicked(cleanOrig, false) }
                                )
                                val hasDistinctTranslation = !cleanTrans.isNullOrBlank() &&
                                    !cleanTrans.equals(cleanOrig, ignoreCase = true)
                                if (hasDistinctTranslation) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 2.dp, bottom = (prefs.paraSpacingEm * 6f).dp),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 4.dp)
                                                .width(2.dp)
                                                .heightIn(min = 16.dp)
                                                .background(palette.accent.copy(alpha = 0.55f), RoundedCornerShape(1.dp))
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = cleanTrans!!,
                                            fontFamily = resolvedFontFamily,
                                            fontSize = (prefs.fontSizeSp - 1).coerceAtLeast(13).sp,
                                            lineHeight = ((prefs.fontSizeSp - 1).coerceAtLeast(13) * prefs.lineHeightMultiplier * 0.95f).sp,
                                            textAlign = resolvedTextAlign,
                                            color = palette.textMuted.copy(alpha = 0.92f),
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                } else {
                                    Spacer(modifier = Modifier.height((prefs.paraSpacingEm * 6f).dp))
                                }
                            }
                        }
                    }
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val padBottomPx = with(density) { bottomPadDp.toPx() }
                        val availHeightPx = (with(density) { maxHeight.toPx() } - padBottomPx).coerceAtLeast(100f)
                        Text(
                            text = annotatedString,
                            style = pageTextStyle,
                            onTextLayout = { textLayoutResult = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawWithContent {
                                    val l = textLayoutResult
                                    if (!transparentTextForFlipbook && l != null) {
                                        pageHighlights.forEach { hl ->
                                            val colorKey = HighlightColorKey.fromKey(hl.colorKey)
                                            val pastelBg = palette.highlightBg(colorKey)
                                            val hlStart = if (hl.startCharIndex in 0 until fullPageText.length &&
                                                hl.endCharIndex in (hl.startCharIndex + 1)..fullPageText.length
                                            ) {
                                                hl.startCharIndex
                                            } else {
                                                val q = hl.quoteText.trim()
                                                if (q.isNotBlank()) fullPageText.indexOf(q, ignoreCase = true) else -1
                                            }
                                            val hlEnd = if (hl.startCharIndex in 0 until fullPageText.length &&
                                                hl.endCharIndex in (hl.startCharIndex + 1)..fullPageText.length
                                            ) {
                                                hl.endCharIndex
                                            } else if (hlStart >= 0) {
                                                hlStart + hl.quoteText.trim().length
                                            } else -1

                                            if (hlStart in 0 until fullPageText.length && hlEnd in (hlStart + 1)..fullPageText.length) {
                                                val sLine = l.getLineForOffset(hlStart)
                                                val eLine = l.getLineForOffset(hlEnd)
                                                val sX = l.getHorizontalPosition(hlStart, usePrimaryDirection = true)
                                                val eX = l.getHorizontalPosition(hlEnd, usePrimaryDirection = true)

                                                for (line in sLine..eLine) {
                                                    val lineLeft = if (line == sLine) sX else l.getLineLeft(line)
                                                    val lineRight = if (line == eLine) eX else l.getLineRight(line)
                                                    val rawTop = l.getLineTop(line)
                                                    val rawBottom = l.getLineBottom(line)
                                                    val lineH = rawBottom - rawTop
                                                    val tightTop = rawTop + lineH * 0.06f
                                                    val tightBottom = rawBottom - lineH * 0.14f
                                                    if (lineRight > lineLeft) {
                                                        drawRoundRect(
                                                            color = pastelBg,
                                                            topLeft = Offset(lineLeft, tightTop),
                                                            size = Size(lineRight - lineLeft, (tightBottom - tightTop).coerceAtLeast(10f)),
                                                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                                        )
                                                    }
                                                }
                                            }
                                        }

                                        if (availHeightPx > 0f && l.size.height > availHeightPx) {
                                            val lastCompleteLine = (0 until l.lineCount)
                                                .lastOrNull { l.getLineBottom(it) <= availHeightPx + 2f }
                                                ?: (l.lineCount - 1)
                                            val safeClipBottom = l.getLineBottom(lastCompleteLine)
                                            clipRect(0f, 0f, size.width, safeClipBottom) {
                                                this@drawWithContent.drawContent()
                                                if (prefs.fontWeight == 500) {
                                                    drawText(
                                                        textLayoutResult = l,
                                                        drawStyle = Stroke(width = 0.45f * density.density)
                                                    )
                                                }
                                            }
                                        } else {
                                            drawContent()
                                            if (prefs.fontWeight == 500) {
                                                drawText(
                                                    textLayoutResult = l,
                                                    drawStyle = Stroke(width = 0.45f * density.density)
                                                )
                                            }
                                        }
                                    }

                                    if (hasActiveSelection && l != null && fullPageText.isNotEmpty()) {
                                        val safeStart = selStart.coerceIn(0, fullPageText.lastIndex)
                                        val safeEnd = (selEnd - 1).coerceIn(safeStart, fullPageText.lastIndex)
                                        val startLine = l.getLineForOffset(safeStart)
                                        val endLine = l.getLineForOffset(safeEnd)
                                        val startX = l.getHorizontalPosition(safeStart, usePrimaryDirection = true)
                                        val endX = l.getHorizontalPosition(safeEnd + 1, usePrimaryDirection = true)

                                        for (line in startLine..endLine) {
                                            val lineLeft = if (line == startLine) startX else l.getLineLeft(line)
                                            val lineRight = if (line == endLine) endX else l.getLineRight(line)
                                            val rawTop = l.getLineTop(line)
                                            val rawBottom = l.getLineBottom(line)
                                            val lineH = rawBottom - rawTop
                                            val tightTop = rawTop + lineH * 0.06f
                                            val tightBottom = rawBottom - lineH * 0.14f
                                            if (lineRight > lineLeft) {
                                                drawRoundRect(
                                                    color = Color(0xFF38BDF8).copy(alpha = 0.28f),
                                                    topLeft = Offset(lineLeft, tightTop),
                                                    size = Size(lineRight - lineLeft, (tightBottom - tightTop).coerceAtLeast(10f)),
                                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                                )
                                            } else if (line > startLine && line < endLine) {
                                                val prevLeft = l.getLineLeft(line - 1).coerceAtLeast(0f)
                                                val prevRight = l.getLineRight(line - 1)
                                                val fillRight = if (prevRight > prevLeft) prevRight else size.width
                                                drawRoundRect(
                                                    color = Color(0xFF38BDF8).copy(alpha = 0.28f),
                                                    topLeft = Offset(prevLeft, tightTop),
                                                    size = Size((fillRight - prevLeft).coerceAtLeast(10f), (tightBottom - tightTop).coerceAtLeast(10f)),
                                                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                                )
                                            }
                                        }

                                        val barColor = Color(0xFF0284C7)
                                        val barStroke = 2.dp.toPx()
                                        val handleRadius = 7.dp.toPx()

                                        val startRawTop = l.getLineTop(startLine)
                                        val startRawBottom = l.getLineBottom(startLine)
                                        val startLineH = startRawBottom - startRawTop
                                        val startTightTop = startRawTop + startLineH * 0.06f
                                        val startTightBottom = startRawBottom - startLineH * 0.14f

                                        drawLine(
                                            color = barColor,
                                            start = Offset(startX, startTightTop),
                                            end = Offset(startX, startTightBottom),
                                            strokeWidth = barStroke
                                        )
                                        drawCircle(
                                            color = barColor,
                                            radius = handleRadius,
                                            center = Offset(startX, startTightBottom + handleRadius)
                                        )

                                        val endRawTop = l.getLineTop(endLine)
                                        val endRawBottom = l.getLineBottom(endLine)
                                        val endLineH = endRawBottom - endRawTop
                                        val endTightTop = endRawTop + endLineH * 0.06f
                                        val endTightBottom = endRawBottom - endLineH * 0.14f

                                        drawLine(
                                            color = barColor,
                                            start = Offset(endX, endTightTop),
                                            end = Offset(endX, endTightBottom),
                                            strokeWidth = barStroke
                                        )
                                        drawCircle(
                                            color = barColor,
                                            radius = handleRadius,
                                            center = Offset(endX, endTightBottom + handleRadius)
                                        )
                                    }
                                }
                                .pointerInput(bookId, displayPageIndex) {
                                    awaitEachGesture {
                                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                        val l = currentTextLayout ?: return@awaitEachGesture
                                        val txt = currentFullText
                                        if (txt.isEmpty()) return@awaitEachGesture

                                        val hitRadius = 36.dp.toPx()
                                        val curStart = selStart
                                        val curEnd = selEnd
                                        val hasSelection = curStart >= 0 && curEnd > curStart && curEnd <= txt.length

                                        if (hasSelection) {
                                            val safeStart = curStart.coerceIn(0, txt.lastIndex)
                                            val safeEnd = (curEnd - 1).coerceIn(safeStart, txt.lastIndex)
                                            val startLine = l.getLineForOffset(safeStart)
                                            val endLine = l.getLineForOffset(safeEnd)
                                            val startX = l.getHorizontalPosition(safeStart, usePrimaryDirection = true)
                                            val endX = l.getHorizontalPosition(safeEnd + 1, usePrimaryDirection = true)
                                            val handleRadius = 7.dp.toPx()
                                            val startTightBottom = l.getLineBottom(startLine) - (l.getLineBottom(startLine) - l.getLineTop(startLine)) * 0.14f
                                            val endTightBottom = l.getLineBottom(endLine) - (l.getLineBottom(endLine) - l.getLineTop(endLine)) * 0.14f

                                            val startHandleCenter = Offset(startX, startTightBottom + handleRadius)
                                            val endHandleCenter = Offset(endX, endTightBottom + handleRadius)

                                            val distStart = (down.position - startHandleCenter).getDistance()
                                            val distEnd = (down.position - endHandleCenter).getDistance()

                                            if (distStart <= hitRadius || distEnd <= hitRadius) {
                                                down.consume()
                                                val draggingStart = distStart <= distEnd
                                                activeDraggingHandle = if (draggingStart) 0 else 1

                                                while (true) {
                                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                                    if (!change.pressed) {
                                                        change.consume()
                                                        break
                                                    }
                                                    change.consume()
                                                    val targetOffset = l.getOffsetForPosition(change.position).coerceIn(0, txt.length)
                                                    if (draggingStart) {
                                                        selStart = targetOffset.coerceIn(0, (selEnd - 1).coerceAtLeast(0))
                                                    } else {
                                                        selEnd = targetOffset.coerceIn(selStart + 1, txt.length)
                                                    }
                                                }
                                                activeDraggingHandle = null
                                                return@awaitEachGesture
                                            }
                                        }

                                        val touchSlop = viewConfiguration.touchSlop
                                        val longPressTimeout = 400L
                                        val startTime = System.currentTimeMillis()
                                        var isLongPressTriggered = false
                                        var initialWordStart = -1
                                        var initialWordEnd = -1

                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break

                                            if (event.changes.count { it.pressed } >= 2) {
                                                break
                                            }

                                            if (!change.pressed) {
                                                if (isLongPressTriggered) {
                                                    change.consume()
                                                } else {
                                                    val elapsed = System.currentTimeMillis() - startTime
                                                    val dx = change.position.x - down.position.x
                                                    val dy = change.position.y - down.position.y
                                                    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
                                                    if (elapsed < longPressTimeout && dist <= touchSlop) {
                                                        if (hasSelection) {
                                                            change.consume()
                                                            selStart = -1
                                                            selEnd = -1
                                                            selectedHighlightId = null
                                                        } else if (activeSelectionPageIndex != -1 && activeSelectionPageIndex != displayPageIndex) {
                                                            change.consume()
                                                            currentOnCenterTap?.invoke()
                                                        } else {
                                                            val tapOffset = l.getOffsetForPosition(change.position).coerceIn(0, txt.length)
                                                            val matchedHl = currentPageHighlights.find { hl ->
                                                                val hlS = if (hl.startCharIndex in 0 until txt.length && hl.endCharIndex in (hl.startCharIndex + 1)..txt.length) {
                                                                    hl.startCharIndex
                                                                } else {
                                                                    val q = hl.quoteText.trim()
                                                                    if (q.isNotBlank()) txt.indexOf(q, ignoreCase = true) else -1
                                                                }
                                                                val hlE = if (hl.startCharIndex in 0 until txt.length && hl.endCharIndex in (hl.startCharIndex + 1)..txt.length) {
                                                                    hl.endCharIndex
                                                                } else if (hlS >= 0) {
                                                                    hlS + hl.quoteText.trim().length
                                                                } else -1

                                                                hlS >= 0 && hlE > hlS && tapOffset in hlS until hlE
                                                            }
                                                            if (matchedHl != null) {
                                                                val hlS = if (matchedHl.startCharIndex in 0 until txt.length && matchedHl.endCharIndex in (matchedHl.startCharIndex + 1)..txt.length) {
                                                                    matchedHl.startCharIndex
                                                                } else {
                                                                    val q = matchedHl.quoteText.trim()
                                                                    if (q.isNotBlank()) txt.indexOf(q, ignoreCase = true) else -1
                                                                }
                                                                val hlE = if (matchedHl.startCharIndex in 0 until txt.length && matchedHl.endCharIndex in (matchedHl.startCharIndex + 1)..txt.length) {
                                                                    matchedHl.endCharIndex
                                                                } else if (hlS >= 0) {
                                                                    hlS + matchedHl.quoteText.trim().length
                                                                } else -1

                                                                if (hlS >= 0 && hlE > hlS) {
                                                                    change.consume()
                                                                    selStart = hlS
                                                                    selEnd = hlE
                                                                    selectedHighlightId = matchedHl.id
                                                                    val sRect = l.getCursorRect(hlS.coerceIn(0, txt.lastIndex))
                                                                    val eRect = l.getCursorRect((hlE - 1).coerceIn(hlS, txt.lastIndex) + 1)
                                                                    val hRadius = with(density) { 7.dp.toPx() }
                                                                    val startAnchor = Offset(sRect.left + padX, sRect.bottom + padTop + hRadius)
                                                                    val endAnchor = Offset(eRect.left + padX, eRect.bottom + padTop + hRadius)
                                                                    onSelectionActiveChanged(true, startAnchor, endAnchor)
                                                                }
                                                            } else if (currentOnCenterTap != null) {
                                                                val relX = change.position.x / size.width.toFloat().coerceAtLeast(1f)
                                                                if (relX in 0.24f..0.76f) {
                                                                    change.consume()
                                                                    currentOnCenterTap?.invoke()
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                break
                                            }

                                            val dx = change.position.x - down.position.x
                                            val dy = change.position.y - down.position.y
                                            val dist = kotlin.math.sqrt(dx * dx + dy * dy)

                                            if (!isLongPressTriggered) {
                                                if (dist > touchSlop || change.isConsumed) {
                                                    break
                                                }

                                                val elapsed = System.currentTimeMillis() - startTime
                                                if (elapsed >= longPressTimeout) {
                                                    isLongPressTriggered = true
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    change.consume()

                                                    val rawOffset = l.getOffsetForPosition(down.position).coerceIn(0, txt.length)
                                                    var wStart = rawOffset
                                                    while (wStart > 0 && !txt[wStart - 1].isWhitespace() && txt[wStart - 1] !in setOf('.', ',', ';', ':', '!', '?', '"', '\'', '—', '–', '(', ')')) {
                                                        wStart--
                                                    }
                                                    var wEnd = rawOffset
                                                    while (wEnd < txt.length && !txt[wEnd].isWhitespace() && txt[wEnd] !in setOf('.', ',', ';', ':', '!', '?', '"', '\'', '—', '–', '(', ')')) {
                                                        wEnd++
                                                    }
                                                    if (wEnd <= wStart) {
                                                        wEnd = (wStart + 1).coerceAtMost(txt.length)
                                                    }
                                                    initialWordStart = wStart
                                                    initialWordEnd = wEnd
                                                    selStart = wStart
                                                    selEnd = wEnd
                                                    selectedHighlightId = null
                                                    val sRect = l.getCursorRect(wStart.coerceIn(0, txt.lastIndex))
                                                    val eRect = l.getCursorRect((wEnd - 1).coerceIn(wStart, txt.lastIndex) + 1)
                                                    val hRadius = with(density) { 7.dp.toPx() }
                                                    val startAnchor = Offset(sRect.left + padX, sRect.bottom + padTop + hRadius)
                                                    val endAnchor = Offset(eRect.left + padX, eRect.bottom + padTop + hRadius)
                                                    onSelectionActiveChanged(true, startAnchor, endAnchor)
                                                }
                                            } else {
                                                change.consume()
                                                val currentOffset = l.getOffsetForPosition(change.position).coerceIn(0, txt.length)
                                                if (currentOffset >= initialWordStart) {
                                                    selStart = initialWordStart
                                                    selEnd = currentOffset.coerceIn(initialWordEnd, txt.length)
                                                } else {
                                                    selStart = currentOffset.coerceIn(0, initialWordStart)
                                                    selEnd = initialWordEnd
                                                }
                                            }
                                        }
                                    }
                                }
                        )
                    }
                }
            }
        }

        // 5. Floating Highlight & Note Popover (#highlightPopover)
        val popoverVisible = hasActiveSelection && activeDraggingHandle == null && layout != null
        val popoverAlpha by animateFloatAsState(
            targetValue = if (popoverVisible) 1f else 0f,
            animationSpec = tween(160, easing = FastOutSlowInEasing),
            label = "popoverAlpha"
        )
        val popoverScale by animateFloatAsState(
            targetValue = if (popoverVisible) 1f else 0.88f,
            animationSpec = tween(160, easing = FastOutSlowInEasing),
            label = "popoverScale"
        )

        if (popoverAlpha > 0.01f && layout != null && selStart >= 0 && selEnd > selStart) {
            val selectedText = fullPageText.substring(
                selStart.coerceIn(0, fullPageText.length),
                selEnd.coerceIn(0, fullPageText.length)
            ).trim()
            val sStart = selStart.coerceIn(0, fullPageText.lastIndex)
            val sEnd = (selEnd - 1).coerceIn(sStart, fullPageText.lastIndex)
            val sLine = layout.getLineForOffset(sStart)
            val eLine = layout.getLineForOffset(sEnd)
            val sTop = layout.getLineTop(sLine)
            val eBottom = layout.getLineBottom(eLine)
            val popoverY = if (sTop < with(density) { 80.dp.toPx() }) {
                (eBottom + with(density) { 12.dp.toPx() }).roundToInt()
            } else {
                with(density) { (sTop - 64.dp.toPx()).roundToInt().coerceAtLeast(0) }
            }

            Popup(
                alignment = Alignment.TopCenter,
                offset = IntOffset(0, popoverY),
                properties = PopupProperties(focusable = false, clippingEnabled = false)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = palette.bgSurface,
                    border = BorderStroke(1.dp, palette.borderSoft),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .graphicsLayer {
                            alpha = popoverAlpha
                            scaleX = popoverScale
                            scaleY = popoverScale
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val pastelSwatches = listOf(
                            HighlightColorKey.GOLD to Color(0xFFFEF3C7),
                            HighlightColorKey.SKY to Color(0xFFDBEAFE),
                            HighlightColorKey.PEACH to Color(0xFFFCE7F3),
                            HighlightColorKey.MINT to Color(0xFFDCFCE7)
                        )
                        pastelSwatches.forEach { (key, col) ->
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(1.dp, palette.borderSoft, CircleShape)
                                    .clickable {
                                        onSaveHighlightNote(selectedText, "", key, selectedHighlightId, selStart, selEnd)
                                        selStart = -1
                                        selEnd = -1
                                        selectedHighlightId = null
                                    }
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(16.dp)
                                .background(palette.borderSoft)
                        )

                        Surface(
                            onClick = {
                                onParagraphClicked(selectedText, sTop <= 200f)
                                selStart = -1
                                selEnd = -1
                                selectedHighlightId = null
                            },
                            shape = CircleShape,
                            color = Color.Transparent
                        ) {
                            Text(
                                text = if (prefs.lang == "en") "+ Note" else "+ Catatan",
                                color = palette.textMain,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }

                        Surface(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(selectedText))
                                selStart = -1
                                selEnd = -1
                                selectedHighlightId = null
                            },
                            shape = CircleShape,
                            color = Color.Transparent
                        ) {
                            Text(
                                text = if (prefs.lang == "en") "Copy" else "Salin",
                                color = palette.textMain,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }

                        if (selectedHighlightId != null) {
                            Surface(
                                onClick = {
                                    onDeleteHighlightNote(selectedHighlightId!!)
                                    selStart = -1
                                    selEnd = -1
                                    selectedHighlightId = null
                                },
                                shape = CircleShape,
                                color = Color.Transparent
                            ) {
                                Text(
                                    text = if (prefs.lang == "en") "Delete" else "Hapus",
                                    color = Color(0xFFEF4444),
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfPageItemContent(
    bookId: String,
    filePath: String,
    pageIndex: Int,
    viewModel: LlunevieViewModel,
    modifier: Modifier = Modifier,
    colorFilter: ColorFilter? = null,
    surfaceColor: Color = Color.White,
    overrideBitmap: Bitmap? = null,
    uiState: LlunevieUiState = viewModel.uiState.collectAsState().value
) {
    val palette = LocalLluneviePalette.current
    val isTrans = uiState.isTranslationActive && !uiState.translationMode.equals("original", ignoreCase = true)
    val targetLang = uiState.translationTargetLang
    val transMode = uiState.translationMode

    var pageBitmap by remember(bookId, pageIndex, isTrans, targetLang, transMode, uiState.translationRevision, overrideBitmap) {
        mutableStateOf(
            if (overrideBitmap != null) {
                overrideBitmap
            } else if (isTrans) {
                viewModel.getCachedTranslatedPdfBitmap(bookId, pageIndex, targetLang, transMode)
            } else {
                viewModel.getCachedPdfBitmap(bookId, pageIndex, filePath)
            }
        )
    }

    LaunchedEffect(bookId, filePath, pageIndex, isTrans, targetLang, transMode, uiState.translationRevision, overrideBitmap) {
        if (overrideBitmap != null) {
            pageBitmap = overrideBitmap
            return@LaunchedEffect
        }
        if (isTrans) {
            val cached = viewModel.getCachedTranslatedPdfBitmap(bookId, pageIndex, targetLang, transMode)
            if (cached != null) {
                pageBitmap = cached
            } else {
                if (pageBitmap == null) {
                    pageBitmap = viewModel.getCachedPdfBitmap(bookId, pageIndex, filePath)
                        ?: viewModel.loadPdfPageBitmap(bookId, filePath, pageIndex)
                }
                val translated = viewModel.loadOrTranslatePdfPageBitmap(
                    bookId = bookId,
                    filePath = filePath,
                    pageIndex = pageIndex,
                    targetLang = targetLang,
                    mode = transMode
                )
                if (translated != null) {
                    pageBitmap = translated
                }
            }
        } else {
            pageBitmap = viewModel.getCachedPdfBitmap(bookId, pageIndex, filePath)
                ?: viewModel.loadPdfPageBitmap(bookId, filePath, pageIndex)
            viewModel.prefetchPdfNeighbors(bookId, filePath, pageIndex)
        }
    }
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bgApp)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        val bmp = overrideBitmap ?: pageBitmap
        val imageBitmap = remember(bmp) { bmp?.asImageBitmap() }
        if (imageBitmap != null) {
            val ratio = if (bmp != null && bmp.height > 0) (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.45f, 1.6f) else 0.707f
            val isDarkSurface = palette.bgApp.red < 0.25f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(ratio, matchHeightConstraintsFirst = false)
                    .softBookShadow(
                        cornerRadius = 6.dp,
                        spreadRadius = 16.dp,
                        offsetY = 2.dp,
                        isDarkTheme = isDarkSurface
                    )
                    .clip(RoundedCornerShape(6.dp))
                    .background(surfaceColor, RoundedCornerShape(6.dp))
                    .border(0.75.dp, palette.borderSoft, RoundedCornerShape(6.dp))
            ) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
