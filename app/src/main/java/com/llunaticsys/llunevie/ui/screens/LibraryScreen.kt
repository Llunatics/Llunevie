package com.llunaticsys.llunevie.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.os.Build
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.llunaticsys.llunevie.data.parser.SpineColorExtractor
import com.llunaticsys.llunevie.LlunevieUiState
import com.llunaticsys.llunevie.data.local.BookEntity
import com.llunaticsys.llunevie.data.local.DEFAULT_CURATED_GENRES
import com.llunaticsys.llunevie.data.local.ShelfEntity
import com.llunaticsys.llunevie.data.local.UserPreferencesState
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import com.llunaticsys.llunevie.model.LibrarySortOption
import com.llunaticsys.llunevie.model.LibraryViewMode
import com.llunaticsys.llunevie.ui.components.BookCoverCard
import com.llunaticsys.llunevie.ui.components.ClassicGlassSegmentedControl
import com.llunaticsys.llunevie.ui.components.CustomThemeDropdown
import com.llunaticsys.llunevie.ui.components.LlunevieCenteredTextField
import com.llunaticsys.llunevie.ui.components.LlunevieThinSlider
import com.llunaticsys.llunevie.ui.components.PrototypeIconType
import com.llunaticsys.llunevie.ui.components.PrototypeSvgIcon
import com.llunaticsys.llunevie.ui.theme.LlunevieStrings
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette
import com.llunaticsys.llunevie.ui.theme.shelfMatchesFilter
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun LibraryScreen(
    books: List<BookEntity>,
    shelves: List<ShelfEntity>,
    prefs: UserPreferencesState,
    uiState: LlunevieUiState,
    strings: LlunevieStrings,
    onSetViewMode: (LibraryViewMode) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCycleSort: () -> Unit,
    onSetSort: (LibrarySortOption) -> Unit,
    onSetFilter: (String) -> Unit,
    onSetStatus: (String) -> Unit = {},
    onSetCoverStyle: (String) -> Unit = {},
    onSetShelf: (String) -> Unit,
    onSetGenre: (String) -> Unit = {},
    onAddCustomGenre: (String) -> Unit = {},
    onDeleteCustomGenre: (String) -> Unit = {},
    onBatchAssignShelfAndGenres: (bookIds: List<String>, targetShelf: String?, addGenres: List<String>) -> Unit = { _, _, _ -> },
    onSetGridCols: (Int) -> Unit,
    onResetFilters: () -> Unit,
    onToggleFilterSheet: (Boolean) -> Unit,
    onAddCustomShelf: (String) -> Unit,
    onDeleteCustomShelf: (String) -> Unit,
    onOpenBook: (String) -> Unit,
    onToggleSelectBook: (String) -> Unit,
    onSelectAll: (List<String>) -> Unit,
    onExitSelection: () -> Unit,
    onOpenEditSelected: () -> Unit,
    onCloseEditModal: () -> Unit,
    onSaveEditedBook: (BookEntity) -> Unit,
    onShowDeleteConfirm: (Boolean) -> Unit,
    onConfirmDeleteSelected: () -> Unit,
    onUpdateSpineColor: (bookId: String, extractedHex: Long) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val palette = LocalLluneviePalette.current

    val allAvailableGenres = remember(prefs.customGenres, books) {
        (DEFAULT_CURATED_GENRES + prefs.customGenres + books.flatMap { it.genreList() }).distinctBy { it.lowercase() }
    }

    // Filter & sort books (matching `getFilteredLibraryBooks` in `llunevie_prototype.html`)
    val filteredBooks = remember(books, prefs.libShelf, prefs.libFilter, prefs.libGenre, prefs.libStatus, prefs.libSort, uiState.libSearchQuery) {
        val q = uiState.libSearchQuery.trim().lowercase()
        val list = books.filter { b ->
            val matchShelf = if (prefs.libShelf.equals("all", ignoreCase = true) || prefs.libShelf.isBlank()) {
                true
            } else {
                shelfMatchesFilter(b.shelf, prefs.libShelf)
            }
            val matchCat = prefs.libFilter == "all" || b.category.equals(prefs.libFilter, ignoreCase = true)
            val matchGenre = prefs.libGenre.equals("all", ignoreCase = true) || prefs.libGenre.isBlank() ||
                b.genreList().any { it.equals(prefs.libGenre, ignoreCase = true) }
            val matchStatus = when (prefs.libStatus.lowercase()) {
                "unread" -> b.progress == 0 && !b.completedAllPages
                "reading" -> b.progress in 1..99 && !b.completedAllPages
                "finished" -> b.completedAllPages || b.progress >= 100
                else -> true
            }
            val matchQ = q.isEmpty() ||
                b.title.lowercase().contains(q) ||
                b.author.lowercase().contains(q) ||
                b.shelf.lowercase().contains(q) ||
                b.genres.lowercase().contains(q)
            matchShelf && matchCat && matchGenre && matchStatus && matchQ
        }
        when (prefs.libSort) {
            LibrarySortOption.TITLE -> list.sortedBy { it.title.lowercase() }
            LibrarySortOption.AUTHOR -> list.sortedBy { it.author.lowercase() }
            LibrarySortOption.PROGRESS -> list.sortedByDescending { it.progress }
            LibrarySortOption.RECENT -> list
        }
    }

    val spineColorCache = remember { mutableStateMapOf<String, Color>() }
    val screenWidthDp = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val maxRowWidthDp = (screenWidthDp - 64).coerceAtLeast(240)
    val shelvesRows = remember(filteredBooks, maxRowWidthDp) {
        val rows = mutableListOf<List<Pair<Int, BookEntity>>>()
        var currentRow = mutableListOf<Pair<Int, BookEntity>>()
        var currentWidthDp = 0
        filteredBooks.forEachIndexed { globalIdx, b ->
            val pages = b.pagesCount.coerceIn(80, 950)
            val wDp = (38 + ((pages - 80) * 16) / (950 - 80)).coerceIn(38, 54)
            val footprint = wDp + if (currentRow.isNotEmpty()) 4 else 0
            if (currentRow.isNotEmpty() && currentWidthDp + footprint > maxRowWidthDp) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentWidthDp = 0
            }
            currentRow.add(globalIdx to b)
            currentWidthDp += footprint
        }
        if (currentRow.isNotEmpty()) rows.add(currentRow)
        rows
    }

    val gridCols = if (prefs.libGridCols in 2..5) prefs.libGridCols else if (prefs.libViewMode == LibraryViewMode.COVERS) 3 else 2
    val gridRows = remember(filteredBooks, gridCols) {
        filteredBooks.chunked(gridCols)
    }

    val hasActiveFilterDot = prefs.libFilter != "all" || prefs.libStatus != "all" || prefs.libSort != LibrarySortOption.RECENT || (prefs.libShelf != "all" && prefs.libShelf.isNotBlank()) || (prefs.libGenre != "all" && prefs.libGenre.isNotBlank())

    var showBatchOrganizeDialog by remember { mutableStateOf(false) }

    val isAnyModalOpenInLibrary = uiState.showLibFilterSheet ||
        uiState.editingBook != null ||
        uiState.showDeleteConfirmDialog ||
        showBatchOrganizeDialog

    val targetBlur = if (Build.VERSION.SDK_INT >= 31 && isAnyModalOpenInLibrary) 10.dp else 0.dp
    val animatedBlur by animateDpAsState(
        targetValue = targetBlur,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "libraryBackdropBlur"
    )
    val backgroundBlurModifier = if (Build.VERSION.SDK_INT >= 31 && animatedBlur > 0.5.dp) {
        Modifier.blur(animatedBlur)
    } else {
        Modifier
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(backgroundBlurModifier)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(top = 16.dp, bottom = 108.dp, start = 20.dp, end = 20.dp)
            ) {
                item(key = "library_top_header", contentType = "header") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        // 1. Header + 4 View Modes Switcher (`#viewLibrary` in `llunevie_prototype.html`)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = strings.libHeader,
                                        color = palette.textMain,
                                        fontSize = 26.sp,
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = strings.libSub,
                                    color = palette.textMuted,
                                    fontSize = 12.sp
                                )
                            }

                            // 4 View Mode Pills (Spine, Grid, Full Cover, List) with exact 1:1 prototype SVG icons
                            Surface(
                                shape = CircleShape,
                                color = palette.bgSurface,
                                border = BorderStroke(1.dp, palette.borderSoft)
                            ) {
                                Row(modifier = Modifier.padding(4.dp)) {
                                    val modes = listOf(
                                        Pair(LibraryViewMode.SPINE, PrototypeIconType.SPINE_SHELF),
                                        Pair(LibraryViewMode.GRID, PrototypeIconType.GRID_2X2),
                                        Pair(LibraryViewMode.COVERS, PrototypeIconType.FULL_COVER),
                                        Pair(LibraryViewMode.LIST, PrototypeIconType.LIST_BULLETS)
                                    )
                                    modes.forEach { (mode, iconType) ->
                                        val active = prefs.libViewMode == mode
                                        Surface(
                                            shape = CircleShape,
                                            color = if (active) palette.pillActiveBg else Color.Transparent,
                                            shadowElevation = if (active) 1.dp else 0.dp,
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(CircleShape)
                                                .semantics {
                                                    contentDescription = mode.name
                                                    role = Role.Tab
                                                }
                                                .clickable { onSetViewMode(mode) }
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                PrototypeSvgIcon(
                                                    type = iconType,
                                                    tint = if (active) palette.textMain else palette.textMuted,
                                                    modifier = Modifier.size(16.dp),
                                                    strokeWidthDp = 1.8f,
                                                    contentDescription = mode.name
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Search Bar + Sort (Direct 1-Click Cycle, Zero Popup!) & Filter Capsule
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = palette.bgSurface,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier.weight(1f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    PrototypeSvgIcon(
                                        type = PrototypeIconType.SEARCH,
                                        tint = palette.textMuted.copy(alpha = 0.65f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    LlunevieCenteredTextField(
                                        value = uiState.libSearchQuery,
                                        onValueChange = onSearchQueryChange,
                                        placeholder = strings.searchHint,
                                        fontSize = 13.sp,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                    if (uiState.libSearchQuery.isNotEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .sizeIn(minWidth = 28.dp, minHeight = 20.dp)
                                                .semantics {
                                                    contentDescription = if (strings.isId) "Hapus pencarian" else "Clear search"
                                                    role = Role.Button
                                                }
                                                .clickable { onSearchQueryChange("") }
                                                .padding(horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "✕",
                                                color = palette.textMuted,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }

                            // Combined Sort + Filter Capsule (`#libSortBtn` + `#libFilterDropdownBtn`)
                            Surface(
                                shape = CircleShape,
                                color = palette.bgSurface,
                                border = BorderStroke(1.dp, palette.borderSoft)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Direct 1-click sort cycle button (NO popup!)
                                    Row(
                                        modifier = Modifier
                                            .height(32.dp)
                                            .clip(CircleShape)
                                            .clickable { onCycleSort() }
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        PrototypeSvgIcon(
                                            type = PrototypeIconType.SORT_DESC,
                                            tint = palette.textMain,
                                            modifier = Modifier.size(14.dp),
                                            strokeWidthDp = 1.8f
                                        )
                                        Text(
                                            text = prefs.libSort.localizedLabel(prefs.lang),
                                            color = palette.textMain,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(16.dp)
                                            .background(palette.borderSoft)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .semantics {
                                                contentDescription = if (strings.isId) "Filter pustaka" else "Filter library"
                                                role = Role.Button
                                            }
                                            .clickable { onToggleFilterSheet(true) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PrototypeSvgIcon(
                                            type = PrototypeIconType.FILTER_FUNNEL,
                                            tint = palette.textMain,
                                            modifier = Modifier.size(16.dp),
                                            strokeWidthDp = 1.8f,
                                            contentDescription = if (strings.isId) "Filter pustaka" else "Filter library"
                                        )
                                        if (hasActiveFilterDot) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .padding(top = 6.dp, end = 6.dp)
                                                    .size(7.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFFF59E0B))
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // 3. Empty Shelf State or Books Content
                if (filteredBooks.isEmpty()) {
                    item(key = "library_empty_state", contentType = "empty") {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = palette.bgCard,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = strings.emptyShelfTitle,
                                    color = palette.textMain,
                                    fontSize = 18.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = strings.emptyShelfSub,
                                    color = palette.textMuted,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    when (prefs.libViewMode) {
                        // 1. SPINE SHELF MODE
                        LibraryViewMode.SPINE -> {
                            item(key = "spine_top_cap", contentType = "spine_frame") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                        .background(Color(0xFF4A3520).copy(alpha = 0.7f))
                                )
                            }
                            itemsIndexed(
                                items = shelvesRows,
                                key = { rowIdx, row -> "spine_row_${rowIdx}_${row.first().second.id}" },
                                contentType = { _, _ -> "spine_row" }
                            ) { rowIdx, shelfRow ->
                                var rowHoverX by remember { mutableStateOf<Float?>(null) }
                                val rowSpineBoundsMap = remember { mutableMapOf<Int, Pair<Float, Float>>() }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFF0A0806).copy(alpha = 0.65f))
                                        .drawBehind {
                                            val frameW = 4.dp.toPx()
                                            val frameColor = Color(0xFF4A3520).copy(alpha = 0.5f)
                                            drawRect(color = frameColor, topLeft = Offset.Zero, size = Size(frameW, size.height))
                                            drawRect(color = frameColor, topLeft = Offset(size.width - frameW, 0f), size = Size(frameW, size.height))
                                        }
                                        .padding(horizontal = 4.dp)
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(248.dp)
                                                .padding(horizontal = 8.dp)
                                                .pointerInput(shelfRow, uiState.libSelectMode) {
                                                    if (uiState.libSelectMode) return@pointerInput
                                                    detectHorizontalDragGestures(
                                                        onDragStart = { offset ->
                                                            rowHoverX = offset.x
                                                        },
                                                        onHorizontalDrag = { change, _ ->
                                                            rowHoverX = change.position.x
                                                        },
                                                        onDragEnd = { rowHoverX = null },
                                                        onDragCancel = { rowHoverX = null }
                                                    )
                                                },
                                            verticalAlignment = Alignment.Bottom,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
                                        ) {
                                            shelfRow.forEach { (globalIdx, book) ->
                                                val pages = book.pagesCount.coerceIn(80, 950)
                                                val wDp = (38 + ((pages - 80) * 16) / (950 - 80)).dp.coerceIn(38.dp, 54.dp)
                                                val hDp = remember(book.id, book.heightCm) {
                                                    if (book.heightCm > 0f) {
                                                        val clampedCm = book.heightCm.coerceIn(14f, 30f)
                                                        (176f + ((clampedCm - 14f) / 16f) * 76f).dp
                                                    } else {
                                                        (208 + (abs(book.id.hashCode()) % 28)).dp
                                                    }
                                                }
                                                val isSelected = uiState.libSelectedIds.contains(book.id)

                                                val isScrubbingOrSelected = rowHoverX != null || isSelected
                                                val isHovered = if (rowHoverX != null) {
                                                    rowSpineBoundsMap[globalIdx]?.let { (left, right) -> rowHoverX!! in left..right } == true
                                                } else false

                                                val liftOffset: Dp
                                                val scaleAnim: Float
                                                if (isScrubbingOrSelected) {
                                                    val targetLift = when {
                                                        isHovered || isSelected -> (-18).dp
                                                        else -> 0.dp
                                                    }
                                                    val targetScale = when {
                                                        isHovered -> 1.03f
                                                        else -> 1f
                                                    }
                                                    val animLift by animateDpAsState(
                                                        targetValue = targetLift,
                                                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                                        label = "spineLift"
                                                    )
                                                    val animScale by animateFloatAsState(
                                                        targetValue = targetScale,
                                                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                                        label = "spineScale"
                                                    )
                                                    liftOffset = animLift
                                                    scaleAnim = animScale
                                                } else {
                                                    liftOffset = 0.dp
                                                    scaleAnim = 1f
                                                }

                                                val spineShape = RoundedCornerShape(
                                                    topStart = 5.dp,
                                                    topEnd = 5.dp,
                                                    bottomStart = 3.dp,
                                                    bottomEnd = 3.dp
                                                )
                                                val spineTextColor = Color(book.spineTextColorHex)
                                                val shortTitle = remember(book.title) {
                                                    book.title.substringBefore(":").trim().ifBlank { book.title }
                                                }
                                                val firstAuthor = remember(book.author) {
                                                    book.author.trim().split(Regex("\\s+")).firstOrNull()?.uppercase().orEmpty()
                                                }

                                                val hasSavedColor = book.spineColorHex != 0L && book.spineColorHex != 0xFF2B3A4AL
                                                val effectiveSpineColor = remember(book.spineColorHex) {
                                                    if (hasSavedColor) Color(book.spineColorHex) else Color(0xFF2B3A4A)
                                                }

                                                if (!hasSavedColor && !book.coverImagePath.isNullOrBlank() && !book.coverImagePath.startsWith("http", ignoreCase = true)) {
                                                    LaunchedEffect(book.id, book.coverImagePath) {
                                                        val coverPath = book.coverImagePath
                                                        withContext(Dispatchers.IO) {
                                                            runCatching {
                                                                val opts = BitmapFactory.Options().apply {
                                                                    inSampleSize = 8
                                                                    inPreferredConfig = Bitmap.Config.RGB_565
                                                                }
                                                                val bmp = BitmapFactory.decodeFile(coverPath, opts)
                                                                if (bmp != null) {
                                                                    val extracted = SpineColorExtractor.extractDominantSpineHex(bmp)
                                                                    bmp.recycle()
                                                                    spineColorCache[book.id] = Color(extracted)
                                                                    onUpdateSpineColor(book.id, extracted)
                                                                }
                                                            }
                                                        }
                                                    }
                                                }

                                                val displaySpineColor = spineColorCache[book.id] ?: effectiveSpineColor

                                                Box(
                                                    modifier = Modifier
                                                        .width(wDp)
                                                        .height(hDp)
                                                        .zIndex(if (isHovered || isSelected) 25f else 1f)
                                                        .offset(y = liftOffset)
                                                        .scale(scaleAnim)
                                                        .onGloballyPositioned { coords ->
                                                            val left = coords.positionInParent().x
                                                            rowSpineBoundsMap[globalIdx] = Pair(left, left + coords.size.width)
                                                        }
                                                        .shadow(if (isHovered || isSelected) 14.dp else 6.dp, spineShape)
                                                        .clip(spineShape)
                                                        .background(displaySpineColor)
                                                        .background(
                                                            Brush.horizontalGradient(
                                                                colors = listOf(
                                                                    Color.White.copy(alpha = 0.28f),
                                                                    Color.White.copy(alpha = 0.06f),
                                                                    Color.Black.copy(alpha = 0.04f),
                                                                    Color.Black.copy(alpha = 0.22f)
                                                                )
                                                            )
                                                        )
                                                        .border(
                                                            width = if (isSelected) 2.5.dp else 1.dp,
                                                            color = if (isSelected) palette.textMain else Color.Black.copy(alpha = 0.12f),
                                                            shape = spineShape
                                                        )
                                                        .combinedClickable(
                                                            onClick = {
                                                                if (uiState.libSelectMode) onToggleSelectBook(book.id)
                                                                else onOpenBook(book.id)
                                                            },
                                                            onLongClick = { onToggleSelectBook(book.id) }
                                                        )
                                                ) {
                                                    // Left edge book-crease texture (5dp)
                                                    Box(
                                                        modifier = Modifier
                                                            .width(5.dp)
                                                            .fillMaxHeight()
                                                            .align(Alignment.CenterStart)
                                                            .background(
                                                                Brush.horizontalGradient(
                                                                    colors = listOf(
                                                                        Color.Black.copy(alpha = 0.24f),
                                                                        Color.White.copy(alpha = 0.14f),
                                                                        Color.Transparent
                                                                    )
                                                                )
                                                            )
                                                    )

                                                    // Ribbon bookmark tag if finished reading
                                                    if (book.progress >= 100 || book.completedAllPages) {
                                                        Box(
                                                            modifier = Modifier
                                                                .align(Alignment.TopEnd)
                                                                .padding(end = 5.dp)
                                                                .width(6.dp)
                                                                .height(18.dp)
                                                                .clip(RoundedCornerShape(bottomStart = 2.dp, bottomEnd = 2.dp))
                                                                .background(Color(0xFFD4A74A))
                                                        )
                                                    }

                                                    Column(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(vertical = 10.dp, horizontal = 2.dp),
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        if (uiState.libSelectMode) {
                                                            SelectionCheckBadge(
                                                                selected = isSelected,
                                                                sizeDp = 16.dp
                                                            )
                                                        } else {
                                                            Text(
                                                                text = "✦",
                                                                color = spineTextColor.copy(alpha = 0.6f),
                                                                fontSize = 8.sp,
                                                                fontFamily = FontFamily.Monospace
                                                            )
                                                        }

                                                        // Vertical Rotated Book Title (matching `writing-mode: vertical-rl` in prototype)
                                                        Box(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .fillMaxWidth(),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            VerticalRotatedText(
                                                                text = shortTitle,
                                                                color = spineTextColor,
                                                                fontSizeSp = 11.5f,
                                                                fontFamily = FontFamily.Serif,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }

                                                        // Page Count + Author First Name at Bottom of Spine
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                                        ) {
                                                            Text(
                                                                text = pages.toString(),
                                                                color = spineTextColor.copy(alpha = 0.72f),
                                                                fontSize = 8.5.sp,
                                                                fontFamily = FontFamily.Monospace
                                                            )
                                                            Box(
                                                                modifier = Modifier.height(44.dp),
                                                                contentAlignment = Alignment.Center
                                                            ) {
                                                                VerticalRotatedText(
                                                                    text = firstAuthor.take(8),
                                                                    color = spineTextColor.copy(alpha = 0.75f),
                                                                    fontSizeSp = 8f,
                                                                    fontFamily = FontFamily.Monospace,
                                                                    fontWeight = FontWeight.Medium
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                        // Flush Shelf Plank (`h-3.5 w-full rounded-full shadow-inner lg-surface`)
                                        Surface(
                                            shape = CircleShape,
                                            color = palette.bgSurface,
                                            border = BorderStroke(1.dp, palette.borderSoft),
                                            shadowElevation = 2.dp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(14.dp)
                                        ) {}
                                    }
                                }
                            }
                            item(key = "spine_bottom_base", contentType = "spine_frame") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp))
                                        .background(Color(0xFF4A3520).copy(alpha = 0.7f))
                                )
                            }
                        }

                        // 2. GRID MODE & 3. FULL COVER MODE (Novel 2:3 Aspect Ratio + 3dp Progress Bar in Grid Mode)
                        LibraryViewMode.GRID, LibraryViewMode.COVERS -> {
                            val isCoversOnly = prefs.libViewMode == LibraryViewMode.COVERS
                            itemsIndexed(
                                items = gridRows,
                                key = { rowIdx, row -> "grid_row_${rowIdx}_${row.first().id}" },
                                contentType = { _, _ -> "grid_row" }
                            ) { _, rowBooks ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = if (isCoversOnly) 14.dp else 18.dp),
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    rowBooks.forEach { book ->
                                        val isSelected = uiState.libSelectedIds.contains(book.id)
                                        val importPct = uiState.importingBooksProgress[book.id]
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .combinedClickable(
                                                    onClick = {
                                                        if (uiState.libSelectMode) onToggleSelectBook(book.id)
                                                        else onOpenBook(book.id)
                                                    },
                                                    onLongClick = { onToggleSelectBook(book.id) }
                                                )
                                        ) {
                                            Box {
                                                BookCoverCard(
                                                    book = book,
                                                    coverStyle = prefs.libCoverStyle,
                                                    showTitleInsideFallback = isCoversOnly,
                                                    importProgress = importPct,
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                                if (uiState.libSelectMode) {
                                                    SelectionCheckBadge(
                                                        selected = isSelected,
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(8.dp)
                                                    )
                                                }
                                            }
                                            if (!isCoversOnly) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                // 1:1 3px Reading Progress Bar below Cover in Grid Mode
                                                LinearProgressIndicator(
                                                    progress = { (book.progress / 100f).coerceIn(0f, 1f) },
                                                    color = palette.textMain,
                                                    trackColor = palette.borderSoft,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(3.dp)
                                                        .clip(CircleShape)
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = book.title,
                                                    color = palette.textMain,
                                                    fontSize = 14.sp,
                                                    fontFamily = FontFamily.Serif,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = book.author,
                                                    color = palette.textMuted,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                    repeat(gridCols - rowBooks.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        // 4. LIST MODE (1:1 with `renderLibrary` list mode in `llunevie_prototype.html`)
                        LibraryViewMode.LIST -> {
                            items(
                                items = filteredBooks,
                                key = { book -> "list_book_${book.id}" },
                                contentType = { "list_book" }
                            ) { book ->
                                val isSelected = uiState.libSelectedIds.contains(book.id)
                                val importPct = uiState.importingBooksProgress[book.id]
                                Box(modifier = Modifier.padding(bottom = 12.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(24.dp),
                                        color = palette.bgCard,
                                        border = BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) palette.textMain else palette.borderSoft
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(24.dp))
                                            .combinedClickable(
                                                onClick = {
                                                    if (uiState.libSelectMode) onToggleSelectBook(book.id)
                                                    else onOpenBook(book.id)
                                                },
                                                onLongClick = { onToggleSelectBook(book.id) }
                                            )
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            BookCoverCard(
                                                book = book,
                                                coverStyle = prefs.libCoverStyle,
                                                showTitleInsideFallback = false,
                                                importProgress = importPct,
                                                modifier = Modifier.width(56.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = book.title,
                                                    color = palette.textMain,
                                                    fontSize = 15.5.sp,
                                                    fontFamily = FontFamily.Serif,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = book.author,
                                                    color = palette.textMuted,
                                                    fontSize = 12.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                val metaText = remember(book.year, book.pagesCount, book.shelf, book.genres, strings.pagesUnit) {
                                                    val base = "${book.year} · ${book.pagesCount} ${strings.pagesUnit} · ${strings.localizeShelfName(book.shelf)}"
                                                    if (book.genres.isNotBlank()) "$base · ${book.genreList().take(3).joinToString(", ")}" else base
                                                }
                                                Text(
                                                    text = metaText,
                                                    color = palette.textMuted,
                                                    fontSize = 10.5.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Spacer(modifier = Modifier.height(10.dp))
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                                ) {
                                                    LinearProgressIndicator(
                                                        progress = { (book.progress / 100f).coerceIn(0f, 1f) },
                                                        color = palette.textMain,
                                                        trackColor = palette.borderSoft,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(6.dp)
                                                            .clip(CircleShape)
                                                    )
                                                    Text(
                                                        text = "${book.progress}% ${strings.readSuffix}",
                                                        color = palette.textMuted,
                                                        fontSize = 10.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                            if (uiState.libSelectMode) {
                                                SelectionCheckBadge(selected = isSelected)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item(key = "library_footer", contentType = "footer") {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "${filteredBooks.size} ${strings.booksUnit} · ${strings.localizeShelfName(prefs.libShelf).uppercase()}",
                            color = palette.textMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.6.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

        // FLOATING MULTI-SELECT TOP ACTION PILL (`#libSelectionBar` in `llunevie_prototype.html`)
        AnimatedVisibility(
            visible = uiState.libSelectMode,
            enter = expandVertically(spring(dampingRatio = 0.86f, stiffness = 400f)) + fadeIn(tween(160)),
            exit = shrinkVertically(tween(160)) + fadeOut(tween(120)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .fillMaxWidth()
        ) {
            Surface(
                shape = CircleShape,
                color = palette.sheetBg,
                border = BorderStroke(1.dp, palette.borderSoft),
                shadowElevation = 14.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = onExitSelection,
                            shape = CircleShape,
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "✕",
                                    color = palette.textMain,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        Text(
                            text = "${uiState.libSelectedIds.size} ${if (strings.isId) "dipilih" else "selected"}",
                            color = palette.textMain,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (uiState.libSelectedIds.size == 1) {
                            Surface(
                                onClick = onOpenEditSelected,
                                shape = CircleShape,
                                color = palette.bgSurface,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    PrototypeSvgIcon(
                                        type = PrototypeIconType.EDIT_PEN,
                                        tint = palette.textMain,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = if (strings.isId) "Edit" else "Edit",
                                        color = palette.textMain,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        } else {
                            Surface(
                                onClick = { showBatchOrganizeDialog = true },
                                shape = CircleShape,
                                color = palette.bgSurface,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Text(
                                        text = if (strings.isId) "Rak & Genre" else "Organize",
                                        color = palette.textMain,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                        Surface(
                            onClick = { onSelectAll(filteredBooks.map { it.id }) },
                            shape = CircleShape,
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Text(
                                    text = if (strings.isId) "Semua" else "Select All",
                                    color = palette.textMain,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        Surface(
                            onClick = { onShowDeleteConfirm(true) },
                            shape = CircleShape,
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                PrototypeSvgIcon(
                                    type = PrototypeIconType.TRASH_CAN,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(15.dp),
                                    contentDescription = if (strings.isId) "Hapus buku terpilih" else "Delete selected books"
                                )
                            }
                        }
                    }
                }
            }
        }
        }

        // 4. 1:1 LIBRARY FILTER & SHELVES BOTTOM SHEET (`#libDropdownMenu` in `llunevie_prototype.html`)
        // Backdrop Scrim
        AnimatedVisibility(
            visible = uiState.showLibFilterSheet,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(160))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onToggleFilterSheet(false) }
            )
        }

        // Filter Sheet Panel
        AnimatedVisibility(
            visible = uiState.showLibFilterSheet,
            enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 380f)) { it } + fadeIn(tween(180)),
            exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            var showManageShelves by remember { mutableStateOf(false) }
            var newShelfText by remember { mutableStateOf("") }
            var shelfPendingDelete by remember { mutableStateOf<String?>(null) }
            var showManageGenres by remember { mutableStateOf(false) }
            var newGenreText by remember { mutableStateOf("") }

            Surface(
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                color = palette.sheetBg,
                border = BorderStroke(1.dp, palette.borderSoft),
                shadowElevation = 24.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {}
            ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp)
                            .padding(top = 12.dp, bottom = 18.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Top Drag Handle
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .width(40.dp)
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(palette.textMain.copy(alpha = 0.2f))
                        )

                        // Header Row: Title + Subtitle on Left, [Atur Ulang] + [✕] on Right
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.filterTitle,
                                    color = palette.textMain,
                                    fontSize = 22.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = strings.filterSub,
                                    color = palette.textMuted,
                                    fontSize = 12.sp
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    onClick = onResetFilters,
                                    shape = CircleShape,
                                    color = palette.bgSurface,
                                    border = BorderStroke(1.dp, palette.borderSoft),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = strings.resetBtn,
                                            color = palette.textMain,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            style = TextStyle(
                                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                lineHeight = 12.sp,
                                                lineHeightStyle = LineHeightStyle(
                                                    alignment = LineHeightStyle.Alignment.Center,
                                                    trim = LineHeightStyle.Trim.Both
                                                )
                                            )
                                        )
                                    }
                                }
                                Surface(
                                    onClick = { onToggleFilterSheet(false) },
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
                        }

                        // 1. RAK BUKU + Inline Expandable "Kelola Rak"
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 28.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = strings.shelfLabel,
                                    color = palette.textMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.2.sp
                                )
                                Surface(
                                    onClick = { showManageShelves = !showManageShelves },
                                    shape = CircleShape,
                                    color = if (showManageShelves) palette.accentDark else palette.bgSurface,
                                    border = BorderStroke(1.dp, if (showManageShelves) palette.accentDark else palette.borderSoft),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = strings.manageShelvesBtn,
                                            color = if (showManageShelves) palette.bgApp else palette.textMain,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            style = TextStyle(
                                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                lineHeight = 10.5.sp,
                                                lineHeightStyle = LineHeightStyle(
                                                    alignment = LineHeightStyle.Alignment.Center,
                                                    trim = LineHeightStyle.Trim.Both
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // "Semua" (All) as the very first shelf chip
                                val isAllSelected = prefs.libShelf.equals("all", ignoreCase = true) || prefs.libShelf.isBlank()
                                val allChipBg = if (isAllSelected) palette.accentDark else palette.bgSurface
                                val allChipBorder = if (isAllSelected) palette.accentDark else palette.borderSoft
                                val allChipText = if (isAllSelected) palette.bgApp else palette.textMuted

                                item(key = "shelf_all") {
                                    Surface(
                                        onClick = { onSetShelf("all") },
                                        shape = CircleShape,
                                        color = allChipBg,
                                        border = BorderStroke(1.dp, allChipBorder),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                                            Text(
                                                text = strings.shelfAll,
                                                color = allChipText,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isAllSelected) FontWeight.SemiBold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                items(shelves, key = { it.name }) { shelf ->
                                    val selected = !isAllSelected && shelfMatchesFilter(shelf.name, prefs.libShelf)
                                    val chipBg = if (selected) palette.accentDark else palette.bgSurface
                                    val chipBorder = if (selected) palette.accentDark else palette.borderSoft
                                    val chipText = if (selected) palette.bgApp else palette.textMuted

                                    Surface(
                                        onClick = { onSetShelf(shelf.name) },
                                        shape = CircleShape,
                                        color = chipBg,
                                        border = BorderStroke(1.dp, chipBorder),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                                            Text(
                                                text = strings.localizeShelfName(shelf.name),
                                                color = chipText,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            // Inline Expandable Manage Shelves Panel (`#inlineManageShelvesPanel`)
                            if (showManageShelves) {
                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = palette.borderSoft)
                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = palette.bgSurface,
                                        border = BorderStroke(1.dp, palette.borderSoft),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            LlunevieCenteredTextField(
                                                value = newShelfText,
                                                onValueChange = { newShelfText = it },
                                                placeholder = strings.newShelfHint,
                                                fontSize = 12.sp,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                    Surface(
                                        onClick = {
                                            if (newShelfText.isNotBlank()) {
                                                onAddCustomShelf(newShelfText)
                                                newShelfText = ""
                                            }
                                        },
                                        shape = CircleShape,
                                        color = palette.accentDark
                                    ) {
                                        Text(
                                            text = strings.addShelfBtn,
                                            color = palette.bgApp,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                Column(
                                    modifier = Modifier.heightIn(max = 200.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    shelves.forEach { shelfItem ->
                                        val displayLabel = strings.localizeShelfName(shelfItem.name)
                                        val bookCountInShelf = books.count { shelfMatchesFilter(it.shelf, shelfItem.name) }
                                        val canDelete = shelves.size > 1
                                        val isConfirming = shelfPendingDelete == shelfItem.name

                                        if (isConfirming) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(Color(0xFFEF4444).copy(alpha = 0.08f))
                                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = "${strings.confirmDeleteShelfTitle} \"$displayLabel\"?",
                                                        color = Color(0xFFDC2626),
                                                        fontSize = 11.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = if (strings.isId) "Buku dipindah ke rak utama" else "Books moved to primary shelf",
                                                        color = palette.textMuted,
                                                        fontSize = 10.sp
                                                    )
                                                }
                                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                    Surface(
                                                        onClick = { shelfPendingDelete = null },
                                                        shape = CircleShape,
                                                        color = Color.Transparent
                                                    ) {
                                                        Text(
                                                            text = strings.cancelBtn,
                                                            color = palette.textMain,
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                    Surface(
                                                        onClick = {
                                                            shelfPendingDelete = null
                                                            onDeleteCustomShelf(shelfItem.name)
                                                        },
                                                        shape = CircleShape,
                                                        color = Color(0xFFEF4444).copy(alpha = 0.15f)
                                                    ) {
                                                        Text(
                                                            text = strings.deleteShelfBtn,
                                                            color = Color(0xFFDC2626),
                                                            fontSize = 11.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        } else {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .padding(horizontal = 12.dp, vertical = 7.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = displayLabel,
                                                        color = palette.textMain,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    if (shelfItem.isDefault) {
                                                        Text(
                                                            text = "(${strings.defaultShelfBadge.lowercase()})",
                                                            color = palette.textMuted.copy(alpha = 0.6f),
                                                            fontSize = 9.5.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                    Text(
                                                        text = "· $bookCountInShelf ${strings.booksLower}",
                                                        color = palette.textMuted.copy(alpha = 0.75f),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                                if (canDelete) {
                                                    Text(
                                                        text = strings.deleteShelfBtn,
                                                        color = Color(0xFFDC2626),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier
                                                            .clip(CircleShape)
                                                            .clickable { shelfPendingDelete = shelfItem.name }
                                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. GENRE / TAG Section + Inline Expandable "Kelola Genre"
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 28.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (strings.isId) "GENRE / TAG" else "GENRE / TAGS",
                                    color = palette.textMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.2.sp
                                )
                                Surface(
                                    onClick = { showManageGenres = !showManageGenres },
                                    shape = CircleShape,
                                    color = if (showManageGenres) palette.accentDark else palette.bgSurface,
                                    border = BorderStroke(1.dp, if (showManageGenres) palette.accentDark else palette.borderSoft),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(horizontal = 12.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (strings.isId) "Kelola Genre" else "Manage Genres",
                                            color = if (showManageGenres) palette.bgApp else palette.textMain,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            style = TextStyle(
                                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                lineHeight = 10.5.sp,
                                                lineHeightStyle = LineHeightStyle(
                                                    alignment = LineHeightStyle.Alignment.Center,
                                                    trim = LineHeightStyle.Trim.Both
                                                )
                                            )
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val isAllGenreSelected = prefs.libGenre.equals("all", ignoreCase = true) || prefs.libGenre.isBlank()
                                item(key = "genre_all") {
                                    Surface(
                                        onClick = { onSetGenre("all") },
                                        shape = CircleShape,
                                        color = if (isAllGenreSelected) palette.accentDark else palette.bgSurface,
                                        border = BorderStroke(1.dp, if (isAllGenreSelected) palette.accentDark else palette.borderSoft),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                                            Text(
                                                text = if (strings.isId) "Semua" else "All",
                                                color = if (isAllGenreSelected) palette.bgApp else palette.textMuted,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (isAllGenreSelected) FontWeight.SemiBold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                items(allAvailableGenres, key = { it }) { genre ->
                                    val selected = !isAllGenreSelected && prefs.libGenre.equals(genre, ignoreCase = true)
                                    Surface(
                                        onClick = {
                                            if (selected) onSetGenre("all") else onSetGenre(genre)
                                        },
                                        shape = CircleShape,
                                        color = if (selected) palette.accentDark else palette.bgSurface,
                                        border = BorderStroke(1.dp, if (selected) palette.accentDark else palette.borderSoft),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                                            Text(
                                                text = genre,
                                                color = if (selected) palette.bgApp else palette.textMuted,
                                                fontSize = 11.5.sp,
                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }

                            if (showManageGenres) {
                                Spacer(modifier = Modifier.height(14.dp))
                                HorizontalDivider(color = palette.borderSoft)
                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = palette.bgSurface,
                                        border = BorderStroke(1.dp, palette.borderSoft),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(38.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 16.dp),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            LlunevieCenteredTextField(
                                                value = newGenreText,
                                                onValueChange = { newGenreText = it },
                                                placeholder = if (strings.isId) "Nama genre baru (mis. Romcom)..." else "New genre tag (e.g. Romcom)...",
                                                fontSize = 12.sp,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                    Surface(
                                        onClick = {
                                            if (newGenreText.isNotBlank()) {
                                                onAddCustomGenre(newGenreText.trim())
                                                newGenreText = ""
                                            }
                                        },
                                        shape = CircleShape,
                                        color = palette.accentDark
                                    ) {
                                        Text(
                                            text = if (strings.isId) "Tambah" else "Add",
                                            color = palette.bgApp,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                                        )
                                    }
                                }

                                if (prefs.customGenres.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Column(
                                        modifier = Modifier.heightIn(max = 200.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        prefs.customGenres.sorted().forEach { customGenre ->
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(palette.bgSurface.copy(alpha = 0.5f))
                                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = customGenre,
                                                    color = palette.textMain,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Surface(
                                                    onClick = { onDeleteCustomGenre(customGenre) },
                                                    shape = CircleShape,
                                                    color = Color(0xFFEF4444).copy(alpha = 0.12f)
                                                ) {
                                                    Text(
                                                        text = if (strings.isId) "Hapus" else "Delete",
                                                        color = Color(0xFFEF4444),
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2.5 GAYA SAMPUL BUKU (BOOK COVER STYLE) Segmented Control
                        Column {
                            Text(
                                text = if (strings.isId) "GAYA SAMPUL BUKU" else "BOOK COVER STYLE",
                                color = palette.textMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            val coverStyleOptions = listOf(
                                "rounded" to (if (strings.isId) "Membulat" else "Rounded"),
                                "flat_crease" to (if (strings.isId) "Buku 2D" else "Flat Crease"),
                                "book_3d" to (if (strings.isId) "Buku 3D" else "3D Pages")
                            )
                            ClassicGlassSegmentedControl(
                                items = coverStyleOptions,
                                selectedItem = prefs.libCoverStyle,
                                onItemSelected = onSetCoverStyle
                            )
                        }

                        // 3. STATUS BACA (READING STATUS) Segmented Control
                        Column {
                            Text(
                                text = strings.statusLabel,
                                color = palette.textMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            ClassicGlassSegmentedControl(
                                items = listOf(
                                    "all" to strings.statusAll,
                                    "unread" to strings.statusUnread,
                                    "reading" to strings.statusReading,
                                    "finished" to strings.statusFinished
                                ),
                                selectedItem = prefs.libStatus,
                                onItemSelected = onSetStatus
                            )
                        }

                        // 4. KATEGORI Segmented Control
                        Column {
                            Text(
                                text = strings.catLabel,
                                color = palette.textMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            ClassicGlassSegmentedControl(
                                items = listOf(
                                    "all" to strings.catAll,
                                    "Light Novel" to "Light Novel",
                                    "Novel" to "Novel"
                                ),
                                selectedItem = prefs.libFilter,
                                onItemSelected = onSetFilter
                            )
                        }

                        // 4. URUTKAN BERDASARKAN Segmented Control (4 options)
                        Column {
                            Text(
                                text = strings.sortByLabel,
                                color = palette.textMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            ClassicGlassSegmentedControl(
                                items = LibrarySortOption.entries.map { opt -> opt to opt.localizedLabel(prefs.lang) },
                                selectedItem = prefs.libSort,
                                onItemSelected = onSetSort
                            )
                        }

                        // 5. BUKU PER BARIS (GRID / COVER) Clean 2–5 Slider
                        Column(modifier = Modifier.fillMaxWidth()) {
                            val activeCols = if (prefs.libGridCols in 2..5) {
                                prefs.libGridCols
                            } else {
                                if (prefs.libViewMode == LibraryViewMode.COVERS) 3 else 2
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (strings.isEn) "BOOKS PER ROW (GRID / COVER)" else "BUKU PER BARIS (GRID / SAMPUL)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.8.sp,
                                    color = palette.textMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = if (strings.isEn) "$activeCols per row" else "$activeCols buku",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = FontFamily.Monospace,
                                    color = palette.textMain
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LlunevieThinSlider(
                                value = activeCols.toFloat(),
                                onValueChange = { onSetGridCols(it.roundToInt().coerceIn(2, 5)) },
                                valueRange = 2f..5f,
                                steps = 2
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf(2, 3, 4, 5).forEach { n ->
                                    Text(
                                        text = "$n",
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = if (activeCols == n) FontWeight.Bold else FontWeight.Normal,
                                        color = if (activeCols == n) palette.textMain else palette.textMuted
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

    // 5. EDIT BOOK DETAILS MODAL (`#editBookDetailModal` in `llunevie_prototype.html`)
    uiState.editingBook?.let { editing ->
        var title by remember(editing) { mutableStateOf(editing.title) }
        var author by remember(editing) { mutableStateOf(editing.author) }
        var publisher by remember(editing) { mutableStateOf(editing.publisher) }
        var shelf by remember(editing) { mutableStateOf(editing.shelf) }
        var category by remember(editing) { mutableStateOf(editing.category) }
        var year by remember(editing) { mutableStateOf(editing.year) }
        var pagesStr by remember(editing) { mutableStateOf(editing.pagesCount.toString()) }
        var isbnStr by remember(editing) { mutableStateOf(editing.isbn) }
        var coverUrlStr by remember(editing) { mutableStateOf(editing.coverImagePath.orEmpty()) }
        var synopsis by remember(editing) { mutableStateOf(editing.synopsis) }
        var selectedGenres by remember(editing) { mutableStateOf(editing.genreList().toSet()) }
        var showInlineAddGenre by remember { mutableStateOf(false) }
        var inlineNewGenre by remember { mutableStateOf("") }

        val parsedDim = remember(editing) {
            val raw = editing.dimensionsText
            val pMatch = Regex("""(?:P|T|Panjang|Tinggi):\s*([0-9.,]+)""", RegexOption.IGNORE_CASE).find(raw)
            val lMatch = Regex("""(?:L|Lebar):\s*([0-9.,]+)""", RegexOption.IGNORE_CASE).find(raw)
            val bMatch = Regex("""(?:B|Berat|Tebal):\s*([0-9.,]+)""", RegexOption.IGNORE_CASE).find(raw)
            Triple(
                pMatch?.groupValues?.get(1).orEmpty(),
                lMatch?.groupValues?.get(1).orEmpty(),
                bMatch?.groupValues?.get(1).orEmpty()
            )
        }
        var lengthCm by remember(editing) {
            mutableStateOf(if (editing.heightCm > 0f) "${editing.heightCm}" else parsedDim.first)
        }
        var widthCm by remember(editing) {
            mutableStateOf(if (editing.widthCm > 0f) "${editing.widthCm}" else parsedDim.second)
        }
        var weightKg by remember(editing) {
            mutableStateOf(if (editing.weightGrams > 0f) "${editing.weightGrams / 1000f}" else parsedDim.third)
        }

        Dialog(onDismissRequest = onCloseEditModal) {
            Surface(
                shape = RoundedCornerShape(30.dp),
                color = palette.sheetBg,
                border = BorderStroke(1.dp, palette.borderSoft),
                shadowElevation = 24.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (strings.isId) "Edit Detail Buku" else "Edit Book Details",
                                color = palette.textMain,
                                fontSize = 20.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (strings.isId) "Perbarui metadata, rak, sampul, atau sinopsis buku" else "Update metadata, shelf, cover, or synopsis",
                                color = palette.textMuted,
                                fontSize = 11.sp
                            )
                        }
                        Surface(
                            onClick = onCloseEditModal,
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

                    HorizontalDivider(color = palette.borderSoft)

                    PrototypeFormInput(
                        label = if (strings.isId) "JUDUL BUKU" else "BOOK TITLE",
                        value = title,
                        onValueChange = { title = it }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrototypeFormInput(
                            label = if (strings.isId) "PENULIS" else "AUTHOR",
                            value = author,
                            onValueChange = { author = it },
                            modifier = Modifier.weight(1f)
                        )
                        PrototypeFormInput(
                            label = if (strings.isId) "PENERBIT / EDISI" else "PUBLISHER / EDITION",
                            value = publisher,
                            onValueChange = { publisher = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.shelfLabel,
                                color = palette.textMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            CustomThemeDropdown(
                                title = strings.shelfLabel,
                                selectedValue = shelf,
                                options = shelves.map { it.name to strings.localizeShelfName(it.name) },
                                onValueSelected = { shelf = it },
                                cornerRadius = 16.dp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strings.catLabel,
                                color = palette.textMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            CustomThemeDropdown(
                                title = strings.catLabel,
                                selectedValue = category,
                                options = listOf("Novel" to "Novel", "Light Novel" to "Light Novel"),
                                onValueSelected = { category = it },
                                cornerRadius = 16.dp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // GENRE / TAG
                    Column {
                        Text(
                            text = if (strings.isId) "GENRE / TAG" else "GENRE / TAGS",
                            color = palette.textMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val editAvailableGenres = (allAvailableGenres + selectedGenres).distinctBy { it.lowercase() }
                            editAvailableGenres.forEach { g ->
                                val isSel = selectedGenres.any { it.equals(g, ignoreCase = true) }
                                Surface(
                                    onClick = {
                                        selectedGenres = if (isSel) {
                                            selectedGenres.filterNot { it.equals(g, ignoreCase = true) }.toSet()
                                        } else {
                                            selectedGenres + g
                                        }
                                    },
                                    shape = CircleShape,
                                    color = if (isSel) palette.textMain else palette.bgSurface,
                                    border = BorderStroke(1.dp, if (isSel) palette.textMain else palette.borderSoft),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                        Text(
                                            text = g,
                                            color = if (isSel) palette.bgApp else palette.textMuted,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                            Surface(
                                onClick = { showInlineAddGenre = !showInlineAddGenre },
                                shape = CircleShape,
                                color = if (showInlineAddGenre) palette.textMain else palette.bgSurface,
                                border = BorderStroke(1.dp, if (showInlineAddGenre) palette.textMain else palette.borderSoft),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                    Text(
                                        text = "+ Genre",
                                        color = if (showInlineAddGenre) palette.bgApp else palette.textMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                        if (showInlineAddGenre) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = palette.bgSurface,
                                    border = BorderStroke(1.dp, palette.borderSoft),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        BasicTextField(
                                            value = inlineNewGenre,
                                            onValueChange = { inlineNewGenre = it },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                color = palette.textMain,
                                                fontSize = 12.sp,
                                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                                            ),
                                            cursorBrush = SolidColor(palette.textMain),
                                            decorationBox = { innerTextField ->
                                                if (inlineNewGenre.isEmpty()) {
                                                    Text(
                                                        text = if (strings.isId) "+ Tambah genre baru..." else "+ Add new genre...",
                                                        color = palette.textMuted,
                                                        fontSize = 11.5.sp
                                                    )
                                                }
                                                innerTextField()
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                Surface(
                                    onClick = {
                                        val trimmed = inlineNewGenre.trim()
                                        if (trimmed.isNotEmpty()) {
                                            onAddCustomGenre(trimmed)
                                            selectedGenres = selectedGenres + trimmed
                                            inlineNewGenre = ""
                                            showInlineAddGenre = false
                                        }
                                    },
                                    shape = CircleShape,
                                    color = palette.accentDark,
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                                        Text(
                                            text = if (strings.isId) "Tambah" else "Add",
                                            color = palette.bgApp,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrototypeFormInput(
                            label = if (strings.isId) "TAHUN" else "YEAR",
                            value = year,
                            onValueChange = { year = it },
                            modifier = Modifier.weight(1f)
                        )
                        PrototypeFormInput(
                            label = if (strings.isId) "HALAMAN" else "PAGES",
                            value = pagesStr,
                            onValueChange = { pagesStr = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PrototypeFormInput(
                            label = "ISBN",
                            value = isbnStr,
                            onValueChange = { isbnStr = it },
                            modifier = Modifier.weight(1f)
                        )
                        PrototypeFormInput(
                            label = if (strings.isId) "URL SAMPUL" else "COVER URL",
                            value = coverUrlStr,
                            onValueChange = { coverUrlStr = it },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Column {
                        Text(
                            text = if (strings.isId) "DIMENSI (PANJANG/TINGGI · LEBAR · BERAT/TEBAL)" else "DIMENSIONS (LENGTH/HEIGHT · WIDTH · WEIGHT)",
                            color = palette.textMuted,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PrototypeFormInput(
                                label = "",
                                value = lengthCm,
                                onValueChange = { lengthCm = it },
                                placeholder = if (strings.isId) "Tinggi/P (cm)" else "Height (cm)",
                                modifier = Modifier.weight(1f)
                            )
                            PrototypeFormInput(
                                label = "",
                                value = widthCm,
                                onValueChange = { widthCm = it },
                                placeholder = if (strings.isId) "Lebar (cm)" else "Width (cm)",
                                modifier = Modifier.weight(1f)
                            )
                            PrototypeFormInput(
                                label = "",
                                value = weightKg,
                                onValueChange = { weightKg = it },
                                placeholder = if (strings.isId) "Berat/Tebal" else "Weight/Thick",
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    PrototypeFormInput(
                        label = if (strings.isId) "SINOPSIS BUKU" else "BOOK SYNOPSIS",
                        value = synopsis,
                        onValueChange = { synopsis = it },
                        singleLine = false
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = onCloseEditModal,
                            shape = CircleShape,
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = strings.cancelBtn,
                                color = palette.textMain,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                        Surface(
                            onClick = {
                                val parsedHeight = lengthCm.replace(',', '.').toFloatOrNull() ?: editing.heightCm
                                val parsedWidth = widthCm.replace(',', '.').toFloatOrNull() ?: editing.widthCm
                                val parsedWeight = weightKg.replace(',', '.').toFloatOrNull()?.let { it * 1000f } ?: editing.weightGrams
                                val dimInfo = if (lengthCm.isNotBlank() || widthCm.isNotBlank() || weightKg.isNotBlank()) {
                                    listOfNotNull(
                                        lengthCm.ifBlank { null }?.let { "P: ${it}cm" },
                                        widthCm.ifBlank { null }?.let { "L: ${it}cm" },
                                        weightKg.ifBlank { null }?.let { "B: ${it}kg" }
                                    ).joinToString(" · ")
                                } else editing.dimensionsText

                                onSaveEditedBook(
                                    editing.copy(
                                        title = title.ifBlank { editing.title },
                                        author = author.ifBlank { editing.author },
                                        publisher = publisher,
                                        edition = publisher,
                                        shelf = shelf,
                                        category = category,
                                        format = editing.format,
                                        year = year.ifBlank { editing.year },
                                        pagesCount = pagesStr.toIntOrNull()?.coerceAtLeast(1) ?: editing.pagesCount,
                                        progress = editing.progress,
                                        timeMinutes = editing.timeMinutes,
                                        readingSeconds = editing.readingSeconds,
                                        isbn = isbnStr.ifBlank { "—" },
                                        coverImagePath = coverUrlStr.ifBlank { editing.coverImagePath },
                                        synopsis = synopsis,
                                        dimensionsText = dimInfo,
                                        heightCm = parsedHeight,
                                        widthCm = parsedWidth,
                                        lengthCm = parsedHeight,
                                        weightGrams = parsedWeight,
                                        genres = selectedGenres.joinToString(", ")
                                    )
                                )
                            },
                            shape = CircleShape,
                            color = palette.textMain,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (strings.isId) "Simpan Perubahan" else "Save Changes",
                                color = palette.bgApp,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 12.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // 6. DELETE CONFIRMATION POPUP DIALOG (`#deleteBooksConfirmModal` in `llunevie_prototype.html`)
    if (uiState.showDeleteConfirmDialog) {
        val count = uiState.libSelectedIds.size
        Dialog(onDismissRequest = { onShowDeleteConfirm(false) }) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = palette.sheetBg,
                border = BorderStroke(1.dp, palette.borderSoft),
                shadowElevation = 24.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = Color(0xFFEF4444).copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                PrototypeSvgIcon(
                                    type = PrototypeIconType.TRASH_CAN,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = if (strings.isId) "Hapus Buku?" else "Delete Books?",
                                color = palette.textMain,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (strings.isId) "Terpilih: $count" else "Selected: $count",
                                color = palette.textMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (strings.isId) "Apakah kamu yakin ingin menghapus buku yang dipilih dari pustaka? Tindakan ini tidak dapat dibatalkan."
                        else "Are you sure you want to delete the selected book(s) from your library? This action cannot be undone.",
                        color = palette.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = { onShowDeleteConfirm(false) },
                            shape = CircleShape,
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = strings.cancelBtn,
                                color = palette.textMain,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                        Surface(
                            onClick = onConfirmDeleteSelected,
                            shape = CircleShape,
                            color = Color(0xFFDC2626),
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier.padding(vertical = 10.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PrototypeSvgIcon(
                                    type = PrototypeIconType.TRASH_CAN,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (strings.isId) "Hapus" else "Delete",
                                    color = Color.White,
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

    // 7. BATCH ORGANIZE SHELF & GENRES DIALOG
    if (showBatchOrganizeDialog && uiState.libSelectedIds.isNotEmpty()) {
        var chosenShelf by remember { mutableStateOf("__KEEP__") }
        var chosenGenres by remember { mutableStateOf(setOf<String>()) }
        var showInlineAddGenre by remember { mutableStateOf(false) }
        var inlineNewGenre by remember { mutableStateOf("") }

        Dialog(onDismissRequest = { showBatchOrganizeDialog = false }) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = palette.sheetBg,
                border = BorderStroke(1.dp, palette.borderSoft),
                shadowElevation = 24.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (strings.isId) "Atur Rak & Genre" else "Organize Shelf & Genres",
                                color = palette.textMain,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = if (strings.isId) "${uiState.libSelectedIds.size} buku dipilih" else "${uiState.libSelectedIds.size} books selected",
                                color = palette.textMuted,
                                fontSize = 11.sp
                            )
                        }
                        Surface(
                            onClick = { showBatchOrganizeDialog = false },
                            shape = CircleShape,
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.size(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("✕", color = palette.textMain, fontSize = 12.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = palette.borderSoft)

                    // Section 1: Target Shelf
                    Column {
                        Text(
                            text = strings.shelfLabel,
                            color = palette.textMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        CustomThemeDropdown(
                            title = strings.shelfLabel,
                            selectedValue = chosenShelf,
                            options = listOf("__KEEP__" to (if (strings.isId) "Pertahankan Rak Saat Ini" else "Keep Current Shelf")) + shelves.map { it.name to strings.localizeShelfName(it.name) },
                            onValueSelected = { chosenShelf = it },
                            cornerRadius = 16.dp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Section 2: Genres
                    Column {
                        Text(
                            text = if (strings.isId) "TAG GENRE (PILIH UNTUK DITAMBAHKAN)" else "GENRE TAGS (SELECT TO APPLY)",
                            color = palette.textMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.2.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val availableForBatch = (allAvailableGenres + chosenGenres).distinctBy { it.lowercase() }
                            availableForBatch.forEach { g ->
                                val isSel = chosenGenres.any { it.equals(g, ignoreCase = true) }
                                Surface(
                                    onClick = {
                                        chosenGenres = if (isSel) {
                                            chosenGenres.filterNot { it.equals(g, ignoreCase = true) }.toSet()
                                        } else {
                                            chosenGenres + g
                                        }
                                    },
                                    shape = CircleShape,
                                    color = if (isSel) palette.textMain else palette.bgSurface,
                                    border = BorderStroke(1.dp, if (isSel) palette.textMain else palette.borderSoft),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                        Text(
                                            text = g,
                                            color = if (isSel) palette.bgApp else palette.textMuted,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSel) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                            Surface(
                                onClick = { showInlineAddGenre = !showInlineAddGenre },
                                shape = CircleShape,
                                color = if (showInlineAddGenre) palette.textMain else palette.bgSurface,
                                border = BorderStroke(1.dp, if (showInlineAddGenre) palette.textMain else palette.borderSoft),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                    Text(
                                        text = "+ Genre",
                                        color = if (showInlineAddGenre) palette.bgApp else palette.textMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        softWrap = false
                                    )
                                }
                            }
                        }
                        if (showInlineAddGenre) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = palette.bgSurface,
                                    border = BorderStroke(1.dp, palette.borderSoft),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 14.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        BasicTextField(
                                            value = inlineNewGenre,
                                            onValueChange = { inlineNewGenre = it },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                color = palette.textMain,
                                                fontSize = 12.sp,
                                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                                            ),
                                            cursorBrush = SolidColor(palette.textMain),
                                            decorationBox = { innerTextField ->
                                                if (inlineNewGenre.isEmpty()) {
                                                    Text(
                                                        text = if (strings.isId) "+ Tambah genre baru..." else "+ Add new genre...",
                                                        color = palette.textMuted,
                                                        fontSize = 11.5.sp
                                                    )
                                                }
                                                innerTextField()
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                                Surface(
                                    onClick = {
                                        val trimmed = inlineNewGenre.trim()
                                        if (trimmed.isNotEmpty()) {
                                            onAddCustomGenre(trimmed)
                                            chosenGenres = chosenGenres + trimmed
                                            inlineNewGenre = ""
                                            showInlineAddGenre = false
                                        }
                                    },
                                    shape = CircleShape,
                                    color = palette.accentDark,
                                    modifier = Modifier.height(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 14.dp)) {
                                        Text(
                                            text = if (strings.isId) "Tambah" else "Add",
                                            color = palette.bgApp,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            softWrap = false
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            onClick = { showBatchOrganizeDialog = false },
                            shape = CircleShape,
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = strings.cancelBtn,
                                color = palette.textMain,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 11.dp)
                            )
                        }
                        Surface(
                            onClick = {
                                onBatchAssignShelfAndGenres(
                                    uiState.libSelectedIds.toList(),
                                    if (chosenShelf == "__KEEP__") null else chosenShelf,
                                    chosenGenres.toList()
                                )
                                showBatchOrganizeDialog = false
                            },
                            shape = CircleShape,
                            color = palette.accentDark,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (strings.isId) "Terapkan" else "Apply",
                                color = palette.bgApp,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 11.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Custom Layout that measures text unconstrained horizontally and rotates it 90 degrees (`vertical-rl`),
 * so vertical spine titles and author names on narrow book spines (30dp–64dp) never wrap or clip into 1 character per line!
 */
@Composable
private fun VerticalRotatedText(
    text: String,
    color: Color,
    fontSizeSp: Float,
    fontFamily: FontFamily,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier
) {
    Layout(
        content = {
            Text(
                text = text,
                color = color,
                fontSize = fontSizeSp.sp,
                fontFamily = fontFamily,
                fontWeight = fontWeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.rotate(90f)
            )
        },
        modifier = modifier
    ) { measurables, constraints ->
        val maxLen = constraints.maxHeight.coerceAtLeast(20)
        val placeable = measurables.first().measure(
            Constraints(
                minWidth = 0,
                maxWidth = maxLen,
                minHeight = 0,
                maxHeight = constraints.maxWidth.coerceAtLeast(20)
            )
        )
        val layoutW = constraints.maxWidth.coerceAtLeast(placeable.height)
        val layoutH = placeable.width.coerceAtMost(constraints.maxHeight)
        layout(layoutW, layoutH) {
            val x = (layoutW - placeable.width) / 2
            val y = (layoutH - placeable.height) / 2
            placeable.place(x, y)
        }
    }
}

@Composable
fun PrototypeFormInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    singleLine: Boolean = true,
    modifier: Modifier = Modifier
) {
    val palette = LocalLluneviePalette.current
    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(
                text = label,
                color = palette.textMuted,
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = palette.bgSurface,
            border = BorderStroke(1.dp, palette.borderSoft),
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier.padding(
                    horizontal = 14.dp,
                    vertical = if (singleLine) 6.dp else 12.dp
                )
            ) {
                if (singleLine) {
                    LlunevieCenteredTextField(
                        value = value,
                        onValueChange = onValueChange,
                        placeholder = placeholder,
                        fontSize = 12.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                    )
                } else {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(
                            text = placeholder,
                            color = palette.textMuted.copy(alpha = 0.7f),
                            fontSize = 12.sp
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        singleLine = false,
                        textStyle = TextStyle(color = palette.textMain, fontSize = 12.sp),
                        cursorBrush = SolidColor(palette.textMain),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectionCheckBadge(
    selected: Boolean,
    sizeDp: androidx.compose.ui.unit.Dp = 22.dp,
    modifier: Modifier = Modifier
) {
    val palette = LocalLluneviePalette.current
    Surface(
        shape = CircleShape,
        color = if (selected) palette.textMain else palette.bgSurface.copy(alpha = 0.88f),
        border = BorderStroke(1.dp, palette.borderSoft),
        shadowElevation = 4.dp,
        modifier = modifier.size(sizeDp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (selected) {
                PrototypeSvgIcon(
                    type = PrototypeIconType.CHECK,
                    tint = palette.bgApp,
                    modifier = Modifier.size(sizeDp * 0.6f)
                )
            }
        }
    }
}
