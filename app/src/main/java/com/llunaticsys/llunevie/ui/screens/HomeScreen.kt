package com.llunaticsys.llunevie.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llunaticsys.llunevie.MainNavTab
import com.llunaticsys.llunevie.data.local.BookEntity
import com.llunaticsys.llunevie.data.local.DailyReadingLogEntity
import com.llunaticsys.llunevie.data.local.UserPreferencesState
import com.llunaticsys.llunevie.ui.components.BookCoverCard
import com.llunaticsys.llunevie.ui.components.sanitizeBookOrChapterText
import kotlin.math.roundToInt
import com.llunaticsys.llunevie.ui.components.LlunevieAppLogoSvg
import com.llunaticsys.llunevie.ui.components.PrototypeIconType
import com.llunaticsys.llunevie.ui.components.PrototypeSvgIcon
import com.llunaticsys.llunevie.ui.theme.LlunevieStrings
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette
import coil.compose.AsyncImage
import java.io.File

fun formatMinutesText(totalMinutes: Int, isId: Boolean): String {
    val mins = totalMinutes.coerceAtLeast(0)
    val hUnit = if (isId) "j" else "h"
    if (mins < 60) return "${mins}m"
    val h = mins / 60
    val m = mins % 60
    return if (m > 0) "$h$hUnit ${m}m" else "$h$hUnit"
}

