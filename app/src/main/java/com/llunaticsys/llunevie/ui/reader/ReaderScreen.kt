package com.llunaticsys.llunevie.ui.reader

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.llunaticsys.llunevie.LlunevieViewModel
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.platform.LocalLayoutDirection
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntOffset
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
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
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import com.llunaticsys.llunevie.ui.components.sanitizeBookOrChapterText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font
import com.llunaticsys.llunevie.R
import com.llunaticsys.llunevie.ui.components.ClassicGlassSegmentedControl
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llunaticsys.llunevie.GlobalPageMetrics
import com.llunaticsys.llunevie.LlunevieUiState
import com.llunaticsys.llunevie.data.local.BookEntity
import com.llunaticsys.llunevie.data.local.localizeChapterTitle
import com.llunaticsys.llunevie.data.local.HighlightNoteEntity
import com.llunaticsys.llunevie.data.local.UserPreferencesState
import com.llunaticsys.llunevie.data.parser.EpubNativeParser
import com.llunaticsys.llunevie.data.parser.PdfNativeRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.llunaticsys.llunevie.model.AppThemeMode
import com.llunaticsys.llunevie.model.HighlightColorKey
import com.llunaticsys.llunevie.model.PageAnimStyle
import com.llunaticsys.llunevie.model.ReadingFlowMode
import com.llunaticsys.llunevie.ui.components.FlyleafThinSlider
import com.llunaticsys.llunevie.ui.components.LlunevieThinSlider
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
    onSaveHighlightNote: (String, String, HighlightColorKey, String?, Int, Int) -> Unit,
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
    viewModel: LlunevieViewModel? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalLluneviePalette.current
    val density = LocalDensity.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

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
    var lastTurnDirection by remember { mutableIntStateOf(1) }
    var slideDragOffsetPx by remember { mutableFloatStateOf(0f) }
    var hasSelectionActive by remember { mutableStateOf(false) }
    var selStartHandleAnchor by remember { mutableStateOf(Offset.Zero) }
    var selEndHandleAnchor by remember { mutableStateOf(Offset.Zero) }

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

    val context = LocalContext.current
    val view = LocalView.current
    val showReaderChrome = !uiState.readerFullView
    val isTwoPageSpread = isSpread
    DisposableEffect(isTwoPageSpread, showReaderChrome) {
        val window = (context as? android.app.Activity)?.window
        val controller = if (window != null) WindowCompat.getInsetsController(window, view) else null
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (isTwoPageSpread || !showReaderChrome) {
            controller?.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bgApp)
    ) {
        // 1. MAIN READER VIEWPORT (`#readerViewport`) - Always fills full screen Box!
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize()
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
                            initialValue = if (isPdfBook) {
                                PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, curZero, ((stageW / 120) * 120).coerceAtLeast(360))
                                    ?: viewModel?.getCachedPdfBitmap(book.id, curZero)
                            } else null,
                            isPdfBook, curZero, book.localFilePath, stageW
                        ) {
                            value = if (isPdfBook) PdfNativeRenderer.renderPageBitmap(book.localFilePath!!, curZero, stageW) else null
                        }
                        val curRightPdfBmp by produceState<Bitmap?>(
                            initialValue = if (isPdfBook && isSpread && curZero + 1 < metrics.totalBookPages) {
                                PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, curZero + 1, ((stageW / 240) * 120).coerceAtLeast(360))
                                    ?: viewModel?.getCachedPdfBitmap(book.id, curZero + 1)
                            } else null,
                            isPdfBook, isSpread, curZero, book.localFilePath, stageW
                        ) {
                            value = if (isPdfBook && isSpread && curZero + 1 < metrics.totalBookPages) {
                                PdfNativeRenderer.renderPageBitmap(book.localFilePath!!, curZero + 1, stageW / 2)
                            } else null
                        }
                        val nextLeftPdfBmp by produceState<Bitmap?>(
                            initialValue = if (isPdfBook && curZero + step < metrics.totalBookPages) {
                                PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, curZero + step, ((stageW / 120) * 120).coerceAtLeast(360))
                                    ?: viewModel?.getCachedPdfBitmap(book.id, curZero + step)
                            } else null,
                            isPdfBook, curZero, step, book.localFilePath, stageW
                        ) {
                            value = if (isPdfBook && curZero + step < metrics.totalBookPages) {
                                PdfNativeRenderer.renderPageBitmap(book.localFilePath!!, curZero + step, stageW)
                            } else null
                        }
                        val nextRightPdfBmp by produceState<Bitmap?>(
                            initialValue = if (isPdfBook && isSpread && curZero + step + 1 < metrics.totalBookPages) {
                                PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, curZero + step + 1, ((stageW / 240) * 120).coerceAtLeast(360))
                                    ?: viewModel?.getCachedPdfBitmap(book.id, curZero + step + 1)
                            } else null,
                            isPdfBook, isSpread, curZero, step, book.localFilePath, stageW
                        ) {
                            value = if (isPdfBook && isSpread && curZero + step + 1 < metrics.totalBookPages) {
                                PdfNativeRenderer.renderPageBitmap(book.localFilePath!!, curZero + step + 1, stageW / 2)
                            } else null
                        }
                        val prevLeftPdfBmp by produceState<Bitmap?>(
                            initialValue = if (isPdfBook && curZero - step >= 0) {
                                PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, curZero - step, ((stageW / 120) * 120).coerceAtLeast(360))
                                    ?: viewModel?.getCachedPdfBitmap(book.id, curZero - step)
                            } else null,
                            isPdfBook, curZero, step, book.localFilePath, stageW
                        ) {
                            value = if (isPdfBook && curZero - step >= 0) {
                                PdfNativeRenderer.renderPageBitmap(book.localFilePath!!, curZero - step, stageW)
                            } else null
                        }
                        val prevRightPdfBmp by produceState<Bitmap?>(
                            initialValue = if (isPdfBook && isSpread && curZero - step + 1 >= 0) {
                                PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, curZero - step + 1, ((stageW / 240) * 120).coerceAtLeast(360))
                                    ?: viewModel?.getCachedPdfBitmap(book.id, curZero - step + 1)
                            } else null,
                            isPdfBook, isSpread, curZero, step, book.localFilePath, stageW
                        ) {
                            value = if (isPdfBook && isSpread && curZero - step + 1 >= 0) {
                                PdfNativeRenderer.renderPageBitmap(book.localFilePath!!, curZero - step + 1, stageW / 2)
                            } else null
                        }

                        var promotedTargetBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                        LaunchedEffect(curZero) {
                            promotedTargetBitmap = null
                        }

                        val textMeasurer = rememberTextMeasurer()
                        val context = LocalContext.current
                        val padX = with(density) { 22.dp.toPx() }
                        val padTop = with(density) { 20.dp.toPx() }
                        val resolvedFontFamily = remember(prefs.fontFamily, prefs.fontWeight) {
                            getReaderFontFamily(context, prefs.fontFamily, prefs.fontWeight)
                        }
                        val resolvedFontWeight = when {
                            prefs.fontWeight >= 600 -> FontWeight.Bold
                            prefs.fontWeight == 500 -> FontWeight.Medium
                            else -> FontWeight.Normal
                        }
                        val resolvedTextAlign = when (prefs.textAlign.lowercase()) {
                            "justify" -> TextAlign.Justify
                            "center" -> TextAlign.Center
                            else -> TextAlign.Start
                        }
                        val baseTextStyle = remember(prefs, resolvedFontFamily, resolvedFontWeight, resolvedTextAlign, palette) {
                            TextStyle(
                                color = palette.textMain,
                                fontSize = prefs.fontSizeSp.sp,
                                lineHeight = (prefs.fontSizeSp * prefs.lineHeightMultiplier).sp,
                                letterSpacing = (prefs.letterSpacingEm * prefs.fontSizeSp).sp,
                                fontFamily = resolvedFontFamily,
                                fontWeight = resolvedFontWeight,
                                textAlign = resolvedTextAlign,
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            )
                        }
                        val spreadTextStyle = remember(baseTextStyle, prefs) {
                            baseTextStyle.copy(
                                fontSize = (prefs.fontSizeSp * 0.78f).sp,
                                lineHeight = (prefs.fontSizeSp * 0.78f * prefs.lineHeightMultiplier).sp
                            )
                        }

                        if (prefs.animatePageTurn && prefs.pageAnimStyle == PageAnimStyle.FLIPBOOK) {
                            val curBitmapInitial = if (isPdfBook && !isSpread) {
                                (curLeftPdfBmp
                                    ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, curZero, ((stageW / 120) * 120).coerceAtLeast(360))
                                    ?: viewModel?.getCachedPdfBitmap(book.id, curZero))?.asImageBitmap()
                            } else null

                            val curBitmap by produceState<ImageBitmap?>(
                                initialValue = curBitmapInitial,
                                curZero, isSpread, isPdfBook, currentPageRaw, curLeftPdfBmp, curRightPdfBmp,
                                palette.bgApp, palette.textMain, baseTextStyle, spreadTextStyle, stageW, stageH
                            ) {
                                value = if (isSpread) {
                                    val leftZero = (curZero / 2) * 2
                                    val rightZero = leftZero + 1
                                    val leftTriple = getPageAtGlobalZero(leftZero)
                                    val rightTriple = getPageAtGlobalZero(rightZero)
                                    val leftCh = leftTriple?.let { chapters.getOrNull(it.first) } ?: currentChapter
                                    val rightCh = rightTriple?.let { chapters.getOrNull(it.first) }
                                    renderTwoPageSpreadSnapshot(
                                        textMeasurer = textMeasurer,
                                        widthPx = (stageH * 1.414f).toInt().coerceAtMost(stageW),
                                        heightPx = stageH,
                                        density = density,
                                        leftPageIndex = leftZero,
                                        rightPageIndex = rightZero,
                                        leftHeader = leftCh?.title.orEmpty(),
                                        rightHeader = rightCh?.title.orEmpty(),
                                        leftText = leftTriple?.third.orEmpty().ifBlank { currentPageRaw },
                                        rightText = rightTriple?.third.orEmpty(),
                                        leftPdfBitmap = curLeftPdfBmp,
                                        rightPdfBitmap = curRightPdfBmp,
                                        bgColor = if (isPdfBook) Color.White else palette.bgCard,
                                        pageTextStyle = spreadTextStyle,
                                        palette = palette
                                    )
                                } else if (isPdfBook) {
                                    curLeftPdfBmp?.asImageBitmap()
                                } else {
                                    renderEpubPageSnapshot(
                                        textMeasurer = textMeasurer,
                                        widthPx = stageW,
                                        heightPx = stageH,
                                        density = density,
                                        chapterTitle = currentChapter?.title.orEmpty(),
                                        pageText = currentPageRaw,
                                        bgColor = palette.bgApp,
                                        pageTextStyle = baseTextStyle,
                                        padX = padX,
                                        padTop = padTop,
                                        isCover = (curZero == 0),
                                        highlights = bookHighlights,
                                        chapterIndex = uiState.activeChapterIndex,
                                        pageIndex = uiState.activePageIndex,
                                        palette = palette
                                    )
                                }
                            }

                            val nextTriple = remember(chapters, curZero, step) { getPageAtGlobalZero(curZero + step) }
                            val nextBitmap by produceState<ImageBitmap?>(
                                initialValue = null,
                                curZero, isSpread, isPdfBook, nextTriple, nextLeftPdfBmp, nextRightPdfBmp,
                                palette.bgApp, palette.textMain, baseTextStyle, spreadTextStyle, stageW, stageH
                            ) {
                                value = if (nextTriple == null) null
                                else if (isSpread) {
                                    val leftZero = curZero + step
                                    val rightZero = leftZero + 1
                                    val leftTripleSpread = getPageAtGlobalZero(leftZero)
                                    val rightTripleSpread = getPageAtGlobalZero(rightZero)
                                    val leftCh = leftTripleSpread?.let { chapters.getOrNull(it.first) }
                                    val rightCh = rightTripleSpread?.let { chapters.getOrNull(it.first) }
                                    renderTwoPageSpreadSnapshot(
                                        textMeasurer = textMeasurer,
                                        widthPx = (stageH * 1.414f).toInt().coerceAtMost(stageW),
                                        heightPx = stageH,
                                        density = density,
                                        leftPageIndex = leftZero,
                                        rightPageIndex = rightZero,
                                        leftHeader = leftCh?.title.orEmpty(),
                                        rightHeader = rightCh?.title.orEmpty(),
                                        leftText = leftTripleSpread?.third.orEmpty(),
                                        rightText = rightTripleSpread?.third.orEmpty(),
                                        leftPdfBitmap = nextLeftPdfBmp,
                                        rightPdfBitmap = nextRightPdfBmp,
                                        bgColor = if (isPdfBook) Color.White else palette.bgCard,
                                        pageTextStyle = spreadTextStyle,
                                        palette = palette
                                    )
                                } else if (isPdfBook) {
                                    nextLeftPdfBmp?.asImageBitmap()
                                } else {
                                    renderEpubPageSnapshot(
                                        textMeasurer = textMeasurer,
                                        widthPx = stageW,
                                        heightPx = stageH,
                                        density = density,
                                        chapterTitle = chapters.getOrNull(nextTriple.first)?.title.orEmpty(),
                                        pageText = nextTriple.third,
                                        bgColor = palette.bgApp,
                                        pageTextStyle = baseTextStyle,
                                        padX = padX,
                                        padTop = padTop,
                                        isCover = (curZero + step == 0),
                                        highlights = bookHighlights,
                                        chapterIndex = nextTriple.first,
                                        pageIndex = nextTriple.second,
                                        palette = palette
                                    )
                                }
                            }

                            val prevTriple = remember(chapters, curZero, step) { getPageAtGlobalZero(curZero - step) }
                            val prevBitmap by produceState<ImageBitmap?>(
                                initialValue = null,
                                curZero, isSpread, isPdfBook, prevTriple, prevLeftPdfBmp, prevRightPdfBmp,
                                palette.bgApp, palette.textMain, baseTextStyle, spreadTextStyle, stageW, stageH
                            ) {
                                value = if (prevTriple == null) null
                                else if (isSpread) {
                                    val leftZero = curZero - step
                                    val rightZero = leftZero + 1
                                    val leftTripleSpread = getPageAtGlobalZero(leftZero)
                                    val rightTripleSpread = getPageAtGlobalZero(rightZero)
                                    val leftCh = leftTripleSpread?.let { chapters.getOrNull(it.first) }
                                    val rightCh = rightTripleSpread?.let { chapters.getOrNull(it.first) }
                                    renderTwoPageSpreadSnapshot(
                                        textMeasurer = textMeasurer,
                                        widthPx = (stageH * 1.414f).toInt().coerceAtMost(stageW),
                                        heightPx = stageH,
                                        density = density,
                                        leftPageIndex = leftZero,
                                        rightPageIndex = rightZero,
                                        leftHeader = leftCh?.title.orEmpty(),
                                        rightHeader = rightCh?.title.orEmpty(),
                                        leftText = leftTripleSpread?.third.orEmpty(),
                                        rightText = rightTripleSpread?.third.orEmpty(),
                                        leftPdfBitmap = prevLeftPdfBmp,
                                        rightPdfBitmap = prevRightPdfBmp,
                                        bgColor = if (isPdfBook) Color.White else palette.bgCard,
                                        pageTextStyle = spreadTextStyle,
                                        palette = palette
                                    )
                                } else if (isPdfBook) {
                                    prevLeftPdfBmp?.asImageBitmap()
                                } else {
                                    renderEpubPageSnapshot(
                                        textMeasurer = textMeasurer,
                                        widthPx = stageW,
                                        heightPx = stageH,
                                        density = density,
                                        chapterTitle = chapters.getOrNull(prevTriple.first)?.title.orEmpty(),
                                        pageText = prevTriple.third,
                                        bgColor = palette.bgApp,
                                        pageTextStyle = baseTextStyle,
                                        padX = padX,
                                        padTop = padTop,
                                        isCover = (curZero - step == 0),
                                        highlights = bookHighlights,
                                        chapterIndex = prevTriple.first,
                                        pageIndex = prevTriple.second,
                                        palette = palette
                                    )
                                }
                            }

                            var isFlippingInProgress by remember { mutableStateOf(false) }
                            var flipTurnDir by remember { mutableIntStateOf(1) }
                            var liveDragProgress by remember { mutableFloatStateOf(0f) }
                            var liveCornerYNorm by remember { mutableFloatStateOf(0.76f) }

                            val flipbookGestureModifier = Modifier.pointerInput(curZero, metrics.totalBookPages, hasSelectionActive, selStartHandleAnchor, selEndHandleAnchor) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    if (hasSelectionActive) {
                                        val distStart = (down.position - selStartHandleAnchor).getDistance()
                                        val distEnd = (down.position - selEndHandleAnchor).getDistance()
                                        if (distStart < 44.dp.toPx() || distEnd < 44.dp.toPx()) {
                                            return@awaitEachGesture
                                        }
                                    }
                                    val touchSlop = viewConfiguration.touchSlop
                                    var dragLocked = false
                                    var totalDx = 0f
                                    var totalDy = 0f
                                    val velocityTracker = VelocityTracker()
                                    velocityTracker.addPosition(down.uptimeMillis, down.position)

                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull() ?: break
                                        if (change.changedToUp()) {
                                            if (dragLocked && isFlippingInProgress) {
                                                val velX = velocityTracker.calculateVelocity().x
                                                val speedInDir = if (flipTurnDir > 0) -velX else velX
                                                val shouldCommit = liveDragProgress >= 0.14f || (speedInDir > 350f)
                                                scope.launch {
                                                    if (shouldCommit) {
                                                        val oldPage = curZero
                                                        val anim = Animatable(liveDragProgress)
                                                        anim.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) {
                                                            liveDragProgress = value
                                                        }
                                                        liveDragProgress = 1f
                                                        val targetBmp = if (flipTurnDir > 0) nextBitmap else prevBitmap
                                                        if (targetBmp != null) {
                                                            promotedTargetBitmap = targetBmp
                                                        }
                                                        onTurnPage(flipTurnDir)
                                                        snapshotFlow { curZero }.first { it != oldPage }
                                                        withFrameNanos { }
                                                        liveDragProgress = 0f
                                                        isFlippingInProgress = false
                                                    } else {
                                                        val anim = Animatable(liveDragProgress)
                                                        anim.animateTo(0f, tween(160, easing = FastOutSlowInEasing)) {
                                                            liveDragProgress = value
                                                        }
                                                        liveDragProgress = 0f
                                                        isFlippingInProgress = false
                                                    }
                                                }
                                            } else if (!dragLocked) {
                                                val relX = down.position.x / size.width.toFloat().coerceAtLeast(1f)
                                                if (relX >= 0.76f) {
                                                    externalFlipTrigger = 1
                                                } else if (relX <= 0.24f) {
                                                    externalFlipTrigger = -1
                                                } else {
                                                    if (hasSelectionActive) {
                                                        hasSelectionActive = false
                                                    }
                                                    selectedQuoteForPopover = null
                                                }
                                            }
                                            break
                                        }

                                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                                        totalDx = change.position.x - down.position.x
                                        totalDy = change.position.y - down.position.y

                                        if (!dragLocked) {
                                            if (abs(totalDx) > touchSlop && abs(totalDx) > abs(totalDy) * 1.15f) {
                                                val candidateDir = if (totalDx < 0f) 1 else -1
                                                if (candidateDir > 0 && curZero >= metrics.totalBookPages - 1) break
                                                if (candidateDir < 0 && curZero <= 0) break
                                                dragLocked = true
                                                flipTurnDir = candidateDir
                                                isFlippingInProgress = true
                                                change.consume()
                                            }
                                        }

                                        if (dragLocked) {
                                            change.consume()
                                            val signedDelta = if (flipTurnDir > 0) -totalDx else totalDx
                                            liveDragProgress = (signedDelta / (size.width.toFloat() * 0.85f)).coerceIn(0.01f, 0.99f)
                                            liveCornerYNorm = (change.position.y / size.height.toFloat()).coerceIn(0.15f, 0.85f)
                                        }
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = if (isSpread) 16.dp else 0.dp, vertical = if (isSpread) 6.dp else 0.dp)
                                    .then(flipbookGestureModifier),
                                contentAlignment = Alignment.Center
                            ) {
                                if (!isPdfBook && !isSpread && !isFlippingInProgress && externalFlipTrigger == 0) {
                                    val curTriple = getPageAtGlobalZero(curZero)
                                    val curCh = curTriple?.let { chapters.getOrNull(it.first) } ?: currentChapter
                                    val curText = curTriple?.third.orEmpty().ifBlank { currentPageRaw }
                                    InteractiveTextPageContent(
                                        chapterTitle = curCh?.title.orEmpty(),
                                        pageText = curText,
                                        currentChapterIndex = curTriple?.first ?: uiState.activeChapterIndex,
                                        currentPageInChapter = curTriple?.second ?: uiState.activePageIndex,
                                        showChapterHeader = (curTriple?.second ?: uiState.activePageIndex) == 0,
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
                                        onDeleteHighlightNote = onDeleteHighlightNote,
                                        onSearchInBook = { inBookSearchQuery = it },
                                        isCoverPage = (curZero == 0),
                                        bookTitle = book.title,
                                        fontScale = 1f,
                                        onSelectionActiveChanged = { active, start, end ->
                                            hasSelectionActive = active
                                            selStartHandleAnchor = start
                                            selEndHandleAnchor = end
                                        }
                                    )
                                }

                                val activeBmp = promotedTargetBitmap ?: curBitmap
                                if (activeBmp != null && (isPdfBook || isSpread || isFlippingInProgress || externalFlipTrigger != 0)) {
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
                                        manualProgress = if (isFlippingInProgress) liveDragProgress else null,
                                        manualCornerYNorm = if (isFlippingInProgress) liveCornerYNorm else null,
                                        manualTurnDir = if (isFlippingInProgress) flipTurnDir else null,
                                        onPageTurnCommitted = { dir -> onTurnPage(dir) },
                                        onCenterTap = {
                                            if (hasSelectionActive) hasSelectionActive = false
                                            selectedQuoteForPopover = null
                                        },
                                        onFlipStateChanged = { isFlippingInProgress = it },
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
                            @Composable
                            fun RenderSpreadPageForNonFlipbook(leftZero: Int, rightZero: Int) {
                                val leftTriple = getPageAtGlobalZero(leftZero)
                                val rightTriple = getPageAtGlobalZero(rightZero)
                                val leftCh = leftTriple?.let { chapters.getOrNull(it.first) } ?: currentChapter
                                val leftText = leftTriple?.third.orEmpty().ifBlank { if (leftZero == curZero) currentPageRaw else "" }
                                val rightCh = rightTriple?.let { chapters.getOrNull(it.first) }
                                val rightText = rightTriple?.third.orEmpty()
                                val spreadBmp = remember(
                                    leftZero, rightZero, stageW, stageH,
                                    palette, isPdfBook, curLeftPdfBmp, curRightPdfBmp
                                ) {
                                    renderTwoPageSpreadSnapshot(
                                        textMeasurer = textMeasurer,
                                        widthPx = (stageH * 1.414f).toInt().coerceAtMost(stageW),
                                        heightPx = stageH,
                                        density = density,
                                        leftPageIndex = leftZero,
                                        rightPageIndex = rightZero,
                                        leftHeader = leftCh?.title.orEmpty(),
                                        rightHeader = rightCh?.title.orEmpty(),
                                        leftText = leftText,
                                        rightText = rightText,
                                        leftPdfBitmap = if (isPdfBook && leftZero == (curZero / 2) * 2) curLeftPdfBmp else if (isPdfBook) PdfNativeRenderer.getCachedBitmap(book.localFilePath ?: "", leftZero, stageW / 2) else null,
                                        rightPdfBitmap = if (isPdfBook && leftZero == (curZero / 2) * 2) curRightPdfBmp else if (isPdfBook) PdfNativeRenderer.getCachedBitmap(book.localFilePath ?: "", rightZero, stageW / 2) else null,
                                        bgColor = if (isPdfBook) Color.White else palette.bgCard,
                                        pageTextStyle = spreadTextStyle,
                                        palette = palette
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = spreadBmp,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .aspectRatio(1.414f)
                                            .shadow(14.dp, RoundedCornerShape(6.dp))
                                            .clip(RoundedCornerShape(6.dp))
                                            .border(1.dp, palette.borderSoft, RoundedCornerShape(6.dp))
                                    )
                                }
                            }

                            @Composable
                            fun RenderPageForNonFlipbook(pageZero: Int) {
                                if (isSpread) {
                                    val leftZero = (pageZero / 2) * 2
                                    val rightZero = leftZero + 1
                                    RenderSpreadPageForNonFlipbook(leftZero, rightZero)
                                } else if (isPdfBook) {
                                    if (viewModel != null) {
                                        PdfPageItemContent(
                                            bookId = book.id,
                                            filePath = book.localFilePath!!,
                                            pageIndex = pageZero,
                                            viewModel = viewModel,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    } else {
                                        val pdfRatio = if (curLeftPdfBmp != null && curLeftPdfBmp!!.height > 0) {
                                            curLeftPdfBmp!!.width.toFloat() / curLeftPdfBmp!!.height.toFloat()
                                        } else {
                                            PdfNativeRenderer.getCachedPageAspectRatio(book.localFilePath ?: "")
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(palette.bgApp)
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
                                                        contentScale = ContentScale.Fit,
                                                        modifier = Modifier.fillMaxSize()
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                        val pageTriple = getPageAtGlobalZero(pageZero)
                                        val ch = pageTriple?.let { chapters.getOrNull(it.first) } ?: currentChapter
                                        val pText = pageTriple?.third.orEmpty().ifBlank { if (pageZero == curZero) currentPageRaw else "" }
                                        val chIdx = pageTriple?.first ?: uiState.activeChapterIndex
                                        val pInCh = pageTriple?.second ?: uiState.activePageIndex
                                        InteractiveTextPageContent(
                                            chapterTitle = ch?.title.orEmpty(),
                                            pageText = pText,
                                            currentChapterIndex = chIdx,
                                            currentPageInChapter = pInCh,
                                            showChapterHeader = pInCh == 0,
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
                                            onDeleteHighlightNote = onDeleteHighlightNote,
                                            onSearchInBook = { inBookSearchQuery = it },
                                            isCoverPage = (pageZero == 0),
                                            bookTitle = book.title,
                                            fontScale = 1f
                                        )
                                    }
                                }

                            if (prefs.animatePageTurn && prefs.pageAnimStyle == PageAnimStyle.SLIDE) {
                                if (isSpread) {
                                    val totalSpreads = (metrics.totalBookPages + 1) / 2
                                    val initialSpread = (curZero / 2).coerceIn(0, (totalSpreads - 1).coerceAtLeast(0))
                                    val spreadPagerState = rememberPagerState(
                                        initialPage = initialSpread,
                                        pageCount = { totalSpreads.coerceAtLeast(1) }
                                    )
                                    LaunchedEffect(spreadPagerState) {
                                        snapshotFlow { spreadPagerState.settledPage }
                                            .distinctUntilChanged()
                                            .collect { settledSpread ->
                                                if (!spreadPagerState.isScrollInProgress) {
                                                    val targetPage = settledSpread * 2
                                                    if (targetPage / 2 != curZero / 2) {
                                                        val target = getPageAtGlobalZero(targetPage)
                                                        if (target != null) {
                                                            onJumpToChapterAndPage(target.first, target.second)
                                                        }
                                                    }
                                                }
                                            }
                                    }
                                    LaunchedEffect(curZero) {
                                        val targetSpread = curZero / 2
                                        if (!spreadPagerState.isScrollInProgress &&
                                            spreadPagerState.currentPage != targetSpread &&
                                            spreadPagerState.targetPage != targetSpread
                                        ) {
                                            spreadPagerState.animateScrollToPage(
                                                targetSpread,
                                                animationSpec = spring(dampingRatio = 0.88f, stiffness = 380f)
                                            )
                                        }
                                    }
                                    HorizontalPager(
                                        state = spreadPagerState,
                                        beyondViewportPageCount = 1,
                                        modifier = Modifier.fillMaxSize()
                                    ) { spreadIndex ->
                                        val spreadLeftPage = spreadIndex * 2
                                        val spreadRightPage = spreadIndex * 2 + 1
                                        RenderSpreadPageForNonFlipbook(spreadLeftPage, spreadRightPage)
                                    }
                                } else {
                                    val pagerState = rememberPagerState(
                                        initialPage = curZero.coerceIn(0, (metrics.totalBookPages - 1).coerceAtLeast(0)),
                                        pageCount = { metrics.totalBookPages.coerceAtLeast(1) }
                                    )
                                    LaunchedEffect(pagerState) {
                                        snapshotFlow { pagerState.settledPage }
                                            .distinctUntilChanged()
                                            .collect { settled ->
                                                if (!pagerState.isScrollInProgress && settled != curZero) {
                                                    val target = getPageAtGlobalZero(settled)
                                                    if (target != null) {
                                                        onJumpToChapterAndPage(target.first, target.second)
                                                    }
                                                }
                                            }
                                    }
                                    LaunchedEffect(curZero) {
                                        if (!pagerState.isScrollInProgress &&
                                            pagerState.currentPage != curZero &&
                                            pagerState.targetPage != curZero
                                        ) {
                                            pagerState.animateScrollToPage(
                                                curZero,
                                                animationSpec = spring(dampingRatio = 0.88f, stiffness = 380f)
                                            )
                                        }
                                    }
                                    HorizontalPager(
                                        state = pagerState,
                                        beyondViewportPageCount = 1,
                                        modifier = Modifier.fillMaxSize()
                                    ) { pIdx ->
                                        RenderPageForNonFlipbook(pIdx)
                                    }
                                }
                            } else if (prefs.animatePageTurn && prefs.pageAnimStyle == PageAnimStyle.FADE) {
                                val fadeSwipeModifier = Modifier.pointerInput(curZero, metrics.totalBookPages) {
                                    var totalDx = 0f
                                    var totalDy = 0f
                                    detectHorizontalDragGestures(
                                        onDragStart = { totalDx = 0f; totalDy = 0f },
                                        onDragEnd = {
                                            if (abs(totalDx) > 42.dp.toPx() && abs(totalDx) > abs(totalDy) * 1.15f) {
                                                if (totalDx < 0f && curZero < metrics.totalBookPages - 1) onTurnPage(1)
                                                else if (totalDx > 0f && curZero > 0) onTurnPage(-1)
                                            }
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            totalDx += dragAmount
                                            if (abs(totalDx) > 16.dp.toPx()) change.consume()
                                        }
                                    )
                                }
                                AnimatedContent(
                                    targetState = curZero,
                                    transitionSpec = {
                                        fadeIn(tween(220, easing = FastOutSlowInEasing)) togetherWith fadeOut(tween(180, easing = FastOutSlowInEasing)) using SizeTransform(clip = false)
                                    },
                                    label = "readerFadeTransition",
                                    modifier = Modifier.fillMaxSize().then(fadeSwipeModifier)
                                ) { pIdx ->
                                    RenderPageForNonFlipbook(pIdx)
                                }
                            } else {
                                val noAnimSwipeModifier = Modifier.pointerInput(curZero, metrics.totalBookPages) {
                                    var totalDx = 0f
                                    var totalDy = 0f
                                    detectHorizontalDragGestures(
                                        onDragStart = { totalDx = 0f; totalDy = 0f },
                                        onDragEnd = {
                                            if (abs(totalDx) > 42.dp.toPx() && abs(totalDx) > abs(totalDy) * 1.15f) {
                                                if (totalDx < 0f && curZero < metrics.totalBookPages - 1) onTurnPage(1)
                                                else if (totalDx > 0f && curZero > 0) onTurnPage(-1)
                                            }
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            totalDx += dragAmount
                                            if (abs(totalDx) > 16.dp.toPx()) change.consume()
                                        }
                                    )
                                }
                                Box(
                                    modifier = Modifier.fillMaxSize().then(noAnimSwipeModifier),
                                    contentAlignment = Alignment.Center
                                ) {
                                    RenderPageForNonFlipbook(curZero)
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
                                                            onDeleteHighlightNote = onDeleteHighlightNote,
                                                            onSearchInBook = { inBookSearchQuery = it },
                                                            bookTitle = book.title,
                                                            onSelectionActiveChanged = { active, start, end ->
                                                                hasSelectionActive = active
                                                                selStartHandleAnchor = start
                                                                selEndHandleAnchor = end
                                                            }
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

        // 2. OVERLAY TOP CHROME BAR (`#readerTopChrome` in `llunevie_prototype.html`)
        if (!uiState.readerFullView) {
            val cutoutStart = WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(LocalLayoutDirection.current)
            val topBarStartPad = if (isSpread) maxOf(36.dp, cutoutStart + 18.dp) else 16.dp
            Row(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(start = topBarStartPad, end = 16.dp, top = 10.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = palette.bgSurface,
                    border = BorderStroke(1.dp, palette.borderSoft),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        PrototypeSvgIcon(
                            type = PrototypeIconType.ARROW_LEFT,
                            tint = palette.textMain,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Compact single-line Title Pill (`#readerTitlePill`: max-w-[200px] height=30.dp, px-3.5, rounded-full, text-[11.5px])
                Surface(
                    onClick = { onToggleTocDrawer(true) },
                    shape = CircleShape,
                    color = palette.bgSurface,
                    border = BorderStroke(1.dp, palette.borderSoft),
                    modifier = Modifier
                        .height(30.dp)
                        .widthIn(max = 200.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = book.title,
                            color = palette.textMain,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 14.dp)
                        )
                    }
                }

                Surface(
                    onClick = { onToggleNotesDrawer(true) },
                    shape = CircleShape,
                    color = palette.bgSurface,
                    border = BorderStroke(1.dp, palette.borderSoft),
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        PrototypeSvgIcon(
                            type = if (bookHighlights.isNotEmpty()) PrototypeIconType.BOOKMARK_FILLED else PrototypeIconType.BOOKMARK_OUTLINE,
                            tint = palette.textMain,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        // 3. BOTTOM FLOATING CHROME BAR (`#readerBottomChrome` in `llunevie_prototype.html`)
        if (!uiState.readerFullView && !isSpread) {
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

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
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
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PrototypeSvgIcon(
                                type = PrototypeIconType.TOC_LIST,
                                tint = palette.textMain,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // In-Place Animated Bottom Scrubber Capsule (Never shrinks book viewport!)
                    AnimatedContent(
                        targetState = showPageScrubber,
                        transitionSpec = {
                            fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                        },
                        label = "bottomScrubberTransition",
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                            .height(40.dp)
                    ) { isScrubberOpen ->
                        if (!isScrubberOpen) {
                            Surface(
                                shape = CircleShape,
                                color = palette.bgSurface,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    if (showArrows) {
                                        Text(
                                            text = "‹",
                                            color = palette.textMain.copy(alpha = 0.75f),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .clickable {
                                                    if (prefs.animatePageTurn && prefs.pageAnimStyle == PageAnimStyle.FLIPBOOK) {
                                                        externalFlipTrigger = -1
                                                    } else {
                                                        onTurnPage(-1)
                                                    }
                                                }
                                                .padding(horizontal = 6.dp)
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
                                            .clickable { showPageScrubber = true }
                                            .padding(horizontal = 6.dp, vertical = 4.dp)
                                    )
                                    if (showArrows) {
                                        Text(
                                            text = "›",
                                            color = palette.textMain.copy(alpha = 0.75f),
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .clip(CircleShape)
                                                .clickable {
                                                    if (prefs.animatePageTurn && prefs.pageAnimStyle == PageAnimStyle.FLIPBOOK) {
                                                        externalFlipTrigger = 1
                                                    } else {
                                                        onTurnPage(1)
                                                    }
                                                }
                                                .padding(horizontal = 6.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = palette.bgSurface,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = "${scrubberPageValue.toInt().coerceIn(1, metrics.totalBookPages)} / ${metrics.totalBookPages}",
                                        color = palette.textMain,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    LlunevieThinSlider(
                                        value = scrubberPageValue,
                                        onValueChange = { newVal ->
                                            scrubberPageValue = newVal
                                            val target = newVal.toInt().coerceIn(1, metrics.totalBookPages)
                                            val targetZero = target - 1
                                            val triple = getPageAtGlobalZero(targetZero)
                                            if (triple != null) {
                                                onJumpToChapterAndPage(triple.first, triple.second)
                                            }
                                        },
                                        valueRange = 1f..metrics.totalBookPages.toFloat().coerceAtLeast(1f),
                                        modifier = Modifier.weight(1f)
                                    )
                                    Surface(
                                        onClick = { showPageScrubber = false },
                                        shape = CircleShape,
                                        color = palette.accentDark,
                                        modifier = Modifier.size(26.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text("✓", color = palette.bgApp, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Surface(
                        onClick = { onToggleTypeSheet(!uiState.showReaderTypeSheet, null) },
                        shape = CircleShape,
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Aa",
                                color = palette.textMain,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

        // Left Vertical Page Bar in 2-Page Spread Mode (Notch-safe with in-place transition to vertical page slider)
        if (!uiState.readerFullView && isSpread) {
            val cutoutStart = WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(LocalLayoutDirection.current)
            val leftBarStartPad = maxOf(36.dp, cutoutStart + 18.dp)
            var showInlinePageSlider by remember { mutableStateOf(false) }
            var scrubPage by remember(metrics.currentGlobalPage) {
                mutableIntStateOf((metrics.currentGlobalPage - 1).coerceAtLeast(0))
            }

            AnimatedContent(
                targetState = showInlinePageSlider,
                transitionSpec = {
                    (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                        scaleIn(initialScale = 0.92f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow)))
                        .togetherWith(
                            fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                scaleOut(targetScale = 0.92f, animationSpec = spring(stiffness = Spring.StiffnessMediumLow))
                        ) using SizeTransform(clip = false)
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(start = 18.dp),
                label = "leftBarSpreadTransition"
            ) { isSliderOpen ->
                if (!isSliderOpen) {
                    // Standard Vertical Control Capsule
                    Surface(
                        shape = CircleShape,
                        color = palette.bgSurface.copy(alpha = 0.96f),
                        border = BorderStroke(1.dp, palette.borderSoft),
                        shadowElevation = 8.dp,
                        modifier = Modifier.width(46.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Chapters & Illustrations button (32.dp circle)
                            Surface(
                                onClick = { onToggleTocDrawer(true) },
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    PrototypeSvgIcon(
                                        type = PrototypeIconType.TOC_LIST,
                                        tint = palette.textMain,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }

                            // 2. Previous Spread button (28.dp circle)
                            Surface(
                                onClick = {
                                    if (prefs.animatePageTurn && prefs.pageAnimStyle == PageAnimStyle.FLIPBOOK) {
                                        externalFlipTrigger = -1
                                    } else {
                                        onTurnPage(-1)
                                    }
                                },
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("⌃", color = palette.textMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // 3. Compact Clickable Page Indicator: tapping morphs capsule into vertical slider!
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        scrubPage = (metrics.currentGlobalPage - 1).coerceAtLeast(0)
                                        showInlinePageSlider = true
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "${metrics.currentGlobalPage}",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = palette.textPrimary,
                                    maxLines = 1,
                                    softWrap = false,
                                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(vertical = 3.dp)
                                        .width(14.dp)
                                        .height(1.dp)
                                        .background(palette.borderSoft)
                                )
                                Text(
                                    text = "${metrics.totalBookPages}",
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = palette.textMuted,
                                    maxLines = 1,
                                    softWrap = false,
                                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                )
                            }

                            // 4. Next Spread button (28.dp circle)
                            Surface(
                                onClick = {
                                    if (prefs.animatePageTurn && prefs.pageAnimStyle == PageAnimStyle.FLIPBOOK) {
                                        externalFlipTrigger = 1
                                    } else {
                                        onTurnPage(1)
                                    }
                                },
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("⌄", color = palette.textMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }

                            // 5. Aa Reader Settings button (32.dp circle)
                            Surface(
                                onClick = { onToggleTypeSheet(!uiState.showReaderTypeSheet, null) },
                                shape = CircleShape,
                                color = Color.Transparent,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "Aa",
                                        color = palette.textMain,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Vertical Page Scrubber Bar (width = 46.dp, height ≈ 230.dp, CircleShape, readerPalette.bgSurface, 1.dp readerPalette.borderSoft)
                    val totalPages = metrics.totalBookPages.coerceAtLeast(1)
                    Surface(
                        shape = CircleShape,
                        color = palette.bgSurface.copy(alpha = 0.98f),
                        border = BorderStroke(1.dp, palette.borderSoft),
                        shadowElevation = 10.dp,
                        modifier = Modifier
                            .width(46.dp)
                            .height(230.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Top: Live page number readout ("${scrubPage + 1}" bold monospace above "$totalPages" muted monospace)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(bottom = 4.dp)
                            ) {
                                Text(
                                    text = "${scrubPage + 1}",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = palette.textPrimary,
                                    maxLines = 1,
                                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                )
                                Text(
                                    text = "$totalPages",
                                    fontSize = 8.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = palette.textMuted,
                                    maxLines = 1,
                                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                )
                            }

                            // Middle: Interactive Vertical Slider Track & Thumb (dragging vertically from top = Page 1 to bottom = totalPages)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .pointerInput(totalPages) {
                                        detectVerticalDragGestures(
                                            onVerticalDrag = { change, _ ->
                                                change.consume()
                                                val trackH = size.height.toFloat().coerceAtLeast(1f)
                                                val fraction = (change.position.y / trackH).coerceIn(0f, 1f)
                                                val targetZero = (fraction * (totalPages - 1)).roundToInt().coerceIn(0, totalPages - 1)
                                                if (targetZero != scrubPage) {
                                                    scrubPage = targetZero
                                                    val step = if (isSpread) 2 else 1
                                                    val leftZero = if (isSpread) (scrubPage / 2) * 2 else scrubPage
                                                    val target = getPageAtGlobalZero(leftZero)
                                                    if (target != null) {
                                                        onJumpToChapterAndPage(target.first, target.second)
                                                    }
                                                }
                                            }
                                        )
                                    }
                                    .pointerInput(totalPages) {
                                        detectTapGestures { tapOffset ->
                                            val trackH = size.height.toFloat().coerceAtLeast(1f)
                                            val fraction = (tapOffset.y / trackH).coerceIn(0f, 1f)
                                            val targetZero = (fraction * (totalPages - 1)).roundToInt().coerceIn(0, totalPages - 1)
                                            if (targetZero != scrubPage) {
                                                scrubPage = targetZero
                                                val step = if (isSpread) 2 else 1
                                                val leftZero = if (isSpread) (scrubPage / 2) * 2 else scrubPage
                                                val target = getPageAtGlobalZero(leftZero)
                                                if (target != null) {
                                                    onJumpToChapterAndPage(target.first, target.second)
                                                }
                                            }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                // Background vertical track (width = 4.dp, height = fillMaxHeight, CircleShape, borderSoft)
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(palette.borderSoft)
                                )

                                // Active Vertical Thumb
                                val fraction = if (totalPages > 1) {
                                    scrubPage.toFloat() / (totalPages - 1).toFloat()
                                } else 0f

                                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                    val trackH = maxHeight
                                    val thumbSize = 16.dp
                                    val availableH = (trackH - thumbSize).coerceAtLeast(0.dp)
                                    val thumbOffset = availableH * fraction

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .offset(y = thumbOffset)
                                            .size(thumbSize)
                                            .shadow(3.dp, CircleShape)
                                            .clip(CircleShape)
                                            .background(palette.textPrimary)
                                            .border(1.5.dp, palette.bgSurface, CircleShape)
                                    )
                                }
                            }

                            // Bottom: Compact 28.dp circular close button that sets showInlinePageSlider = false
                            Surface(
                                onClick = { showInlinePageSlider = false },
                                shape = CircleShape,
                                color = palette.bgCard,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier
                                    .size(28.dp)
                                    .padding(top = 2.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "✕",
                                        color = palette.textMain,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
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
                                            onSaveHighlightNote(quote, existingHl?.noteText.orEmpty(), key, existingHl?.id, -1, -1)
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
                                        existingHl?.id,
                                        -1,
                                        -1
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
                                    existingHl?.id,
                                    -1,
                                    -1
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

        val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        // 4. IN-HIERARCHY READER TYPOGRAPHY & FLOW SHEET (`#readerTypeSheet` in `llunevie_prototype.html`)
        // Using an in-hierarchy overlay instead of ModalBottomSheet prevents WindowManager crashes during orientation switches!
        if (uiState.showReaderTypeSheet) {
            var isDraggingSettingsSlider by remember { mutableStateOf(false) }
            val scrimAlpha by animateFloatAsState(
                targetValue = if (isDraggingSettingsSlider) 0f else 0.35f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "settingsScrimAlpha"
            )
            val panelAlpha by animateFloatAsState(
                targetValue = if (isDraggingSettingsSlider) 0.04f else 1f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
                label = "settingsPanelAlpha"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onToggleTypeSheet(false, null) }
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    color = palette.bgSurface,
                    border = BorderStroke(1.dp, palette.borderSoft),
                    shadowElevation = if (isDraggingSettingsSlider) 0.dp else 24.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(min = 340.dp, max = 420.dp)
                        .fillMaxWidth()
                        .heightIn(max = if (isLandscape) 310.dp else 460.dp)
                        .alpha(panelAlpha)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {}
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 18.dp, vertical = 14.dp)
                    ) {
                        // Top Tab Switcher (TEXT | THEME | FLOW / ALUR | TEMA) + Close Button (✕)
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

                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(36.dp)
                                    .clip(CircleShape)
                                    .background(palette.bgCard)
                                    .border(1.dp, palette.borderSoft, CircleShape)
                                    .padding(3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                availableTabs.forEach { (tabKey, label) ->
                                    val isSelected = (effectiveTab == tabKey)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .then(
                                                if (isSelected) {
                                                    Modifier
                                                        .shadow(elevation = 1.dp, shape = CircleShape, clip = false)
                                                        .clip(CircleShape)
                                                        .background(palette.bgSurface, CircleShape)
                                                        .border(0.75.dp, palette.borderSoft, CircleShape)
                                                } else {
                                                    Modifier.clip(CircleShape)
                                                }
                                            )
                                            .clickable { onToggleTypeSheet(true, tabKey) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                            color = if (isSelected) palette.textPrimary else palette.textSecondary,
                                            maxLines = 1,
                                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(10.dp))
                            Surface(
                                onClick = { onToggleTypeSheet(false, null) },
                                shape = CircleShape,
                                color = palette.bgSurface,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Canvas(modifier = Modifier.size(11.dp)) {
                                        val strokePx = 1.5.dp.toPx()
                                        drawLine(
                                            color = palette.textPrimary,
                                            start = Offset(0f, 0f),
                                            end = Offset(size.width, size.height),
                                            strokeWidth = strokePx,
                                            cap = StrokeCap.Round
                                        )
                                        drawLine(
                                            color = palette.textPrimary,
                                            start = Offset(size.width, 0f),
                                            end = Offset(0f, size.height),
                                            strokeWidth = strokePx,
                                            cap = StrokeCap.Round
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val currentTabKey = if (isPdfBook && uiState.activeReaderSheetTab == "text") "turn" else uiState.activeReaderSheetTab

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
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
                                            onValueChange = {
                                                isDraggingSettingsSlider = true
                                                onUpdateTypography(null, it.roundToInt(), null, null, null, null, null, null, null, null)
                                            },
                                            onValueChangeFinished = { isDraggingSettingsSlider = false },
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
                                        val readerFontContext = LocalContext.current
                                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            CURATED_READER_FONTS.chunked(3).forEach { rowFonts ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    rowFonts.forEach { (famKey, famDisplay) ->
                                                        val selected = prefs.fontFamily == famKey
                                                        val chipFontFamily = remember(famKey) { getReaderFontFamily(readerFontContext, famKey) }
                                                        Surface(
                                                            onClick = { onUpdateTypography(famKey, null, null, null, null, null, null, null, null, null) },
                                                            shape = RoundedCornerShape(8.dp),
                                                            color = if (selected) palette.textPrimary else palette.bgSurface,
                                                            border = BorderStroke(1.dp, if (selected) palette.textPrimary else palette.borderSoft),
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .height(32.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                Text(
                                                                    text = famDisplay,
                                                                    color = if (selected) palette.bgApp else palette.textPrimary,
                                                                    fontSize = 11.sp,
                                                                    fontFamily = chipFontFamily,
                                                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    textAlign = TextAlign.Center
                                                                )
                                                            }
                                                        }
                                                    }
                                                    if (rowFonts.size < 3) {
                                                        repeat(3 - rowFonts.size) {
                                                            Spacer(modifier = Modifier.weight(1f))
                                                        }
                                                    }
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
                                            val isSystemDark = isSystemInDarkTheme()
                                            val effectiveTheme = prefs.effectiveTheme(isSystemDark)
                                            val themeColors = listOf(
                                                Triple(AppThemeMode.PAPER, Color(0xFFF5F2EA), Color(0xFF23201A)),
                                                Triple(AppThemeMode.WHITE, Color(0xFFFFFFFF), Color(0xFF18181B)),
                                                Triple(AppThemeMode.SEPIA, Color(0xFFEFE6D5), Color(0xFF2C2217)),
                                                Triple(AppThemeMode.BLACK, Color(0xFF141413), Color(0xFFEDEAE2))
                                            )
                                            themeColors.forEach { (mode, chipBg, chipFg) ->
                                                val active = effectiveTheme == mode
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
                                                      onValueChange = {
                                                          isDraggingSettingsSlider = true
                                                          onUpdateTypography(null, null, null, null, it, null, null, null, null, null)
                                                      },
                                                      onValueChangeFinished = { isDraggingSettingsSlider = false },
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
                                                      onValueChange = {
                                                          isDraggingSettingsSlider = true
                                                          onUpdateTypography(null, null, null, null, null, it, null, null, null, null)
                                                      },
                                                      onValueChangeFinished = { isDraggingSettingsSlider = false },
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
                                                      onValueChange = {
                                                          isDraggingSettingsSlider = true
                                                          onUpdateTypography(null, null, null, null, null, null, it, null, null, null)
                                                      },
                                                      onValueChangeFinished = { isDraggingSettingsSlider = false },
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
                                                      onValueChange = {
                                                          isDraggingSettingsSlider = true
                                                          onUpdateTypography(null, null, null, null, null, null, null, it.roundToInt(), null, null)
                                                      },
                                                      onValueChangeFinished = { isDraggingSettingsSlider = false },
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
                                                      onValueChange = {
                                                          isDraggingSettingsSlider = true
                                                          onUpdateTypography(null, null, null, null, null, null, null, null, it.roundToInt(), null)
                                                      },
                                                      onValueChangeFinished = { isDraggingSettingsSlider = false },
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

                                                   Row(
                                                       modifier = Modifier
                                                           .weight(1f)
                                                           .heightIn(min = 62.dp)
                                                           .clip(RoundedCornerShape(14.dp))
                                                           .background(if (selected) palette.textPrimary else palette.bgCard)
                                                           .border(1.dp, if (selected) palette.textPrimary else palette.borderSoft, RoundedCornerShape(14.dp))
                                                           .clickable { onSetReadingFlowMode(mode) }
                                                           .padding(horizontal = 12.dp, vertical = 10.dp),
                                                       verticalAlignment = Alignment.CenterVertically
                                                   ) {
                                                       PrototypeSvgIcon(
                                                           type = modeIcon,
                                                           tint = if (selected) palette.bgApp else palette.textSecondary,
                                                           modifier = Modifier.size(16.dp),
                                                           strokeWidthDp = 1.8f
                                                       )
                                                       Spacer(modifier = Modifier.width(10.dp))
                                                       Column(modifier = Modifier.weight(1f)) {
                                                           Text(
                                                               text = mode.localizedLabel(prefs.lang),
                                                               fontSize = 12.5.sp,
                                                               lineHeight = 15.sp,
                                                               fontWeight = FontWeight.SemiBold,
                                                               color = if (selected) palette.bgApp else palette.textPrimary,
                                                               maxLines = 1,
                                                               overflow = TextOverflow.Ellipsis,
                                                               style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                                           )
                                                           Spacer(modifier = Modifier.height(2.dp))
                                                           Text(
                                                               text = mode.localizedDesc(prefs.lang),
                                                               fontSize = 10.sp,
                                                               lineHeight = 13.sp,
                                                               color = if (selected) palette.bgApp.copy(alpha = 0.78f) else palette.textSecondary,
                                                               maxLines = 1,
                                                               overflow = TextOverflow.Ellipsis,
                                                               style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                                           )
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
                        .fillMaxWidth(if (isSpread || isLandscape) 0.38f else 0.82f)
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
                            .padding(horizontal = 14.dp, vertical = 14.dp),
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
                                        text = if (strings.isEn) "${book.displayFormatLabel(true)} · ${chapters.size} chapters" else "${book.displayFormatLabel(false)} · ${chapters.size} bab",
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

                            val allIllusts = uiState.activeBookIllustrations
                            val pdfIndexingProgress = uiState.pdfIndexingState[book.id]

                            if (pdfIndexingProgress != null) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 10.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(palette.bgCard)
                                        .border(1.dp, palette.borderSoft, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = palette.textPrimary
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (strings.isEn) "Indexing chapters & illustrations..." else "Mengindeks daftar bab & ilustrasi...",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = palette.textPrimary
                                        )
                                        Text(
                                            text = "${pdfIndexingProgress}%",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = palette.textSecondary
                                        )
                                    }
                                }
                            }

                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Section 1: Pure chapter rows
                                itemsIndexed(chapters, key = { idx, _ -> "toc_ch_$idx" }) { idx, ch ->
                                    val active = idx == uiState.activeChapterIndex
                                    Surface(
                                        onClick = {
                                            onJumpToChapterAndPage(idx, 0)
                                            onToggleTocDrawer(false)
                                        },
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
                                                    text = sanitizeBookOrChapterText(localizeChapterTitle(ch.title, strings.isEn)),
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

                                // Section 2: Insert Illustrations (Directly below chapters in 2-column grid!)
                                if (allIllusts.isNotEmpty()) {
                                    item(key = "toc_illust_header") {
                                        Spacer(modifier = Modifier.height(14.dp))
                                        Text(
                                            text = (if (strings.isEn) "INSERT ILLUSTRATIONS" else "ILUSTRASI SISIPAN") + " (${allIllusts.size})",
                                            color = palette.textMuted,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.2.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }

                                    val rows = allIllusts.chunked(2)
                                    itemsIndexed(rows, key = { rIdx, _ -> "toc_illust_row_$rIdx" }) { rIdx, pair ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            pair.forEachIndexed { colIdx, illust ->
                                                val globalIdx = rIdx * 2 + colIdx
                                                val pNum = illust.pageIndexInBook + 1
                                                val parentChTitle = chapters.find { ch ->
                                                    val target = getPageAtGlobalZero(illust.pageIndexInBook)
                                                    target?.first == ch.index
                                                }?.title ?: ""

                                                Surface(
                                                    onClick = {
                                                        val target = getPageAtGlobalZero(illust.pageIndexInBook)
                                                        if (target != null) {
                                                            onJumpToChapterAndPage(target.first, target.second)
                                                        }
                                                        onToggleTocDrawer(false)
                                                    },
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = palette.bgCard,
                                                    border = BorderStroke(1.dp, palette.borderSoft),
                                                    modifier = Modifier.weight(1f)
                                                ) {
                                                    Column(modifier = Modifier.padding(6.dp)) {
                                                        AsyncImage(
                                                            model = File(illust.uriOrPath),
                                                            contentDescription = illust.caption,
                                                            contentScale = ContentScale.Crop,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .aspectRatio(0.74f)
                                                                .clip(RoundedCornerShape(10.dp))
                                                                .clickable { onOpenIllustViewer(globalIdx) }
                                                        )
                                                        Spacer(modifier = Modifier.height(6.dp))
                                                        Text(
                                                            text = if (strings.isEn) "Page $pNum" else "Hal $pNum",
                                                            color = palette.textMain,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis
                                                        )
                                                        if (parentChTitle.isNotBlank()) {
                                                            Text(
                                                                text = sanitizeBookOrChapterText(parentChTitle),
                                                                color = palette.textMuted,
                                                                fontSize = 9.5.sp,
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
                        .fillMaxWidth(if (isSpread || isLandscape) 0.38f else 0.84f)
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
                            .padding(14.dp)
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
                                                    onSaveHighlightNote(finalQuote, manualNoteText.trim(), selectedNoteColor, null, -1, -1)
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
            var panOffset by remember(uiState.activeIllustIndex) { mutableStateOf(Offset.Zero) }
            val currentZoom = manualScale.coerceIn(1f, 4f)

            val pdfPath = book.localFilePath.orEmpty()
            var pdfHighResBmp by remember(activeItem.pageIndexInBook, pdfPath) {
                mutableStateOf(if (isPdfBook && pdfPath.isNotBlank()) PdfNativeRenderer.getCachedHighResIllustration(pdfPath, activeItem.pageIndexInBook) else null)
            }
            var epubHighResBmp by remember(activeItem.uriOrPath) {
                mutableStateOf(if (!isPdfBook) EpubNativeParser.getCachedFullResImage(activeItem.uriOrPath) else null)
            }

            LaunchedEffect(uiState.activeIllustIndex, isPdfBook, pdfPath, activeItem.uriOrPath) {
                withContext(Dispatchers.IO) {
                    if (isPdfBook && pdfPath.isNotBlank()) {
                        val bmp = PdfNativeRenderer.renderHighResIllustrationPage(pdfPath, activeItem.pageIndexInBook, 1800)
                        withContext(Dispatchers.Main) {
                            pdfHighResBmp = bmp
                        }
                        // Prefetch next and previous illustrations
                        val nextIdx = uiState.activeIllustIndex + 1
                        if (nextIdx < illusts.size) {
                            PdfNativeRenderer.renderHighResIllustrationPage(pdfPath, illusts[nextIdx].pageIndexInBook, 1800)
                        }
                        val prevIdx = uiState.activeIllustIndex - 1
                        if (prevIdx >= 0) {
                            PdfNativeRenderer.renderHighResIllustrationPage(pdfPath, illusts[prevIdx].pageIndexInBook, 1800)
                        }
                    } else if (!isPdfBook) {
                        val bmp = EpubNativeParser.decodeFullResImage(activeItem.uriOrPath)
                        withContext(Dispatchers.Main) {
                            epubHighResBmp = bmp
                        }
                        // Prefetch next and previous illustrations
                        val nextIdx = uiState.activeIllustIndex + 1
                        if (nextIdx < illusts.size) {
                            EpubNativeParser.decodeFullResImage(illusts[nextIdx].uriOrPath)
                        }
                        val prevIdx = uiState.activeIllustIndex - 1
                        if (prevIdx >= 0) {
                            EpubNativeParser.decodeFullResImage(illusts[prevIdx].uriOrPath)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.bgApp)
                    .clickable(enabled = false) {}
            ) {
                // Top Bar (Row with statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val pageNum = activeItem.pageIndexInBook + 1
                    val pageLabel = if (strings.isEn) "Page $pageNum" else "Hal $pageNum"

                    // Left — Refined Counter & Page Pill
                    Surface(
                        shape = CircleShape,
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${uiState.activeIllustIndex + 1} / ${illusts.size}",
                                fontSize = 11.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textPrimary
                            )
                            Text(
                                text = " · ",
                                fontSize = 11.sp,
                                color = palette.textMuted
                            )
                            Text(
                                text = pageLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = palette.textSecondary
                            )
                        }
                    }

                    // Right — Clean Circular Close (✕) Button
                    Surface(
                        onClick = onCloseIllustViewer,
                        shape = CircleShape,
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PrototypeSvgIcon(
                                type = PrototypeIconType.CLOSE_X,
                                tint = palette.textPrimary,
                                modifier = Modifier.size(12.dp),
                                strokeWidthDp = 1.5f
                            )
                        }
                    }
                }

                // Interactive Pinch-to-Zoom & Drag-to-Pan Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(palette.bgApp)
                        .clipToBounds()
                        .pointerInput(uiState.activeIllustIndex) {
                            detectTapGestures(
                                onDoubleTap = {
                                    if (manualScale > 1.2f) {
                                        manualScale = 1f
                                        panOffset = Offset.Zero
                                    } else {
                                        manualScale = 2.2f
                                        panOffset = Offset.Zero
                                    }
                                }
                            )
                        }
                        .pointerInput(uiState.activeIllustIndex) {
                            detectTransformGestures { centroid: Offset, pan: Offset, zoom: Float, rotation: Float ->
                                val nextScale = (manualScale * zoom).coerceIn(1f, 4f)
                                manualScale = nextScale
                                if (nextScale > 1.02f) {
                                    val maxPanX = (size.width.toFloat() * (nextScale - 1f)) / 2f
                                    val maxPanY = (size.height.toFloat() * (nextScale - 1f)) / 2f
                                    val nextPanX = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX)
                                    val nextPanY = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                    panOffset = Offset(nextPanX, nextPanY)
                                } else {
                                    panOffset = Offset.Zero
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp)
                            .graphicsLayer {
                                scaleX = currentZoom
                                scaleY = currentZoom
                                translationX = if (currentZoom > 1.02f) panOffset.x else 0f
                                translationY = if (currentZoom > 1.02f) panOffset.y else 0f
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = File(activeItem.uriOrPath),
                            contentDescription = activeItem.caption,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                        val displayBitmap = if (isPdfBook) pdfHighResBmp else epubHighResBmp
                        if (displayBitmap != null && !displayBitmap.isRecycled) {
                            Image(
                                bitmap = displayBitmap.asImageBitmap(),
                                contentDescription = activeItem.caption,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // Bottom Bar (Column with navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (activeItem.caption.isNotBlank()) {
                        Text(
                            text = activeItem.caption,
                            color = palette.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val canGoPrev = uiState.activeIllustIndex > 0
                        val canGoNext = uiState.activeIllustIndex < illusts.size - 1

                        Surface(
                            onClick = {
                                if (canGoPrev) {
                                    manualScale = 1f
                                    panOffset = Offset.Zero
                                    onOpenIllustViewer(uiState.activeIllustIndex - 1)
                                }
                            },
                            enabled = canGoPrev,
                            shape = CircleShape,
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .alpha(if (canGoPrev) 1.0f else 0.4f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (strings.isEn) "‹  Previous" else "‹  Sebelumnya",
                                    color = palette.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                if (canGoNext) {
                                    manualScale = 1f
                                    panOffset = Offset.Zero
                                    onOpenIllustViewer(uiState.activeIllustIndex + 1)
                                }
                            },
                            enabled = canGoNext,
                            shape = CircleShape,
                            color = palette.textPrimary,
                            border = BorderStroke(1.dp, palette.textPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .alpha(if (canGoNext) 1.0f else 0.4f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (strings.isEn) "Next  ›" else "Berikutnya  ›",
                                    color = palette.bgApp,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
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
    currentChapterIndex: Int = 0,
    currentPageInChapter: Int = 0,
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
    onSelectionActiveChanged: (Boolean, Offset, Offset) -> Unit = { _, _, _ -> }
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
    ) || (rawParagraphs.size == 1 && rawParagraphs[0].startsWith("[ILLUST:") && !showChapterHeader)

    // 1. Natural Aspect-Ratio EPUB Cover (Never Stretched or Cropped!)
    if (isCover) {
        val illustPara = rawParagraphs.firstOrNull { it.startsWith("[ILLUST:") }
        val imgPath = illustPara?.removePrefix("[ILLUST:")?.substringBefore("|")?.substringBefore("]")
        if (imgPath != null && File(imgPath).exists()) {
            val coverBitmap = remember(imgPath) {
                runCatching { BitmapFactory.decodeFile(imgPath) }.getOrNull()
            }
            if (coverBitmap != null) {
                val ratio = (coverBitmap.width.toFloat() / coverBitmap.height.toFloat()).coerceIn(0.58f, 0.80f)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { onIllustrationClicked(imgPath) },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = coverBitmap.asImageBitmap(),
                        contentDescription = bookTitle.ifBlank { "Cover" },
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(ratio, matchHeightConstraintsFirst = false)
                            .clip(RoundedCornerShape(10.dp))
                    )
                }
                return
            }
        }
    }

    val textParagraphs = remember(rawParagraphs) {
        rawParagraphs.filter { !it.startsWith("[ILLUST:") }
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

    val lineHeightSp = (prefs.fontSizeSp * fontScale * prefs.lineHeightMultiplier).sp
    val paraGapSp = (prefs.fontSizeSp * fontScale * prefs.paraSpacingEm.coerceIn(0.2f, 2.5f) * 0.85f).sp
    val wordExtraSp = (prefs.wordSpacingPx * 0.28f).sp
    val indentSpec = if (prefs.paraIndent) {
        TextIndent(firstLine = (prefs.fontSizeSp * fontScale * 1.6f).sp)
    } else {
        TextIndent.None
    }

    // Build unified AnnotatedString with ParagraphStyle, word spacing, indent, and pastel highlights
    val annotatedString = remember(
        textParagraphs, prefs.paraSpacingEm, prefs.wordSpacingPx, prefs.paraIndent,
        prefs.pageMargin, prefs.letterSpacingEm, prefs.lineHeightMultiplier, prefs.fontSizeSp,
        fontScale, prefs.fontWeight, prefs.textAlign, prefs.fontFamily, bookHighlights,
        currentChapterIndex, currentPageInChapter, palette
    ) {
        buildAnnotatedString {
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

                if (prefs.wordSpacingPx > 0) {
                    for (cIdx in paraStart until paraEnd) {
                        if (cleanPara[cIdx - paraStart] == ' ') {
                            addStyle(SpanStyle(letterSpacing = wordExtraSp), cIdx, cIdx + 1)
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
            bookHighlights.filter { hl ->
                hl.chapterIndex == currentChapterIndex && hl.pageIndex == currentPageInChapter
            }.forEach { hl ->
                if (hl.startCharIndex in 0 until fullText.length &&
                    hl.endCharIndex in (hl.startCharIndex + 1)..fullText.length) {
                    val colorKey = HighlightColorKey.fromKey(hl.colorKey)
                    addStyle(
                        SpanStyle(
                            background = palette.highlightBg(colorKey),
                            color = Color(0xFF18181B)
                        ),
                        hl.startCharIndex,
                        hl.endCharIndex
                    )
                } else {
                    val quote = hl.quoteText.trim()
                    if (quote.isNotBlank()) {
                        val found = fullText.indexOf(quote, ignoreCase = true)
                        if (found >= 0) {
                            val colorKey = HighlightColorKey.fromKey(hl.colorKey)
                            addStyle(
                                SpanStyle(
                                    background = palette.highlightBg(colorKey),
                                    color = Color(0xFF18181B)
                                ),
                                found,
                                found + quote.length
                            )
                        }
                    }
                }
            }
        }
    }

    val fullPageText = annotatedString.text

    var selStart by remember(pageText) { mutableIntStateOf(-1) }
    var selEnd by remember(pageText) { mutableIntStateOf(-1) }
    var activeDraggingHandle by remember { mutableStateOf<Int?>(null) } // 0 = start handle, 1 = end handle
    var selectedHighlightId by remember(pageText) { mutableStateOf<String?>(null) }
    val hasActiveSelection = selStart >= 0 && selEnd > selStart && selEnd <= fullPageText.length
    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val pageTextStyle = TextStyle(
        color = palette.textMain,
        fontSize = (prefs.fontSizeSp * fontScale).sp,
        letterSpacing = (prefs.letterSpacingEm * prefs.fontSizeSp * fontScale).sp,
        fontFamily = resolvedFontFamily,
        fontWeight = resolvedFontWeight,
        platformStyle = PlatformTextStyle(includeFontPadding = false)
    )

    val layout = textLayoutResult
    val startBox = remember(layout, selStart, fullPageText.length) {
        if (layout != null && selStart in 0 until fullPageText.length) {
            layout.getBoundingBox(selStart)
        } else Rect.Zero
    }
    val endBox = remember(layout, selEnd, fullPageText.length) {
        if (layout != null && selEnd in 1..fullPageText.length) {
            layout.getBoundingBox(selEnd - 1)
        } else Rect.Zero
    }
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

    val padX = with(density) { horizontalMarginDp.toPx() }
    val padTop = with(density) { verticalMarginDp.toPx() }

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

    val outerScrollModifier = if (scrollable) {
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    } else {
        Modifier.fillMaxSize()
    }

    Box(
        modifier = outerScrollModifier
            .padding(start = horizontalMarginDp, end = horizontalMarginDp, top = verticalMarginDp, bottom = verticalMarginDp + 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
                        .padding(bottom = 16.dp)
                )
            }

            // Inline illustrations inside the chapter
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

            // Deterministic Single-Text Selection Canvas
            Box(modifier = Modifier.fillMaxWidth()) {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val availHeightPx = with(density) { maxHeight.toPx() }
                    Text(
                        text = annotatedString,
                        style = pageTextStyle,
                        onTextLayout = { textLayoutResult = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .drawWithContent {
                                val l = textLayoutResult
                                if (l != null && availHeightPx > 0f && l.size.height > availHeightPx) {
                                    val lastCompleteLine = (0 until l.lineCount)
                                        .lastOrNull { l.getLineBottom(it) <= availHeightPx + 2f }
                                        ?: (l.lineCount - 1)
                                    val safeClipBottom = l.getLineBottom(lastCompleteLine)
                                    clipRect(0f, 0f, size.width, safeClipBottom) {
                                        this@drawWithContent.drawContent()
                                    }
                                } else {
                                    drawContent()
                                }

                                if (hasActiveSelection && l != null && fullPageText.isNotEmpty()) {
                                val safeStart = selStart.coerceIn(0, fullPageText.lastIndex)
                                val safeEnd = (selEnd - 1).coerceIn(safeStart, fullPageText.lastIndex)

                                // 1. Draw active selection highlight rects using l.getPathForRange
                                val selectionPath = l.getPathForRange(safeStart, safeEnd + 1)
                                drawPath(
                                    path = selectionPath,
                                    color = Color(0xFF0EA5E9).copy(alpha = 0.24f)
                                )

                                val startRect = l.getCursorRect(safeStart)
                                val endRect = l.getCursorRect((safeEnd + 1).coerceAtMost(fullPageText.length))
                                val barColor = Color(0xFF0284C7)
                                val barStroke = 2.dp.toPx()
                                val handleRadius = 7.dp.toPx()

                                // Left (Start) vertical bar + bottom teardrop circle
                                drawLine(
                                    color = barColor,
                                    start = Offset(startRect.left, startRect.top),
                                    end = Offset(startRect.left, startRect.bottom),
                                    strokeWidth = barStroke
                                )
                                drawCircle(
                                    color = barColor,
                                    radius = handleRadius,
                                    center = Offset(startRect.left, startRect.bottom + handleRadius)
                                )

                                // Right (End) vertical bar + bottom teardrop circle
                                drawLine(
                                    color = barColor,
                                    start = Offset(endRect.left, endRect.top),
                                    end = Offset(endRect.left, endRect.bottom),
                                    strokeWidth = barStroke
                                )
                                drawCircle(
                                    color = barColor,
                                    radius = handleRadius,
                                    center = Offset(endRect.left, endRect.bottom + handleRadius)
                                )
                            }
                        }
                        .pointerInput(fullPageText, hasActiveSelection, selStart, selEnd) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val l = textLayoutResult ?: return@awaitEachGesture
                                if (fullPageText.isEmpty()) return@awaitEachGesture

                                if (selStart >= 0 && selEnd > selStart) {
                                    val safeStart = selStart.coerceIn(0, fullPageText.lastIndex)
                                    val safeEnd = (selEnd - 1).coerceIn(safeStart, fullPageText.lastIndex)
                                    val startRect = l.getCursorRect(safeStart)
                                    val endRect = l.getCursorRect((safeEnd + 1).coerceAtMost(fullPageText.length))
                                    val handleRadius = 7.dp.toPx()
                                    val startHandlePos = Offset(startRect.left, startRect.bottom + handleRadius)
                                    val endHandlePos = Offset(endRect.left, endRect.bottom + handleRadius)
                                    val hitRadius = 36.dp.toPx()

                                    val distStart = (down.position - startHandlePos).getDistance()
                                    val distEnd = (down.position - endHandlePos).getDistance()

                                    if (distStart <= hitRadius || distEnd <= hitRadius) {
                                        val draggingStart = distStart <= distEnd
                                        activeDraggingHandle = if (draggingStart) 0 else 1
                                        down.consume()
                                        val touchOffset = if (draggingStart) {
                                            Offset(startRect.left, startRect.center.y) - down.position
                                        } else {
                                            Offset(endRect.left, endRect.center.y) - down.position
                                        }

                                        while (true) {
                                            val event = awaitPointerEvent(PointerEventPass.Initial)
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) {
                                                change.consume()
                                                break
                                            }
                                            change.consume()
                                            val targetPoint = Offset(
                                                x = (change.position.x + touchOffset.x).coerceIn(0f, l.size.width.toFloat()),
                                                y = (change.position.y + touchOffset.y).coerceIn(0f, l.size.height.toFloat())
                                            )
                                            val rawIdx = l.getOffsetForPosition(targetPoint)
                                            if (draggingStart) {
                                                selStart = rawIdx.coerceIn(0, (selEnd - 1).coerceAtLeast(0))
                                            } else {
                                                selEnd = rawIdx.coerceIn(selStart + 1, fullPageText.length)
                                            }
                                        }
                                        activeDraggingHandle = null
                                        return@awaitEachGesture
                                    }
                                }
                            }
                        }
                        .pointerInput(fullPageText) {
                            detectTapGestures(
                                onLongPress = { pressPos ->
                                    val l = textLayoutResult ?: return@detectTapGestures
                                    val rawOffset = l.getOffsetForPosition(pressPos).coerceIn(0, fullPageText.length)
                                    var wStart = rawOffset
                                    while (wStart > 0 && !fullPageText[wStart - 1].isWhitespace() && fullPageText[wStart - 1] !in setOf('.', ',', ';', ':', '!', '?', '"', '\'', '—', '–', '(', ')')) {
                                        wStart--
                                    }
                                    var wEnd = rawOffset
                                    while (wEnd < fullPageText.length && !fullPageText[wEnd].isWhitespace() && fullPageText[wEnd] !in setOf('.', ',', ';', ':', '!', '?', '"', '\'', '—', '–', '(', ')')) {
                                        wEnd++
                                    }
                                    if (wEnd <= wStart) {
                                        wEnd = (wStart + 1).coerceAtMost(fullPageText.length)
                                    }
                                    selStart = wStart
                                    selEnd = wEnd
                                    selectedHighlightId = null
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onTap = { tapPos ->
                                    val l = textLayoutResult ?: return@detectTapGestures
                                    if (hasActiveSelection) {
                                        selStart = -1
                                        selEnd = -1
                                        selectedHighlightId = null
                                        return@detectTapGestures
                                    }
                                    val tapOffset = l.getOffsetForPosition(tapPos).coerceIn(0, fullPageText.length)
                                    val matchedHl = bookHighlights.find { hl ->
                                        if (hl.chapterIndex == currentChapterIndex && hl.pageIndex == currentPageInChapter) {
                                            if (hl.startCharIndex in 0 until fullPageText.length && hl.endCharIndex in (hl.startCharIndex + 1)..fullPageText.length) {
                                                tapOffset in hl.startCharIndex until hl.endCharIndex
                                            } else {
                                                val q = hl.quoteText.trim()
                                                val idx = fullPageText.indexOf(q, ignoreCase = true)
                                                idx >= 0 && tapOffset in idx until (idx + q.length)
                                            }
                                        } else false
                                    }
                                    if (matchedHl != null) {
                                        if (matchedHl.startCharIndex in 0 until fullPageText.length && matchedHl.endCharIndex in (matchedHl.startCharIndex + 1)..fullPageText.length) {
                                            selStart = matchedHl.startCharIndex
                                            selEnd = matchedHl.endCharIndex
                                        } else {
                                            val idx = fullPageText.indexOf(matchedHl.quoteText.trim(), ignoreCase = true)
                                            if (idx >= 0) {
                                                selStart = idx
                                                selEnd = idx + matchedHl.quoteText.trim().length
                                            }
                                        }
                                        selectedHighlightId = matchedHl.id
                                    }
                                }
                            )
                        }
                )
                }
            }
        }

        // 5. Floating Highlight & Note Popover (#highlightPopover)
        if (hasActiveSelection && activeDraggingHandle == null && layout != null) {
            val selectedText = fullPageText.substring(selStart, selEnd).trim()
            val popoverY = if (startBox.top < 80f) {
                (endBox.bottom + 12f).roundToInt()
            } else {
                with(density) { (startBox.top - 64.dp.toPx()).roundToInt().coerceAtLeast(0) }
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = palette.bgSurface,
                border = BorderStroke(1.dp, palette.borderSoft),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset { IntOffset(0, popoverY) }
                    .zIndex(100f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 4 Pastel color swatches (Amber #FEF3C7, Sky #DBEAFE, Rose #FCE7F3, Mint #DCFCE7)
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

                    // + Note button
                    Surface(
                        onClick = {
                            onParagraphClicked(selectedText, startBox.top <= 200f)
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

                    // Copy button
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

                    // Delete button (when an existing highlight is tapped/selected)
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

@Composable
fun PdfPageItemContent(
    bookId: String,
    filePath: String,
    pageIndex: Int,
    viewModel: LlunevieViewModel,
    modifier: Modifier = Modifier
) {
    val palette = LocalLluneviePalette.current
    // Initialize synchronously from LRU cache so cached pages render on Frame 0 with ZERO null blink!
    var pageBitmap by remember(bookId, pageIndex) {
        mutableStateOf(viewModel.getCachedPdfBitmap(bookId, pageIndex))
    }
    LaunchedEffect(bookId, filePath, pageIndex) {
        if (pageBitmap == null) {
            pageBitmap = viewModel.loadPdfPageBitmap(bookId, filePath, pageIndex)
        }
        // Prefetch neighbors so Slide and Fade already have pageIndex ± 1 in cache!
        viewModel.prefetchPdfNeighbors(bookId, filePath, pageIndex)
    }
    Box(
        modifier = modifier.fillMaxSize().background(palette.bgApp),
        contentAlignment = Alignment.Center
    ) {
        val bmp = pageBitmap
        if (bmp != null) {
            val ratio = if (bmp.height > 0) (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.45f, 1.6f) else 0.707f
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.White,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(ratio)
                    .padding(vertical = 4.dp)
            ) {
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Page ${pageIndex + 1}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

private fun renderEpubPageSnapshot(
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
    isCover: Boolean,
    highlights: List<HighlightNoteEntity>,
    chapterIndex: Int,
    pageIndex: Int,
    palette: com.llunaticsys.llunevie.ui.theme.LluneviePalette
): ImageBitmap {
    val bitmap = ImageBitmap(widthPx.coerceAtLeast(100), heightPx.coerceAtLeast(100))
    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
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
                val coverBmp = runCatching { BitmapFactory.decodeFile(imgPath) }.getOrNull()
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

        val textParagraphs = rawParagraphs.filter { !it.startsWith("[ILLUST:") }
        val fullPageText = textParagraphs.joinToString("\n\n")

        val annotatedString = buildAnnotatedString {
            append(fullPageText)
            highlights.filter { hl ->
                hl.chapterIndex == chapterIndex && hl.pageIndex == pageIndex
            }.forEach { hl ->
                if (hl.startCharIndex in 0 until fullPageText.length &&
                    hl.endCharIndex in (hl.startCharIndex + 1)..fullPageText.length) {
                    val colorKey = HighlightColorKey.fromKey(hl.colorKey)
                    addStyle(
                        SpanStyle(
                            background = palette.highlightBg(colorKey),
                            color = Color(0xFF18181B)
                        ),
                        hl.startCharIndex,
                        hl.endCharIndex
                    )
                } else {
                    val quote = hl.quoteText.trim()
                    if (quote.isNotBlank()) {
                        val found = fullPageText.indexOf(quote, ignoreCase = true)
                        if (found >= 0) {
                            val colorKey = HighlightColorKey.fromKey(hl.colorKey)
                            addStyle(
                                SpanStyle(
                                    background = palette.highlightBg(colorKey),
                                    color = Color(0xFF18181B)
                                ),
                                found,
                                found + quote.length
                            )
                        }
                    }
                }
            }
        }

        val contentW = (widthPx - padX * 2).roundToInt().coerceAtLeast(100)
        val layout = textMeasurer.measure(
            text = annotatedString,
            style = pageTextStyle,
            constraints = Constraints(maxWidth = contentW)
        )
        drawText(layout, topLeft = Offset(padX, padTop))
    }
    return bitmap
}

private fun renderTwoPageSpreadSnapshot(
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
    palette: com.llunaticsys.llunevie.ui.theme.LluneviePalette
): ImageBitmap {
    val bitmap = ImageBitmap(widthPx.coerceAtLeast(100), heightPx.coerceAtLeast(100))
    val canvas = androidx.compose.ui.graphics.Canvas(bitmap)
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
            color = palette.borderSoft,
            start = Offset(spineX, 0f),
            end = Offset(spineX, size.height),
            strokeWidth = with(density) { 1.dp.toPx() }
        )

        val colPadHoriz = with(density) { 14.dp.toPx() }
        val headerH = with(density) { 22.dp.toPx() }
        val footerH = with(density) { 20.dp.toPx() }
        val colInnerW = (halfW - colPadHoriz * 2).roundToInt().coerceAtLeast(100)
        val colInnerH = (size.height - headerH - footerH - 12f).coerceAtLeast(100f)

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
            drawText(hLayout, topLeft = Offset(colPadHoriz, 6f))
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
            drawText(rhLayout, topLeft = Offset(halfW + colPadHoriz, 6f))
        }
        val rightPageNum = textMeasurer.measure(
            text = AnnotatedString("${rightPageIndex + 1}"),
            style = headerStyle,
            constraints = Constraints(maxWidth = colInnerW)
        )
        drawText(rightPageNum, topLeft = Offset(size.width - colPadHoriz - rightPageNum.size.width, size.height - footerH))

        // Left Page Content
        if (leftPdfBitmap != null) {
            val scale = minOf(halfW / leftPdfBitmap.width.toFloat(), size.height / leftPdfBitmap.height.toFloat())
            val dstW = leftPdfBitmap.width * scale
            val dstH = leftPdfBitmap.height * scale
            drawImage(
                image = leftPdfBitmap.asImageBitmap(),
                dstOffset = IntOffset(((halfW - dstW) / 2).roundToInt(), ((size.height - dstH) / 2).roundToInt()),
                dstSize = IntSize(dstW.roundToInt(), dstH.roundToInt())
            )
        } else if (leftText.isNotBlank()) {
            val cleanLeft = leftText.replace(Regex("\\[ILLUST:[^]]+]"), "").replace(Regex("\\[PDF_PAGE:[^]]+]"), "").trim()
            val leftAnnotated = buildAnnotatedString { append(cleanLeft) }
            val baseFontSize = pageTextStyle.fontSize.value
            val baseLineHeight = pageTextStyle.lineHeight.value
            var effectiveLeftStyle = pageTextStyle
            var leftLayout = textMeasurer.measure(
                text = leftAnnotated,
                style = effectiveLeftStyle,
                constraints = Constraints(maxWidth = colInnerW)
            )
            var fontScale = 1.0f
            while (leftLayout.size.height > colInnerH && fontScale > 0.48f) {
                fontScale *= 0.94f
                effectiveLeftStyle = pageTextStyle.copy(
                    fontSize = (baseFontSize * fontScale).sp,
                    lineHeight = (baseLineHeight * fontScale).sp
                )
                leftLayout = textMeasurer.measure(
                    text = leftAnnotated,
                    style = effectiveLeftStyle,
                    constraints = Constraints(maxWidth = colInnerW)
                )
            }
            clipRect(colPadHoriz, headerH + 6f, colPadHoriz + colInnerW, size.height - footerH) {
                drawText(leftLayout, topLeft = Offset(colPadHoriz, headerH + 6f))
            }
        }

        // Right Page Content
        if (rightPdfBitmap != null) {
            val scale = minOf(halfW / rightPdfBitmap.width.toFloat(), size.height / rightPdfBitmap.height.toFloat())
            val dstW = rightPdfBitmap.width * scale
            val dstH = rightPdfBitmap.height * scale
            drawImage(
                image = rightPdfBitmap.asImageBitmap(),
                dstOffset = IntOffset((halfW + (halfW - dstW) / 2).roundToInt(), ((size.height - dstH) / 2).roundToInt()),
                dstSize = IntSize(dstW.roundToInt(), dstH.roundToInt())
            )
        } else if (rightText.isNotBlank()) {
            val cleanRight = rightText.replace(Regex("\\[ILLUST:[^]]+]"), "").replace(Regex("\\[PDF_PAGE:[^]]+]"), "").trim()
            val rightAnnotated = buildAnnotatedString { append(cleanRight) }
            val baseFontSize = pageTextStyle.fontSize.value
            val baseLineHeight = pageTextStyle.lineHeight.value
            var effectiveRightStyle = pageTextStyle
            var rightLayout = textMeasurer.measure(
                text = rightAnnotated,
                style = effectiveRightStyle,
                constraints = Constraints(maxWidth = colInnerW)
            )
            var fontScale = 1.0f
            while (rightLayout.size.height > colInnerH && fontScale > 0.48f) {
                fontScale *= 0.94f
                effectiveRightStyle = pageTextStyle.copy(
                    fontSize = (baseFontSize * fontScale).sp,
                    lineHeight = (baseLineHeight * fontScale).sp
                )
                rightLayout = textMeasurer.measure(
                    text = rightAnnotated,
                    style = effectiveRightStyle,
                    constraints = Constraints(maxWidth = colInnerW)
                )
            }
            clipRect(halfW + colPadHoriz, headerH + 6f, halfW + colPadHoriz + colInnerW, size.height - footerH) {
                drawText(rightLayout, topLeft = Offset(halfW + colPadHoriz, headerH + 6f))
            }
        }
    }
    return bitmap
}
