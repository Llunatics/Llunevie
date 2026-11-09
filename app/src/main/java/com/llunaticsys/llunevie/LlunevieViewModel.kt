package com.llunaticsys.llunevie

import android.content.Context
import android.graphics.Bitmap
import android.util.LruCache
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.llunaticsys.llunevie.data.local.BookChapterEntity
import com.llunaticsys.llunevie.data.local.BookEntity
import com.llunaticsys.llunevie.data.local.DailyReadingLogEntity
import com.llunaticsys.llunevie.data.local.HighlightNoteEntity
import com.llunaticsys.llunevie.data.local.PageBookmarkEntity
import com.llunaticsys.llunevie.data.local.LlunevieDao
import com.llunaticsys.llunevie.data.local.ShelfEntity
import com.llunaticsys.llunevie.data.local.UserPreferencesRepository
import com.llunaticsys.llunevie.data.local.UserPreferencesState
import com.llunaticsys.llunevie.data.parser.EpubNativeParser
import com.llunaticsys.llunevie.data.parser.PdfNativeRenderer
import com.llunaticsys.llunevie.data.parser.SpineColorExtractor
import com.llunaticsys.llunevie.data.parser.TxtNativeParser
import com.llunaticsys.llunevie.data.remote.MultiBookApiRepository
import com.llunaticsys.llunevie.data.remote.TranslationRepository
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Environment
import androidx.documentfile.provider.DocumentFile
import com.llunaticsys.llunevie.model.AppThemeMode
import com.llunaticsys.llunevie.model.BatchLookupStatus
import com.llunaticsys.llunevie.model.BookChapter
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.HttpURLConnection
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import android.graphics.BitmapFactory
import com.llunaticsys.llunevie.model.BookIllustration
import com.llunaticsys.llunevie.model.CatalogBookCandidate
import com.llunaticsys.llunevie.model.HighlightColorKey
import com.llunaticsys.llunevie.model.LibrarySortOption
import com.llunaticsys.llunevie.model.LibraryViewMode
import com.llunaticsys.llunevie.model.PageAnimStyle
import com.llunaticsys.llunevie.model.ReadingFlowMode
import com.llunaticsys.llunevie.ui.components.normalizeBookTitle
import com.llunaticsys.llunevie.ui.components.sanitizeBookOrChapterText
import com.llunaticsys.llunevie.ui.theme.canonicalShelfKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MainNavTab {
    HOME, LIBRARY, STATS, SETTINGS, READER
}

data class GlobalPageMetrics(
    val currentGlobalPage: Int,
    val totalBookPages: Int,
    val globalPct: Int,
    val isAtVeryLastPage: Boolean
)

fun normalizeTranslationLangCode(rawLang: String): String =
    com.llunaticsys.llunevie.model.normalizeTranslationLangCode(rawLang)

fun isPageTranslationComplete(pairs: List<Pair<String, String?>>?, targetLang: String = ""): Boolean =
    com.llunaticsys.llunevie.model.isPageTranslationComplete(pairs, targetLang)

data class LlunevieUiState(
    val activeTab: MainNavTab = MainNavTab.HOME,
    val previousTab: MainNavTab = MainNavTab.HOME,
    val activeBookId: String? = null,
    val activeChapterIndex: Int = 0,
    val activePageIndex: Int = 0,
    val activeBookChapters: List<BookChapter> = emptyList(),
    val activeBookIllustrations: List<BookIllustration> = emptyList(),
    val readerFullView: Boolean = false,
    val showReaderTypeSheet: Boolean = false,
    val activeReaderSheetTab: String = "text", // "text", "page", "turn"
    val showReaderTocDrawer: Boolean = false,
    val showReaderNotesDrawer: Boolean = false,
    val showIllustViewer: Boolean = false,
    val activeIllustIndex: Int = 0,
    val illustZoom: Float = 1f,
    val libSearchQuery: String = "",
    val libSelectMode: Boolean = false,
    val libSelectedIds: Set<String> = emptySet(),
    val showLibFilterSheet: Boolean = false,
    val editingBook: BookEntity? = null,
    val showDeleteConfirmDialog: Boolean = false,
    val showAddBookSheet: Boolean = false,
    val addModalTab: String = "search", // "search", "isbn", "batch", "manual"
    val catalogSearchQuery: String = "",
    val catalogSearchResults: List<CatalogBookCandidate> = emptyList(),
    val isCatalogSearching: Boolean = false,
    val selectedCatalogBookDetail: CatalogBookCandidate? = null,
    val pendingCandidateForShelf: CatalogBookCandidate? = null,
    val showFullCameraScanner: Boolean = false,
    val cameraMode: String = "isbn", // "isbn" or "batch"
    val cameraTorchEnabled: Boolean = false,
    val lastScannedCandidate: CatalogBookCandidate? = null,
    val isLookingUpIsbn: Boolean = false,
    val batchScanQueue: List<CatalogBookCandidate> = emptyList(),
    val manualBookPrefillIsbn: String? = null,
    val pdfIndexingState: Map<String, Int?> = emptyMap(),
    val importingBooksProgress: Map<String, Int> = emptyMap(),
    val toastMessage: String? = null,
    val isInitialDataReady: Boolean = false,
    val isTranslationActive: Boolean = false,
    val translationMode: String = "replace",
    val translationTargetLang: String = "id",
    val isTranslatingPage: Boolean = false,
    val translationRevision: Int = 0
) {
    val showCenterSearchLoader: Boolean
        get() = isCatalogSearching && catalogSearchResults.isEmpty()

    val showTopSearchLoadingBar: Boolean
        get() = isCatalogSearching && catalogSearchResults.isNotEmpty()
}

