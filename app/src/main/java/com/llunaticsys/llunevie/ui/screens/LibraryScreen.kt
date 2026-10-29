package com.llunaticsys.llunevie.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import com.llunaticsys.llunevie.LlunevieUiState
import com.llunaticsys.llunevie.data.local.BookEntity
import com.llunaticsys.llunevie.data.local.ShelfEntity
import com.llunaticsys.llunevie.data.local.UserPreferencesState
import com.llunaticsys.llunevie.model.LibrarySortOption
import com.llunaticsys.llunevie.model.LibraryViewMode
import com.llunaticsys.llunevie.ui.components.BookCoverCard
import com.llunaticsys.llunevie.ui.components.ClassicGlassSegmentedControl
import com.llunaticsys.llunevie.ui.components.CustomThemeDropdown
import com.llunaticsys.llunevie.ui.components.LlunevieCenteredTextField
import com.llunaticsys.llunevie.ui.components.PrototypeIconType
import com.llunaticsys.llunevie.ui.components.PrototypeSvgIcon
import com.llunaticsys.llunevie.ui.theme.LlunevieStrings
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette
import com.llunaticsys.llunevie.ui.theme.shelfMatchesFilter
import kotlin.math.abs

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
    onSetShelf: (String) -> Unit,
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
    modifier: Modifier = Modifier
) {
    val palette = LocalLluneviePalette.current

    // Filter & sort books (matching `getFilteredLibraryBooks` in `llunevie_prototype.html`)
    val filteredBooks = remember(books, prefs.libShelf, prefs.libFilter, prefs.libStatus, prefs.libSort, uiState.libSearchQuery) {
        val q = uiState.libSearchQuery.trim().lowercase()
        val list = books.filter { b ->
            val matchShelf = if (prefs.libShelf.equals("all", ignoreCase = true) || prefs.libShelf.isBlank()) {
                true
            } else {
                shelfMatchesFilter(b.shelf, prefs.libShelf)
            }
            val matchCat = prefs.libFilter == "all" || b.category.equals(prefs.libFilter, ignoreCase = true)
            val matchStatus = when (prefs.libStatus.lowercase()) {
                "unread" -> b.progress == 0 && !b.completedAllPages
                "reading" -> b.progress in 1..99 && !b.completedAllPages
                "finished" -> b.completedAllPages || b.progress >= 100
                else -> true
            }
            val matchQ = q.isEmpty() ||
                b.title.lowercase().contains(q) ||
                b.author.lowercase().contains(q) ||
                b.shelf.lowercase().contains(q)
            matchShelf && matchCat && matchStatus && matchQ
        }
        when (prefs.libSort) {
            LibrarySortOption.TITLE -> list.sortedBy { it.title.lowercase() }
            LibrarySortOption.AUTHOR -> list.sortedBy { it.author.lowercase() }
            LibrarySortOption.PROGRESS -> list.sortedByDescending { it.progress }
            LibrarySortOption.RECENT -> list
        }
    }

    val hasActiveFilterDot = prefs.libFilter != "all" || prefs.libStatus != "all" || prefs.libSort != LibrarySortOption.RECENT || (prefs.libShelf != "all" && prefs.libShelf.isNotBlank())

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 16.dp, bottom = 108.dp)
        ) {
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
                                    .clickable { onSetViewMode(mode) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    PrototypeSvgIcon(
                                        type = iconType,
                                        tint = if (active) palette.textMain else palette.textMuted,
                                        modifier = Modifier.size(16.dp),
                                        strokeWidthDp = 1.8f
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
                            Text(
                                text = "✕",
                                color = palette.textMuted,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .clickable { onSearchQueryChange("") }
                                    .padding(horizontal = 4.dp)
                            )
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
                                .clickable { onToggleFilterSheet(true) },
                            contentAlignment = Alignment.Center
                        ) {
                            PrototypeSvgIcon(
                                type = PrototypeIconType.FILTER_FUNNEL,
                                tint = palette.textMain,
                                modifier = Modifier.size(16.dp),
                                strokeWidthDp = 1.8f
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

            // 3. Empty Shelf State
            if (filteredBooks.isEmpty()) {
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
            } else {
                when (prefs.libViewMode) {
                    // 1. SPINE SHELF MODE (1:1 with `llunevie_prototype.html`: ✦ top, vertical title, page count, vertical author first name, 3D spine gradient, swipe wave lift, 14dp rounded-full plank)
                    LibraryViewMode.SPINE -> {
                        var hoveredBookIdx by remember { mutableStateOf<Int?>(null) }
                        val spineBoundsMap = remember { mutableMapOf<Int, Pair<Float, Float>>() }

                        val shelvesRows = remember(filteredBooks) {
                            val rows = mutableListOf<List<Pair<Int, BookEntity>>>()
                            var currentRow = mutableListOf<Pair<Int, BookEntity>>()
                            var currentWidthDp = 0
                            filteredBooks.forEachIndexed { globalIdx, b ->
                                val pages = b.pagesCount.coerceIn(80, 950)
                                val wDp = 30 + ((pages - 80) * 34) / (950 - 80)
                                val footprint = wDp + if (currentRow.isNotEmpty()) 4 else 0
                                if (currentRow.isNotEmpty() && currentWidthDp + footprint > 328) {
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

                        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                            shelvesRows.forEach { shelfRow ->
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(248.dp)
                                            .padding(horizontal = 12.dp)
                                            .pointerInput(shelfRow, uiState.libSelectMode) {
                                                if (uiState.libSelectMode) return@pointerInput
                                                detectHorizontalDragGestures(
                                                    onDragStart = { offset ->
                                                        val hit = spineBoundsMap.entries.find { (_, range) ->
                                                            offset.x in range.first..range.second
                                                        }?.key
                                                        hoveredBookIdx = hit
                                                    },
                                                    onHorizontalDrag = { change, _ ->
                                                        val x = change.position.x
                                                        val hit = spineBoundsMap.entries.find { (_, range) ->
                                                            x in range.first..range.second
                                                        }?.key
                                                        if (hoveredBookIdx != hit) {
                                                            hoveredBookIdx = hit
                                                        }
                                                    },
                                                    onDragEnd = { hoveredBookIdx = null },
                                                    onDragCancel = { hoveredBookIdx = null }
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
                                                       (176f + ((clampedCm - 14f) / 16f) * 76f).dp // Maps 14cm..30cm -> 176.dp..252.dp
                                                   } else {
                                                       (208 + (abs(book.id.hashCode()) % 28)).dp
                                                   }
                                               }
                                               val isSelected = uiState.libSelectedIds.contains(book.id)

                                              val dist = hoveredBookIdx?.let { abs(it - globalIdx) }
                                              val isHovered = dist == 0
                                              val targetLift = when {
                                                  isHovered || isSelected -> (-18).dp
                                                  else -> 0.dp
                                              }
                                              val targetScale = when {
                                                  isHovered -> 1.03f
                                                  else -> 1f
                                              }
                                              val liftOffset by animateDpAsState(
                                                  targetValue = targetLift,
                                                  animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                                  label = "spineLift"
                                              )
                                              val scaleAnim by animateFloatAsState(
                                                  targetValue = targetScale,
                                                  animationSpec = spring(stiffness = Spring.StiffnessMedium),
                                                  label = "spineScale"
                                              )

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

                                              Box(
                                                  modifier = Modifier
                                                      .width(wDp)
                                                      .height(hDp)
                                                      .zIndex(if (isHovered || isSelected) 25f else 1f)
                                                      .offset(y = liftOffset)
                                                      .scale(scaleAnim)
                                                      .onGloballyPositioned { coords ->
                                                          val left = coords.positionInRoot().x
                                                          spineBoundsMap[globalIdx] = Pair(left, left + coords.size.width)
                                                      }
                                                      .shadow(if (isHovered || isSelected) 14.dp else 6.dp, spineShape)
                                                     .clip(spineShape)
                                                     .background(Color(book.spineColorHex))
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
                    }

                    // 2. GRID MODE & 3. FULL COVER MODE (Novel 2:3 Aspect Ratio + 3dp Progress Bar in Grid Mode)
                    LibraryViewMode.GRID, LibraryViewMode.COVERS -> {
                        val isCoversOnly = prefs.libViewMode == LibraryViewMode.COVERS
                        val cols = if (prefs.libGridCols in 2..5) prefs.libGridCols else if (isCoversOnly) 3 else 2
                        val rows = filteredBooks.chunked(cols)

                        Column(verticalArrangement = Arrangement.spacedBy(if (isCoversOnly) 12.dp else 14.dp)) {
                            rows.forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(if (isCoversOnly) 12.dp else 14.dp)
                                ) {
                                    rowItems.forEach { book ->
                                        val isSelected = uiState.libSelectedIds.contains(book.id)
                                        val importPct = uiState.importingBooksProgress[book.id]
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(16.dp))
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
                                    repeat(cols - rowItems.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    // 4. LIST MODE (1:1 with `renderLibrary` list mode in `llunevie_prototype.html`)
                    LibraryViewMode.LIST -> {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            filteredBooks.forEach { book ->
                                val isSelected = uiState.libSelectedIds.contains(book.id)
                                val importPct = uiState.importingBooksProgress[book.id]
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
                                            Text(
                                                text = "${book.year} · ${book.pagesCount} ${strings.pagesUnit} · ${strings.localizeShelfName(book.shelf)}",
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
            }

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

        // FLOATING MULTI-SELECT TOP ACTION PILL (`#libSelectionBar` in `llunevie_prototype.html`)
        if (uiState.libSelectMode) {
            Surface(
                shape = CircleShape,
                color = palette.sheetBg,
                border = BorderStroke(1.dp, palette.borderSoft),
                shadowElevation = 14.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .fillMaxWidth()
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
                                Text("✕", color = palette.textMain, fontSize = 12.sp)
                            }
                        }
                        Text(
                            text = if (strings.isId) "Terpilih: ${uiState.libSelectedIds.size}" else "Selected: ${uiState.libSelectedIds.size}",
                            color = palette.textMain,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
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
                                border = BorderStroke(1.dp, palette.borderSoft)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    PrototypeSvgIcon(
                                        type = PrototypeIconType.EDIT_PEN,
                                        tint = palette.textMain,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (strings.isId) "Edit Detail" else "Edit Details",
                                        color = palette.textMain,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        Surface(
                            onClick = { onSelectAll(filteredBooks.map { it.id }) },
                            shape = CircleShape,
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft)
                        ) {
                            Text(
                                text = if (strings.isId) "Pilih Semua" else "Select All",
                                color = palette.textMain,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                        Surface(
                            onClick = { onShowDeleteConfirm(true) },
                            shape = CircleShape,
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                PrototypeSvgIcon(
                                    type = PrototypeIconType.TRASH_CAN,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. 1:1 LIBRARY FILTER & SHELVES BOTTOM SHEET (`#libDropdownMenu` in `llunevie_prototype.html`)
        if (uiState.showLibFilterSheet) {
            var showManageShelves by remember { mutableStateOf(false) }
            var newShelfText by remember { mutableStateOf("") }
            var shelfPendingDelete by remember { mutableStateOf<String?>(null) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onToggleFilterSheet(false) }
            ) {
                Surface(
                    shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                    color = palette.sheetBg,
                    border = BorderStroke(1.dp, palette.borderSoft),
                    shadowElevation = 24.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
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
                                    border = BorderStroke(1.dp, palette.borderSoft)
                                ) {
                                    Text(
                                        text = strings.resetBtn,
                                        color = palette.textMain,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                                    )
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
                                modifier = Modifier.fillMaxWidth(),
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
                                    color = if (showManageShelves) (if (palette.isGlass) palette.pillActiveBg else palette.accentDark) else palette.bgSurface,
                                    border = BorderStroke(1.dp, if (showManageShelves) (if (palette.isGlass) palette.pillActiveBorder else palette.accentDark) else palette.borderSoft)
                                ) {
                                    Text(
                                        text = strings.manageShelvesBtn,
                                        color = if (showManageShelves) (if (palette.isGlass) palette.textMain else palette.bgApp) else palette.textMain,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // "Semua" (All) as the very first shelf chip
                                val isAllSelected = prefs.libShelf.equals("all", ignoreCase = true) || prefs.libShelf.isBlank()
                                val allChipBg = if (isAllSelected) (if (palette.isGlass) palette.pillActiveBg else palette.accentDark) else palette.bgSurface
                                val allChipBorder = if (isAllSelected) (if (palette.isGlass) palette.pillActiveBorder else palette.accentDark) else palette.borderSoft
                                val allChipText = if (isAllSelected) (if (palette.isGlass) palette.textMain else palette.bgApp) else palette.textMuted

                                Surface(
                                    onClick = { onSetShelf("all") },
                                    shape = CircleShape,
                                    color = allChipBg,
                                    border = BorderStroke(1.dp, allChipBorder),
                                    shadowElevation = if (isAllSelected) 1.5.dp else 0.dp
                                ) {
                                    Text(
                                        text = strings.shelfAll,
                                        color = allChipText,
                                        fontSize = 12.sp,
                                        fontWeight = if (isAllSelected) FontWeight.SemiBold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }

                                shelves.forEach { shelf ->
                                    val selected = !isAllSelected && shelfMatchesFilter(shelf.name, prefs.libShelf)
                                    val chipBg = if (selected) (if (palette.isGlass) palette.pillActiveBg else palette.accentDark) else palette.bgSurface
                                    val chipBorder = if (selected) (if (palette.isGlass) palette.pillActiveBorder else palette.accentDark) else palette.borderSoft
                                    val chipText = if (selected) (if (palette.isGlass) palette.textMain else palette.bgApp) else palette.textMuted

                                    Surface(
                                        onClick = { onSetShelf(shelf.name) },
                                        shape = CircleShape,
                                        color = chipBg,
                                        border = BorderStroke(1.dp, chipBorder),
                                        shadowElevation = if (selected) 1.5.dp else 0.dp
                                    ) {
                                        Text(
                                            text = strings.localizeShelfName(shelf.name),
                                            color = chipText,
                                            fontSize = 12.sp,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        )
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

                        // 2. STATUS BACA (READING STATUS) Segmented Control
                        Column {
                            Text(
                                text = strings.statusLabel,
                                color = palette.textMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
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

                        // 3. KATEGORI Segmented Control
                        Column {
                            Text(
                                text = strings.catLabel,
                                color = palette.textMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
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
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            ClassicGlassSegmentedControl(
                                items = LibrarySortOption.entries.map { opt -> opt to opt.localizedLabel(prefs.lang) },
                                selectedItem = prefs.libSort,
                                onItemSelected = onSetSort
                            )
                        }

                        // 5. BUKU PER BARIS (GRID / COVER) Segmented Control (2, 3, 4, 5)
                        Column {
                            val activeColsHighlight = if (prefs.libGridCols in 2..5) {
                                prefs.libGridCols
                            } else {
                                if (prefs.libViewMode == LibraryViewMode.COVERS) 3 else 2
                            }
                            Text(
                                text = strings.colsPerRowLabel,
                                color = palette.textMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.2.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            ClassicGlassSegmentedControl(
                                items = listOf(2 to "2", 3 to "3", 4 to "4", 5 to "5"),
                                selectedItem = activeColsHighlight,
                                onItemSelected = onSetGridCols
                            )
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
        var progressStr by remember(editing) { mutableStateOf(editing.progress.toString()) }
        var timeMinsStr by remember(editing) { mutableStateOf(editing.timeMinutes.toString()) }
        var isbnStr by remember(editing) { mutableStateOf(editing.isbn) }
        var coverUrlStr by remember(editing) { mutableStateOf(editing.coverImagePath.orEmpty()) }
        var synopsis by remember(editing) { mutableStateOf(editing.synopsis) }
        var format by remember(editing) { mutableStateOf(editing.format) }

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

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "FORMAT",
                                color = palette.textMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                            CustomThemeDropdown(
                                title = "FORMAT",
                                selectedValue = format,
                                options = listOf(
                                    "PHYSICAL" to (if (strings.isId) "Buku Fisik" else "Physical Book"),
                                    "EPUB" to (if (strings.isId) "eBook Digital" else "Digital eBook"),
                                    "PDF" to "PDF"
                                ),
                                onValueSelected = { format = it },
                                cornerRadius = 16.dp,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        PrototypeFormInput(
                            label = if (strings.isId) "PROGRES (%)" else "PROGRESS (%)",
                            value = progressStr,
                            onValueChange = { progressStr = it },
                            modifier = Modifier.weight(1f)
                        )
                        PrototypeFormInput(
                            label = if (strings.isId) "MENIT BACA" else "READ MINS",
                            value = timeMinsStr,
                            onValueChange = { timeMinsStr = it },
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
                                val nextMins = timeMinsStr.toIntOrNull()?.coerceAtLeast(0) ?: editing.timeMinutes
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
                                        format = format,
                                        year = year.ifBlank { editing.year },
                                        pagesCount = pagesStr.toIntOrNull()?.coerceAtLeast(1) ?: editing.pagesCount,
                                        progress = progressStr.toIntOrNull()?.coerceIn(0, 100) ?: editing.progress,
                                        timeMinutes = nextMins,
                                        readingSeconds = nextMins * 60,
                                        isbn = isbnStr.ifBlank { "—" },
                                        coverImagePath = coverUrlStr.ifBlank { editing.coverImagePath },
                                        synopsis = synopsis,
                                        dimensionsText = dimInfo,
                                        heightCm = parsedHeight,
                                        widthCm = parsedWidth,
                                        lengthCm = parsedHeight,
                                        weightGrams = parsedWeight
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
