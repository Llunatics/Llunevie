package com.llunaticsys.llunevie.ui.reader

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.llunaticsys.llunevie.GlobalPageMetrics
import com.llunaticsys.llunevie.LlunevieUiState
import com.llunaticsys.llunevie.LlunevieViewModel
import com.llunaticsys.llunevie.data.local.BookEntity
import com.llunaticsys.llunevie.data.local.localizeChapterTitle
import com.llunaticsys.llunevie.model.BookChapter
import com.llunaticsys.llunevie.ui.components.PrototypeIconType
import com.llunaticsys.llunevie.ui.components.PrototypeSvgIcon
import com.llunaticsys.llunevie.ui.components.sanitizeBookOrChapterText
import com.llunaticsys.llunevie.ui.theme.LlunevieStrings
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette
import java.io.File

@Composable
internal fun BoxScope.ReaderTocDrawer(
    book: BookEntity,
    uiState: LlunevieUiState,
    strings: LlunevieStrings,
    metrics: GlobalPageMetrics,
    chapters: List<BookChapter>,
    isSpread: Boolean,
    isPdfBook: Boolean,
    getPageAtGlobalZero: (Int) -> Triple<Int, Int, String>?,
    onJumpToChapterAndPage: (Int, Int) -> Unit,
    onToggleTocDrawer: (Boolean) -> Unit,
    onOpenIllustViewer: (Int) -> Unit,
    viewModel: LlunevieViewModel?
) {
    val palette = LocalLluneviePalette.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 5. LEFT SIDEBAR TOC & ILLUSTRATIONS DRAWER (`#readerTocDrawer` with Footer Progress Bar!)
    // Backdrop Scrim
    AnimatedVisibility(
        visible = uiState.showReaderTocDrawer,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(180))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onToggleTocDrawer(false) }
        )
    }

    // Left Drawer Panel
    AnimatedVisibility(
        visible = uiState.showReaderTocDrawer,
        enter = slideInHorizontally(spring(dampingRatio = 0.86f, stiffness = 380f)) { -it } + fadeIn(tween(180)),
        exit = slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(160)),
        modifier = Modifier.align(Alignment.CenterStart)
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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isPdfBook && uiState.pdfIndexingState[book.id] == null) {
                                Surface(
                                    onClick = { viewModel?.forceReindexCurrentPdfBook() },
                                    shape = CircleShape,
                                    color = palette.bgSurface,
                                    border = BorderStroke(1.dp, palette.borderSoft),
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("↻", color = palette.textMain, fontSize = 13.sp)
                                    }
                                }
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

                    var isManualTocEditExpanded by remember { mutableStateOf(false) }
                    var inlineEditingChIdx by remember { mutableStateOf<Int?>(null) }
                    var inlineEditTitle by remember { mutableStateOf("") }
                    var inlineEditPageStr by remember { mutableStateOf("") }
                    var newChapterTitle by remember { mutableStateOf("") }
                    var newChapterPageStr by remember(metrics.currentGlobalPage) { mutableStateOf(metrics.currentGlobalPage.toString()) }

                    val chapterStartPages = remember(chapters, isPdfBook) {
                        var cum = 0
                        chapters.map { ch ->
                            val pageOne = if (isPdfBook) {
                                val first = ch.pages.firstOrNull().orEmpty()
                                val match = Regex("""\[PDF_PAGE:(\d+)\]""").find(first)
                                if (match != null) {
                                    (match.groupValues[1].toIntOrNull() ?: cum) + 1
                                } else {
                                    cum + 1
                                }
                            } else {
                                cum + 1
                            }
                            cum += ch.pages.size.coerceAtLeast(1)
                            pageOne
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Section 1: Pure chapter rows
                        itemsIndexed(chapters, key = { idx, _ -> "toc_ch_$idx" }) { idx, ch ->
                            val active = idx == uiState.activeChapterIndex
                            val startPage = chapterStartPages.getOrElse(idx) { 1 }
                            val isRowSelected = active && !isManualTocEditExpanded
                            Surface(
                                onClick = {
                                    if (isManualTocEditExpanded) {
                                        if (inlineEditingChIdx == idx) {
                                            inlineEditingChIdx = null
                                        } else {
                                            inlineEditingChIdx = idx
                                            inlineEditTitle = ch.title
                                            inlineEditPageStr = startPage.toString()
                                        }
                                    } else {
                                        onJumpToChapterAndPage(idx, 0)
                                        onToggleTocDrawer(false)
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                color = if (isRowSelected) palette.textMain else palette.bgSurface,
                                border = BorderStroke(1.dp, if (isRowSelected) palette.textMain else if (inlineEditingChIdx == idx) palette.textMain.copy(alpha = 0.5f) else palette.borderSoft),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
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
                                                color = if (isRowSelected) palette.bgApp.copy(alpha = 0.70f) else palette.textMuted.copy(alpha = 0.65f),
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Normal
                                            )
                                            Text(
                                                text = sanitizeBookOrChapterText(localizeChapterTitle(ch.title, strings.isEn)),
                                                color = if (isRowSelected) palette.bgApp else palette.textMain,
                                                fontSize = 13.5.sp,
                                                fontFamily = FontFamily.Serif,
                                                fontWeight = if (isRowSelected) FontWeight.SemiBold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "${if (strings.isEn) "p." else "Hal"} $startPage · ${ch.pages.size} ${strings.pagesUnit}",
                                                color = if (isRowSelected) palette.bgApp.copy(alpha = 0.8f) else palette.textMuted,
                                                fontSize = 10.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            if (isManualTocEditExpanded) {
                                                PrototypeSvgIcon(
                                                    type = if (inlineEditingChIdx == idx) PrototypeIconType.CHEVRON_UP else PrototypeIconType.EDIT_PEN,
                                                    tint = if (isRowSelected) palette.bgApp else palette.textMuted,
                                                    modifier = Modifier.size(12.dp),
                                                    contentDescription = if (inlineEditingChIdx == idx) (if (strings.isEn) "Collapse chapter edit" else "Tutup edit bab") else (if (strings.isEn) "Edit chapter" else "Edit bab")
                                                )
                                            }
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = isManualTocEditExpanded && inlineEditingChIdx == idx,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 14.dp, end = 14.dp, bottom = 12.dp)
                                        ) {
                                            HorizontalDivider(color = palette.borderSoft.copy(alpha = 0.5f))
                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Row 1 (Title Edit)
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = palette.bgCard,
                                                border = BorderStroke(1.dp, palette.borderSoft),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                                ) {
                                                    if (inlineEditTitle.isEmpty()) {
                                                        Text(
                                                            text = if (strings.isEn) "Chapter title..." else "Judul bab...",
                                                            fontSize = 12.5.sp,
                                                            fontFamily = FontFamily.Serif,
                                                            color = palette.textMuted
                                                        )
                                                    }
                                                    BasicTextField(
                                                        value = inlineEditTitle,
                                                        onValueChange = { inlineEditTitle = it },
                                                        textStyle = androidx.compose.ui.text.TextStyle(
                                                            fontSize = 12.5.sp,
                                                            fontFamily = FontFamily.Serif,
                                                            color = palette.textMain
                                                        ),
                                                        singleLine = true,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Row 2 (Page Stepper + Delete + Save)
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                // Left: Start page adjuster capsule [ - ]  Hal [ inlineEditPageStr ]  [ + ]
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = palette.bgCard,
                                                    border = BorderStroke(1.dp, palette.borderSoft)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                    ) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(26.dp)
                                                                .clip(CircleShape)
                                                                .clickable {
                                                                    val cur = inlineEditPageStr.toIntOrNull() ?: startPage
                                                                    val next = (cur - 1).coerceIn(1, metrics.totalBookPages)
                                                                    inlineEditPageStr = next.toString()
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text("-", color = palette.textMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = if (strings.isEn) "p. " else "Hal ",
                                                            color = palette.textMuted,
                                                            fontSize = 11.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                        BasicTextField(
                                                            value = inlineEditPageStr,
                                                            onValueChange = { str ->
                                                                val filtered = str.filter { it.isDigit() }
                                                                inlineEditPageStr = filtered
                                                            },
                                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                                fontSize = 12.sp,
                                                                fontFamily = FontFamily.Monospace,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color = palette.textMain,
                                                                textAlign = TextAlign.Center
                                                            ),
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                            singleLine = true,
                                                            modifier = Modifier.width(36.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Box(
                                                            modifier = Modifier
                                                                .size(26.dp)
                                                                .clip(CircleShape)
                                                                .clickable {
                                                                    val cur = inlineEditPageStr.toIntOrNull() ?: startPage
                                                                    val next = (cur + 1).coerceIn(1, metrics.totalBookPages)
                                                                    inlineEditPageStr = next.toString()
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Text("+", color = palette.textMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }

                                                // Right: Delete + Save
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    if (chapters.size > 1) {
                                                        Surface(
                                                            onClick = {
                                                                viewModel?.deleteManualChapter(book.id, idx)
                                                                inlineEditingChIdx = null
                                                            },
                                                            shape = CircleShape,
                                                            color = palette.bgCard,
                                                            border = BorderStroke(1.dp, palette.borderSoft),
                                                            modifier = Modifier.size(30.dp)
                                                        ) {
                                                            Box(contentAlignment = Alignment.Center) {
                                                                PrototypeSvgIcon(
                                                                    type = PrototypeIconType.TRASH_CAN,
                                                                    tint = palette.textMain,
                                                                    modifier = Modifier.size(13.dp),
                                                                    contentDescription = if (strings.isEn) "Delete chapter" else "Hapus bab"
                                                                )
                                                            }
                                                        }
                                                    }

                                                    Surface(
                                                        onClick = {
                                                            val parsed = inlineEditPageStr.toIntOrNull() ?: startPage
                                                            viewModel?.updateManualChapter(book.id, idx, inlineEditTitle, parsed)
                                                            inlineEditingChIdx = null
                                                        },
                                                        shape = CircleShape,
                                                        color = palette.textMain,
                                                        modifier = Modifier.height(30.dp)
                                                    ) {
                                                        Box(
                                                            contentAlignment = Alignment.Center,
                                                            modifier = Modifier.padding(horizontal = 14.dp)
                                                        ) {
                                                            Text(
                                                                text = if (strings.isEn) "Save" else "Simpan",
                                                                color = palette.bgApp,
                                                                fontSize = 11.sp,
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
                        }

                        // Section 1b: Expandable Manual Chapter Editor
                        item(key = "toc_manual_chapter_editor") {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = palette.bgSurface,
                                border = BorderStroke(1.dp, if (isManualTocEditExpanded) palette.textMain.copy(alpha = 0.5f) else palette.borderSoft),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth()) {
                                    // Collapsed / Header Bar
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                isManualTocEditExpanded = !isManualTocEditExpanded
                                                if (!isManualTocEditExpanded) inlineEditingChIdx = null
                                            }
                                            .padding(horizontal = 14.dp, vertical = 11.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            PrototypeSvgIcon(
                                                type = PrototypeIconType.EDIT_PEN,
                                                tint = palette.textMain,
                                                modifier = Modifier.size(13.dp),
                                                contentDescription = if (strings.isEn) "Edit & Manage Chapters" else "Edit & Kelola Bab Manual"
                                            )
                                            Text(
                                                text = if (strings.isEn) "Edit & Manage Chapters" else "Edit & Kelola Bab Manual",
                                                color = palette.textMain,
                                                fontSize = 12.5.sp,
                                                fontFamily = FontFamily.Serif,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        Text(
                                            text = if (isManualTocEditExpanded) (if (strings.isEn) "Done ▴" else "Selesai ▴") else (if (strings.isEn) "Expand ▾" else "Kelola ▾"),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = palette.textMuted
                                        )
                                    }

                                    // Expanded Inline Panel
                                    AnimatedVisibility(
                                        visible = isManualTocEditExpanded,
                                        enter = expandVertically() + fadeIn(),
                                        exit = shrinkVertically() + fadeOut()
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp)
                                        ) {
                                            HorizontalDivider(color = palette.borderSoft)
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = if (strings.isEn) "Tap any chapter above to rename, adjust its page, or delete it—or add a new chapter below:" else "Ketuk bab di atas untuk ubah judul, sesuaikan halaman, atau hapus—atau tambah bab baru di bawah:",
                                                color = palette.textMuted,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))

                                            // Title Input Field
                                            Surface(
                                                shape = RoundedCornerShape(10.dp),
                                                color = palette.bgCard,
                                                border = BorderStroke(1.dp, palette.borderSoft),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                                ) {
                                                    if (newChapterTitle.isEmpty()) {
                                                        Text(
                                                            text = if (strings.isEn) "New chapter title (e.g. Chapter 1)..." else "Judul bab baru (mis. Bab 1)...",
                                                            fontSize = 12.sp,
                                                            fontFamily = FontFamily.Serif,
                                                            color = palette.textMuted
                                                        )
                                                    }
                                                    BasicTextField(
                                                        value = newChapterTitle,
                                                        onValueChange = { newChapterTitle = it },
                                                        textStyle = androidx.compose.ui.text.TextStyle(
                                                            fontSize = 12.sp,
                                                            fontFamily = FontFamily.Serif,
                                                            color = palette.textMain
                                                        ),
                                                        singleLine = true,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(8.dp))

                                            // Page Number + Quick Current Page Pill + + Add Button Row
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Left: numeric BasicTextField for newChapterPageStr
                                                Surface(
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = palette.bgCard,
                                                    border = BorderStroke(1.dp, palette.borderSoft),
                                                    modifier = Modifier
                                                        .width(86.dp)
                                                        .height(34.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .padding(horizontal = 8.dp)
                                                    ) {
                                                        Text(
                                                            text = if (strings.isEn) "p. " else "Hal ",
                                                            color = palette.textMuted,
                                                            fontSize = 11.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                        BasicTextField(
                                                            value = newChapterPageStr,
                                                            onValueChange = { str ->
                                                                val filtered = str.filter { it.isDigit() }
                                                                newChapterPageStr = filtered
                                                            },
                                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                                fontSize = 11.5.sp,
                                                                fontFamily = FontFamily.Monospace,
                                                                fontWeight = FontWeight.Medium,
                                                                color = palette.textMain
                                                            ),
                                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                            singleLine = true,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }

                                                // Middle: Compact clickable pill
                                                Surface(
                                                    onClick = {
                                                        newChapterPageStr = metrics.currentGlobalPage.toString()
                                                    },
                                                    shape = CircleShape,
                                                    color = palette.bgCard,
                                                    border = BorderStroke(1.dp, palette.borderSoft),
                                                    modifier = Modifier.height(34.dp)
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.padding(horizontal = 10.dp)
                                                    ) {
                                                        Text(
                                                            text = if (strings.isEn) "Current (${metrics.currentGlobalPage})" else "Hal. Ini (${metrics.currentGlobalPage})",
                                                            color = palette.textMain,
                                                            fontSize = 10.5.sp,
                                                            fontFamily = FontFamily.Monospace,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }

                                                Spacer(modifier = Modifier.weight(1f))

                                                // Right: + Tambah / + Add Pill Button
                                                Surface(
                                                    onClick = {
                                                        val pNum = newChapterPageStr.toIntOrNull() ?: metrics.currentGlobalPage
                                                        viewModel?.addManualChapter(book.id, newChapterTitle, pNum)
                                                        newChapterTitle = ""
                                                    },
                                                    shape = CircleShape,
                                                    color = palette.textMain,
                                                    modifier = Modifier.height(34.dp)
                                                ) {
                                                    Box(
                                                        contentAlignment = Alignment.Center,
                                                        modifier = Modifier.padding(horizontal = 14.dp)
                                                    ) {
                                                        Text(
                                                            text = if (strings.isEn) "+ Add" else "+ Tambah",
                                                            color = palette.bgApp,
                                                            fontSize = 11.5.sp,
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
