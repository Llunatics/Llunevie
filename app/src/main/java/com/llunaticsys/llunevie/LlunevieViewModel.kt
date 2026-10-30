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
import com.llunaticsys.llunevie.data.local.LlunevieDao
import com.llunaticsys.llunevie.data.local.ShelfEntity
import com.llunaticsys.llunevie.data.local.UserPreferencesRepository
import com.llunaticsys.llunevie.data.local.UserPreferencesState
import com.llunaticsys.llunevie.data.parser.EpubNativeParser
import com.llunaticsys.llunevie.data.parser.PdfNativeRenderer
import com.llunaticsys.llunevie.data.parser.SpineColorExtractor
import com.llunaticsys.llunevie.data.parser.TxtNativeParser
import com.llunaticsys.llunevie.data.remote.MultiBookApiRepository
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import android.graphics.drawable.BitmapDrawable
import com.llunaticsys.llunevie.model.AppThemeMode
import com.llunaticsys.llunevie.model.BatchLookupStatus
import com.llunaticsys.llunevie.model.BookChapter
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import java.net.URL
import java.net.HttpURLConnection
import java.io.File
import java.io.FileOutputStream
import android.graphics.BitmapFactory
import com.llunaticsys.llunevie.model.BookIllustration
import com.llunaticsys.llunevie.model.CatalogBookCandidate
import com.llunaticsys.llunevie.model.HighlightColorKey
import com.llunaticsys.llunevie.model.LibrarySortOption
import com.llunaticsys.llunevie.model.LibraryViewMode
import com.llunaticsys.llunevie.model.PageAnimStyle
import com.llunaticsys.llunevie.model.ReadingFlowMode
import com.llunaticsys.llunevie.ui.components.sanitizeBookOrChapterText
import com.llunaticsys.llunevie.ui.theme.canonicalShelfKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    val toastMessage: String? = null
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

    private val _previewOverrides = MutableStateFlow<UserPreferencesState?>(null)

    val preferences: StateFlow<UserPreferencesState> = combine(
        prefsRepo.preferencesFlow,
        _previewOverrides
    ) { diskPrefs, preview ->
        preview ?: diskPrefs
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UserPreferencesState()
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

    private val _uiState = MutableStateFlow(LlunevieUiState())
    val uiState: StateFlow<LlunevieUiState> = _uiState.asStateFlow()

    private val pdfBitmapCache = object : LruCache<String, Bitmap>(32) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }
    private val _pdfIndexingState = MutableStateFlow<Map<String, Int?>>(emptyMap())
    val pdfIndexingState: StateFlow<Map<String, Int?>> = _pdfIndexingState.asStateFlow()

    fun getCachedPdfBitmap(bookId: String, pageIndex: Int): Bitmap? {
        val bmp = pdfBitmapCache.get("$bookId:$pageIndex")
        if (bmp != null && !bmp.isRecycled) return bmp
        return null
    }

    suspend fun loadPdfPageBitmap(bookId: String, filePath: String, pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        val key = "$bookId:$pageIndex"
        pdfBitmapCache.get(key)?.let { if (!it.isRecycled) return@withContext it }
        val bmp = PdfNativeRenderer.renderPageBitmap(filePath, pageIndex, 1080)
        if (bmp != null) {
            pdfBitmapCache.put(key, bmp)
        }
        bmp
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
                listOf(pageIndex - 1, pageIndex + 1)
            }
            for (p in pagesToPrefetch) {
                if (p in 0 until total && pdfBitmapCache.get("$bookId:$p") == null) {
                    val bmp = PdfNativeRenderer.renderPageBitmap(filePath, p, 1080)
                    if (bmp != null) pdfBitmapCache.put("$bookId:$p", bmp)
                }
            }
        }
    }

    private var searchJob: Job? = null
    private var toastJob: Job? = null
    private var readingTimerJob: Job? = null
    private var sessionAccumulatedSeconds: Int = 0

    init {
        startActiveReadingSessionTicker()
        viewModelScope.launch {
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
    }

    /**
     * Smooth, zero-jank reading timer:
     * Accumulates seconds in memory without writing to Room every second (which would trigger
     * full-screen StateFlow recompositions every 1s). Writes to Room only once every 60 seconds!
     */
    private fun startActiveReadingSessionTicker() {
        readingTimerJob?.cancel()
        readingTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                val st = _uiState.value
                if (st.activeTab == MainNavTab.READER && st.activeBookId != null) {
                    sessionAccumulatedSeconds++
                    if (sessionAccumulatedSeconds >= 60) {
                        sessionAccumulatedSeconds = 0
                        val book = books.value.find { it.id == st.activeBookId }
                        if (book != null) {
                            val nextSecs = book.readingSeconds + 60
                            val nextMins = book.timeMinutes + 1
                            dao.updateBook(book.copy(readingSeconds = nextSecs, timeMinutes = nextMins))
                            recordDailyReadingMinute(1, 0)
                        }
                    }
                } else {
                    sessionAccumulatedSeconds = 0
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
            runCatching {
                val dir = File(appContext.filesDir, "profile").apply { mkdirs() }
                val outFile = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
                appContext.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                prefsRepo.setUserPhotoPath(outFile.absolutePath)
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
                    readerFullView = if (mode == ReadingFlowMode.SPREAD) true else it.readerFullView
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
                gridCols = 0
            )
            _uiState.update { it.copy(showLibFilterSheet = false) }
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

    fun confirmDeleteSelectedBooks() {
        val ids = _uiState.value.libSelectedIds.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            dao.deleteBooksByIds(ids)
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
    fun openBookInReader(bookId: String) {
        viewModelScope.launch {
            val rawBook = dao.getBookById(bookId) ?: return@launch
            val sanitizedTitle = sanitizeBookOrChapterText(rawBook.title)
            val book = if (sanitizedTitle != rawBook.title) {
                val updated = rawBook.copy(title = sanitizedTitle)
                dao.updateBook(updated)
                updated
            } else rawBook

            val chEntities = dao.getChaptersForBook(bookId)
            val parsedChapters = mutableListOf<BookChapter>()
            val parsedIllustrations = mutableListOf<BookIllustration>()

            if (chEntities.isNotEmpty()) {
                var globalTracker = 0
                for (ce in chEntities) {
                    val pagesArr = runCatching { JSONArray(ce.pagesJson) }.getOrNull() ?: JSONArray()
                    val pagesList = (0 until pagesArr.length()).map { pagesArr.optString(it) }.ifEmpty { listOf(book.synopsis) }
                    val cleanChTitle = sanitizeBookOrChapterText(ce.title)
                    parsedChapters.add(BookChapter(index = ce.chapterIndex, title = cleanChTitle, pages = pagesList))

                    val illustArr = runCatching { JSONArray(ce.illustrationsJson) }.getOrNull() ?: JSONArray()
                    for (i in 0 until illustArr.length()) {
                        val obj = illustArr.optJSONObject(i) ?: continue
                        val path = obj.optString("uriOrPath")
                        if (path.isNotBlank()) {
                            val savedPageIdx = obj.optInt("pageIndexInBook", -1)
                            val effectivePageIdx = if (savedPageIdx >= 0) savedPageIdx else {
                                val found = pagesList.indexOfFirst { it.contains(path) }
                                if (found >= 0) globalTracker + found else globalTracker
                            }
                            parsedIllustrations.add(
                                BookIllustration(
                                    uriOrPath = path,
                                    caption = sanitizeBookOrChapterText(obj.optString("caption", cleanChTitle)),
                                    pageIndexInBook = effectivePageIdx
                                )
                            )
                        }
                    }

                    // Extract embedded illustrations in page texts if not already recorded
                    pagesList.forEachIndexed { pIdx, pText ->
                        if (pText.contains("[ILLUST:")) {
                            val inner = pText.substringAfter("[ILLUST:").substringBefore("]")
                            val path = inner.substringBefore("|")
                            val cap = inner.substringAfter("|", "")
                            if (path.isNotBlank() && parsedIllustrations.none { it.uriOrPath == path }) {
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
                    globalTracker += pagesList.size
                }
            }

            // Verify real PDF page count: if book is PDF, verify against actual file pageCount and rebuild if needed
            val isPdf = book.format.equals("PDF", true)
            if (isPdf && book.localFilePath != null) {
                val pdfFile = File(book.localFilePath)
                if (pdfFile.exists()) {
                    val actualPageCount = runCatching {
                        android.os.ParcelFileDescriptor.open(pdfFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                            android.graphics.pdf.PdfRenderer(pfd).use { it.pageCount }
                        }
                    }.getOrDefault(book.pagesCount)

                    val isEn = preferences.value.lang == "en"
                    val totalPagesInChapters = parsedChapters.sumOf { it.pages.size }

                    if (parsedChapters.isEmpty() || totalPagesInChapters != actualPageCount) {
                        val rebuilt = PdfNativeRenderer.buildPdfChapters(bookId, actualPageCount, book.title, isEn)
                        dao.saveBookWithChapters(book.copy(pagesCount = actualPageCount), rebuilt)
                        parsedChapters.clear()
                        rebuilt.forEach { ce ->
                            val pagesArr = runCatching { JSONArray(ce.pagesJson) }.getOrNull() ?: JSONArray()
                            val pagesList = (0 until pagesArr.length()).map { pagesArr.optString(it) }
                            parsedChapters.add(BookChapter(index = ce.chapterIndex, title = ce.title, pages = pagesList))
                        }
                    }

                    // Trigger indexing automatically strictly once when the PDF is first imported (or the very first time it is opened if tocIndexed == false, or if tocIndexVersion < 3 to upgrade chapter and illustration detection).
                    // On all subsequent opens (tocIndexed == true && tocIndexVersion >= 3), load the saved chapters and illustrations directly from Room and never show or re-run the indexing banner again!
                    if ((!book.tocIndexed || book.tocIndexVersion < 3) && _pdfIndexingState.value[bookId] == null) {
                        indexPdfChaptersAndIllustrations(book.copy(pagesCount = actualPageCount))
                    }
                }
            } else if (parsedChapters.isEmpty()) {
                parsedChapters.addAll(buildStructuredChaptersFromSynopsis(book))
            }

            val safeChIdx = book.savedChapterIndex.coerceIn(0, (parsedChapters.size - 1).coerceAtLeast(0))
            val safePgIdx = book.savedPageIndex.coerceIn(0, (parsedChapters.getOrNull(safeChIdx)?.pages?.size ?: 1) - 1)

            if (!book.completedAllPages) {
                dao.updateBook(book.copy(onTheGo = true))
            }

            _uiState.update { cur ->
                cur.copy(
                    previousTab = if (cur.activeTab != MainNavTab.READER) cur.activeTab else cur.previousTab,
                    activeTab = MainNavTab.READER,
                    activeBookId = bookId,
                    activeChapterIndex = safeChIdx,
                    activePageIndex = safePgIdx,
                    activeBookChapters = parsedChapters,
                    activeBookIllustrations = parsedIllustrations,
                    readerFullView = preferences.value.readingFlowMode == ReadingFlowMode.SPREAD,
                    showReaderTypeSheet = false,
                    showReaderTocDrawer = false,
                    showReaderNotesDrawer = false
                )
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

                val existingChapters = dao.getChaptersForBook(book.id)
                val isEn = preferences.value.lang == "en"
                _pdfIndexingState.update { it + (book.id to 5) }
                _uiState.update { it.copy(pdfIndexingState = it.pdfIndexingState + (book.id to 5)) }

                PdfNativeRenderer.scanPdfChaptersAndIllustrationsAsync(
                    context = appContext,
                    bookId = book.id,
                    pdfFile = pdfFile,
                    totalPages = actualPageCount,
                    defaultTitle = book.title,
                    isEn = isEn,
                    existingChapters = if (book.tocIndexed && book.tocIndexVersion >= 3 && existingChapters.isNotEmpty()) existingChapters else null,
                    onProgress = { pct ->
                        _pdfIndexingState.update { it + (book.id to pct) }
                        _uiState.update { it.copy(pdfIndexingState = it.pdfIndexingState + (book.id to pct)) }
                    }
                ) { updatedChapters, updatedIllusts ->
                    val currentBook = dao.getBookById(book.id) ?: book
                    val updatedBook = currentBook.copy(
                        pagesCount = actualPageCount,
                        tocIndexed = true,
                        tocIndexVersion = 3
                    )
                    dao.saveBookWithChapters(updatedBook, updatedChapters)
                    if (_uiState.value.activeBookId == book.id) {
                        val liveChs = updatedChapters.map { ce ->
                            val pArr = runCatching { JSONArray(ce.pagesJson) }.getOrNull() ?: JSONArray()
                            val pList = (0 until pArr.length()).map { pArr.optString(it) }
                            BookChapter(index = ce.chapterIndex, title = sanitizeBookOrChapterText(ce.title), pages = pList)
                        }
                        _uiState.update { it.copy(activeBookChapters = liveChs, activeBookIllustrations = updatedIllusts) }
                    }
                }
            } catch (t: Throwable) {
                android.util.Log.e("LlunevieViewModel", "PDF indexing error for ${book.id}", t)
                runCatching {
                    val currentBook = dao.getBookById(book.id) ?: book
                    dao.updateBook(currentBook.copy(tocIndexed = true, tocIndexVersion = 3))
                }
            } finally {
                _pdfIndexingState.update { it - book.id }
                _uiState.update { it.copy(pdfIndexingState = it.pdfIndexingState - book.id) }
            }
        }
    }

    private fun buildStructuredChaptersFromSynopsis(book: BookEntity): List<BookChapter> {
        val isId = preferences.value.lang != "en"
        val syn = book.synopsis.ifBlank { "${book.title} — ${book.author}" }
        val sentences = syn.split(Regex("(?<=[.!?])\\s+")).filter { it.isNotBlank() }
        val p1 = sentences.take((sentences.size / 2).coerceAtLeast(1)).joinToString(" ").ifBlank { syn }
        val p2 = sentences.drop((sentences.size / 2).coerceAtLeast(1)).joinToString(" ").ifBlank { syn }
        return listOf(
            BookChapter(
                index = 0,
                title = if (isId) "Bab I — ${book.title}" else "Chapter I — ${book.title}",
                pages = listOf(
                    if (isId) "$p1\n\n${book.title} ditulis oleh ${book.author} (${book.publisher}, ${book.year})."
                    else "$p1\n\n${book.title} by ${book.author} (${book.publisher}, ${book.year}).",
                    p2
                )
            ),
            BookChapter(
                index = 1,
                title = if (isId) "Bab II — Perkembangan Naratif" else "Chapter II — Narrative Arc",
                pages = listOf(
                    if (isId) "Bagian lanjutan dari ${book.title} karya ${book.author}.\n\n$p1"
                    else "Continuation of ${book.title} by ${book.author}.\n\n$p1",
                    p2
                )
            )
        )
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

    fun turnReaderPage(deltaSteps: Int) {
        val st = _uiState.value
        val chapters = st.activeBookChapters
        if (chapters.isEmpty()) return
        val isSpread = preferences.value.readingFlowMode == ReadingFlowMode.SPREAD
        val stepSize = if (isSpread) 2 else 1
        val metrics = computeGlobalPageMetrics(chapters, st.activeChapterIndex, st.activePageIndex)
        val currentZero = metrics.currentGlobalPage - 1
        val targetZero = currentZero + deltaSteps * stepSize

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
                dao.updateBook(
                    book.copy(
                        progress = 100,
                        completedAllPages = true,
                        onTheGo = false
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
                dao.updateBook(
                    book.copy(
                        progress = 100,
                        completedAllPages = true,
                        onTheGo = false
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
            dao.updateBook(
                book.copy(
                    savedChapterIndex = safeCh,
                    savedPageIndex = safePg,
                    progress = newMetrics.globalPct,
                    completedAllPages = newMetrics.isAtVeryLastPage,
                    onTheGo = !newMetrics.isAtVeryLastPage
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
        viewModelScope.launch {
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
            } catch (e: Exception) {
                showToast(if (isId) "Gagal mengimpor: ${e.localizedMessage ?: "Kesalahan berkas"}" else "Import failed: ${e.localizedMessage ?: "File error"}")
            } finally {
                trackedBookId?.let { setBookImportProgress(it, null) }
            }
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
                val bmp = BitmapFactory.decodeFile(targetFile.absolutePath)
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
            val all = dao.getAllBooks()
            val book = all.find { it.id == bookId }
            if (book != null && book.spineColorHex != extractedHex) {
                dao.updateBook(book.copy(spineColorHex = extractedHex))
            }
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
                synopsis = candidate.synopsis,
                heightCm = candidate.heightCm,
                widthCm = candidate.widthCm,
                lengthCm = candidate.lengthCm,
                weightGrams = candidate.weightGrams
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
    fun buildBackupJsonString(): String {
        val root = JSONObject()
        val booksArr = JSONArray()
        for (b in books.value) {
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
                put("timeMinutes", b.timeMinutes)
                put("coverImagePath", b.coverImagePath ?: "")
                put("spineColorHex", b.spineColorHex)
                put("synopsis", b.synopsis)
            })
        }
        root.put("books", booksArr)
        return root.toString(2)
    }

    private suspend fun restoreBackupFromJsonString(rawJson: String) {
        runCatching {
            val obj = JSONObject(rawJson)
            val arr = obj.optJSONArray("books") ?: return@runCatching
            val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            for (i in 0 until arr.length()) {
                val b = arr.optJSONObject(i) ?: continue
                val entity = BookEntity(
                    id = b.optString("id", "restored-$i-${System.currentTimeMillis()}"),
                    title = b.optString("title", "Book Title"),
                    author = b.optString("author", "Author"),
                    year = b.optString("year", "2023"),
                    publisher = b.optString("publisher", "Publisher"),
                    edition = b.optString("edition", "1st Edition"),
                    isbn = b.optString("isbn", "—"),
                    category = b.optString("category", "Novel"),
                    shelf = canonicalShelfKey(b.optString("shelf", "eReader")),
                    format = b.optString("format", "PHYSICAL"),
                    pagesCount = b.optInt("pagesCount", b.optInt("pages", 320)),
                    addedDate = b.optString("addedDate", todayKey),
                    progress = b.optInt("progress", 0),
                    completedAllPages = b.optBoolean("completedAllPages", b.optInt("progress", 0) >= 100),
                    onTheGo = b.optInt("progress", 0) in 1..99,
                    timeMinutes = b.optInt("timeMinutes", 0),
                    readingSeconds = b.optInt("timeMinutes", 0) * 60,
                    savedChapterIndex = 0,
                    savedPageIndex = 0,
                    bookmarked = false,
                    localFilePath = null,
                    coverImagePath = b.optString("coverImagePath").ifBlank { b.optString("coverUrl").ifBlank { null } },
                    spineColorHex = b.optLong("spineColorHex", 0xFF2B3A4AL),
                    spineTextColorHex = 0xFFFAF6EBL,
                    synopsis = b.optString("synopsis", "")
                )
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
