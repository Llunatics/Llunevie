package com.llunaticsys.llunevie.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llunaticsys.llunevie.data.local.BookEntity
import com.llunaticsys.llunevie.data.local.DailyReadingLogEntity
import com.llunaticsys.llunevie.data.local.HighlightNoteEntity
import com.llunaticsys.llunevie.ui.components.PrototypeIconType
import com.llunaticsys.llunevie.ui.components.PrototypeSvgIcon
import com.llunaticsys.llunevie.ui.theme.LlunevieStrings
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Composable
fun StatsScreen(
    books: List<BookEntity>,
    dailyLogs: List<DailyReadingLogEntity>,
    highlights: List<HighlightNoteEntity>,
    strings: LlunevieStrings,
    onOpenBook: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLluneviePalette.current
    val totalMinutes = books.sumOf { it.timeMinutes }.coerceAtLeast(dailyLogs.sumOf { it.minutesRead })
    val finishedCount = books.count { it.completedAllPages }
    val activeLogs = dailyLogs.filter { it.minutesRead > 0 || it.pagesTurned > 0 }
    val totalPagesTurned = dailyLogs.sumOf { it.pagesTurned }
    val bestDayLog = activeLogs.maxByOrNull { it.minutesRead }
    val avgDailyMins = if (activeLogs.isNotEmpty()) totalMinutes / activeLogs.size else 0

    // Build 168-day real calendar map (24 weeks x 7 days) strictly from recorded dailyLogs
    val logMap = remember(dailyLogs) { dailyLogs.associateBy { it.dateKey } }
    val last168Dates = remember {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -167)
        (0 until 168).map {
            val key = fmt.format(cal.time)
            cal.add(Calendar.DAY_OF_YEAR, 1)
            key
        }
    }

    val sixMonthLabels = remember(strings.isId) {
        val locale = if (strings.isId) Locale("id", "ID") else Locale.ENGLISH
        val fmt = SimpleDateFormat("MMM", locale)
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -5)
        (0 until 6).map {
            val lbl = fmt.format(cal.time)
            cal.add(Calendar.MONTH, 1)
            lbl
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 108.dp)
    ) {
        Text(
            text = strings.statsHeader,
            color = palette.textMain,
            fontSize = 26.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = strings.statsSub,
            color = palette.textMuted,
            fontSize = 12.sp,
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = palette.borderSoft)

        // Back to active book button (`#statsBackToBookBtn` in `llunevie_prototype.html`)
        books.firstOrNull()?.let { latestBook ->
            Spacer(modifier = Modifier.height(14.dp))
            Surface(
                onClick = { onOpenBook(latestBook.id) },
                shape = RoundedCornerShape(16.dp),
                color = palette.bgCard,
                border = BorderStroke(1.dp, palette.borderSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    PrototypeSvgIcon(
                        type = PrototypeIconType.ARROW_LEFT,
                        tint = palette.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = strings.statsBackTo,
                            color = palette.textMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.4.sp
                        )
                        Text(
                            text = latestBook.title,
                            color = palette.textMain,
                            fontSize = 15.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // 4 Top Stat Cards (Starting cleanly at 0 when no books/activity exist)
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatSummaryCard(
                value = formatMinutesText(totalMinutes, strings.isId),
                label = strings.statsTimeRead,
                modifier = Modifier.weight(1f)
            )
            StatSummaryCard(
                value = activeLogs.size.toString(),
                label = strings.statsDaysRunning,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            StatSummaryCard(
                value = finishedCount.toString(),
                label = strings.statsFinished,
                modifier = Modifier.weight(1f)
            )
            StatSummaryCard(
                value = highlights.size.toString(),
                label = strings.statsHighlights,
                modifier = Modifier.weight(1f)
            )
        }

        // 6-Month Activity Heatmap Section (1:1 with `#viewStats` in `llunevie_prototype.html`)
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = strings.statsSixMonthsTitle,
                color = palette.textMain,
                fontSize = 19.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = if (strings.isId) "${activeLogs.size} hari membaca" else "${activeLogs.size} reading days",
                color = palette.textMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = if (strings.isId) "${activeLogs.size} hari bersama buku · rekor terpanjang ${activeLogs.size} hari"
            else "${activeLogs.size} days with books · longest run ${activeLogs.size} days",
            color = palette.textMuted,
            fontSize = 12.sp
        )

        Spacer(modifier = Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            sixMonthLabels.forEach { mLbl ->
                Text(
                    text = mLbl,
                    color = palette.textMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        // 24 columns x 7 rows dot matrix (`dot-matrix-grid`)
        val weeks = last168Dates.chunked(7)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            weeks.forEach { weekDates ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    weekDates.forEach { dateKey ->
                        val mins = logMap[dateKey]?.minutesRead ?: 0
                        val dotColor = when {
                            mins <= 0 -> palette.dotEmpty
                            mins < 15 -> palette.dotL1
                            mins < 30 -> palette.dotL2
                            mins < 60 -> palette.dotL3
                            else -> palette.dotL4
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(2.5.dp))
                                .background(dotColor)
                        )
                    }
                }
            }
        }

        // Bottom-Right SEDIKIT ... BANYAK Legend (`#i18nStatsLess` / `#i18nStatsMore`)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (strings.isId) "SEDIKIT" else "LESS",
                color = palette.textMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(end = 6.dp)
            )
            listOf(
                palette.dotEmpty,
                palette.dotL1,
                palette.dotL2,
                palette.dotL3,
                palette.dotL4
            ).forEachIndexed { i, c ->
                Box(
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .size(10.dp)
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(c)
                        .then(
                            if (i == 0) Modifier.border(0.5.dp, Color.Black.copy(alpha = 0.08f), RoundedCornerShape(2.5.dp))
                            else Modifier
                        )
                )
            }
            Text(
                text = if (strings.isId) "BANYAK" else "MORE",
                color = palette.textMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp,
                modifier = Modifier.padding(start = 6.dp)
            )
        }

        // Where the time went (Per-book reading time breakdown)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = strings.statsWhereTimeWent,
            color = palette.textMain,
            fontSize = 19.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))

        val sortedBooks = remember(books) {
            books.filter { b ->
                val t = b.title.trim()
                !t.startsWith("Searching ISBN", ignoreCase = true) &&
                !t.startsWith("Mencari ISBN", ignoreCase = true) &&
                !t.startsWith("ISBN Not Found", ignoreCase = true) &&
                !t.startsWith("ISBN Tidak Ditemukan", ignoreCase = true)
            }.sortedWith(
                compareByDescending<BookEntity> { it.timeMinutes }
                    .thenByDescending { it.readingProgress }
            )
        }
        var showCompleteBookList by remember { mutableStateOf(false) }
        val displayedBooks = if (showCompleteBookList) sortedBooks else sortedBooks.take(5)
        val isEn = !strings.isId

        if (sortedBooks.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = palette.bgCard,
                border = BorderStroke(1.dp, palette.borderSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (strings.isId) "Belum ada buku di pustaka." else "No books in library yet.",
                    color = palette.textMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                displayedBooks.forEach { book ->
                    val ratio = if (totalMinutes > 0) (book.timeMinutes.toFloat() / totalMinutes.toFloat()).coerceIn(0f, 1f) else (book.progress / 100f)
                    Surface(
                        shape = RoundedCornerShape(18.dp),
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
                                Text(
                                    text = book.title,
                                    color = palette.textMain,
                                    fontSize = 13.5.sp,
                                    fontFamily = FontFamily.Serif,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = "${formatMinutesText(book.timeMinutes, strings.isId)} · ${book.progress}%",
                                    color = palette.textMuted,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { ratio.coerceIn(0f, 1f) },
                                color = palette.textMain,
                                trackColor = palette.borderSoft,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(5.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }
                }

                if (sortedBooks.size > 5) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { showCompleteBookList = !showCompleteBookList }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showCompleteBookList) {
                                if (isEn) "Show less" else "Tampilkan lebih sedikit"
                            } else {
                                if (isEn) "Show complete list (${sortedBooks.size - 5} more)" else "Lihat daftar lengkap (${sortedBooks.size - 5} lainnya)"
                            },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.textSecondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (showCompleteBookList) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Reading Pace Section (`#i18nStatsPace` with divider-separated rows in `llunevie_prototype.html`)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = strings.statsPace,
            color = palette.textMain,
            fontSize = 19.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.fillMaxWidth()) {
            PaceRow(
                label = strings.paceLast30,
                value = if (strings.isId) "${formatMinutesText(totalMinutes, true)} dalam ${activeLogs.size} hari"
                else "${formatMinutesText(totalMinutes, false)} over ${activeLogs.size} day(s)"
            )
            HorizontalDivider(color = palette.borderSoft)
            PaceRow(
                label = strings.paceTypical,
                value = formatMinutesText(avgDailyMins, strings.isId)
            )
            HorizontalDivider(color = palette.borderSoft)
            PaceRow(
                label = strings.paceBest,
                value = if (bestDayLog != null) "${bestDayLog.dateKey} (${formatMinutesText(bestDayLog.minutesRead, strings.isId)})"
                else (if (strings.isId) "Belum tercatat" else "None yet")
            )
            HorizontalDivider(color = palette.borderSoft)
            PaceRow(
                label = strings.paceLongest,
                value = if (strings.isId) "${activeLogs.size} hari" else "${activeLogs.size} day(s)"
            )
            HorizontalDivider(color = palette.borderSoft)
            PaceRow(
                label = strings.pacePages,
                value = totalPagesTurned.toString()
            )
        }
    }
}

@Composable
private fun StatSummaryCard(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    val palette = LocalLluneviePalette.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = palette.bgCard,
        border = BorderStroke(1.dp, palette.borderSoft),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = value,
                color = palette.textMain,
                fontSize = 26.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                color = palette.textMuted,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 1.2.sp
            )
        }
    }
}

@Composable
private fun PaceRow(label: String, value: String) {
    val palette = LocalLluneviePalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = palette.textMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.sp
        )
        Text(
            text = value,
            color = palette.textMain,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium
        )
    }
}
