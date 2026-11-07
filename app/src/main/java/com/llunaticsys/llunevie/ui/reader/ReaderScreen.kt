package com.llunaticsys.llunevie.ui.reader

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.llunaticsys.llunevie.GlobalPageMetrics
import com.llunaticsys.llunevie.LlunevieUiState
import com.llunaticsys.llunevie.LlunevieViewModel
import com.llunaticsys.llunevie.data.local.BookEntity
import com.llunaticsys.llunevie.data.local.HighlightNoteEntity
import com.llunaticsys.llunevie.data.local.PageBookmarkEntity
import com.llunaticsys.llunevie.data.local.UserPreferencesState
import com.llunaticsys.llunevie.data.parser.PdfNativeRenderer
import com.llunaticsys.llunevie.data.remote.TranslationRepository
import com.llunaticsys.llunevie.model.AppThemeMode
import com.llunaticsys.llunevie.model.HighlightColorKey
import com.llunaticsys.llunevie.model.PageAnimStyle
import com.llunaticsys.llunevie.model.ReadingFlowMode
import com.llunaticsys.llunevie.model.getActiveTranslationLanguages
import com.llunaticsys.llunevie.normalizeTranslationLangCode
import com.llunaticsys.llunevie.ui.components.PrototypeIconType
import com.llunaticsys.llunevie.ui.components.PrototypeSvgIcon
import com.llunaticsys.llunevie.ui.components.targetLanguageDisplayName
import com.llunaticsys.llunevie.ui.theme.LlunevieStrings
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