class LlunevieViewModel(
    private val appContext: Context,
    private val dao: LlunevieDao,
    private val prefsRepo: UserPreferencesRepository,
    private val apiRepo: MultiBookApiRepository
) : ViewModel() {

    companion object {
        const val CURRENT_PDF_TOC_VERSION = 12
    }

    private val _previewOverrides = MutableStateFlow<UserPreferencesState?>(null)

    val preferences: StateFlow<UserPreferencesState> = combine(
        prefsRepo.preferencesFlow,
        _previewOverrides
    ) { diskPrefs, preview ->
        preview ?: diskPrefs
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = prefsRepo.getInitialBootState()
    )

    val books: StateFlow<List<BookEntity>> = dao.observeAllBooks().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val shelves: StateFlow<List<ShelfEntity>> = dao.observeShelves().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = listOf(
            ShelfEntity("eReader", 0, true),
            ShelfEntity("Koleksi Fisik", 1, true)
        )
    )

    val dailyLogs: StateFlow<List<DailyReadingLogEntity>> = dao.observeDailyLogs().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allHighlights: StateFlow<List<HighlightNoteEntity>> = dao.observeAllHighlights().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val allBookmarks: StateFlow<List<PageBookmarkEntity>> = dao.observeAllBookmarks().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val initialBootPrefs = prefsRepo.getInitialBootState()
    private val _uiState = MutableStateFlow(
        LlunevieUiState(
            translationTargetLang = normalizeTranslationLangCode(initialBootPrefs.translatorTargetLang),
            translationMode = initialBootPrefs.translatorDisplayMode.ifBlank { "replace" }
        )
    )
    val uiState: StateFlow<LlunevieUiState> = _uiState.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeBookHighlights: StateFlow<List<HighlightNoteEntity>> = _uiState
        .map { it.activeBookId }
        .distinctUntilChanged()
        .flatMapLatest { bookId ->
            if (bookId.isNullOrBlank()) flowOf(emptyList())
            else dao.observeHighlightsForBook(bookId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val activeBookBookmarks: StateFlow<List<PageBookmarkEntity>> = _uiState
        .map { it.activeBookId }
        .distinctUntilChanged()
        .flatMapLatest { bookId ->
            if (bookId.isNullOrBlank()) flowOf(emptyList())
            else dao.observeBookmarksForBook(bookId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val translatedTextCache = object : LruCache<String, List<Pair<String, String?>>>(100) {
        override fun sizeOf(key: String, value: List<Pair<String, String?>>): Int = 1
    }

    private var activeTranslationJob: Job? = null
    private val inFlightTextTranslations = ConcurrentHashMap<String, Deferred<List<Pair<String, String?>>?>>()
    private val inFlightPdfTranslations = ConcurrentHashMap<String, Deferred<Bitmap?>>()
    private val _pdfIndexingState = MutableStateFlow<Map<String, Int?>>(emptyMap())
    val pdfIndexingState: StateFlow<Map<String, Int?>> = _pdfIndexingState.asStateFlow()

    /** True when the app may use raw File paths on external storage (pre-R or MANAGE_EXTERNAL_STORAGE granted). */
    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    private fun resolvePdfFilePath(bookId: String, filePath: String? = null): String? {
        if (!filePath.isNullOrBlank()) return filePath
        return books.value.find { it.id == bookId }?.localFilePath?.takeIf { it.isNotBlank() }
    }

    fun getCachedPdfBitmap(bookId: String, pageIndex: Int, filePath: String? = null): Bitmap? {
        val resolvedPath = resolvePdfFilePath(bookId, filePath) ?: return null
        return PdfNativeRenderer.getAnyCachedPageBitmap(resolvedPath, pageIndex)
    }

    suspend fun loadPdfPageBitmap(bookId: String, filePath: String, pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        getCachedPdfBitmap(bookId, pageIndex, filePath)?.let { return@withContext it }
        PdfNativeRenderer.renderPageBitmap(filePath, pageIndex, 1080)
    }

    fun prefetchPdfNeighbors(bookId: String, filePath: String, pageIndex: Int, isTwoPageSpread: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            val book = books.value.find { it.id == bookId }
            val total = book?.pagesCount ?: 9999
            val pagesToPrefetch = if (isTwoPageSpread) {
                listOf(
                    pageIndex - 2,
                    pageIndex - 1,
                    pageIndex,
                    pageIndex + 1,
                    pageIndex + 2,
                    pageIndex + 3
                )
            } else {
                listOf(pageIndex + 1, pageIndex - 1, pageIndex + 2, pageIndex - 2)
            }
            for (p in pagesToPrefetch) {
                if (p in 0 until total && PdfNativeRenderer.getAnyCachedPageBitmap(filePath, p) == null) {
                    PdfNativeRenderer.renderPageBitmap(filePath, p, 1080)
                }
            }
        }
    }

    fun buildTranslatedPdfCacheKey(bookId: String, pageIndex: Int, targetLang: String, mode: String): String {
        val normLang = normalizeTranslationLangCode(targetLang)
        val normMode = mode.trim().lowercase()
        return "$bookId:$pageIndex:$normLang:$normMode"
    }

    fun buildTranslatedTextCacheKey(bookId: String, pageIndex: Int, targetLang: String, sourceHash: Int): String {
        val normLang = normalizeTranslationLangCode(targetLang)
        return "$bookId:$pageIndex:$normLang:$sourceHash"
    }

    fun getCachedTranslatedPdfBitmap(bookId: String, pageIndex: Int, targetLang: String, mode: String): Bitmap? {
        val keyOwner = resolvePdfFilePath(bookId) ?: bookId
        return PdfNativeRenderer.getCachedTranslatedPageBitmap(keyOwner, pageIndex, targetLang, mode)
    }

    fun putCachedTranslatedPdfBitmap(bookId: String, pageIndex: Int, targetLang: String, mode: String, bitmap: Bitmap) {
        val keyOwner = resolvePdfFilePath(bookId) ?: bookId
        PdfNativeRenderer.putCachedTranslatedPageBitmap(keyOwner, pageIndex, targetLang, mode, bitmap)
    }

    fun getCachedTranslatedParagraphs(bookId: String, pageIndex: Int, targetLang: String, sourceHash: Int): List<Pair<String, String?>>? {
        val key = buildTranslatedTextCacheKey(bookId, pageIndex, targetLang, sourceHash)
        val cached = translatedTextCache.get(key) ?: return null
        if (!isPageTranslationComplete(cached, targetLang)) {
            translatedTextCache.remove(key)
            return null
        }
        return cached
    }

    fun putCachedTranslatedParagraphs(bookId: String, pageIndex: Int, targetLang: String, sourceHash: Int, paragraphs: List<Pair<String, String?>>) {
        if (!isPageTranslationComplete(paragraphs, targetLang)) return
        val key = buildTranslatedTextCacheKey(bookId, pageIndex, targetLang, sourceHash)
        translatedTextCache.put(key, paragraphs)
    }

    fun setTranslationTargetLang(newLang: String) {
        val normLang = normalizeTranslationLangCode(newLang)
        activeTranslationJob?.cancel()
        com.llunaticsys.llunevie.ui.reader.clearEpubSnapshotCache(evictAll = true)
        _uiState.update {
            it.copy(
                translationTargetLang = normLang,
                isTranslatingPage = it.isTranslationActive && it.translationMode != "original",
                translationRevision = it.translationRevision + 1
            )
        }
        updateTranslatorPrefs(targetLang = normLang)
        // If translation is active, immediately trigger translation with the EXPLICIT normLang parameter:
        if (_uiState.value.isTranslationActive && _uiState.value.translationMode != "original") {
            triggerCurrentPageTranslation(explicitTargetLang = normLang)
        }
    }

    fun addCustomTranslationLang(code: String) {
        val norm = normalizeTranslationLangCode(code)
        viewModelScope.launch {
            prefsRepo.addCustomTranslationLang(norm)
        }
    }

    fun removeCustomTranslationLang(code: String) {
        val norm = normalizeTranslationLangCode(code)
        viewModelScope.launch {
            prefsRepo.removeCustomTranslationLang(norm)
        }
        if (normalizeTranslationLangCode(_uiState.value.translationTargetLang) == norm) {
            setTranslationTargetLang("en")
        }
    }

    fun setTranslationActive(active: Boolean) {
        activeTranslationJob?.cancel()
        com.llunaticsys.llunevie.ui.reader.clearEpubSnapshotCache(evictAll = true)
        _uiState.update {
            it.copy(
                isTranslationActive = active,
                isTranslatingPage = active && it.translationMode != "original",
                translationRevision = it.translationRevision + 1
            )
        }
        if (active && _uiState.value.translationMode != "original") {
            triggerCurrentPageTranslation(explicitTargetLang = _uiState.value.translationTargetLang)
        }
    }

    fun setTranslationMode(mode: String) {
        val normMode = mode.trim().lowercase()
        activeTranslationJob?.cancel()
        com.llunaticsys.llunevie.ui.reader.clearEpubSnapshotCache(evictAll = true)
        _uiState.update {
            it.copy(
                translationMode = normMode,
                isTranslatingPage = it.isTranslationActive && normMode != "original",
                translationRevision = it.translationRevision + 1
            )
        }
        updateTranslatorPrefs(displayMode = normMode)
        if (_uiState.value.isTranslationActive && normMode != "original") {
            triggerCurrentPageTranslation(explicitTargetLang = _uiState.value.translationTargetLang)
        }
    }

    fun triggerCurrentPageTranslation(explicitTargetLang: String) {
        val normLang = normalizeTranslationLangCode(explicitTargetLang)
        activeTranslationJob?.cancel()
        activeTranslationJob = viewModelScope.launch(Dispatchers.IO) {
            val st = _uiState.value
            val bookId = st.activeBookId ?: return@launch
            val book = books.value.find { it.id == bookId } ?: return@launch
            val isPdf = book.format.equals("PDF", ignoreCase = true) || book.localFilePath?.endsWith(".pdf", ignoreCase = true) == true
            val chapters = st.activeBookChapters
            val metrics = computeGlobalPageMetrics(chapters, st.activeChapterIndex, st.activePageIndex)
            val globalPageIdx = (metrics.currentGlobalPage - 1).coerceAtLeast(0)
            val mode = st.translationMode
            val isSpread = preferences.value.readingFlowMode == ReadingFlowMode.SPREAD
            val isBilingualSpread = isSpread && mode.equals("bilingual", ignoreCase = true)
            val isContinuousScroll = preferences.value.readingFlowMode == ReadingFlowMode.SCROLL ||
                preferences.value.readingFlowMode == ReadingFlowMode.WEBTOON
            val targetPages = if (isContinuousScroll) {
                listOf(globalPageIdx).filter { it in 0 until metrics.totalBookPages }
            } else if (isSpread) {
                val sStart = (globalPageIdx / 2) * 2
                listOf(globalPageIdx, sStart, sStart + 1).distinct().filter { it in 0 until metrics.totalBookPages }
            } else {
                listOf(globalPageIdx).filter { it in 0 until metrics.totalBookPages }
            }

            if (isPdf) {
                val path = book.localFilePath ?: return@launch
                for (pZero in targetPages) {
                    runCatching {
                        loadOrTranslatePdfPageBitmap(
                            bookId = bookId,
                            filePath = path,
                            pageIndex = pZero,
                            targetLang = normLang,
                            mode = if (isBilingualSpread) "replace" else mode
                        )
                    }
                }
            } else {
                for (pZero in targetPages) {
                    val pageRaw = getPageContentAtGlobalZeroIndex(chapters, pZero)?.third.orEmpty()
                    if (pageRaw.isNotBlank()) {
                        runCatching {
                            loadOrTranslatePageParagraphs(
                                bookId = bookId,
                                pageIndex = pZero,
                                pageRaw = pageRaw,
                                targetLang = normLang
                            )
                        }
                    }
                }
            }
        }
    }

    suspend fun loadOrTranslatePdfPageBitmap(
        bookId: String,
        filePath: String,
        pageIndex: Int,
        targetLang: String,
        mode: String,
        targetWidthPx: Int = 1080
    ): Bitmap? = withContext(Dispatchers.IO) {
        val normLang = normalizeTranslationLangCode(targetLang)
        val normMode = mode.trim().lowercase()
        if (normMode == "original") {
            return@withContext loadPdfPageBitmap(bookId, filePath, pageIndex)
        }
        getCachedTranslatedPdfBitmap(bookId, pageIndex, normLang, normMode)?.let { return@withContext it }

        val inFlightKey = "${buildTranslatedPdfCacheKey(bookId, pageIndex, normLang, normMode)}:$targetWidthPx"
        val newDeferred = viewModelScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                getCachedTranslatedPdfBitmap(bookId, pageIndex, normLang, normMode)?.let { return@async it }

                _uiState.update { it.copy(isTranslatingPage = true) }

                val translationRepo = TranslationRepository.getInstance(appContext)
                val book = books.value.find { it.id == bookId }
                val bookTitle = book?.title.orEmpty()
                val blocks = translationRepo.extractPdfPageParagraphBlocks(appContext, filePath, pageIndex, bookTitle)
                if (blocks.isEmpty()) {
                    val orig = loadPdfPageBitmap(bookId, filePath, pageIndex)
                    _uiState.update { it.copy(isTranslatingPage = false) }
                    return@async orig
                }

                val texts = blocks.map { it.text }
                val currentPrefs = preferences.value
                val res = runCatching {
                    translationRepo.translateParagraphList(texts, currentPrefs, appContext, explicitTargetLang = normLang)
                }.getOrElse { Result.failure(it) }
                if (!res.isSuccess) {
                    _uiState.update { it.copy(isTranslatingPage = false) }
                    return@async loadPdfPageBitmap(bookId, filePath, pageIndex)
                }

                val transPairs = res.getOrNull() ?: return@async loadPdfPageBitmap(bookId, filePath, pageIndex)
                val transTexts = transPairs.map { it.second ?: it.first }

                val isIllust = pageIndex == 0 || _uiState.value.activeBookIllustrations.any { it.pageIndexInBook == pageIndex }
                val rendered = PdfNativeRenderer.getOrRenderTranslatedPdfPageBitmap(
                    pdfFilePath = filePath,
                    pageIndex = pageIndex,
                    blocks = blocks,
                    translatedTexts = transTexts,
                    displayMode = normMode,
                    targetWidthPx = targetWidthPx,
                    isIllustrationPage = isIllust,
                    targetLang = normLang,
                    engine = currentPrefs.translatorEngine
                ) ?: loadPdfPageBitmap(bookId, filePath, pageIndex)

                if (rendered != null) {
                    putCachedTranslatedPdfBitmap(bookId, pageIndex, normLang, normMode, rendered)
                }

                if (normalizeTranslationLangCode(_uiState.value.translationTargetLang) == normLang) {
                    _uiState.update {
                        it.copy(
                            isTranslatingPage = false,
                            translationRevision = it.translationRevision + 1
                        )
                    }
                }

                rendered
            } finally {
                inFlightPdfTranslations.remove(inFlightKey)
            }
        }

        val activeDeferred = inFlightPdfTranslations.compute(inFlightKey) { _, existing ->
            if (existing != null && existing.isActive) existing else newDeferred
        }!!
        if (activeDeferred !== newDeferred) {
            newDeferred.cancel()
        }
        activeDeferred.start()
        runCatching { activeDeferred.await() }.getOrNull()
    }

    suspend fun loadOrTranslatePageParagraphs(
        bookId: String,
        pageIndex: Int,
        pageRaw: String,
        targetLang: String
    ): List<Pair<String, String?>>? = withContext(Dispatchers.IO) {
        val normLang = normalizeTranslationLangCode(targetLang)
        val sourceHash = pageRaw.hashCode()
        getCachedTranslatedParagraphs(bookId, pageIndex, normLang, sourceHash)?.let { return@withContext it }

        val inFlightKey = buildTranslatedTextCacheKey(bookId, pageIndex, normLang, sourceHash)
        val newDeferred = viewModelScope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
            try {
                getCachedTranslatedParagraphs(bookId, pageIndex, normLang, sourceHash)?.let { return@async it }

                _uiState.update { it.copy(isTranslatingPage = true) }

                val translationRepo = TranslationRepository.getInstance(appContext)
                val currentPrefs = preferences.value
                val res = runCatching {
                    translationRepo.translatePageParagraphs(pageRaw, currentPrefs, appContext, explicitTargetLang = normLang)
                }.getOrElse { Result.failure(it) }
                if (!res.isSuccess) {
                    _uiState.update { it.copy(isTranslatingPage = false) }
                    return@async null
                }

                val transList = res.getOrNull() ?: return@async null
                val isComplete = isPageTranslationComplete(transList, normLang)
                if (isComplete) {
                    putCachedTranslatedParagraphs(bookId, pageIndex, normLang, sourceHash, transList)
                }

                if (normalizeTranslationLangCode(_uiState.value.translationTargetLang) == normLang) {
                    if (isComplete) {
                        _uiState.update {
                            it.copy(
                                isTranslatingPage = false,
                                translationRevision = it.translationRevision + 1
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                isTranslatingPage = false
                            )
                        }
                    }
                }

                transList
            } finally {
                inFlightTextTranslations.remove(inFlightKey)
            }
        }

        val activeDeferred = inFlightTextTranslations.compute(inFlightKey) { _, existing ->
            if (existing != null && existing.isActive) existing else newDeferred
        }!!
        if (activeDeferred !== newDeferred) {
            newDeferred.cancel()
        }
        activeDeferred.start()
        runCatching { activeDeferred.await() }.getOrNull()
    }

    private var searchJob: Job? = null
    private var toastJob: Job? = null
    private var readingTimerJob: Job? = null
    @Volatile
    private var isAppInForeground: Boolean = true
    private var sessionAccumulatedSeconds: Int = 0
    private val sessionFlushLock = Any()
    private val orphanSweepDone = CompletableDeferred<Unit>()

    init {
        startActiveReadingSessionTicker()
        _uiState.update { it.copy(isInitialDataReady = true) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val all = dao.getAllBooks()
                all.forEach { b ->
                    val t = b.title.trim()
                    if (t.startsWith("Searching ISBN", ignoreCase = true) ||
                        t.startsWith("Mencari ISBN", ignoreCase = true) ||
                        t.startsWith("ISBN Not Found", ignoreCase = true) ||
                        t.startsWith("ISBN Tidak Ditemukan", ignoreCase = true)
                    ) {
                        dao.deleteBooksByIds(listOf(b.id))
                    } else {
                        val rawShelf = b.shelf.trim()
                        if (rawShelf.isEmpty() || rawShelf.equals("All", true) || rawShelf.equals("Semua", true)) {
                            val fixed = if (b.format.equals("Physical", true) || b.format.equals("Fisik", true)) "Koleksi Fisik" else "eReader"
                            dao.updateBook(b.copy(shelf = fixed))
                        }
                    }
                }
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                runCatching {
                    // Delete all redundant .epub and .txt files inside File(appContext.filesDir, "books")
                    val booksDir = File(appContext.filesDir, "books")
                    if (booksDir.exists()) {
                        booksDir.listFiles()?.forEach { f ->
                            val ext = f.extension.lowercase()
                            if (ext == "epub" || ext == "txt") {
                                f.delete()
                            }
                        }
                    }

                    // Delete any subfolder in covers or file in books whose book ID is not in activeBookIds
                    val activeBookIds = dao.getAllBooks().map { it.id }.toSet()
                    val coversDir = File(appContext.filesDir, "covers")
                    if (coversDir.exists()) {
                        coversDir.listFiles()?.forEach { folder ->
                            if (folder.isDirectory && folder.name !in activeBookIds) {
                                folder.deleteRecursively()
                            }
                        }
                    }
                    if (booksDir.exists()) {
                        booksDir.listFiles()?.forEach { file ->
                            val baseName = file.nameWithoutExtension
                            if (baseName !in activeBookIds) {
                                file.delete()
                            }
                        }
                    }

                    // Delete leftover PDFBox* or tmp_* files inside appContext.cacheDir
                    appContext.cacheDir.listFiles()?.forEach { f ->
                        val name = f.name
                        if (name.startsWith("PDFBox", ignoreCase = true) || name.startsWith("tmp_", ignoreCase = true)) {
                            if (f.isDirectory) f.deleteRecursively() else f.delete()
                        }
                    }
                }
            } finally {
                orphanSweepDone.complete(Unit)
            }
            val initialPrefs = prefsRepo.preferencesFlow.first()
            _uiState.update {
                it.copy(
                    translationTargetLang = normalizeTranslationLangCode(initialPrefs.translatorTargetLang),
                    translationMode = initialPrefs.translatorDisplayMode.ifBlank { "replace" }
                )
            }
            if (initialPrefs.autoImportFolderEnabled) {
                scanAutoImportFolder()
            }
        }
    }

    fun onAppForegroundChanged(inForeground: Boolean) {
        isAppInForeground = inForeground
        if (!inForeground) {
            flushActiveReadingSession(_uiState.value.activeBookId)
        }
    }

    fun flushActiveReadingSession(bookIdOverride: String? = null) {
        val targetBookId = bookIdOverride ?: _uiState.value.activeBookId ?: return
        val deltaSeconds: Int
        synchronized(sessionFlushLock) {
            deltaSeconds = sessionAccumulatedSeconds
            sessionAccumulatedSeconds = 0
        }
        if (deltaSeconds <= 0) return

        viewModelScope.launch(Dispatchers.IO) {
            val book = dao.getBookById(targetBookId) ?: books.value.find { it.id == targetBookId } ?: return@launch
            val prevTotalSeconds = maxOf(book.readingSeconds, book.timeMinutes * 60)
            val prevWholeMinutes = prevTotalSeconds / 60
            val newTotalSeconds = prevTotalSeconds + deltaSeconds
            val newWholeMinutes = newTotalSeconds / 60
            val newlyCompletedMinutes = (newWholeMinutes - prevWholeMinutes).coerceAtLeast(0)

            dao.updateBook(
                book.copy(
                    readingSeconds = newTotalSeconds,
                    timeMinutes = newWholeMinutes,
                    lastReadAtMillis = System.currentTimeMillis()
                )
            )
            if (newlyCompletedMinutes > 0) {
                recordDailyReadingMinute(newlyCompletedMinutes, 0)
            }
        }
    }

    /**
     * Smooth, zero-jank reading timer:
     * Accumulates seconds in memory while app is in the foreground and in Reader tab without writing
     * to Room every second. Flushes every 60 seconds and on Reader exit / lifecycle stop!
     */
    private fun startActiveReadingSessionTicker() {
        readingTimerJob?.cancel()
        readingTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val st = _uiState.value
                if (isAppInForeground && st.activeTab == MainNavTab.READER && st.activeBookId != null) {
                    val shouldFlush = synchronized(sessionFlushLock) {
                        sessionAccumulatedSeconds++
                        sessionAccumulatedSeconds >= 60
                    }
                    if (shouldFlush) {
                        flushActiveReadingSession(st.activeBookId)
                    }
                }
            }
        }
    }

    private suspend fun recordDailyReadingMinute(addedMinutes: Int, addedPages: Int) {
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val existing = dao.getDailyLog(todayKey)
        val updated = DailyReadingLogEntity(
            dateKey = todayKey,
            minutesRead = (existing?.minutesRead ?: 0) + addedMinutes,
            pagesTurned = (existing?.pagesTurned ?: 0) + addedPages
        )
        dao.upsertDailyLog(updated)
    }

    fun showToast(message: String) {
        toastJob?.cancel()
        _uiState.update { it.copy(toastMessage = message) }
        toastJob = viewModelScope.launch {
            delay(2400L)
            _uiState.update { it.copy(toastMessage = null) }
        }
    }

    fun switchTab(tab: MainNavTab) {
        val curState = _uiState.value
        if (curState.activeTab == MainNavTab.READER && tab != MainNavTab.READER) {
            flushActiveReadingSession(curState.activeBookId)
            com.llunaticsys.llunevie.ui.reader.clearEpubSnapshotCache(evictAll = true)
            EpubNativeParser.clearMemoryCaches()
            PdfNativeRenderer.clearMemoryCaches(evictPageBitmaps = false)
            viewModelScope.launch(Dispatchers.IO) {
                PdfNativeRenderer.closeCachedDoc()
            }
        }
        _uiState.update { cur ->
            val prev = if (cur.activeTab != MainNavTab.READER) cur.activeTab else cur.previousTab
            cur.copy(
                activeTab = tab,
                previousTab = prev,
                readerFullView = if (tab != MainNavTab.READER) false else cur.readerFullView,
                showReaderTypeSheet = false,
                showReaderTocDrawer = false,
                showReaderNotesDrawer = false
            )
        }
    }

    override fun onCleared() {
        PdfNativeRenderer.closeCachedDoc()
        super.onCleared()
    }

    // -------------------------------------------------------------------------
    // THEME, LIQUID GLASS, AMOLED, LANGUAGE & READER PREFERENCES
    // -------------------------------------------------------------------------
    fun setTheme(mode: AppThemeMode) {
        viewModelScope.launch {
            prefsRepo.setTheme(mode)
        }
    }

    fun setFollowDeviceTheme(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setFollowDeviceTheme(enabled)
            val isId = preferences.value.lang != "en"
            showToast(
                if (isId) {
                    if (enabled) "Ikuti Tema Perangkat: Aktif" else "Ikuti Tema Perangkat: Mati"
                } else {
                    if (enabled) "Follow Device Theme: On" else "Follow Device Theme: Off"
                }
            )
        }
    }

    fun toggleGlassDesign() {
        viewModelScope.launch {
            val next = !preferences.value.followDeviceTheme
            setFollowDeviceTheme(next)
        }
    }

    fun toggleAmoledBlack() {
        viewModelScope.launch {
            val curPrefs = preferences.value
            val next = !curPrefs.amoledBlack
            val switchedToBlack = next && curPrefs.appTheme != AppThemeMode.BLACK
            prefsRepo.setAmoledBlack(next)
            val isId = curPrefs.lang != "en"
            if (switchedToBlack) {
                showToast(
                    if (isId) "AMOLED aktif (Tema otomatis ke Black)"
                    else "AMOLED enabled (Switched to Black theme)"
                )
            } else {
                showToast(
                    if (isId) (if (next) "AMOLED: Aktif" else "AMOLED: Mati")
                    else (if (next) "AMOLED: On" else "AMOLED: Off")
                )
            }
        }
    }

    fun setLanguage(lang: String) {
        viewModelScope.launch {
            prefsRepo.setLanguage(lang)
            showToast(if (lang == "en") "English applied" else "Bahasa Indonesia diterapkan")
        }
    }

    fun setUserName(name: String) {
        viewModelScope.launch {
            prefsRepo.setUserName(name)
            showToast(name.ifBlank { "Llunevie" })
        }
    }

    fun importUserPhoto(uri: Uri) {
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(appContext.filesDir, "profile").apply { mkdirs() }
                    val outFile = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
                    appContext.contentResolver.openInputStream(uri)?.use { input ->
                        FileOutputStream(outFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    outFile.absolutePath
                }.getOrNull()
            }
            if (savedPath != null) {
                prefsRepo.setUserPhotoPath(savedPath)
            }
        }
    }

    fun resetUserPhoto() {
        viewModelScope.launch {
            prefsRepo.setUserPhotoPath(null)
        }
    }

    fun setUserPhotoPath(path: String?) {
        viewModelScope.launch {
            prefsRepo.setUserPhotoPath(path)
        }
    }

    fun setReadingFlowMode(mode: ReadingFlowMode, showFeedback: Boolean = true) {
        viewModelScope.launch {
            // Close open sheets first before orientation/mode transitions to prevent WindowManager crashes
            _uiState.update {
                it.copy(
                    showReaderTypeSheet = false,
                    readerFullView = (mode == ReadingFlowMode.SPREAD)
                )
            }
            prefsRepo.setReadingFlowMode(mode)
            if (showFeedback) {
                showToast(mode.localizedLabel(preferences.value.lang))
            }
        }
    }

    fun setAnimatePageTurn(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAnimatePageTurn(enabled)
        }
    }

    fun setPageAnimStyle(style: PageAnimStyle, showFeedback: Boolean = true) {
        viewModelScope.launch {
            prefsRepo.setPageAnimStyle(style)
            if (!preferences.value.animatePageTurn) {
                prefsRepo.setAnimatePageTurn(true)
            }
            if (showFeedback) {
                showToast(style.label)
            }
        }
    }

    fun setVolumeKeysTurn(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setVolumeKeysTurn(enabled)
        }
    }

    fun updateTranslatorPrefs(
        engine: String? = null,
        targetLang: String? = null,
        displayMode: String? = null,
        apiKey: String? = null,
        modelName: String? = null,
        customBaseUrl: String? = null,
        systemPrompt: String? = null
    ) {
        val normLang = targetLang?.let { normalizeTranslationLangCode(it) }
        viewModelScope.launch {
            prefsRepo.updateTranslatorPrefs(
                engine = engine,
                targetLang = normLang,
                displayMode = displayMode,
                apiKey = apiKey,
                modelName = modelName,
                customBaseUrl = customBaseUrl,
                systemPrompt = systemPrompt
            )
        }
        if (normLang != null || displayMode != null) {
            com.llunaticsys.llunevie.ui.reader.clearEpubSnapshotCache(evictAll = true)
            _uiState.update {
                it.copy(
                    translationTargetLang = normLang ?: it.translationTargetLang,
                    translationMode = displayMode ?: it.translationMode,
                    translationRevision = it.translationRevision + 1
                )
            }
        }
    }

    fun clearTranslationCache() {
        viewModelScope.launch {
            com.llunaticsys.llunevie.ui.reader.clearEpubSnapshotCache(evictAll = true)
            translatedTextCache.evictAll()
            PdfNativeRenderer.clearTranslatedPdfBitmaps()
            TranslationRepository.getInstance(appContext).clearCache(appContext)
            _uiState.update { it.copy(translationRevision = it.translationRevision + 1) }
            val isId = preferences.value.lang != "en"
            showToast(if (isId) "Cache terjemahan dibersihkan" else "Translation cache cleared")
        }
    }

    fun setPdfThemeTintEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setPdfThemeTintEnabled(enabled)
        }
    }

    fun setPdfProtectIllustrations(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setPdfProtectIllustrations(enabled)
        }
    }

    fun updateTypography(
        fontFamily: String? = null,
        fontSizeSp: Int? = null,
        fontWeight: Int? = null,
        textAlign: String? = null,
        lineHeight: Float? = null,
        paraSpacing: Float? = null,
        letterSpacing: Float? = null,
        wordSpacing: Int? = null,
        pageMargin: Int? = null,
        paraIndent: Boolean? = null,
        commitToDisk: Boolean = true
    ) {
        val current = preferences.value
        val updated = current.copy(
            fontFamily = fontFamily ?: current.fontFamily,
            fontSizeSp = fontSizeSp ?: current.fontSizeSp,
            fontWeight = fontWeight ?: current.fontWeight,
            textAlign = textAlign ?: current.textAlign,
            lineHeightMultiplier = lineHeight ?: current.lineHeightMultiplier,
            paraSpacingEm = paraSpacing ?: current.paraSpacingEm,
            letterSpacingEm = letterSpacing ?: current.letterSpacingEm,
            wordSpacingPx = wordSpacing ?: current.wordSpacingPx,
            pageMarginDp = pageMargin ?: current.pageMarginDp,
            paraIndent = paraIndent ?: current.paraIndent
        )
        _previewOverrides.value = updated

        if (commitToDisk) {
            commitTypography()
        }
    }

    fun commitTypography() {
        val current = _previewOverrides.value ?: preferences.value
        viewModelScope.launch {
            prefsRepo.updateReaderTypography(
                fontFamily = current.fontFamily,
                fontSizeSp = current.fontSizeSp,
                fontWeight = current.fontWeight,
                textAlign = current.textAlign,
                lineHeight = current.lineHeightMultiplier,
                paraSpacing = current.paraSpacingEm,
                letterSpacing = current.letterSpacingEm,
                wordSpacing = current.wordSpacingPx,
                pageMargin = current.pageMarginDp,
                paraIndent = current.paraIndent
            )
            _previewOverrides.value = null
        }
    }

    // -------------------------------------------------------------------------
    // LIBRARY VIEW, SORT, FILTER, SHELVES & MULTI-SELECT
    // -------------------------------------------------------------------------
    fun setLibraryViewMode(mode: LibraryViewMode) {
        viewModelScope.launch { prefsRepo.updateLibraryViewPrefs(viewMode = mode) }
    }

    fun setLibraryCoverStyle(style: String) {
        viewModelScope.launch { prefsRepo.setLibraryCoverStyle(style) }
    }

    fun setHomeRecentMode(mode: String) {
        viewModelScope.launch { prefsRepo.updateLibraryViewPrefs(homeRecentMode = mode) }
    }

    /**
     * Directly cycles sort option (`Terbaru -> Judul A–Z -> Progres`) with 1 tap without bottom toast
     * matching `#libSortBtn` in `llunevie_prototype.html` without opening any popup dialog.
     */
    fun cycleLibrarySort() {
        val current = preferences.value.libSort
        val order = LibrarySortOption.entries
        val next = order[(order.indexOf(current) + 1) % order.size]
        setLibrarySort(next)
    }

    fun setLibrarySort(sort: LibrarySortOption) {
        viewModelScope.launch {
            prefsRepo.updateLibraryViewPrefs(sort = sort)
        }
    }

    fun setLibraryFilter(filter: String) {
        viewModelScope.launch { prefsRepo.updateLibraryViewPrefs(filter = filter) }
    }

    fun setLibraryStatus(status: String) {
        viewModelScope.launch { prefsRepo.updateLibraryViewPrefs(status = status) }
    }

    fun setLibraryShelf(shelfName: String) {
        viewModelScope.launch {
            val key = if (shelfName.equals("all", ignoreCase = true)) "all" else canonicalShelfKey(shelfName)
            prefsRepo.updateLibraryViewPrefs(shelf = key)
        }
    }

    fun setLibraryGridCols(cols: Int) {
        viewModelScope.launch { prefsRepo.updateLibraryViewPrefs(gridCols = cols) }
    }

    fun resetLibraryFilters() {
        viewModelScope.launch {
            prefsRepo.updateLibraryViewPrefs(
                shelf = "all",
                filter = "all",
                status = "all",
                sort = LibrarySortOption.RECENT,
                gridCols = 0,
                genre = "all"
            )
            _uiState.update { it.copy(showLibFilterSheet = false) }
        }
    }

    fun setLibraryGenre(genre: String) {
        viewModelScope.launch {
            prefsRepo.setLibraryGenre(genre)
        }
    }

    fun addCustomGenre(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            prefsRepo.addCustomGenre(trimmed)
            val isId = preferences.value.lang != "en"
            showToast(if (isId) "Genre \"$trimmed\" ditambahkan" else "Genre \"$trimmed\" added")
        }
    }

    fun deleteCustomGenre(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            prefsRepo.removeCustomGenre(trimmed)
            if (preferences.value.libGenre.equals(trimmed, ignoreCase = true)) {
                prefsRepo.setLibraryGenre("all")
            }
            val allBooks = dao.getAllBooks()
            for (b in allBooks) {
                val currentGenres = b.genreList()
                if (currentGenres.any { it.equals(trimmed, ignoreCase = true) }) {
                    val updatedGenres = currentGenres.filterNot { it.equals(trimmed, ignoreCase = true) }.joinToString(", ")
                    dao.updateBookGenresById(b.id, updatedGenres)
                }
            }
            val isId = preferences.value.lang != "en"
            showToast(if (isId) "Genre dihapus" else "Genre deleted")
        }
    }

    fun updateBookShelfAndGenres(bookId: String, newShelf: String, newGenres: List<String>) {
        viewModelScope.launch {
            val book = dao.getBookById(bookId) ?: return@launch
            val cleanedGenres = newGenres.map { it.trim() }.filter { it.isNotEmpty() }.distinctBy { it.lowercase() }.joinToString(", ")
            val updated = book.copy(
                shelf = canonicalShelfKey(newShelf.trim()),
                genres = cleanedGenres
            )
            dao.updateBook(updated)
            val isId = preferences.value.lang != "en"
            showToast(if (isId) "Koleksi & genre diperbarui" else "Collection & genres updated")
        }
    }

    fun batchAssignShelfAndGenres(bookIds: List<String>, targetShelf: String?, addGenres: List<String>) {
        if (bookIds.isEmpty()) return
        viewModelScope.launch {
            if (!targetShelf.isNullOrBlank()) {
                val canonical = canonicalShelfKey(targetShelf)
                dao.updateBooksShelfByIds(bookIds, canonical)
            }
            if (addGenres.isNotEmpty()) {
                val cleanedAdd = addGenres.map { it.trim() }.filter { it.isNotEmpty() }
                for (id in bookIds) {
                    val b = dao.getBookById(id) ?: continue
                    val merged = (b.genreList() + cleanedAdd).distinctBy { it.lowercase() }.joinToString(", ")
                    dao.updateBookGenresById(id, merged)
                }
            }
            exitSelectionMode()
            val isId = preferences.value.lang != "en"
            showToast(if (isId) "${bookIds.size} buku diperbarui" else "${bookIds.size} books updated")
        }
    }

    fun setLibSearchQuery(q: String) {
        _uiState.update { it.copy(libSearchQuery = q) }
    }

    fun toggleShowLibFilterSheet(show: Boolean) {
        _uiState.update { it.copy(showLibFilterSheet = show) }
    }

    fun addCustomShelf(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            val order = shelves.value.size
            dao.upsertShelf(ShelfEntity(name = trimmed, sortOrder = order, isDefault = false))
            prefsRepo.updateLibraryViewPrefs(shelf = trimmed)
            val isId = preferences.value.lang != "en"
            showToast(if (isId) "Rak \"$trimmed\" ditambahkan" else "Shelf \"$trimmed\" added")
        }
    }

    fun deleteCustomShelf(name: String) {
        viewModelScope.launch {
            val fallbackShelf = shelves.value.firstOrNull { it.name != name }?.name ?: "eReader"
            dao.moveBooksFromDeletedShelf(name, fallbackShelf)
            dao.deleteCustomShelf(name)
            if (canonicalShelfKey(preferences.value.libShelf) == canonicalShelfKey(name)) {
                prefsRepo.updateLibraryViewPrefs(shelf = fallbackShelf)
            }
            val isId = preferences.value.lang != "en"
            showToast(if (isId) "Rak dihapus" else "Shelf deleted")
        }
    }

    fun toggleBookSelection(bookId: String) {
        _uiState.update { cur ->
            val nextSet = cur.libSelectedIds.toMutableSet()
            if (nextSet.contains(bookId)) nextSet.remove(bookId) else nextSet.add(bookId)
            cur.copy(
                libSelectMode = nextSet.isNotEmpty(),
                libSelectedIds = nextSet
            )
        }
    }

    fun selectAllFilteredBooks(bookIds: List<String>) {
        _uiState.update { cur ->
            val allSelected = bookIds.isNotEmpty() && bookIds.all { cur.libSelectedIds.contains(it) }
            if (allSelected) {
                cur.copy(libSelectMode = false, libSelectedIds = emptySet())
            } else {
                cur.copy(libSelectMode = true, libSelectedIds = bookIds.toSet())
            }
        }
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(libSelectMode = false, libSelectedIds = emptySet()) }
    }

    fun openEditSelectedBook() {
        val id = _uiState.value.libSelectedIds.firstOrNull() ?: return
        val book = books.value.find { it.id == id } ?: return
        _uiState.update { it.copy(editingBook = book) }
    }

    fun closeEditBookModal() {
        _uiState.update { it.copy(editingBook = null) }
    }

    fun saveEditedBook(updated: BookEntity) {
        viewModelScope.launch {
            val completed = updated.progress >= 100
            dao.updateBook(
                updated.copy(
                    shelf = canonicalShelfKey(updated.shelf),
                    completedAllPages = completed,
                    onTheGo = updated.progress in 1..99
                )
            )
            _uiState.update { it.copy(editingBook = null, libSelectMode = false, libSelectedIds = emptySet()) }
            val isId = preferences.value.lang != "en"
            showToast(if (isId) "Detail \"${updated.title}\" diperbarui" else "\"${updated.title}\" updated")
        }
    }

    fun setShowDeleteConfirmDialog(show: Boolean) {
        _uiState.update { it.copy(showDeleteConfirmDialog = show) }
    }

    private fun sanitizeBookId(rawId: String): String {
        val cleaned = rawId.trim().replace(Regex("[^a-zA-Z0-9_-]"), "_")
        return if (cleaned.isBlank() || cleaned.all { it == '_' }) {
            "book_${System.currentTimeMillis()}"
        } else {
            cleaned
        }
    }

    private fun isCanonicalPathInsideDir(path: String, parentDir: File): Boolean {
        return runCatching {
            val targetCanon = File(path).canonicalPath
            val parentCanon = parentDir.canonicalPath
            targetCanon.startsWith("$parentCanon${File.separator}")
        }.getOrDefault(false)
    }

    private fun deleteInternalBookFiles(bookId: String, localFilePath: String?) {
        runCatching {
            val safeId = sanitizeBookId(bookId)
            val coversRoot = File(appContext.filesDir, "covers")
            val booksRoot = File(appContext.filesDir, "books")
            val coverDir = File(coversRoot, safeId)
            if (isCanonicalPathInsideDir(coverDir.path, coversRoot)) {
                coverDir.deleteRecursively()
            }
            listOf("epub", "pdf", "txt").forEach { ext ->
                val bookFile = File(booksRoot, "$safeId.$ext")
                if (isCanonicalPathInsideDir(bookFile.path, booksRoot)) {
                    bookFile.delete()
                }
            }
            if (!localFilePath.isNullOrBlank() &&
                (isCanonicalPathInsideDir(localFilePath, booksRoot) || isCanonicalPathInsideDir(localFilePath, coversRoot))
            ) {
                File(localFilePath).delete()
            }
        }
    }

    fun confirmDeleteSelectedBooks() {
        val ids = _uiState.value.libSelectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val booksToDelete = books.value.filter { it.id in ids }
            withContext(Dispatchers.IO) {
                PdfNativeRenderer.closeCachedDoc()
                booksToDelete.forEach { b ->
                    deleteInternalBookFiles(b.id, b.localFilePath)
                }
                dao.deleteBooksByIds(ids)
            }
            _uiState.update {
                it.copy(
                    showDeleteConfirmDialog = false,
                    libSelectMode = false,
                    libSelectedIds = emptySet()
                )
            }
            val isId = preferences.value.lang != "en"
            showToast(if (isId) "${ids.size} buku dihapus dari pustaka" else "${ids.size} book(s) deleted")
        }
    }

    // -------------------------------------------------------------------------
    // READER ENGINE: OPEN BOOK, CUMULATIVE PAGINATION, CHAPTERS, HIGHLIGHTS
    // -------------------------------------------------------------------------
    
    private fun reflowOversizedTextPages(pages: List<String>, maxChars: Int = 580): List<String> {
        val reflowed = mutableListOf<String>()
        for (pageText in pages) {
            // Keep illustration pages, PDF page markers, and normal-sized pages as-is
            if (pageText.length <= 620 || pageText.contains("[ILLUST:") || pageText.contains("[PDF_PAGE:")) {
                reflowed.add(pageText)
                continue
            }
            // Split oversized page into balanced sub-pages
            val paragraphs = pageText.split(Regex("""\n\n+"""))
            val subPages = mutableListOf<String>()
            val currentChunk = mutableListOf<String>()
            var currentChars = 0
            
            for (para in paragraphs) {
                // Check before adding to prevent overflow
                if (currentChunk.isNotEmpty() && currentChars + para.length > maxChars) {
                    subPages.add(currentChunk.joinToString("\n\n"))
                    currentChunk.clear()
                    currentChars = 0
                }
                // Split single oversized paragraphs at sentence boundaries
                if (para.length > 620) {
                    val sentences = para.split(Regex("""(?<=[.!?…])\s+|(?<=[.!?…]["”’'])\s+"""))
                    var subChunk = StringBuilder()
                    for (sent in sentences) {
                        if (subChunk.isNotEmpty() && subChunk.length + sent.length > maxChars) {
                            if (currentChunk.isNotEmpty()) {
                                currentChunk.add(subChunk.toString())
                                subPages.add(currentChunk.joinToString("\n\n"))
                                currentChunk.clear()
                                currentChars = 0
                            } else {
                                subPages.add(subChunk.toString())
                            }
                            subChunk = StringBuilder()
                        }
                        if (subChunk.isNotEmpty()) subChunk.append(" ")
                        subChunk.append(sent)
                    }
                    if (subChunk.isNotEmpty()) {
                        currentChunk.add(subChunk.toString())
                        currentChars += subChunk.length
                    }
                } else {
                    currentChunk.add(para)
                    currentChars += para.length
                }
            }
            if (currentChunk.isNotEmpty()) {
                subPages.add(currentChunk.joinToString("\n\n"))
            }
            reflowed.addAll(subPages)
        }
        return reflowed
    }
    
    fun openBookInReader(bookId: String) {
        val prevActiveBookId = _uiState.value.activeBookId
        if (prevActiveBookId != null && prevActiveBookId != bookId) {
            flushActiveReadingSession(prevActiveBookId)
            viewModelScope.launch(Dispatchers.IO) {
                PdfNativeRenderer.closeCachedDoc()
            }
        }
        viewModelScope.launch {
            data class OpenBookPrepared(
                val book: BookEntity,
                val actualPageCount: Int,
                val needsPdfIndexing: Boolean,
                val safeChIdx: Int,
                val safePgIdx: Int,
                val parsedChapters: List<BookChapter>,
                val parsedIllustrations: List<BookIllustration>
            )

            val isEn = preferences.value.lang == "en"
            val prepared = withContext(Dispatchers.IO) {
                val rawBook = dao.getBookById(bookId) ?: return@withContext null
                val sanitizedTitle = sanitizeBookOrChapterText(rawBook.title)
                val book = if (sanitizedTitle != rawBook.title) {
                    val updated = rawBook.copy(title = sanitizedTitle)
                    dao.updateBook(updated)
                    updated
                } else rawBook

                val chEntities = dao.getChaptersForBook(bookId)
                val parsedChapters = mutableListOf<BookChapter>()
                val parsedIllustrations = mutableListOf<BookIllustration>()
                val isPdf = book.format.equals("PDF", true)

                if (chEntities.isNotEmpty()) {
                    withContext(Dispatchers.Default) {
                        var globalTracker = 0
                        val pdfIllustFilePageRegex = Regex("""_illust_p(\d+)\.""", RegexOption.IGNORE_CASE)

                        for (ce in chEntities) {
                            val pagesArr = runCatching { JSONArray(ce.pagesJson) }.getOrNull() ?: JSONArray()
                            val rawPagesList = (0 until pagesArr.length()).map { pagesArr.optString(it) }.ifEmpty { listOf(book.synopsis) }
                            // Apply reflow to fix oversized pages in existing EPUB/TXT books
                            val basePagesList = if (!isPdf) reflowOversizedTextPages(rawPagesList) else rawPagesList
                            val cleanChTitle = sanitizeBookOrChapterText(ce.title)

                            // For PDF books, clean any polluted [ILLUST:...] tag out of pagesList when fileOneBasedPage - 1 != globalTracker + pIdx
                            val pagesList = if (isPdf) {
                                basePagesList.mapIndexed { pIdx, pText ->
                                    if (pText.contains("[ILLUST:")) {
                                        val inner = pText.substringAfter("[ILLUST:").substringBefore("]")
                                        val path = inner.substringBefore("|")
                                        val filePageMatch = pdfIllustFilePageRegex.find(path)
                                        val fileOneBasedPage = filePageMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                                        if (fileOneBasedPage == null || fileOneBasedPage - 1 != globalTracker + pIdx) {
                                            val stripped = pText.replace(Regex("""\[ILLUST:[^]]+\]"""), "").trim()
                                            if (stripped.isBlank()) "[PDF_PAGE:${globalTracker + pIdx}]" else stripped
                                        } else {
                                            pText
                                        }
                                    } else {
                                        pText
                                    }
                                }
                            } else {
                                basePagesList
                            }

                            parsedChapters.add(BookChapter(index = ce.chapterIndex, title = cleanChTitle, pages = pagesList))

                            val illustArr = runCatching { JSONArray(ce.illustrationsJson) }.getOrNull() ?: JSONArray()
                            for (i in 0 until illustArr.length()) {
                                val obj = illustArr.optJSONObject(i) ?: continue
                                val path = obj.optString("uriOrPath")
                                if (path.isNotBlank()) {
                                    val filePageMatch = pdfIllustFilePageRegex.find(path)
                                    val fileOneBasedPage = filePageMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                                    val savedPageIdx = obj.optInt("pageIndexInBook", -1)
                                    val effectivePageIdx = when {
                                        fileOneBasedPage != null && fileOneBasedPage >= 1 -> fileOneBasedPage - 1
                                        savedPageIdx >= 0 -> savedPageIdx
                                        else -> {
                                            val found = pagesList.indexOfFirst { it.contains(path) }
                                            if (found >= 0) globalTracker + found else globalTracker
                                        }
                                    }
                                    val rawCaption = obj.optString("caption", cleanChTitle)
                                    val cleanCaption = if (fileOneBasedPage != null && fileOneBasedPage >= 1) {
                                        if (isEn) "Illustration — Page $fileOneBasedPage" else "Ilustrasi — Hal $fileOneBasedPage"
                                    } else {
                                        sanitizeBookOrChapterText(rawCaption)
                                    }
                                    if (parsedIllustrations.none { it.uriOrPath == path || (isPdf && it.pageIndexInBook == effectivePageIdx) }) {
                                        parsedIllustrations.add(
                                            BookIllustration(
                                                uriOrPath = path,
                                                caption = cleanCaption,
                                                pageIndexInBook = effectivePageIdx
                                            )
                                        )
                                    }
                                }
                            }

                            // Extract embedded illustrations in page texts if not already recorded
                            basePagesList.forEachIndexed { pIdx, pText ->
                                if (pText.contains("[ILLUST:")) {
                                    val inner = pText.substringAfter("[ILLUST:").substringBefore("]")
                                    val path = inner.substringBefore("|")
                                    val cap = inner.substringAfter("|", "")
                                    if (path.isNotBlank()) {
                                        if (isPdf) {
                                            val filePageMatch = pdfIllustFilePageRegex.find(path)
                                            val fileOneBasedPage = filePageMatch?.groupValues?.getOrNull(1)?.toIntOrNull()
                                            if (fileOneBasedPage != null && fileOneBasedPage >= 1) {
                                                val truePageIdx = fileOneBasedPage - 1
                                                val cleanCaption = if (isEn) "Illustration — Page $fileOneBasedPage" else "Ilustrasi — Hal $fileOneBasedPage"
                                                if (parsedIllustrations.none { it.uriOrPath == path || it.pageIndexInBook == truePageIdx }) {
                                                    parsedIllustrations.add(
                                                        BookIllustration(
                                                            uriOrPath = path,
                                                            caption = cleanCaption,
                                                            pageIndexInBook = truePageIdx
                                                        )
                                                    )
                                                }
                                            }
                                        } else {
                                            if (parsedIllustrations.none { it.uriOrPath == path }) {
                                                parsedIllustrations.add(
                                                    BookIllustration(
                                                        uriOrPath = path,
                                                        caption = sanitizeBookOrChapterText(cap.ifBlank { cleanChTitle }),
                                                        pageIndexInBook = globalTracker + pIdx
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            globalTracker += pagesList.size
                        }
                        parsedIllustrations.sortBy { it.pageIndexInBook }
                    }
                }

                // Verify real PDF page count: if book is PDF, verify against actual file pageCount and rebuild if needed
                var actualPageCount = book.pagesCount
                var needsPdfIndexing = false
                if (isPdf && book.localFilePath != null) {
                    val pdfFile = File(book.localFilePath)
                    if (pdfFile.exists()) {
                        actualPageCount = PdfNativeRenderer.getPageCount(pdfFile.absolutePath, book.pagesCount)

                        if (parsedChapters.isEmpty()) {
                            val rebuilt = PdfNativeRenderer.buildPdfChapters(bookId, actualPageCount, book.title, isEn)
                            dao.saveBookWithChapters(book.copy(pagesCount = actualPageCount), rebuilt)
                            rebuilt.forEach { ce ->
                                val pagesArr = runCatching { JSONArray(ce.pagesJson) }.getOrNull() ?: JSONArray()
                                val pagesList = (0 until pagesArr.length()).map { pagesArr.optString(it) }
                                parsedChapters.add(BookChapter(index = ce.chapterIndex, title = ce.title, pages = pagesList))
                            }
                        }

                        needsPdfIndexing = !book.tocIndexed || book.tocIndexVersion < PdfNativeRenderer.CURRENT_PDF_TOC_VERSION
                    }
                } else {
                    val isLegacyDuplicatedSynopsisChapters = parsedChapters.size == 2 &&
                        parsedChapters[1].pages.firstOrNull()?.let { firstPageCh2 ->
                            firstPageCh2.startsWith("Continuation of ") || firstPageCh2.startsWith("Bagian lanjutan dari ")
                        } == true
                    if (parsedChapters.isEmpty() || isLegacyDuplicatedSynopsisChapters) {
                        parsedChapters.clear()
                        val rebuiltSynopsisChapters = buildStructuredChaptersFromSynopsis(book)
                        parsedChapters.addAll(rebuiltSynopsisChapters)
                        if (isLegacyDuplicatedSynopsisChapters) {
                            val rebuiltEntities = rebuiltSynopsisChapters.map {
                                BookChapterEntity(
                                    bookId = bookId,
                                    chapterIndex = it.index,
                                    title = it.title,
                                    pagesJson = JSONArray(it.pages).toString(),
                                    illustrationsJson = "[]"
                                )
                            }
                            dao.saveBookWithChapters(book, rebuiltEntities)
                        }
                    }
                }

                val safeChIdx = book.savedChapterIndex.coerceIn(0, (parsedChapters.size - 1).coerceAtLeast(0))
                val safePgIdx = book.savedPageIndex.coerceIn(0, (parsedChapters.getOrNull(safeChIdx)?.pages?.size ?: 1) - 1)

                val nowMillis = System.currentTimeMillis()
                dao.updateBook(book.copy(onTheGo = !book.completedAllPages, lastReadAtMillis = nowMillis))

                OpenBookPrepared(
                    book = book,
                    actualPageCount = actualPageCount,
                    needsPdfIndexing = needsPdfIndexing,
                    safeChIdx = safeChIdx,
                    safePgIdx = safePgIdx,
                    parsedChapters = parsedChapters,
                    parsedIllustrations = parsedIllustrations
                )
            } ?: return@launch

            if (prepared.needsPdfIndexing && _pdfIndexingState.value[bookId] == null) {
                indexPdfChaptersAndIllustrations(prepared.book.copy(pagesCount = prepared.actualPageCount))
            }

            _uiState.update { cur ->
                cur.copy(
                    previousTab = if (cur.activeTab != MainNavTab.READER) cur.activeTab else cur.previousTab,
                    activeTab = MainNavTab.READER,
                    activeBookId = bookId,
                    activeChapterIndex = prepared.safeChIdx,
                    activePageIndex = prepared.safePgIdx,
                    activeBookChapters = prepared.parsedChapters,
                    activeBookIllustrations = prepared.parsedIllustrations,
                    readerFullView = preferences.value.readingFlowMode == ReadingFlowMode.SPREAD,
                    showReaderTypeSheet = false,
                    showReaderTocDrawer = false,
                    showReaderNotesDrawer = false
                )
            }
        }
    }

    fun forceReindexCurrentPdfBook() {
        val bookId = _uiState.value.activeBookId ?: return
        if (_pdfIndexingState.value[bookId] != null) return
        viewModelScope.launch(Dispatchers.IO) {
            val book = dao.getBookById(bookId) ?: return@launch
            val isPdf = book.format.equals("PDF", true) || book.localFilePath?.endsWith(".pdf", ignoreCase = true) == true
            if (isPdf && book.localFilePath != null) {
                indexPdfChaptersAndIllustrations(book)
            }
        }
    }

    fun indexPdfChaptersAndIllustrations(book: BookEntity) {
        val filePath = book.localFilePath ?: return
        val pdfFile = File(filePath)
        if (!pdfFile.exists()) return

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val actualPageCount = runCatching {
                    android.os.ParcelFileDescriptor.open(pdfFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                        android.graphics.pdf.PdfRenderer(pfd).use { it.pageCount }
                    }
                }.getOrDefault(book.pagesCount)

                val isEn = preferences.value.lang == "en"
                _pdfIndexingState.update { it + (book.id to 5) }
                _uiState.update { it.copy(pdfIndexingState = it.pdfIndexingState + (book.id to 5)) }

                var currentProgress = 5
                PdfNativeRenderer.scanPdfChaptersAndIllustrationsAsync(
                    context = appContext,
                    bookId = book.id,
                    pdfFile = pdfFile,
                    totalPages = actualPageCount,
                    defaultTitle = book.title,
                    isEn = isEn,
                    existingChapters = null,
                    onProgress = { pct ->
                        currentProgress = pct
                        _pdfIndexingState.update { it + (book.id to pct) }
                        _uiState.update { it.copy(pdfIndexingState = it.pdfIndexingState + (book.id to pct)) }
                    }
                ) { updatedChapters, updatedIllusts ->
                    val isComplete = currentProgress >= 100
                    val currentBook = dao.getBookById(book.id) ?: book
                    val updatedBook = if (isComplete) {
                        currentBook.copy(
                            pagesCount = actualPageCount,
                            tocIndexed = true,
                            tocIndexVersion = CURRENT_PDF_TOC_VERSION
                        )
                    } else {
                        currentBook.copy(
                            pagesCount = actualPageCount
                        )
                    }
                    dao.saveBookWithChapters(updatedBook, updatedChapters)
                    if (_uiState.value.activeBookId == book.id) {
                        val liveChs = updatedChapters.map { ce ->
                            val pArr = runCatching { JSONArray(ce.pagesJson) }.getOrNull() ?: JSONArray()
                            val pList = (0 until pArr.length()).map { pArr.optString(it) }
                            BookChapter(index = ce.chapterIndex, title = sanitizeBookOrChapterText(ce.title), pages = pList)
                        }
                        _uiState.update { it.copy(activeBookChapters = liveChs, activeBookIllustrations = updatedIllusts.sortedBy { it.pageIndexInBook }) }
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("LlunevieViewModel", "PDF indexing error for ${book.id}", t)
                // Do NOT set tocIndexed = true on failure so it can retry cleanly on next open
            } finally {
                _pdfIndexingState.update { it - book.id }
                _uiState.update { it.copy(pdfIndexingState = it.pdfIndexingState - book.id) }
            }
        }
    }

    private fun buildStructuredChaptersFromSynopsis(book: BookEntity): List<BookChapter> {
        val syn = book.synopsis.ifBlank { "${book.title} — ${book.author}" }.trim()
        val idPattern = Regex("\\b(yang|dan|di|ini|itu|aku|tidak|dengan|untuk|dari|dalam|pada|adalah|sebagai|oleh|karena|sebuah|seorang|mereka|dia|kami|kita|bisa|akan|sudah)\\b", RegexOption.IGNORE_CASE)
        val enPattern = Regex("\\b(the|and|of|to|in|is|for|that|with|on|as|are|this|by|from|they|have|had|was|were|would|could|should)\\b", RegexOption.IGNORE_CASE)
        val idHits = idPattern.findAll(syn).count()
        val enHits = enPattern.findAll(syn).count()
        val isId = when {
            idHits > enHits && idHits >= 1 -> true
            enHits > idHits && enHits >= 1 -> false
            else -> preferences.value.lang != "en"
        }

        val sentences = syn.split(Regex("(?<=[.!?])\\s+")).map { it.trim() }.filter { it.isNotBlank() }
        val pubMeta = if (isId) {
            "${book.title} ditulis oleh ${book.author} (${book.publisher}, ${book.year})."
        } else {
            "${book.title} by ${book.author} (${book.publisher}, ${book.year})."
        }
        val editionMeta = if (isId) {
            "Kategori: ${book.category.ifBlank { "Literatur" }} · Edisi: ${book.edition.ifBlank { "Standar" }} · ISBN: ${book.isbn.ifBlank { "—" }}."
        } else {
            "Category: ${book.category.ifBlank { "Literature" }} · Edition: ${book.edition.ifBlank { "Standard" }} · ISBN: ${book.isbn.ifBlank { "—" }}."
        }

        return if (sentences.size >= 4) {
            val q1 = (sentences.size / 4).coerceAtLeast(1)
            val q2 = (sentences.size * 2 / 4).coerceAtLeast(q1 + 1)
            val q3 = (sentences.size * 3 / 4).coerceAtLeast(q2 + 1)
            val page1 = sentences.subList(0, q1).joinToString(" ")
            val page2 = sentences.subList(q1, q2).joinToString(" ")
            val page3 = sentences.subList(q2, q3).joinToString(" ")
            val page4 = sentences.subList(q3, sentences.size).joinToString(" ")
            listOf(
                BookChapter(
                    index = 0,
                    title = if (isId) "Bab I — ${book.title}" else "Chapter I — ${book.title}",
                    pages = listOf("$page1\n\n$pubMeta", page2)
                ),
                BookChapter(
                    index = 1,
                    title = if (isId) "Bab II — Perkembangan Naratif" else "Chapter II — Narrative Arc",
                    pages = listOf(page3, "$page4\n\n$editionMeta")
                )
            )
        } else {
            val half = ((sentences.size + 1) / 2).coerceAtLeast(1)
            val p1 = sentences.take(half).joinToString(" ").ifBlank { syn }
            val p2 = sentences.drop(half).joinToString(" ")
            val ch1Pages = if (p2.isNotBlank() && p2 != p1) {
                listOf("$p1\n\n$pubMeta", p2)
            } else {
                listOf(p1, "$pubMeta\n\n$editionMeta")
            }
            val ch2Page1 = if (isId) {
                "Catatan Katalog — ${book.title}\n\n$pubMeta"
            } else {
                "Catalog Notes — ${book.title}\n\n$pubMeta"
            }
            val ch2Page2 = editionMeta
            listOf(
                BookChapter(
                    index = 0,
                    title = if (isId) "Bab I — ${book.title}" else "Chapter I — ${book.title}",
                    pages = ch1Pages
                ),
                BookChapter(
                    index = 1,
                    title = if (isId) "Bab II — Informasi Edisi" else "Chapter II — Edition Details",
                    pages = listOf(ch2Page1, ch2Page2)
                )
            )
        }
    }

    fun computeGlobalPageMetrics(
        chapters: List<BookChapter>,
        chIdx: Int,
        pgIdx: Int
    ): GlobalPageMetrics {
        if (chapters.isEmpty()) return GlobalPageMetrics(1, 1, 100, true)
        var total = 0
        var beforeCurrent = 0
        chapters.forEachIndexed { idx, ch ->
            val count = ch.pages.size.coerceAtLeast(1)
            if (idx < chIdx) beforeCurrent += count
            total += count
        }
        val safeTotal = total.coerceAtLeast(1)
        val currentGlobal = (beforeCurrent + pgIdx + 1).coerceIn(1, safeTotal)
        val isSpread = preferences.value.readingFlowMode == ReadingFlowMode.SPREAD
        val effectiveReached = if (isSpread) (currentGlobal + 1).coerceAtMost(safeTotal) else currentGlobal
        val isLast = effectiveReached >= safeTotal
        val pct = if (isLast) 100 else ((effectiveReached.toFloat() / safeTotal.toFloat()) * 100f).toInt().coerceIn(0, 99)
        return GlobalPageMetrics(
            currentGlobalPage = currentGlobal,
            totalBookPages = safeTotal,
            globalPct = pct,
            isAtVeryLastPage = isLast
        )
    }

    fun getPageContentAtGlobalZeroIndex(
        chapters: List<BookChapter>,
        globalZeroIdx: Int
    ): Triple<Int, Int, String>? {
        if (globalZeroIdx < 0 || chapters.isEmpty()) return null
        var cursor = 0
        for ((cIdx, ch) in chapters.withIndex()) {
            val len = ch.pages.size.coerceAtLeast(1)
            if (globalZeroIdx < cursor + len) {
                val pIdx = globalZeroIdx - cursor
                return Triple(cIdx, pIdx, ch.pages.getOrElse(pIdx) { "" })
            }
            cursor += len
        }
        return null
    }

    fun getChapterStartPageOneBased(book: BookEntity?, chapters: List<BookChapter>, chapterIndex: Int): Int {
        if (chapterIndex !in chapters.indices) return 1
        val isPdf = book?.format.equals("PDF", true) || book?.localFilePath?.endsWith(".pdf", ignoreCase = true) == true
        if (isPdf) {
            val firstPage = chapters[chapterIndex].pages.firstOrNull().orEmpty()
            val match = Regex("""\[PDF_PAGE:(\d+)\]""").find(firstPage)
            if (match != null) {
                val pZero = match.groupValues[1].toIntOrNull()
                if (pZero != null) return pZero + 1
            }
        }
        var sum = 0
        for (i in 0 until chapterIndex) {
            sum += chapters[i].pages.size.coerceAtLeast(1)
        }
        return sum + 1
    }

    private suspend fun loadCurrentBookChapters(bookId: String): List<BookChapter> {
        return if (_uiState.value.activeBookId == bookId && _uiState.value.activeBookChapters.isNotEmpty()) {
            _uiState.value.activeBookChapters
        } else {
            dao.getChaptersForBook(bookId).map { ce ->
                val pArr = runCatching { JSONArray(ce.pagesJson) }.getOrNull() ?: JSONArray()
                val pList = (0 until pArr.length()).map { pArr.optString(it) }
                BookChapter(index = ce.chapterIndex, title = ce.title, pages = pList)
            }
        }
    }

    private fun extractPdfBookmarks(chapters: List<BookChapter>): MutableList<PdfNativeRenderer.PdfBookmark> {
        var cumZero = 0
        return chapters.map { ch ->
            val firstP = ch.pages.firstOrNull().orEmpty()
            val match = Regex("""\[PDF_PAGE:(\d+)\]""").find(firstP)
            val sZero = match?.groupValues?.get(1)?.toIntOrNull() ?: cumZero
            cumZero += ch.pages.size.coerceAtLeast(1)
            PdfNativeRenderer.PdfBookmark(title = ch.title, startPageZero = sZero)
        }.toMutableList()
    }

    private suspend fun loadBookIllustrations(bookId: String): List<BookIllustration> {
        return if (_uiState.value.activeBookId == bookId && _uiState.value.activeBookIllustrations.isNotEmpty()) {
            _uiState.value.activeBookIllustrations
        } else {
            dao.getChaptersForBook(bookId).flatMap { ce ->
                val arr = runCatching { JSONArray(ce.illustrationsJson) }.getOrNull() ?: JSONArray()
                (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                    BookIllustration(
                        uriOrPath = obj.optString("uriOrPath"),
                        caption = obj.optString("caption"),
                        pageIndexInBook = obj.optInt("pageIndexInBook")
                    )
                }
            }.distinctBy { it.pageIndexInBook }
        }
    }

    private suspend fun saveAndApplyPdfChapters(
        bookId: String,
        book: BookEntity,
        bookmarks: List<PdfNativeRenderer.PdfBookmark>,
        totalPages: Int
    ) {
        val illustrations = loadBookIllustrations(bookId)
        val isEn = preferences.value.lang == "en"
        val updatedEntities = PdfNativeRenderer.buildBookChaptersFromBookmarks(
            bookId = bookId,
            bookmarks = bookmarks,
            totalPages = totalPages,
            illustrations = illustrations,
            defaultTitle = book.title,
            isEn = isEn
        )
        val updatedBook = book.copy(
            tocIndexed = true,
            tocIndexVersion = PdfNativeRenderer.CURRENT_PDF_TOC_VERSION
        )
        dao.saveBookWithChapters(updatedBook, updatedEntities)
        applyUpdatedChaptersToState(bookId, updatedBook, updatedEntities)
    }

    private fun buildNonPdfChapterEntities(
        bookId: String,
        bookmarks: List<Pair<String, Int>>,
        allPages: List<String>,
        totalPages: Int
    ): List<BookChapterEntity> {
        val updatedEntities = mutableListOf<BookChapterEntity>()
        for (i in bookmarks.indices) {
            val bm = bookmarks[i]
            val sIdx = bm.second.coerceIn(0, totalPages)
            val eIdx = if (i + 1 < bookmarks.size) bookmarks[i + 1].second.coerceIn(sIdx, totalPages) else totalPages
            val pagesSlice = if (sIdx < eIdx) allPages.subList(sIdx, eIdx) else listOf("")
            val pagesArr = JSONArray()
            pagesSlice.forEach { pagesArr.put(it) }
            updatedEntities.add(
                BookChapterEntity(
                    bookId = bookId,
                    chapterIndex = i,
                    title = bm.first,
                    pagesJson = pagesArr.toString(),
                    illustrationsJson = "[]"
                )
            )
        }
        return updatedEntities
    }

    fun addManualChapter(bookId: String, title: String, startPageOneBased: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val book = dao.getBookById(bookId) ?: return@launch
            val isPdf = book.format.equals("PDF", true) || book.localFilePath?.endsWith(".pdf", ignoreCase = true) == true
            val currentChapters = loadCurrentBookChapters(bookId)

            if (isPdf) {
                val totalPages = book.pagesCount.coerceAtLeast(1)
                val bookmarks = extractPdfBookmarks(currentChapters)
                if (bookmarks.isEmpty()) {
                    bookmarks.add(PdfNativeRenderer.PdfBookmark(book.title, 0))
                }

                val targetStartZero = (startPageOneBased - 1).coerceIn(0, totalPages - 1)
                val defaultTitle = if (preferences.value.lang == "en") "Chapter $startPageOneBased" else "Bab $startPageOneBased"
                val cleanTitle = sanitizeBookOrChapterText(title.trim()).ifBlank { defaultTitle }

                val existingIdx = bookmarks.indexOfFirst { it.startPageZero == targetStartZero }
                if (existingIdx != -1) {
                    bookmarks[existingIdx] = bookmarks[existingIdx].copy(title = cleanTitle)
                } else {
                    bookmarks.add(PdfNativeRenderer.PdfBookmark(cleanTitle, targetStartZero))
                }
                bookmarks.sortBy { it.startPageZero }
                if (bookmarks.isNotEmpty() && bookmarks.first().startPageZero > 0) {
                    bookmarks.add(0, PdfNativeRenderer.PdfBookmark(book.title, 0))
                }

                saveAndApplyPdfChapters(bookId, book, bookmarks, totalPages)
            } else {
                val allPages = currentChapters.flatMap { it.pages }
                val totalPages = allPages.size.coerceAtLeast(1)
                val splitZero = (startPageOneBased - 1).coerceIn(0, totalPages - 1)
                val defaultTitle = if (preferences.value.lang == "en") "Chapter $startPageOneBased" else "Bab $startPageOneBased"
                val cleanTitle = sanitizeBookOrChapterText(title.trim()).ifBlank { defaultTitle }

                var cum = 0
                val bookmarks = currentChapters.map { ch ->
                    val s = cum
                    cum += ch.pages.size.coerceAtLeast(1)
                    Pair(ch.title, s)
                }.toMutableList()
                if (bookmarks.isEmpty()) {
                    bookmarks.add(Pair(book.title, 0))
                }

                val existingIdx = bookmarks.indexOfFirst { it.second == splitZero }
                if (existingIdx != -1) {
                    bookmarks[existingIdx] = Pair(cleanTitle, splitZero)
                } else {
                    bookmarks.add(Pair(cleanTitle, splitZero))
                }
                bookmarks.sortBy { it.second }
                if (bookmarks.first().second > 0) {
                    bookmarks.add(0, Pair(book.title, 0))
                }

                val updatedEntities = buildNonPdfChapterEntities(bookId, bookmarks, allPages, totalPages)
                dao.saveBookWithChapters(book, updatedEntities)
                applyUpdatedChaptersToState(bookId, book, updatedEntities)
            }
        }
    }

    fun updateManualChapter(bookId: String, chapterIndex: Int, newTitle: String, newStartPageOneBased: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val book = dao.getBookById(bookId) ?: return@launch
            val isPdf = book.format.equals("PDF", true) || book.localFilePath?.endsWith(".pdf", ignoreCase = true) == true
            val currentChapters = loadCurrentBookChapters(bookId)
            if (chapterIndex !in currentChapters.indices) return@launch

            if (isPdf) {
                val totalPages = book.pagesCount.coerceAtLeast(1)
                val bookmarks = extractPdfBookmarks(currentChapters)

                val targetStartZero = (newStartPageOneBased - 1).coerceIn(0, totalPages - 1)
                val cleanTitle = sanitizeBookOrChapterText(newTitle.trim()).ifBlank { bookmarks[chapterIndex].title }
                bookmarks[chapterIndex] = PdfNativeRenderer.PdfBookmark(cleanTitle, targetStartZero)
                bookmarks.sortBy { it.startPageZero }
                if (bookmarks.isNotEmpty() && bookmarks.first().startPageZero > 0) {
                    bookmarks.add(0, PdfNativeRenderer.PdfBookmark(book.title, 0))
                }

                saveAndApplyPdfChapters(bookId, book, bookmarks, totalPages)
            } else {
                val allPages = currentChapters.flatMap { it.pages }
                val totalPages = allPages.size.coerceAtLeast(1)
                val currentStartOne = getChapterStartPageOneBased(book, currentChapters, chapterIndex)
                val cleanTitle = sanitizeBookOrChapterText(newTitle.trim()).ifBlank { currentChapters[chapterIndex].title }

                if (newStartPageOneBased == currentStartOne) {
                    val updatedEntities = currentChapters.mapIndexed { idx, ch ->
                        val title = if (idx == chapterIndex) cleanTitle else ch.title
                        val pagesArr = JSONArray()
                        ch.pages.forEach { pagesArr.put(it) }
                        BookChapterEntity(
                            bookId = bookId,
                            chapterIndex = idx,
                            title = title,
                            pagesJson = pagesArr.toString(),
                            illustrationsJson = "[]"
                        )
                    }
                    dao.saveBookWithChapters(book, updatedEntities)
                    applyUpdatedChaptersToState(bookId, book, updatedEntities)
                } else {
                    val targetStartZero = (newStartPageOneBased - 1).coerceIn(0, totalPages - 1)
                    var cum = 0
                    val bookmarks = currentChapters.map { ch ->
                        val s = cum
                        cum += ch.pages.size.coerceAtLeast(1)
                        Pair(ch.title, s)
                    }.toMutableList()

                    bookmarks[chapterIndex] = Pair(cleanTitle, targetStartZero)
                    bookmarks.sortBy { it.second }
                    if (bookmarks.first().second > 0) {
                        bookmarks.add(0, Pair(book.title, 0))
                    }

                    val updatedEntities = buildNonPdfChapterEntities(bookId, bookmarks, allPages, totalPages)
                    dao.saveBookWithChapters(book, updatedEntities)
                    applyUpdatedChaptersToState(bookId, book, updatedEntities)
                }
            }
        }
    }

    fun deleteManualChapter(bookId: String, chapterIndex: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val book = dao.getBookById(bookId) ?: return@launch
            val isPdf = book.format.equals("PDF", true) || book.localFilePath?.endsWith(".pdf", ignoreCase = true) == true
            val currentChapters = loadCurrentBookChapters(bookId)
            if (currentChapters.size <= 1 || chapterIndex !in currentChapters.indices) return@launch

            if (isPdf) {
                val totalPages = book.pagesCount.coerceAtLeast(1)
                val bookmarks = extractPdfBookmarks(currentChapters)
                bookmarks.removeAt(chapterIndex)
                if (bookmarks.isNotEmpty() && bookmarks.first().startPageZero > 0) {
                    bookmarks[0] = bookmarks[0].copy(startPageZero = 0)
                }

                saveAndApplyPdfChapters(bookId, book, bookmarks, totalPages)
            } else {
                val newChapters = currentChapters.toMutableList()
                if (chapterIndex > 0) {
                    val prev = newChapters[chapterIndex - 1]
                    val cur = newChapters[chapterIndex]
                    newChapters[chapterIndex - 1] = prev.copy(pages = prev.pages + cur.pages)
                    newChapters.removeAt(chapterIndex)
                } else {
                    val cur = newChapters[0]
                    val next = newChapters[1]
                    newChapters[1] = next.copy(pages = cur.pages + next.pages)
                    newChapters.removeAt(0)
                }
                val updatedEntities = newChapters.mapIndexed { idx, ch ->
                    val pagesArr = JSONArray()
                    ch.pages.forEach { pagesArr.put(it) }
                    BookChapterEntity(
                        bookId = bookId,
                        chapterIndex = idx,
                        title = ch.title,
                        pagesJson = pagesArr.toString(),
                        illustrationsJson = "[]"
                    )
                }
                dao.saveBookWithChapters(book, updatedEntities)
                applyUpdatedChaptersToState(bookId, book, updatedEntities)
            }
        }
    }

    private suspend fun applyUpdatedChaptersToState(
        bookId: String,
        updatedBook: BookEntity,
        updatedEntities: List<BookChapterEntity>
    ) {
        if (_uiState.value.activeBookId == bookId) {
            val liveChs = updatedEntities.map { ce ->
                val pArr = runCatching { JSONArray(ce.pagesJson) }.getOrNull() ?: JSONArray()
                val pList = (0 until pArr.length()).map { pArr.optString(it) }
                BookChapter(index = ce.chapterIndex, title = sanitizeBookOrChapterText(ce.title), pages = pList)
            }
            val currentGlobalPage = computeGlobalPageMetrics(
                _uiState.value.activeBookChapters,
                _uiState.value.activeChapterIndex,
                _uiState.value.activePageIndex
            ).currentGlobalPage
            val targetGlobalZero = (currentGlobalPage - 1).coerceAtLeast(0)
            val resolved = getPageContentAtGlobalZeroIndex(liveChs, targetGlobalZero)
            val resolvedActiveChIdx = (resolved?.first ?: 0).coerceIn(0, (liveChs.size - 1).coerceAtLeast(0))
            val resolvedActivePgIdx = (resolved?.second ?: 0).coerceIn(0, (liveChs.getOrNull(resolvedActiveChIdx)?.pages?.size ?: 1) - 1)

            _uiState.update {
                it.copy(
                    activeBookChapters = liveChs,
                    activeChapterIndex = resolvedActiveChIdx,
                    activePageIndex = resolvedActivePgIdx
                )
            }
            dao.updateBook(updatedBook.copy(savedChapterIndex = resolvedActiveChIdx, savedPageIndex = resolvedActivePgIdx))
        }
    }

    fun turnReaderPage(deltaSteps: Int) {
        val st = _uiState.value
        val chapters = st.activeBookChapters
        if (chapters.isEmpty()) return
        val isSpread = preferences.value.readingFlowMode == ReadingFlowMode.SPREAD
        val isBilingualSpread = isSpread && st.isTranslationActive && st.translationMode.equals("bilingual", ignoreCase = true)
        val metrics = computeGlobalPageMetrics(chapters, st.activeChapterIndex, st.activePageIndex)
        val currentZero = metrics.currentGlobalPage - 1
        val spreadStart = if (isSpread && !isBilingualSpread) (currentZero / 2) * 2 else currentZero
        val maxSpread = if (isSpread && !isBilingualSpread) (((metrics.totalBookPages - 1) / 2) * 2).coerceAtLeast(0) else (metrics.totalBookPages - 1).coerceAtLeast(0)
        val targetZero = if (isSpread && !isBilingualSpread) {
            if (deltaSteps > 0) (spreadStart + 2 * deltaSteps).coerceAtMost(maxSpread)
            else (spreadStart + 2 * deltaSteps).coerceAtLeast(0)
        } else {
            currentZero + deltaSteps
        }

        if (targetZero < 0) {
            if (currentZero > 0) {
                jumpToChapterAndPage(0, 0)
            }
            return
        }

        if (targetZero >= metrics.totalBookPages) {
            val bookId = st.activeBookId ?: return
            viewModelScope.launch {
                val book = dao.getBookById(bookId) ?: return@launch
                val nowMillis = System.currentTimeMillis()
                dao.updateBook(
                    book.copy(
                        progress = 100,
                        completedAllPages = true,
                        onTheGo = false,
                        lastReadAtMillis = nowMillis
                    )
                )
                recordDailyReadingMinute(0, 1)
                val isId = preferences.value.lang != "en"
                showToast(
                    if (isId) "Selamat! \"${book.title}\" selesai dibaca (100%)"
                    else "\"${book.title}\" finished (100%)"
                )
            }
            return
        }

        val target = getPageContentAtGlobalZeroIndex(chapters, targetZero) ?: return
        jumpToChapterAndPage(target.first, target.second, recordTurn = true)
    }

    fun jumpToGlobalPage(targetZero: Int) {
        val st = _uiState.value
        val chapters = st.activeBookChapters
        if (chapters.isEmpty()) return
        val metrics = computeGlobalPageMetrics(chapters, st.activeChapterIndex, st.activePageIndex)

        if (targetZero < 0) {
            jumpToChapterAndPage(0, 0, recordTurn = true)
            return
        }

        if (targetZero >= metrics.totalBookPages) {
            val bookId = st.activeBookId ?: return
            viewModelScope.launch {
                val book = dao.getBookById(bookId) ?: return@launch
                val nowMillis = System.currentTimeMillis()
                dao.updateBook(
                    book.copy(
                        progress = 100,
                        completedAllPages = true,
                        onTheGo = false,
                        lastReadAtMillis = nowMillis
                    )
                )
                recordDailyReadingMinute(0, 1)
                val isId = preferences.value.lang != "en"
                showToast(
                    if (isId) "Selamat! \"${book.title}\" selesai dibaca (100%)"
                    else "\"${book.title}\" finished (100%)"
                )
            }
            return
        }

        val target = getPageContentAtGlobalZeroIndex(chapters, targetZero) ?: return
        jumpToChapterAndPage(target.first, target.second, recordTurn = true)
    }

    fun jumpToChapterAndPage(chapterIndex: Int, pageIndex: Int, recordTurn: Boolean = false) {
        val st = _uiState.value
        val chapters = st.activeBookChapters
        if (chapters.isEmpty()) return
        val safeCh = chapterIndex.coerceIn(0, chapters.size - 1)
        val safePg = pageIndex.coerceIn(0, (chapters[safeCh].pages.size - 1).coerceAtLeast(0))
        val newMetrics = computeGlobalPageMetrics(chapters, safeCh, safePg)

        _uiState.update {
            it.copy(
                activeChapterIndex = safeCh,
                activePageIndex = safePg,
                showReaderTocDrawer = false,
                showReaderNotesDrawer = false
            )
        }

        val bookId = st.activeBookId ?: return
        viewModelScope.launch {
            val book = dao.getBookById(bookId) ?: return@launch
            val nowMillis = System.currentTimeMillis()
            dao.updateBook(
                book.copy(
                    savedChapterIndex = safeCh,
                    savedPageIndex = safePg,
                    progress = newMetrics.globalPct,
                    completedAllPages = newMetrics.isAtVeryLastPage,
                    onTheGo = !newMetrics.isAtVeryLastPage,
                    lastReadAtMillis = nowMillis
                )
            )
            if (recordTurn) {
                recordDailyReadingMinute(0, 1)
            }
        }
    }

    fun toggleReaderFullView() {
        _uiState.update { it.copy(readerFullView = !it.readerFullView) }
    }

    fun setShowReaderTypeSheet(show: Boolean, tab: String? = null) {
        _uiState.update {
            it.copy(
                showReaderTypeSheet = show,
                activeReaderSheetTab = tab ?: it.activeReaderSheetTab
            )
        }
    }

    fun setShowReaderTocDrawer(show: Boolean) {
        _uiState.update { it.copy(showReaderTocDrawer = show) }
    }

    fun setShowReaderNotesDrawer(show: Boolean) {
        _uiState.update { it.copy(showReaderNotesDrawer = show) }
    }

    fun openIllustrationViewer(index: Int) {
        val illusts = _uiState.value.activeBookIllustrations
        if (illusts.isEmpty()) return
        val safeIdx = ((index % illusts.size) + illusts.size) % illusts.size
        _uiState.update {
            it.copy(
                showIllustViewer = true,
                activeIllustIndex = safeIdx,
                illustZoom = 1f,
                showReaderTocDrawer = false
            )
        }
    }

    fun setIllustZoom(zoom: Float) {
        _uiState.update { it.copy(illustZoom = zoom) }
    }

    fun closeIllustrationViewer() {
        _uiState.update { it.copy(showIllustViewer = false) }
    }

    // -------------------------------------------------------------------------
    // HIGHLIGHTS & NOTES IN ROOM DATABASE
    // -------------------------------------------------------------------------
    fun saveHighlightNote(
        quoteText: String,
        noteText: String,
        colorKey: HighlightColorKey,
        existingId: String? = null,
        startCharIndex: Int = -1,
        endCharIndex: Int = -1
    ) {
        val st = _uiState.value
        val bookId = st.activeBookId ?: return
        val isId = preferences.value.lang != "en"
        val chTitle = st.activeBookChapters.getOrNull(st.activeChapterIndex)?.title
            ?: (if (isId) "Bab ${st.activeChapterIndex + 1}" else "Chapter ${st.activeChapterIndex + 1}")
        viewModelScope.launch {
            val entity = HighlightNoteEntity(
                id = existingId ?: "hl-${System.currentTimeMillis()}",
                bookId = bookId,
                chapterIndex = st.activeChapterIndex,
                pageIndex = st.activePageIndex,
                chapterTitle = chTitle,
                quoteText = quoteText.trim(),
                noteText = noteText.trim(),
                colorKey = colorKey.key,
                startCharIndex = startCharIndex,
                endCharIndex = endCharIndex
            )
            dao.upsertHighlight(entity)
            showToast(if (isId) "Catatan & sorotan disimpan" else "Highlight & note saved")
        }
    }

    fun deleteHighlightNote(highlightId: String) {
        viewModelScope.launch {
            dao.deleteHighlightById(highlightId)
            val isId = preferences.value.lang != "en"
            showToast(if (isId) "Catatan dihapus" else "Note deleted")
        }
    }

    fun togglePageBookmark(
        bookId: String,
        pageNumber: Int,
        chapterIndex: Int,
        chapterTitle: String,
        snippetText: String
    ) {
        val isId = preferences.value.lang != "en"
        viewModelScope.launch {
            val existing = allBookmarks.value.find { it.bookId == bookId && it.pageNumber == pageNumber }
            if (existing != null) {
                dao.deleteBookmarkById(existing.id)
                showToast(if (isId) "Penanda halaman $pageNumber dihapus" else "Page $pageNumber bookmark removed")
            } else {
                val entity = PageBookmarkEntity(
                    id = "bm-${System.currentTimeMillis()}-${pageNumber}",
                    bookId = bookId,
                    pageNumber = pageNumber,
                    chapterIndex = chapterIndex,
                    chapterTitle = chapterTitle.trim(),
                    snippetText = snippetText.trim().take(120),
                    createdAtMillis = System.currentTimeMillis()
                )
                dao.upsertBookmark(entity)
                showToast(if (isId) "Halaman $pageNumber ditandai" else "Page $pageNumber bookmarked")
            }
        }
    }

    fun deletePageBookmark(bookmarkId: String) {
        val isId = preferences.value.lang != "en"
        viewModelScope.launch {
            dao.deleteBookmarkById(bookmarkId)
            showToast(if (isId) "Penanda halaman dihapus" else "Bookmark removed")
        }
    }

    // -------------------------------------------------------------------------
    // REAL LOCAL FILE IMPORT (.EPUB via Jsoup, .PDF via PdfRenderer, .TXT, .JSON)
    // Adds cover immediately to Home & Library with a minimal bottom progress bar!
    // Never auto-opens the reader upon import.
    // -------------------------------------------------------------------------
    private fun queryFileName(uri: Uri): String {
        var name = "book_import"
        val cursor: Cursor? = appContext.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = it.getString(idx) ?: name
            }
        }
        return name
    }

    private fun setBookImportProgress(bookId: String, pct: Int?) {
        _uiState.update { cur ->
            val nextMap = cur.importingBooksProgress.toMutableMap()
            if (pct == null || pct >= 100) {
                nextMap.remove(bookId)
            } else {
                nextMap[bookId] = pct.coerceIn(1, 99)
            }
            cur.copy(importingBooksProgress = nextMap)
        }
    }

    fun importLocalBookOrBackupFile(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val fileName = queryFileName(uri)
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val isId = preferences.value.lang != "en"
            val targetShelf = if (preferences.value.libShelf.equals("all", true) || preferences.value.libShelf.isBlank()) {
                "eReader"
            } else {
                canonicalShelfKey(preferences.value.libShelf)
            }

            // Validation: Only import supported files (PDF, EPUB, TXT) or JSON backup
            if (ext !in setOf("pdf", "epub", "txt", "json")) {
                showToast(
                    if (isId) "Format file tidak didukung ($ext). Hanya .pdf, .epub, dan .txt."
                    else "Unsupported file format ($ext). Only .pdf, .epub, and .txt."
                )
                return@launch
            }

            // Close AddBookSheet immediately so user sees the book card appear with its cover & progress bar in Home / Library
            _uiState.update { it.copy(showAddBookSheet = false) }

            var trackedBookId: String? = null
            try {
                when (ext) {
                    "pdf" -> {
                        val bundle = PdfNativeRenderer.importPdfDocumentWithProgress(
                            context = appContext,
                            uri = uri,
                            fallbackFileName = fileName,
                            targetShelf = targetShelf,
                            isEn = !isId,
                            onInitialBookCreated = { partialBook ->
                                trackedBookId = partialBook.id
                                dao.upsertBook(partialBook)
                            },
                            onProgressUpdate = { bId, pct ->
                                trackedBookId = bId
                                setBookImportProgress(bId, pct)
                            }
                        )
                        dao.saveBookWithChapters(bundle.book, bundle.chapters)
                        indexPdfChaptersAndIllustrations(bundle.book)
                        showToast(
                            if (isId) "\"${bundle.book.title}\" ditambahkan ke pustaka"
                            else "\"${bundle.book.title}\" added to library"
                        )
                    }
                    "epub" -> {
                        val bundle = EpubNativeParser.importAndParseEpubWithProgress(
                            context = appContext,
                            uri = uri,
                            fallbackFileName = fileName,
                            targetShelf = targetShelf,
                            isEn = !isId,
                            onInitialBookCreated = { partialBook ->
                                trackedBookId = partialBook.id
                                dao.upsertBook(partialBook)
                            },
                            onProgressUpdate = { bId, pct ->
                                trackedBookId = bId
                                setBookImportProgress(bId, pct)
                            }
                        )
                        dao.saveBookWithChapters(bundle.book, bundle.chapters)
                        showToast(
                            if (isId) "\"${bundle.book.title}\" ditambahkan ke pustaka"
                            else "\"${bundle.book.title}\" added to library"
                        )
                    }
                    "txt" -> {
                        val bundle = TxtNativeParser.importAndParseTxtWithProgress(
                            context = appContext,
                            uri = uri,
                            fallbackFileName = fileName,
                            targetShelf = targetShelf,
                            onInitialBookCreated = { partialBook ->
                                trackedBookId = partialBook.id
                                dao.upsertBook(partialBook)
                            },
                            onProgressUpdate = { bId, pct ->
                                trackedBookId = bId
                                setBookImportProgress(bId, pct)
                            }
                        )
                        dao.saveBookWithChapters(bundle.book, bundle.chapters)
                        showToast(
                            if (isId) "\"${bundle.book.title}\" ditambahkan ke pustaka"
                            else "\"${bundle.book.title}\" added to library"
                        )
                    }
                    "json" -> {
                        val rawJson = appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                        restoreBackupFromJsonString(rawJson)
                        showToast(if (isId) "Cadangan lokal dipulihkan" else "Local backup restored")
                    }
                    else -> {
                        showToast(if (isId) "Format berkas tidak didukung (.$ext)" else "Unsupported file format (.$ext)")
                    }
                }
            } catch (t: Throwable) {
                if (t is OutOfMemoryError) {
                    System.gc()
                }
                trackedBookId?.let { badId ->
                    runCatching { dao.deleteBooksByIds(listOf(badId)) }
                }
                showToast(if (isId) "Gagal mengimpor: ${t.localizedMessage ?: "Kesalahan berkas"}" else "Import failed: ${t.localizedMessage ?: "File error"}")
            } finally {
                trackedBookId?.let { setBookImportProgress(it, null) }
            }
        }
    }

    private val isFolderScanning = AtomicBoolean(false)
    private val pendingRescanForce = AtomicBoolean(false)

    private fun collectDocumentFilesRecursively(
        dir: DocumentFile,
        currentDepth: Int = 0,
        maxDepth: Int = 6,
        out: MutableList<DocumentFile>
    ) {
        if (currentDepth > maxDepth || !dir.isDirectory) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (child in children) {
            val name = child.name.orEmpty()
            if (name.startsWith(".")) continue
            if (child.isDirectory) {
                collectDocumentFilesRecursively(child, currentDepth + 1, maxDepth, out)
            } else if (child.isFile) {
                val ext = name.substringAfterLast('.', "").lowercase()
                if (ext in setOf("epub", "pdf", "txt")) {
                    out.add(child)
                }
            }
        }
    }

    private var lastAutoImportScanAtMillis = 0L

    fun scanAutoImportFolder(force: Boolean = false) {
        val prefs = preferences.value
        if (!prefs.autoImportFolderEnabled && !force) return
        val now = System.currentTimeMillis()
        if (!force && (now - lastAutoImportScanAtMillis) < 60_000L) return
        if (!isFolderScanning.compareAndSet(false, true)) {
            if (force) {
                pendingRescanForce.set(true)
            }
            return
        }
        lastAutoImportScanAtMillis = now

        viewModelScope.launch(Dispatchers.IO) {
            try {
                orphanSweepDone.await()
                val currentPrefs = prefsRepo.preferencesFlow.first()
                val isEn = currentPrefs.lang == "en"
                val existingBooks = dao.getAllBooks()
                val alreadyImported = currentPrefs.importedFileSignatures.toMutableSet()
                val newlyImportedSignatures = mutableSetOf<String>()

                data class PendingBookFile(
                    val fileName: String,
                    val fileSize: Long,
                    val uri: Uri,
                    val ext: String,
                    val sig: String
                )
                val pendingFiles = mutableListOf<PendingBookFile>()

                // Raw-path scanning: only when All Files Access is granted (or pre-R)
                if (hasAllFilesAccess()) {
                    val defaultDir = File(Environment.getExternalStorageDirectory(), "Llunevie/Books")
                    runCatching { defaultDir.mkdirs() }

                    val dirsToScan = mutableListOf<File>()
                    dirsToScan.add(defaultDir)
                    if (currentPrefs.autoImportFolderPath.isNotBlank()) {
                        val customDir = File(currentPrefs.autoImportFolderPath)
                        if (customDir.exists() && customDir.absolutePath != defaultDir.absolutePath) {
                            dirsToScan.add(customDir)
                        }
                    }

                    for (dir in dirsToScan) {
                        if (!dir.exists() || !dir.isDirectory) continue
                        val files = runCatching {
                            dir.walkTopDown().maxDepth(6).onEnter { !it.name.startsWith(".") }.filter { it.isFile }.toList()
                        }.getOrNull() ?: continue
                        for (file in files) {
                            val name = file.name
                            if (name.startsWith(".")) continue
                            val ext = file.extension.lowercase()
                            if (ext !in setOf("epub", "pdf", "txt")) continue
                            val size = file.length()
                            val sig = "${file.absolutePath}:$size"
                            val legacySig = "$name:$size"
                            if (sig in alreadyImported || legacySig in alreadyImported) continue

                            val matchesRoom = existingBooks.any { b ->
                                b.localFilePath == file.absolutePath ||
                                    (b.localFilePath != null && runCatching { File(b.localFilePath).name.equals(name, ignoreCase = true) && File(b.localFilePath).length() == size }.getOrDefault(false))
                            }
                            if (matchesRoom) {
                                alreadyImported.add(sig)
                                newlyImportedSignatures.add(sig)
                                continue
                            }

                            if (pendingFiles.none { it.sig == sig }) {
                                pendingFiles.add(PendingBookFile(name, size, Uri.fromFile(file), ext, sig))
                            }
                        }
                    }
                }

                if (!currentPrefs.autoImportFolderUri.isNullOrBlank()) {
                    runCatching {
                        val treeUri = Uri.parse(currentPrefs.autoImportFolderUri)
                        val docDir = DocumentFile.fromTreeUri(appContext, treeUri)
                        if (docDir != null && docDir.isDirectory) {
                            val collectedDocFiles = mutableListOf<DocumentFile>()
                            collectDocumentFilesRecursively(docDir, 0, 6, collectedDocFiles)
                            for (docFile in collectedDocFiles) {
                                val name = docFile.name.orEmpty()
                                if (name.startsWith(".")) continue
                                val ext = name.substringAfterLast('.', "").lowercase()
                                if (ext !in setOf("epub", "pdf", "txt")) continue
                                val size = docFile.length()
                                val uriStr = docFile.uri.toString()
                                val sig = "$uriStr:$size"
                                val legacySig = "$name:$size"
                                if (sig in alreadyImported || legacySig in alreadyImported) continue

                                val matchesRoom = existingBooks.any { b ->
                                    b.localFilePath == uriStr ||
                                        (b.localFilePath != null && runCatching { File(b.localFilePath).name.equals(name, ignoreCase = true) && File(b.localFilePath).length() == size }.getOrDefault(false))
                                }
                                if (matchesRoom) {
                                    alreadyImported.add(sig)
                                    newlyImportedSignatures.add(sig)
                                    continue
                                }

                                if (pendingFiles.none { it.sig == sig }) {
                                    pendingFiles.add(PendingBookFile(name, size, docFile.uri, ext, sig))
                                }
                            }
                        }
                    }
                }

                var successCount = 0
                for ((index, item) in pendingFiles.withIndex()) {
                    val batchProgress = if (pendingFiles.size > 1) {
                        ((index * 100) / pendingFiles.size).coerceIn(1, 99)
                    } else null
                    var trackedBookId: String? = null
                    try {
                        when (item.ext) {
                            "pdf" -> {
                                val bundle = PdfNativeRenderer.importPdfDocumentWithProgress(
                                    context = appContext,
                                    uri = item.uri,
                                    fallbackFileName = item.fileName,
                                    targetShelf = "eReader",
                                    isEn = isEn,
                                    onInitialBookCreated = { partialBook ->
                                        trackedBookId = partialBook.id
                                        dao.upsertBook(partialBook)
                                        if (batchProgress != null) {
                                            setBookImportProgress(partialBook.id, batchProgress)
                                        }
                                    },
                                    onProgressUpdate = { bId, pct ->
                                        trackedBookId = bId
                                        val displayPct = batchProgress ?: pct
                                        setBookImportProgress(bId, displayPct)
                                    }
                                )
                                dao.saveBookWithChapters(bundle.book, bundle.chapters)
                                indexPdfChaptersAndIllustrations(bundle.book)
                                newlyImportedSignatures.add(item.sig)
                                alreadyImported.add(item.sig)
                                successCount++
                            }
                            "epub" -> {
                                val bundle = EpubNativeParser.importAndParseEpubWithProgress(
                                    context = appContext,
                                    uri = item.uri,
                                    fallbackFileName = item.fileName,
                                    targetShelf = "eReader",
                                    isEn = isEn,
                                    onInitialBookCreated = { partialBook ->
                                        trackedBookId = partialBook.id
                                        dao.upsertBook(partialBook)
                                        if (batchProgress != null) {
                                            setBookImportProgress(partialBook.id, batchProgress)
                                        }
                                    },
                                    onProgressUpdate = { bId, pct ->
                                        trackedBookId = bId
                                        val displayPct = batchProgress ?: pct
                                        setBookImportProgress(bId, displayPct)
                                    }
                                )
                                dao.saveBookWithChapters(bundle.book, bundle.chapters)
                                newlyImportedSignatures.add(item.sig)
                                alreadyImported.add(item.sig)
                                successCount++
                            }
                            "txt" -> {
                                val bundle = TxtNativeParser.importAndParseTxtWithProgress(
                                    context = appContext,
                                    uri = item.uri,
                                    fallbackFileName = item.fileName,
                                    targetShelf = "eReader",
                                    onInitialBookCreated = { partialBook ->
                                        trackedBookId = partialBook.id
                                        dao.upsertBook(partialBook)
                                        if (batchProgress != null) {
                                            setBookImportProgress(partialBook.id, batchProgress)
                                        }
                                    },
                                    onProgressUpdate = { bId, pct ->
                                        trackedBookId = bId
                                        val displayPct = batchProgress ?: pct
                                        setBookImportProgress(bId, displayPct)
                                    }
                                )
                                dao.saveBookWithChapters(bundle.book, bundle.chapters)
                                newlyImportedSignatures.add(item.sig)
                                alreadyImported.add(item.sig)
                                successCount++
                            }
                        }

                        if (successCount % 10 == 0 && newlyImportedSignatures.isNotEmpty()) {
                            prefsRepo.addImportedSignatures(newlyImportedSignatures)
                        }
                    } catch (t: Throwable) {
                        if (t is OutOfMemoryError) {
                            System.gc()
                        }
                        trackedBookId?.let { badId ->
                            runCatching { dao.deleteBooksByIds(listOf(badId)) }
                        }
                    } finally {
                        trackedBookId?.let { setBookImportProgress(it, null) }
                    }
                }

                if (newlyImportedSignatures.isNotEmpty()) {
                    prefsRepo.addImportedSignatures(newlyImportedSignatures)
                }

                if (successCount > 0) {
                    showToast(
                        if (isEn) "$successCount new book(s) imported from folder"
                        else "$successCount buku baru diimpor dari folder"
                    )
                }
            } finally {
                isFolderScanning.set(false)
                if (pendingRescanForce.getAndSet(false)) {
                    scanAutoImportFolder(force = true)
                }
            }
        }
    }

    fun setAutoImportFolderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            prefsRepo.setAutoImportFolderEnabled(enabled)
            if (enabled && hasAllFilesAccess()) {
                val dir = File(Environment.getExternalStorageDirectory(), "Llunevie/Books")
                runCatching { dir.mkdirs() }
                scanAutoImportFolder(force = true)
            }
        }
    }

    fun setAutoImportFolder(path: String, uri: String? = null) {
        viewModelScope.launch {
            prefsRepo.setAutoImportFolder(path, uri)
            scanAutoImportFolder(force = true)
        }
    }

    fun completeOnboarding(autoImportEnabled: Boolean, customPath: String? = null, customUri: String? = null) {
        viewModelScope.launch {
            if (!customPath.isNullOrBlank()) {
                prefsRepo.setAutoImportFolder(customPath, customUri)
            }
            prefsRepo.setAutoImportFolderEnabled(autoImportEnabled)
            prefsRepo.setHasCompletedOnboarding(true)
            if (autoImportEnabled) {
                val dir = File(Environment.getExternalStorageDirectory(), "Llunevie/Books")
                runCatching { dir.mkdirs() }
                scanAutoImportFolder(force = true)
            }
        }
    }

    fun skipOnboarding() {
        viewModelScope.launch {
            prefsRepo.setAutoImportFolderEnabled(false)
            prefsRepo.setHasCompletedOnboarding(true)
        }
    }

    // -------------------------------------------------------------------------
    // PARALLEL MULTI-API BOOK SEARCH (GRAMEDIA, GOODREADS, GOOGLE BOOKS, OPEN LIBRARY, JIKAN)
    // -------------------------------------------------------------------------
    fun setShowAddBookSheet(show: Boolean, tab: String = "search") {
        _uiState.update {
            it.copy(
                showAddBookSheet = show,
                addModalTab = tab,
                showFullCameraScanner = tab == "isbn" || tab == "batch",
                cameraMode = if (tab == "batch") "batch" else "isbn"
            )
        }
    }

    fun setCatalogSearchQuery(query: String) {
        _uiState.update { it.copy(catalogSearchQuery = query) }
        searchJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < 2) {
            _uiState.update { it.copy(catalogSearchResults = emptyList(), isCatalogSearching = false) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(260L)
            _uiState.update { it.copy(catalogSearchResults = emptyList(), isCatalogSearching = true) }

            val digitsOnly = trimmed.replace(Regex("[^0-9Xx]"), "").uppercase()
            val isIsbn = digitsOnly.length in 10..13 && trimmed.all { it.isDigit() || it == '-' || it == ' ' || it == 'X' || it == 'x' }

            val accumulated = mutableListOf<CatalogBookCandidate>()
            fun mergeNewHits(hits: List<CatalogBookCandidate>) {
                if (hits.isEmpty()) return
                synchronized(accumulated) {
                    for (h in hits) {
                        val dup = accumulated.any {
                            (it.isbn != "—" && it.isbn == h.isbn) ||
                                (it.title.equals(h.title, true) && it.author.equals(h.author, true) && it.year == h.year)
                        }
                        if (!dup) accumulated.add(h)
                    }
                    _uiState.update { it.copy(catalogSearchResults = accumulated.toList()) }
                }
            }

            val tasks = if (isIsbn) {
                listOf(
                    launch { mergeNewHits(apiRepo.searchGramedia(digitsOnly)) },
                    launch { mergeNewHits(apiRepo.searchGoogleBooks("isbn:$digitsOnly", "")) },
                    launch { mergeNewHits(apiRepo.searchGoogleBooks(digitsOnly, "")) },
                    launch { mergeNewHits(apiRepo.searchOpenLibrary(digitsOnly, isIsbn = true)) },
                    launch { mergeNewHits(apiRepo.searchGoodreadsAutocomplete(digitsOnly)) }
                )
            } else {
                listOf(
                    launch { mergeNewHits(apiRepo.searchGramedia(trimmed)) },
                    launch { mergeNewHits(apiRepo.searchGoodreadsAutocomplete(trimmed)) },
                    launch { mergeNewHits(apiRepo.searchGoogleBooks(trimmed, "id")) },
                    launch { mergeNewHits(apiRepo.searchGoogleBooks(trimmed, "")) },
                    launch { mergeNewHits(apiRepo.searchOpenLibrary(trimmed, isIsbn = false)) },
                    launch { mergeNewHits(apiRepo.searchJikanLightNovels(trimmed)) }
                )
            }

            tasks.forEach { it.join() }
            _uiState.update { it.copy(isCatalogSearching = false) }
        }
    }

    fun lookupIsbnFromScanner(rawIsbn: String) {
        val clean = rawIsbn.replace(Regex("[^0-9Xx]"), "").uppercase()
        if (clean.length !in 10..13) return

        val isBatch = _uiState.value.cameraMode == "batch"
        val existingInBatch = isBatch && _uiState.value.batchScanQueue.any { it.isbn == clean }
        if (existingInBatch) return

        val isEn = preferences.value.lang == "en"
        val currentYear = SimpleDateFormat("yyyy", Locale.US).format(Date())
        val fallbackCandidate = CatalogBookCandidate(
            id = "isbn-$clean-${System.currentTimeMillis()}",
            title = if (isEn) "Searching ISBN $clean..." else "Mencari ISBN $clean...",
            author = "ISBN $clean",
            year = currentYear,
            pages = 280,
            rating = "4.5",
            edition = "Edisi Fisik",
            publisher = "Penerbit",
            lang = if (isEn) "en" else "id",
            isbn = clean,
            category = "Novel",
            coverUrl = null,
            coverBgHex = 0xFF1E3A5FL,
            synopsis = "Buku dengan nomor ISBN $clean.",
            status = BatchLookupStatus.SEARCHING
        )

        // 0ms instant insertion into batch queue
        _uiState.update { cur ->
            val newBatch = if (isBatch) {
                listOf(fallbackCandidate) + cur.batchScanQueue
            } else cur.batchScanQueue
            cur.copy(
                isLookingUpIsbn = true,
                lastScannedCandidate = fallbackCandidate,
                batchScanQueue = newBatch
            )
        }

        // Sub-300ms First-Response Parallel Lookup (No awaitAll blocking!)
        var bestCandidate: CatalogBookCandidate? = null
        val syncLock = Any()

        fun onCandidateMatched(candidate: CatalogBookCandidate) {
            viewModelScope.launch {
                var finalCandidate = candidate.copy(status = BatchLookupStatus.FOUND)
                if (!candidate.coverUrl.isNullOrBlank()) {
                    val downloaded = downloadCoverAndExtractSpine(candidate.coverUrl, clean)
                    if (downloaded != null) {
                        val (localPath, spineHex) = downloaded
                        finalCandidate = finalCandidate.copy(
                            coverUrl = localPath,
                            coverBgHex = spineHex
                        )
                    }
                }

                val shouldUpdate: Boolean
                synchronized(syncLock) {
                    val prev = bestCandidate
                    shouldUpdate = prev == null ||
                        (!finalCandidate.coverUrl.isNullOrBlank() && prev.coverUrl.isNullOrBlank()) ||
                        (finalCandidate.synopsis.length > prev.synopsis.length && !finalCandidate.synopsis.startsWith("Buku dengan nomor"))
                    if (shouldUpdate) {
                        bestCandidate = finalCandidate
                    }
                }
                if (shouldUpdate) {
                    _uiState.update { cur ->
                        val updatedBatch = if (isBatch) {
                            cur.batchScanQueue.map { item ->
                                if (item.isbn == clean) finalCandidate else item
                            }
                        } else cur.batchScanQueue

                        cur.copy(
                            isLookingUpIsbn = false,
                            lastScannedCandidate = if (cur.lastScannedCandidate?.isbn == clean || cur.lastScannedCandidate == null) finalCandidate else cur.lastScannedCandidate,
                            batchScanQueue = updatedBatch
                        )
                    }
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            withTimeoutOrNull(8_000L) {
                val jobs = listOf(
                    launch {
                        val list = runCatching { apiRepo.searchGoogleBooks("isbn:$clean", "") }.getOrDefault(emptyList())
                        list.firstOrNull()?.let { onCandidateMatched(it) }
                    },
                    launch {
                        val list = runCatching { apiRepo.searchOpenLibrary(clean, isIsbn = true) }.getOrDefault(emptyList())
                        list.firstOrNull()?.let { onCandidateMatched(it) }
                    },
                    launch {
                        val list = runCatching { apiRepo.searchGramedia(clean) }.getOrDefault(emptyList())
                        list.firstOrNull()?.let { onCandidateMatched(it) }
                    },
                    launch {
                        val list = runCatching { apiRepo.searchGoodreadsAutocomplete(clean) }.getOrDefault(emptyList())
                        list.firstOrNull()?.let { onCandidateMatched(it) }
                    }
                )
                jobs.joinAll()
            }

            val candidate = bestCandidate
            if (candidate == null || candidate.status != BatchLookupStatus.FOUND) {
                val notFoundCandidate = CatalogBookCandidate(
                    id = fallbackCandidate.id,
                    title = if (isEn) "ISBN Not Found" else "ISBN Tidak Ditemukan",
                    author = clean,
                    year = currentYear,
                    pages = 0,
                    rating = "—",
                    edition = "—",
                    publisher = "—",
                    lang = if (isEn) "en" else "id",
                    isbn = clean,
                    category = "—",
                    coverUrl = null,
                    coverBgHex = 0xFF1E3A5FL,
                    synopsis = "",
                    status = BatchLookupStatus.NOT_FOUND
                )
                _uiState.update { cur ->
                    val updatedBatch = if (isBatch) {
                        cur.batchScanQueue.map { item ->
                            if (item.isbn == clean) notFoundCandidate else item
                        }
                    } else cur.batchScanQueue
                    cur.copy(
                        isLookingUpIsbn = false,
                        lastScannedCandidate = if (cur.lastScannedCandidate?.isbn == clean || cur.lastScannedCandidate == null) notFoundCandidate else cur.lastScannedCandidate,
                        batchScanQueue = updatedBatch
                    )
                }
            } else {
                _uiState.update { it.copy(isLookingUpIsbn = false) }
            }
        }
    }

    suspend fun downloadCoverAndExtractSpine(coverUrl: String, isbn: String): Pair<String, Long>? = withContext(Dispatchers.IO) {
        runCatching {
            val coversDir = File(appContext.filesDir, "covers").apply { mkdirs() }
            val targetFile = File(coversDir, "isbn_${isbn}.jpg")
            var currentUrl = coverUrl.trim().replace("http://", "https://", ignoreCase = true)
            var hops = 0
            var finalConn: HttpURLConnection? = null

            while (hops < 5) {
                val url = URL(currentUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6000
                    readTimeout = 8000
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Mobile) Llunevie/1.0")
                }
                val code = conn.responseCode
                if (code in 301..308) {
                    val location = conn.getHeaderField("Location")
                    if (!location.isNullOrBlank()) {
                        currentUrl = if (location.startsWith("http://") || location.startsWith("https://")) {
                            location.replace("http://", "https://", ignoreCase = true)
                        } else {
                            URL(url, location).toString().replace("http://", "https://", ignoreCase = true)
                        }
                        conn.disconnect()
                        hops++
                        continue
                    }
                }
                if (code in 200..299) {
                    finalConn = conn
                    break
                } else {
                    conn.disconnect()
                    break
                }
            }

            if (finalConn != null) {
                finalConn.inputStream.use { input ->
                    FileOutputStream(targetFile).use { output ->
                        input.copyTo(output)
                    }
                }
                finalConn.disconnect()
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = 4 }
                val bmp = BitmapFactory.decodeFile(targetFile.absolutePath, decodeOpts)
                if (bmp != null) {
                    val spineColor = SpineColorExtractor.extractDominantSpineHex(bmp)
                    bmp.recycle()
                    Pair(targetFile.absolutePath, spineColor)
                } else {
                    Pair(targetFile.absolutePath, 0xFF1E3A5FL)
                }
            } else null
        }.getOrNull()
    }

    fun updateBookSpineColor(bookId: String, extractedHex: Long) {
        viewModelScope.launch {
            dao.updateBookSpineColorById(bookId, extractedHex)
        }
    }

    fun openManualEntryWithIsbn(prefillIsbn: String) {
        _uiState.update {
            it.copy(
                showFullCameraScanner = false,
                showAddBookSheet = true,
                addModalTab = "manual",
                manualBookPrefillIsbn = prefillIsbn
            )
        }
    }

    fun commitResolvedBatchOnly(shelfName: String) {
        _uiState.update { cur ->
            cur.copy(batchScanQueue = cur.batchScanQueue.filter { it.status == BatchLookupStatus.FOUND })
        }
        commitBatchScanToShelf(shelfName)
    }

    fun saveResolvedBatchAndOpenManual(shelfName: String, prefillIsbn: String) {
        commitResolvedBatchOnly(shelfName)
        openManualEntryWithIsbn(prefillIsbn)
    }

    fun setCameraMode(mode: String) {
        _uiState.update { it.copy(cameraMode = mode) }
    }

    fun toggleCameraTorch() {
        _uiState.update { it.copy(cameraTorchEnabled = !it.cameraTorchEnabled) }
    }

    fun closeFullCameraScanner() {
        _uiState.update {
            it.copy(
                showFullCameraScanner = false,
                cameraTorchEnabled = false,
                addModalTab = "search"
            )
        }
    }

    fun clearBatchScanQueue() {
        _uiState.update { it.copy(batchScanQueue = emptyList()) }
    }

    fun removeBatchItem(candidateId: String) {
        _uiState.update { cur ->
            cur.copy(batchScanQueue = cur.batchScanQueue.filterNot { it.id == candidateId })
        }
    }

    fun commitBatchScanToShelf(shelfName: String) {
        val queue = _uiState.value.batchScanQueue.filter { c ->
            c.status == BatchLookupStatus.FOUND &&
            !c.title.startsWith("Searching ISBN", ignoreCase = true) &&
            !c.title.startsWith("Mencari ISBN", ignoreCase = true) &&
            !c.title.startsWith("ISBN Not Found", ignoreCase = true) &&
            !c.title.startsWith("ISBN Tidak Ditemukan", ignoreCase = true)
        }
        if (queue.isEmpty()) return
        val canonicalShelf = canonicalShelfKey(shelfName)
        viewModelScope.launch {
            val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            for (c in queue) {
                val id = "cat-${System.currentTimeMillis()}-${c.id.hashCode()}"
                val entity = BookEntity(
                    id = id,
                    title = c.title,
                    author = c.author,
                    year = c.year,
                    publisher = c.publisher,
                    edition = c.edition,
                    isbn = c.isbn,
                    category = c.category,
                    shelf = canonicalShelf,
                    format = "PHYSICAL",
                    pagesCount = c.pages,
                    addedDate = todayKey,
                    progress = 0,
                    completedAllPages = false,
                    onTheGo = false,
                    timeMinutes = 0,
                    readingSeconds = 0,
                    savedChapterIndex = 0,
                    savedPageIndex = 0,
                    bookmarked = false,
                    localFilePath = null,
                    coverImagePath = c.coverUrl,
                    spineColorHex = c.coverBgHex,
                    spineTextColorHex = 0xFFFAF6EBL,
                    synopsis = c.synopsis
                )
                val chapters = buildStructuredChaptersFromSynopsis(entity).map {
                    BookChapterEntity(
                        bookId = id,
                        chapterIndex = it.index,
                        title = it.title,
                        pagesJson = JSONArray(it.pages).toString(),
                        illustrationsJson = "[]"
                    )
                }
                dao.saveBookWithChapters(entity, chapters)
            }
            _uiState.update {
                it.copy(
                    batchScanQueue = emptyList(),
                    showFullCameraScanner = false,
                    showAddBookSheet = false
                )
            }
            val isId = preferences.value.lang != "en"
            showToast(if (isId) "${queue.size} buku disimpan" else "${queue.size} books saved")
        }
    }

    fun openCatalogBookDetail(candidate: CatalogBookCandidate?) {
        _uiState.update { it.copy(selectedCatalogBookDetail = candidate) }
    }

    fun promptChooseShelfForCandidate(candidate: CatalogBookCandidate?) {
        _uiState.update { it.copy(pendingCandidateForShelf = candidate) }
    }

    fun saveCandidateToShelf(candidate: CatalogBookCandidate, shelfName: String, format: String = "PHYSICAL") {
        val canonicalShelf = if (shelfName.equals("all", true) || shelfName.equals("semua", true) || shelfName.isBlank()) {
            if (format.equals("Physical", true)) "Koleksi Fisik" else "eReader"
        } else {
            canonicalShelfKey(shelfName)
        }
        viewModelScope.launch {
            val id = "book-${System.currentTimeMillis()}"
            val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

            var spineColor = candidate.coverBgHex
            var spineTextColor = 0xFFFAF6EBL
            if (!candidate.coverUrl.isNullOrBlank()) {
                runCatching {
                    val request = ImageRequest.Builder(appContext)
                        .data(candidate.coverUrl)
                        .allowHardware(false)
                        .build()
                    val result = (Coil.imageLoader(appContext).execute(request) as? SuccessResult)?.drawable
                    val bmp = (result as? BitmapDrawable)?.bitmap
                    if (bmp != null) {
                        val colors = SpineColorExtractor.extractSpineColors(bmp)
                        spineColor = colors.first
                        spineTextColor = colors.second
                    }
                }
            } else {
                val paletteList = listOf(
                    0xFF1E3A5FL to 0xFFFAF6EBL,
                    0xFF4A2E35L to 0xFFFAF6EBL,
                    0xFF2D4B40L to 0xFFFAF6EBL,
                    0xFF5C4033L to 0xFFFAF6EBL,
                    0xFF363946L to 0xFFFAF6EBL,
                    0xFF6A3B22L to 0xFFFAF6EBL,
                    0xFF2C3E50L to 0xFFFAF6EBL,
                    0xFF483D8BL to 0xFFFAF6EBL
                )
                val pIdx = kotlin.math.abs((candidate.title + candidate.author).hashCode()) % paletteList.size
                spineColor = paletteList[pIdx].first
                spineTextColor = paletteList[pIdx].second
            }

            val genreMatch = Regex("""\[Genres:\s*(.*?)\]""").find(candidate.synopsis)
            val extractedCandidateGenres = genreMatch?.groupValues?.get(1)?.trim().orEmpty()
            val cleanCandidateSynopsis = if (genreMatch != null) candidate.synopsis.replace(Regex("""\s*\[Genres:\s*.*?\]"""), "").trim() else candidate.synopsis

            val entity = BookEntity(
                id = id,
                title = candidate.title,
                author = candidate.author,
                year = candidate.year,
                publisher = candidate.publisher,
                edition = candidate.edition,
                isbn = candidate.isbn,
                category = candidate.category,
                shelf = canonicalShelf,
                format = format,
                pagesCount = candidate.pages.coerceAtLeast(1),
                addedDate = todayKey,
                progress = 0,
                completedAllPages = false,
                onTheGo = false,
                timeMinutes = 0,
                readingSeconds = 0,
                savedChapterIndex = 0,
                savedPageIndex = 0,
                bookmarked = false,
                localFilePath = null,
                coverImagePath = candidate.coverUrl,
                spineColorHex = spineColor,
                spineTextColorHex = spineTextColor,
                synopsis = cleanCandidateSynopsis,
                heightCm = candidate.heightCm,
                widthCm = candidate.widthCm,
                lengthCm = candidate.lengthCm,
                weightGrams = candidate.weightGrams,
                genres = extractedCandidateGenres
            )
            val chapters = buildStructuredChaptersFromSynopsis(entity).map {
                BookChapterEntity(
                    bookId = id,
                    chapterIndex = it.index,
                    title = it.title,
                    pagesJson = JSONArray(it.pages).toString(),
                    illustrationsJson = "[]"
                )
            }
            dao.saveBookWithChapters(entity, chapters)
            _uiState.update {
                it.copy(
                    pendingCandidateForShelf = null,
                    selectedCatalogBookDetail = null,
                    showAddBookSheet = false,
                    showFullCameraScanner = false
                )
            }
            val isId = preferences.value.lang != "en"
            val displayShelf = if (!isId && canonicalShelf == "Koleksi Fisik") "Physical Collection" else canonicalShelf
            showToast("\"${entity.title}\" → $displayShelf")
        }
    }

    // -------------------------------------------------------------------------
    // BACKUP EXPORT & RESTORE (JSON)
    // -------------------------------------------------------------------------
    suspend fun buildBackupJsonString(): String = withContext(Dispatchers.IO) {
        val snapshotBooks = dao.getAllBooks().ifEmpty { books.value }
        val snapshotShelves = dao.getAllShelvesOnce().ifEmpty { shelves.value }
        val snapshotDailyLogs = dao.getAllDailyLogsOnce().ifEmpty { dailyLogs.value }
        val snapshotHighlights = dao.getAllHighlightsOnce().ifEmpty { allHighlights.value }
        val snapshotBookmarks = dao.getAllBookmarksOnce().ifEmpty { allBookmarks.value }

        val root = JSONObject()
        root.put("version", 2)
        root.put("exportedAtMillis", System.currentTimeMillis())

        val booksArr = JSONArray()
        for (b in snapshotBooks) {
            booksArr.put(JSONObject().apply {
                put("id", b.id)
                put("title", b.title)
                put("author", b.author)
                put("year", b.year)
                put("publisher", b.publisher)
                put("edition", b.edition)
                put("isbn", b.isbn)
                put("category", b.category)
                put("shelf", b.shelf)
                put("format", b.format)
                put("pagesCount", b.pagesCount)
                put("addedDate", b.addedDate)
                put("progress", b.progress)
                put("completedAllPages", b.completedAllPages)
                put("onTheGo", b.onTheGo)
                put("timeMinutes", b.timeMinutes)
                put("readingSeconds", b.readingSeconds)
                put("savedChapterIndex", b.savedChapterIndex)
                put("savedPageIndex", b.savedPageIndex)
                put("bookmarked", b.bookmarked)
                put("localFilePath", b.localFilePath ?: "")
                put("coverImagePath", b.coverImagePath ?: "")
                put("spineColorHex", b.spineColorHex)
                put("spineTextColorHex", b.spineTextColorHex)
                put("synopsis", b.synopsis)
                put("dimensionsText", b.dimensionsText)
                put("heightCm", b.heightCm.toDouble())
                put("widthCm", b.widthCm.toDouble())
                put("lengthCm", b.lengthCm.toDouble())
                put("weightGrams", b.weightGrams.toDouble())
                put("createdAtMillis", b.createdAtMillis)
                put("lastReadAtMillis", b.lastReadAtMillis)
                put("tocIndexed", b.tocIndexed)
                put("tocIndexVersion", b.tocIndexVersion)
                put("genres", b.genres)
            })
        }
        root.put("books", booksArr)

        val shelvesArr = JSONArray()
        for (s in snapshotShelves) {
            shelvesArr.put(JSONObject().apply {
                put("name", s.name)
                put("sortOrder", s.sortOrder)
                put("isDefault", s.isDefault)
            })
        }
        root.put("shelves", shelvesArr)

        val dailyLogsArr = JSONArray()
        for (log in snapshotDailyLogs) {
            dailyLogsArr.put(JSONObject().apply {
                put("dateKey", log.dateKey)
                put("minutesRead", log.minutesRead)
                put("pagesTurned", log.pagesTurned)
            })
        }
        root.put("daily_reading_logs", dailyLogsArr)

        val highlightsArr = JSONArray()
        for (hl in snapshotHighlights) {
            highlightsArr.put(JSONObject().apply {
                put("id", hl.id)
                put("bookId", hl.bookId)
                put("chapterIndex", hl.chapterIndex)
                put("pageIndex", hl.pageIndex)
                put("chapterTitle", hl.chapterTitle)
                put("quoteText", hl.quoteText)
                put("noteText", hl.noteText)
                put("colorKey", hl.colorKey)
                put("createdAtMillis", hl.createdAtMillis)
                put("startCharIndex", hl.startCharIndex)
                put("endCharIndex", hl.endCharIndex)
            })
        }
        root.put("highlight_notes", highlightsArr)

        val bookmarksArr = JSONArray()
        for (bm in snapshotBookmarks) {
            bookmarksArr.put(JSONObject().apply {
                put("id", bm.id)
                put("bookId", bm.bookId)
                put("pageNumber", bm.pageNumber)
                put("chapterIndex", bm.chapterIndex)
                put("chapterTitle", bm.chapterTitle)
                put("snippetText", bm.snippetText)
                put("createdAtMillis", bm.createdAtMillis)
            })
        }
        root.put("page_bookmarks", bookmarksArr)

        root.toString(2)
    }

    fun exportBackupJson(onReady: (String) -> Unit) {
        viewModelScope.launch {
            val json = buildBackupJsonString()
            onReady(json)
        }
    }

    fun exportBackupToUri(uri: Uri) {
        viewModelScope.launch {
            val isId = preferences.value.lang != "en"
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val json = buildBackupJsonString()
                    appContext.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                        writer.write(json)
                    } ?: error("Cannot open output stream")
                }.isSuccess
            }
            if (ok) {
                showToast(if (isId) "Cadangan berhasil disimpan" else "Backup saved successfully")
            } else {
                showToast(if (isId) "Gagal menyimpan cadangan" else "Failed to save backup")
            }
        }
    }

    private suspend fun restoreBackupFromJsonString(rawJson: String) = withContext(Dispatchers.IO) {
        runCatching {
            val obj = JSONObject(rawJson)
            val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

            val shelvesArr = obj.optJSONArray("shelves")
            if (shelvesArr != null) {
                for (i in 0 until shelvesArr.length()) {
                    val s = shelvesArr.optJSONObject(i) ?: continue
                    val name = s.optString("name").trim()
                    if (name.isNotBlank()) {
                        dao.upsertShelf(
                            ShelfEntity(
                                name = name,
                                sortOrder = s.optInt("sortOrder", i),
                                isDefault = s.optBoolean("isDefault", name == "eReader" || name == "Koleksi Fisik")
                            )
                        )
                    }
                }
            }

            val booksRoot = File(appContext.filesDir, "books")
            val coversRoot = File(appContext.filesDir, "covers")
            val arr = obj.optJSONArray("books")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val b = arr.optJSONObject(i) ?: continue
                    val rawBookId = b.optString("id", "restored-$i-${System.currentTimeMillis()}")
                    val bookId = sanitizeBookId(rawBookId)
                    val existingBook = dao.getBookById(bookId)
                    val existingChapters = dao.getChaptersForBook(bookId)

                    val restoredProgress = b.optInt("progress", existingBook?.progress ?: 0)
                    val restoredMins = b.optInt("timeMinutes", existingBook?.timeMinutes ?: 0)
                    val restoredSecs = b.optInt("readingSeconds", maxOf(existingBook?.readingSeconds ?: 0, restoredMins * 60))

                    val backupLocalPath = b.optString("localFilePath").takeIf {
                        it.isNotBlank() &&
                            (isCanonicalPathInsideDir(it, booksRoot) || isCanonicalPathInsideDir(it, coversRoot)) &&
                            File(it).exists()
                    }
                    val existingLocalPath = existingBook?.localFilePath?.takeIf {
                        it.isNotBlank() &&
                            (isCanonicalPathInsideDir(it, booksRoot) || isCanonicalPathInsideDir(it, coversRoot)) &&
                            File(it).exists()
                    }
                    val effectiveLocalPath = existingLocalPath ?: backupLocalPath

                    val backupCoverPath = b.optString("coverImagePath").ifBlank { b.optString("coverUrl") }.takeIf {
                        it.isNotBlank() && (
                            it.startsWith("http://", ignoreCase = true) ||
                                it.startsWith("https://", ignoreCase = true) ||
                                ((isCanonicalPathInsideDir(it, coversRoot) || isCanonicalPathInsideDir(it, booksRoot)) && File(it).exists())
                            )
                    }
                    val existingCoverPath = existingBook?.coverImagePath?.takeIf {
                        it.isNotBlank() && (
                            it.startsWith("http://", ignoreCase = true) ||
                                it.startsWith("https://", ignoreCase = true) ||
                                ((isCanonicalPathInsideDir(it, coversRoot) || isCanonicalPathInsideDir(it, booksRoot)) && File(it).exists())
                            )
                    }
                    val effectiveCoverPath = existingCoverPath ?: backupCoverPath

                    val entity = BookEntity(
                        id = bookId,
                        title = b.optString("title", existingBook?.title ?: "Book Title"),
                        author = b.optString("author", existingBook?.author ?: "Author"),
                        year = b.optString("year", existingBook?.year ?: "2023"),
                        publisher = b.optString("publisher", existingBook?.publisher ?: "Publisher"),
                        edition = b.optString("edition", existingBook?.edition ?: "1st Edition"),
                        isbn = b.optString("isbn", existingBook?.isbn ?: "—"),
                        category = b.optString("category", existingBook?.category ?: "Novel"),
                        shelf = canonicalShelfKey(b.optString("shelf", existingBook?.shelf ?: "eReader")),
                        format = b.optString("format", existingBook?.format ?: "PHYSICAL"),
                        pagesCount = b.optInt("pagesCount", b.optInt("pages", existingBook?.pagesCount ?: 320)),
                        addedDate = b.optString("addedDate", existingBook?.addedDate ?: todayKey),
                        progress = restoredProgress,
                        completedAllPages = b.optBoolean("completedAllPages", restoredProgress >= 100),
                        onTheGo = b.optBoolean("onTheGo", restoredProgress in 1..99),
                        timeMinutes = maxOf(restoredMins, restoredSecs / 60),
                        readingSeconds = restoredSecs,
                        savedChapterIndex = b.optInt("savedChapterIndex", existingBook?.savedChapterIndex ?: 0),
                        savedPageIndex = b.optInt("savedPageIndex", existingBook?.savedPageIndex ?: 0),
                        bookmarked = b.optBoolean("bookmarked", existingBook?.bookmarked ?: false),
                        localFilePath = effectiveLocalPath,
                        coverImagePath = effectiveCoverPath,
                        spineColorHex = b.optLong("spineColorHex", existingBook?.spineColorHex ?: 0xFF2B3A4AL),
                        spineTextColorHex = b.optLong("spineTextColorHex", existingBook?.spineTextColorHex ?: 0xFFFAF6EBL),
                        synopsis = b.optString("synopsis", existingBook?.synopsis ?: ""),
                        dimensionsText = b.optString("dimensionsText", existingBook?.dimensionsText ?: ""),
                        heightCm = b.optDouble("heightCm", (existingBook?.heightCm ?: 20f).toDouble()).toFloat(),
                        widthCm = b.optDouble("widthCm", (existingBook?.widthCm ?: 14f).toDouble()).toFloat(),
                        lengthCm = b.optDouble("lengthCm", (existingBook?.lengthCm ?: 20f).toDouble()).toFloat(),
                        weightGrams = b.optDouble("weightGrams", (existingBook?.weightGrams ?: 300f).toDouble()).toFloat(),
                        createdAtMillis = b.optLong("createdAtMillis", existingBook?.createdAtMillis ?: System.currentTimeMillis()),
                        lastReadAtMillis = b.optLong("lastReadAtMillis", existingBook?.lastReadAtMillis ?: 0L),
                        tocIndexed = existingBook?.tocIndexed ?: b.optBoolean("tocIndexed", false),
                        tocIndexVersion = existingBook?.tocIndexVersion ?: b.optInt("tocIndexVersion", 0),
                        genres = b.optString("genres", existingBook?.genres ?: "")
                    )

                    if (existingBook != null) {
                        // Update in place so ForeignKey.CASCADE does NOT wipe existing EPUB/PDF chapters, highlights, or bookmarks
                        dao.updateBook(entity)
                        if (existingChapters.isEmpty()) {
                            val fallbackChapters = buildStructuredChaptersFromSynopsis(entity).map {
                                BookChapterEntity(
                                    bookId = entity.id,
                                    chapterIndex = it.index,
                                    title = it.title,
                                    pagesJson = JSONArray(it.pages).toString(),
                                    illustrationsJson = "[]"
                                )
                            }
                            if (fallbackChapters.isNotEmpty()) {
                                dao.insertChapters(fallbackChapters)
                            }
                        }
                    } else {
                        val chapters = buildStructuredChaptersFromSynopsis(entity).map {
                            BookChapterEntity(
                                bookId = entity.id,
                                chapterIndex = it.index,
                                title = it.title,
                                pagesJson = JSONArray(it.pages).toString(),
                                illustrationsJson = "[]"
                            )
                        }
                        dao.saveBookWithChapters(entity, chapters)
                    }
                }
            }

            val dailyLogsArr = obj.optJSONArray("daily_reading_logs") ?: obj.optJSONArray("dailyLogs")
            if (dailyLogsArr != null) {
                for (i in 0 until dailyLogsArr.length()) {
                    val l = dailyLogsArr.optJSONObject(i) ?: continue
                    val dateKey = l.optString("dateKey").trim()
                    if (dateKey.isNotBlank()) {
                        val existingLog = dao.getDailyLog(dateKey)
                        val mins = maxOf(existingLog?.minutesRead ?: 0, l.optInt("minutesRead", 0))
                        val pages = maxOf(existingLog?.pagesTurned ?: 0, l.optInt("pagesTurned", 0))
                        dao.upsertDailyLog(
                            DailyReadingLogEntity(
                                dateKey = dateKey,
                                minutesRead = mins,
                                pagesTurned = pages
                            )
                        )
                    }
                }
            }

            val existingBookIds = dao.getAllBooks().map { it.id }.toHashSet()

            val highlightsArr = obj.optJSONArray("highlight_notes") ?: obj.optJSONArray("highlights")
            if (highlightsArr != null) {
                for (i in 0 until highlightsArr.length()) {
                    val hl = highlightsArr.optJSONObject(i) ?: continue
                    val bId = sanitizeBookId(hl.optString("bookId"))
                    val hlId = hl.optString("id").trim()
                    if (hlId.isNotBlank() && bId in existingBookIds) {
                        dao.upsertHighlight(
                            HighlightNoteEntity(
                                id = hlId,
                                bookId = bId,
                                chapterIndex = hl.optInt("chapterIndex", 0),
                                pageIndex = hl.optInt("pageIndex", 0),
                                chapterTitle = hl.optString("chapterTitle", ""),
                                quoteText = hl.optString("quoteText", ""),
                                noteText = hl.optString("noteText", ""),
                                colorKey = hl.optString("colorKey", "amber"),
                                createdAtMillis = hl.optLong("createdAtMillis", System.currentTimeMillis()),
                                startCharIndex = hl.optInt("startCharIndex", -1),
                                endCharIndex = hl.optInt("endCharIndex", -1)
                            )
                        )
                    }
                }
            }

            val bookmarksArr = obj.optJSONArray("page_bookmarks") ?: obj.optJSONArray("bookmarks")
            if (bookmarksArr != null) {
                for (i in 0 until bookmarksArr.length()) {
                    val bm = bookmarksArr.optJSONObject(i) ?: continue
                    val bId = sanitizeBookId(bm.optString("bookId"))
                    val bmId = bm.optString("id").trim()
                    if (bmId.isNotBlank() && bId in existingBookIds) {
                        dao.upsertBookmark(
                            PageBookmarkEntity(
                                id = bmId,
                                bookId = bId,
                                pageNumber = bm.optInt("pageNumber", 1),
                                chapterIndex = bm.optInt("chapterIndex", 0),
                                chapterTitle = bm.optString("chapterTitle", ""),
                                snippetText = bm.optString("snippetText", ""),
                                createdAtMillis = bm.optLong("createdAtMillis", System.currentTimeMillis())
                            )
                        )
                    }
                }
            }
        }
    }

    class Factory(private val app: LlunevieApplication) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return LlunevieViewModel(
                appContext = app.applicationContext,
                dao = app.database.dao(),
                prefsRepo = app.preferencesRepository,
                apiRepo = app.apiRepository
            ) as T
        }
    }
}
