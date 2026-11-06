package com.llunaticsys.llunevie.ui.reader

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llunaticsys.llunevie.GlobalPageMetrics
import com.llunaticsys.llunevie.LlunevieUiState
import com.llunaticsys.llunevie.LlunevieViewModel
import com.llunaticsys.llunevie.data.local.BookEntity
import com.llunaticsys.llunevie.data.local.HighlightNoteEntity
import com.llunaticsys.llunevie.data.local.PageBookmarkEntity
import com.llunaticsys.llunevie.model.HighlightColorKey
import com.llunaticsys.llunevie.ui.components.PrototypeIconType
import com.llunaticsys.llunevie.ui.components.PrototypeSvgIcon
import com.llunaticsys.llunevie.ui.theme.LlunevieStrings
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette

@Composable
internal fun BoxScope.ReaderNotesDrawer(
    book: BookEntity,
    uiState: LlunevieUiState,
    strings: LlunevieStrings,
    metrics: GlobalPageMetrics,
    bookHighlights: List<HighlightNoteEntity>,
    bookBookmarks: List<PageBookmarkEntity>,
    isSpread: Boolean,
    onDisplayPageIndexChanged: (Int) -> Unit,
    onJumpToChapterAndPage: (Int, Int) -> Unit,
    onToggleNotesDrawer: (Boolean) -> Unit,
    onSaveHighlightNote: (String, String, HighlightColorKey, String?, Int, Int) -> Unit,
    onDeleteHighlightNote: (String) -> Unit,
    viewModel: LlunevieViewModel?
) {
    val palette = LocalLluneviePalette.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    // 6. RIGHT SAVED NOTES & HIGHLIGHTS DRAWER (`#readerNotesDrawer`)
    // Backdrop Scrim
    AnimatedVisibility(
        visible = uiState.showReaderNotesDrawer,
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
                ) { onToggleNotesDrawer(false) }
        )
    }

    // Right Drawer Panel
    AnimatedVisibility(
        visible = uiState.showReaderNotesDrawer,
        enter = slideInHorizontally(spring(dampingRatio = 0.86f, stiffness = 380f)) { it } + fadeIn(tween(180)),
        exit = slideOutHorizontally(tween(200, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(160)),
        modifier = Modifier.align(Alignment.CenterEnd)
    ) {
        var isAddingManualNote by remember { mutableStateOf(false) }
        var manualNoteText by remember { mutableStateOf("") }
        var manualQuoteText by remember { mutableStateOf("") }
        var selectedNoteColor by remember { mutableStateOf(HighlightColorKey.GOLD) }

        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, bottomStart = 28.dp),
            color = palette.sheetBg,
            border = BorderStroke(1.dp, palette.borderSoft),
            shadowElevation = 24.dp,
            modifier = Modifier
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
                            text = if (strings.isId) "${bookBookmarks.size} penanda · ${bookHighlights.size} catatan"
                            else "${bookBookmarks.size} bookmark(s) · ${bookHighlights.size} note(s)",
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

                if (bookHighlights.isEmpty() && bookBookmarks.isEmpty()) {
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
                                text = if (strings.isId) "Belum ada penanda atau catatan" else "No saved bookmarks or notes yet",
                                color = palette.textMain,
                                fontSize = 15.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (strings.isId) "Ketuk tombol penanda di kanan atas atau tandai sorotan pada halaman baca."
                                else "Tap the top-right bookmark button or highlight text on reading pages.",
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
                        if (bookBookmarks.isNotEmpty()) {
                            item(key = "hdr_bookmarks") {
                                Text(
                                    text = if (strings.isId) "HALAMAN TERSIMPAN (${bookBookmarks.size})" else "BOOKMARKED PAGES (${bookBookmarks.size})",
                                    color = palette.textMuted,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            itemsIndexed(bookBookmarks, key = { _, b -> b.id }) { _, bm ->
                                Surface(
                                    onClick = {
                                        onToggleNotesDrawer(false)
                                        val targetZero = (bm.pageNumber - 1).coerceIn(0, (metrics.totalBookPages - 1).coerceAtLeast(0))
                                        onDisplayPageIndexChanged(targetZero)
                                        viewModel?.jumpToGlobalPage(targetZero)
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    color = palette.bgCard,
                                    border = BorderStroke(1.dp, palette.borderSoft),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.weight(1f),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Bookmark,
                                                contentDescription = null,
                                                tint = palette.accent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = if (strings.isId) "Halaman ${bm.pageNumber}" else "Page ${bm.pageNumber}",
                                                    color = palette.textMain,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (bm.chapterTitle.isNotBlank()) {
                                                    Text(
                                                        text = bm.chapterTitle,
                                                        color = palette.textMuted,
                                                        fontSize = 10.5.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                if (bm.snippetText.isNotBlank()) {
                                                    Text(
                                                        text = "“${bm.snippetText}”",
                                                        color = palette.textMuted.copy(alpha = 0.8f),
                                                        fontSize = 10.5.sp,
                                                        fontFamily = FontFamily.Serif,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                            }
                                        }
                                        Surface(
                                            onClick = { viewModel?.deletePageBookmark(bm.id) },
                                            shape = CircleShape,
                                            color = Color.Transparent,
                                            modifier = Modifier.size(26.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("✕", color = palette.textMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                            item(key = "divider_bookmarks") {
                                Spacer(modifier = Modifier.height(2.dp))
                                HorizontalDivider(color = palette.borderSoft)
                                Spacer(modifier = Modifier.height(2.dp))
                            }
                        }

                        if (bookHighlights.isNotEmpty()) {
                            item(key = "hdr_highlights") {
                                Text(
                                    text = if (strings.isId) "CATATAN & HIGHLIGHT (${bookHighlights.size})" else "NOTES & HIGHLIGHTS (${bookHighlights.size})",
                                    color = palette.textMuted,
                                    fontSize = 9.sp,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
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
}
