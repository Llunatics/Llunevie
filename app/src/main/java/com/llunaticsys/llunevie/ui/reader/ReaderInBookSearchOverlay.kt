package com.llunaticsys.llunevie.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.llunaticsys.llunevie.model.BookChapter
import com.llunaticsys.llunevie.ui.theme.LlunevieStrings
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SEARCH_ILLUST_TAG_REGEX = Regex("\\[ILLUST:[^]]+]")
private val SEARCH_PDF_PAGE_TAG_REGEX = Regex("\\[PDF_PAGE:[^]]+]")

@Composable
internal fun BoxScope.ReaderInBookSearchOverlay(
    inBookSearchQuery: String?,
    onDismissSearch: () -> Unit,
    chapters: List<BookChapter>,
    strings: LlunevieStrings,
    onJumpToChapterAndPage: (Int, Int) -> Unit
) {
    val palette = LocalLluneviePalette.current

    var cachedSearchQuery by remember { mutableStateOf<String?>(null) }
    if (inBookSearchQuery != null) {
        cachedSearchQuery = inBookSearchQuery
    }

    // Search Scrim
    AnimatedVisibility(
        visible = inBookSearchQuery != null,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(160)),
        modifier = Modifier.zIndex(90f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismissSearch() }
        )
    }

    // Search Panel
    AnimatedVisibility(
        visible = inBookSearchQuery != null,
        enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 380f)) { it } + fadeIn(tween(180)),
        exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(150)),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .zIndex(91f)
    ) {
        val initialQuery = cachedSearchQuery ?: inBookSearchQuery.orEmpty()
        var currentQuery by remember(cachedSearchQuery) { mutableStateOf(initialQuery) }
        val searchResults by produceState(
            initialValue = emptyList<Triple<Int, Int, String>>(),
            currentQuery,
            chapters
        ) {
            val q = currentQuery.trim()
            value = if (q.length < 2) {
                emptyList()
            } else {
                withContext(Dispatchers.Default) {
                    val list = mutableListOf<Triple<Int, Int, String>>()
                    for ((chIdx, ch) in chapters.withIndex()) {
                        for ((pgIdx, pageContent) in ch.pages.withIndex()) {
                            val clean = pageContent
                                .replace(SEARCH_ILLUST_TAG_REGEX, "")
                                .replace(SEARCH_PDF_PAGE_TAG_REGEX, "")
                                .trim()
                            val matchIdx = clean.indexOf(q, ignoreCase = true)
                            if (matchIdx >= 0) {
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
        }

        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = palette.sheetBg,
            border = BorderStroke(1.dp, palette.borderSoft),
            shadowElevation = 24.dp,
            modifier = Modifier
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
                        onClick = { onDismissSearch() },
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
                                    onDismissSearch()
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
