package com.llunaticsys.llunevie.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.llunaticsys.llunevie.LlunevieUiState
import com.llunaticsys.llunevie.data.local.ShelfEntity
import com.llunaticsys.llunevie.model.CatalogBookCandidate
import com.llunaticsys.llunevie.ui.components.CustomThemeDropdown
import com.llunaticsys.llunevie.ui.components.PrototypeIconType
import com.llunaticsys.llunevie.ui.components.PrototypeSvgIcon
import com.llunaticsys.llunevie.ui.theme.LlunevieStrings
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette
import coil.compose.AsyncImage
import java.util.Calendar

/**
 * 1:1 Add Book Modal Sheet (`#addBookModal` & `#bookDetailSheet` in `llunevie_prototype.html`)
 * - In-hierarchy bottom sheet (`h-[84%] rounded-t-[36px]`)
 * - 4 Mode Selector Tabs (`Cari`, `Pindai ISBN`, `Batch Scan`, `Manual`)
 * - Search list rows match prototype 1:1: flat divider-separated rows with ONLY Cover (48x72dp), Title (15sp Bold), and `Author · Year` (13sp Muted).
 * - Clicking a search row slides up the 1:1 `#bookDetailSheet` with Hero Cover, Category & Publisher badges, ISBN, 4-Column Bento Metadata Strip (`TAHUN | HALAMAN | RATING | EDISI`), Synopsis card, and `Simpan ke Rak Koleksi` primary button!
 * - Manual entry uses clean `lg-surface` rounded-2xl inputs and an inline `Advance` chevron toggle.
 * - Bottom button (`Impor EPUB / PDF Lokal`) features a 1:1 dashed pill border (`#shelveItYourselfBtn`).
 */
