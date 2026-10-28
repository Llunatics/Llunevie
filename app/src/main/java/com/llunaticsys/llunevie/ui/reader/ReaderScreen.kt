package com.llunaticsys.llunevie.ui.reader

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font
import com.llunaticsys.llunevie.R
import com.llunaticsys.llunevie.ui.components.ClassicGlassSegmentedControl
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llunaticsys.llunevie.GlobalPageMetrics
import com.llunaticsys.llunevie.LlunevieUiState
import com.llunaticsys.llunevie.data.local.BookEntity
import com.llunaticsys.llunevie.data.local.HighlightNoteEntity
import com.llunaticsys.llunevie.data.local.UserPreferencesState
import com.llunaticsys.llunevie.data.parser.PdfNativeRenderer
import com.llunaticsys.llunevie.model.AppThemeMode
import com.llunaticsys.llunevie.model.HighlightColorKey
import com.llunaticsys.llunevie.model.PageAnimStyle
import com.llunaticsys.llunevie.model.ReadingFlowMode
import com.llunaticsys.llunevie.ui.components.FlyleafThinSlider
import com.llunaticsys.llunevie.ui.components.PrototypeIconType
import com.llunaticsys.llunevie.ui.components.PrototypeSvgIcon
import com.llunaticsys.llunevie.ui.theme.LlunevieStrings
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette
import coil.compose.AsyncImage
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

private val SANS_FONTS = setOf("Plus Jakarta Sans", "Inter", "DM Sans", "Nunito")

private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

private val fontFamiliesCache = mutableMapOf<String, FontFamily>()

