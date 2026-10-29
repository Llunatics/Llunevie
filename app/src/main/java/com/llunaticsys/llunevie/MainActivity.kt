package com.llunaticsys.llunevie

import android.content.Intent
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.llunaticsys.llunevie.model.ReadingFlowMode
import com.llunaticsys.llunevie.ui.components.BottomFloatingNavDock
import com.llunaticsys.llunevie.ui.reader.ReaderScreen
import com.llunaticsys.llunevie.ui.screens.AddBookSheet
import com.llunaticsys.llunevie.ui.screens.HomeScreen
import com.llunaticsys.llunevie.ui.screens.IsbnCameraScannerScreen
import com.llunaticsys.llunevie.ui.screens.LibraryScreen
import com.llunaticsys.llunevie.ui.screens.SettingsScreen
import com.llunaticsys.llunevie.ui.screens.StatsScreen
import com.llunaticsys.llunevie.ui.theme.LlunevieTheme
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette
import com.llunaticsys.llunevie.ui.theme.getLlunevieStrings

class MainActivity : ComponentActivity() {

    private val viewModel: LlunevieViewModel by viewModels {
        LlunevieViewModel.Factory(application as LlunevieApplication)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val st = viewModel.uiState.value
                when {
                    st.showIllustViewer -> viewModel.closeIllustrationViewer()
                    st.showReaderNotesDrawer -> viewModel.setShowReaderNotesDrawer(false)
                    st.showReaderTocDrawer -> viewModel.setShowReaderTocDrawer(false)
                    st.showReaderTypeSheet -> viewModel.setShowReaderTypeSheet(false)
                    st.showFullCameraScanner -> viewModel.closeFullCameraScanner()
                    st.pendingCandidateForShelf != null -> viewModel.promptChooseShelfForCandidate(null)
                    st.selectedCatalogBookDetail != null -> viewModel.openCatalogBookDetail(null)
                    st.showAddBookSheet -> viewModel.setShowAddBookSheet(false)
                    st.showLibFilterSheet -> viewModel.toggleShowLibFilterSheet(false)
                    st.libSelectMode -> viewModel.exitSelectionMode()
                    st.activeTab == MainNavTab.READER -> viewModel.switchTab(st.previousTab)
                    st.activeTab != MainNavTab.HOME -> viewModel.switchTab(MainNavTab.HOME)
                    else -> {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                        isEnabled = true
                    }
                }
            }
        })

        setContent {
            val prefs by viewModel.preferences.collectAsState()
            val books by viewModel.books.collectAsState()
            val shelves by viewModel.shelves.collectAsState()
            val dailyLogs by viewModel.dailyLogs.collectAsState()
            val allHighlights by viewModel.allHighlights.collectAsState()
            val uiState by viewModel.uiState.collectAsState()
            val strings = getLlunevieStrings(prefs.lang)

            val openFileLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    viewModel.importLocalBookOrBackupFile(uri)
                }
            }

            val pickPhotoLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.GetContent()
            ) { uri ->
                if (uri != null) {
                    viewModel.importUserPhoto(uri)
                }
            }

            val isSpreadReader = uiState.activeTab == MainNavTab.READER && prefs.readingFlowMode == ReadingFlowMode.SPREAD
            LaunchedEffect(isSpreadReader, uiState.readerFullView, uiState.activeTab) {
                runCatching {
                    val targetOrientation = if (isSpreadReader) {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                    }
                    if (requestedOrientation != targetOrientation) {
                        requestedOrientation = targetOrientation
                    }
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    if (uiState.activeTab == MainNavTab.READER && uiState.readerFullView) {
                        controller.hide(WindowInsetsCompat.Type.systemBars())
                    } else {
                        controller.show(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }

            LlunevieTheme(
                themeMode = prefs.appTheme,
                glassDesign = prefs.glassDesign,
                amoledBlack = prefs.amoledBlack
            ) {
                val palette = LocalLluneviePalette.current
                LaunchedEffect(palette.bgApp, palette.isDark) {
                    runCatching {
                        window.statusBarColor = palette.bgApp.toArgb()
                        window.navigationBarColor = palette.bgApp.toArgb()
                        val controller = WindowInsetsControllerCompat(window, window.decorView)
                        controller.isAppearanceLightStatusBars = !palette.isDark
                        controller.isAppearanceLightNavigationBars = !palette.isDark
                    }
                }

                LlunevieMainContainer(
                    viewModel = viewModel,
                    prefs = prefs,
                    books = books,
                    shelves = shelves,
                    dailyLogs = dailyLogs,
                    allHighlights = allHighlights,
                    uiState = uiState,
                    strings = strings,
                    onPickLocalFile = {
                        openFileLauncher.launch(
                            arrayOf(
                                "application/epub+zip",
                                "application/pdf",
                                "text/plain",
                                "application/json",
                                "*/*"
                            )
                        )
                    },
                    onPickPhoto = { pickPhotoLauncher.launch("image/*") },
                    onShareBackupJson = { jsonPayload ->
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "application/json"
                            putExtra(Intent.EXTRA_TITLE, "llunevie-backup.json")
                            putExtra(Intent.EXTRA_TEXT, jsonPayload)
                        }
                        startActivity(Intent.createChooser(sendIntent, "Save Llunevie Backup"))
                    }
                )
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        val prefs = viewModel.preferences.value
        val uiState = viewModel.uiState.value
        if (uiState.activeTab == MainNavTab.READER && prefs.volumeKeysTurn) {
            if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                viewModel.turnReaderPage(1)
                return true
            } else if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                viewModel.turnReaderPage(-1)
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}

@Composable
private fun LlunevieMainContainer(
    viewModel: LlunevieViewModel,
    prefs: com.llunaticsys.llunevie.data.local.UserPreferencesState,
    books: List<com.llunaticsys.llunevie.data.local.BookEntity>,
    shelves: List<com.llunaticsys.llunevie.data.local.ShelfEntity>,
    dailyLogs: List<com.llunaticsys.llunevie.data.local.DailyReadingLogEntity>,
    allHighlights: List<com.llunaticsys.llunevie.data.local.HighlightNoteEntity>,
    uiState: LlunevieUiState,
    strings: com.llunaticsys.llunevie.ui.theme.LlunevieStrings,
    onPickLocalFile: () -> Unit,
    onPickPhoto: () -> Unit,
    onShareBackupJson: (String) -> Unit
) {
    val palette = LocalLluneviePalette.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bgApp)
            .statusBarsPadding()
    ) {
        // Directional spring + subtle scale screen transition matching native fluid navigation
        AnimatedContent(
            targetState = uiState.activeTab,
            transitionSpec = {
                val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                (slideInHorizontally(spring(dampingRatio = 0.84f, stiffness = 360f)) { w -> dir * (w / 6) } +
                    fadeIn(tween(200)) +
                    scaleIn(initialScale = 0.965f, animationSpec = spring(dampingRatio = 0.84f, stiffness = 360f)))
                    .togetherWith(
                        slideOutHorizontally(tween(170)) { w -> -dir * (w / 8) } +
                            fadeOut(tween(140)) +
                            scaleOut(targetScale = 0.965f, animationSpec = tween(170))
                    )
            },
            label = "mainTabTransition",
            modifier = Modifier.fillMaxSize()
        ) { targetTab ->
            when (targetTab) {
                MainNavTab.HOME -> HomeScreen(
                    books = books,
                    dailyLogs = dailyLogs,
                    prefs = prefs,
                    strings = strings,
                    importingBooksProgress = uiState.importingBooksProgress,
                    onOpenBook = { viewModel.openBookInReader(it) },
                    onOpenAddBook = { viewModel.setShowAddBookSheet(true, "search") },
                    onNavigateTab = { viewModel.switchTab(it) },
                    onSetHomeRecentMode = { viewModel.setHomeRecentMode(it) }
                )

                MainNavTab.LIBRARY -> LibraryScreen(
                    books = books,
                    shelves = shelves,
                    prefs = prefs,
                    uiState = uiState,
                    strings = strings,
                    onSetViewMode = { viewModel.setLibraryViewMode(it) },
                    onSearchQueryChange = { viewModel.setLibSearchQuery(it) },
                    onCycleSort = { viewModel.cycleLibrarySort() },
                    onSetSort = { viewModel.setLibrarySort(it) },
                    onSetFilter = { viewModel.setLibraryFilter(it) },
                    onSetStatus = { viewModel.setLibraryStatus(it) },
                    onSetShelf = { viewModel.setLibraryShelf(it) },
                    onSetGridCols = { viewModel.setLibraryGridCols(it) },
                    onResetFilters = { viewModel.resetLibraryFilters() },
                    onToggleFilterSheet = { viewModel.toggleShowLibFilterSheet(it) },
                    onAddCustomShelf = { viewModel.addCustomShelf(it) },
                    onDeleteCustomShelf = { viewModel.deleteCustomShelf(it) },
                    onOpenBook = { viewModel.openBookInReader(it) },
                    onToggleSelectBook = { viewModel.toggleBookSelection(it) },
                    onSelectAll = { viewModel.selectAllFilteredBooks(it) },
                    onExitSelection = { viewModel.exitSelectionMode() },
                    onOpenEditSelected = { viewModel.openEditSelectedBook() },
                    onCloseEditModal = { viewModel.closeEditBookModal() },
                    onSaveEditedBook = { viewModel.saveEditedBook(it) },
                    onShowDeleteConfirm = { viewModel.setShowDeleteConfirmDialog(it) },
                    onConfirmDeleteSelected = { viewModel.confirmDeleteSelectedBooks() }
                )

                MainNavTab.STATS -> StatsScreen(
                    books = books,
                    dailyLogs = dailyLogs,
                    highlights = allHighlights,
                    strings = strings,
                    onOpenBook = { viewModel.openBookInReader(it) }
                )

                MainNavTab.SETTINGS -> SettingsScreen(
                    prefs = prefs,
                    strings = strings,
                    booksCount = books.size,
                    highlightsCount = allHighlights.size,
                    onSetTheme = { viewModel.setTheme(it) },
                    onToggleGlass = { viewModel.toggleGlassDesign() },
                    onToggleAmoled = { viewModel.toggleAmoledBlack() },
                    onSetLanguage = { viewModel.setLanguage(it) },
                    onSetUserName = { viewModel.setUserName(it) },
                    onPickPhoto = onPickPhoto,
                    onResetPhoto = { viewModel.resetUserPhoto() },
                    onSavePhotoPath = { viewModel.setUserPhotoPath(it) },
                    onSetReadingFlowMode = { viewModel.setReadingFlowMode(it) },
                    onSetAnimatePageTurn = { viewModel.setAnimatePageTurn(it) },
                    onSetPageAnimStyle = { viewModel.setPageAnimStyle(it) },
                    onSetVolumeKeysTurn = { viewModel.setVolumeKeysTurn(it) },
                    onExportBackup = { onShareBackupJson(viewModel.buildBackupJsonString()) },
                    onRestoreBackup = onPickLocalFile
                )

                MainNavTab.READER -> {
                    val activeBook = books.find { it.id == uiState.activeBookId } ?: books.firstOrNull()
                    if (activeBook != null) {
                        val bookHighlights = allHighlights.filter { it.bookId == activeBook.id }
                        val metrics = viewModel.computeGlobalPageMetrics(
                            uiState.activeBookChapters,
                            uiState.activeChapterIndex,
                            uiState.activePageIndex
                        )
                        ReaderScreen(
                            book = activeBook,
                            uiState = uiState,
                            prefs = prefs,
                            bookHighlights = bookHighlights,
                            strings = strings,
                            metrics = metrics,
                            getPageAtGlobalZero = { gIdx ->
                                viewModel.getPageContentAtGlobalZeroIndex(uiState.activeBookChapters, gIdx)
                            },
                            onBack = { viewModel.switchTab(uiState.previousTab) },
                            onTurnPage = { viewModel.turnReaderPage(it) },
                            onJumpToChapterAndPage = { cIdx, pIdx -> viewModel.jumpToChapterAndPage(cIdx, pIdx) },
                            onToggleFullView = { viewModel.toggleReaderFullView() },
                            onToggleTypeSheet = { show, tab -> viewModel.setShowReaderTypeSheet(show, tab) },
                            onToggleTocDrawer = { viewModel.setShowReaderTocDrawer(it) },
                            onToggleNotesDrawer = { viewModel.setShowReaderNotesDrawer(it) },
                            onOpenIllustViewer = { viewModel.openIllustrationViewer(it) },
                            onSetIllustZoom = { viewModel.setIllustZoom(it) },
                            onCloseIllustViewer = { viewModel.closeIllustrationViewer() },
                            onSaveHighlightNote = { quote, note, color, id ->
                                viewModel.saveHighlightNote(quote, note, color, id)
                            },
                            onDeleteHighlightNote = { viewModel.deleteHighlightNote(it) },
                            onSearchInLibrary = { query ->
                                viewModel.setLibSearchQuery(query)
                                viewModel.switchTab(MainNavTab.LIBRARY)
                            },
                            onSetTheme = { viewModel.setTheme(it) },
                            onSetReadingFlowMode = { viewModel.setReadingFlowMode(it) },
                            onSetPageAnimStyle = { viewModel.setPageAnimStyle(it) },
                            onUpdateTypography = { fam, size, wt, align, lh, ps, ls, ws, pm, pi ->
                                viewModel.updateTypography(fam, size, wt, align, lh, ps, ls, ws, pm, pi)
                            }
                        )
                    } else {
                        LaunchedEffect(Unit) {
                            viewModel.switchTab(MainNavTab.HOME)
                        }
                    }
                }
            }
        }

        val showBottomDock = uiState.activeTab != MainNavTab.READER &&
            !uiState.showLibFilterSheet &&
            !uiState.showAddBookSheet &&
            !uiState.showFullCameraScanner &&
            uiState.editingBook == null &&
            !uiState.showDeleteConfirmDialog &&
            uiState.pendingCandidateForShelf == null

        if (showBottomDock) {
            // Subtle 96.dp bottom gradient fade scrim preventing text clash under dock
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                palette.bgApp.copy(alpha = 0.82f)
                            )
                        )
                    )
            )
            BottomFloatingNavDock(
                activeTab = uiState.activeTab,
                strings = strings,
                onSelectTab = { viewModel.switchTab(it) },
                onOpenAddBook = { viewModel.setShowAddBookSheet(true, "search") },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }

        if (uiState.showAddBookSheet && !uiState.showFullCameraScanner) {
            AddBookSheet(
                uiState = uiState,
                shelves = shelves,
                strings = strings,
                onDismiss = { viewModel.setShowAddBookSheet(false) },
                onSelectTab = { tab ->
                    viewModel.setShowAddBookSheet(true, tab)
                },
                onQueryChange = { viewModel.setCatalogSearchQuery(it) },
                onSelectCandidateDetail = { viewModel.openCatalogBookDetail(it) },
                onPromptSaveCandidate = { viewModel.promptChooseShelfForCandidate(it) },
                onDismissChooseShelf = { viewModel.promptChooseShelfForCandidate(null) },
                onSaveCandidateToShelf = { candidate, shelfName, format ->
                    viewModel.saveCandidateToShelf(candidate, shelfName, format)
                },
                onPickLocalFile = onPickLocalFile
            )
        }

        if (uiState.showFullCameraScanner) {
            IsbnCameraScannerScreen(
                uiState = uiState,
                strings = strings,
                defaultShelf = prefs.libShelf.ifBlank { "eReader" },
                onClose = { viewModel.closeFullCameraScanner() },
                onSetCameraMode = { viewModel.setCameraMode(it) },
                onToggleTorch = { viewModel.toggleCameraTorch() },
                onIsbnDetected = { viewModel.lookupIsbnFromScanner(it) },
                onSaveSingleCandidate = { viewModel.promptChooseShelfForCandidate(it) },
                onRemoveBatchItem = { viewModel.removeBatchItem(it) },
                onClearBatchQueue = { viewModel.clearBatchScanQueue() },
                onCommitBatchQueue = { viewModel.commitBatchScanToShelf(it) }
            )
        }

        // Minimal Floating Progress Bar when importing large PDF/EPUB in background
        val activeImportEntry = uiState.importingBooksProgress.entries.firstOrNull()
        if (activeImportEntry != null) {
            val pct = activeImportEntry.value
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = palette.sheetBg,
                border = BorderStroke(1.dp, palette.borderSoft),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 86.dp)
                    .widthIn(min = 200.dp, max = 280.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (strings.isId) "Mengimpor buku..." else "Importing book...",
                            color = palette.textMain,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "$pct%",
                            color = palette.textMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (pct / 100f).coerceIn(0.05f, 1f) },
                        color = palette.textMain,
                        trackColor = palette.borderSoft,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(CircleShape)
                    )
                }
            }
        }

        uiState.toastMessage?.let { msg ->
            Surface(
                shape = CircleShape,
                color = palette.sheetBg,
                border = BorderStroke(1.dp, palette.borderSoft),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (uiState.activeTab == MainNavTab.READER) 68.dp else 88.dp)
            ) {
                Text(
                    text = msg,
                    color = palette.textMain,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