@Composable
fun AddBookSheet(
    uiState: LlunevieUiState,
    shelves: List<ShelfEntity>,
    strings: LlunevieStrings,
    onDismiss: () -> Unit,
    onSelectTab: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onSelectCandidateDetail: (CatalogBookCandidate?) -> Unit,
    onPromptSaveCandidate: (CatalogBookCandidate) -> Unit,
    onDismissChooseShelf: () -> Unit = {},
    onSaveCandidateToShelf: (CatalogBookCandidate, String, String) -> Unit,
    onPickLocalFile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLluneviePalette.current

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.45f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
            color = palette.sheetBg,
            border = BorderStroke(1.dp, palette.borderSoft),
            shadowElevation = 24.dp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp)
                        .padding(top = 14.dp, bottom = 18.dp)
                ) {
                    // Drag Handle
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(palette.textMain.copy(alpha = 0.2f))
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Header (`#i18nAddBookHeader` + `#closeAddBookModalBtn`)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (strings.isEn) "Add book" else "Tambah buku",
                            color = palette.textPrimary,
                            fontSize = 22.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Normal
                        )
                        Surface(
                            onClick = onDismiss,
                            shape = CircleShape,
                            color = palette.bgCard.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.size(34.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Canvas(modifier = Modifier.size(11.dp)) {
                                    val strokePx = 1.5.dp.toPx()
                                    drawLine(
                                        color = palette.textPrimary,
                                        start = Offset(0f, 0f),
                                        end = Offset(size.width, size.height),
                                        strokeWidth = strokePx,
                                        cap = StrokeCap.Round
                                    )
                                    drawLine(
                                        color = palette.textPrimary,
                                        start = Offset(size.width, 0f),
                                        end = Offset(0f, size.height),
                                        strokeWidth = strokePx,
                                        cap = StrokeCap.Round
                                    )
                                }
                            }
                        }
                    }

                    // 4 Mode Selector Tabs (`Cari | Pindai ISBN | Batch Scan | Manual`) (1793303330541.jpg)
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(CircleShape)
                            .background(palette.bgCard)
                            .border(1.dp, palette.borderSoft, CircleShape)
                            .padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val tabs = listOf(
                            "search" to strings.tabSearch,
                            "isbn" to strings.tabIsbn,
                            "batch" to strings.tabBatch,
                            "manual" to strings.tabManual
                        )
                        tabs.forEach { (tabKey, label) ->
                            val isSelected = (uiState.addModalTab == tabKey)
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .then(
                                        if (isSelected) {
                                            Modifier
                                                .shadow(elevation = 1.dp, shape = CircleShape, clip = false)
                                                .clip(CircleShape)
                                                .background(palette.bgSurface, CircleShape)
                                                .border(0.75.dp, palette.borderSoft, CircleShape)
                                        } else {
                                            Modifier.clip(CircleShape)
                                        }
                                    )
                                    .clickable { onSelectTab(tabKey) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    color = if (isSelected) palette.textPrimary else palette.textSecondary,
                                    maxLines = 1,
                                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (uiState.addModalTab == "manual") {
                        // SUB-VIEW D: MANUAL BOOK ENTRY (`#addTabManualView` in `llunevie_prototype.html`)
                        var title by remember { mutableStateOf("") }
                        var author by remember { mutableStateOf("") }
                        var pagesStr by remember { mutableStateOf("320") }
                        var category by remember { mutableStateOf("Light Novel") }
                        var format by remember { mutableStateOf("PHYSICAL") }
                        var showAdvance by remember { mutableStateOf(false) }
                        var publisher by remember { mutableStateOf("") }
                        var releaseYear by remember { mutableStateOf(Calendar.getInstance().get(Calendar.YEAR).toString()) }
                        var isbn by remember(uiState.manualBookPrefillIsbn) { mutableStateOf(uiState.manualBookPrefillIsbn ?: "") }
                        var coverUrl by remember { mutableStateOf("") }
                        var lengthCm by remember { mutableStateOf("") }
                        var widthCm by remember { mutableStateOf("") }
                        var weightKg by remember { mutableStateOf("") }
                        var synopsis by remember { mutableStateOf("") }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            PrototypeFormInput(
                                label = if (strings.isId) "JUDUL BUKU / LIGHT NOVEL" else "BOOK / LIGHT NOVEL TITLE",
                                value = title,
                                onValueChange = { title = it },
                                placeholder = if (strings.isId) "Masukkan judul buku..." else "Enter book title..."
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                PrototypeFormInput(
                                    label = if (strings.isId) "PENULIS" else "AUTHOR",
                                    value = author,
                                    onValueChange = { author = it },
                                    placeholder = if (strings.isId) "Masukkan nama penulis..." else "Enter author name...",
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
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = strings.catLabel,
                                        color = palette.textMuted,
                                        fontSize = 9.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    CustomThemeDropdown(
                                        title = strings.catLabel,
                                        selectedValue = category,
                                        options = listOf("Light Novel" to "Light Novel", "Novel" to "Novel"),
                                        onValueSelected = { category = it },
                                        cornerRadius = 16.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "FORMAT",
                                        color = palette.textMuted,
                                        fontSize = 9.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    CustomThemeDropdown(
                                        title = "FORMAT",
                                        selectedValue = format,
                                        options = listOf(
                                            "PHYSICAL" to (if (strings.isId) "Buku Fisik" else "Physical Book"),
                                            "EPUB" to (if (strings.isId) "eBook Digital" else "Digital eBook")
                                        ),
                                        onValueSelected = { format = it },
                                        cornerRadius = 16.dp,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            // Inline "Advance" Text + Chevron Button (`#toggleManualAdvanceBtn`)
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { showAdvance = !showAdvance }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Advance",
                                    color = palette.textMain.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                PrototypeSvgIcon(
                                    type = if (showAdvance) PrototypeIconType.CHEVRON_UP else PrototypeIconType.CHEVRON_DOWN,
                                    tint = palette.textMain.copy(alpha = 0.8f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            if (showAdvance) {
                                HorizontalDivider(color = palette.borderSoft)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    PrototypeFormInput(
                                        label = if (strings.isId) "PENERBIT" else "PUBLISHER",
                                        value = publisher,
                                        onValueChange = { publisher = it },
                                        placeholder = "Gramedia / Haru...",
                                        modifier = Modifier.weight(1f)
                                    )
                                    PrototypeFormInput(
                                        label = if (strings.isId) "TANGGAL RILIS / TAHUN" else "RELEASE DATE / YEAR",
                                        value = releaseYear,
                                        onValueChange = { releaseYear = it },
                                        placeholder = "2025",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    PrototypeFormInput(
                                        label = if (strings.isId) "ISBN (OPSIONAL)" else "ISBN (OPTIONAL)",
                                        value = isbn,
                                        onValueChange = { isbn = it },
                                        placeholder = "978602...",
                                        modifier = Modifier.weight(1f)
                                    )
                                    PrototypeFormInput(
                                        label = if (strings.isId) "URL SAMPUL (OPSIONAL)" else "COVER URL (OPTIONAL)",
                                        value = coverUrl,
                                        onValueChange = { coverUrl = it },
                                        placeholder = "https://...",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Column {
                                    Text(
                                        text = if (strings.isId) "DIMENSI (PANJANG · LEBAR · BERAT)" else "DIMENSIONS (LENGTH · WIDTH · WEIGHT)",
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
                                            placeholder = if (strings.isId) "Panjang (cm)" else "Length (cm)",
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
                                            placeholder = if (strings.isId) "Berat (kg)" else "Weight (kg)",
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                PrototypeFormInput(
                                    label = if (strings.isId) "SINOPSIS (OPSIONAL)" else "SYNOPSIS (OPTIONAL)",
                                    value = synopsis,
                                    onValueChange = { synopsis = it },
                                    placeholder = if (strings.isId) "Ringkasan singkat buku..." else "Brief book summary...",
                                    singleLine = false
                                )
                            }

                            Surface(
                                onClick = {
                                    if (title.isNotBlank()) {
                                        val dimInfo = if (lengthCm.isNotBlank() || widthCm.isNotBlank() || weightKg.isNotBlank()) {
                                            "\n\nDimensi: ${lengthCm.ifBlank { "-" }} × ${widthCm.ifBlank { "-" }} cm · Berat: ${weightKg.ifBlank { "-" }} kg."
                                        } else ""
                                        val parsedHeight = lengthCm.replace(',', '.').toFloatOrNull() ?: 20f
                                        val parsedWidth = widthCm.replace(',', '.').toFloatOrNull() ?: 14f
                                        val parsedWeight = weightKg.replace(',', '.').toFloatOrNull()?.let { it * 1000f } ?: 300f
                                        val candidate = CatalogBookCandidate(
                                            id = "manual-${System.currentTimeMillis()}",
                                            title = title.trim(),
                                            author = author.trim().ifBlank { if (strings.isId) "Penulis" else "Author" },
                                            year = releaseYear.takeLast(4).ifBlank { "2025" },
                                            pages = pagesStr.toIntOrNull()?.coerceAtLeast(1) ?: 320,
                                            rating = "4.80",
                                            edition = publisher.ifBlank { "Cetak / ID" },
                                            publisher = publisher.ifBlank { "Penerbit" },
                                            lang = if (strings.isId) "id" else "en",
                                            isbn = isbn.ifBlank { "—" },
                                            category = category,
                                            coverUrl = coverUrl.ifBlank { null },
                                            coverBgHex = 0xFF2B3A4AL,
                                            synopsis = (synopsis + dimInfo).trim(),
                                            heightCm = parsedHeight,
                                            widthCm = parsedWidth,
                                            lengthCm = parsedHeight,
                                            weightGrams = parsedWeight
                                        )
                                        onPromptSaveCandidate(candidate)
                                    }
                                },
                                shape = CircleShape,
                                color = palette.accentDark,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (strings.isId) "Tambah Buku" else "Add Book",
                                    color = palette.bgApp,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 12.dp)
                                )
                            }
                        }
                    } else {
                        // SUB-VIEW A: UNIFIED PARALLEL SEARCH (`#addTabSearchView` in `llunevie_prototype.html`)
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(42.dp)
                                    .clip(CircleShape)
                                    .background(palette.bgCard.copy(alpha = 0.45f))
                                    .border(1.dp, palette.borderSoft, CircleShape)
                                    .padding(horizontal = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                PrototypeSvgIcon(
                                    type = PrototypeIconType.SEARCH,
                                    tint = palette.textMuted.copy(alpha = 0.65f),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Box(modifier = Modifier.weight(1f)) {
                                    if (uiState.catalogSearchQuery.isEmpty()) {
                                        Text(
                                            text = strings.catalogSearchHint,
                                            color = palette.textMuted,
                                            fontSize = 12.5.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    BasicTextField(
                                        value = uiState.catalogSearchQuery,
                                        onValueChange = onQueryChange,
                                        singleLine = true,
                                        textStyle = TextStyle(color = palette.textPrimary, fontSize = 12.5.sp),
                                        cursorBrush = SolidColor(palette.textPrimary),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                if (uiState.catalogSearchQuery.isNotEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .clickable { onQueryChange("") }
                                            .padding(start = 6.dp)
                                    ) {
                                        Canvas(modifier = Modifier.size(10.dp)) {
                                            val strokePx = 1.4.dp.toPx()
                                            drawLine(
                                                color = palette.textMuted,
                                                start = Offset(0f, 0f),
                                                end = Offset(size.width, size.height),
                                                strokeWidth = strokePx,
                                                cap = StrokeCap.Round
                                            )
                                            drawLine(
                                                color = palette.textMuted,
                                                start = Offset(size.width, 0f),
                                                end = Offset(0f, size.height),
                                                strokeWidth = strokePx,
                                                cap = StrokeCap.Round
                                            )
                                        }
                                    }
                                }
                            }

                            // Minimal Vertical Lines Loading Animation when searching with existing results
                            if (uiState.showTopSearchLoadingBar) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    VerticalPulseBarsLoader(color = palette.textMain)
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                when {
                                    uiState.catalogSearchQuery.trim().length < 2 -> {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = if (strings.isId) "Ketik judul buku, penulis, atau nomor ISBN."
                                                else "Type a book title, author, or ISBN.",
                                                color = palette.textMuted,
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                    uiState.showCenterSearchLoader -> {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                VerticalPulseBarsLoader(color = palette.textMain)
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    text = if (strings.isId) "Mencari \"${uiState.catalogSearchQuery}\"..."
                                                    else "Searching \"${uiState.catalogSearchQuery}\"...",
                                                    color = palette.textMuted,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        }
                                    }
                                    uiState.catalogSearchResults.isEmpty() -> {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.padding(horizontal = 16.dp)
                                            ) {
                                                Text(
                                                    text = if (strings.isId) "Buku tidak ditemukan" else "No books found",
                                                    color = palette.textMain,
                                                    fontSize = 16.sp,
                                                    fontFamily = FontFamily.Serif
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    text = if (strings.isId) "Tidak ada hasil untuk \"${uiState.catalogSearchQuery}\". Pastikan ejaan judul atau nomor ISBN benar, atau gunakan tab Manual."
                                                    else "No results for \"${uiState.catalogSearchQuery}\". Check the title spelling or ISBN digits, or use the Manual tab.",
                                                    color = palette.textMuted,
                                                    fontSize = 12.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                    else -> {
                                        // 1:1 Flat Divider-Separated Search Results: ONLY Cover (48x72dp), Title (15sp Bold), and Author · Year (13sp Muted)!
                                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                                            items(uiState.catalogSearchResults, key = { it.id }) { item ->
                                                Column(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { onSelectCandidateDetail(item) }
                                                ) {
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 12.dp, horizontal = 6.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                                    ) {
                                                        // 48dp x 72dp Cover Thumbnail (`w-12 h-[72px] rounded-[7px]`)
                                                        Box(
                                                            modifier = Modifier
                                                                .width(48.dp)
                                                                .height(72.dp)
                                                                .shadow(4.dp, RoundedCornerShape(7.dp))
                                                                .clip(RoundedCornerShape(7.dp))
                                                                .background(Color(item.coverBgHex)),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Column(
                                                                modifier = Modifier
                                                                    .fillMaxSize()
                                                                    .padding(6.dp),
                                                                verticalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Text("✦", color = Color(0xFFFAF6EB), fontSize = 7.sp)
                                                                Text(
                                                                    text = item.title,
                                                                    color = Color(0xFFFAF6EB),
                                                                    fontSize = 7.sp,
                                                                    fontFamily = FontFamily.Serif,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                    maxLines = 2,
                                                                    overflow = TextOverflow.Ellipsis
                                                                )
                                                                Text(item.year, color = Color(0xFFFAF6EB), fontSize = 7.sp)
                                                            }
                                                            if (!item.coverUrl.isNullOrBlank()) {
                                                                AsyncImage(
                                                                    model = item.coverUrl,
                                                                    contentDescription = item.title,
                                                                    contentScale = ContentScale.Crop,
                                                                    modifier = Modifier.fillMaxSize()
                                                                )
                                                            }
                                                        }

                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(
                                                                text = item.title,
                                                                color = palette.textMain,
                                                                fontSize = 15.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                            Spacer(modifier = Modifier.height(2.dp))
                                                            Text(
                                                                text = "${item.author} · ${item.year}",
                                                                color = palette.textMuted,
                                                                fontSize = 13.sp,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                    HorizontalDivider(color = palette.borderSoft)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    // Bottom Dashed Pill Button: "Impor EPUB / PDF Lokal" (`#shelveItYourselfBtn`)
                    val dashColor = palette.textMuted.copy(alpha = 0.7f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CircleShape)
                            .background(palette.bgSurface)
                            .clickable { onPickLocalFile() },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.matchParentSize()) {
                            drawRoundRect(
                                color = dashColor,
                                style = Stroke(
                                    width = 1.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                                ),
                                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f)
                            )
                        }
                        Text(
                            text = strings.importEpubPdfBtn,
                            color = palette.textMain,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    }
                }

                // SLIDE-UP BOOK DETAIL SHEET (`#bookDetailSheet` in `llunevie_prototype.html`)
                uiState.selectedCatalogBookDetail?.let { detail ->
                    Surface(
                        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                        color = palette.sheetBg,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        shadowElevation = 24.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .heightIn(max = 620.dp)
                            .navigationBarsPadding()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {}
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp)
                                .padding(top = 14.dp, bottom = 18.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterHorizontally)
                                        .width(40.dp)
                                        .height(4.dp)
                                        .clip(CircleShape)
                                        .background(palette.textMain.copy(alpha = 0.2f))
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = if (strings.isId) "Detail Buku" else "Book Details",
                                        color = palette.textMain,
                                        fontSize = 18.sp,
                                        fontFamily = FontFamily.Serif,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Surface(
                                        onClick = { onSelectCandidateDetail(null) },
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

                                // Book Hero Card (`flex gap-4 items-start`)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(96.dp)
                                            .height(144.dp)
                                            .shadow(8.dp, RoundedCornerShape(12.dp))
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color(detail.coverBgHex)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(10.dp),
                                            verticalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("✦", color = Color(0xFFFAF6EB), fontSize = 9.sp)
                                            Text(
                                                text = detail.title,
                                                color = Color(0xFFFAF6EB),
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Serif,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 3
                                            )
                                            Text(detail.year, color = Color(0xFFFAF6EB), fontSize = 9.sp)
                                        }
                                        if (!detail.coverUrl.isNullOrBlank()) {
                                            AsyncImage(
                                                model = detail.coverUrl,
                                                contentDescription = detail.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        // Clean, borderless uppercase monospace category & publisher text
                                        Text(
                                            text = "${detail.category.uppercase()} · ${detail.publisher.uppercase()}",
                                            color = palette.textMuted,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.1.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(
                                            text = detail.title,
                                            color = palette.textMain,
                                            fontSize = 19.sp,
                                            fontFamily = FontFamily.Serif,
                                            fontWeight = FontWeight.SemiBold,
                                            lineHeight = 24.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = detail.author,
                                            color = palette.textMuted,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "ISBN ${detail.isbn}",
                                            color = palette.textMuted.copy(alpha = 0.75f),
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }

                                // 4-Column Metadata Bento Strip (`grid-cols-4 rounded-2xl p-3 text-center divide-x lg-card`)
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = palette.bgCard,
                                    border = BorderStroke(1.dp, palette.borderSoft),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    val editionLabel = when {
                                        detail.edition.contains("1st", ignoreCase = true) -> "1st Ed · ${detail.lang.uppercase()}"
                                        detail.edition.contains("Cetak", ignoreCase = true) || detail.edition.contains("Fisik", ignoreCase = true) -> "Cetak · ${detail.lang.uppercase()}"
                                        detail.edition.contains("Digital", ignoreCase = true) || detail.edition.contains("EPUB", ignoreCase = true) -> "Digital · ${detail.lang.uppercase()}"
                                        else -> "${detail.edition.take(8)} · ${detail.lang.uppercase()}"
                                    }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf(
                                            (if (strings.isId) "TAHUN" else "YEAR") to detail.year,
                                            (if (strings.isId) "HALAMAN" else "PAGES") to detail.pages.toString(),
                                            "RATING" to "★ ${detail.rating}",
                                            (if (strings.isId) "EDISI" else "EDITION") to editionLabel
                                        ).forEachIndexed { i, (mLabel, mVal) ->
                                            if (i > 0) {
                                                Box(
                                                    modifier = Modifier
                                                        .width(1.dp)
                                                        .height(28.dp)
                                                        .background(palette.borderSoft)
                                                )
                                            }
                                            Column(
                                                modifier = Modifier.weight(1f),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = mLabel,
                                                    color = palette.textMuted,
                                                    fontSize = 8.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                    letterSpacing = 0.8.sp
                                                )
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text(
                                                    text = mVal,
                                                    color = palette.textMain,
                                                    fontSize = 14.sp,
                                                    fontFamily = FontFamily.Serif,
                                                    fontWeight = FontWeight.SemiBold,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }
                                }

                                // Synopsis Card
                                Column {
                                    Text(
                                        text = if (strings.isId) "SINOPSIS BUKU" else "BOOK SYNOPSIS",
                                        color = palette.textMuted,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 1.2.sp,
                                        modifier = Modifier.padding(bottom = 6.dp)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = palette.bgCard,
                                        border = BorderStroke(1.dp, palette.borderSoft),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = detail.synopsis,
                                            color = palette.textMain,
                                            fontSize = 12.5.sp,
                                            lineHeight = 20.sp,
                                            modifier = Modifier.padding(16.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                onClick = { onPromptSaveCandidate(detail) },
                                shape = CircleShape,
                                color = palette.accentDark,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (strings.isId) "Simpan ke Rak Koleksi" else "Save to Collection Shelf",
                                    color = palette.bgApp,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VerticalPulseBarsLoader(
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "pulseBars")
    Row(
        modifier = modifier.height(22.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until 5) {
            val scale by transition.animateFloat(
                initialValue = 0.30f,
                targetValue = 0.95f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 425, delayMillis = i * 140, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar_$i"
            )
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .fillMaxHeight(scale)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}