fun getReaderFontFamily(name: String): FontFamily {
    return fontFamiliesCache.getOrPut(name) {
        val googleFontName = when (name) {
            "Literata" -> "Literata"
            "EB Garamond" -> "EB Garamond"
            "Source Serif 4" -> "Source Serif 4"
            "Newsreader" -> "Newsreader"
            "Lora" -> "Lora"
            "Merriweather" -> "Merriweather"
            "Crimson Pro" -> "Crimson Pro"
            "Playfair Display" -> "Playfair Display"
            "PT Serif" -> "PT Serif"
            "Bitter" -> "Bitter"
            "Plus Jakarta Sans" -> "Plus Jakarta Sans"
            "Inter" -> "Inter"
            "DM Sans" -> "DM Sans"
            "Nunito" -> "Nunito"
            "JetBrains Mono" -> "JetBrains Mono"
            else -> null
        }
        val fallback = when {
            name == "JetBrains Mono" -> FontFamily.Monospace
            SANS_FONTS.contains(name) -> FontFamily.SansSerif
            else -> FontFamily.Serif
        }
        if (googleFontName != null) {
            val gf = GoogleFont(googleFontName)
            FontFamily(
                Font(googleFont = gf, fontProvider = googleFontProvider, weight = FontWeight.Normal),
                Font(googleFont = gf, fontProvider = googleFontProvider, weight = FontWeight.Medium),
                Font(googleFont = gf, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
                Font(googleFont = gf, fontProvider = googleFontProvider, weight = FontWeight.Bold)
            )
        } else {
            fallback
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    book: BookEntity,
    uiState: LlunevieUiState,
    prefs: UserPreferencesState,
    bookHighlights: List<HighlightNoteEntity>,
    strings: LlunevieStrings,
    metrics: GlobalPageMetrics,
    getPageAtGlobalZero: (Int) -> Triple<Int, Int, String>?,
    onBack: () -> Unit,
    onTurnPage: (Int) -> Unit,
    onJumpToChapterAndPage: (Int, Int) -> Unit,
    onToggleFullView: () -> Unit,
    onToggleTypeSheet: (Boolean, String?) -> Unit,
    onToggleTocDrawer: (Boolean) -> Unit,
    onToggleNotesDrawer: (Boolean) -> Unit,
    onOpenIllustViewer: (Int) -> Unit,
    onSetIllustZoom: (Float) -> Unit,
    onCloseIllustViewer: () -> Unit,
    onSaveHighlightNote: (String, String, HighlightColorKey, String?) -> Unit,
    onDeleteHighlightNote: (String) -> Unit,
    onSearchInLibrary: (String) -> Unit,
    onSetTheme: (AppThemeMode) -> Unit,
    onSetReadingFlowMode: (ReadingFlowMode) -> Unit,
    onSetPageAnimStyle: (PageAnimStyle) -> Unit,
    onUpdateTypography: (
        fontFamily: String?,
        fontSizeSp: Int?,
        fontWeight: Int?,
        textAlign: String?,
        lineHeight: Float?,
        paraSpacing: Float?,
        letterSpacing: Float?,
        wordSpacing: Int?,
        pageMargin: Int?,
        paraIndent: Boolean?
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLluneviePalette.current
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current

    val chapters = uiState.activeBookChapters
    val currentChapter = chapters.getOrNull(uiState.activeChapterIndex)
    val currentPageRaw = currentChapter?.pages?.getOrNull(uiState.activePageIndex).orEmpty()

    var externalFlipTrigger by remember { mutableIntStateOf(0) }
    var selectedQuoteForPopover by remember { mutableStateOf<String?>(null) }
    var selectedPopoverInUpperHalf by remember { mutableStateOf(true) }
    var showInlineNoteInput by remember { mutableStateOf(false) }
    var inlineNoteText by remember { mutableStateOf("") }
    var showPageScrubber by remember { mutableStateOf(false) }
    var scrubberPageValue by remember { mutableFloatStateOf(metrics.currentGlobalPage.toFloat()) }
    var isFullViewBtnDocked by remember { mutableStateOf(false) }
    var fullViewBtnLastActive by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var inBookSearchQuery by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(fullViewBtnLastActive, uiState.readerFullView) {
        kotlinx.coroutines.delay(3500L)
        isFullViewBtnDocked = true
    }

    LaunchedEffect(metrics.currentGlobalPage) {
        scrubberPageValue = metrics.currentGlobalPage.toFloat()
    }

    val isSpread = prefs.readingFlowMode == ReadingFlowMode.SPREAD
    val isPdfBook = (book.format.equals("PDF", ignoreCase = true) || currentPageRaw.startsWith("[PDF_PAGE:")) &&
        !book.localFilePath.isNullOrBlank()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bgApp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // 1. TOP CHROME BAR (`#readerTopChrome` in `llunevie_prototype.html`)
            if (!uiState.readerFullView) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 10.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = onBack,
                        shape = CircleShape,
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PrototypeSvgIcon(
                                type = PrototypeIconType.ARROW_LEFT,
                                tint = palette.textMain,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Compact single-line Title Pill (`#readerTitlePill`: max-w-[210px] px-4 py-1.5 rounded-full text-[12px])
                    Surface(
                        onClick = { onToggleTocDrawer(true) },
                        shape = CircleShape,
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.widthIn(max = 216.dp)
                    ) {
                        Text(
                            text = book.title,
                            color = palette.textMain,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                        )
                    }

                    Surface(
                        onClick = { onToggleNotesDrawer(true) },
                        shape = CircleShape,
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PrototypeSvgIcon(
                                type = if (bookHighlights.isNotEmpty()) PrototypeIconType.BOOKMARK_FILLED else PrototypeIconType.BOOKMARK_OUTLINE,
                                tint = palette.textMain,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 2. MAIN READER VIEWPORT (`#readerViewport`)
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val stageW = with(density) { maxWidth.roundToPx() }.coerceIn(320, 1920)
                val stageH = with(density) { maxHeight.roundToPx() }.coerceIn(360, 2400)
                val fontPx = with(density) { prefs.fontSizeSp.sp.toPx() }
                val marginPx = with(density) { prefs.pageMarginDp.dp.roundToPx() }

                when (prefs.readingFlowMode) {
                    ReadingFlowMode.PAGINATED, ReadingFlowMode.SPREAD -> {
                        val curZero = (metrics.currentGlobalPage - 1).coerceAtLeast(0)
                        val step = if (isSpread) 2 else 1

                        val curLeftPdfBmp by produceState<Bitmap?>(
                            initialValue = if (isPdfBook) PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, curZero, ((stageW / 120) * 120).coerceAtLeast(360)) else null,
                            isPdfBook, curZero, book.localFilePath, stageW
                        ) {
                            value = if (isPdfBook) PdfNativeRenderer.renderPageBitmap(book.localFilePath!!, curZero, stageW) else null
                        }
                        val curRightPdfBmp by produceState<Bitmap?>(
                            initialValue = null,
                            isPdfBook, isSpread, curZero, book.localFilePath, stageW
                        ) {
                            value = if (isPdfBook && isSpread && curZero + 1 < metrics.totalBookPages) {
                                PdfNativeRenderer.renderPageBitmap(book.localFilePath!!, curZero + 1, stageW / 2)
                            } else null
                        }
                        val nextLeftPdfBmp by produceState<Bitmap?>(
                            initialValue = null,
                            isPdfBook, curZero, step, book.localFilePath, stageW
                        ) {
                            value = if (isPdfBook && curZero + step < metrics.totalBookPages) {
                                PdfNativeRenderer.renderPageBitmap(book.localFilePath!!, curZero + step, stageW)
                            } else null
                        }
                        val nextRightPdfBmp by produceState<Bitmap?>(
                            initialValue = null,
                            isPdfBook, isSpread, curZero, step, book.localFilePath, stageW
                        ) {
                            value = if (isPdfBook && isSpread && curZero + step + 1 < metrics.totalBookPages) {
                                PdfNativeRenderer.renderPageBitmap(book.localFilePath!!, curZero + step + 1, stageW / 2)
                            } else null
                        }
                        val prevLeftPdfBmp by produceState<Bitmap?>(
                            initialValue = null,
                            isPdfBook, curZero, step, book.localFilePath, stageW
                        ) {
                            value = if (isPdfBook && curZero - step >= 0) {
                                PdfNativeRenderer.renderPageBitmap(book.localFilePath!!, curZero - step, stageW)
                            } else null
                        }
                        val prevRightPdfBmp by produceState<Bitmap?>(
                            initialValue = null,
                            isPdfBook, isSpread, curZero, step, book.localFilePath, stageW
                        ) {
                            value = if (isPdfBook && isSpread && curZero - step + 1 >= 0) {
                                PdfNativeRenderer.renderPageBitmap(book.localFilePath!!, curZero - step + 1, stageW / 2)
                            } else null
                        }

                        val highlightBgMap = remember(palette) {
                            HighlightColorKey.entries.associate { it.key to palette.highlightBg(it).toArgb() }
                        }

                        if (prefs.animatePageTurn && prefs.pageAnimStyle == PageAnimStyle.FLIPBOOK) {
                            val curBitmap by produceState<ImageBitmap?>(
                                initialValue = null,
                                curZero, isSpread, isPdfBook, currentPageRaw, curLeftPdfBmp, curRightPdfBmp,
                                palette.bgApp, palette.textMain, prefs.fontSizeSp, prefs.lineHeightMultiplier, prefs.pageMarginDp,
                                prefs.fontFamily, prefs.fontWeight, prefs.textAlign, bookHighlights, stageW, stageH
                            ) {
                                value = if (isSpread) {
                                    val rightTriple = getPageAtGlobalZero(curZero + 1)
                                    val rightTitle = rightTriple?.let { chapters.getOrNull(it.first)?.title }.orEmpty()
                                    PageSnapshotBitmapRenderer.renderTwoPageSpreadBitmapAsync(
                                        widthPx = (stageH * 1.414f).toInt().coerceAtMost(stageW),
                                        heightPx = stageH,
                                        leftTitle = currentChapter?.title.orEmpty(),
                                        leftText = currentPageRaw,
                                        leftPageNum = "${curZero + 1}",
                                        rightTitle = rightTitle,
                                        rightText = rightTriple?.third.orEmpty(),
                                        rightPageNum = if (rightTriple != null) "${curZero + 2}" else "",
                                        leftPdfBitmap = curLeftPdfBmp,
                                        rightPdfBitmap = curRightPdfBmp,
                                        bgColor = if (isPdfBook) Color.White else palette.bgCard,
                                        textColor = palette.textMain,
                                        mutedColor = palette.textMuted,
                                        fontSizePx = fontPx,
                                        lineSpacingMultiplier = prefs.lineHeightMultiplier,
                                        fontFamily = prefs.fontFamily,
                                        fontWeight = prefs.fontWeight,
                                        textAlign = prefs.textAlign,
                                        highlights = bookHighlights,
                                        highlightBgMap = highlightBgMap
                                    )
                                } else if (isPdfBook) {
                                    curLeftPdfBmp?.asImageBitmap()
                                } else {
                                    PageSnapshotBitmapRenderer.renderSingleTextPageBitmapAsync(
                                        widthPx = stageW,
                                        heightPx = stageH,
                                        chapterTitle = currentChapter?.title.orEmpty(),
                                        pageText = currentPageRaw,
                                        pageNumberLabel = if (strings.isId) "Halaman ${curZero + 1} dari ${metrics.totalBookPages}" else "Page ${curZero + 1} of ${metrics.totalBookPages}",
                                        bgColor = palette.bgApp,
                                        textColor = palette.textMain,
                                        mutedColor = palette.textMuted,
                                        fontSizePx = fontPx,
                                        lineSpacingMultiplier = prefs.lineHeightMultiplier,
                                        marginPx = marginPx,
                                        fontFamily = prefs.fontFamily,
                                        fontWeight = prefs.fontWeight,
                                        textAlign = prefs.textAlign,
                                        highlights = bookHighlights,
                                        highlightBgMap = highlightBgMap
                                    )
                                }
                            }

                            val nextTriple = remember(chapters, curZero, step) { getPageAtGlobalZero(curZero + step) }
                            val nextBitmap by produceState<ImageBitmap?>(
                                initialValue = null,
                                curZero, isSpread, isPdfBook, nextTriple, nextLeftPdfBmp, nextRightPdfBmp,
                                palette.bgApp, palette.textMain, prefs.fontSizeSp,
                                prefs.fontFamily, prefs.fontWeight, prefs.textAlign, bookHighlights, stageW, stageH
                            ) {
                                value = if (nextTriple == null) null
                                else if (isSpread) {
                                    val nrTriple = getPageAtGlobalZero(curZero + step + 1)
                                    PageSnapshotBitmapRenderer.renderTwoPageSpreadBitmapAsync(
                                        widthPx = (stageH * 1.414f).toInt().coerceAtMost(stageW),
                                        heightPx = stageH,
                                        leftTitle = chapters.getOrNull(nextTriple.first)?.title.orEmpty(),
                                        leftText = nextTriple.third,
                                        leftPageNum = "${curZero + step + 1}",
                                        rightTitle = nrTriple?.let { chapters.getOrNull(it.first)?.title }.orEmpty(),
                                        rightText = nrTriple?.third.orEmpty(),
                                        rightPageNum = if (nrTriple != null) "${curZero + step + 2}" else "",
                                        leftPdfBitmap = nextLeftPdfBmp,
                                        rightPdfBitmap = nextRightPdfBmp,
                                        bgColor = if (isPdfBook) Color.White else palette.bgCard,
                                        textColor = palette.textMain,
                                        mutedColor = palette.textMuted,
                                        fontSizePx = fontPx,
                                        lineSpacingMultiplier = prefs.lineHeightMultiplier,
                                        fontFamily = prefs.fontFamily,
                                        fontWeight = prefs.fontWeight,
                                        textAlign = prefs.textAlign,
                                        highlights = bookHighlights,
                                        highlightBgMap = highlightBgMap
                                    )
                                } else if (isPdfBook) {
                                    nextLeftPdfBmp?.asImageBitmap()
                                } else {
                                    PageSnapshotBitmapRenderer.renderSingleTextPageBitmapAsync(
                                        widthPx = stageW,
                                        heightPx = stageH,
                                        chapterTitle = chapters.getOrNull(nextTriple.first)?.title.orEmpty(),
                                        pageText = nextTriple.third,
                                        pageNumberLabel = if (strings.isId) "Halaman ${curZero + 2} dari ${metrics.totalBookPages}" else "Page ${curZero + 2} of ${metrics.totalBookPages}",
                                        bgColor = palette.bgApp,
                                        textColor = palette.textMain,
                                        mutedColor = palette.textMuted,
                                        fontSizePx = fontPx,
                                        lineSpacingMultiplier = prefs.lineHeightMultiplier,
                                        marginPx = marginPx,
                                        fontFamily = prefs.fontFamily,
                                        fontWeight = prefs.fontWeight,
                                        textAlign = prefs.textAlign,
                                        highlights = bookHighlights,
                                        highlightBgMap = highlightBgMap
                                    )
                                }
                            }

                            val prevTriple = remember(chapters, curZero, step) { getPageAtGlobalZero(curZero - step) }
                            val prevBitmap by produceState<ImageBitmap?>(
                                initialValue = null,
                                curZero, isSpread, isPdfBook, prevTriple, prevLeftPdfBmp, prevRightPdfBmp,
                                palette.bgApp, palette.textMain, prefs.fontSizeSp,
                                prefs.fontFamily, prefs.fontWeight, prefs.textAlign, bookHighlights, stageW, stageH
                            ) {
                                value = if (prevTriple == null) null
                                else if (isSpread) {
                                    val prTriple = getPageAtGlobalZero(curZero - step + 1)
                                    PageSnapshotBitmapRenderer.renderTwoPageSpreadBitmapAsync(
                                        widthPx = (stageH * 1.414f).toInt().coerceAtMost(stageW),
                                        heightPx = stageH,
                                        leftTitle = chapters.getOrNull(prevTriple.first)?.title.orEmpty(),
                                        leftText = prevTriple.third,
                                        leftPageNum = "${curZero - step + 1}",
                                        rightTitle = prTriple?.let { chapters.getOrNull(it.first)?.title }.orEmpty(),
                                        rightText = prTriple?.third.orEmpty(),
                                        rightPageNum = if (prTriple != null) "${curZero - step + 2}" else "",
                                        leftPdfBitmap = prevLeftPdfBmp,
                                        rightPdfBitmap = prevRightPdfBmp,
                                        bgColor = if (isPdfBook) Color.White else palette.bgCard,
                                        textColor = palette.textMain,
                                        mutedColor = palette.textMuted,
                                        fontSizePx = fontPx,
                                        lineSpacingMultiplier = prefs.lineHeightMultiplier,
                                        fontFamily = prefs.fontFamily,
                                        fontWeight = prefs.fontWeight,
                                        textAlign = prefs.textAlign,
                                        highlights = bookHighlights,
                                        highlightBgMap = highlightBgMap
                                    )
                                } else if (isPdfBook) {
                                    prevLeftPdfBmp?.asImageBitmap()
                                } else {
                                    PageSnapshotBitmapRenderer.renderSingleTextPageBitmapAsync(
                                        widthPx = stageW,
                                        heightPx = stageH,
                                        chapterTitle = chapters.getOrNull(prevTriple.first)?.title.orEmpty(),
                                        pageText = prevTriple.third,
                                        pageNumberLabel = if (strings.isId) "Halaman $curZero dari ${metrics.totalBookPages}" else "Page $curZero of ${metrics.totalBookPages}",
                                        bgColor = palette.bgApp,
                                        textColor = palette.textMain,
                                        mutedColor = palette.textMuted,
                                        fontSizePx = fontPx,
                                        lineSpacingMultiplier = prefs.lineHeightMultiplier,
                                        marginPx = marginPx,
                                        fontFamily = prefs.fontFamily,
                                        fontWeight = prefs.fontWeight,
                                        textAlign = prefs.textAlign,
                                        highlights = bookHighlights,
                                        highlightBgMap = highlightBgMap
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = if (isSpread) 16.dp else 0.dp, vertical = if (isSpread) 6.dp else 0.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val activeBmp = curBitmap
                                if (activeBmp != null) {
                                    CylindricalFlipbookCanvas(
                                        currentPageBitmap = activeBmp,
                                        nextPageBitmap = nextBitmap,
                                        prevPageBitmap = prevBitmap,
                                        themeBgColor = if (isPdfBook) Color.White else if (isSpread) palette.bgCard else palette.bgApp,
                                        backPaperColor = if (isPdfBook) Color(0xFFF2EFE9) else palette.flipBackPaper,
                                        isPdfBook = isPdfBook,
                                        isTwoPageSpread = isSpread,
                                        externalTriggerDir = externalFlipTrigger,
                                        onExternalTriggerConsumed = { externalFlipTrigger = 0 },
                                        onPageTurnCommitted = { dir -> onTurnPage(dir) },
                                        onCenterTap = { onToggleFullView() },
                                        modifier = if (isSpread) {
                                            Modifier
                                                .fillMaxHeight()
                                                .aspectRatio(1.414f)
                                                .shadow(14.dp, RoundedCornerShape(6.dp))
                                                .clip(RoundedCornerShape(6.dp))
                                                .border(1.dp, palette.borderSoft, RoundedCornerShape(6.dp))
                                        } else if (isPdfBook) {
                                            val pdfRatio = if (curLeftPdfBmp != null && curLeftPdfBmp!!.height > 0) {
                                                curLeftPdfBmp!!.width.toFloat() / curLeftPdfBmp!!.height.toFloat()
                                            } else {
                                                PdfNativeRenderer.getCachedPageAspectRatio(book.localFilePath ?: "")
                                            }
                                            Modifier
                                                .fillMaxHeight()
                                                .aspectRatio(pdfRatio)
                                                .padding(vertical = 4.dp)
                                                .shadow(8.dp, RoundedCornerShape(4.dp))
                                                .clip(RoundedCornerShape(4.dp))
                                        } else {
                                            Modifier.fillMaxSize()
                                        }
                                    )
                                }
                            }
                        } else {
                            AnimatedContent(
                                targetState = curZero,
                                transitionSpec = {
                                    if (!prefs.animatePageTurn) {
                                        fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                                    } else if (prefs.pageAnimStyle == PageAnimStyle.SLIDE) {
                                        val dir = if (targetState >= initialState) 1 else -1
                                        slideInHorizontally(tween(260)) { w -> dir * (w / 6) } + fadeIn(tween(220)) togetherWith
                                            slideOutHorizontally(tween(200)) { w -> -dir * (w / 6) } + fadeOut(tween(180))
                                    } else {
                                        fadeIn(tween(240)) togetherWith fadeOut(tween(180))
                                    }
                                },
                                label = "readerPageTransition"
                            ) { pageZero ->
                                if (isSpread) {
                                    val rightTriple = getPageAtGlobalZero(pageZero + 1)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .aspectRatio(1.414f)
                                                .shadow(14.dp, RoundedCornerShape(6.dp))
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isPdfBook) Color.White else palette.bgCard)
                                                .border(1.dp, palette.borderSoft, RoundedCornerShape(6.dp))
                                        ) {
                                            // Left Leaf (Strict 3-Row Column: Top Header 22.dp, Middle Body weight(1f), Bottom Footer 20.dp)
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(22.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    Text(
                                                        text = currentChapter?.title.orEmpty().take(32).uppercase(),
                                                        color = palette.textMuted,
                                                        fontSize = 8.5.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxWidth()
                                                        .padding(vertical = 6.dp)
                                                        .clipToBounds()
                                                ) {
                                                    if (isPdfBook && curLeftPdfBmp != null) {
                                                        Image(
                                                            bitmap = curLeftPdfBmp!!.asImageBitmap(),
                                                            contentDescription = null,
                                                            contentScale = ContentScale.Fit,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else {
                                                        InteractiveTextPageContent(
                                                            chapterTitle = currentChapter?.title.orEmpty(),
                                                            pageText = currentPageRaw,
                                                            showChapterHeader = false,
                                                            scrollable = false,
                                                            prefs = prefs,
                                                            bookHighlights = bookHighlights,
                                                            onParagraphClicked = { paraText, isUpper ->
                                                                selectedQuoteForPopover = paraText
                                                                selectedPopoverInUpperHalf = isUpper
                                                            },
                                                            onIllustrationClicked = { path ->
                                                                val idx = uiState.activeBookIllustrations.indexOfFirst { it.uriOrPath == path }.coerceAtLeast(0)
                                                                onOpenIllustViewer(idx)
                                                            },
                                                            onSaveHighlightNote = onSaveHighlightNote,
                                                            onSearchInBook = { inBookSearchQuery = it }
                                                        )
                                                    }
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(20.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    Text(
                                                        text = "${metrics.currentGlobalPage}",
                                                        color = palette.textMuted,
                                                        fontSize = 8.5.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .width(1.dp)
                                                    .fillMaxHeight()
                                                    .background(Color.Black.copy(alpha = 0.14f))
                                            )
                                            // Right Leaf (Strict 3-Row Column: Top Header 22.dp, Middle Body weight(1f), Bottom Footer 20.dp)
                                            Column(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(22.dp),
                                                    contentAlignment = Alignment.CenterEnd
                                                ) {
                                                    Text(
                                                        text = (if (rightTriple != null) chapters.getOrNull(rightTriple.first)?.title.orEmpty() else currentChapter?.title.orEmpty()).take(32).uppercase(),
                                                        color = palette.textMuted,
                                                        fontSize = 8.5.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .fillMaxWidth()
                                                        .padding(vertical = 6.dp)
                                                        .clipToBounds()
                                                ) {
                                                    if (isPdfBook && curRightPdfBmp != null) {
                                                        Image(
                                                            bitmap = curRightPdfBmp!!.asImageBitmap(),
                                                            contentDescription = null,
                                                            contentScale = ContentScale.Fit,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
                                                    } else if (rightTriple != null) {
                                                        InteractiveTextPageContent(
                                                            chapterTitle = chapters.getOrNull(rightTriple.first)?.title.orEmpty(),
                                                            pageText = rightTriple.third,
                                                            showChapterHeader = false,
                                                            scrollable = false,
                                                            prefs = prefs,
                                                            bookHighlights = bookHighlights,
                                                            onParagraphClicked = { paraText, isUpper ->
                                                                selectedQuoteForPopover = paraText
                                                                selectedPopoverInUpperHalf = isUpper
                                                            },
                                                            onIllustrationClicked = { path ->
                                                                val idx = uiState.activeBookIllustrations.indexOfFirst { it.uriOrPath == path }.coerceAtLeast(0)
                                                                onOpenIllustViewer(idx)
                                                            },
                                                            onSaveHighlightNote = onSaveHighlightNote,
                                                            onSearchInBook = { inBookSearchQuery = it }
                                                        )
                                                    }
                                                }
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(20.dp),
                                                    contentAlignment = Alignment.CenterEnd
                                                ) {
                                                    Text(
                                                        text = "${metrics.currentGlobalPage + 1}",
                                                        color = palette.textMuted,
                                                        fontSize = 8.5.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else if (isPdfBook) {
                                    val pdfRatio = if (curLeftPdfBmp != null && curLeftPdfBmp!!.height > 0) {
                                        curLeftPdfBmp!!.width.toFloat() / curLeftPdfBmp!!.height.toFloat()
                                    } else {
                                        PdfNativeRenderer.getCachedPageAspectRatio(book.localFilePath ?: "")
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(vertical = 4.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (curLeftPdfBmp != null) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color.White,
                                                shadowElevation = 8.dp,
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .aspectRatio(pdfRatio)
                                            ) {
                                                Image(
                                                    bitmap = curLeftPdfBmp!!.asImageBitmap(),
                                                    contentDescription = "PDF Page ${pageZero + 1}",
                                                    contentScale = ContentScale.FillBounds,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    InteractiveTextPageContent(
                                        chapterTitle = currentChapter?.title.orEmpty(),
                                        pageText = currentPageRaw,
                                        showChapterHeader = uiState.activePageIndex == 0,
                                        scrollable = true,
                                        prefs = prefs,
                                        bookHighlights = bookHighlights,
                                        onParagraphClicked = { paraText, isUpper ->
                                                selectedQuoteForPopover = paraText
                                            selectedPopoverInUpperHalf = isUpper
                                        },
                                        onIllustrationClicked = { path ->
                                            val idx = uiState.activeBookIllustrations.indexOfFirst { it.uriOrPath == path }.coerceAtLeast(0)
                                            onOpenIllustViewer(idx)
                                        },
                                        onSaveHighlightNote = onSaveHighlightNote,
                                        onSearchInBook = { inBookSearchQuery = it }
                                    )
                                }
                            }
                        }
                    }

                    // 3. VERTICAL CONTINUOUS SCROLL & WEBTOON MODES (Fixed for both PDF and EPUB/TXT!)
                    ReadingFlowMode.SCROLL, ReadingFlowMode.WEBTOON -> {
                        val isWebtoon = prefs.readingFlowMode == ReadingFlowMode.WEBTOON
                        val scrollListState = rememberLazyListState()

                        LaunchedEffect(metrics.currentGlobalPage) {
                            val targetIdx = (metrics.currentGlobalPage - 1).coerceAtLeast(0)
                            if (!scrollListState.isScrollInProgress) {
                                scrollListState.scrollToItem(targetIdx)
                            }
                        }

                        if (isPdfBook) {
                            val totalPdfPages = metrics.totalBookPages.coerceAtLeast(1)
                            val pdfPath = book.localFilePath!!
                            LazyColumn(
                                state = scrollListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = if (isWebtoon) 0.dp else 12.dp),
                                verticalArrangement = Arrangement.spacedBy(if (isWebtoon) 0.dp else 12.dp)
                            ) {
                                items(
                                    count = totalPdfPages,
                                    key = { pageIdx -> "pdf_vertical_page_$pageIdx" }
                                ) { pageIdx ->
                                    PdfVerticalPageItem(
                                        pdfFilePath = pdfPath,
                                        pageIndex = pageIdx,
                                        targetWidthPx = stageW,
                                        isWebtoon = isWebtoon
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = scrollListState,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = prefs.pageMarginDp.dp)
                            ) {
                                chapters.forEachIndexed { chIdx, ch ->
                                    item(key = "ch_hdr_$chIdx") {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(top = if (isWebtoon) 16.dp else 28.dp, bottom = 12.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            if (!isWebtoon) {
                                                Text(
                                                    text = if (strings.isId) "BAB ${chIdx + 1}" else "CHAPTER ${chIdx + 1}",
                                                    color = palette.textMuted.copy(alpha = 0.65f),
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    letterSpacing = 1.4.sp
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                            }
                                            Text(
                                                text = ch.title,
                                                color = palette.textMain,
                                                fontSize = if (isWebtoon) 20.sp else 24.sp,
                                                fontFamily = FontFamily.Serif,
                                                fontWeight = FontWeight.SemiBold,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                    itemsIndexed(
                                        items = ch.pages,
                                        key = { pIdx, _ -> "ch_${chIdx}_p_$pIdx" }
                                    ) { _, pgRaw ->
                                        InteractiveTextPageContent(
                                            chapterTitle = ch.title,
                                            pageText = pgRaw,
                                            showChapterHeader = false,
                                            scrollable = false, // Must be false inside LazyColumn to prevent crash!
                                            prefs = prefs,
                                            bookHighlights = bookHighlights,
                                            onParagraphClicked = { paraText, isUpper ->
                                            selectedQuoteForPopover = paraText
                                                selectedPopoverInUpperHalf = isUpper
                                            },
                                            onIllustrationClicked = { path ->
                                                val idx = uiState.activeBookIllustrations.indexOfFirst { it.uriOrPath == path }.coerceAtLeast(0)
                                                onOpenIllustViewer(idx)
                                            },
                                            onSaveHighlightNote = onSaveHighlightNote,
                                            onSearchInBook = { inBookSearchQuery = it }
                                        )
                                    }
                                    if (!isWebtoon) {
                                        item(key = "ch_div_$chIdx") {
                                            HorizontalDivider(
                                                color = palette.borderSoft,
                                                modifier = Modifier.padding(vertical = 22.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. BOTTOM FLOATING CHROME BAR (`#readerBottomChrome` in `llunevie_prototype.html`)
            if (!uiState.readerFullView) {
                val showArrows = prefs.readingFlowMode == ReadingFlowMode.PAGINATED || prefs.readingFlowMode == ReadingFlowMode.SPREAD
                val pageIndicatorLabel = when (prefs.readingFlowMode) {
                    ReadingFlowMode.WEBTOON -> {
                        if (strings.isId) "WEBTOON · ${metrics.totalBookPages} HAL (${chapters.size.coerceAtLeast(1)} BAB)"
                        else "WEBTOON · ${metrics.totalBookPages} P (${chapters.size.coerceAtLeast(1)} CH)"
                    }
                    ReadingFlowMode.SCROLL -> {
                        if (strings.isId) "GULIR · ${metrics.totalBookPages} HAL (${chapters.size.coerceAtLeast(1)} BAB)"
                        else "SCROLL · ${metrics.totalBookPages} P (${chapters.size.coerceAtLeast(1)} CH)"
                    }
                    ReadingFlowMode.SPREAD -> {
                        val rightNum = (metrics.currentGlobalPage + 1).coerceAtMost(metrics.totalBookPages)
                        val rangeStr = if (rightNum > metrics.currentGlobalPage) "${metrics.currentGlobalPage}–$rightNum" else "${metrics.currentGlobalPage}"
                        if (strings.isId) "HAL $rangeStr DARI ${metrics.totalBookPages} · ${metrics.globalPct}%"
                        else "PAGE $rangeStr OF ${metrics.totalBookPages} · ${metrics.globalPct}%"
                    }
                    ReadingFlowMode.PAGINATED -> {
                        if (strings.isId) "HAL ${metrics.currentGlobalPage} DARI ${metrics.totalBookPages} · ${metrics.globalPct}%"
                        else "PAGE ${metrics.currentGlobalPage} OF ${metrics.totalBookPages} · ${metrics.globalPct}%"
                    }
                }

                // Interactive Page Scrubber Popup when tapping "HAL X DARI Y"
                androidx.compose.animation.AnimatedVisibility(
                    visible = showPageScrubber,
                    enter = fadeIn(tween(180)) + slideInVertically(tween(200)) { it / 2 },
                    exit = fadeOut(tween(140)) + slideOutVertically(tween(180)) { it / 2 }
                ) {
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = palette.sheetBg,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        shadowElevation = 14.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (strings.isId) "LOMPAT KE HALAMAN" else "JUMP TO PAGE",
                                    color = palette.textMuted,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${scrubberPageValue.toInt().coerceIn(1, metrics.totalBookPages)} / ${metrics.totalBookPages}",
                                    color = palette.textMain,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            FlyleafThinSlider(
                                value = scrubberPageValue,
                                onValueChange = { scrubberPageValue = it },
                                valueRange = 1f..metrics.totalBookPages.toFloat().coerceAtLeast(1f),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    onClick = { showPageScrubber = false },
                                    shape = CircleShape,
                                    color = Color.Transparent
                                ) {
                                    Text(
                                        text = strings.cancelBtn,
                                        color = palette.textMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    onClick = {
                                        val target = scrubberPageValue.toInt().coerceIn(1, metrics.totalBookPages)
                                        val targetZero = target - 1
                                        val triple = getPageAtGlobalZero(targetZero)
                                        if (triple != null) {
                                            onJumpToChapterAndPage(triple.first, triple.second)
                                        }
                                        showPageScrubber = false
                                    },
                                    shape = CircleShape,
                                    color = palette.accentDark
                                ) {
                                    Text(
                                        text = if (strings.isId) "Buka" else "Go",
                                        color = palette.bgApp,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 4.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { onToggleTocDrawer(true) },
                        shape = CircleShape,
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PrototypeSvgIcon(
                                type = PrototypeIconType.TOC_LIST,
                                tint = palette.textMain,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    Surface(
                        shape = CircleShape,
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 12.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (showArrows) {
                                Text(
                                    text = "‹",
                                    color = palette.textMain.copy(alpha = 0.75f),
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            if (prefs.animatePageTurn && prefs.pageAnimStyle == PageAnimStyle.FLIPBOOK) {
                                                externalFlipTrigger = -1
                                            } else {
                                                onTurnPage(-1)
                                            }
                                        }
                                        .padding(horizontal = 4.dp)
                                )
                            }
                            Text(
                                text = pageIndicatorLabel,
                                color = palette.textMain,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { showPageScrubber = !showPageScrubber }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                            if (showArrows) {
                                Text(
                                    text = "›",
                                    color = palette.textMain.copy(alpha = 0.75f),
                                    fontSize = 14.sp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            if (prefs.animatePageTurn && prefs.pageAnimStyle == PageAnimStyle.FLIPBOOK) {
                                                externalFlipTrigger = 1
                                            } else {
                                                onTurnPage(1)
                                            }
                                        }
                                        .padding(horizontal = 4.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = { onToggleTypeSheet(!uiState.showReaderTypeSheet, null) },
                        shape = CircleShape,
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Aa",
                                color = palette.textMain,
                                fontSize = 13.5.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }

        // MIDDLE-RIGHT FLOATING FULL-VIEW TOGGLE BUTTON (`#readerFullViewToggleBtn` in `llunevie_prototype.html`)
        // Dynamically docks against the right edge as a sleek semi-circle after a few seconds of idle time.
        val fullViewBtnWidth by animateDpAsState(
            targetValue = if (isFullViewBtnDocked) 16.dp else 34.dp,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "fullViewBtnW"
        )
        val fullViewBtnHeight by animateDpAsState(
            targetValue = if (isFullViewBtnDocked) 38.dp else 34.dp,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "fullViewBtnH"
        )
        val fullViewBtnEndPad by animateDpAsState(
            targetValue = if (isFullViewBtnDocked) 0.dp else 12.dp,
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "fullViewBtnPad"
        )
        val fullViewBtnShape = if (isFullViewBtnDocked) {
            RoundedCornerShape(topStart = 19.dp, bottomStart = 19.dp, topEnd = 0.dp, bottomEnd = 0.dp)
        } else {
            CircleShape
        }

        Surface(
            onClick = {
                if (isFullViewBtnDocked) {
                    isFullViewBtnDocked = false
                    fullViewBtnLastActive = System.currentTimeMillis()
                } else {
                    fullViewBtnLastActive = System.currentTimeMillis()
                    onToggleFullView()
                }
            },
            shape = fullViewBtnShape,
            color = palette.bgSurface.copy(alpha = if (isFullViewBtnDocked) 0.75f else 0.92f),
            border = BorderStroke(1.dp, palette.borderSoft),
            shadowElevation = if (isFullViewBtnDocked) 3.dp else 6.dp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = fullViewBtnEndPad)
                .width(fullViewBtnWidth)
                .height(fullViewBtnHeight)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isFullViewBtnDocked) {
                    Text(
                        text = "‹",
                        color = palette.textMain.copy(alpha = 0.75f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(end = 2.dp)
                    )
                } else {
                    PrototypeSvgIcon(
                        type = if (uiState.readerFullView) PrototypeIconType.COLLAPSE_FULL else PrototypeIconType.EXPAND_FULL,
                        tint = palette.textMain,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        // HIGHLIGHT & NOTE POPOVER (`#highlightPopover`)
        selectedQuoteForPopover?.let { quote ->
            val existingHl = bookHighlights.find { it.quoteText.equals(quote, ignoreCase = true) }
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = palette.sheetBg.copy(alpha = 0.97f),
                border = BorderStroke(1.dp, palette.borderSoft),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .align(if (selectedPopoverInUpperHalf) Alignment.BottomCenter else Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 52.dp)
                    .fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = quote,
                                color = palette.textMain,
                                fontSize = 13.5.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = existingHl?.noteText?.ifBlank { null }
                                    ?: (if (strings.isId) "Sorotan tersimpan di bab ini" else "Highlight saved in this chapter"),
                                color = palette.textMuted,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Surface(
                            onClick = {
                                selectedQuoteForPopover = null
                                showInlineNoteInput = false
                            },
                            shape = CircleShape,
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.size(26.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("✕", color = palette.textMain, fontSize = 10.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            HighlightColorKey.entries.forEach { key ->
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(palette.highlightDot(key))
                                        .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                                        .clickable {
                                            onSaveHighlightNote(quote, existingHl?.noteText.orEmpty(), key, existingHl?.id)
                                            selectedQuoteForPopover = null
                                        }
                                )
                            }
                        }
                        Surface(
                            onClick = {
                                if (existingHl != null) onDeleteHighlightNote(existingHl.id)
                                selectedQuoteForPopover = null
                            },
                            shape = CircleShape,
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                PrototypeSvgIcon(
                                    type = PrototypeIconType.TRASH_CAN,
                                    tint = palette.textMain,
                                    modifier = Modifier.size(15.dp),
                                    strokeWidthDp = 1.8f
                                )
                            }
                        }
                    }

                    if (showInlineNoteInput) {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = palette.borderSoft)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = palette.bgSurface,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    if (inlineNoteText.isEmpty()) {
                                        Text(
                                            text = if (strings.isId) "Tulis catatan untuk kutipan ini..." else "Write note...",
                                            color = palette.textMuted,
                                            fontSize = 11.5.sp
                                        )
                                    }
                                    BasicTextField(
                                        value = inlineNoteText,
                                        onValueChange = { inlineNoteText = it },
                                        singleLine = true,
                                        textStyle = TextStyle(color = palette.textMain, fontSize = 11.5.sp),
                                        cursorBrush = SolidColor(palette.textMain),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            Surface(
                                onClick = {
                                    onSaveHighlightNote(
                                        quote,
                                        inlineNoteText,
                                        HighlightColorKey.fromKey(existingHl?.colorKey),
                                        existingHl?.id
                                    )
                                    showInlineNoteInput = false
                                    selectedQuoteForPopover = null
                                },
                                shape = CircleShape,
                                color = palette.accentDark,
                                modifier = Modifier.height(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                    Text(
                                        text = if (strings.isId) "Simpan" else "Save",
                                        color = palette.bgApp,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = palette.borderSoft)
                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // 1. [Pen/Note Icon] Catatan
                        Surface(
                            onClick = {
                                inlineNoteText = existingHl?.noteText.orEmpty()
                                showInlineNoteInput = !showInlineNoteInput
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = if (showInlineNoteInput) (if (palette.isGlass) palette.pillActiveBg else palette.accentDark) else palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                PrototypeSvgIcon(
                                    type = PrototypeIconType.EDIT_PEN,
                                    tint = if (showInlineNoteInput && !palette.isGlass) palette.bgApp else palette.textMain,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = strings.hlNote,
                                    color = if (showInlineNoteInput && !palette.isGlass) palette.bgApp else palette.textMain,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }

                        // 2. [Copy Icon] Salin
                        Surface(
                            onClick = {
                                clipboard.setText(AnnotatedString(quote))
                                selectedQuoteForPopover = null
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                PrototypeSvgIcon(
                                    type = PrototypeIconType.BOOKMARK_OUTLINE,
                                    tint = palette.textMain,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = strings.hlCopy,
                                    color = palette.textMain,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }

                        // 3. [Bookmark/List Icon] Di Buku
                        Surface(
                            onClick = {
                                onSaveHighlightNote(
                                    quote,
                                    existingHl?.noteText.orEmpty(),
                                    HighlightColorKey.fromKey(existingHl?.colorKey),
                                    existingHl?.id
                                )
                                selectedQuoteForPopover = null
                                onToggleNotesDrawer(true)
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                PrototypeSvgIcon(
                                    type = PrototypeIconType.TOC_LIST,
                                    tint = palette.textMain,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = strings.hlInBook,
                                    color = palette.textMain,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }

                        // 4. [Search Icon] Cari
                        Surface(
                            onClick = {
                                val q = quote.take(50)
                                selectedQuoteForPopover = null
                                inBookSearchQuery = q
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                PrototypeSvgIcon(
                                    type = PrototypeIconType.SEARCH,
                                    tint = palette.textMain,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = strings.hlFind,
                                    color = palette.textMain,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        // IN-BOOK SEARCH OVERLAY (`#inBookSearchDrawer`)
        inBookSearchQuery?.let { initialQuery ->
            var currentQuery by remember { mutableStateOf(initialQuery) }
            val searchResults = remember(currentQuery, chapters) {
                val q = currentQuery.trim()
                if (q.length < 2) emptyList()
                else {
                    val list = mutableListOf<Triple<Int, Int, String>>() // chIdx, pgIdx, snippet
                    for ((chIdx, ch) in chapters.withIndex()) {
                        for ((pgIdx, pageContent) in ch.pages.withIndex()) {
                            val clean = pageContent
                                .replace(Regex("\\[ILLUST:[^]]+]"), "")
                                .replace(Regex("\\[PDF_PAGE:[^]]+]"), "")
                                .trim()
                            if (clean.contains(q, ignoreCase = true)) {
                                val matchIdx = clean.indexOf(q, ignoreCase = true)
                                val start = maxOf(0, matchIdx - 30)
                                val end = minOf(clean.length, matchIdx + q.length + 45)
                                val snip = (if (start > 0) "…" else "") + clean.substring(start, end).trim() + (if (end < clean.length) "…" else "")
                                list.add(Triple(chIdx, pgIdx, snip))
                            }
                        }
                    }
                    list
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { inBookSearchQuery = null }
                    .zIndex(90f)
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = palette.sheetBg,
                    border = BorderStroke(1.dp, palette.borderSoft),
                    shadowElevation = 24.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.72f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (strings.isId) "CARI DI BUKU INI" else "SEARCH IN THIS BOOK",
                                    color = palette.textMuted,
                                    fontSize = 9.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.2.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${searchResults.size} ${if (strings.isId) "hasil ditemukan" else "results found"}",
                                    color = palette.textMain,
                                    fontSize = 14.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Surface(
                                onClick = { inBookSearchQuery = null },
                                shape = CircleShape,
                                color = palette.bgSurface,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier.size(30.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("✕", color = palette.textMain, fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text("🔍", fontSize = 13.sp)
                                BasicTextField(
                                    value = currentQuery,
                                    onValueChange = { currentQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = palette.textMain, fontSize = 13.sp),
                                    cursorBrush = SolidColor(palette.textMain),
                                    modifier = Modifier.weight(1f)
                                )
                                if (currentQuery.isNotEmpty()) {
                                    Text(
                                        text = "✕",
                                        color = palette.textMuted,
                                        fontSize = 11.sp,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable { currentQuery = "" }
                                            .padding(4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = palette.borderSoft)
                        Spacer(modifier = Modifier.height(10.dp))

                        if (searchResults.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (currentQuery.trim().length < 2)
                                        (if (strings.isId) "Ketik kata kunci untuk mencari..." else "Type keywords to search...")
                                    else
                                        (if (strings.isId) "Tidak ada kata yang cocok ditemukan" else "No matching text found"),
                                    color = palette.textMuted,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(searchResults, key = { idx, _ -> "sr_$idx" }) { _, (chIdx, pgIdx, snippet) ->
                                    val chTitle = chapters.getOrNull(chIdx)?.title.orEmpty()
                                    Surface(
                                        onClick = {
                                            onJumpToChapterAndPage(chIdx, pgIdx)
                                            inBookSearchQuery = null
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        color = palette.bgSurface,
                                        border = BorderStroke(1.dp, palette.borderSoft),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = chTitle,
                                                    color = palette.textMain,
                                                    fontSize = 12.sp,
                                                    fontFamily = FontFamily.Serif,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                Surface(
                                                    shape = CircleShape,
                                                    color = palette.pillActiveBg,
                                                    border = BorderStroke(1.dp, palette.pillActiveBorder)
                                                ) {
                                                    Text(
                                                        text = "${strings.pagesUnit} ${pgIdx + 1}",
                                                        color = palette.textMain,
                                                        fontSize = 9.5.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = snippet,
                                                color = palette.textMuted,
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. IN-HIERARCHY READER TYPOGRAPHY & FLOW SHEET (`#readerTypeSheet` in `llunevie_prototype.html`)
        // Using an in-hierarchy overlay instead of ModalBottomSheet prevents WindowManager crashes during orientation switches!
        if (uiState.showReaderTypeSheet) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onToggleTypeSheet(false, null) }
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    color = palette.sheetBg,
                    border = BorderStroke(1.dp, palette.borderSoft),
                    shadowElevation = 24.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        // Top Tab Switcher (TEKS / TEMA / ALUR) + Close Button (✕)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val availableTabs = if (isPdfBook) {
                                listOf(
                                    "turn" to (if (strings.isId) "ALUR" else "FLOW"),
                                    "page" to (if (strings.isId) "TEMA" else "THEME")
                                )
                            } else {
                                listOf(
                                    "text" to (if (strings.isId) "TEKS" else "TEXT"),
                                    "page" to (if (strings.isId) "TEMA" else "THEME"),
                                    "turn" to (if (strings.isId) "ALUR" else "FLOW")
                                )
                            }
                            val effectiveTab = if (isPdfBook && uiState.activeReaderSheetTab == "text") "turn" else uiState.activeReaderSheetTab

                            ClassicGlassSegmentedControl(
                                items = availableTabs,
                                selectedItem = effectiveTab,
                                onItemSelected = { onToggleTypeSheet(true, it) },
                                height = 36.dp,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Surface(
                                onClick = { onToggleTypeSheet(false, null) },
                                shape = CircleShape,
                                color = palette.bgSurface,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("✕", color = palette.textMain, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        val currentTabKey = if (isPdfBook && uiState.activeReaderSheetTab == "text") "turn" else uiState.activeReaderSheetTab

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            when (currentTabKey) {
                                // TAB 1: SIZE, ALIGNMENT, WEIGHT & UNIFIED "FONT" SECTION (1:1 with #sheetTabText)
                                "text" -> {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = if (strings.isId) "UKURAN HURUF" else "FONT SIZE",
                                                color = palette.textMuted,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                letterSpacing = 1.sp
                                            )
                                            Text(
                                                text = "${prefs.fontSizeSp}px",
                                                color = palette.textMain,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                        FlyleafThinSlider(
                                            value = prefs.fontSizeSp.toFloat(),
                                            onValueChange = { onUpdateTypography(null, it.roundToInt(), null, null, null, null, null, null, null, null) },
                                            valueRange = 12f..28f
                                        )
                                    }

                                    // 2-Column Grid: PERATAAN TEKS + KETEBALAN HURUF
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (strings.isId) "PERATAAN TEKS" else "TEXT ALIGNMENT",
                                                color = palette.textMuted,
                                                fontSize = 9.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                letterSpacing = 1.sp,
                                                modifier = Modifier.padding(bottom = 6.dp)
                                            )
                                            ClassicGlassSegmentedControl(
                                                items = listOf(
                                                    "left" to (if (strings.isId) "Kiri" else "Left"),
                                                    "justify" to "Justify",
                                                    "center" to (if (strings.isId) "Tengah" else "Center")
                                                ),
                                                selectedItem = prefs.textAlign.lowercase(),
                                                onItemSelected = { onUpdateTypography(null, null, null, it, null, null, null, null, null, null) },
                                                height = 34.dp
                                            )
                                        }

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (strings.isId) "KETEBALAN HURUF" else "FONT WEIGHT",
                                                color = palette.textMuted,
                                                fontSize = 9.5.sp,
                                                fontFamily = FontFamily.Monospace,
                                                letterSpacing = 1.sp,
                                                modifier = Modifier.padding(bottom = 6.dp)
                                            )
                                            ClassicGlassSegmentedControl(
                                                items = listOf(
                                                    400 to "Normal",
                                                    500 to "Medium",
                                                    600 to (if (strings.isId) "Tebal" else "Bold")
                                                ),
                                                selectedItem = prefs.fontWeight,
                                                onItemSelected = { onUpdateTypography(null, null, it, null, null, null, null, null, null, null) },
                                                height = 34.dp
                                            )
                                        }
                                    }

                                    // FONT SECTION
                                    Column {
                                        Text(
                                            text = "FONT",
                                            color = palette.textMuted,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            CURATED_READER_FONTS.forEach { (famKey, famDisplay) ->
                                                val selected = prefs.fontFamily == famKey
                                                Surface(
                                                    onClick = { onUpdateTypography(famKey, null, null, null, null, null, null, null, null, null) },
                                                    shape = CircleShape,
                                                    color = if (selected) palette.textMain else palette.bgSurface,
                                                    border = BorderStroke(1.dp, if (selected) palette.textMain else palette.borderSoft)
                                                ) {
                                                    Text(
                                                        text = famDisplay,
                                                        color = if (selected) palette.bgApp else palette.textMain,
                                                        fontSize = 12.sp,
                                                        fontFamily = when {
                                                            famKey == "JetBrains Mono" -> FontFamily.Monospace
                                                            SANS_FONTS.contains(famKey) -> FontFamily.SansSerif
                                                            else -> FontFamily.Serif
                                                        },
                                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // TAB 2: THEME & COMFORT SPACING (1:1 with #sheetTabPage)
                                "page" -> {
                                    Column {
                                        Text(
                                            text = if (strings.isId) "TEMA BACA" else "READING THEME",
                                            color = palette.textMuted,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(bottom = 8.dp)
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val themeColors = listOf(
                                                Triple(AppThemeMode.PAPER, Color(0xFFF5F2EA), Color(0xFF23201A)),
                                                Triple(AppThemeMode.WHITE, Color(0xFFFFFFFF), Color(0xFF18181B)),
                                                Triple(AppThemeMode.SEPIA, Color(0xFFEFE6D5), Color(0xFF2C2217)),
                                                Triple(AppThemeMode.BLACK, Color(0xFF141413), Color(0xFFEDEAE2))
                                            )
                                            themeColors.forEach { (mode, chipBg, chipFg) ->
                                                val active = prefs.appTheme == mode
                                                Surface(
                                                    onClick = { onSetTheme(mode) },
                                                    shape = RoundedCornerShape(16.dp),
                                                    color = chipBg,
                                                    border = BorderStroke(
                                                        width = if (active) 2.dp else 1.dp,
                                                        color = if (active) palette.textMain else palette.borderSoft
                                                    ),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Text(
                                                        text = mode.displayLabel,
                                                        color = chipFg,
                                                        fontSize = 12.sp,
                                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.padding(vertical = 8.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                     // If not PDF, show 2x2 Comfort Sliders Grid & Margins/Indents
                                     if (!isPdfBook) {
                                         Row(
                                             modifier = Modifier.fillMaxWidth(),
                                             horizontalArrangement = Arrangement.spacedBy(12.dp)
                                         ) {
                                             Column(modifier = Modifier.weight(1f)) {
                                                 Row(
                                                     modifier = Modifier.fillMaxWidth(),
                                                     horizontalArrangement = Arrangement.SpaceBetween
                                                 ) {
                                                     Text(
                                                         text = if (strings.isId) "SPASI BARIS" else "LINE SPACING",
                                                         color = palette.textMuted,
                                                         fontSize = 9.sp,
                                                         fontFamily = FontFamily.Monospace
                                                     )
                                                     Text(
                                                         text = String.format("%.2f", prefs.lineHeightMultiplier),
                                                         color = palette.textMain,
                                                         fontSize = 9.sp,
                                                         fontFamily = FontFamily.Monospace
                                                     )
                                                 }
                                                 FlyleafThinSlider(
                                                     value = prefs.lineHeightMultiplier,
                                                     onValueChange = { onUpdateTypography(null, null, null, null, it, null, null, null, null, null) },
                                                     valueRange = 1.2f..2.4f
                                                 )
                                             }

                                             Column(modifier = Modifier.weight(1f)) {
                                                 Row(
                                                     modifier = Modifier.fillMaxWidth(),
                                                     horizontalArrangement = Arrangement.SpaceBetween
                                                 ) {
                                                     Text(
                                                         text = if (strings.isId) "JARAK PARAGRAF" else "PARA SPACING",
                                                         color = palette.textMuted,
                                                         fontSize = 9.sp,
                                                         fontFamily = FontFamily.Monospace
                                                     )
                                                     Text(
                                                         text = String.format("%.1fem", prefs.paraSpacingEm),
                                                         color = palette.textMain,
                                                         fontSize = 9.sp,
                                                         fontFamily = FontFamily.Monospace
                                                     )
                                                 }
                                                 FlyleafThinSlider(
                                                     value = prefs.paraSpacingEm,
                                                     onValueChange = { onUpdateTypography(null, null, null, null, null, it, null, null, null, null) },
                                                     valueRange = 0.4f..2.2f
                                                 )
                                             }
                                         }

                                         Row(
                                             modifier = Modifier.fillMaxWidth(),
                                             horizontalArrangement = Arrangement.spacedBy(12.dp)
                                         ) {
                                             Column(modifier = Modifier.weight(1f)) {
                                                 Row(
                                                     modifier = Modifier.fillMaxWidth(),
                                                     horizontalArrangement = Arrangement.SpaceBetween
                                                 ) {
                                                     Text(
                                                         text = if (strings.isId) "SPASI HURUF" else "LETTER SPACING",
                                                         color = palette.textMuted,
                                                         fontSize = 9.sp,
                                                         fontFamily = FontFamily.Monospace
                                                     )
                                                     Text(
                                                         text = String.format("%.2fem", prefs.letterSpacingEm),
                                                         color = palette.textMain,
                                                         fontSize = 9.sp,
                                                         fontFamily = FontFamily.Monospace
                                                     )
                                                 }
                                                 FlyleafThinSlider(
                                                     value = prefs.letterSpacingEm,
                                                     onValueChange = { onUpdateTypography(null, null, null, null, null, null, it, null, null, null) },
                                                     valueRange = -0.02f..0.10f
                                                 )
                                             }

                                             Column(modifier = Modifier.weight(1f)) {
                                                 Row(
                                                     modifier = Modifier.fillMaxWidth(),
                                                     horizontalArrangement = Arrangement.SpaceBetween
                                                 ) {
                                                     Text(
                                                         text = if (strings.isId) "SPASI KATA" else "WORD SPACING",
                                                         color = palette.textMuted,
                                                         fontSize = 9.sp,
                                                         fontFamily = FontFamily.Monospace
                                                     )
                                                     Text(
                                                         text = "${prefs.wordSpacingPx}px",
                                                         color = palette.textMain,
                                                         fontSize = 9.sp,
                                                         fontFamily = FontFamily.Monospace
                                                     )
                                                 }
                                                 FlyleafThinSlider(
                                                     value = prefs.wordSpacingPx.toFloat(),
                                                     onValueChange = { onUpdateTypography(null, null, null, null, null, null, null, it.roundToInt(), null, null) },
                                                     valueRange = 0f..8f
                                                 )
                                             }
                                         }

                                         // Bottom Row: MARGIN TEPI + INDENTASI PARAGRAF
                                         Row(
                                             modifier = Modifier.fillMaxWidth(),
                                             horizontalArrangement = Arrangement.spacedBy(12.dp),
                                             verticalAlignment = Alignment.CenterVertically
                                         ) {
                                             Column(modifier = Modifier.weight(1f)) {
                                                 Row(
                                                     modifier = Modifier.fillMaxWidth(),
                                                     horizontalArrangement = Arrangement.SpaceBetween
                                                 ) {
                                                     Text(
                                                         text = if (strings.isId) "MARGIN TEPI" else "PAGE MARGIN",
                                                         color = palette.textMuted,
                                                         fontSize = 9.sp,
                                                         fontFamily = FontFamily.Monospace
                                                     )
                                                     Text(
                                                         text = "${prefs.pageMarginDp}px",
                                                         color = palette.textMain,
                                                         fontSize = 9.sp,
                                                         fontFamily = FontFamily.Monospace
                                                     )
                                                 }
                                                 FlyleafThinSlider(
                                                     value = prefs.pageMarginDp.toFloat(),
                                                     onValueChange = { onUpdateTypography(null, null, null, null, null, null, null, null, it.roundToInt(), null) },
                                                     valueRange = 12f..48f
                                                 )
                                             }

                                             Column(modifier = Modifier.weight(1f)) {
                                                  Text(
                                                      text = if (strings.isId) "INDENTASI PARAGRAF" else "PARA INDENT",
                                                      color = palette.textMuted,
                                                      fontSize = 9.sp,
                                                      fontFamily = FontFamily.Monospace,
                                                      modifier = Modifier.padding(bottom = 6.dp)
                                                  )
                                                  ClassicGlassSegmentedControl(
                                                      items = listOf(
                                                          false to (if (strings.isId) "Tanpa" else "None"),
                                                          true to "Indent"
                                                      ),
                                                      selectedItem = prefs.paraIndent,
                                                      onItemSelected = { onUpdateTypography(null, null, null, null, null, null, null, null, null, it) },
                                                      height = 34.dp
                                                  )
                                              }
                                          }
                                      }
                                  }

                                  // TAB 3: FLOW MODES & PAGE ANIMATION (1:1 with #sheetTabTurn)
                                  "turn" -> {
                                      Text(
                                          text = if (strings.isId) "MODE ALUR BACA" else "READING FLOW MODE",
                                          color = palette.textMuted,
                                          fontSize = 10.sp,
                                          fontFamily = FontFamily.Monospace,
                                          letterSpacing = 1.sp
                                      )
                                      val modePairs = ReadingFlowMode.entries.chunked(2)
                                      modePairs.forEach { rowModes ->
                                          Row(
                                              modifier = Modifier.fillMaxWidth(),
                                              horizontalArrangement = Arrangement.spacedBy(8.dp)
                                          ) {
                                              rowModes.forEach { mode ->
                                                  val selected = prefs.readingFlowMode == mode
                                                  val cardBg = if (palette.isGlass) {
                                                      if (selected) palette.pillActiveBg else palette.bgSurface
                                                  } else {
                                                      if (selected) palette.accentDark else palette.bgSurface
                                                  }
                                                  val cardBorder = if (palette.isGlass) {
                                                      if (selected) palette.pillActiveBorder else palette.borderSoft
                                                  } else {
                                                      if (selected) palette.accentDark else palette.borderSoft
                                                  }
                                                  val titleColor = if (selected && !palette.isGlass) palette.bgApp else palette.textMain
                                                  val subtitleColor = if (selected && !palette.isGlass) palette.bgApp.copy(alpha = 0.75f) else palette.textMuted
                                                  val modeIcon = when (mode) {
                                                      ReadingFlowMode.PAGINATED -> PrototypeIconType.CHEVRON_RIGHT
                                                      ReadingFlowMode.SCROLL -> PrototypeIconType.CHEVRON_DOWN
                                                      ReadingFlowMode.WEBTOON -> PrototypeIconType.LIST_BULLETS
                                                      ReadingFlowMode.SPREAD -> PrototypeIconType.FULL_COVER
                                                  }

                                                  Surface(
                                                      onClick = { onSetReadingFlowMode(mode) },
                                                      shape = RoundedCornerShape(14.dp),
                                                      color = cardBg,
                                                      border = BorderStroke(1.dp, cardBorder),
                                                      shadowElevation = if (selected) 2.dp else 0.dp,
                                                      modifier = Modifier
                                                          .weight(1f)
                                                          .height(60.dp)
                                                  ) {
                                                      Row(
                                                          modifier = Modifier
                                                              .fillMaxSize()
                                                              .padding(horizontal = 12.dp, vertical = 8.dp),
                                                          verticalAlignment = Alignment.CenterVertically,
                                                          horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                      ) {
                                                          PrototypeSvgIcon(
                                                              type = modeIcon,
                                                              tint = titleColor,
                                                              modifier = Modifier.size(16.dp),
                                                              strokeWidthDp = 1.8f
                                                          )
                                                          Column(
                                                              modifier = Modifier.weight(1f),
                                                              verticalArrangement = Arrangement.Center
                                                          ) {
                                                              Text(
                                                                  text = mode.localizedLabel(prefs.lang),
                                                                  color = titleColor,
                                                                  fontSize = 12.sp,
                                                                  fontWeight = FontWeight.SemiBold,
                                                                  maxLines = 1,
                                                                  overflow = TextOverflow.Ellipsis
                                                              )
                                                              Spacer(modifier = Modifier.height(2.dp))
                                                              Text(
                                                                  text = mode.localizedDesc(prefs.lang),
                                                                  color = subtitleColor,
                                                                  fontSize = 10.sp,
                                                                  maxLines = 1,
                                                                  overflow = TextOverflow.Ellipsis
                                                              )
                                                          }
                                                      }
                                                  }
                                              }
                                          }
                                      }

                                      HorizontalDivider(color = palette.borderSoft)

                                      Text(
                                          text = if (strings.isId) "GAYA ANIMASI HALAMAN" else "PAGE ANIMATION STYLE",
                                          color = palette.textMuted,
                                          fontSize = 10.sp,
                                          fontFamily = FontFamily.Monospace,
                                          letterSpacing = 1.sp
                                      )
                                      ClassicGlassSegmentedControl(
                                          items = PageAnimStyle.entries.map { it to it.label },
                                          selectedItem = prefs.pageAnimStyle,
                                          onItemSelected = { onSetPageAnimStyle(it) },
                                          height = 36.dp
                                      )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 5. LEFT SIDEBAR TOC & ILLUSTRATIONS DRAWER (`#readerTocDrawer` with Footer Progress Bar!)
        if (uiState.showReaderTocDrawer) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onToggleTocDrawer(false) }
            ) {
                Surface(
                    shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
                    color = palette.sheetBg,
                    border = BorderStroke(1.dp, palette.borderSoft),
                    shadowElevation = 24.dp,
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(0.82f)
                        .widthIn(max = 300.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (strings.isId) "DAFTAR BAB & ILUSTRASI" else "CHAPTERS & ILLUSTRATIONS",
                                        color = palette.textMuted.copy(alpha = 0.7f),
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 1.4.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = book.title,
                                        color = palette.textMain,
                                        fontSize = 17.sp,
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = if (isPdfBook) "Dokumen PDF · ${chapters.size} bab" else "${book.author} · ${chapters.size} bab",
                                        color = palette.textMuted,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Surface(
                                    onClick = { onToggleTocDrawer(false) },
                                    shape = CircleShape,
                                    color = palette.bgSurface,
                                    border = BorderStroke(1.dp, palette.borderSoft),
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("✕", color = palette.textMain, fontSize = 12.sp)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = palette.borderSoft)
                            Spacer(modifier = Modifier.height(12.dp))

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                itemsIndexed(chapters, key = { idx, _ -> "toc_ch_$idx" }) { idx, ch ->
                                    val active = idx == uiState.activeChapterIndex
                                    Surface(
                                        onClick = { onJumpToChapterAndPage(idx, 0) },
                                        shape = RoundedCornerShape(16.dp),
                                        color = if (active) palette.textMain else palette.bgSurface,
                                        border = BorderStroke(1.dp, if (active) palette.textMain else palette.borderSoft),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                modifier = Modifier.weight(1f),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Text(
                                                    text = String.format("%02d", idx + 1),
                                                    color = if (active) palette.bgApp.copy(alpha = 0.70f) else palette.textMuted.copy(alpha = 0.65f),
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Normal
                                                )
                                                Text(
                                                    text = ch.title,
                                                    color = if (active) palette.bgApp else palette.textMain,
                                                    fontSize = 13.5.sp,
                                                    fontFamily = FontFamily.Serif,
                                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "${ch.pages.size} ${strings.pagesUnit}",
                                                color = if (active) palette.bgApp.copy(alpha = 0.8f) else palette.textMuted,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }
                                }

                                 if (uiState.activeBookIllustrations.isNotEmpty()) {
                                     item(key = "toc_illust_hdr") {
                                         Spacer(modifier = Modifier.height(14.dp))
                                         Text(
                                             text = "INSERT ILLUSTRATIONS (${uiState.activeBookIllustrations.size})",
                                             color = palette.textMuted,
                                             fontSize = 10.sp,
                                             fontFamily = FontFamily.Monospace,
                                             letterSpacing = 1.2.sp
                                         )
                                     }
                                     val illustPairs = uiState.activeBookIllustrations.chunked(2)
                                     itemsIndexed(illustPairs, key = { rowIdx, _ -> "toc_il_row_$rowIdx" }) { rowIdx, pair ->
                                         Row(
                                             modifier = Modifier.fillMaxWidth(),
                                             horizontalArrangement = Arrangement.spacedBy(8.dp)
                                         ) {
                                             pair.forEachIndexed { colIdx, illust ->
                                                 val globalIllustIdx = rowIdx * 2 + colIdx
                                                 Surface(
                                                     onClick = { onOpenIllustViewer(globalIllustIdx) },
                                                     shape = RoundedCornerShape(14.dp),
                                                     color = palette.bgCard,
                                                     border = BorderStroke(1.dp, palette.borderSoft),
                                                     modifier = Modifier
                                                         .weight(1f)
                                                         .aspectRatio(0.74f)
                                                 ) {
                                                     Box(modifier = Modifier.fillMaxSize()) {
                                                         AsyncImage(
                                                             model = File(illust.uriOrPath),
                                                             contentDescription = illust.caption,
                                                             contentScale = ContentScale.Crop,
                                                             modifier = Modifier.fillMaxSize()
                                                         )
                                                         Box(
                                                             modifier = Modifier
                                                                 .fillMaxWidth()
                                                                 .align(Alignment.BottomCenter)
                                                                 .background(
                                                                     Brush.verticalGradient(
                                                                         listOf(
                                                                             Color.Transparent,
                                                                             Color.Black.copy(alpha = 0.82f)
                                                                         )
                                                                     )
                                                                 )
                                                                 .padding(horizontal = 8.dp, vertical = 6.dp)
                                                         ) {
                                                             Text(
                                                                 text = illust.caption,
                                                                 color = Color.White,
                                                                 fontSize = 10.sp,
                                                                 fontWeight = FontWeight.Medium,
                                                                 maxLines = 1,
                                                                 overflow = TextOverflow.Ellipsis
                                                             )
                                                         }
                                                     }
                                                 }
                                             }
                                             if (pair.size == 1) {
                                                 Spacer(modifier = Modifier.weight(1f))
                                             }
                                         }
                                     }
                                 }
                             }
                        }

                        // Sidebar Footer Progress (`#tocSidebarProgressBar` in `llunevie_prototype.html`)
                        Column(modifier = Modifier.padding(top = 12.dp)) {
                            HorizontalDivider(color = palette.borderSoft)
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = if (strings.isId) "PROGRES BACA" else "READING PROGRESS",
                                    color = palette.textMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = "${metrics.globalPct}%",
                                    color = palette.textMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { (metrics.globalPct / 100f).coerceIn(0f, 1f) },
                                color = palette.textMain,
                                trackColor = palette.borderSoft,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                }
            }
        }

        // 6. RIGHT SAVED NOTES & HIGHLIGHTS DRAWER (`#readerNotesDrawer`)
        if (uiState.showReaderNotesDrawer) {
            var isAddingManualNote by remember { mutableStateOf(false) }
            var manualNoteText by remember { mutableStateOf("") }
            var manualQuoteText by remember { mutableStateOf("") }
            var selectedNoteColor by remember { mutableStateOf(HighlightColorKey.GOLD) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onToggleNotesDrawer(false) }
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
                    color = palette.sheetBg,
                    border = BorderStroke(1.dp, palette.borderSoft),
                    shadowElevation = 24.dp,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .fillMaxWidth(0.84f)
                        .widthIn(max = 310.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.notesDrawerTag,
                                    color = palette.textMuted,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.4.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = book.title,
                                    color = palette.textMain,
                                    fontSize = 17.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = if (strings.isId) "${bookHighlights.size} catatan tersimpan" else "${bookHighlights.size} saved note(s)",
                                    color = palette.textMuted,
                                    fontSize = 11.sp
                                )
                            }
                            Surface(
                                onClick = { onToggleNotesDrawer(false) },
                                shape = CircleShape,
                                color = palette.bgSurface,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("✕", color = palette.textMain, fontSize = 12.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = palette.borderSoft)
                        Spacer(modifier = Modifier.height(12.dp))

                        // Manual Note Creator (clean & minimal, perfect for PDF & EPUB reading)
                        if (!isAddingManualNote) {
                            Surface(
                                onClick = { isAddingManualNote = true },
                                shape = RoundedCornerShape(14.dp),
                                color = palette.bgSurface,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    PrototypeSvgIcon(
                                        type = PrototypeIconType.PLUS,
                                        tint = palette.textMain,
                                        modifier = Modifier.size(14.dp),
                                        strokeWidthDp = 1.8f
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = if (strings.isId) "Tambah Catatan Manual" else "Add Manual Note",
                                        color = palette.textMain,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        } else {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = palette.bgCard,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (strings.isId) "CATATAN HAL ${metrics.currentGlobalPage}"
                                            else "NOTE PAGE ${metrics.currentGlobalPage}",
                                            color = palette.textMuted,
                                            fontSize = 9.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            HighlightColorKey.entries.forEach { key ->
                                                val colorBorder = palette.highlightBorder(key)
                                                val colorBg = palette.highlightBg(key)
                                                val isSelected = selectedNoteColor == key
                                                Box(
                                                    modifier = Modifier
                                                        .size(18.dp)
                                                        .clip(CircleShape)
                                                        .background(colorBg)
                                                        .border(
                                                            width = if (isSelected) 2.dp else 1.dp,
                                                            color = if (isSelected) palette.textMain else colorBorder,
                                                            shape = CircleShape
                                                        )
                                                        .clickable { selectedNoteColor = key }
                                                )
                                            }
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = palette.bgSurface,
                                        border = BorderStroke(1.dp, palette.borderSoft),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                            if (manualQuoteText.isEmpty()) {
                                                Text(
                                                    text = if (strings.isId) "Judul / topik (opsional)..." else "Title / topic (optional)...",
                                                    color = palette.textMuted.copy(alpha = 0.6f),
                                                    fontSize = 12.sp
                                                )
                                            }
                                            BasicTextField(
                                                value = manualQuoteText,
                                                onValueChange = { manualQuoteText = it },
                                                textStyle = TextStyle(color = palette.textMain, fontSize = 12.sp),
                                                singleLine = true,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = palette.bgSurface,
                                        border = BorderStroke(1.dp, palette.borderSoft),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                                .heightIn(min = 60.dp)
                                        ) {
                                            if (manualNoteText.isEmpty()) {
                                                Text(
                                                    text = if (strings.isId) "Tulis catatan penting Anda di sini..." else "Write your note here...",
                                                    color = palette.textMuted.copy(alpha = 0.6f),
                                                    fontSize = 12.sp
                                                )
                                            }
                                            BasicTextField(
                                                value = manualNoteText,
                                                onValueChange = { manualNoteText = it },
                                                textStyle = TextStyle(color = palette.textMain, fontSize = 12.sp),
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            onClick = {
                                                isAddingManualNote = false
                                                manualNoteText = ""
                                                manualQuoteText = ""
                                            },
                                            shape = CircleShape,
                                            color = Color.Transparent
                                        ) {
                                            Text(
                                                text = strings.cancelBtn,
                                                color = palette.textMuted,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Surface(
                                            onClick = {
                                                if (manualNoteText.isNotBlank() || manualQuoteText.isNotBlank()) {
                                                    val finalQuote = manualQuoteText.ifBlank {
                                                        if (strings.isId) "Catatan Hal ${metrics.currentGlobalPage}" else "Note Page ${metrics.currentGlobalPage}"
                                                    }
                                                    onSaveHighlightNote(finalQuote, manualNoteText.trim(), selectedNoteColor, null)
                                                    isAddingManualNote = false
                                                    manualNoteText = ""
                                                    manualQuoteText = ""
                                                }
                                            },
                                            shape = CircleShape,
                                            color = palette.accentDark
                                        ) {
                                            Text(
                                                text = if (strings.isId) "Simpan" else "Save",
                                                color = palette.bgApp,
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (bookHighlights.isEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(22.dp),
                                color = palette.bgCard,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(22.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = if (strings.isId) "Belum ada catatan tersimpan" else "No saved notes yet",
                                        color = palette.textMain,
                                        fontSize = 15.sp,
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = if (strings.isId) "Ketuk paragraf pada halaman baca untuk menandai sorotan dan menyimpan catatan."
                                        else "Tap any paragraph on the reading page to highlight and save a note.",
                                        color = palette.textMuted,
                                        fontSize = 12.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                itemsIndexed(bookHighlights, key = { _, h -> h.id }) { _, hl ->
                                    val colorKey = HighlightColorKey.fromKey(hl.colorKey)
                                    val hlBg = palette.highlightBg(colorKey)
                                    val hlBorder = palette.highlightBorder(colorKey)

                                    Surface(
                                        onClick = { onJumpToChapterAndPage(hl.chapterIndex, hl.pageIndex) },
                                        shape = RoundedCornerShape(20.dp),
                                        color = palette.bgCard,
                                        border = BorderStroke(1.dp, palette.borderSoft),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(9.dp)
                                                            .clip(CircleShape)
                                                            .background(hlBorder)
                                                    )
                                                    Text(
                                                        text = "${hl.chapterTitle} · ${if (strings.isId) "Hal" else "Page"} ${hl.pageIndex + 1}",
                                                        color = palette.textMuted,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace
                                                    )
                                                }
                                                Text(
                                                    text = "✕",
                                                    color = palette.textMuted,
                                                    fontSize = 12.sp,
                                                    modifier = Modifier.clickable { onDeleteHighlightNote(hl.id) }
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Surface(
                                                shape = RoundedCornerShape(12.dp),
                                                color = hlBg,
                                                border = BorderStroke(1.dp, hlBorder),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "“${hl.quoteText}”",
                                                    color = palette.hlText,
                                                    fontSize = 13.sp,
                                                    fontFamily = FontFamily.Serif,
                                                    lineHeight = 18.sp,
                                                    modifier = Modifier.padding(10.dp)
                                                )
                                            }
                                            if (hl.noteText.isNotBlank()) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = hl.noteText,
                                                    color = palette.textMuted,
                                                    fontSize = 12.sp,
                                                    lineHeight = 17.sp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 7. OPAQUE ILLUSTRATION VIEWER MODAL (`#illustViewerModal`)
        if (uiState.showIllustViewer && uiState.activeBookIllustrations.isNotEmpty()) {
            val illusts = uiState.activeBookIllustrations
            val activeItem = illusts.getOrElse(uiState.activeIllustIndex) { illusts.first() }
            var manualScale by remember(uiState.activeIllustIndex) { mutableFloatStateOf(1f) }
            var panOffset by remember(uiState.activeIllustIndex) { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
            val currentZoom = (manualScale * uiState.illustZoom).coerceIn(1f, 5f)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.bgApp)
                    .clickable(enabled = false) {}
            ) {
                Surface(
                    color = palette.bgCard,
                    border = BorderStroke(1.dp, palette.borderSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .statusBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = strings.illustBadge,
                                color = palette.textMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                            Surface(
                                shape = CircleShape,
                                color = palette.bgSurface,
                                border = BorderStroke(1.dp, palette.borderSoft)
                            ) {
                                Text(
                                    text = "${uiState.activeIllustIndex + 1} / ${illusts.size}",
                                    color = palette.textMain,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Surface(
                            onClick = onCloseIllustViewer,
                            shape = CircleShape,
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft)
                        ) {
                            Text(
                                text = "${strings.closeText} ✕",
                                color = palette.textMain,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(palette.bgApp)
                        .clipToBounds()
                        .pointerInput(uiState.activeIllustIndex, uiState.illustZoom) {
                            detectTransformGestures { centroid: Offset, pan: Offset, zoom: Float, rotation: Float ->
                                val nextScale = (manualScale * zoom).coerceIn(1f, 5f)
                                manualScale = nextScale
                                val eff = nextScale * uiState.illustZoom
                                if (eff > 1.02f) {
                                    val maxPanX = (size.width.toFloat() * (eff - 1f)) / 2f
                                    val maxPanY = (size.height.toFloat() * (eff - 1f)) / 2f
                                    val nextPanX = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX)
                                    val nextPanY = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                    panOffset = Offset(nextPanX, nextPanY)
                                } else {
                                    panOffset = Offset.Zero
                                }
                            }
                        }
                        .pointerInput(uiState.activeIllustIndex, uiState.illustZoom) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (currentZoom > 1.2f) {
                                        manualScale = 1f
                                        onSetIllustZoom(1f)
                                        panOffset = androidx.compose.ui.geometry.Offset.Zero
                                    } else {
                                        manualScale = 2.2f
                                        panOffset = androidx.compose.ui.geometry.Offset.Zero
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = File(activeItem.uriOrPath),
                        contentDescription = activeItem.caption,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .graphicsLayer {
                                scaleX = currentZoom
                                scaleY = currentZoom
                                translationX = if (currentZoom > 1.02f) panOffset.x else 0f
                                translationY = if (currentZoom > 1.02f) panOffset.y else 0f
                            }
                    )
                }

                Surface(
                    color = palette.bgCard,
                    border = BorderStroke(1.dp, palette.borderSoft),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = activeItem.caption,
                            color = palette.textMain,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = palette.bgSurface,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 3.dp, vertical = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    listOf(1f to "1.0x", 1.75f to "1.75x", 2.5f to "2.5x").forEach { (zVal, zLbl) ->
                                        val active = (kotlin.math.abs(currentZoom - zVal) < 0.15f)
                                        Surface(
                                            onClick = {
                                                onSetIllustZoom(zVal)
                                                manualScale = 1f
                                                panOffset = androidx.compose.ui.geometry.Offset.Zero
                                            },
                                            shape = CircleShape,
                                            color = if (active) palette.pillActiveBg else Color.Transparent,
                                            border = if (active) BorderStroke(1.dp, palette.pillActiveBorder) else null,
                                            modifier = Modifier.fillMaxHeight()
                                        ) {
                                            Box(
                                                contentAlignment = Alignment.Center,
                                                modifier = Modifier.padding(horizontal = 8.dp)
                                            ) {
                                                Text(
                                                    text = zLbl,
                                                    color = if (active) palette.textMain else palette.textMuted,
                                                    fontSize = 10.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    onClick = {
                                        manualScale = 1f
                                        panOffset = androidx.compose.ui.geometry.Offset.Zero
                                        onOpenIllustViewer(uiState.activeIllustIndex - 1)
                                    },
                                    shape = CircleShape,
                                    color = palette.bgSurface,
                                    border = BorderStroke(1.dp, palette.borderSoft),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 11.dp)
                                    ) {
                                        Text(
                                            text = strings.prevText,
                                            color = palette.textMain,
                                            fontSize = 10.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                                Surface(
                                    onClick = {
                                        manualScale = 1f
                                        panOffset = androidx.compose.ui.geometry.Offset.Zero
                                        onOpenIllustViewer(uiState.activeIllustIndex + 1)
                                    },
                                    shape = CircleShape,
                                    color = palette.bgSurface,
                                    border = BorderStroke(1.dp, palette.borderSoft),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.padding(horizontal = 11.dp)
                                    ) {
                                        Text(
                                            text = strings.nextText,
                                            color = palette.textMain,
                                            fontSize = 10.5.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Renders a single PDF page inside `LazyColumn` for `ReadingFlowMode.SCROLL` and `ReadingFlowMode.WEBTOON`.
 * Uses `PdfNativeRenderer`'s LruCache and cached aspect ratio so vertical PDF scrolling is 100% glitch-free.
 */
@Composable
private fun PdfVerticalPageItem(
    pdfFilePath: String,
    pageIndex: Int,
    targetWidthPx: Int,
    isWebtoon: Boolean
) {
    val widthBucket = ((targetWidthPx.coerceIn(360, 1280) / 120) * 120).coerceAtLeast(360)
    val pageBmp by produceState<Bitmap?>(
        initialValue = PdfNativeRenderer.getCachedBitmap(pdfFilePath, pageIndex, widthBucket),
        pdfFilePath, pageIndex, widthBucket
    ) {
        if (value == null) {
            value = PdfNativeRenderer.renderPageBitmap(pdfFilePath, pageIndex, widthBucket)
        }
    }
    val bmp = pageBmp
    val ratio = if (bmp != null && bmp.height > 0) {
        (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.45f, 1.6f)
    } else {
        PdfNativeRenderer.getCachedPageAspectRatio(pdfFilePath)
    }

    Surface(
        shape = if (isWebtoon) RoundedCornerShape(0.dp) else RoundedCornerShape(6.dp),
        color = Color.White,
        shadowElevation = if (isWebtoon) 0.dp else 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
    ) {
        if (bmp != null && !bmp.isRecycled) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White),
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
private fun InteractiveTextPageContent(
    chapterTitle: String,
    pageText: String,
    showChapterHeader: Boolean,
    scrollable: Boolean,
    prefs: UserPreferencesState,
    bookHighlights: List<HighlightNoteEntity>,
    onParagraphClicked: (String, Boolean) -> Unit,
    onIllustrationClicked: (String) -> Unit,
    onSaveHighlightNote: (String, String, HighlightColorKey, String?) -> Unit,
    onSearchInBook: (String) -> Unit
) {
    val palette = LocalLluneviePalette.current
    val paragraphs = remember(pageText) {
        pageText.split("\n\n")
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("[PDF_PAGE:") }
    }

    val colModifier = if (scrollable) {
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = prefs.pageMarginDp.dp, vertical = 12.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    }

    val resolvedTextAlign = when (prefs.textAlign.lowercase()) {
        "justify" -> TextAlign.Justify
        "center" -> TextAlign.Center
        else -> TextAlign.Start
    }
    val resolvedFontFamily = getReaderFontFamily(prefs.fontFamily)
    val resolvedFontWeight = when {
        prefs.fontWeight >= 600 -> FontWeight.Bold
        prefs.fontWeight == 500 -> FontWeight.Medium
        else -> FontWeight.Normal
    }
    val paraSpacingDp = (prefs.fontSizeSp * prefs.paraSpacingEm * 0.65f).dp.coerceIn(6.dp, 36.dp)

    val clipboardManager = LocalClipboardManager.current
    var isToolbarShown by remember { mutableStateOf(false) }
    var pendingCopyAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val customTextToolbar = remember(clipboardManager) {
        object : TextToolbar {
            override var status: TextToolbarStatus = TextToolbarStatus.Hidden
            override fun hide() {
                status = TextToolbarStatus.Hidden
                isToolbarShown = false
                pendingCopyAction = null
            }
            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?
            ) {
                status = TextToolbarStatus.Shown
                pendingCopyAction = onCopyRequested
                isToolbarShown = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        CompositionLocalProvider(LocalTextToolbar provides customTextToolbar) {
            SelectionContainer {
                Column(
                    modifier = colModifier,
                    verticalArrangement = Arrangement.spacedBy(paraSpacingDp)
                ) {
                    if (showChapterHeader && chapterTitle.isNotBlank()) {
                        Text(
                            text = chapterTitle,
                            color = palette.textMain,
                            fontSize = (prefs.fontSizeSp + 6).sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        )
                    }

                    paragraphs.forEachIndexed { idx, para ->
                        if (para.startsWith("[ILLUST:") && para.endsWith("]")) {
                            val inner = para.removePrefix("[ILLUST:").removeSuffix("]")
                            val path = inner.substringBefore("|")
                            val caption = inner.substringAfter("|", "")
                            Surface(
                                onClick = { onIllustrationClicked(path) },
                                shape = RoundedCornerShape(16.dp),
                                color = palette.bgCard,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier.fillMaxWidth()
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
                        } else {
                            val isUpperHalf = idx <= paragraphs.size / 2
                            var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                            var dragStartOffset by remember { mutableIntStateOf(-1) }
                            var dragCurrentOffset by remember { mutableIntStateOf(-1) }

                            val selStart = if (dragStartOffset >= 0 && dragCurrentOffset >= 0) minOf(dragStartOffset, dragCurrentOffset) else -1
                            val selEnd = if (dragStartOffset >= 0 && dragCurrentOffset >= 0) maxOf(dragStartOffset, dragCurrentOffset) else -1

                            // Stabilo Highlighting via SpanStyle inside AnnotatedString (Pastel swatches with dark readable text)
                            val annotatedPara = buildAnnotatedString {
                                append(para)
                                bookHighlights.forEach { hl ->
                                    val quote = hl.quoteText.trim()
                                    if (quote.isNotBlank() && para.contains(quote, ignoreCase = true)) {
                                        var startIndex = 0
                                        while (startIndex < para.length) {
                                            val foundIdx = para.indexOf(quote, startIndex, ignoreCase = true)
                                            if (foundIdx < 0) break
                                            val colorKey = HighlightColorKey.fromKey(hl.colorKey)
                                            addStyle(
                                                style = SpanStyle(
                                                    background = palette.highlightBg(colorKey),
                                                    color = Color(0xFF1E1B18)
                                                ),
                                                start = foundIdx,
                                                end = foundIdx + quote.length
                                            )
                                            startIndex = foundIdx + quote.length
                                        }
                                    }
                                }
                                if (selStart in 0 until para.length && selEnd in (selStart + 1)..para.length) {
                                    addStyle(
                                        style = SpanStyle(
                                            background = Color(0xFFFEF08A).copy(alpha = 0.65f),
                                            color = Color(0xFF1E1B18)
                                        ),
                                        start = selStart,
                                        end = selEnd
                                    )
                                }
                            }

                            Box(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = annotatedPara,
                                    color = palette.textMain,
                                    fontSize = prefs.fontSizeSp.sp,
                                    lineHeight = (prefs.fontSizeSp * prefs.lineHeightMultiplier).sp,
                                    letterSpacing = (prefs.letterSpacingEm * 14f).sp,
                                    fontFamily = resolvedFontFamily,
                                    fontWeight = resolvedFontWeight,
                                    textAlign = resolvedTextAlign,
                                    style = TextStyle(
                                        textIndent = if (prefs.paraIndent) TextIndent(firstLine = 22.sp) else TextIndent.None
                                    ),
                                    onTextLayout = { textLayoutResult = it },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerInput(para) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = { startPos ->
                                                    textLayoutResult?.let { layout ->
                                                        val charIdx = layout.getOffsetForPosition(startPos).coerceIn(0, para.length)
                                                        val word = layout.getWordBoundary(charIdx)
                                                        dragStartOffset = word.start
                                                        dragCurrentOffset = word.end
                                                    }
                                                },
                                                onDrag = { change, _ ->
                                                    change.consume()
                                                    textLayoutResult?.let { layout ->
                                                        val charIdx = layout.getOffsetForPosition(change.position).coerceIn(0, para.length)
                                                        dragCurrentOffset = charIdx
                                                    }
                                                },
                                                onDragEnd = {
                                                    if (dragStartOffset >= 0 && dragCurrentOffset >= 0) {
                                                        val s = minOf(dragStartOffset, dragCurrentOffset).coerceIn(0, para.length)
                                                        val e = maxOf(dragStartOffset, dragCurrentOffset).coerceIn(0, para.length)
                                                        if (e > s) {
                                                            val selectedText = para.substring(s, e).trim()
                                                            if (selectedText.isNotBlank()) {
                                                                onParagraphClicked(selectedText, isUpperHalf)
                                                            }
                                                        }
                                                    }
                                                },
                                                onDragCancel = {
                                                    dragStartOffset = -1
                                                    dragCurrentOffset = -1
                                                }
                                            )
                                        }
                                        .pointerInput(para) {
                                            detectTapGestures { tapPos ->
                                                textLayoutResult?.let { layout ->
                                                    val charIdx = layout.getOffsetForPosition(tapPos).coerceIn(0, para.length)
                                                    val matchedHl = bookHighlights.find { hl ->
                                                        val q = hl.quoteText.trim()
                                                        val idx = para.indexOf(q, ignoreCase = true)
                                                        idx >= 0 && charIdx in idx until (idx + q.length)
                                                    }
                                                    if (matchedHl != null) {
                                                        onParagraphClicked(matchedHl.quoteText, isUpperHalf)
                                                    } else {
                                                        val word = layout.getWordBoundary(charIdx)
                                                        val selectedText = para.substring(word.start.coerceIn(0, para.length), word.end.coerceIn(0, para.length)).trim()
                                                        if (selectedText.isNotBlank()) {
                                                            dragStartOffset = word.start
                                                            dragCurrentOffset = word.end
                                                            onParagraphClicked(selectedText, isUpperHalf)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        .padding(vertical = 2.dp)
                                )

                                if (selStart in 0 until para.length && selEnd in (selStart + 1)..para.length && textLayoutResult != null) {
                                    val layout = textLayoutResult!!
                                    val startBox = layout.getBoundingBox(selStart)
                                    val endBox = layout.getBoundingBox((selEnd - 1).coerceAtLeast(0))

                                    Box(
                                        modifier = Modifier
                                            .offset { IntOffset((startBox.left - 18f).roundToInt(), startBox.bottom.roundToInt()) }
                                            .size(13.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFACC15))
                                            .border(1.5.dp, Color.White, CircleShape)
                                    )
                                    Box(
                                        modifier = Modifier
                                            .offset { IntOffset((endBox.right - 6f).roundToInt(), endBox.bottom.roundToInt()) }
                                            .size(13.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFFACC15))
                                            .border(1.5.dp, Color.White, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Floating Compact Selection Action Bar: Selector stays alive until user taps an action
        if (isToolbarShown && pendingCopyAction != null) {
            Surface(
                shape = CircleShape,
                color = palette.sheetBg,
                border = BorderStroke(1.dp, palette.borderSoft),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .zIndex(60f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    HighlightColorKey.entries.forEach { key ->
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(palette.highlightBorder(key))
                                .border(1.dp, palette.borderSoft, CircleShape)
                                .clickable {
                                    pendingCopyAction?.invoke()
                                    val text = clipboardManager.getText()?.text?.toString()?.trim()
                                    if (!text.isNullOrBlank()) {
                                        onSaveHighlightNote(text, "", key, null)
                                    }
                                    customTextToolbar.hide()
                                }
                        )
                    }
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(14.dp)
                            .background(palette.borderSoft)
                    )
                    Surface(
                        onClick = {
                            pendingCopyAction?.invoke()
                            val text = clipboardManager.getText()?.text?.toString()?.trim()
                            if (!text.isNullOrBlank()) {
                                onParagraphClicked(text, true)
                            }
                            customTextToolbar.hide()
                        },
                        shape = CircleShape,
                        color = Color.Transparent
                    ) {
                        Text(
                            text = "Catatan",
                            color = palette.textMain,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        onClick = {
                            pendingCopyAction?.invoke()
                            customTextToolbar.hide()
                        },
                        shape = CircleShape,
                        color = Color.Transparent
                    ) {
                        Text(
                            text = "Salin",
                            color = palette.textMain,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(
                        onClick = {
                            pendingCopyAction?.invoke()
                            val text = clipboardManager.getText()?.text?.toString()?.trim()
                            if (!text.isNullOrBlank()) {
                                onSearchInBook(text)
                            }
                            customTextToolbar.hide()
                        },
                        shape = CircleShape,
                        color = Color.Transparent
                    ) {
                        Text(
                            text = "Cari",
                            color = palette.textMain,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