private data class ScrollLazyItemMeta(
    val globalPageZero: Int,
    val chapterIndex: Int,
    val pageInChapter: Int,
    val isPageItem: Boolean
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReaderScreen(
    book: BookEntity,
    uiState: LlunevieUiState,
    prefs: UserPreferencesState,
    bookHighlights: List<HighlightNoteEntity>,
    bookBookmarks: List<PageBookmarkEntity> = emptyList(),
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
        paraIndent: Boolean?,
        commitToDisk: Boolean
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
    var showInlinePageSlider by remember { mutableStateOf(false) }

    var scrubberPageValue by remember { mutableFloatStateOf(metrics.currentGlobalPage.toFloat()) }
    var inBookSearchQuery by remember { mutableStateOf<String?>(null) }
    var lastTurnDirection by remember { mutableIntStateOf(1) }
    var slideDragOffsetPx by remember { mutableFloatStateOf(0f) }
    var hasSelectionActive by remember { mutableStateOf(false) }
    var clearSelectionTrigger by remember { mutableIntStateOf(0) }
    var selStartHandleAnchor by remember { mutableStateOf(Offset.Zero) }
    var selEndHandleAnchor by remember { mutableStateOf(Offset.Zero) }
    var activeDraggingSliderKey by remember { mutableStateOf<String?>(null) }

    val curZeroFromMetrics = (metrics.currentGlobalPage - 1).coerceAtLeast(0)
    var displayPageIndex by remember(book.id) { mutableIntStateOf(curZeroFromMetrics) }
    var readerZoom by remember(book.id, displayPageIndex, prefs.readingFlowMode) { mutableFloatStateOf(1f) }
    var readerPan by remember(book.id, displayPageIndex, prefs.readingFlowMode) { mutableStateOf(Offset.Zero) }
    var nonFlipZoomScale by remember(book.id, prefs.readingFlowMode) { mutableFloatStateOf(1f) }
    var nonFlipZoomOffset by remember(book.id, prefs.readingFlowMode) { mutableStateOf(Offset.Zero) }
    var isFlippingInProgress by remember { mutableStateOf(false) }

    LaunchedEffect(curZeroFromMetrics) {
        if (!isFlippingInProgress) {
            displayPageIndex = curZeroFromMetrics
        }
    }

    LaunchedEffect(metrics.currentGlobalPage) {
        scrubberPageValue = metrics.currentGlobalPage.toFloat()
    }

    val isSpread = prefs.readingFlowMode == ReadingFlowMode.SPREAD
    val isPdfBook = (book.format.equals("PDF", ignoreCase = true) || currentPageRaw.startsWith("[PDF_PAGE:")) &&
        !book.localFilePath.isNullOrBlank()

    // Compute PDF theme filter
    val isSystemDark = isSystemInDarkTheme()
    val effectiveTheme = prefs.effectiveTheme(isSystemDark)
    val pdfThemeColorFilter = if (isPdfBook && prefs.pdfThemeTintEnabled) {
        PdfNativeRenderer.buildPdfThemeColorFilter(effectiveTheme, prefs.amoledBlack)
    } else null
    val androidPdfThemeColorFilter = if (isPdfBook && prefs.pdfThemeTintEnabled) {
        PdfNativeRenderer.buildAndroidPdfThemeColorFilter(effectiveTheme, prefs.amoledBlack)
    } else null

    val indexedIllustrationPages = remember(uiState.activeBookIllustrations) {
        buildSet {
            add(0) // Page 1 (0-based index 0) front cover is always protected
            for (illust in uiState.activeBookIllustrations) {
                if (illust.pageIndexInBook >= 0) {
                    add(illust.pageIndexInBook)
                }
            }
        }
    }

    fun pdfFilterForPage(pageZeroIdx: Int): ColorFilter? {
        if (pageZeroIdx < 0 || pageZeroIdx >= metrics.totalBookPages) return null
        if (!isPdfBook || !prefs.pdfThemeTintEnabled || effectiveTheme == AppThemeMode.WHITE) return null
        if (prefs.pdfProtectIllustrations && PdfNativeRenderer.isPdfPageProtectedIllustration(pageZeroIdx, indexedIllustrationPages)) {
            return null
        }
        return pdfThemeColorFilter
    }

    val context = LocalContext.current
    val view = LocalView.current
    var showReaderChrome by remember { mutableStateOf(true) }
    var showLangDropdown by remember { mutableStateOf(false) }
    val isTwoPageSpread = isSpread

    val translationRepo = remember { TranslationRepository.getInstance() }
    var isPageTranslationActive by remember { mutableStateOf(false) }
    var peekOriginalPdfPage by remember { mutableStateOf(false) }
    var pageTranslationMap by remember { mutableStateOf<Map<String, List<Pair<String, String?>>>>(emptyMap()) }
    var isTranslatingPage by remember { mutableStateOf(false) }

    fun pageTransMapKey(pageZero: Int, targetLang: String = uiState.translationTargetLang): String =
        "${normalizeTranslationLangCode(targetLang)}:$pageZero"

    fun pdfTransMapKey(pageZero: Int, targetLang: String = uiState.translationTargetLang, mode: String = uiState.translationMode): String =
        "pdf:${normalizeTranslationLangCode(targetLang)}:${mode.lowercase()}:$pageZero"

    val getEffectivePdfPageBitmap: (Int, Bitmap?) -> Bitmap? = { pageZero, origBmp ->
        val isTrans = (isPageTranslationActive || uiState.isTranslationActive) && !uiState.translationMode.equals("original", ignoreCase = true)
        if (!isPdfBook || !isTrans || peekOriginalPdfPage) {
            origBmp
        } else {
            val cachedTrans = viewModel?.getCachedTranslatedPdfBitmap(book.id, pageZero, uiState.translationTargetLang, uiState.translationMode)
            if (cachedTrans != null && !cachedTrans.isRecycled) {
                cachedTrans
            } else {
                origBmp
            }
        }
    }

    var showInlineTranslation by remember { mutableStateOf(false) }
    var inlineTranslationText by remember { mutableStateOf<String?>(null) }
    var isTranslatingInline by remember { mutableStateOf(false) }
    var inlineTranslationError by remember { mutableStateOf<String?>(null) }

    // Intercept system back press to dismiss active in-reader overlays before leaving the reader
    val hasActiveInReaderOverlay = inBookSearchQuery != null ||
        showLangDropdown ||
        showPageScrubber ||
        showInlinePageSlider ||
        selectedQuoteForPopover != null ||
        hasSelectionActive

    BackHandler(
        enabled = hasActiveInReaderOverlay &&
            !uiState.showIllustViewer &&
            !uiState.showReaderNotesDrawer &&
            !uiState.showReaderTocDrawer &&
            !uiState.showReaderTypeSheet
    ) {
        when {
            inBookSearchQuery != null -> inBookSearchQuery = null
            showLangDropdown -> showLangDropdown = false
            showPageScrubber -> showPageScrubber = false
            showInlinePageSlider -> showInlinePageSlider = false
            selectedQuoteForPopover != null -> {
                selectedQuoteForPopover = null
                showInlineNoteInput = false
                showInlineTranslation = false
                inlineTranslationText = null
                inlineTranslationError = null
            }
            hasSelectionActive -> {
                hasSelectionActive = false
                clearSelectionTrigger++
            }
        }
    }

    LaunchedEffect(displayPageIndex, isPageTranslationActive) {
        peekOriginalPdfPage = false
    }

    LaunchedEffect(uiState.isTranslationActive) {
        if (isPageTranslationActive != uiState.isTranslationActive) {
            isPageTranslationActive = uiState.isTranslationActive
        }
    }

    LaunchedEffect(
        uiState.translationTargetLang,
        uiState.translationMode,
        isSpread,
        prefs.translatorEngine,
        prefs.aiModelName,
        prefs.aiSystemPrompt
    ) {
        clearEpubSnapshotCache(evictAll = true)
    }

    LaunchedEffect(
        isPageTranslationActive,
        uiState.isTranslationActive,
        uiState.translationTargetLang,
        uiState.translationMode,
        isSpread,
        displayPageIndex,
        prefs.translatorEngine,
        prefs.aiModelName,
        prefs.aiSystemPrompt
    ) {
        val isTransActive = isPageTranslationActive || uiState.isTranslationActive
        if (!isTransActive || uiState.translationMode.equals("original", ignoreCase = true)) return@LaunchedEffect

        val curTargetLang = uiState.translationTargetLang
        val curMode = uiState.translationMode
        val isBilingualSpreadNow = isSpread && curMode.equals("bilingual", ignoreCase = true)
        val effPdfMode = if (isBilingualSpreadNow) "replace" else curMode

        val pagesToTranslate = if (isSpread) {
            val sStart = (displayPageIndex / 2) * 2
            listOf(displayPageIndex, sStart, sStart + 1).distinct()
        } else {
            listOf(displayPageIndex)
        }

        if (isPdfBook) {
            val path = book.localFilePath
            if (path.isNotBlank()) {
                for (pZero in pagesToTranslate) {
                    val pdfKey = pdfTransMapKey(pZero, curTargetLang, effPdfMode)
                    if (pZero in 0 until metrics.totalBookPages && !pageTranslationMap.containsKey(pdfKey)) {
                        isTranslatingPage = true
                        val targetW = if (isSpread) 540 else 1080
                        viewModel?.loadOrTranslatePdfPageBitmap(
                            bookId = book.id,
                            filePath = path,
                            pageIndex = pZero,
                            targetLang = curTargetLang,
                            mode = effPdfMode,
                            targetWidthPx = targetW
                        )
                        pageTranslationMap = pageTranslationMap + (pdfKey to emptyList())
                        isTranslatingPage = false
                    }
                }

                // Pre-extract & pre-translate upcoming pages in the background
                val nextPages = if (isBilingualSpreadNow) {
                    listOf(displayPageIndex + 1, displayPageIndex + 2)
                } else if (isSpread) {
                    val sStart = (displayPageIndex / 2) * 2
                    listOf(sStart + 2, sStart + 3, sStart + 4, sStart + 5)
                } else {
                    listOf(displayPageIndex + 1, displayPageIndex + 2)
                }
                for (nextP in nextPages) {
                    val pdfKey = pdfTransMapKey(nextP, curTargetLang, effPdfMode)
                    if (nextP in 0 until metrics.totalBookPages && !pageTranslationMap.containsKey(pdfKey)) {
                        val targetW = if (isSpread) 540 else 1080
                        viewModel?.loadOrTranslatePdfPageBitmap(
                            bookId = book.id,
                            filePath = path,
                            pageIndex = nextP,
                            targetLang = curTargetLang,
                            mode = effPdfMode,
                            targetWidthPx = targetW
                        )
                        pageTranslationMap = pageTranslationMap + (pdfKey to emptyList())
                    }
                }
            }
        } else {
            for (pZero in pagesToTranslate) {
                val mapKey = pageTransMapKey(pZero, curTargetLang)
                if (pZero in 0 until metrics.totalBookPages) {
                    val pTriple = getPageAtGlobalZero(pZero)
                    val pRaw = pTriple?.third.orEmpty().ifBlank { if (pZero == displayPageIndex) currentPageRaw else "" }
                    if (pRaw.isNotBlank()) {
                        val cached = viewModel?.getCachedTranslatedParagraphs(book.id, pZero, curTargetLang, pRaw.hashCode())
                            ?: pageTranslationMap[mapKey]
                        val hasValidCached = cached != null && cached.any { (orig, trans) ->
                            !orig.startsWith("[ILLUST:") && !orig.startsWith("[PDF_PAGE:") &&
                                !trans.isNullOrBlank() && !trans.trim().equals(orig.trim(), ignoreCase = true)
                        }
                        if (hasValidCached && cached != null) {
                            if (!pageTranslationMap.containsKey(mapKey)) {
                                pageTranslationMap = pageTranslationMap + (mapKey to cached)
                            }
                        } else {
                            isTranslatingPage = true
                            val res = if (viewModel != null) {
                                viewModel.loadOrTranslatePageParagraphs(
                                    bookId = book.id,
                                    pageIndex = pZero,
                                    pageRaw = pRaw,
                                    targetLang = curTargetLang
                                )
                            } else {
                                translationRepo.translatePageParagraphs(pRaw, prefs, context, curTargetLang).getOrNull()
                            }
                            if (res != null) {
                                pageTranslationMap = pageTranslationMap + (mapKey to res)
                            }
                            isTranslatingPage = false
                        }
                    }
                }
            }

            // Pre-fetch next pages in background
            val nextPages = if (isBilingualSpreadNow) {
                listOf(displayPageIndex + 1, displayPageIndex + 2)
            } else if (isSpread) {
                val sStart = (displayPageIndex / 2) * 2
                listOf(sStart + 2, sStart + 3)
            } else {
                listOf(displayPageIndex + 1, displayPageIndex + 2)
            }
            for (nextP in nextPages) {
                val mapKey = pageTransMapKey(nextP, curTargetLang)
                if (nextP in 0 until metrics.totalBookPages && !pageTranslationMap.containsKey(mapKey)) {
                    val nextTriple = getPageAtGlobalZero(nextP)
                    val nextRaw = nextTriple?.third.orEmpty()
                    if (nextRaw.isNotBlank()) {
                        val cached = viewModel?.getCachedTranslatedParagraphs(book.id, nextP, curTargetLang, nextRaw.hashCode())
                        val res = cached ?: if (viewModel != null) {
                            viewModel.loadOrTranslatePageParagraphs(
                                bookId = book.id,
                                pageIndex = nextP,
                                pageRaw = nextRaw,
                                targetLang = curTargetLang
                            )
                        } else {
                            translationRepo.translatePageParagraphs(nextRaw, prefs, context, curTargetLang).getOrNull()
                        }
                        if (res != null) {
                            pageTranslationMap = pageTranslationMap + (mapKey to res)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(prefs.flowMode, isTwoPageSpread) {
        showReaderChrome = true
    }

    LaunchedEffect(uiState.isTranslationActive, showReaderChrome) {
        if (!uiState.isTranslationActive || !showReaderChrome) {
            showLangDropdown = false
        }
    }

    DisposableEffect(isTwoPageSpread, showReaderChrome) {
        val window = (context as? android.app.Activity)?.window
        val insetsController = if (window != null) WindowCompat.getInsetsController(window, view) else null
        insetsController?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (isTwoPageSpread) {
            insetsController?.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        } else {
            insetsController?.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        }
        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
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

            val currentStatusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val currentCutoutTop = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
            val effectiveTopSystemInset = maxOf(currentStatusBarTop, currentCutoutTop)
            val currentNavBarBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            var stableStatusBarTopDp by remember { mutableStateOf(24.dp) }
            var stableNavBarBottomDp by remember { mutableStateOf(16.dp) }
            if (effectiveTopSystemInset > 0.dp && effectiveTopSystemInset > stableStatusBarTopDp) {
                stableStatusBarTopDp = effectiveTopSystemInset
            }
            if (currentNavBarBottom > 0.dp && currentNavBarBottom > stableNavBarBottomDp) {
                stableNavBarBottomDp = currentNavBarBottom
            }
            val isTranslationQuickBarActive = isPageTranslationActive || uiState.isTranslationActive
            val topClearanceDp = stableStatusBarTopDp + if (isTranslationQuickBarActive) 68.dp else 64.dp
            val bottomClearanceDp = stableNavBarBottomDp + 76.dp

            val spreadTopPadDp = if (isSpread) {
                effectiveTopSystemInset + if (showReaderChrome || isTranslationQuickBarActive) 52.dp else 8.dp
            } else 0.dp
            val spreadBottomPadDp = if (isSpread) 8.dp else 0.dp
            val spreadStageH = if (isSpread) {
                (stageH - with(density) { (spreadTopPadDp + spreadBottomPadDp).roundToPx() }).coerceAtLeast(240)
            } else stageH
            val spreadW = (spreadStageH * 1.414f).toInt().coerceAtMost(
                (stageW - with(density) { (if (isSpread) 32.dp else 0.dp).roundToPx() }).coerceAtLeast(240)
            )

            val horizontalMarginDp = when (prefs.pageMargin) {
                0 -> 12.dp
                2 -> 36.dp
                else -> 22.dp
            }

            val padX = with(density) { horizontalMarginDp.toPx() }
            val padTop = with(density) { topClearanceDp.toPx() }
            val padBottom = with(density) { bottomClearanceDp.toPx() }

            when (prefs.readingFlowMode) {
                ReadingFlowMode.PAGINATED, ReadingFlowMode.SPREAD -> {
                    val curZero = displayPageIndex

                    LaunchedEffect(book.id, curZero, prefs.pageAnimStyle, prefs.readingFlowMode) {
                        nonFlipZoomScale = 1f
                        nonFlipZoomOffset = Offset.Zero
                    }
                    val isTrans = (isPageTranslationActive || uiState.isTranslationActive) && !uiState.translationMode.equals("original", ignoreCase = true)
                    val transTargetLang = uiState.translationTargetLang
                    val transMode = uiState.translationMode
                    val transRev = uiState.translationRevision
                    val isBilingualSpread = isSpread && isTrans && transMode.equals("bilingual", ignoreCase = true)
                    val spreadStep = if (isBilingualSpread) 1 else if (isSpread) 2 else 1

                    val spreadStart = if (isBilingualSpread) curZero else if (isSpread) (curZero / 2) * 2 else curZero
                    val curLeftIdx = spreadStart
                    val curRightIdx = if (isBilingualSpread) spreadStart else spreadStart + 1
                    val nextLeftIdx = if (isSpread) spreadStart + spreadStep else curZero + 1
                    val nextRightIdx = if (isBilingualSpread) nextLeftIdx else if (isSpread) spreadStart + spreadStep + 1 else curZero + 2
                    val prevLeftIdx = if (isSpread) spreadStart - spreadStep else curZero - 1
                    val prevRightIdx = if (isBilingualSpread) prevLeftIdx else if (isSpread) spreadStart - spreadStep + 1 else curZero

                    val needStagePdfBitmaps = isPdfBook && (
                        (prefs.animatePageTurn && prefs.pageAnimStyle == PageAnimStyle.FLIPBOOK) ||
                            isSpread
                        )
                    val leftPdfShouldTranslate = isTrans && !isBilingualSpread
                    val rightPdfTransMode = if (isBilingualSpread) "replace" else transMode

                    val curLeftPdfBmp by produceState<Bitmap?>(
                        initialValue = if (needStagePdfBitmaps) {
                            if (leftPdfShouldTranslate) {
                                viewModel?.getCachedTranslatedPdfBitmap(book.id, curLeftIdx, transTargetLang, transMode)
                                    ?: viewModel?.getCachedPdfBitmap(book.id, curLeftIdx, book.localFilePath)
                                    ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, curLeftIdx, ((stageW / 120) * 120).coerceAtLeast(360))
                            } else {
                                viewModel?.getCachedPdfBitmap(book.id, curLeftIdx, book.localFilePath)
                                    ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, curLeftIdx, ((stageW / 120) * 120).coerceAtLeast(360))
                            }
                        } else null,
                        needStagePdfBitmaps, curLeftIdx, book.localFilePath, stageW,
                        uiState.isTranslationActive, transMode, transTargetLang, transRev, isBilingualSpread
                    ) {
                        value = if (needStagePdfBitmaps) {
                            if (leftPdfShouldTranslate && viewModel != null) {
                                viewModel.loadOrTranslatePdfPageBitmap(
                                    bookId = book.id,
                                    filePath = book.localFilePath!!,
                                    pageIndex = curLeftIdx,
                                    targetLang = transTargetLang,
                                    mode = transMode,
                                    targetWidthPx = if (isSpread) stageW / 2 else stageW
                                )
                            } else {
                                PdfNativeRenderer.renderPageBitmap(book.localFilePath!!, curLeftIdx, if (isSpread) stageW / 2 else stageW)
                            }
                        } else null
                    }
                    val curRightPdfBmp by produceState<Bitmap?>(
                        initialValue = if (needStagePdfBitmaps && isSpread && curRightIdx < metrics.totalBookPages) {
                            if (isTrans) {
                                viewModel?.getCachedTranslatedPdfBitmap(book.id, curRightIdx, transTargetLang, rightPdfTransMode)
                                    ?: viewModel?.getCachedPdfBitmap(book.id, curRightIdx, book.localFilePath)
                                    ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, curRightIdx, ((stageW / 240) * 120).coerceAtLeast(360))
                            } else {
                                viewModel?.getCachedPdfBitmap(book.id, curRightIdx, book.localFilePath)
                                    ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, curRightIdx, ((stageW / 240) * 120).coerceAtLeast(360))
                            }
                        } else null,
                        needStagePdfBitmaps, isSpread, curRightIdx, book.localFilePath, stageW,
                        uiState.isTranslationActive, transMode, transTargetLang, transRev, isBilingualSpread
                    ) {
                        value = if (needStagePdfBitmaps && isSpread && curRightIdx < metrics.totalBookPages) {
                            if (isTrans && viewModel != null) {
                                viewModel.loadOrTranslatePdfPageBitmap(
                                    bookId = book.id,
                                    filePath = book.localFilePath!!,
                                    pageIndex = curRightIdx,
                                    targetLang = transTargetLang,
                                    mode = rightPdfTransMode,
                                    targetWidthPx = stageW / 2
                                )
                            } else {
                                PdfNativeRenderer.renderPageBitmap(book.localFilePath!!, curRightIdx, stageW / 2)
                            }
                        } else null
                    }
                    val nextLeftPdfBmp by produceState<Bitmap?>(
                        initialValue = if (needStagePdfBitmaps && nextLeftIdx < metrics.totalBookPages) {
                            if (leftPdfShouldTranslate) {
                                viewModel?.getCachedTranslatedPdfBitmap(book.id, nextLeftIdx, transTargetLang, transMode)
                                    ?: viewModel?.getCachedPdfBitmap(book.id, nextLeftIdx, book.localFilePath)
                                    ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, nextLeftIdx, ((stageW / 120) * 120).coerceAtLeast(360))
                            } else {
                                viewModel?.getCachedPdfBitmap(book.id, nextLeftIdx, book.localFilePath)
                                    ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, nextLeftIdx, ((stageW / 120) * 120).coerceAtLeast(360))
                            }
                        } else null,
                        needStagePdfBitmaps, nextLeftIdx, book.localFilePath, stageW,
                        uiState.isTranslationActive, transMode, transTargetLang, transRev, isBilingualSpread
                    ) {
                        value = if (needStagePdfBitmaps && nextLeftIdx < metrics.totalBookPages) {
                            if (leftPdfShouldTranslate && viewModel != null) {
                                viewModel.loadOrTranslatePdfPageBitmap(
                                    bookId = book.id,
                                    filePath = book.localFilePath!!,
                                    pageIndex = nextLeftIdx,
                                    targetLang = transTargetLang,
                                    mode = transMode,
                                    targetWidthPx = if (isSpread) stageW / 2 else stageW
                                )
                            } else {
                                PdfNativeRenderer.renderPageBitmap(book.localFilePath!!, nextLeftIdx, if (isSpread) stageW / 2 else stageW)
                            }
                        } else null
                    }
                    val nextRightPdfBmp by produceState<Bitmap?>(
                        initialValue = if (needStagePdfBitmaps && isSpread && nextRightIdx < metrics.totalBookPages) {
                            if (isTrans) {
                                viewModel?.getCachedTranslatedPdfBitmap(book.id, nextRightIdx, transTargetLang, rightPdfTransMode)
                                    ?: viewModel?.getCachedPdfBitmap(book.id, nextRightIdx, book.localFilePath)
                                    ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, nextRightIdx, ((stageW / 240) * 120).coerceAtLeast(360))
                            } else {
                                viewModel?.getCachedPdfBitmap(book.id, nextRightIdx, book.localFilePath)
                                    ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, nextRightIdx, ((stageW / 240) * 120).coerceAtLeast(360))
                            }
                        } else null,
                        needStagePdfBitmaps, isSpread, nextRightIdx, book.localFilePath, stageW,
                        uiState.isTranslationActive, transMode, transTargetLang, transRev, isBilingualSpread
                    ) {
                        value = if (needStagePdfBitmaps && isSpread && nextRightIdx < metrics.totalBookPages) {
                            if (isTrans && viewModel != null) {
                                viewModel.loadOrTranslatePdfPageBitmap(
                                    bookId = book.id,
                                    filePath = book.localFilePath!!,
                                    pageIndex = nextRightIdx,
                                    targetLang = transTargetLang,
                                    mode = rightPdfTransMode,
                                    targetWidthPx = stageW / 2
                                )
                            } else {
                                PdfNativeRenderer.renderPageBitmap(book.localFilePath!!, nextRightIdx, stageW / 2)
                            }
                        } else null
                    }
                    val prevLeftPdfBmp by produceState<Bitmap?>(
                        initialValue = if (needStagePdfBitmaps && prevLeftIdx >= 0) {
                            if (leftPdfShouldTranslate) {
                                viewModel?.getCachedTranslatedPdfBitmap(book.id, prevLeftIdx, transTargetLang, transMode)
                                    ?: viewModel?.getCachedPdfBitmap(book.id, prevLeftIdx, book.localFilePath)
                                    ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, prevLeftIdx, ((stageW / 120) * 120).coerceAtLeast(360))
                            } else {
                                viewModel?.getCachedPdfBitmap(book.id, prevLeftIdx, book.localFilePath)
                                    ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, prevLeftIdx, ((stageW / 120) * 120).coerceAtLeast(360))
                            }
                        } else null,
                        needStagePdfBitmaps, prevLeftIdx, book.localFilePath, stageW,
                        uiState.isTranslationActive, transMode, transTargetLang, transRev, isBilingualSpread
                    ) {
                        value = if (needStagePdfBitmaps && prevLeftIdx >= 0) {
                            if (leftPdfShouldTranslate && viewModel != null) {
                                viewModel.loadOrTranslatePdfPageBitmap(
                                    bookId = book.id,
                                    filePath = book.localFilePath!!,
                                    pageIndex = prevLeftIdx,
                                    targetLang = transTargetLang,
                                    mode = transMode,
                                    targetWidthPx = if (isSpread) stageW / 2 else stageW
                                )
                            } else {
                                PdfNativeRenderer.renderPageBitmap(book.localFilePath!!, prevLeftIdx, if (isSpread) stageW / 2 else stageW)
                            }
                        } else null
                    }
                    val prevRightPdfBmp by produceState<Bitmap?>(
                        initialValue = if (needStagePdfBitmaps && isSpread && prevRightIdx >= 0) {
                            if (isTrans) {
                                viewModel?.getCachedTranslatedPdfBitmap(book.id, prevRightIdx, transTargetLang, rightPdfTransMode)
                                    ?: viewModel?.getCachedPdfBitmap(book.id, prevRightIdx, book.localFilePath)
                                    ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, prevRightIdx, ((stageW / 240) * 120).coerceAtLeast(360))
                            } else {
                                viewModel?.getCachedPdfBitmap(book.id, prevRightIdx, book.localFilePath)
                                    ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, prevRightIdx, ((stageW / 240) * 120).coerceAtLeast(360))
                            }
                        } else null,
                        needStagePdfBitmaps, isSpread, prevRightIdx, book.localFilePath, stageW,
                        uiState.isTranslationActive, transMode, transTargetLang, transRev, isBilingualSpread
                    ) {
                        value = if (needStagePdfBitmaps && isSpread && prevRightIdx >= 0) {
                            if (isTrans && viewModel != null) {
                                viewModel.loadOrTranslatePdfPageBitmap(
                                    bookId = book.id,
                                    filePath = book.localFilePath!!,
                                    pageIndex = prevRightIdx,
                                    targetLang = transTargetLang,
                                    mode = rightPdfTransMode,
                                    targetWidthPx = stageW / 2
                                )
                            } else {
                                PdfNativeRenderer.renderPageBitmap(book.localFilePath!!, prevRightIdx, stageW / 2)
                            }
                        } else null
                    }

                    val curLeftEffectivePdfBmp = if (isBilingualSpread) curLeftPdfBmp else getEffectivePdfPageBitmap(curLeftIdx, curLeftPdfBmp)
                    val curRightEffectivePdfBmp = curRightPdfBmp ?: getEffectivePdfPageBitmap(curRightIdx, curRightPdfBmp)
                    val nextLeftEffectivePdfBmp = if (isBilingualSpread) nextLeftPdfBmp else getEffectivePdfPageBitmap(nextLeftIdx, nextLeftPdfBmp)
                    val nextRightEffectivePdfBmp = nextRightPdfBmp ?: getEffectivePdfPageBitmap(nextRightIdx, nextRightPdfBmp)
                    val prevLeftEffectivePdfBmp = if (isBilingualSpread) prevLeftPdfBmp else getEffectivePdfPageBitmap(prevLeftIdx, prevLeftPdfBmp)
                    val prevRightEffectivePdfBmp = prevRightPdfBmp ?: getEffectivePdfPageBitmap(prevRightIdx, prevRightPdfBmp)

                    val textMeasurer = rememberTextMeasurer()
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

                    val isReplaceMode = (isPageTranslationActive || uiState.isTranslationActive) &&
                        uiState.translationMode.equals("replace", ignoreCase = true)
                    val isBilingualMode = (isPageTranslationActive || uiState.isTranslationActive) &&
                        uiState.translationMode.equals("bilingual", ignoreCase = true)

                    fun getEffectivePageTranslationPairs(pageZero: Int, rawText: String = ""): List<Pair<String, String?>>? {
                        val raw = rawText.ifBlank { getPageAtGlobalZero(pageZero)?.third.orEmpty() }
                        if (raw.isBlank()) return null
                        return viewModel?.getCachedTranslatedParagraphs(
                            book.id,
                            pageZero,
                            uiState.translationTargetLang,
                            raw.hashCode()
                        ) ?: pageTranslationMap[pageTransMapKey(pageZero)]
                    }

                    fun isEpubPageTranslationReady(pageZero: Int, fallbackRaw: String = ""): Boolean {
                        val transActive = (isPageTranslationActive || uiState.isTranslationActive) &&
                            !uiState.translationMode.equals("original", ignoreCase = true)
                        if (!transActive || isPdfBook) return true
                        val raw = getPageAtGlobalZero(pageZero)?.third.orEmpty().ifBlank { fallbackRaw }
                        val hasTranslatable = raw.split(Regex("\n\\s*\n")).any {
                            val t = it.trim()
                            t.isNotBlank() && !t.startsWith("[ILLUST:") && !t.startsWith("[PDF_PAGE:")
                        }
                        if (!hasTranslatable) return true
                        val pairs = getEffectivePageTranslationPairs(pageZero, raw) ?: return false
                        return pairs.any { (orig, trans) ->
                            !orig.startsWith("[ILLUST:") && !orig.startsWith("[PDF_PAGE:") &&
                                !trans.isNullOrBlank() && !trans.trim().equals(orig.trim(), ignoreCase = true)
                        }
                    }

                    fun isSpreadTranslationReady(leftZero: Int, rightZero: Int, fallbackLeftRaw: String = ""): Boolean {
                        val transActive = (isPageTranslationActive || uiState.isTranslationActive) &&
                            !uiState.translationMode.equals("original", ignoreCase = true)
                        if (!transActive || isPdfBook) return true
                        return when {
                            isBilingualSpread -> isEpubPageTranslationReady(leftZero, fallbackLeftRaw)
                            isReplaceMode -> isEpubPageTranslationReady(leftZero, fallbackLeftRaw) &&
                                (rightZero >= metrics.totalBookPages || isEpubPageTranslationReady(rightZero))
                            else -> true
                        }
                    }

                    fun resolveEffectiveEpubText(pageZero: Int, fallbackRaw: String = ""): String {
                        val raw = getPageAtGlobalZero(pageZero)?.third.orEmpty().ifBlank { fallbackRaw }
                        if (isPdfBook || raw.isBlank()) return raw
                        if (!isReplaceMode && (!isBilingualMode || isSpread)) return raw
                        val cachedTrans = getEffectivePageTranslationPairs(pageZero, raw)
                        val effMode = if (isReplaceMode) "replace" else "bilingual"
                        return if (!cachedTrans.isNullOrEmpty()) {
                            buildTranslatedPageRaw(raw, cachedTrans, effMode)
                        } else {
                            raw
                        }
                    }

                    fun resolveTranslatedOnlyEpubText(pageZero: Int, fallbackRaw: String = ""): String {
                        val raw = getPageAtGlobalZero(pageZero)?.third.orEmpty().ifBlank { fallbackRaw }
                        if (isPdfBook || raw.isBlank()) return ""
                        val cachedTrans = getEffectivePageTranslationPairs(pageZero, raw)
                        return if (!cachedTrans.isNullOrEmpty()) {
                            buildTranslatedPageRaw(raw, cachedTrans, "replace")
                        } else {
                            ""
                        }
                    }

                    fun resolveSpreadPairTexts(leftZero: Int, rightZero: Int, fallbackLeftRaw: String = ""): Pair<String, String> {
                        val leftOrig = getPageAtGlobalZero(leftZero)?.third.orEmpty().ifBlank { fallbackLeftRaw }
                        if (isPdfBook) {
                            val rightOrig = if (rightZero < metrics.totalBookPages) getPageAtGlobalZero(rightZero)?.third.orEmpty() else ""
                            return leftOrig to rightOrig
                        }
                        return when {
                            isBilingualSpread -> {
                                // Bilingual Spread: Left = Original of leftZero, Right = Translation of the EXACT SAME leftZero
                                leftOrig to resolveTranslatedOnlyEpubText(leftZero, leftOrig)
                            }
                            isReplaceMode -> {
                                val leftRep = resolveEffectiveEpubText(leftZero, leftOrig)
                                val rightRep = if (rightZero < metrics.totalBookPages) resolveEffectiveEpubText(rightZero) else ""
                                leftRep to rightRep
                            }
                            else -> {
                                val rightOrig = if (rightZero < metrics.totalBookPages) getPageAtGlobalZero(rightZero)?.third.orEmpty() else ""
                                leftOrig to rightOrig
                            }
                        }
                    }

                    fun buildEpubSnapshotKey(pageZero: Int, textHash: Int, spread: Boolean, rightZero: Int = pageZero): String {
                        val transActive = (isPageTranslationActive || uiState.isTranslationActive) &&
                            !uiState.translationMode.equals("original", ignoreCase = true)
                        val normLang = normalizeTranslationLangCode(uiState.translationTargetLang)
                        val normMode = if (transActive) uiState.translationMode.lowercase() else "original"
                        val leftReady = isEpubPageTranslationReady(pageZero)
                        val rightReady = if (spread) (rightZero >= metrics.totalBookPages || isEpubPageTranslationReady(rightZero)) else true
                        val versionTag = if (!transActive) "orig" else if (leftReady && rightReady) "trans" else "partial"
                        val modeTag = "$versionTag:$normMode:$normLang:$leftReady:$rightReady"
                        val combinedHash = (((bookHighlights.hashCode() * 31 + textHash) * 31 +
                            (if (transActive) normLang.hashCode() else 0)) * 31 +
                            normMode.hashCode()) * 31 + rightZero
                        return buildSnapshotCacheKey(
                            bookId = book.id,
                            pageZero = pageZero,
                            widthPx = if (spread) spreadW else stageW,
                            heightPx = if (spread) spreadStageH else stageH,
                            prefs = prefs,
                            bgArgb = (if (spread) palette.bgCard else palette.bgApp).toArgb(),
                            textArgb = palette.textMain.toArgb(),
                            isSpread = spread,
                            highlightsHash = combinedHash,
                            translationSuffix = ":$modeTag:$rightZero"
                        )
                    }

                    if (prefs.animatePageTurn && prefs.pageAnimStyle == PageAnimStyle.FLIPBOOK) {
                        var lockedCurrBmp by remember { mutableStateOf<ImageBitmap?>(null) }
                        var lockedNextBmp by remember { mutableStateOf<ImageBitmap?>(null) }
                        var lockedPrevBmp by remember { mutableStateOf<ImageBitmap?>(null) }
                        var committedCurrentBmp by remember { mutableStateOf<ImageBitmap?>(null) }
                        var isTurnCommitting by remember { mutableStateOf(false) }

                        var lockedCurrFilter by remember { mutableStateOf<ColorFilter?>(null) }
                        var lockedNextFilter by remember { mutableStateOf<ColorFilter?>(null) }
                        var lockedPrevFilter by remember { mutableStateOf<ColorFilter?>(null) }

                        LaunchedEffect(uiState.isTranslationActive, uiState.translationMode, uiState.translationTargetLang, uiState.translationRevision) {
                            committedCurrentBmp = null
                            lockedCurrBmp = null
                            lockedNextBmp = null
                            lockedPrevBmp = null
                        }

                        val curBitmapInitial = if (isPdfBook && !isSpread) {
                            (curLeftEffectivePdfBmp
                                ?: (if (isTrans) viewModel?.getCachedTranslatedPdfBitmap(book.id, curZero, transTargetLang, transMode) else null)
                                ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath!!, curZero, ((stageW / 120) * 120).coerceAtLeast(360))
                                ?: viewModel?.getCachedPdfBitmap(book.id, curZero, book.localFilePath))?.asImageBitmap()
                        } else if (!isPdfBook) {
                            if (isSpread) {
                                val (leftText, rightText) = resolveSpreadPairTexts(curLeftIdx, curRightIdx, if (curLeftIdx == curZero) currentPageRaw else "")
                                epubSnapshotCache.get(buildEpubSnapshotKey(curLeftIdx, leftText.hashCode() * 31 + rightText.hashCode(), true, curRightIdx))
                            } else {
                                val curText = resolveEffectiveEpubText(curZero, currentPageRaw)
                                epubSnapshotCache.get(buildEpubSnapshotKey(curZero, curText.hashCode(), false))
                            }
                        } else null

                        val curBitmap by produceState<ImageBitmap?>(
                            initialValue = curBitmapInitial,
                            curZero, curLeftIdx, curRightIdx, isSpread, isBilingualSpread, isPdfBook, currentPageRaw,
                            curLeftEffectivePdfBmp, curRightEffectivePdfBmp,
                            palette.bgApp, palette.textMain, baseTextStyle, spreadTextStyle, stageW, stageH, spreadW, spreadStageH,
                            prefs.paraSpacingEm, prefs.wordSpacingPx, prefs.pageMargin, prefs.paraIndent,
                            prefs.letterSpacingEm, prefs.lineHeightMultiplier, prefs.fontSizeSp,
                            prefs.fontWeight, prefs.textAlign, prefs.fontFamily, padTop, padBottom, padX,
                            isPageTranslationActive, prefs.translatorDisplayMode, prefs.translatorTargetLang,
                            pageTranslationMap[pageTransMapKey(curZero)],
                            pageTranslationMap[pageTransMapKey(curLeftIdx)],
                            pageTranslationMap[pageTransMapKey(curRightIdx)],
                            peekOriginalPdfPage,
                            uiState.isTranslationActive, uiState.translationMode, uiState.translationTargetLang, uiState.translationRevision
                        ) {
                            if (isSpread) {
                                val leftZero = curLeftIdx
                                val rightZero = curRightIdx
                                val leftTriple = getPageAtGlobalZero(leftZero)
                                val rightTriple = getPageAtGlobalZero(rightZero)
                                val leftCh = leftTriple?.let { chapters.getOrNull(it.first) } ?: currentChapter
                                val rightCh = rightTriple?.let { chapters.getOrNull(it.first) }
                                val (leftText, rightText) = resolveSpreadPairTexts(leftZero, rightZero, if (leftZero == curZero) currentPageRaw else "")
                                val canCacheSpread = !isPdfBook && isSpreadTranslationReady(leftZero, rightZero, if (leftZero == curZero) currentPageRaw else "")
                                val cacheKey = buildEpubSnapshotKey(leftZero, leftText.hashCode() * 31 + rightText.hashCode(), true, rightZero)
                                val cached = if (canCacheSpread) epubSnapshotCache.get(cacheKey) else null
                                if (cached != null) {
                                    value = cached
                                } else {
                                    val shouldTintLeft = isPdfBook && prefs.pdfThemeTintEnabled && effectiveTheme != AppThemeMode.WHITE &&
                                        !(prefs.pdfProtectIllustrations && PdfNativeRenderer.isPdfPageProtectedIllustration(
                                            leftZero, indexedIllustrationPages
                                        ))
                                    val shouldTintRight = isPdfBook && prefs.pdfThemeTintEnabled && effectiveTheme != AppThemeMode.WHITE &&
                                        rightZero < metrics.totalBookPages &&
                                        !(prefs.pdfProtectIllustrations && PdfNativeRenderer.isPdfPageProtectedIllustration(
                                            rightZero, indexedIllustrationPages
                                        ))
                                    value = withContext(Dispatchers.Default) {
                                        renderCachedTwoPageSpreadSnapshot(
                                            cacheKey = if (canCacheSpread) cacheKey else null,
                                            textMeasurer = textMeasurer,
                                            widthPx = spreadW,
                                            heightPx = spreadStageH,
                                            density = density,
                                            leftPageIndex = leftZero,
                                            rightPageIndex = rightZero,
                                            leftHeader = leftCh?.title.orEmpty(),
                                            rightHeader = rightCh?.title.orEmpty(),
                                            leftText = leftText,
                                            rightText = rightText,
                                            leftPdfBitmap = curLeftEffectivePdfBmp,
                                            rightPdfBitmap = curRightEffectivePdfBmp,
                                            bgColor = if (isPdfBook && !prefs.pdfThemeTintEnabled) Color.White else palette.bgCard,
                                            pageTextStyle = spreadTextStyle,
                                            palette = palette,
                                            prefs = prefs,
                                            leftColorFilter = if (shouldTintLeft) androidPdfThemeColorFilter else null,
                                            rightColorFilter = if (shouldTintRight) androidPdfThemeColorFilter else null
                                        )
                                    }
                                }
                            } else if (isPdfBook) {
                                value = curLeftEffectivePdfBmp?.asImageBitmap()
                            } else {
                                val curTriple = getPageAtGlobalZero(curZero)
                                val curCh = curTriple?.let { chapters.getOrNull(it.first) } ?: currentChapter
                                val curChIdx = curTriple?.first ?: uiState.activeChapterIndex
                                val curPInCh = curTriple?.second ?: uiState.activePageIndex
                                val curText = resolveEffectiveEpubText(curZero, currentPageRaw)
                                val canCachePage = isEpubPageTranslationReady(curZero, currentPageRaw)
                                val cacheKey = buildEpubSnapshotKey(curZero, curText.hashCode(), false)
                                val cached = if (canCachePage) epubSnapshotCache.get(cacheKey) else null
                                value = cached ?: withContext(Dispatchers.Default) {
                                    renderCachedEpubPageSnapshot(
                                        cacheKey = if (canCachePage) cacheKey else null,
                                        textMeasurer = textMeasurer,
                                        widthPx = stageW,
                                        heightPx = stageH,
                                        density = density,
                                        chapterTitle = curCh?.title.orEmpty(),
                                        pageText = curText,
                                        bgColor = palette.bgApp,
                                        pageTextStyle = baseTextStyle,
                                        padX = padX,
                                        padTop = padTop,
                                        padBottom = padBottom,
                                        showChapterHeader = curPInCh == 0,
                                        isCover = (curZero == 0),
                                        highlights = bookHighlights,
                                        chapterIndex = curChIdx,
                                        pageIndex = curPInCh,
                                        palette = palette,
                                        prefs = prefs
                                    )
                                }
                            }
                        }

                        val nextSpreadStart = spreadStart + spreadStep
                        val nextTriple = remember(chapters, curZero, isSpread, isBilingualSpread, spreadStart, nextSpreadStart) {
                            if (isSpread) {
                                if (nextSpreadStart < metrics.totalBookPages) getPageAtGlobalZero(nextSpreadStart) else null
                            } else {
                                getPageAtGlobalZero(curZero + 1)
                            }
                        }
                        val nextBitmapInitial = if (!isPdfBook) {
                            if (isSpread) {
                                if (nextSpreadStart < metrics.totalBookPages) {
                                    val (leftText, rightText) = resolveSpreadPairTexts(nextLeftIdx, nextRightIdx)
                                    epubSnapshotCache.get(buildEpubSnapshotKey(nextLeftIdx, leftText.hashCode() * 31 + rightText.hashCode(), true, nextRightIdx))
                                } else null
                            } else if (nextTriple != null) {
                                val nextPZero = curZero + 1
                                val nextText = resolveEffectiveEpubText(nextPZero)
                                epubSnapshotCache.get(buildEpubSnapshotKey(nextPZero, nextText.hashCode(), false))
                            } else null
                        } else null
                        val nextBitmap by produceState<ImageBitmap?>(
                            initialValue = nextBitmapInitial,
                            curZero, nextLeftIdx, nextRightIdx, isSpread, isBilingualSpread, isPdfBook, nextTriple,
                            nextLeftEffectivePdfBmp, nextRightEffectivePdfBmp,
                            palette.bgApp, palette.textMain, baseTextStyle, spreadTextStyle, stageW, stageH, spreadW, spreadStageH,
                            prefs.paraSpacingEm, prefs.wordSpacingPx, prefs.pageMargin, prefs.paraIndent,
                            prefs.letterSpacingEm, prefs.lineHeightMultiplier, prefs.fontSizeSp,
                            prefs.fontWeight, prefs.textAlign, prefs.fontFamily, padTop, padBottom, padX,
                            activeDraggingSliderKey == null,
                            isPageTranslationActive, prefs.translatorDisplayMode, prefs.translatorTargetLang,
                            pageTranslationMap[pageTransMapKey(if (isSpread) nextLeftIdx else curZero + 1)],
                            pageTranslationMap[pageTransMapKey(nextRightIdx)],
                            peekOriginalPdfPage,
                            uiState.isTranslationActive, uiState.translationMode, uiState.translationTargetLang, uiState.translationRevision
                        ) {
                            if (activeDraggingSliderKey != null) return@produceState
                            if (isSpread) {
                                if (nextSpreadStart >= metrics.totalBookPages) {
                                    value = null
                                } else {
                                    val leftZero = nextLeftIdx
                                    val rightZero = nextRightIdx
                                    val leftTripleSpread = getPageAtGlobalZero(leftZero)
                                    val rightTripleSpread = getPageAtGlobalZero(rightZero)
                                    val leftCh = leftTripleSpread?.let { chapters.getOrNull(it.first) }
                                    val rightCh = rightTripleSpread?.let { chapters.getOrNull(it.first) }
                                    val (leftText, rightText) = resolveSpreadPairTexts(leftZero, rightZero)
                                    val canCacheSpread = !isPdfBook && isSpreadTranslationReady(leftZero, rightZero)
                                    val cacheKey = buildEpubSnapshotKey(leftZero, leftText.hashCode() * 31 + rightText.hashCode(), true, rightZero)
                                    val cached = if (canCacheSpread) epubSnapshotCache.get(cacheKey) else null
                                    if (cached != null) {
                                        value = cached
                                    } else {
                                        val shouldTintLeft = isPdfBook && prefs.pdfThemeTintEnabled && effectiveTheme != AppThemeMode.WHITE &&
                                            !(prefs.pdfProtectIllustrations && PdfNativeRenderer.isPdfPageProtectedIllustration(
                                                leftZero, indexedIllustrationPages
                                            ))
                                        val shouldTintRight = isPdfBook && prefs.pdfThemeTintEnabled && effectiveTheme != AppThemeMode.WHITE &&
                                            rightZero < metrics.totalBookPages &&
                                                !(prefs.pdfProtectIllustrations && PdfNativeRenderer.isPdfPageProtectedIllustration(
                                                    rightZero, indexedIllustrationPages
                                                ))
                                        value = withContext(Dispatchers.Default) {
                                            renderCachedTwoPageSpreadSnapshot(
                                                cacheKey = if (canCacheSpread) cacheKey else null,
                                                textMeasurer = textMeasurer,
                                                widthPx = spreadW,
                                                heightPx = spreadStageH,
                                                density = density,
                                                leftPageIndex = leftZero,
                                                rightPageIndex = rightZero,
                                                leftHeader = leftCh?.title.orEmpty(),
                                                rightHeader = rightCh?.title.orEmpty(),
                                                leftText = leftText,
                                                rightText = rightText,
                                                leftPdfBitmap = nextLeftEffectivePdfBmp,
                                                rightPdfBitmap = nextRightEffectivePdfBmp,
                                                bgColor = if (isPdfBook && !prefs.pdfThemeTintEnabled) Color.White else palette.bgCard,
                                                pageTextStyle = spreadTextStyle,
                                                palette = palette,
                                                prefs = prefs,
                                                leftColorFilter = if (shouldTintLeft) androidPdfThemeColorFilter else null,
                                                rightColorFilter = if (shouldTintRight) androidPdfThemeColorFilter else null
                                            )
                                        }
                                    }
                                }
                            } else if (nextTriple == null) {
                                value = null
                            } else if (isPdfBook) {
                                value = nextLeftEffectivePdfBmp?.asImageBitmap()
                            } else {
                                val nextCh = nextTriple.let { chapters.getOrNull(it.first) }
                                val nextChIdx = nextTriple.first
                                val nextPInCh = nextTriple.second
                                val nextPZero = curZero + 1
                                val nextText = resolveEffectiveEpubText(nextPZero)
                                val canCachePage = isEpubPageTranslationReady(nextPZero)
                                val cacheKey = buildEpubSnapshotKey(nextPZero, nextText.hashCode(), false)
                                val cached = if (canCachePage) epubSnapshotCache.get(cacheKey) else null
                                value = cached ?: withContext(Dispatchers.Default) {
                                    renderCachedEpubPageSnapshot(
                                        cacheKey = if (canCachePage) cacheKey else null,
                                        textMeasurer = textMeasurer,
                                        widthPx = stageW,
                                        heightPx = stageH,
                                        density = density,
                                        chapterTitle = nextCh?.title.orEmpty(),
                                        pageText = nextText,
                                        bgColor = palette.bgApp,
                                        pageTextStyle = baseTextStyle,
                                        padX = padX,
                                        padTop = padTop,
                                        padBottom = padBottom,
                                        showChapterHeader = nextPInCh == 0,
                                        isCover = (nextPZero == 0),
                                        highlights = bookHighlights,
                                        chapterIndex = nextChIdx,
                                        pageIndex = nextPInCh,
                                        palette = palette,
                                        prefs = prefs
                                    )
                                }
                            }
                        }

                        val prevSpreadStart = spreadStart - spreadStep
                        val prevTriple = remember(chapters, curZero, isSpread, isBilingualSpread, spreadStart, prevSpreadStart) {
                            if (isSpread) {
                                if (prevSpreadStart >= 0) getPageAtGlobalZero(prevSpreadStart) else null
                            } else {
                                getPageAtGlobalZero(curZero - 1)
                            }
                        }
                        val prevBitmapInitial = if (!isPdfBook) {
                            if (isSpread) {
                                if (prevSpreadStart >= 0) {
                                    val (leftText, rightText) = resolveSpreadPairTexts(prevLeftIdx, prevRightIdx)
                                    epubSnapshotCache.get(buildEpubSnapshotKey(prevLeftIdx, leftText.hashCode() * 31 + rightText.hashCode(), true, prevRightIdx))
                                } else null
                            } else if (prevTriple != null) {
                                val prevPZero = curZero - 1
                                val prevText = resolveEffectiveEpubText(prevPZero)
                                epubSnapshotCache.get(buildEpubSnapshotKey(prevPZero, prevText.hashCode(), false))
                            } else null
                        } else null
                        val prevBitmap by produceState<ImageBitmap?>(
                            initialValue = prevBitmapInitial,
                            curZero, prevLeftIdx, prevRightIdx, isSpread, isBilingualSpread, isPdfBook, prevTriple,
                            prevLeftEffectivePdfBmp, prevRightEffectivePdfBmp,
                            palette.bgApp, palette.textMain, baseTextStyle, spreadTextStyle, stageW, stageH, spreadW, spreadStageH,
                            prefs.paraSpacingEm, prefs.wordSpacingPx, prefs.pageMargin, prefs.paraIndent,
                            prefs.letterSpacingEm, prefs.lineHeightMultiplier, prefs.fontSizeSp,
                            prefs.fontWeight, prefs.textAlign, prefs.fontFamily, padTop, padBottom, padX,
                            activeDraggingSliderKey == null,
                            isPageTranslationActive, prefs.translatorDisplayMode, prefs.translatorTargetLang,
                            pageTranslationMap[pageTransMapKey(if (isSpread) prevLeftIdx else curZero - 1)],
                            pageTranslationMap[pageTransMapKey(prevRightIdx)],
                            peekOriginalPdfPage,
                            uiState.isTranslationActive, uiState.translationMode, uiState.translationTargetLang, uiState.translationRevision
                        ) {
                            if (activeDraggingSliderKey != null) return@produceState
                            if (isSpread) {
                                if (prevSpreadStart < 0) {
                                    value = null
                                } else {
                                    val leftZero = prevLeftIdx
                                    val rightZero = prevRightIdx
                                    val leftTripleSpread = getPageAtGlobalZero(leftZero)
                                    val rightTripleSpread = getPageAtGlobalZero(rightZero)
                                    val leftCh = leftTripleSpread?.let { chapters.getOrNull(it.first) }
                                    val rightCh = rightTripleSpread?.let { chapters.getOrNull(it.first) }
                                    val (leftText, rightText) = resolveSpreadPairTexts(leftZero, rightZero)
                                    val canCacheSpread = !isPdfBook && isSpreadTranslationReady(leftZero, rightZero)
                                    val cacheKey = buildEpubSnapshotKey(leftZero, leftText.hashCode() * 31 + rightText.hashCode(), true, rightZero)
                                    val cached = if (canCacheSpread) epubSnapshotCache.get(cacheKey) else null
                                    if (cached != null) {
                                        value = cached
                                    } else {
                                        val shouldTintLeft = isPdfBook && prefs.pdfThemeTintEnabled && effectiveTheme != AppThemeMode.WHITE &&
                                            !(prefs.pdfProtectIllustrations && PdfNativeRenderer.isPdfPageProtectedIllustration(
                                                leftZero, indexedIllustrationPages
                                            ))
                                        val shouldTintRight = isPdfBook && prefs.pdfThemeTintEnabled && effectiveTheme != AppThemeMode.WHITE &&
                                            rightZero < metrics.totalBookPages &&
                                                !(prefs.pdfProtectIllustrations && PdfNativeRenderer.isPdfPageProtectedIllustration(
                                                    rightZero, indexedIllustrationPages
                                                ))
                                        value = withContext(Dispatchers.Default) {
                                            renderCachedTwoPageSpreadSnapshot(
                                                cacheKey = if (canCacheSpread) cacheKey else null,
                                                textMeasurer = textMeasurer,
                                                widthPx = spreadW,
                                                heightPx = spreadStageH,
                                                density = density,
                                                leftPageIndex = leftZero,
                                                rightPageIndex = rightZero,
                                                leftHeader = leftCh?.title.orEmpty(),
                                                rightHeader = rightCh?.title.orEmpty(),
                                                leftText = leftText,
                                                rightText = rightText,
                                                leftPdfBitmap = prevLeftEffectivePdfBmp,
                                                rightPdfBitmap = prevRightEffectivePdfBmp,
                                                bgColor = if (isPdfBook && !prefs.pdfThemeTintEnabled) Color.White else palette.bgCard,
                                                pageTextStyle = spreadTextStyle,
                                                palette = palette,
                                                prefs = prefs,
                                                leftColorFilter = if (shouldTintLeft) androidPdfThemeColorFilter else null,
                                                rightColorFilter = if (shouldTintRight) androidPdfThemeColorFilter else null
                                            )
                                        }
                                    }
                                }
                            } else if (prevTriple == null) {
                                value = null
                            } else if (isPdfBook) {
                                value = prevLeftEffectivePdfBmp?.asImageBitmap()
                            } else {
                                val prevCh = prevTriple.let { chapters.getOrNull(it.first) }
                                val prevChIdx = prevTriple.first
                                val prevPInCh = prevTriple.second
                                val prevPZero = curZero - 1
                                val prevText = resolveEffectiveEpubText(prevPZero)
                                val canCachePage = isEpubPageTranslationReady(prevPZero)
                                val cacheKey = buildEpubSnapshotKey(prevPZero, prevText.hashCode(), false)
                                val cached = if (canCachePage) epubSnapshotCache.get(cacheKey) else null
                                value = cached ?: withContext(Dispatchers.Default) {
                                    renderCachedEpubPageSnapshot(
                                        cacheKey = if (canCachePage) cacheKey else null,
                                        textMeasurer = textMeasurer,
                                        widthPx = stageW,
                                        heightPx = stageH,
                                        density = density,
                                        chapterTitle = prevCh?.title.orEmpty(),
                                        pageText = prevText,
                                        bgColor = palette.bgApp,
                                        pageTextStyle = baseTextStyle,
                                        padX = padX,
                                        padTop = padTop,
                                        padBottom = padBottom,
                                        showChapterHeader = prevPInCh == 0,
                                        isCover = (prevPZero == 0),
                                        highlights = bookHighlights,
                                        chapterIndex = prevChIdx,
                                        pageIndex = prevPInCh,
                                        palette = palette,
                                        prefs = prefs
                                    )
                                }
                            }
                        }

                        LaunchedEffect(curBitmap) {
                            if (curBitmap != null) {
                                committedCurrentBmp = null
                            }
                        }

                        LaunchedEffect(
                            pageTranslationMap[pageTransMapKey(curZero)],
                            pageTranslationMap[pageTransMapKey(curLeftIdx)],
                            pageTranslationMap[pageTransMapKey(curRightIdx)],
                            pageTranslationMap[pageTransMapKey(nextLeftIdx)],
                            pageTranslationMap[pageTransMapKey(nextRightIdx)],
                            pageTranslationMap[pageTransMapKey(prevLeftIdx)],
                            pageTranslationMap[pageTransMapKey(prevRightIdx)],
                            prefs.translatorDisplayMode,
                            prefs.translatorTargetLang,
                            isPageTranslationActive,
                            peekOriginalPdfPage,
                            uiState.isTranslationActive,
                            uiState.translationMode,
                            uiState.translationTargetLang,
                            uiState.translationRevision
                        ) {
                            committedCurrentBmp = null
                            lockedCurrBmp = null
                            lockedNextBmp = null
                            lockedPrevBmp = null
                        }

                        val curPdfColorFilter = pdfFilterForPage(curLeftIdx)
                        val nextPdfColorFilter = pdfFilterForPage(nextLeftIdx)
                        val prevPdfColorFilter = pdfFilterForPage(prevLeftIdx)

                        val activeCurrBmp = committedCurrentBmp ?: (if (isFlippingInProgress) (lockedCurrBmp ?: curBitmap) else curBitmap)
                        val activeNextBmp = if (isFlippingInProgress) (lockedNextBmp ?: nextBitmap) else nextBitmap
                        val activePrevBmp = if (isFlippingInProgress) (lockedPrevBmp ?: prevBitmap) else prevBitmap

                        var flipTurnDir by remember { mutableIntStateOf(1) }
                        var liveDragProgress by remember { mutableFloatStateOf(0f) }
                        var liveCornerYNorm by remember { mutableFloatStateOf(0.76f) }

                        val flipbookGestureModifier = Modifier.pointerInput(
                            curZero, metrics.totalBookPages, hasSelectionActive, selStartHandleAnchor, selEndHandleAnchor,
                            showPageScrubber, showInlinePageSlider, uiState.showReaderTypeSheet, uiState.showReaderTocDrawer, uiState.showReaderNotesDrawer
                        ) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                if (isTurnCommitting) return@awaitEachGesture
                                if (showPageScrubber ||
                                    showInlinePageSlider ||
                                    down.position.y >= size.height - 96.dp.toPx() ||
                                    down.position.y <= 88.dp.toPx() ||
                                    uiState.showReaderTypeSheet ||
                                    uiState.showReaderTocDrawer ||
                                    uiState.showReaderNotesDrawer
                                ) {
                                    return@awaitEachGesture
                                }
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
                                var isMultiTouchPinch = false
                                val velocityTracker = VelocityTracker()
                                velocityTracker.addPosition(down.uptimeMillis, down.position)

                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val pressedChanges = event.changes.filter { it.pressed }

                                    // 1. MULTI-TOUCH PINCH-TO-ZOOM (2+ fingers down at any point)
                                    if (pressedChanges.size >= 2 || isMultiTouchPinch) {
                                        if (!isMultiTouchPinch) {
                                            isMultiTouchPinch = true
                                            dragLocked = false
                                            liveDragProgress = 0f
                                            isFlippingInProgress = false
                                            lockedCurrBmp = null
                                            lockedNextBmp = null
                                            lockedPrevBmp = null
                                            lockedCurrFilter = null
                                            lockedNextFilter = null
                                            lockedPrevFilter = null
                                        }
                                        if (pressedChanges.size >= 2) {
                                            val zoomChange = event.calculateZoom()
                                            val panChange = event.calculatePan()
                                            val newZoom = (readerZoom * zoomChange).coerceIn(1f, 4f)
                                            val maxPanX = (size.width.toFloat() * (newZoom - 1f)) * 0.5f
                                            val maxPanY = (size.height.toFloat() * (newZoom - 1f)) * 0.5f
                                            readerZoom = if (newZoom <= 1.02f) 1f else newZoom
                                            readerPan = if (readerZoom <= 1f) {
                                                Offset.Zero
                                            } else {
                                                Offset(
                                                    x = (readerPan.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                                    y = (readerPan.y + panChange.y).coerceIn(-maxPanY, maxPanY)
                                                )
                                            }
                                        }
                                        event.changes.forEach { it.consume() }
                                        if (pressedChanges.isEmpty()) {
                                            break
                                        }
                                        continue
                                    }

                                    // 2. SINGLE-FINGER PAN WHEN CURRENTLY ZOOMED IN (readerZoom > 1.01f)
                                    if (readerZoom > 1.01f) {
                                        val panChange = event.calculatePan()
                                        val maxPanX = (size.width.toFloat() * (readerZoom - 1f)) * 0.5f
                                        val maxPanY = (size.height.toFloat() * (readerZoom - 1f)) * 0.5f
                                        readerPan = Offset(
                                            x = (readerPan.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                            y = (readerPan.y + panChange.y).coerceIn(-maxPanY, maxPanY)
                                        )
                                        event.changes.forEach { it.consume() }
                                        if (pressedChanges.isEmpty()) {
                                            break
                                        }
                                        continue
                                    }

                                    // 3. EXISTING SINGLE-FINGER PAGE FLIP LOGIC (only when !isMultiTouchPinch && readerZoom <= 1.01f)
                                    val change = event.changes.firstOrNull() ?: break
                                    if (change.changedToUp()) {
                                        if (dragLocked && isFlippingInProgress) {
                                            val velX = velocityTracker.calculateVelocity().x
                                            val speedInDir = if (flipTurnDir > 0) -velX else velX
                                            val shouldCommit = liveDragProgress >= 0.14f || (speedInDir > 350f)
                                            scope.launch {
                                                if (shouldCommit) {
                                                    isTurnCommitting = true
                                                    isFlippingInProgress = true
                                                    val anim = Animatable(liveDragProgress)
                                                    anim.animateTo(1f, tween(220, easing = FastOutSlowInEasing)) {
                                                        liveDragProgress = value
                                                    }
                                                    val dir = flipTurnDir
                                                    val targetPage = if (isSpread) {
                                                        val sStart = if (isBilingualSpread) displayPageIndex else (displayPageIndex / 2) * 2
                                                        val maxSpread = if (isBilingualSpread) (metrics.totalBookPages - 1).coerceAtLeast(0) else (((metrics.totalBookPages - 1) / 2) * 2).coerceAtLeast(0)
                                                        if (dir > 0) (sStart + spreadStep).coerceAtMost(maxSpread) else (sStart - spreadStep).coerceAtLeast(0)
                                                    } else {
                                                        if (dir > 0) (displayPageIndex + 1).coerceAtMost(metrics.totalBookPages - 1) else (displayPageIndex - 1).coerceAtLeast(0)
                                                    }
                                                    val nextB = lockedNextBmp ?: nextBitmap
                                                    val prevB = lockedPrevBmp ?: prevBitmap
                                                    val currB = lockedCurrBmp ?: curBitmap
                                                    if (dir > 0 && nextB != null) {
                                                        lockedPrevBmp = currB
                                                        lockedCurrBmp = nextB
                                                        lockedNextBmp = null
                                                        committedCurrentBmp = nextB
                                                    } else if (dir < 0 && prevB != null) {
                                                        lockedNextBmp = currB
                                                        lockedCurrBmp = prevB
                                                        lockedPrevBmp = null
                                                        committedCurrentBmp = prevB
                                                    }
                                                    lockedCurrFilter = if (dir > 0) lockedNextFilter else lockedPrevFilter
                                                    displayPageIndex = targetPage
                                                    liveDragProgress = 0f
                                                    viewModel?.jumpToGlobalPage(targetPage)
                                                    withFrameNanos { }
                                                    withFrameNanos { }
                                                    isTurnCommitting = false
                                                    isFlippingInProgress = false
                                                    lockedCurrBmp = null
                                                    lockedNextBmp = null
                                                    lockedPrevBmp = null
                                                    lockedCurrFilter = null
                                                    lockedNextFilter = null
                                                    lockedPrevFilter = null
                                                } else {
                                                    val anim = Animatable(liveDragProgress)
                                                    anim.animateTo(0f, tween(160, easing = FastOutSlowInEasing)) {
                                                        liveDragProgress = value
                                                    }
                                                    liveDragProgress = 0f
                                                    isFlippingInProgress = false
                                                    lockedCurrBmp = null
                                                    lockedNextBmp = null
                                                    lockedPrevBmp = null
                                                    lockedCurrFilter = null
                                                    lockedNextFilter = null
                                                    lockedPrevFilter = null
                                                }
                                            }
                                        } else if (!dragLocked) {
                                            val relX = down.position.x / size.width.toFloat().coerceAtLeast(1f)
                                            if (relX >= 0.76f) {
                                                val canGoNext = if (isSpread) (spreadStart + spreadStep < metrics.totalBookPages) else (curZero + 1 < metrics.totalBookPages)
                                                if (canGoNext && nextBitmap != null) {
                                                    lockedCurrBmp = curBitmap
                                                    lockedNextBmp = nextBitmap
                                                    lockedPrevBmp = prevBitmap
                                                    lockedCurrFilter = curPdfColorFilter
                                                    lockedNextFilter = nextPdfColorFilter
                                                    lockedPrevFilter = prevPdfColorFilter
                                                    isFlippingInProgress = true
                                                    externalFlipTrigger = 1
                                                }
                                            } else if (relX <= 0.24f) {
                                                val canGoPrev = if (isSpread) (spreadStart - spreadStep >= 0) else (curZero - 1 >= 0)
                                                if (canGoPrev && prevBitmap != null) {
                                                    lockedCurrBmp = curBitmap
                                                    lockedNextBmp = nextBitmap
                                                    lockedPrevBmp = prevBitmap
                                                    lockedCurrFilter = curPdfColorFilter
                                                    lockedNextFilter = nextPdfColorFilter
                                                    lockedPrevFilter = prevPdfColorFilter
                                                    isFlippingInProgress = true
                                                    externalFlipTrigger = -1
                                                }
                                            } else {
                                                val elapsed = change.uptimeMillis - down.uptimeMillis
                                                val childHandlesTap = !isPdfBook && !isSpread && !isBilingualMode
                                                if (elapsed < 340L && (!childHandlesTap || selectedQuoteForPopover != null)) {
                                                    if (hasSelectionActive || selectedQuoteForPopover != null) {
                                                        hasSelectionActive = false
                                                        clearSelectionTrigger++
                                                        selectedQuoteForPopover = null
                                                    } else {
                                                        showReaderChrome = !showReaderChrome
                                                    }
                                                }
                                            }
                                        }
                                        break
                                    }

                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    totalDx = change.position.x - down.position.x
                                    totalDy = change.position.y - down.position.y

                                    if (!dragLocked) {
                                        val isBilingualEpub = !isPdfBook && isPageTranslationActive && prefs.translatorDisplayMode == "bilingual"
                                        val horizThresholdRatio = if (isBilingualEpub) 1.35f else 1.15f
                                        if (abs(totalDx) > touchSlop && abs(totalDx) > abs(totalDy) * horizThresholdRatio) {
                                            val candidateDir = if (totalDx < 0f) 1 else -1
                                            if (candidateDir > 0) {
                                                val maxSpread = if (isBilingualSpread) (metrics.totalBookPages - 1).coerceAtLeast(0) else (((metrics.totalBookPages - 1) / 2) * 2).coerceAtLeast(0)
                                                val isAtEnd = if (isSpread) (spreadStart >= maxSpread) else (curZero >= metrics.totalBookPages - 1)
                                                if (isAtEnd) break
                                            } else {
                                                val isAtStart = if (isSpread) (spreadStart <= 0) else (curZero <= 0)
                                                if (isAtStart) break
                                            }
                                            dragLocked = true
                                            flipTurnDir = candidateDir
                                            lockedCurrBmp = curBitmap
                                            lockedNextBmp = nextBitmap
                                            lockedPrevBmp = prevBitmap
                                            lockedCurrFilter = curPdfColorFilter
                                            lockedNextFilter = nextPdfColorFilter
                                            lockedPrevFilter = prevPdfColorFilter
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
                                .padding(
                                    start = if (isSpread) 16.dp else 0.dp,
                                    end = if (isSpread) 16.dp else 0.dp,
                                    top = spreadTopPadDp,
                                    bottom = spreadBottomPadDp
                                )
                                .then(flipbookGestureModifier),
                            contentAlignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clipToBounds()
                                    .graphicsLayer {
                                        scaleX = readerZoom
                                        scaleY = readerZoom
                                        translationX = readerPan.x
                                        translationY = readerPan.y
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val activeBmp = activeCurrBmp ?: curBitmap
                                if (activeBmp != null) {
                                    val pdfTintedPaperColor = if (isPdfBook && prefs.pdfThemeTintEnabled && effectiveTheme != AppThemeMode.WHITE) palette.bgCard else null

                                    CylindricalFlipbookCanvas(
                                        currentPageBitmap = activeBmp,
                                        nextPageBitmap = activeNextBmp ?: nextBitmap,
                                        prevPageBitmap = activePrevBmp ?: prevBitmap,
                                        themeBgColor = if (isSpread) palette.bgCard else palette.bgApp,
                                        backPaperColor = if (isPdfBook && pdfTintedPaperColor == null) Color(0xFFF2EFE9) else palette.flipBackPaper,
                                        isPdfBook = isPdfBook,
                                        isTwoPageSpread = isSpread,
                                        curPageColorFilter = if (isFlippingInProgress) lockedCurrFilter else curPdfColorFilter,
                                        nextPageColorFilter = if (isFlippingInProgress) lockedNextFilter else nextPdfColorFilter,
                                        prevPageColorFilter = if (isFlippingInProgress) lockedPrevFilter else prevPdfColorFilter,
                                        pdfTintedPaperColor = pdfTintedPaperColor,
                                        externalTriggerDir = externalFlipTrigger,
                                        onExternalTriggerConsumed = { externalFlipTrigger = 0 },
                                        manualProgress = { if (isFlippingInProgress) liveDragProgress else null },
                                        manualCornerYNorm = { if (isFlippingInProgress) liveCornerYNorm else null },
                                        manualTurnDir = if (isFlippingInProgress) flipTurnDir else null,
                                        onPageTurnCommitted = { dir ->
                                            scope.launch {
                                                isTurnCommitting = true
                                                isFlippingInProgress = true
                                                val targetPage = if (isSpread) {
                                                    val sStart = if (isBilingualSpread) displayPageIndex else (displayPageIndex / 2) * 2
                                                    val maxSpread = if (isBilingualSpread) (metrics.totalBookPages - 1).coerceAtLeast(0) else (((metrics.totalBookPages - 1) / 2) * 2).coerceAtLeast(0)
                                                    if (dir > 0) (sStart + spreadStep).coerceAtMost(maxSpread) else (sStart - spreadStep).coerceAtLeast(0)
                                                } else {
                                                    (displayPageIndex + dir).coerceIn(0, metrics.totalBookPages - 1)
                                                }
                                                val nextB = lockedNextBmp ?: nextBitmap
                                                val prevB = lockedPrevBmp ?: prevBitmap
                                                val currB = lockedCurrBmp ?: curBitmap
                                                if (dir > 0 && nextB != null) {
                                                    lockedPrevBmp = currB
                                                    lockedCurrBmp = nextB
                                                    lockedNextBmp = null
                                                    committedCurrentBmp = nextB
                                                } else if (dir < 0 && prevB != null) {
                                                    lockedNextBmp = currB
                                                    lockedCurrBmp = prevB
                                                    lockedPrevBmp = null
                                                    committedCurrentBmp = prevB
                                                }
                                                lockedCurrFilter = if (dir > 0) lockedNextFilter else lockedPrevFilter
                                                displayPageIndex = targetPage
                                                liveDragProgress = 0f
                                                viewModel?.jumpToGlobalPage(targetPage)
                                                withFrameNanos { }
                                                withFrameNanos { }
                                                isTurnCommitting = false
                                                isFlippingInProgress = false
                                                lockedCurrBmp = null
                                                lockedNextBmp = null
                                                lockedPrevBmp = null
                                                lockedCurrFilter = null
                                                lockedNextFilter = null
                                                lockedPrevFilter = null
                                            }
                                        },
                                        onCenterTap = {
                                            if (hasSelectionActive || selectedQuoteForPopover != null) {
                                                hasSelectionActive = false
                                                clearSelectionTrigger++
                                                selectedQuoteForPopover = null
                                            } else {
                                                showReaderChrome = !showReaderChrome
                                            }
                                        },
                                        onFlipStateChanged = { flipping ->
                                            if (flipping && !isFlippingInProgress) {
                                                lockedCurrBmp = curBitmap
                                                lockedNextBmp = nextBitmap
                                                lockedPrevBmp = prevBitmap
                                                lockedCurrFilter = curPdfColorFilter
                                                lockedNextFilter = nextPdfColorFilter
                                                lockedPrevFilter = prevPdfColorFilter
                                                isFlippingInProgress = true
                                            } else if (!flipping && !isTurnCommitting) {
                                                isFlippingInProgress = false
                                                lockedCurrBmp = null
                                                lockedNextBmp = null
                                                lockedPrevBmp = null
                                                lockedCurrFilter = null
                                                lockedNextFilter = null
                                                lockedPrevFilter = null
                                            }
                                        },
                                        modifier = if (isSpread) {
                                            Modifier
                                                .fillMaxHeight()
                                                .aspectRatio(1.414f)
                                                .shadow(14.dp, RoundedCornerShape(6.dp))
                                                .clip(RoundedCornerShape(6.dp))
                                                .border(1.dp, palette.borderSoft, RoundedCornerShape(6.dp))
                                        } else {
                                            Modifier.fillMaxSize()
                                        }
                                    )
                                }

                                if (!isPdfBook && !isSpread && !isFlippingInProgress && externalFlipTrigger == 0) {
                                    val curTriple = getPageAtGlobalZero(curZero)
                                    val curCh = curTriple?.let { chapters.getOrNull(it.first) } ?: currentChapter
                                    val curText = curTriple?.third.orEmpty().ifBlank { currentPageRaw }
                                    val curChIdx = curTriple?.first ?: uiState.activeChapterIndex
                                    val curPInCh = curTriple?.second ?: uiState.activePageIndex

                                    val curCachedTrans = getEffectivePageTranslationPairs(curZero, curText)
                                    val isTransActiveNow = (isPageTranslationActive || uiState.isTranslationActive) &&
                                        !uiState.translationMode.equals("original", ignoreCase = true)
                                    val isBilingualActive = isTransActiveNow && uiState.translationMode.equals("bilingual", ignoreCase = true)
                                    val hasActivePageTranslation = isBilingualActive || (isTransActiveNow && !curCachedTrans.isNullOrEmpty())
                                    val effectiveTextForFit = resolveEffectiveEpubText(curZero, curText)
                                    val curFitScale = remember(
                                        curZero, effectiveTextForFit, stageW, stageH, baseTextStyle,
                                        padX, padTop, padBottom, curCh?.title, curPInCh, bookHighlights, palette, prefs
                                    ) {
                                        if (isBilingualActive) 1f else computeOnePageFitScale(
                                            textMeasurer = textMeasurer,
                                            widthPx = stageW,
                                            heightPx = stageH,
                                            density = density,
                                            chapterTitle = curCh?.title.orEmpty(),
                                            pageText = effectiveTextForFit,
                                            baseTextStyle = baseTextStyle,
                                            padX = padX,
                                            padTop = padTop,
                                            padBottom = padBottom,
                                            showChapterHeader = curPInCh == 0,
                                            isCover = (curZero == 0),
                                            highlights = bookHighlights,
                                            chapterIndex = curChIdx,
                                            pageIndex = curPInCh,
                                            palette = palette,
                                            prefs = prefs
                                        )
                                    }
                                    val contentModifier = if (hasActivePageTranslation) {
                                        Modifier.fillMaxSize().background(if (isSpread) palette.bgCard else palette.bgApp)
                                    } else {
                                        Modifier.fillMaxSize()
                                    }

                                    Box(modifier = contentModifier) {
                                        InteractiveTextPageContent(
                                            bookId = book.id,
                                            displayPageIndex = curZero,
                                            chapterTitle = curCh?.title.orEmpty(),
                                            pageText = curText,
                                            currentChapterIndex = curChIdx,
                                            currentPageInChapter = curPInCh,
                                            showChapterHeader = curPInCh == 0,
                                            scrollable = isBilingualActive,
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
                                            fontScale = curFitScale,
                                            transparentTextForFlipbook = (activeBmp != null && !hasActivePageTranslation),
                                            topClearanceDp = topClearanceDp,
                                            bottomClearanceDp = bottomClearanceDp,
                                            clearSelectionTrigger = clearSelectionTrigger,
                                            onSelectionActiveChanged = { active, start, end ->
                                                hasSelectionActive = active
                                                selStartHandleAnchor = start
                                                selEndHandleAnchor = end
                                            },
                                            onCenterTap = {
                                                if (hasSelectionActive || selectedQuoteForPopover != null) {
                                                    hasSelectionActive = false
                                                    clearSelectionTrigger++
                                                    selectedQuoteForPopover = null
                                                } else {
                                                    showReaderChrome = !showReaderChrome
                                                }
                                            },
                                            isPageTranslationActive = isPageTranslationActive,
                                            translatedParagraphs = curCachedTrans,
                                            viewModel = viewModel,
                                            uiState = uiState
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        @Composable
                        fun RenderSpreadPageForNonFlipbook(leftZero: Int, rightZero: Int) {
                            val leftTriple = getPageAtGlobalZero(leftZero)
                            val rightTriple = getPageAtGlobalZero(rightZero)
                            val leftCh = leftTriple?.let { chapters.getOrNull(it.first) } ?: currentChapter
                            val rightCh = rightTriple?.let { chapters.getOrNull(it.first) }
                            val (leftText, rightText) = resolveSpreadPairTexts(leftZero, rightZero, if (leftZero == curZero) currentPageRaw else "")

                            var leftPdfBmp by remember(book.id, leftZero) {
                                mutableStateOf(
                                    if (isPdfBook) {
                                        viewModel?.getCachedPdfBitmap(book.id, leftZero, book.localFilePath)
                                            ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath ?: "", leftZero, 1080)
                                            ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath ?: "", leftZero, ((stageW / 240) * 120).coerceAtLeast(360))
                                    } else null
                                )
                            }
                            var rightPdfBmp by remember(book.id, rightZero, isBilingualSpread, uiState.translationTargetLang, uiState.translationMode, uiState.translationRevision) {
                                mutableStateOf(
                                    if (isPdfBook) {
                                        if (isBilingualSpread) {
                                            viewModel?.getCachedTranslatedPdfBitmap(book.id, rightZero, uiState.translationTargetLang, "replace")
                                        } else {
                                            viewModel?.getCachedPdfBitmap(book.id, rightZero, book.localFilePath)
                                                ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath ?: "", rightZero, 1080)
                                                ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath ?: "", rightZero, ((stageW / 240) * 120).coerceAtLeast(360))
                                        }
                                    } else null
                                )
                            }

                            if (isPdfBook) {
                                LaunchedEffect(book.id, book.localFilePath, leftZero, rightZero, isBilingualSpread, uiState.translationTargetLang, uiState.translationMode, uiState.translationRevision) {
                                    val path = book.localFilePath ?: return@LaunchedEffect
                                    if (leftPdfBmp == null) {
                                        leftPdfBmp = viewModel?.loadPdfPageBitmap(book.id, path, leftZero)
                                    }
                                    if (isBilingualSpread) {
                                        rightPdfBmp = viewModel?.loadOrTranslatePdfPageBitmap(
                                            bookId = book.id,
                                            filePath = path,
                                            pageIndex = rightZero,
                                            targetLang = uiState.translationTargetLang,
                                            mode = "replace",
                                            targetWidthPx = stageW / 2
                                        )
                                    } else if (rightPdfBmp == null) {
                                        rightPdfBmp = viewModel?.loadPdfPageBitmap(book.id, path, rightZero)
                                    }
                                    viewModel?.prefetchPdfNeighbors(book.id, path, leftZero, isTwoPageSpread = true)
                                }
                            } else {
                                LaunchedEffect(
                                    book.id, leftZero, rightZero, isBilingualSpread,
                                    isPageTranslationActive, uiState.isTranslationActive,
                                    uiState.translationTargetLang, uiState.translationMode
                                ) {
                                    val isTransActiveSpread = (isPageTranslationActive || uiState.isTranslationActive) &&
                                        !uiState.translationMode.equals("original", ignoreCase = true)
                                    if (!isTransActiveSpread) return@LaunchedEffect
                                    val targetLangNow = uiState.translationTargetLang
                                    val spreadPages = if (isBilingualSpread) listOf(leftZero) else listOf(leftZero, rightZero)
                                    for (pZero in spreadPages) {
                                        if (pZero in 0 until metrics.totalBookPages && !isEpubPageTranslationReady(pZero)) {
                                            val pRaw = getPageAtGlobalZero(pZero)?.third.orEmpty()
                                            if (pRaw.isNotBlank()) {
                                                val res = if (viewModel != null) {
                                                    viewModel.loadOrTranslatePageParagraphs(
                                                        bookId = book.id,
                                                        pageIndex = pZero,
                                                        pageRaw = pRaw,
                                                        targetLang = targetLangNow
                                                    )
                                                } else {
                                                    translationRepo.translatePageParagraphs(pRaw, prefs, context, targetLangNow).getOrNull()
                                                }
                                                if (res != null) {
                                                    pageTranslationMap = pageTranslationMap + (pageTransMapKey(pZero, targetLangNow) to res)
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            val effLeftBmp = if (isBilingualSpread) leftPdfBmp else getEffectivePdfPageBitmap(leftZero, leftPdfBmp)
                            val effRightBmp = if (isBilingualSpread) rightPdfBmp else getEffectivePdfPageBitmap(rightZero, rightPdfBmp)
                            val canCacheSpread = !isPdfBook && isSpreadTranslationReady(leftZero, rightZero, if (leftZero == curZero) currentPageRaw else "")
                            val spreadCacheKey = buildEpubSnapshotKey(leftZero, leftText.hashCode() * 31 + rightText.hashCode(), true, rightZero)
                            val shouldTintLeft = isPdfBook && prefs.pdfThemeTintEnabled && effectiveTheme != AppThemeMode.WHITE &&
                                !(prefs.pdfProtectIllustrations && PdfNativeRenderer.isPdfPageProtectedIllustration(
                                    leftZero, indexedIllustrationPages
                                ))
                            val shouldTintRight = isPdfBook && prefs.pdfThemeTintEnabled && effectiveTheme != AppThemeMode.WHITE &&
                                rightZero < metrics.totalBookPages &&
                                !(prefs.pdfProtectIllustrations && PdfNativeRenderer.isPdfPageProtectedIllustration(
                                    rightZero, indexedIllustrationPages
                                ))
                            val spreadBmp by produceState<ImageBitmap?>(
                                initialValue = if (canCacheSpread) epubSnapshotCache.get(spreadCacheKey) else null,
                                spreadCacheKey, canCacheSpread, leftZero, rightZero, isBilingualSpread, leftText, rightText,
                                stageW, stageH, spreadW, spreadStageH,
                                prefs.paraSpacingEm, prefs.wordSpacingPx, prefs.pageMargin, prefs.paraIndent,
                                prefs.letterSpacingEm, prefs.lineHeightMultiplier, prefs.fontSizeSp,
                                prefs.fontWeight, prefs.textAlign, prefs.fontFamily,
                                palette, isPdfBook, effLeftBmp, effRightBmp,
                                prefs.pdfThemeTintEnabled, prefs.pdfProtectIllustrations, effectiveTheme, prefs.amoledBlack,
                                isPageTranslationActive, uiState.isTranslationActive, uiState.translationMode,
                                uiState.translationTargetLang, uiState.translationRevision,
                                pageTranslationMap[pageTransMapKey(leftZero)],
                                pageTranslationMap[pageTransMapKey(rightZero)]
                            ) {
                                val cached = if (canCacheSpread) epubSnapshotCache.get(spreadCacheKey) else null
                                value = cached ?: withContext(Dispatchers.Default) {
                                    renderCachedTwoPageSpreadSnapshot(
                                        cacheKey = if (canCacheSpread) spreadCacheKey else null,
                                        textMeasurer = textMeasurer,
                                        widthPx = spreadW,
                                        heightPx = spreadStageH,
                                        density = density,
                                        leftPageIndex = leftZero,
                                        rightPageIndex = rightZero,
                                        leftHeader = leftCh?.title.orEmpty(),
                                        rightHeader = rightCh?.title.orEmpty(),
                                        leftText = leftText,
                                        rightText = rightText,
                                        leftPdfBitmap = effLeftBmp,
                                        rightPdfBitmap = effRightBmp,
                                        bgColor = if (isPdfBook && !prefs.pdfThemeTintEnabled) Color.White else palette.bgCard,
                                        pageTextStyle = spreadTextStyle,
                                        palette = palette,
                                        prefs = prefs,
                                        leftColorFilter = if (shouldTintLeft) androidPdfThemeColorFilter else null,
                                        rightColorFilter = if (shouldTintRight) androidPdfThemeColorFilter else null
                                    )
                                }
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = spreadTopPadDp,
                                        bottom = spreadBottomPadDp
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (spreadBmp != null) {
                                    Image(
                                        bitmap = spreadBmp!!,
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
                        }

                        @Composable
                        fun RenderPageForNonFlipbook(pageZero: Int) {
                            if (isSpread) {
                                val leftZero = if (isBilingualSpread) pageZero else (pageZero / 2) * 2
                                val rightZero = if (isBilingualSpread) pageZero else leftZero + 1
                                RenderSpreadPageForNonFlipbook(leftZero, rightZero)
                            } else if (isPdfBook) {
                                val isTransPage = (isPageTranslationActive || uiState.isTranslationActive) && !uiState.translationMode.equals("original", ignoreCase = true)
                                val effPdfBmp = if (isTransPage && !peekOriginalPdfPage) {
                                    viewModel?.getCachedTranslatedPdfBitmap(book.id, pageZero, uiState.translationTargetLang, uiState.translationMode)
                                } else if (peekOriginalPdfPage) {
                                    viewModel?.getCachedPdfBitmap(book.id, pageZero, book.localFilePath)
                                        ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath ?: "", pageZero, 1080)
                                } else null
                                if (viewModel != null) {
                                    val shouldTintPdfPage = isPdfBook && prefs.pdfThemeTintEnabled && effectiveTheme != AppThemeMode.WHITE &&
                                        !(prefs.pdfProtectIllustrations && PdfNativeRenderer.isPdfPageProtectedIllustration(
                                            pageZero, indexedIllustrationPages
                                        ))
                                    PdfPageItemContent(
                                        bookId = book.id,
                                        filePath = book.localFilePath!!,
                                        pageIndex = pageZero,
                                        viewModel = viewModel,
                                        uiState = uiState,
                                        modifier = Modifier.fillMaxSize(),
                                        colorFilter = if (shouldTintPdfPage) pdfThemeColorFilter else null,
                                        surfaceColor = if (shouldTintPdfPage) palette.bgCard else Color.White,
                                        overrideBitmap = if (peekOriginalPdfPage) effPdfBmp else null
                                    )
                                } else {
                                    val displayPdfBmp = effPdfBmp
                                        ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath ?: "", pageZero, 1080)
                                        ?: PdfNativeRenderer.getCachedBitmap(book.localFilePath ?: "", pageZero, ((stageW / 120) * 120).coerceAtLeast(360))
                                    val pdfRatio = if (displayPdfBmp != null && displayPdfBmp.height > 0) {
                                        displayPdfBmp.width.toFloat() / displayPdfBmp.height.toFloat()
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
                                        if (displayPdfBmp != null) {
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color.White,
                                                shadowElevation = 8.dp,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(pdfRatio, matchHeightConstraintsFirst = false)
                                            ) {
                                                Image(
                                                    bitmap = displayPdfBmp.asImageBitmap(),
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
                                val curCachedTrans = getEffectivePageTranslationPairs(pageZero, pText)
                                InteractiveTextPageContent(
                                    bookId = book.id,
                                    displayPageIndex = pageZero,
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
                                    fontScale = 1f,
                                    transparentTextForFlipbook = false,
                                    topClearanceDp = topClearanceDp,
                                    bottomClearanceDp = bottomClearanceDp,
                                    clearSelectionTrigger = clearSelectionTrigger,
                                    onSelectionActiveChanged = { active, start, end ->
                                        hasSelectionActive = active
                                        selStartHandleAnchor = start
                                        selEndHandleAnchor = end
                                    },
                                    onCenterTap = {
                                        if (hasSelectionActive || selectedQuoteForPopover != null) {
                                            hasSelectionActive = false
                                            clearSelectionTrigger++
                                            selectedQuoteForPopover = null
                                        } else {
                                            showReaderChrome = !showReaderChrome
                                        }
                                    },
                                    isPageTranslationActive = isPageTranslationActive,
                                    translatedParagraphs = curCachedTrans,
                                    viewModel = viewModel,
                                    uiState = uiState
                                )
                            }
                        }

                        val nonFlipbookZoomGestureModifier = Modifier.pointerInput(
                            prefs.pageAnimStyle, prefs.readingFlowMode,
                            uiState.showReaderTypeSheet, uiState.showReaderTocDrawer, uiState.showReaderNotesDrawer,
                            showPageScrubber, showInlinePageSlider, hasSelectionActive
                        ) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                if (uiState.showReaderTypeSheet || uiState.showReaderTocDrawer || uiState.showReaderNotesDrawer ||
                                    showPageScrubber || showInlinePageSlider ||
                                    down.position.y >= size.height - 96.dp.toPx() ||
                                    down.position.y <= 88.dp.toPx()
                                ) {
                                    return@awaitEachGesture
                                }
                                val touchSlop = viewConfiguration.touchSlop
                                var movedBeyondSlop = false
                                var hadMultiTouch = false
                                var lastEventUptime = down.uptimeMillis
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    event.changes.firstOrNull()?.let { lastEventUptime = it.uptimeMillis }
                                    val pressedCount = event.changes.count { it.pressed }
                                    if (pressedCount == 0) {
                                        if (nonFlipZoomScale < 1.03f) {
                                            nonFlipZoomScale = 1f
                                            nonFlipZoomOffset = Offset.Zero
                                        }
                                        val elapsed = lastEventUptime - down.uptimeMillis
                                        val childHandlesTap = !isPdfBook && !isSpread && !isBilingualMode
                                        if (!movedBeyondSlop && !hadMultiTouch && nonFlipZoomScale <= 1.02f &&
                                            elapsed < 340L && (!childHandlesTap || selectedQuoteForPopover != null)
                                        ) {
                                            val relX = down.position.x / size.width.toFloat().coerceAtLeast(1f)
                                            if (relX in 0.24f..0.76f) {
                                                if (hasSelectionActive || selectedQuoteForPopover != null) {
                                                    hasSelectionActive = false
                                                    clearSelectionTrigger++
                                                    selectedQuoteForPopover = null
                                                } else {
                                                    showReaderChrome = !showReaderChrome
                                                }
                                            }
                                        }
                                        break
                                    }
                                    if (pressedCount >= 2) {
                                        hadMultiTouch = true
                                        val zoomChange = event.calculateZoom()
                                        val panChange = event.calculatePan()
                                        val newScale = (nonFlipZoomScale * zoomChange).coerceIn(1f, 4f)
                                        val maxPanX = (size.width.toFloat() * (newScale - 1f)) / 2f
                                        val maxPanY = (size.height.toFloat() * (newScale - 1f)) / 2f
                                        nonFlipZoomScale = newScale
                                        if (newScale > 1.01f) {
                                            nonFlipZoomOffset = Offset(
                                                x = (nonFlipZoomOffset.x + panChange.x * newScale).coerceIn(-maxPanX, maxPanX),
                                                y = (nonFlipZoomOffset.y + panChange.y * newScale).coerceIn(-maxPanY, maxPanY)
                                            )
                                        } else {
                                            nonFlipZoomOffset = Offset.Zero
                                        }
                                        event.changes.forEach { change ->
                                            if (change.positionChanged()) change.consume()
                                        }
                                    } else if (pressedCount == 1) {
                                        val curChange = event.changes.firstOrNull()
                                        if (curChange != null && (curChange.position - down.position).getDistance() > touchSlop) {
                                            movedBeyondSlop = true
                                        }
                                        if (nonFlipZoomScale > 1.02f) {
                                            val panChange = event.calculatePan()
                                            if (panChange != Offset.Zero) {
                                                val maxPanX = (size.width.toFloat() * (nonFlipZoomScale - 1f)) / 2f
                                                val maxPanY = (size.height.toFloat() * (nonFlipZoomScale - 1f)) / 2f
                                                nonFlipZoomOffset = Offset(
                                                    x = (nonFlipZoomOffset.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                                    y = (nonFlipZoomOffset.y + panChange.y).coerceIn(-maxPanY, maxPanY)
                                                )
                                                event.changes.forEach { change ->
                                                    if (change.positionChanged()) change.consume()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .then(nonFlipbookZoomGestureModifier)
                                .graphicsLayer {
                                    scaleX = nonFlipZoomScale
                                    scaleY = nonFlipZoomScale
                                    translationX = if (nonFlipZoomScale > 1.01f) nonFlipZoomOffset.x else 0f
                                    translationY = if (nonFlipZoomScale > 1.01f) nonFlipZoomOffset.y else 0f
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (prefs.animatePageTurn && prefs.pageAnimStyle == PageAnimStyle.SLIDE) {
                                if (isSpread) {
                                    val totalSpreads = if (isBilingualSpread) {
                                        metrics.totalBookPages.coerceAtLeast(1)
                                    } else {
                                        ((metrics.totalBookPages + 1) / 2).coerceAtLeast(1)
                                    }
                                    val initialSpread = if (isBilingualSpread) {
                                        displayPageIndex.coerceIn(0, (totalSpreads - 1).coerceAtLeast(0))
                                    } else {
                                        (displayPageIndex / 2).coerceIn(0, (totalSpreads - 1).coerceAtLeast(0))
                                    }
                                    val spreadPagerState = rememberPagerState(
                                        initialPage = initialSpread,
                                        pageCount = { totalSpreads }
                                    )
                                    LaunchedEffect(isBilingualSpread) {
                                        val desiredSpread = if (isBilingualSpread) {
                                            displayPageIndex.coerceIn(0, (totalSpreads - 1).coerceAtLeast(0))
                                        } else {
                                            (displayPageIndex / 2).coerceIn(0, (totalSpreads - 1).coerceAtLeast(0))
                                        }
                                        if (spreadPagerState.currentPage != desiredSpread) {
                                            spreadPagerState.scrollToPage(desiredSpread)
                                        }
                                    }
                                    LaunchedEffect(spreadPagerState, isBilingualSpread) {
                                        snapshotFlow { spreadPagerState.settledPage }
                                            .distinctUntilChanged()
                                            .collect { settledSpread ->
                                                val targetPage = if (isBilingualSpread) settledSpread else settledSpread * 2
                                                val currentSpread = if (isBilingualSpread) displayPageIndex else displayPageIndex / 2
                                                if (settledSpread != currentSpread) {
                                                    displayPageIndex = targetPage
                                                    val target = getPageAtGlobalZero(targetPage)
                                                    if (target != null) {
                                                        onJumpToChapterAndPage(target.first, target.second)
                                                    }
                                                }
                                            }
                                    }
                                    LaunchedEffect(curZeroFromMetrics, isBilingualSpread) {
                                        val targetSpread = if (isBilingualSpread) curZeroFromMetrics else curZeroFromMetrics / 2
                                        if (!spreadPagerState.isScrollInProgress &&
                                            spreadPagerState.settledPage != targetSpread &&
                                            spreadPagerState.currentPage != targetSpread &&
                                            spreadPagerState.targetPage != targetSpread
                                        ) {
                                            displayPageIndex = curZeroFromMetrics
                                            spreadPagerState.animateScrollToPage(
                                                targetSpread.coerceIn(0, (totalSpreads - 1).coerceAtLeast(0)),
                                                animationSpec = spring(dampingRatio = 0.88f, stiffness = 380f)
                                            )
                                        }
                                    }
                                    HorizontalPager(
                                        state = spreadPagerState,
                                        beyondViewportPageCount = 2,
                                        key = { spreadIndex -> "slide_spread_${isBilingualSpread}_$spreadIndex" },
                                        userScrollEnabled = (nonFlipZoomScale <= 1.02f),
                                        modifier = Modifier.fillMaxSize()
                                    ) { spreadIndex ->
                                        val spreadLeftPage = if (isBilingualSpread) spreadIndex else spreadIndex * 2
                                        val spreadRightPage = if (isBilingualSpread) spreadIndex else spreadIndex * 2 + 1
                                        RenderSpreadPageForNonFlipbook(spreadLeftPage, spreadRightPage)
                                    }
                                } else {
                                    val pagerState = rememberPagerState(
                                        initialPage = displayPageIndex.coerceIn(0, (metrics.totalBookPages - 1).coerceAtLeast(0)),
                                        pageCount = { metrics.totalBookPages.coerceAtLeast(1) }
                                    )
                                    LaunchedEffect(pagerState) {
                                        snapshotFlow { pagerState.settledPage }
                                            .distinctUntilChanged()
                                            .collect { settled ->
                                                if (settled != displayPageIndex) {
                                                    displayPageIndex = settled
                                                    val target = getPageAtGlobalZero(settled)
                                                    if (target != null) {
                                                        onJumpToChapterAndPage(target.first, target.second)
                                                    }
                                                }
                                            }
                                    }
                                    LaunchedEffect(curZeroFromMetrics) {
                                        if (!pagerState.isScrollInProgress &&
                                            pagerState.settledPage != curZeroFromMetrics &&
                                            pagerState.currentPage != curZeroFromMetrics &&
                                            pagerState.targetPage != curZeroFromMetrics
                                        ) {
                                            displayPageIndex = curZeroFromMetrics
                                            pagerState.animateScrollToPage(
                                                curZeroFromMetrics,
                                                animationSpec = spring(dampingRatio = 0.88f, stiffness = 380f)
                                            )
                                        }
                                    }
                                    HorizontalPager(
                                        state = pagerState,
                                        beyondViewportPageCount = 2,
                                        key = { pIdx -> "slide_page_$pIdx" },
                                        userScrollEnabled = (nonFlipZoomScale <= 1.02f),
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
                                            if (nonFlipZoomScale > 1.02f) return@detectHorizontalDragGestures
                                            if (abs(totalDx) > 42.dp.toPx() && abs(totalDx) > abs(totalDy) * 1.15f) {
                                                if (totalDx < 0f && curZero < metrics.totalBookPages - 1) onTurnPage(1)
                                                else if (totalDx > 0f && curZero > 0) onTurnPage(-1)
                                            }
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            if (nonFlipZoomScale > 1.02f) return@detectHorizontalDragGestures
                                            totalDx += dragAmount
                                            if (abs(totalDx) > 16.dp.toPx()) change.consume()
                                        }
                                    )
                                }
                                AnimatedContent(
                                    targetState = curZero,
                                    transitionSpec = {
                                        fadeIn(
                                            animationSpec = tween(
                                                durationMillis = 150,
                                                delayMillis = 95,
                                                easing = LinearOutSlowInEasing
                                            )
                                        ).togetherWith(
                                            fadeOut(
                                                animationSpec = tween(
                                                    durationMillis = 110,
                                                    delayMillis = 0,
                                                    easing = FastOutLinearInEasing
                                                )
                                            )
                                        ).using(
                                            SizeTransform(clip = false)
                                        )
                                    },
                                    label = "readerFadeTransition",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(palette.bgApp)
                                        .then(fadeSwipeModifier)
                                ) { pIdx ->
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(palette.bgApp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        RenderPageForNonFlipbook(pIdx)
                                    }
                                }
                            } else {
                                val noAnimSwipeModifier = Modifier.pointerInput(curZero, metrics.totalBookPages) {
                                    var totalDx = 0f
                                    var totalDy = 0f
                                    detectHorizontalDragGestures(
                                        onDragStart = { totalDx = 0f; totalDy = 0f },
                                        onDragEnd = {
                                            if (nonFlipZoomScale > 1.02f) return@detectHorizontalDragGestures
                                            if (abs(totalDx) > 42.dp.toPx() && abs(totalDx) > abs(totalDy) * 1.15f) {
                                                if (totalDx < 0f && curZero < metrics.totalBookPages - 1) onTurnPage(1)
                                                else if (totalDx > 0f && curZero > 0) onTurnPage(-1)
                                            }
                                        },
                                        onHorizontalDrag = { change, dragAmount ->
                                            if (nonFlipZoomScale > 1.02f) return@detectHorizontalDragGestures
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
                }

                // 3. VERTICAL CONTINUOUS SCROLL & WEBTOON MODES (Fixed for both PDF and EPUB/TXT!)
                ReadingFlowMode.SCROLL, ReadingFlowMode.WEBTOON -> {
                    val isWebtoon = prefs.readingFlowMode == ReadingFlowMode.WEBTOON

                    val (scrollItemMetas, pageToLazyIndexMap, chapterGlobalStartOffsets) = remember(chapters, isPdfBook, isWebtoon, metrics.totalBookPages) {
                        if (isPdfBook) {
                            val totalPages = metrics.totalBookPages.coerceAtLeast(1)
                            val metas = List(totalPages) { pIdx ->
                                val triple = getPageAtGlobalZero(pIdx)
                                ScrollLazyItemMeta(
                                    globalPageZero = pIdx,
                                    chapterIndex = triple?.first ?: 0,
                                    pageInChapter = triple?.second ?: pIdx,
                                    isPageItem = true
                                )
                            }
                            val idxMap = (0 until totalPages).associateWith { it }
                            Triple(metas, idxMap, emptyList<Int>())
                        } else {
                            val metas = mutableListOf<ScrollLazyItemMeta>()
                            val idxMap = mutableMapOf<Int, Int>()
                            val chStarts = mutableListOf<Int>()
                            var runningGlobalZero = 0
                            chapters.forEachIndexed { chIdx, ch ->
                                val chStart = runningGlobalZero
                                chStarts.add(chStart)
                                metas.add(
                                    ScrollLazyItemMeta(
                                        globalPageZero = chStart,
                                        chapterIndex = chIdx,
                                        pageInChapter = 0,
                                        isPageItem = false
                                    )
                                )
                                ch.pages.forEachIndexed { pIdx, _ ->
                                    val lazyIdx = metas.size
                                    idxMap[runningGlobalZero] = if (pIdx == 0) (lazyIdx - 1).coerceAtLeast(0) else lazyIdx
                                    metas.add(
                                        ScrollLazyItemMeta(
                                            globalPageZero = runningGlobalZero,
                                            chapterIndex = chIdx,
                                            pageInChapter = pIdx,
                                            isPageItem = true
                                        )
                                    )
                                    runningGlobalZero++
                                }
                                if (!isWebtoon) {
                                    metas.add(
                                        ScrollLazyItemMeta(
                                            globalPageZero = (runningGlobalZero - 1).coerceAtLeast(0),
                                            chapterIndex = chIdx,
                                            pageInChapter = ch.pages.lastIndex.coerceAtLeast(0),
                                            isPageItem = false
                                        )
                                    )
                                }
                            }
                            Triple(metas.toList(), idxMap.toMap(), chStarts.toList())
                        }
                    }

                    val initialPageZero = (metrics.currentGlobalPage - 1).coerceAtLeast(0)
                    val initialLazyIdx = if (isPdfBook) {
                        initialPageZero
                    } else {
                        pageToLazyIndexMap[initialPageZero] ?: 0
                    }
                    val scrollListState = rememberLazyListState(initialFirstVisibleItemIndex = initialLazyIdx)

                    // Observe scroll position and persist reading progress
                    LaunchedEffect(scrollListState, scrollItemMetas) {
                        snapshotFlow {
                            val effectiveLazyIdx = if (!scrollListState.canScrollForward) {
                                scrollListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                                    ?: scrollListState.firstVisibleItemIndex
                            } else {
                                scrollListState.firstVisibleItemIndex
                            }
                            effectiveLazyIdx to scrollListState.isScrollInProgress
                        }
                            .distinctUntilChanged()
                            .collect { (effectiveIdx, isScrolling) ->
                                val meta = scrollItemMetas.getOrNull(effectiveIdx) ?: return@collect
                                val targetGlobalZero = meta.globalPageZero.coerceIn(0, (metrics.totalBookPages - 1).coerceAtLeast(0))
                                if (targetGlobalZero != displayPageIndex) {
                                    displayPageIndex = targetGlobalZero
                                    scrubberPageValue = (targetGlobalZero + 1).toFloat()
                                    onJumpToChapterAndPage(meta.chapterIndex, meta.pageInChapter)
                                } else if (!isScrolling && targetGlobalZero != (metrics.currentGlobalPage - 1).coerceAtLeast(0)) {
                                    onJumpToChapterAndPage(meta.chapterIndex, meta.pageInChapter)
                                }
                            }
                    }

                    // Respond to external jumps (e.g. ToC, bookmarks, search results, page scrubber)
                    LaunchedEffect(metrics.currentGlobalPage, isPdfBook, isWebtoon, scrollItemMetas) {
                        val targetGlobalZero = (metrics.currentGlobalPage - 1).coerceAtLeast(0)
                        val currentEffectiveLazyIdx = if (!scrollListState.canScrollForward) {
                            scrollListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                                ?: scrollListState.firstVisibleItemIndex
                        } else {
                            scrollListState.firstVisibleItemIndex
                        }
                        val currentVisiblePageZero = scrollItemMetas.getOrNull(currentEffectiveLazyIdx)?.globalPageZero
                        if (!scrollListState.isScrollInProgress && targetGlobalZero != currentVisiblePageZero) {
                            displayPageIndex = targetGlobalZero
                            val targetLazyIdx = if (isPdfBook) {
                                targetGlobalZero.coerceIn(0, (metrics.totalBookPages - 1).coerceAtLeast(0))
                            } else {
                                pageToLazyIndexMap[targetGlobalZero] ?: 0
                            }
                            scrollListState.scrollToItem(targetLazyIdx)
                        }
                    }

                    val scrollZoomGestureModifier = Modifier.pointerInput(
                        prefs.readingFlowMode,
                        uiState.showReaderTypeSheet, uiState.showReaderTocDrawer, uiState.showReaderNotesDrawer,
                        showPageScrubber, hasSelectionActive
                    ) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            if (uiState.showReaderTypeSheet || uiState.showReaderTocDrawer || uiState.showReaderNotesDrawer ||
                                showPageScrubber ||
                                down.position.y >= size.height - 96.dp.toPx() ||
                                down.position.y <= 88.dp.toPx()
                            ) {
                                return@awaitEachGesture
                            }
                            val touchSlop = viewConfiguration.touchSlop
                            var movedBeyondSlop = false
                            var hadMultiTouch = false
                            var lastEventUptime = down.uptimeMillis
                            val isBilingualScroll = (isPageTranslationActive || uiState.isTranslationActive) &&
                                uiState.translationMode.equals("bilingual", ignoreCase = true)
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                event.changes.firstOrNull()?.let { lastEventUptime = it.uptimeMillis }
                                val pressedCount = event.changes.count { it.pressed }
                                if (pressedCount == 0) {
                                    if (nonFlipZoomScale < 1.03f) {
                                        nonFlipZoomScale = 1f
                                        nonFlipZoomOffset = Offset.Zero
                                    }
                                    val elapsed = lastEventUptime - down.uptimeMillis
                                    val childHandlesTap = !isPdfBook && !isBilingualScroll
                                    if (!movedBeyondSlop && !hadMultiTouch && nonFlipZoomScale <= 1.02f &&
                                        elapsed < 340L && (!childHandlesTap || selectedQuoteForPopover != null)
                                    ) {
                                        val relX = down.position.x / size.width.toFloat().coerceAtLeast(1f)
                                        if (relX in 0.24f..0.76f) {
                                            if (hasSelectionActive || selectedQuoteForPopover != null) {
                                                hasSelectionActive = false
                                                clearSelectionTrigger++
                                                selectedQuoteForPopover = null
                                            } else {
                                                showReaderChrome = !showReaderChrome
                                            }
                                        }
                                    }
                                    break
                                }
                                if (pressedCount >= 2) {
                                    hadMultiTouch = true
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()
                                    val newScale = (nonFlipZoomScale * zoomChange).coerceIn(1f, 4f)
                                    val maxPanX = (size.width.toFloat() * (newScale - 1f)) / 2f
                                    val maxPanY = (size.height.toFloat() * (newScale - 1f)) / 2f
                                    nonFlipZoomScale = newScale
                                    if (newScale > 1.01f) {
                                        nonFlipZoomOffset = Offset(
                                            x = (nonFlipZoomOffset.x + panChange.x * newScale).coerceIn(-maxPanX, maxPanX),
                                            y = (nonFlipZoomOffset.y + panChange.y * newScale).coerceIn(-maxPanY, maxPanY)
                                        )
                                    } else {
                                        nonFlipZoomOffset = Offset.Zero
                                    }
                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) change.consume()
                                    }
                                } else if (pressedCount == 1) {
                                    val curChange = event.changes.firstOrNull()
                                    if (curChange != null && (curChange.position - down.position).getDistance() > touchSlop) {
                                        movedBeyondSlop = true
                                    }
                                    if (nonFlipZoomScale > 1.02f) {
                                        val panChange = event.calculatePan()
                                        if (panChange != Offset.Zero) {
                                            val maxPanX = (size.width.toFloat() * (nonFlipZoomScale - 1f)) / 2f
                                            val maxPanY = (size.height.toFloat() * (nonFlipZoomScale - 1f)) / 2f
                                            nonFlipZoomOffset = Offset(
                                                x = (nonFlipZoomOffset.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                                y = (nonFlipZoomOffset.y + panChange.y).coerceIn(-maxPanY, maxPanY)
                                            )
                                            event.changes.forEach { change ->
                                                if (change.positionChanged()) change.consume()
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    val scrollTopInsetDp = if (showReaderChrome || isTranslationQuickBarActive) {
                        topClearanceDp
                    } else {
                        stableStatusBarTopDp
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = scrollTopInsetDp)
                            .clipToBounds()
                            .then(scrollZoomGestureModifier)
                            .graphicsLayer {
                                scaleX = nonFlipZoomScale
                                scaleY = nonFlipZoomScale
                                translationX = if (nonFlipZoomScale > 1.01f) nonFlipZoomOffset.x else 0f
                                translationY = if (nonFlipZoomScale > 1.01f) nonFlipZoomOffset.y else 0f
                            }
                    ) {
                        if (isPdfBook) {
                            val totalPdfPages = metrics.totalBookPages.coerceAtLeast(1)
                            val pdfPath = book.localFilePath!!
                            LazyColumn(
                                state = scrollListState,
                                userScrollEnabled = (nonFlipZoomScale <= 1.02f),
                                contentPadding = PaddingValues(bottom = bottomClearanceDp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = if (isWebtoon) 0.dp else 12.dp),
                                verticalArrangement = Arrangement.spacedBy(if (isWebtoon) 0.dp else 12.dp)
                            ) {
                                items(
                                    count = totalPdfPages,
                                    key = { pageIdx -> "pdf_vertical_page_$pageIdx" }
                                ) { pageIdx ->
                                    val shouldTintPdfPage = isPdfBook && prefs.pdfThemeTintEnabled && effectiveTheme != AppThemeMode.WHITE &&
                                        !(prefs.pdfProtectIllustrations && PdfNativeRenderer.isPdfPageProtectedIllustration(
                                            pageIdx, indexedIllustrationPages
                                        ))
                                    val vertWidthBucket = ((stageW.coerceIn(360, 1280) / 120) * 120).coerceAtLeast(360)
                                    val vertOrigBmp = PdfNativeRenderer.getCachedBitmap(pdfPath, pageIdx, vertWidthBucket)
                                    val vertEffBmp = getEffectivePdfPageBitmap(pageIdx, vertOrigBmp)
                                    PdfVerticalPageItem(
                                        pdfFilePath = pdfPath,
                                        pageIndex = pageIdx,
                                        targetWidthPx = stageW,
                                        isWebtoon = isWebtoon,
                                        colorFilter = if (shouldTintPdfPage) pdfThemeColorFilter else null,
                                        surfaceColor = if (shouldTintPdfPage) palette.bgCard else Color.White,
                                        overrideBitmap = vertEffBmp
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                state = scrollListState,
                                userScrollEnabled = (nonFlipZoomScale <= 1.02f),
                                contentPadding = PaddingValues(bottom = bottomClearanceDp),
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
                                    ) { pIdx, pgRaw ->
                                        val globalPageZero = chapterGlobalStartOffsets.getOrElse(chIdx) { 0 } + pIdx
                                        val curCachedTrans = viewModel?.getCachedTranslatedParagraphs(
                                            book.id,
                                            globalPageZero,
                                            uiState.translationTargetLang,
                                            pgRaw.hashCode()
                                        ) ?: pageTranslationMap[pageTransMapKey(globalPageZero)]
                                        InteractiveTextPageContent(
                                            bookId = book.id,
                                            displayPageIndex = globalPageZero,
                                            chapterTitle = ch.title,
                                            pageText = pgRaw,
                                            currentChapterIndex = chIdx,
                                            currentPageInChapter = pIdx,
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
                                            isCoverPage = (globalPageZero == 0),
                                            bookTitle = book.title,
                                            clearSelectionTrigger = clearSelectionTrigger,
                                            onSelectionActiveChanged = { active, start, end ->
                                                hasSelectionActive = active
                                                selStartHandleAnchor = start
                                                selEndHandleAnchor = end
                                            },
                                            onCenterTap = {
                                                if (hasSelectionActive || selectedQuoteForPopover != null) {
                                                    hasSelectionActive = false
                                                    clearSelectionTrigger++
                                                    selectedQuoteForPopover = null
                                                } else {
                                                    showReaderChrome = !showReaderChrome
                                                }
                                            },
                                            isPageTranslationActive = isPageTranslationActive,
                                            translatedParagraphs = curCachedTrans,
                                            viewModel = viewModel,
                                            uiState = uiState
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
        }

        // 2. OVERLAY TOP CHROME BAR (`#readerTopChrome` in `llunevie_prototype.html`)
        ReaderTopChrome(
            book = book,
            uiState = uiState,
            prefs = prefs,
            bookBookmarks = bookBookmarks,
            strings = strings,
            chapters = chapters,
            currentChapter = currentChapter,
            currentPageRaw = currentPageRaw,
            displayPageIndex = displayPageIndex,
            isSpread = isSpread,
            isPdfBook = isPdfBook,
            showReaderChrome = showReaderChrome,
            showLangDropdown = showLangDropdown,
            onShowLangDropdownChange = { showLangDropdown = it },
            isPageTranslationActive = isPageTranslationActive,
            onIsPageTranslationActiveChange = { isPageTranslationActive = it },
            peekOriginalPdfPage = peekOriginalPdfPage,
            onPeekOriginalPdfPageChange = { peekOriginalPdfPage = it },
            isTranslatingPage = isTranslatingPage,
            readerZoom = readerZoom,
            readerPan = readerPan,
            nonFlipZoomScale = nonFlipZoomScale,
            nonFlipZoomOffset = nonFlipZoomOffset,
            onResetZoom = {
                val isFlipbookMode = prefs.animatePageTurn && prefs.pageAnimStyle == PageAnimStyle.FLIPBOOK &&
                    (prefs.readingFlowMode == ReadingFlowMode.PAGINATED || prefs.readingFlowMode == ReadingFlowMode.SPREAD)
                val startZ = if (isFlipbookMode) readerZoom else nonFlipZoomScale
                val startP = if (isFlipbookMode) readerPan else nonFlipZoomOffset
                scope.launch {
                    Animatable(0f).animateTo(
                        targetValue = 1f,
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    ) {
                        val f = 1f - value
                        if (isFlipbookMode) {
                            readerZoom = 1f + (startZ - 1f) * f
                            readerPan = Offset(startP.x * f, startP.y * f)
                        } else {
                            nonFlipZoomScale = 1f + (startZ - 1f) * f
                            nonFlipZoomOffset = Offset(startP.x * f, startP.y * f)
                        }
                    }
                    readerZoom = 1f
                    readerPan = Offset.Zero
                    nonFlipZoomScale = 1f
                    nonFlipZoomOffset = Offset.Zero
                }
            },
            getPageAtGlobalZero = getPageAtGlobalZero,
            onBack = onBack,
            onToggleTocDrawer = onToggleTocDrawer,
            viewModel = viewModel
        )

        // 3. BOTTOM FLOATING CHROME BAR & SPREAD VERTICAL DOCK
        ReaderBottomChrome(
            uiState = uiState,
            prefs = prefs,
            strings = strings,
            metrics = metrics,
            chaptersCount = chapters.size,
            showReaderChrome = showReaderChrome,
            isTwoPageSpread = isTwoPageSpread,
            showPageScrubber = showPageScrubber,
            onShowPageScrubberChange = { showPageScrubber = it },
            scrubberPageValue = scrubberPageValue,
            onScrubberPageValueChange = { scrubberPageValue = it },
            onExternalFlipTrigger = { externalFlipTrigger = it },
            getPageAtGlobalZero = getPageAtGlobalZero,
            onTurnPage = onTurnPage,
            onJumpToChapterAndPage = onJumpToChapterAndPage,
            onToggleTocDrawer = onToggleTocDrawer,
            onToggleTypeSheet = onToggleTypeSheet
        )

        ReaderSpreadLeftVerticalBar(
            uiState = uiState,
            prefs = prefs,
            metrics = metrics,
            isSpread = isSpread,
            displayPageIndex = displayPageIndex,
            onDisplayPageIndexChange = { displayPageIndex = it },
            showInlinePageSlider = showInlinePageSlider,
            onShowInlinePageSliderChange = { showInlinePageSlider = it },
            onExternalFlipTrigger = { externalFlipTrigger = it },
            onTurnPage = onTurnPage,
            onToggleTocDrawer = onToggleTocDrawer,
            onToggleTypeSheet = onToggleTypeSheet,
            viewModel = viewModel
        )

        // Middle-Right Quick Notes Drawer Handle Tab
        Surface(
            onClick = { onToggleNotesDrawer(true) },
            shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp, topEnd = 0.dp, bottomEnd = 0.dp),
            color = palette.bgSurface.copy(alpha = 0.88f),
            border = BorderStroke(1.dp, palette.borderSoft),
            shadowElevation = 3.dp,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .minimumInteractiveComponentSize()
                .width(20.dp)
                .height(48.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "‹",
                    color = palette.textMain.copy(alpha = 0.8f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 2.dp)
                )
            }
        }

        // HIGHLIGHT & NOTE POPOVER (`#highlightPopover`)
        ReaderHighlightNotePopover(
            selectedQuoteForPopover = selectedQuoteForPopover,
            selectedPopoverInUpperHalf = selectedPopoverInUpperHalf,
            onDismissQuotePopover = {
                selectedQuoteForPopover = null
                showInlineNoteInput = false
                showInlineTranslation = false
                inlineTranslationText = null
                inlineTranslationError = null
            },
            showInlineNoteInput = showInlineNoteInput,
            onShowInlineNoteInputChange = { showInlineNoteInput = it },
            inlineNoteText = inlineNoteText,
            onInlineNoteTextChange = { inlineNoteText = it },
            showInlineTranslation = showInlineTranslation,
            onShowInlineTranslationChange = { showInlineTranslation = it },
            inlineTranslationText = inlineTranslationText,
            onInlineTranslationTextChange = { inlineTranslationText = it },
            isTranslatingInline = isTranslatingInline,
            onIsTranslatingInlineChange = { isTranslatingInline = it },
            inlineTranslationError = inlineTranslationError,
            onInlineTranslationErrorChange = { inlineTranslationError = it },
            bookHighlights = bookHighlights,
            prefs = prefs,
            strings = strings,
            translationRepo = translationRepo,
            onSaveHighlightNote = onSaveHighlightNote,
            onDeleteHighlightNote = onDeleteHighlightNote,
            onToggleNotesDrawer = onToggleNotesDrawer,
            onOpenInBookSearch = { inBookSearchQuery = it },
            viewModel = viewModel
        )

        // IN-BOOK SEARCH OVERLAY (`#inBookSearchDrawer`)
        ReaderInBookSearchOverlay(
            inBookSearchQuery = inBookSearchQuery,
            onDismissSearch = { inBookSearchQuery = null },
            chapters = chapters,
            strings = strings,
            onJumpToChapterAndPage = onJumpToChapterAndPage
        )

        // 4. IN-HIERARCHY READER TYPOGRAPHY & FLOW SHEET (`#readerTypeSheet` in `llunevie_prototype.html`)
        ReaderTypographySheet(
            uiState = uiState,
            prefs = prefs,
            strings = strings,
            isPdfBook = isPdfBook,
            activeDraggingSliderKey = activeDraggingSliderKey,
            onActiveDraggingSliderKeyChanged = { activeDraggingSliderKey = it },
            onToggleTypeSheet = onToggleTypeSheet,
            onSetTheme = onSetTheme,
            onSetReadingFlowMode = onSetReadingFlowMode,
            onSetPageAnimStyle = onSetPageAnimStyle,
            onUpdateTypography = onUpdateTypography,
            viewModel = viewModel
        )

        // 5. LEFT SIDEBAR TOC & ILLUSTRATIONS DRAWER (`#readerTocDrawer` with Footer Progress Bar!)
        ReaderTocDrawer(
            book = book,
            uiState = uiState,
            strings = strings,
            metrics = metrics,
            chapters = chapters,
            isSpread = isSpread,
            isPdfBook = isPdfBook,
            getPageAtGlobalZero = getPageAtGlobalZero,
            onJumpToChapterAndPage = onJumpToChapterAndPage,
            onToggleTocDrawer = onToggleTocDrawer,
            onOpenIllustViewer = onOpenIllustViewer,
            viewModel = viewModel
        )

        // 6. RIGHT SAVED NOTES & HIGHLIGHTS DRAWER (`#readerNotesDrawer`)
        ReaderNotesDrawer(
            book = book,
            uiState = uiState,
            strings = strings,
            metrics = metrics,
            bookHighlights = bookHighlights,
            bookBookmarks = bookBookmarks,
            isSpread = isSpread,
            onDisplayPageIndexChanged = { displayPageIndex = it },
            onJumpToChapterAndPage = onJumpToChapterAndPage,
            onToggleNotesDrawer = onToggleNotesDrawer,
            onSaveHighlightNote = onSaveHighlightNote,
            onDeleteHighlightNote = onDeleteHighlightNote,
            viewModel = viewModel
        )

        // 7. OPAQUE ILLUSTRATION VIEWER MODAL (`#illustViewerModal`)
        ReaderIllustrationViewer(
            book = book,
            uiState = uiState,
            strings = strings,
            isPdfBook = isPdfBook,
            onOpenIllustViewer = onOpenIllustViewer,
            onCloseIllustViewer = onCloseIllustViewer
        )
    }
}
