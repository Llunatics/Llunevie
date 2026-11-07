package com.llunaticsys.llunevie.ui.reader

import android.content.res.Configuration
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llunaticsys.llunevie.LlunevieUiState
import com.llunaticsys.llunevie.LlunevieViewModel
import com.llunaticsys.llunevie.data.local.UserPreferencesState
import com.llunaticsys.llunevie.model.AppThemeMode
import com.llunaticsys.llunevie.model.PageAnimStyle
import com.llunaticsys.llunevie.model.ReadingFlowMode
import com.llunaticsys.llunevie.ui.components.ClassicGlassSegmentedControl
import com.llunaticsys.llunevie.ui.components.FlyleafThinSlider
import com.llunaticsys.llunevie.ui.components.ModernSwitch
import com.llunaticsys.llunevie.ui.components.PrototypeIconType
import com.llunaticsys.llunevie.ui.components.PrototypeSvgIcon
import com.llunaticsys.llunevie.ui.theme.LlunevieStrings
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette
import kotlin.math.roundToInt

@Composable
internal fun BoxScope.ReaderTypographySheet(
    uiState: LlunevieUiState,
    prefs: UserPreferencesState,
    strings: LlunevieStrings,
    isPdfBook: Boolean,
    activeDraggingSliderKey: String?,
    onActiveDraggingSliderKeyChanged: (String?) -> Unit,
    onToggleTypeSheet: (Boolean, String?) -> Unit,
    onSetTheme: (AppThemeMode) -> Unit,
    onSetReadingFlowMode: (ReadingFlowMode) -> Unit,
    onSetPageAnimStyle: (PageAnimStyle) -> Unit,
    onUpdateTypography: (
        fontFamily: String?,
        fontSizeSp: Int?,
        fontWeight: Int?,
        textAlign: String?,
        lineHeight: Float?,
        paraSpacing: Float?,
        letterSpacing: Float?,
        wordSpacing: Int?,
        pageMargin: Int?,
        paraIndent: Boolean?,
        commitToDisk: Boolean
    ) -> Unit,
    viewModel: LlunevieViewModel?
) {
    val palette = LocalLluneviePalette.current
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    // 4. IN-HIERARCHY READER TYPOGRAPHY & FLOW SHEET (`#readerTypeSheet` in `llunevie_prototype.html`)
    // Using an in-hierarchy overlay instead of ModalBottomSheet prevents WindowManager crashes during orientation switches!
    val scrimAlpha by animateFloatAsState(
        targetValue = if (activeDraggingSliderKey != null) 0f else 0.35f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "settingsScrimAlpha"
    )
    val nonActiveAlpha by animateFloatAsState(
        targetValue = if (activeDraggingSliderKey != null) 0f else 1f,
        animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
        label = "nonActiveAlpha"
    )

    // Backdrop Scrim
    AnimatedVisibility(
        visible = uiState.showReaderTypeSheet,
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(160))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onToggleTypeSheet(false, null) }
        )
    }

    // Bottom Sheet Panel
    AnimatedVisibility(
        visible = uiState.showReaderTypeSheet,
        enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 380f)) { it } + fadeIn(tween(180)),
        exit = slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(150)),
        modifier = Modifier.align(Alignment.BottomCenter)
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = palette.bgSurface.copy(alpha = nonActiveAlpha),
            border = BorderStroke(1.dp, palette.borderSoft.copy(alpha = nonActiveAlpha)),
            shadowElevation = (8f * nonActiveAlpha).dp,
            modifier = Modifier
                .widthIn(min = 340.dp, max = 420.dp)
                .fillMaxWidth()
                .heightIn(max = if (isLandscape) 310.dp else 460.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                // Top Tab Switcher (TEXT | THEME | FLOW / ALUR | TEMA) + Close Button (✕)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(nonActiveAlpha),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val availableTabs = if (isPdfBook) {
                        listOf(
                            "turn" to (if (strings.isId) "ALUR" else "FLOW"),
                            "page" to (if (strings.isId) "TEMA" else "THEME")
                        )
                    } else {
                        listOf(
                            "text" to (if (strings.isId) "TEKS" else "TEXT"),
                            "page" to (if (strings.isId) "TEMA" else "THEME"),
                            "turn" to (if (strings.isId) "ALUR" else "FLOW")
                        )
                    }
                    val effectiveTab = if (isPdfBook && uiState.activeReaderSheetTab == "text") "turn" else uiState.activeReaderSheetTab

                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(palette.bgCard)
                            .border(1.dp, palette.borderSoft, CircleShape)
                            .padding(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        availableTabs.forEach { (tabKey, label) ->
                            val isSelected = (effectiveTab == tabKey)
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
                                    .clickable { onToggleTypeSheet(true, tabKey) },
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

                    Spacer(modifier = Modifier.width(10.dp))
                    Surface(
                        onClick = { onToggleTypeSheet(false, null) },
                        shape = CircleShape,
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.size(32.dp)
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

                Spacer(modifier = Modifier.height(10.dp))

                val currentTabKey = if (isPdfBook && uiState.activeReaderSheetTab == "text") "turn" else uiState.activeReaderSheetTab

                AnimatedContent(
                    targetState = currentTabKey,
                    transitionSpec = {
                        val initialIdx = when (initialState) {
                            "text", "type" -> 0
                            "page" -> 1
                            "turn" -> 2
                            else -> 0
                        }
                        val targetIdx = when (targetState) {
                            "text", "type" -> 0
                            "page" -> 1
                            "turn" -> 2
                            else -> 0
                        }
                        val dir = if (targetIdx >= initialIdx) 1 else -1
                        (slideInHorizontally(spring(dampingRatio = 0.88f, stiffness = 420f)) { w -> dir * (w / 3) } + fadeIn(tween(180)))
                            .togetherWith(slideOutHorizontally(tween(160, easing = FastOutSlowInEasing)) { w -> -dir * (w / 3) } + fadeOut(tween(140)))
                    },
                    modifier = Modifier.animateContentSize(spring(dampingRatio = 0.88f, stiffness = 420f)),
                    label = "readerTypeTabContent"
                ) { tab ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when (tab) {
                            // TAB 1: SIZE, ALIGNMENT, WEIGHT & UNIFIED "FONT" SECTION (1:1 with #sheetTabText)
                            "text", "type" -> {
                                Column(
                                    modifier = Modifier.alpha(if (activeDraggingSliderKey == null || activeDraggingSliderKey == "FONT_SIZE") 1f else nonActiveAlpha)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = if (strings.isId) "UKURAN HURUF" else "FONT SIZE",
                                            color = palette.textMuted,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.sp
                                        )
                                        Text(
                                            text = "${prefs.fontSizeSp}px",
                                            color = palette.textMain,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    FlyleafThinSlider(
                                        value = prefs.fontSizeSp.toFloat(),
                                        onValueChange = {
                                            onActiveDraggingSliderKeyChanged("FONT_SIZE")
                                            onUpdateTypography(null, it.roundToInt(), null, null, null, null, null, null, null, null, false)
                                        },
                                        onValueChangeFinished = {
                                            onActiveDraggingSliderKeyChanged(null)
                                            viewModel?.commitTypography()
                                        },
                                        valueRange = 12f..28f
                                    )
                                }

                                // 2-Column Grid: PERATAAN TEKS + KETEBALAN HURUF
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .alpha(nonActiveAlpha),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (strings.isId) "PERATAAN TEKS" else "TEXT ALIGNMENT",
                                            color = palette.textMuted,
                                            fontSize = 9.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        ClassicGlassSegmentedControl(
                                            items = listOf(
                                                "left" to (if (strings.isId) "Kiri" else "Left"),
                                                "justify" to "Justify",
                                                "center" to (if (strings.isId) "Tengah" else "Center")
                                            ),
                                            selectedItem = prefs.textAlign.lowercase(),
                                            onItemSelected = { onUpdateTypography(null, null, null, it, null, null, null, null, null, null, true) },
                                            height = 34.dp
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (strings.isId) "KETEBALAN HURUF" else "FONT WEIGHT",
                                            color = palette.textMuted,
                                            fontSize = 9.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.sp,
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        ClassicGlassSegmentedControl(
                                            items = listOf(
                                                400 to "Normal",
                                                500 to "Medium",
                                                600 to (if (strings.isId) "Tebal" else "Bold")
                                            ),
                                            selectedItem = prefs.fontWeight,
                                            onItemSelected = { onUpdateTypography(null, null, it, null, null, null, null, null, null, null, true) },
                                            height = 34.dp
                                        )
                                    }
                                }

                                // FONT SECTION
                                Column(modifier = Modifier.alpha(nonActiveAlpha)) {
                                    Text(
                                        text = "FONT",
                                        color = palette.textMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    val readerFontContext = LocalContext.current
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        CURATED_READER_FONTS.chunked(3).forEach { rowFonts ->
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                rowFonts.forEach { (famKey, famDisplay) ->
                                                    val selected = prefs.fontFamily == famKey
                                                    val chipFontFamily = remember(famKey) { getReaderFontFamily(readerFontContext, famKey) }
                                                    Surface(
                                                        onClick = { onUpdateTypography(famKey, null, null, null, null, null, null, null, null, null, true) },
                                                        shape = RoundedCornerShape(8.dp),
                                                        color = if (selected) palette.textPrimary else palette.bgSurface,
                                                        border = BorderStroke(1.dp, if (selected) palette.textPrimary else palette.borderSoft),
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .height(32.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                    ) {
                                                        Box(contentAlignment = Alignment.Center) {
                                                            Text(
                                                                text = famDisplay,
                                                                color = if (selected) palette.bgApp else palette.textPrimary,
                                                                fontSize = 11.sp,
                                                                fontFamily = chipFontFamily,
                                                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                                                maxLines = 1,
                                                                overflow = TextOverflow.Ellipsis,
                                                                textAlign = TextAlign.Center
                                                            )
                                                        }
                                                    }
                                                }
                                                if (rowFonts.size < 3) {
                                                    repeat(3 - rowFonts.size) {
                                                        Spacer(modifier = Modifier.weight(1f))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // TAB 2: THEME & COMFORT SPACING (1:1 with #sheetTabPage)
                            "page" -> {
                                Column(modifier = Modifier.alpha(nonActiveAlpha)) {
                                    Text(
                                        text = if (strings.isId) "TEMA BACA" else "READING THEME",
                                        color = palette.textMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        val isSystemDark = isSystemInDarkTheme()
                                        val effectiveTheme = prefs.effectiveTheme(isSystemDark)
                                        val themeColors = listOf(
                                            Triple(AppThemeMode.PAPER, Color(0xFFF5F2EA), Color(0xFF23201A)),
                                            Triple(AppThemeMode.WHITE, Color(0xFFFFFFFF), Color(0xFF18181B)),
                                            Triple(AppThemeMode.SEPIA, Color(0xFFEFE6D5), Color(0xFF2C2217)),
                                            Triple(AppThemeMode.BLACK, Color(0xFF141413), Color(0xFFEDEAE2))
                                        )
                                        themeColors.forEach { (mode, chipBg, chipFg) ->
                                            val active = effectiveTheme == mode
                                            Surface(
                                                onClick = { onSetTheme(mode) },
                                                shape = RoundedCornerShape(16.dp),
                                                color = chipBg,
                                                border = BorderStroke(
                                                    width = if (active) 2.dp else 1.dp,
                                                    color = if (active) palette.textMain else palette.borderSoft
                                                ),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = mode.displayLabel,
                                                    color = chipFg,
                                                    fontSize = 12.sp,
                                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                                    textAlign = TextAlign.Center,
                                                    modifier = Modifier.padding(vertical = 8.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                // PDF Theme Settings (only for PDF books)
                                if (isPdfBook) {
                                    Spacer(modifier = Modifier.height(16.dp))

                                    // Apply Theme to PDF Pages toggle
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel?.setPdfThemeTintEnabled(!prefs.pdfThemeTintEnabled) }
                                            .padding(vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (strings.isId) "Adaptasi Tema pada Kertas PDF" else "Apply Theme to PDF Pages",
                                                color = palette.textMain,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (strings.isId) "Sesuaikan warna halaman teks PDF dengan tema Paper, Sepia, atau Gelap" else "Tint or invert PDF text pages to match Paper, Sepia, or Dark theme",
                                                color = palette.textMuted,
                                                fontSize = 10.sp,
                                                lineHeight = 13.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        ModernSwitch(
                                            checked = prefs.pdfThemeTintEnabled,
                                            onCheckedChange = { viewModel?.setPdfThemeTintEnabled(it) }
                                        )
                                    }

                                    // Preserve Illustration Colors toggle
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(enabled = prefs.pdfThemeTintEnabled) {
                                                viewModel?.setPdfProtectIllustrations(!prefs.pdfProtectIllustrations)
                                            }
                                            .padding(vertical = 12.dp)
                                            .alpha(if (prefs.pdfThemeTintEnabled) 1f else 0.5f),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = if (strings.isId) "Jaga Warna Asli Ilustrasi & Sampul" else "Preserve Original Illustration Colors",
                                                color = palette.textMain,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = if (strings.isId) "Jangan inversi halaman sampul dan gambar ilustrasi saat mode gelap/sepia aktif" else "Keep original colors on cover and artwork pages in dark/sepia mode",
                                                color = palette.textMuted,
                                                fontSize = 10.sp,
                                                lineHeight = 13.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        ModernSwitch(
                                            checked = prefs.pdfProtectIllustrations,
                                            onCheckedChange = { if (prefs.pdfThemeTintEnabled) viewModel?.setPdfProtectIllustrations(it) }
                                        )
                                    }
                                }

                                // If not PDF, show 2x2 Comfort Sliders Grid & Margins/Indents
                                if (!isPdfBook) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .alpha(if (activeDraggingSliderKey == null || activeDraggingSliderKey == "LINE_SPACING") 1f else nonActiveAlpha)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = if (strings.isId) "SPASI BARIS" else "LINE SPACING",
                                                    color = palette.textMuted,
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(
                                                    text = String.format("%.2f", prefs.lineHeightMultiplier),
                                                    color = palette.textMain,
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            FlyleafThinSlider(
                                                value = prefs.lineHeightMultiplier,
                                                onValueChange = {
                                                    onActiveDraggingSliderKeyChanged("LINE_SPACING")
                                                    onUpdateTypography(null, null, null, null, it, null, null, null, null, null, false)
                                                },
                                                onValueChangeFinished = {
                                                    onActiveDraggingSliderKeyChanged(null)
                                                    viewModel?.commitTypography()
                                                },
                                                valueRange = 1.2f..2.4f
                                            )
                                        }

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .alpha(if (activeDraggingSliderKey == null || activeDraggingSliderKey == "PARA_SPACING") 1f else nonActiveAlpha)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = if (strings.isId) "JARAK PARAGRAF" else "PARA SPACING",
                                                    color = palette.textMuted,
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(
                                                    text = String.format("%.1fem", prefs.paraSpacingEm),
                                                    color = palette.textMain,
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            FlyleafThinSlider(
                                                value = prefs.paraSpacingEm,
                                                onValueChange = {
                                                    onActiveDraggingSliderKeyChanged("PARA_SPACING")
                                                    onUpdateTypography(null, null, null, null, null, it, null, null, null, null, false)
                                                },
                                                onValueChangeFinished = {
                                                    onActiveDraggingSliderKeyChanged(null)
                                                    viewModel?.commitTypography()
                                                },
                                                valueRange = 0.4f..2.2f
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .alpha(if (activeDraggingSliderKey == null || activeDraggingSliderKey == "LETTER_SPACING") 1f else nonActiveAlpha)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = if (strings.isId) "SPASI HURUF" else "LETTER SPACING",
                                                    color = palette.textMuted,
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(
                                                    text = String.format("%.2fem", prefs.letterSpacingEm),
                                                    color = palette.textMain,
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            FlyleafThinSlider(
                                                value = prefs.letterSpacingEm,
                                                onValueChange = {
                                                    onActiveDraggingSliderKeyChanged("LETTER_SPACING")
                                                    onUpdateTypography(null, null, null, null, null, null, it, null, null, null, false)
                                                },
                                                onValueChangeFinished = {
                                                    onActiveDraggingSliderKeyChanged(null)
                                                    viewModel?.commitTypography()
                                                },
                                                valueRange = -0.02f..0.10f
                                            )
                                        }

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .alpha(if (activeDraggingSliderKey == null || activeDraggingSliderKey == "WORD_SPACING") 1f else nonActiveAlpha)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = if (strings.isId) "SPASI KATA" else "WORD SPACING",
                                                    color = palette.textMuted,
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(
                                                    text = "${prefs.wordSpacingPx}px",
                                                    color = palette.textMain,
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            FlyleafThinSlider(
                                                value = prefs.wordSpacingPx.toFloat(),
                                                onValueChange = {
                                                    onActiveDraggingSliderKeyChanged("WORD_SPACING")
                                                    onUpdateTypography(null, null, null, null, null, null, null, it.roundToInt(), null, null, false)
                                                },
                                                onValueChangeFinished = {
                                                    onActiveDraggingSliderKeyChanged(null)
                                                    viewModel?.commitTypography()
                                                },
                                                valueRange = 0f..8f
                                            )
                                        }
                                    }

                                    // Bottom Row: MARGIN TEPI + INDENTASI PARAGRAF
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .alpha(if (activeDraggingSliderKey == null || activeDraggingSliderKey == "PAGE_MARGIN") 1f else nonActiveAlpha)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = if (strings.isId) "MARGIN TEPI" else "PAGE MARGIN",
                                                    color = palette.textMuted,
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                                Text(
                                                    text = "${prefs.pageMarginDp}px",
                                                    color = palette.textMain,
                                                    fontSize = 9.sp,
                                                    fontFamily = FontFamily.Monospace
                                                )
                                            }
                                            FlyleafThinSlider(
                                                value = prefs.pageMarginDp.toFloat(),
                                                onValueChange = {
                                                    onActiveDraggingSliderKeyChanged("PAGE_MARGIN")
                                                    onUpdateTypography(null, null, null, null, null, null, null, null, it.roundToInt(), null, false)
                                                },
                                                onValueChangeFinished = {
                                                    onActiveDraggingSliderKeyChanged(null)
                                                    viewModel?.commitTypography()
                                                },
                                                valueRange = 12f..48f
                                            )
                                        }

                                        Column(
                                            modifier = Modifier
                                                .weight(1f)
                                                .alpha(nonActiveAlpha)
                                        ) {
                                            Text(
                                                text = if (strings.isId) "INDENTASI PARAGRAF" else "PARA INDENT",
                                                color = palette.textMuted,
                                                fontSize = 9.sp,
                                                fontFamily = FontFamily.Monospace,
                                                modifier = Modifier.padding(bottom = 6.dp)
                                            )
                                            ClassicGlassSegmentedControl(
                                                items = listOf(
                                                    false to (if (strings.isId) "Tanpa" else "None"),
                                                    true to "Indent"
                                                ),
                                                selectedItem = prefs.paraIndent,
                                                onItemSelected = { onUpdateTypography(null, null, null, null, null, null, null, null, null, it, true) },
                                                height = 34.dp
                                            )
                                        }
                                    }
                                }
                            }

                            // TAB 3: FLOW MODES & PAGE ANIMATION (1:1 with #sheetTabTurn)
                            "turn" -> {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .alpha(nonActiveAlpha),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = if (strings.isId) "MODE ALUR BACA" else "READING FLOW MODE",
                                        color = palette.textMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 1.sp
                                    )
                                    val modePairs = ReadingFlowMode.entries.chunked(2)
                                    modePairs.forEach { rowModes ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            rowModes.forEach { mode ->
                                                val selected = prefs.readingFlowMode == mode
                                                val modeIcon = when (mode) {
                                                    ReadingFlowMode.PAGINATED -> PrototypeIconType.CHEVRON_RIGHT
                                                    ReadingFlowMode.SCROLL -> PrototypeIconType.CHEVRON_DOWN
                                                    ReadingFlowMode.WEBTOON -> PrototypeIconType.LIST_BULLETS
                                                    ReadingFlowMode.SPREAD -> PrototypeIconType.FULL_COVER
                                                }

                                                Row(
                                                    modifier = Modifier
                                                        .weight(1f)
                                                        .heightIn(min = 62.dp)
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .background(if (selected) palette.textPrimary else palette.bgCard)
                                                        .border(1.dp, if (selected) palette.textPrimary else palette.borderSoft, RoundedCornerShape(14.dp))
                                                        .clickable { onSetReadingFlowMode(mode) }
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    PrototypeSvgIcon(
                                                        type = modeIcon,
                                                        tint = if (selected) palette.bgApp else palette.textSecondary,
                                                        modifier = Modifier.size(16.dp),
                                                        strokeWidthDp = 1.8f,
                                                        contentDescription = mode.localizedLabel(prefs.lang)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = mode.localizedLabel(prefs.lang),
                                                            fontSize = 12.5.sp,
                                                            lineHeight = 15.sp,
                                                            fontWeight = FontWeight.SemiBold,
                                                            color = if (selected) palette.bgApp else palette.textPrimary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = mode.localizedDesc(prefs.lang),
                                                            fontSize = 10.sp,
                                                            lineHeight = 13.sp,
                                                            color = if (selected) palette.bgApp.copy(alpha = 0.78f) else palette.textSecondary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    HorizontalDivider(color = palette.borderSoft)

                                    Text(
                                        text = if (strings.isId) "GAYA ANIMASI HALAMAN" else "PAGE ANIMATION STYLE",
                                        color = palette.textMuted,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = 1.sp
                                    )
                                    ClassicGlassSegmentedControl(
                                        items = PageAnimStyle.entries.map { it to it.label },
                                        selectedItem = prefs.pageAnimStyle,
                                        onItemSelected = { onSetPageAnimStyle(it) },
                                        height = 36.dp
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
