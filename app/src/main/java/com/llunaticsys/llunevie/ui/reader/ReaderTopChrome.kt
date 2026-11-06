package com.llunaticsys.llunevie.ui.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llunaticsys.llunevie.LlunevieUiState
import com.llunaticsys.llunevie.LlunevieViewModel
import com.llunaticsys.llunevie.data.local.BookEntity
import com.llunaticsys.llunevie.data.local.PageBookmarkEntity
import com.llunaticsys.llunevie.data.local.UserPreferencesState
import com.llunaticsys.llunevie.model.BookChapter
import com.llunaticsys.llunevie.model.DEFAULT_TRANSLATION_LANGUAGES
import com.llunaticsys.llunevie.model.PageAnimStyle
import com.llunaticsys.llunevie.model.ReadingFlowMode
import com.llunaticsys.llunevie.model.TranslationLangOption
import com.llunaticsys.llunevie.model.getActiveTranslationLanguages
import com.llunaticsys.llunevie.normalizeTranslationLangCode
import com.llunaticsys.llunevie.ui.components.PrototypeIconType
import com.llunaticsys.llunevie.ui.components.PrototypeSvgIcon
import com.llunaticsys.llunevie.ui.theme.LlunevieStrings
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette
import kotlin.math.roundToInt

internal enum class TopCenterPillState {
    ZOOM,
    TRANSLATION,
    TITLE
}