@Composable
fun HomeScreen(
    books: List<BookEntity>,
    dailyLogs: List<DailyReadingLogEntity>,
    prefs: UserPreferencesState,
    strings: LlunevieStrings,
    importingBooksProgress: Map<String, Int> = emptyMap(),
    onOpenBook: (String) -> Unit,
    onOpenAddBook: () -> Unit,
    onNavigateTab: (MainNavTab) -> Unit,
    onSetHomeRecentMode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLluneviePalette.current
    val onTheGoBooks = books.filter { it.onTheGo || it.progress in 1..99 }
    val allTimeMins = books.sumOf { it.timeMinutes }.coerceAtLeast(dailyLogs.sumOf { it.minutesRead })
    val weekMins = dailyLogs.takeLast(7).sumOf { it.minutesRead }.coerceAtLeast(if (dailyLogs.isEmpty()) allTimeMins else 0)
    val activeDaysStreak = dailyLogs.count { it.minutesRead > 0 || it.pagesTurned > 0 }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 104.dp)
    ) {
        // Header Row (1:1 with #viewHome header in llunevie_prototype.html)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = prefs.userName.ifBlank { "Llunevie" },
                    color = palette.textMain,
                    fontSize = 28.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (strings.isId) "${formatMinutesText(weekMins, true)} dibaca minggu ini."
                    else "${formatMinutesText(weekMins, false)} read this week.",
                    color = palette.textMuted,
                    fontSize = 13.sp
                )
            }

            Surface(
                onClick = { onNavigateTab(MainNavTab.SETTINGS) },
                shape = CircleShape,
                color = palette.bgSurface,
                border = BorderStroke(1.dp, palette.borderSoft),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (!prefs.userPhotoPath.isNullOrBlank() && File(prefs.userPhotoPath).exists()) {
                        AsyncImage(
                            model = File(prefs.userPhotoPath),
                            contentDescription = "Profile",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LlunevieAppLogoSvg(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        HorizontalDivider(color = palette.borderSoft)

        // 1. CONTINUE READING SECTION
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.continueReading,
                color = palette.textMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.4.sp
            )
            Text(
                text = "${onTheGoBooks.size} ${strings.onTheGoSuffix}",
                color = palette.textMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        if (onTheGoBooks.isEmpty()) {
            Surface(
                onClick = onOpenAddBook,
                shape = RoundedCornerShape(24.dp),
                color = palette.bgCard,
                border = BorderStroke(1.dp, palette.borderSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.startReadingTag,
                            color = palette.textMuted,
                            fontSize = 9.5.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.2.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = strings.emptyContinueTitle,
                            color = palette.textMain,
                            fontSize = 16.5.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = strings.emptyContinueSub,
                            color = palette.textMuted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Surface(
                        shape = CircleShape,
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = "+", color = palette.textMain, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 2.dp)
            ) {
                items(onTheGoBooks, key = { it.id }) { book ->
                    Row(
                        modifier = Modifier
                            .width(238.dp)
                            .height(92.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(palette.bgCard)
                            .border(1.dp, palette.borderSoft, RoundedCornerShape(16.dp))
                            .clickable { onOpenBook(book.id) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Book Cover (50x72dp)
                        BookCoverCard(
                            book = book,
                            showTitleInsideFallback = false,
                            importProgress = importingBooksProgress[book.id],
                            modifier = Modifier
                                .width(50.dp)
                                .height(72.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        // Right Content Column — DO NOT use Arrangement.SpaceBetween!
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            verticalArrangement = Arrangement.Center
                        ) {
                            // 1. Top Metadata Row: Collection Tag + Percentage
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = book.displayCollectionTag(strings.isEn).uppercase(),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.7.sp,
                                    color = palette.textMuted,
                                    maxLines = 1,
                                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                )
                                Text(
                                    text = "${(book.readingProgress * 100).roundToInt()}%",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = FontFamily.Monospace,
                                    color = palette.textMuted,
                                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            // 2. Book Title & Author tightly stacked (0.dp / 1.dp gap!)
                            Text(
                                text = sanitizeBookOrChapterText(book.title),
                                fontSize = 14.sp,
                                lineHeight = 16.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif,
                                color = palette.textMain,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                            )
                            Spacer(modifier = Modifier.height(1.dp))
                            Text(
                                text = book.displayAuthor(strings.isEn),
                                fontSize = 11.sp,
                                lineHeight = 13.sp,
                                color = palette.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            // 3. Minimalist Horizontal Black/Dark Progress Bar at the bottom of the card!
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(palette.textMain.copy(alpha = 0.12f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(book.readingProgress.coerceIn(0.04f, 1f))
                                        .fillMaxHeight()
                                        .clip(CircleShape)
                                        .background(palette.textMain)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. YOUR READING SUMMARY CARD
        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            onClick = { onNavigateTab(MainNavTab.STATS) },
            shape = RoundedCornerShape(24.dp),
            color = palette.bgCard,
            border = BorderStroke(1.dp, palette.borderSoft),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PrototypeSvgIcon(
                            type = PrototypeIconType.NAV_STATS,
                            tint = palette.textMuted,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = strings.yourReading,
                            color = palette.textMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.4.sp
                        )
                    }
                    PrototypeSvgIcon(
                        type = PrototypeIconType.CHEVRON_RIGHT,
                        tint = palette.textMuted.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
                HorizontalDivider(color = palette.borderSoft)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatMinutesText(weekMins, strings.isId),
                            color = palette.textMain,
                            fontSize = 22.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = strings.thisWeek,
                            color = palette.textMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = activeDaysStreak.toString(),
                            color = palette.textMain,
                            fontSize = 22.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = strings.daysRunning,
                            color = palette.textMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = formatMinutesText(allTimeMins, strings.isId),
                            color = palette.textMain,
                            fontSize = 22.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = strings.allTime,
                            color = palette.textMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // 3. RECENTLY ADDED SECTION
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.recentlyAdded,
                color = palette.textMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.4.sp
            )
            val isGrid = prefs.homeRecentMode == "grid"
            if (palette.isGlass) {
                Surface(
                    shape = CircleShape,
                    color = palette.bgCard,
                    border = BorderStroke(1.dp, palette.borderSoft)
                ) {
                    Row(modifier = Modifier.padding(3.dp)) {
                        val activeBg = if (palette.isDark) Color.White.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.68f)
                        val activeBorder = if (palette.isDark) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.75f)

                        Surface(
                            shape = CircleShape,
                            color = if (isGrid) activeBg else Color.Transparent,
                            border = if (isGrid) BorderStroke(1.dp, activeBorder) else null,
                            shadowElevation = if (isGrid) 1.dp else 0.dp,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { onSetHomeRecentMode("grid") }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                PrototypeSvgIcon(
                                    type = PrototypeIconType.GRID_2X2,
                                    tint = if (isGrid) palette.textMain else palette.textMuted,
                                    modifier = Modifier.size(13.5.dp)
                                )
                            }
                        }
                        Surface(
                            shape = CircleShape,
                            color = if (!isGrid) activeBg else Color.Transparent,
                            border = if (!isGrid) BorderStroke(1.dp, activeBorder) else null,
                            shadowElevation = if (!isGrid) 1.dp else 0.dp,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable { onSetHomeRecentMode("list") }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                PrototypeSvgIcon(
                                    type = PrototypeIconType.LIST_BULLETS,
                                    tint = if (!isGrid) palette.textMain else palette.textMuted,
                                    modifier = Modifier.size(13.5.dp)
                                )
                            }
                        }
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = if (isGrid) palette.accentDark else palette.bgSurface,
                        border = BorderStroke(1.dp, if (isGrid) palette.accentDark else palette.borderSoft),
                        shadowElevation = if (isGrid) 1.5.dp else 0.dp,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable { onSetHomeRecentMode("grid") }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PrototypeSvgIcon(
                                type = PrototypeIconType.GRID_2X2,
                                tint = if (isGrid) palette.bgApp else palette.textMuted,
                                modifier = Modifier.size(13.5.dp)
                            )
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (!isGrid) palette.accentDark else palette.bgSurface,
                        border = BorderStroke(1.dp, if (!isGrid) palette.accentDark else palette.borderSoft),
                        shadowElevation = if (!isGrid) 1.5.dp else 0.dp,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .clickable { onSetHomeRecentMode("list") }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PrototypeSvgIcon(
                                type = PrototypeIconType.LIST_BULLETS,
                                tint = if (!isGrid) palette.bgApp else palette.textMuted,
                                modifier = Modifier.size(13.5.dp)
                            )
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))

        if (books.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = palette.bgCard,
                border = BorderStroke(1.dp, palette.borderSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = strings.emptyLibraryCardTitle,
                        color = palette.textMain,
                        fontSize = 16.5.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = strings.emptyLibraryCardSub,
                        color = palette.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else if (prefs.homeRecentMode == "grid") {
            val rows = books.take(6).chunked(3)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                rows.forEach { rowBooks ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowBooks.forEach { book ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { onOpenBook(book.id) }
                            ) {
                                BookCoverCard(
                                    book = book,
                                    showTitleInsideFallback = false,
                                    importProgress = importingBooksProgress[book.id],
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = book.title,
                                    color = palette.textMain,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = book.displayCollectionTag(strings.isEn),
                                    color = palette.textMuted,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                        repeat(3 - rowBooks.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                books.take(8).forEach { book ->
                    val impPct = importingBooksProgress[book.id]
                    Surface(
                        onClick = { onOpenBook(book.id) },
                        shape = RoundedCornerShape(22.dp),
                        color = palette.bgCard,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            BookCoverCard(
                                book = book,
                                showTitleInsideFallback = false,
                                importProgress = impPct,
                                modifier = Modifier.width(48.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = book.title,
                                    color = palette.textMain,
                                    fontSize = 14.5.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${book.displayAuthor(strings.isEn)} · ${book.year} · ${book.displayCollectionTag(strings.isEn)}",
                                    color = palette.textMuted,
                                    fontSize = 11.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${book.progress}%",
                                color = palette.textMuted,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}