@Composable
internal fun BoxScope.ReaderTopChrome(
    showReaderChrome: Boolean,
    isSpread: Boolean,
    isPdfBook: Boolean,
    book: BookEntity,
    uiState: LlunevieUiState,
    prefs: UserPreferencesState,
    strings: LlunevieStrings,
    chapters: List<BookChapter>,
    currentChapter: BookChapter?,
    currentPageRaw: String,
    displayPageIndex: Int,
    bookBookmarks: List<PageBookmarkEntity>,
    readerZoom: Float,
    readerPan: Offset,
    nonFlipZoomScale: Float,
    nonFlipZoomOffset: Offset,
    onResetZoom: () -> Unit,
    isPageTranslationActive: Boolean,
    onIsPageTranslationActiveChange: (Boolean) -> Unit,
    peekOriginalPdfPage: Boolean,
    onPeekOriginalPdfPageChange: (Boolean) -> Unit,
    isTranslatingPage: Boolean,
    showLangDropdown: Boolean,
    onShowLangDropdownChange: (Boolean) -> Unit,
    getPageAtGlobalZero: (Int) -> Triple<Int, Int, String>?,
    onBack: () -> Unit,
    onToggleTocDrawer: (Boolean) -> Unit,
    viewModel: LlunevieViewModel?
) {
    val palette = LocalLluneviePalette.current

    AnimatedVisibility(
        visible = showReaderChrome,
        enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 400f)) { -it } + fadeIn(tween(180)),
        exit = slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { -it } + fadeOut(tween(140)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .fillMaxWidth()
    ) {
        val cutoutStart = WindowInsets.displayCutout.asPaddingValues().calculateStartPadding(LocalLayoutDirection.current)
        val topBarStartPad = if (isSpread) maxOf(16.dp, cutoutStart + 16.dp) else 16.dp
        val topBarEndPad = if (isSpread) maxOf(16.dp, cutoutStart + 16.dp) else 16.dp
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(start = topBarStartPad, end = topBarEndPad, top = 10.dp, bottom = 10.dp)
            ) {
                // Left: 34.dp circular Back button
                Surface(
                    onClick = onBack,
                    shape = CircleShape,
                    color = palette.bgSurface,
                    border = BorderStroke(1.dp, palette.borderSoft),
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .minimumInteractiveComponentSize()
                        .size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        PrototypeSvgIcon(
                            type = PrototypeIconType.ARROW_LEFT,
                            tint = palette.textMain,
                            modifier = Modifier.size(15.dp),
                            contentDescription = if (strings.isId) "Kembali" else "Back"
                        )
                    }
                }

                // Center Morphing Pill (`#readerTitlePill`) - 100% mathematically centered on screen
                val isFlipbookMode = prefs.animatePageTurn && prefs.pageAnimStyle == PageAnimStyle.FLIPBOOK &&
                    (prefs.readingFlowMode == ReadingFlowMode.PAGINATED || prefs.readingFlowMode == ReadingFlowMode.SPREAD)
                val effectiveZoom = if (isFlipbookMode) readerZoom else nonFlipZoomScale

                val topPillState = when {
                    effectiveZoom > 1.01f -> TopCenterPillState.ZOOM
                    isPageTranslationActive || uiState.isTranslationActive -> TopCenterPillState.TRANSLATION
                    else -> TopCenterPillState.TITLE
                }

                Surface(
                    shape = CircleShape,
                    color = palette.bgSurface,
                    border = BorderStroke(1.dp, palette.borderSoft),
                    shadowElevation = 0.dp,
                    modifier = Modifier
                        .height(32.dp)
                        .align(Alignment.Center)
                        .animateContentSize(animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f))
                ) {
                    AnimatedContent(
                        targetState = topPillState,
                        transitionSpec = {
                            (fadeIn(tween(180)) + scaleIn(initialScale = 0.94f, animationSpec = tween(180)))
                                .togetherWith(fadeOut(tween(130)) + scaleOut(targetScale = 0.94f, animationSpec = tween(130)))
                        },
                        label = "topCenterPillTransition"
                    ) { state ->
                        when (state) {
                            TopCenterPillState.ZOOM -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .clickable { onResetZoom() }
                                        .padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val pct = (effectiveZoom * 100f).roundToInt()
                                    Text(
                                        text = "$pct% · Reset",
                                        color = palette.textMain,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        style = TextStyle(
                                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                                            lineHeight = 11.sp
                                        )
                                    )
                                }
                            }
                            TopCenterPillState.TRANSLATION -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    val isReplaceActive = !peekOriginalPdfPage && uiState.translationMode == "replace"
                                    Box(
                                        modifier = Modifier
                                            .height(24.dp)
                                            .clip(CircleShape)
                                            .background(if (isReplaceActive) palette.pillActiveBg else Color.Transparent)
                                            .clickable {
                                                onPeekOriginalPdfPageChange(false)
                                                viewModel?.setTranslationMode("replace")
                                            }
                                            .padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (strings.isId) "Ganti" else "Replace",
                                            color = if (isReplaceActive) palette.textMain else palette.textMuted,
                                            fontSize = 11.sp,
                                            fontWeight = if (isReplaceActive) FontWeight.SemiBold else FontWeight.Medium,
                                            style = TextStyle(
                                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                lineHeight = 11.sp
                                            )
                                        )
                                    }

                                    if (isPdfBook) {
                                        val isOrigActive = peekOriginalPdfPage
                                        Box(
                                            modifier = Modifier
                                                .height(24.dp)
                                                .clip(CircleShape)
                                                .background(if (isOrigActive) palette.pillActiveBg else Color.Transparent)
                                                .clickable {
                                                    onPeekOriginalPdfPageChange(!peekOriginalPdfPage)
                                                }
                                                .padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (strings.isId) "Asli" else "Original",
                                                color = if (isOrigActive) palette.textMain else palette.textMuted,
                                                fontSize = 11.sp,
                                                fontWeight = if (isOrigActive) FontWeight.SemiBold else FontWeight.Medium,
                                                style = TextStyle(
                                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                    lineHeight = 11.sp
                                                )
                                            )
                                        }
                                    } else {
                                        val isBilingualActive = uiState.translationMode == "bilingual"
                                        Box(
                                            modifier = Modifier
                                                .height(24.dp)
                                                .clip(CircleShape)
                                                .background(if (isBilingualActive) palette.pillActiveBg else Color.Transparent)
                                                .clickable {
                                                    viewModel?.setTranslationMode("bilingual")
                                                }
                                                .padding(horizontal = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = if (strings.isId) "Dwibahasa" else "Bilingual",
                                                color = if (isBilingualActive) palette.textMain else palette.textMuted,
                                                fontSize = 11.sp,
                                                fontWeight = if (isBilingualActive) FontWeight.SemiBold else FontWeight.Medium,
                                                style = TextStyle(
                                                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                    lineHeight = 11.sp
                                                )
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(12.dp)
                                            .background(palette.borderSoft)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .height(24.dp)
                                            .clip(CircleShape)
                                            .clickable { onShowLangDropdownChange(!showLangDropdown) }
                                            .padding(horizontal = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val activeNormCode = normalizeTranslationLangCode(uiState.translationTargetLang)
                                        val activeLangs = remember(prefs.customTranslationLangs) {
                                            getActiveTranslationLanguages(prefs.customTranslationLangs)
                                        }
                                        val activeLangOption = activeLangs.find { normalizeTranslationLangCode(it.code) == activeNormCode }
                                            ?: DEFAULT_TRANSLATION_LANGUAGES.find { normalizeTranslationLangCode(it.code) == activeNormCode }
                                            ?: TranslationLangOption(code = activeNormCode, badgeCode = activeNormCode.uppercase().take(2), nativeName = activeNormCode)
                                        val currentLangLabel = activeLangOption.badgeCode
                                        Text(
                                            text = "$currentLangLabel ▾",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold,
                                            color = palette.textMain,
                                            style = TextStyle(
                                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                lineHeight = 11.sp
                                            )
                                        )
                                    }

                                    if (isTranslatingPage) {
                                        Box(
                                            modifier = Modifier.padding(horizontal = 4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                strokeWidth = 1.5.dp,
                                                color = palette.textMuted,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(1.dp)
                                            .height(12.dp)
                                            .background(palette.borderSoft)
                                    )

                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                onIsPageTranslationActiveChange(false)
                                                onPeekOriginalPdfPageChange(false)
                                                viewModel?.setTranslationActive(false)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "✕",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = palette.textMuted,
                                            style = TextStyle(
                                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                lineHeight = 11.sp
                                            )
                                        )
                                    }
                                }
                            }
                            TopCenterPillState.TITLE -> {
                                if (isSpread) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .clickable {
                                                onIsPageTranslationActiveChange(true)
                                                onPeekOriginalPdfPageChange(false)
                                                viewModel?.setTranslationActive(true)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        PrototypeSvgIcon(
                                            type = PrototypeIconType.TRANSLATE,
                                            tint = palette.textMain,
                                            modifier = Modifier.size(14.dp),
                                            contentDescription = if (strings.isId) "Terjemahkan halaman" else "Translate page"
                                        )
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .padding(start = 14.dp, end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = book.title,
                                            color = palette.textMain,
                                            fontSize = 11.5.sp,
                                            fontFamily = FontFamily.Serif,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = TextStyle(
                                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                lineHeight = 12.sp
                                            ),
                                            modifier = Modifier
                                                .widthIn(max = 168.dp)
                                                .clickable { onToggleTocDrawer(true) }
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Box(
                                            Modifier
                                                .width(1.dp)
                                                .height(13.dp)
                                                .background(palette.borderSoft)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .clickable {
                                                    onIsPageTranslationActiveChange(true)
                                                    onPeekOriginalPdfPageChange(false)
                                                    viewModel?.setTranslationActive(true)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            PrototypeSvgIcon(
                                                type = PrototypeIconType.TRANSLATE,
                                                tint = palette.textMain,
                                                modifier = Modifier.size(13.5.dp),
                                                contentDescription = if (strings.isId) "Terjemahkan halaman" else "Translate page"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Right: 34.dp circular Bookmark button
                val isCurrentPageBookmarked = if (isSpread) {
                    val sStart = (displayPageIndex / 2) * 2
                    bookBookmarks.any { it.pageNumber == sStart + 1 || it.pageNumber == sStart + 2 }
                } else {
                    bookBookmarks.any { it.pageNumber == displayPageIndex + 1 }
                }
                Surface(
                    onClick = {
                        val curPageNum = displayPageIndex + 1
                        val curTriple = getPageAtGlobalZero(displayPageIndex)
                        val chTitle = curTriple?.let { chapters.getOrNull(it.first)?.title }
                            ?: currentChapter?.title
                            ?: (if (strings.isId) "Bab ${uiState.activeChapterIndex + 1}" else "Chapter ${uiState.activeChapterIndex + 1}")
                        val pageRaw = curTriple?.third.orEmpty().ifBlank { currentPageRaw }
                        val snippet = pageRaw.replace(Regex("\\[ILLUST:[^]]+]"), "")
                            .replace(Regex("\\[PDF_PAGE:[^]]+]"), "")
                            .trim()
                            .take(100)
                        viewModel?.togglePageBookmark(
                            bookId = book.id,
                            pageNumber = curPageNum,
                            chapterIndex = curTriple?.first ?: uiState.activeChapterIndex,
                            chapterTitle = chTitle,
                            snippetText = snippet
                        )
                    },
                    shape = CircleShape,
                    color = if (isCurrentPageBookmarked) palette.accent.copy(alpha = 0.15f) else palette.bgSurface,
                    border = BorderStroke(1.dp, if (isCurrentPageBookmarked) palette.accent else palette.borderSoft),
                    modifier = Modifier
                        .size(34.dp)
                        .align(Alignment.CenterEnd)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isCurrentPageBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = "Bookmark",
                            tint = if (isCurrentPageBookmarked) palette.accent else palette.textMain,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showLangDropdown && uiState.isTranslationActive,
                enter = fadeIn(animationSpec = tween(150)) +
                    scaleIn(initialScale = 0.96f, animationSpec = tween(150)) +
                    expandVertically(expandFrom = Alignment.Top, animationSpec = tween(160)),
                exit = fadeOut(animationSpec = tween(110)) +
                    scaleOut(targetScale = 0.96f, animationSpec = tween(110)) +
                    shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(120))
            ) {
                val activeNormCode = normalizeTranslationLangCode(uiState.translationTargetLang)
                val activeLangs = remember(prefs.customTranslationLangs) {
                    getActiveTranslationLanguages(prefs.customTranslationLangs)
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = palette.bgSurface,
                    border = BorderStroke(1.dp, palette.borderSoft),
                    shadowElevation = 0.dp,
                    tonalElevation = 0.dp,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .width(200.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(5.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        activeLangs.forEach { langOpt ->
                            val isSelected = normalizeTranslationLangCode(langOpt.code) == activeNormCode
                            Surface(
                                onClick = {
                                    onShowLangDropdownChange(false)
                                    viewModel?.setTranslationTargetLang(langOpt.code)
                                },
                                shape = RoundedCornerShape(11.dp),
                                color = if (isSelected) palette.pillActiveBg else Color.Transparent,
                                border = if (isSelected) BorderStroke(1.dp, palette.pillActiveBorder) else null,
                                shadowElevation = 0.dp,
                                tonalElevation = 0.dp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 11.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Text(
                                            text = langOpt.badgeCode.lowercase(),
                                            color = if (isSelected) palette.textMain else palette.textSecondary,
                                            fontSize = 12.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                        )
                                        Text(
                                            text = "(${langOpt.nativeName})",
                                            color = if (isSelected) palette.textMain else palette.textMuted,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                                        )
                                    }
                                    if (isSelected) {
                                        PrototypeSvgIcon(
                                            type = PrototypeIconType.CHECK,
                                            tint = palette.textMain,
                                            modifier = Modifier.size(13.dp)
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
