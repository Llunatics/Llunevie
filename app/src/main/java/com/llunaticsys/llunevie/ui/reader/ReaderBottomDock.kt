package com.llunaticsys.llunevie.ui.reader

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llunaticsys.llunevie.GlobalPageMetrics
import com.llunaticsys.llunevie.LlunevieUiState
import com.llunaticsys.llunevie.LlunevieViewModel
import com.llunaticsys.llunevie.data.local.BookEntity
import com.llunaticsys.llunevie.data.local.HighlightNoteEntity
import com.llunaticsys.llunevie.data.local.PageBookmarkEntity
import com.llunaticsys.llunevie.data.local.UserPreferencesState
import com.llunaticsys.llunevie.data.remote.TranslationRepository
import com.llunaticsys.llunevie.model.BookChapter
import com.llunaticsys.llunevie.model.HighlightColorKey
import com.llunaticsys.llunevie.model.PageAnimStyle
import com.llunaticsys.llunevie.model.ReadingFlowMode
import com.llunaticsys.llunevie.model.getActiveTranslationLanguages
import com.llunaticsys.llunevie.normalizeTranslationLangCode
import com.llunaticsys.llunevie.ui.components.LlunevieThinSlider
import com.llunaticsys.llunevie.ui.components.PrototypeIconType
import com.llunaticsys.llunevie.ui.components.PrototypeSvgIcon
import com.llunaticsys.llunevie.ui.components.targetLanguageDisplayName
import com.llunaticsys.llunevie.ui.theme.LlunevieStrings
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun BoxScope.ReaderBottomChrome(
    showReaderChrome: Boolean,
    isTwoPageSpread: Boolean,
    prefs: UserPreferencesState,
    uiState: LlunevieUiState,
    strings: LlunevieStrings,
    metrics: GlobalPageMetrics,
    chaptersCount: Int,
    showPageScrubber: Boolean,
    onShowPageScrubberChange: (Boolean) -> Unit,
    scrubberPageValue: Float,
    onScrubberPageValueChange: (Float) -> Unit,
    getPageAtGlobalZero: (Int) -> Triple<Int, Int, String>?,
    onJumpToChapterAndPage: (Int, Int) -> Unit,
    onTurnPage: (Int) -> Unit,
    onExternalFlipTrigger: (Int) -> Unit,
    onToggleTocDrawer: (Boolean) -> Unit,
    onToggleTypeSheet: (Boolean, String?) -> Unit
) {
    val palette = LocalLluneviePalette.current

    AnimatedVisibility(
        visible = showReaderChrome && !isTwoPageSpread,
        enter = slideInVertically(spring(dampingRatio = 0.86f, stiffness = 400f)) { it } + fadeIn(tween(180)),
        exit = slideOutVertically(tween(180, easing = FastOutSlowInEasing)) { it } + fadeOut(tween(140)),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
    ) {
        val showArrows = prefs.readingFlowMode == ReadingFlowMode.PAGINATED || prefs.readingFlowMode == ReadingFlowMode.SPREAD
        val pageIndicatorLabel = when (prefs.readingFlowMode) {
            ReadingFlowMode.WEBTOON -> {
                if (strings.isId) "WEBTOON · ${metrics.totalBookPages} HAL (${chaptersCount.coerceAtLeast(1)} BAB)"
                else "WEBTOON · ${metrics.totalBookPages} P (${chaptersCount.coerceAtLeast(1)} CH)"
            }
            ReadingFlowMode.SCROLL -> {
                if (strings.isId) "GULIR · ${metrics.totalBookPages} HAL (${chaptersCount.coerceAtLeast(1)} BAB)"
                else "SCROLL · ${metrics.totalBookPages} P (${chaptersCount.coerceAtLeast(1)} CH)"
            }
            ReadingFlowMode.SPREAD -> {
                val rightNum = (metrics.currentGlobalPage + 1).coerceAtMost(metrics.totalBookPages)
                val rangeStr = if (rightNum > metrics.currentGlobalPage) "${metrics.currentGlobalPage}–$rightNum" else "${metrics.currentGlobalPage}"
                if (strings.isId) "HAL $rangeStr DARI ${metrics.totalBookPages} · ${metrics.globalPct}%"
                else "PAGE $rangeStr OF ${metrics.totalBookPages} · ${metrics.globalPct}%"
            }
            ReadingFlowMode.PAGINATED -> {
                if (strings.isId) "HAL ${metrics.currentGlobalPage} DARI ${metrics.totalBookPages} · ${metrics.globalPct}%"
                else "PAGE ${metrics.currentGlobalPage} OF ${metrics.totalBookPages} · ${metrics.globalPct}%"
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                onClick = { onToggleTocDrawer(true) },
                shape = CircleShape,
                color = palette.bgSurface,
                border = BorderStroke(1.dp, palette.borderSoft),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    PrototypeSvgIcon(
                        type = PrototypeIconType.TOC_LIST,
                        tint = palette.textMain,
                        modifier = Modifier.size(16.dp),
                        contentDescription = if (strings.isId) "Daftar Isi" else "Table of contents"
                    )
                }
            }

            // In-Place Animated Bottom Scrubber Capsule (Never shrinks book viewport!)
            AnimatedContent(
                targetState = showPageScrubber,
                transitionSpec = {
                    fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                },
                label = "bottomScrubberTransition",
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .height(40.dp)
            ) { isScrubberOpen ->
                if (!isScrubberOpen) {
                    Surface(
                        shape = CircleShape,
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            if (showArrows) {
                                Text(
                                    text = "‹",
                                    color = palette.textMain.copy(alpha = 0.75f),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            if (prefs.animatePageTurn && prefs.pageAnimStyle == PageAnimStyle.FLIPBOOK) {
                                                onExternalFlipTrigger(-1)
                                            } else {
                                                onTurnPage(-1)
                                            }
                                        }
                                        .padding(horizontal = 6.dp)
                                )
                            }
                            Text(
                                text = pageIndicatorLabel,
                                color = palette.textMain,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 1.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onShowPageScrubberChange(true) }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                            if (showArrows) {
                                Text(
                                    text = "›",
                                    color = palette.textMain.copy(alpha = 0.75f),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable {
                                            if (prefs.animatePageTurn && prefs.pageAnimStyle == PageAnimStyle.FLIPBOOK) {
                                                onExternalFlipTrigger(1)
                                            } else {
                                                onTurnPage(1)
                                            }
                                        }
                                        .padding(horizontal = 6.dp)
                                )
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = CircleShape,
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${scrubberPageValue.roundToInt().coerceIn(1, metrics.totalBookPages.coerceAtLeast(1))} / ${metrics.totalBookPages}",
                                color = palette.textMain,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.SemiBold
                            )
                            LlunevieThinSlider(
                                value = scrubberPageValue.coerceIn(1f, metrics.totalBookPages.toFloat().coerceAtLeast(1f)),
                                onValueChange = { newVal ->
                                    onScrubberPageValueChange(newVal)
                                },
                                onValueChangeFinished = {
                                    val target = scrubberPageValue.roundToInt().coerceIn(1, metrics.totalBookPages.coerceAtLeast(1))
                                    val targetZero = target - 1
                                    val triple = getPageAtGlobalZero(targetZero)
                                    if (triple != null) {
                                        onJumpToChapterAndPage(triple.first, triple.second)
                                    }
                                },
                                valueRange = 1f..metrics.totalBookPages.toFloat().coerceAtLeast(1f),
                                modifier = Modifier.weight(1f)
                            )
                            Surface(
                                onClick = { onShowPageScrubberChange(false) },
                                shape = CircleShape,
                                color = palette.accentDark,
                                modifier = Modifier.size(26.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("✓", color = palette.bgApp, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Surface(
                onClick = { onToggleTypeSheet(!uiState.showReaderTypeSheet, null) },
                shape = CircleShape,
                color = palette.bgSurface,
                border = BorderStroke(1.dp, palette.borderSoft),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "Aa",
                        color = palette.textMain,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.ReaderSpreadLeftVerticalBar(
    isSpread: Boolean,
    prefs: UserPreferencesState,
    uiState: LlunevieUiState,
    metrics: GlobalPageMetrics,
    displayPageIndex: Int,
    onDisplayPageIndexChange: (Int) -> Unit,
    showInlinePageSlider: Boolean,
    onShowInlinePageSliderChange: (Boolean) -> Unit,
    onBack: () -> Unit = {},
    onToggleTocDrawer: (Boolean) -> Unit,
    onToggleTypeSheet: (Boolean, String?) -> Unit,
    onTurnPage: (Int) -> Unit,
    onExternalFlipTrigger: (Int) -> Unit,
    viewModel: LlunevieViewModel?
) {
    if (!isSpread) return
    val palette = LocalLluneviePalette.current
    val strings = com.llunaticsys.llunevie.ui.theme.getLlunevieStrings(prefs.lang)
    var scrubPage by remember(metrics.currentGlobalPage) {
        mutableIntStateOf((metrics.currentGlobalPage - 1).coerceAtLeast(0))
    }

    // Back button positioned near the top-left corner (retained from beta.6)
    Surface(
        onClick = onBack,
        shape = CircleShape,
        color = palette.bgSurface,
        border = BorderStroke(1.dp, palette.borderSoft),
        shadowElevation = 4.dp,
        modifier = Modifier
            .align(Alignment.TopStart)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(top = 12.dp, start = 10.dp)
            .size(42.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            PrototypeSvgIcon(
                type = PrototypeIconType.ARROW_LEFT,
                tint = palette.textMain,
                modifier = Modifier.size(16.dp),
                contentDescription = if (strings.isId) "Kembali" else "Back"
            )
        }
    }

    // Left vertical menu pill centered vertically on the left edge
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(start = 10.dp)
            .width(42.dp)
            .shadow(elevation = 4.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(color = palette.bgSurface, shape = CircleShape)
            .border(width = 1.dp, color = palette.borderSoft, shape = CircleShape)
            .animateContentSize(
                animationSpec = spring<IntSize>(dampingRatio = 0.82f, stiffness = 380f)
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = showInlinePageSlider,
            transitionSpec = {
                (fadeIn(animationSpec = tween(durationMillis = 160, delayMillis = 60)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(durationMillis = 160, delayMillis = 60)))
                    .togetherWith(
                        fadeOut(animationSpec = tween(durationMillis = 70)) +
                            scaleOut(targetScale = 0.92f, animationSpec = tween(durationMillis = 70))
                    ) using SizeTransform(clip = false)
            },
            label = "LeftVerticalBarContent"
        ) { isSliderMode ->
            if (!isSliderMode) {
                Column(
                    modifier = Modifier
                        .width(42.dp)
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 1. Chapters & Illustrations button (32.dp circle)
                    Surface(
                        onClick = { onToggleTocDrawer(true) },
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PrototypeSvgIcon(
                                type = PrototypeIconType.TOC_LIST,
                                tint = palette.textMain,
                                modifier = Modifier.size(15.dp),
                                contentDescription = if (strings.isId) "Daftar Isi" else "Table of contents"
                            )
                        }
                    }

                    // 2. Previous Spread button (28.dp circle)
                    Surface(
                        onClick = {
                            if (prefs.animatePageTurn && prefs.pageAnimStyle == PageAnimStyle.FLIPBOOK) {
                                onExternalFlipTrigger(-1)
                            } else {
                                onTurnPage(-1)
                            }
                        },
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("⌃", color = palette.textMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 3. Compact Clickable Page Indicator: tapping morphs capsule into vertical slider!
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                scrubPage = (metrics.currentGlobalPage - 1).coerceAtLeast(0)
                                onShowInlinePageSliderChange(true)
                            }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${metrics.currentGlobalPage}",
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = palette.textPrimary,
                            maxLines = 1,
                            softWrap = false,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                        Box(
                            modifier = Modifier
                                .padding(vertical = 3.dp)
                                .width(14.dp)
                                .height(1.dp)
                                .background(palette.borderSoft)
                        )
                        Text(
                            text = "${metrics.totalBookPages}",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = palette.textMuted,
                            maxLines = 1,
                            softWrap = false,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }

                    // 4. Next Spread button (28.dp circle)
                    Surface(
                        onClick = {
                            if (prefs.animatePageTurn && prefs.pageAnimStyle == PageAnimStyle.FLIPBOOK) {
                                onExternalFlipTrigger(1)
                            } else {
                                onTurnPage(1)
                            }
                        },
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("⌄", color = palette.textMain, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // 5. Aa Reader Settings button (32.dp circle)
                    Surface(
                        onClick = { onToggleTypeSheet(!uiState.showReaderTypeSheet, null) },
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Aa",
                                color = palette.textMain,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            } else {
                val totalPages = metrics.totalBookPages.coerceAtLeast(1)
                val initialFraction = if (totalPages > 1) {
                    (displayPageIndex.toFloat() / (totalPages - 1).toFloat()).coerceIn(0f, 1f)
                } else 0f

                var isVerticalDragging by remember { mutableStateOf(false) }
                var verticalDragFraction by remember(showInlinePageSlider) {
                    mutableFloatStateOf(initialFraction)
                }

                if (!isVerticalDragging) {
                    verticalDragFraction = initialFraction
                }

                val currentFraction = verticalDragFraction
                val previewPage = (currentFraction * (totalPages - 1)).roundToInt().coerceIn(0, totalPages - 1)

                Column(
                    modifier = Modifier
                        .width(42.dp)
                        .height(220.dp)
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "${previewPage + 1}",
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = palette.textPrimary,
                            maxLines = 1,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                        Text(
                            text = "$totalPages",
                            fontSize = 8.5.sp,
                            fontFamily = FontFamily.Monospace,
                            color = palette.textMuted,
                            maxLines = 1,
                            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                        )
                    }

                    val currentTotalPages by rememberUpdatedState(totalPages)
                    val currentDisplayPageIndex by rememberUpdatedState(displayPageIndex)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    down.consume()
                                    isVerticalDragging = true
                                    val thumbRadiusPx = 8.dp.toPx()
                                    val trackHeightPx = (size.height.toFloat() - thumbRadiusPx * 2f).coerceAtLeast(1f)
                                    fun updateFromY(y: Float) {
                                        val totPages = currentTotalPages
                                        val frac = ((y - thumbRadiusPx) / trackHeightPx).coerceIn(0f, 1f)
                                        verticalDragFraction = frac
                                    }
                                    updateFromY(down.position.y)

                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        if (!change.pressed) {
                                            change.consume()
                                            break
                                        }
                                        change.consume()
                                        updateFromY(change.position.y)
                                    }
                                    isVerticalDragging = false
                                    val totPages = currentTotalPages
                                    val targetZero = (verticalDragFraction * (totPages - 1)).roundToInt().coerceIn(0, (totPages - 1).coerceAtLeast(0))
                                    val isBilingualSpread = isSpread && uiState.isTranslationActive &&
                                        uiState.translationMode.equals("bilingual", ignoreCase = true)
                                    val leftZero = if (isSpread && !isBilingualSpread) (targetZero / 2) * 2 else targetZero
                                    if (leftZero != currentDisplayPageIndex) {
                                        onDisplayPageIndexChange(leftZero)
                                        viewModel?.jumpToGlobalPage(leftZero)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .background(palette.borderSoft)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(currentFraction)
                                    .background(palette.textPrimary)
                            )
                        }

                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                            val trackH = maxHeight
                            val thumbSize = 16.dp
                            val availableH = (trackH - thumbSize).coerceAtLeast(0.dp)
                            val thumbOffset = availableH * currentFraction

                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .offset(y = thumbOffset)
                                    .size(thumbSize)
                                    .shadow(3.dp, CircleShape)
                                    .clip(CircleShape)
                                    .background(palette.textPrimary)
                                    .border(1.5.dp, palette.bgSurface, CircleShape)
                            )
                        }
                    }

                    Surface(
                        onClick = { onShowInlinePageSliderChange(false) },
                        shape = CircleShape,
                        color = palette.bgCard,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier
                            .size(28.dp)
                            .padding(top = 2.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "✕",
                                color = palette.textMain,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpreadVerticalRotatedTitle(
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
                style = TextStyle(
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                    lineHeight = (fontSizeSp + 1f).sp
                ),
                modifier = Modifier.rotate(90f)
            )
        },
        modifier = modifier
    ) { measurables, constraints ->
        val maxLen = constraints.maxHeight.coerceAtLeast(24)
        val placeable = measurables.first().measure(
            Constraints(
                minWidth = 0,
                maxWidth = maxLen,
                minHeight = 0,
                maxHeight = constraints.maxWidth.coerceAtLeast(20)
            )
        )
        val layoutW = constraints.maxWidth.coerceAtLeast(placeable.height)
        val layoutH = placeable.width.coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(layoutW, layoutH) {
            val x = (layoutW - placeable.width) / 2
            val y = (layoutH - placeable.height) / 2
            placeable.place(x, y)
        }
    }
}

@Composable
internal fun BoxScope.ReaderSpreadRightVerticalBar(
    isSpread: Boolean,
    book: BookEntity,
    prefs: UserPreferencesState,
    uiState: LlunevieUiState,
    strings: LlunevieStrings,
    chapters: List<BookChapter>,
    currentChapter: BookChapter?,
    currentPageRaw: String,
    displayPageIndex: Int,
    bookBookmarks: List<PageBookmarkEntity>,
    isPageTranslationActive: Boolean,
    onIsPageTranslationActiveChange: (Boolean) -> Unit,
    onPeekOriginalPdfPageChange: (Boolean) -> Unit,
    onShowLangDropdownChange: (Boolean) -> Unit,
    getPageAtGlobalZero: (Int) -> Triple<Int, Int, String>?,
    onToggleTocDrawer: (Boolean) -> Unit,
    viewModel: LlunevieViewModel?
) {
    if (!isSpread) return
    val palette = LocalLluneviePalette.current
    val isTransActive = isPageTranslationActive || uiState.isTranslationActive
    val isBilingualSpread = isSpread && isTransActive &&
        uiState.translationMode.equals("bilingual", ignoreCase = true)
    val isCurrentPageBookmarked = if (!isBilingualSpread) {
        val sStart = (displayPageIndex / 2) * 2
        bookBookmarks.any { it.pageNumber == sStart + 1 || it.pageNumber == sStart + 2 }
    } else {
        bookBookmarks.any { it.pageNumber == displayPageIndex + 1 }
    }

    // Bookmark button positioned near the top-right corner (retained vertical position from beta.6,
    // shifted slightly left to align with the right title pill and clear the flush right-center ‹ tab)
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
        shadowElevation = 4.dp,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(top = 12.dp, end = 26.dp)
            .size(42.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isCurrentPageBookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                contentDescription = "Bookmark",
                tint = if (isCurrentPageBookmarked) palette.accent else palette.textMain,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    // Right vertical pill: Book Title + Translate Access, centered vertically and shifted slightly left
    // so it does not collide with the flush right-center ‹ notes drawer tab (20.dp wide)
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(end = 26.dp)
            .width(42.dp)
            .shadow(elevation = 4.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(color = palette.bgSurface, shape = CircleShape)
            .border(width = 1.dp, color = palette.borderSoft, shape = CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(42.dp)
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SpreadVerticalRotatedTitle(
                text = book.title,
                color = palette.textMain,
                fontSizeSp = 11.5f,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp, max = 148.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleTocDrawer(true) }
                    .padding(vertical = 4.dp)
            )

            Box(
                modifier = Modifier
                    .width(16.dp)
                    .height(1.dp)
                    .background(palette.borderSoft)
            )

            Surface(
                onClick = {
                    val nextActive = !isTransActive
                    onIsPageTranslationActiveChange(nextActive)
                    onPeekOriginalPdfPageChange(false)
                    if (nextActive && uiState.translationMode.equals("original", ignoreCase = true)) {
                        viewModel?.setTranslationMode("replace")
                    }
                    if (!nextActive) {
                        onShowLangDropdownChange(false)
                    }
                    viewModel?.setTranslationActive(nextActive)
                },
                shape = CircleShape,
                color = if (isTransActive) palette.pillActiveBg else Color.Transparent,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    PrototypeSvgIcon(
                        type = PrototypeIconType.TRANSLATE,
                        tint = if (isTransActive) palette.accent else palette.textMain,
                        modifier = Modifier.size(15.dp),
                        contentDescription = if (strings.isId) "Terjemahkan halaman" else "Translate page"
                    )
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.ReaderHighlightNotePopover(
    selectedQuoteForPopover: String?,
    selectedPopoverInUpperHalf: Boolean,
    onDismissQuotePopover: () -> Unit,
    showInlineNoteInput: Boolean,
    onShowInlineNoteInputChange: (Boolean) -> Unit,
    inlineNoteText: String,
    onInlineNoteTextChange: (String) -> Unit,
    showInlineTranslation: Boolean,
    onShowInlineTranslationChange: (Boolean) -> Unit,
    inlineTranslationText: String?,
    onInlineTranslationTextChange: (String?) -> Unit,
    isTranslatingInline: Boolean,
    onIsTranslatingInlineChange: (Boolean) -> Unit,
    inlineTranslationError: String?,
    onInlineTranslationErrorChange: (String?) -> Unit,
    bookHighlights: List<HighlightNoteEntity>,
    prefs: UserPreferencesState,
    strings: LlunevieStrings,
    translationRepo: TranslationRepository,
    onSaveHighlightNote: (String, String, HighlightColorKey, String?, Int, Int) -> Unit,
    onDeleteHighlightNote: (String) -> Unit,
    onToggleNotesDrawer: (Boolean) -> Unit,
    onOpenInBookSearch: (String) -> Unit,
    viewModel: LlunevieViewModel?
) {
    val palette = LocalLluneviePalette.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    var cachedQuoteForPopover by remember { mutableStateOf<String?>(null) }
    if (selectedQuoteForPopover != null) {
        cachedQuoteForPopover = selectedQuoteForPopover
    }

    AnimatedVisibility(
        visible = selectedQuoteForPopover != null,
        enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.94f, animationSpec = spring(dampingRatio = 0.82f, stiffness = 420f)),
        exit = fadeOut(tween(130)) + scaleOut(targetScale = 0.94f, animationSpec = tween(130)),
        modifier = Modifier
            .align(if (selectedPopoverInUpperHalf) Alignment.BottomCenter else Alignment.TopCenter)
            .padding(horizontal = 16.dp, vertical = 52.dp)
            .fillMaxWidth()
    ) {
        val quote = cachedQuoteForPopover ?: selectedQuoteForPopover.orEmpty()
        val existingHl = bookHighlights.find { it.quoteText.equals(quote, ignoreCase = true) }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = palette.sheetBg.copy(alpha = 0.97f),
            border = BorderStroke(1.dp, palette.borderSoft),
            shadowElevation = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = quote,
                            color = palette.textMain,
                            fontSize = 13.5.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = existingHl?.noteText?.ifBlank { null }
                                ?: (if (strings.isId) "Sorotan tersimpan di bab ini" else "Highlight saved in this chapter"),
                            color = palette.textMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Surface(
                        onClick = {
                            onDismissQuotePopover()
                            onShowInlineNoteInputChange(false)
                            onShowInlineTranslationChange(false)
                            onInlineTranslationTextChange(null)
                            onInlineTranslationErrorChange(null)
                        },
                        shape = CircleShape,
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✕", color = palette.textMain, fontSize = 10.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HighlightColorKey.entries.forEach { key ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(palette.highlightDot(key))
                                    .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                                    .clickable {
                                        onSaveHighlightNote(quote, existingHl?.noteText.orEmpty(), key, existingHl?.id, -1, -1)
                                        onDismissQuotePopover()
                                    }
                            )
                        }
                    }
                    Surface(
                        onClick = {
                            if (existingHl != null) onDeleteHighlightNote(existingHl.id)
                            onDismissQuotePopover()
                        },
                        shape = CircleShape,
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.size(30.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            PrototypeSvgIcon(
                                type = PrototypeIconType.TRASH_CAN,
                                tint = palette.textMain,
                                modifier = Modifier.size(15.dp),
                                strokeWidthDp = 1.8f,
                                contentDescription = if (strings.isId) "Hapus penanda" else "Delete bookmark"
                            )
                        }
                    }
                }

                if (showInlineNoteInput) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = palette.borderSoft)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                if (inlineNoteText.isEmpty()) {
                                    Text(
                                        text = if (strings.isId) "Tulis catatan untuk kutipan ini..." else "Write note...",
                                        color = palette.textMuted,
                                        fontSize = 11.5.sp
                                    )
                                }
                                BasicTextField(
                                    value = inlineNoteText,
                                    onValueChange = { onInlineNoteTextChange(it) },
                                    singleLine = true,
                                    textStyle = TextStyle(color = palette.textMain, fontSize = 11.5.sp),
                                    cursorBrush = SolidColor(palette.textMain),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        Surface(
                            onClick = {
                                onSaveHighlightNote(
                                    quote,
                                    inlineNoteText,
                                    HighlightColorKey.fromKey(existingHl?.colorKey),
                                    existingHl?.id,
                                    -1,
                                    -1
                                )
                                onShowInlineNoteInputChange(false)
                                onDismissQuotePopover()
                            },
                            shape = CircleShape,
                            color = palette.accentDark,
                            modifier = Modifier.height(30.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 12.dp)) {
                                Text(
                                    text = if (strings.isId) "Simpan" else "Save",
                                    color = palette.bgApp,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = showInlineTranslation) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        HorizontalDivider(color = palette.borderSoft)
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val badge = if (prefs.translatorEngine == "free") "FREE INSTANT"
                                    else "AI · ${prefs.aiModelName.substringAfterLast('/').uppercase()}"
                                    Text(
                                        text = badge,
                                        fontSize = 9.5.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = palette.textMuted
                                    )
                                    Surface(
                                        onClick = {
                                            val langs = getActiveTranslationLanguages(prefs.customTranslationLangs).map { it.code }
                                            val currentNorm = normalizeTranslationLangCode(prefs.translatorTargetLang)
                                            val currentIdx = langs.indexOfFirst { normalizeTranslationLangCode(it) == currentNorm }.let { if (it == -1) 0 else it }
                                            val nextIdx = (currentIdx + 1) % langs.size
                                            val newLang = langs[nextIdx]
                                            viewModel?.updateTranslatorPrefs(targetLang = newLang)
                                            scope.launch {
                                                onIsTranslatingInlineChange(true)
                                                onInlineTranslationErrorChange(null)
                                                val updatedPrefs = prefs.copy(translatorTargetLang = newLang)
                                                val res = translationRepo.translate(quote, updatedPrefs, context)
                                                if (res.isSuccess) {
                                                    onInlineTranslationTextChange(res.getOrNull())
                                                } else {
                                                    onInlineTranslationErrorChange(res.exceptionOrNull()?.message ?: "Translation error")
                                                }
                                                onIsTranslatingInlineChange(false)
                                            }
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        color = palette.accent.copy(alpha = 0.12f),
                                        modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                    ) {
                                        Text(
                                            text = "→ ${targetLanguageDisplayName(prefs.translatorTargetLang, strings.isEn)} ▾",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = palette.accent,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (isTranslatingInline) {
                                    Text(
                                        text = if (strings.isId) "Menerjemahkan kutipan..." else "Translating passage...",
                                        fontSize = 11.5.sp,
                                        fontFamily = FontFamily.Serif,
                                        color = palette.textMuted,
                                        modifier = Modifier.padding(vertical = 8.dp)
                                    )
                                } else if (inlineTranslationError != null) {
                                    Column {
                                        Text(
                                            text = inlineTranslationError,
                                            fontSize = 11.5.sp,
                                            color = Color(0xFFE53935),
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                        Surface(
                                            onClick = {
                                                scope.launch {
                                                    onIsTranslatingInlineChange(true)
                                                    onInlineTranslationErrorChange(null)
                                                    val res = translationRepo.translate(quote, prefs, context)
                                                    if (res.isSuccess) {
                                                        onInlineTranslationTextChange(res.getOrNull())
                                                    } else {
                                                        onInlineTranslationErrorChange(res.exceptionOrNull()?.message ?: "Translation error")
                                                    }
                                                    onIsTranslatingInlineChange(false)
                                                }
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = palette.accentDark,
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            Text(
                                                text = if (strings.isId) "Coba Lagi" else "Retry",
                                                color = palette.bgApp,
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                            )
                                        }
                                    }
                                } else if (!inlineTranslationText.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 150.dp)
                                            .verticalScroll(rememberScrollState())
                                    ) {
                                        Text(
                                            text = inlineTranslationText,
                                            fontSize = 13.sp,
                                            fontFamily = FontFamily.Serif,
                                            lineHeight = 19.sp,
                                            color = palette.textMain
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(color = palette.borderSoft)
                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(inlineTranslationText))
                                                viewModel?.showToast(if (strings.isId) "Terjemahan disalin" else "Translation copied")
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = palette.bgCard,
                                            border = BorderStroke(1.dp, palette.borderSoft)
                                        ) {
                                            Text(
                                                text = if (strings.isId) "Salin Terjemahan" else "Copy",
                                                fontSize = 10.5.sp,
                                                color = palette.textMain,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }

                                        Surface(
                                            onClick = {
                                                onSaveHighlightNote(
                                                    quote,
                                                    inlineTranslationText,
                                                    HighlightColorKey.fromKey(existingHl?.colorKey),
                                                    existingHl?.id,
                                                    -1,
                                                    -1
                                                )
                                                onDismissQuotePopover()
                                                onShowInlineTranslationChange(false)
                                            },
                                            shape = RoundedCornerShape(6.dp),
                                            color = palette.accentDark
                                        ) {
                                            Text(
                                                text = if (strings.isId) "Simpan ke Catatan" else "Save to Note",
                                                fontSize = 10.5.sp,
                                                color = palette.bgApp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = palette.borderSoft)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. [Pen/Note Icon] Catatan
                    Surface(
                        onClick = {
                            onInlineNoteTextChange(existingHl?.noteText.orEmpty())
                            onShowInlineNoteInputChange(!showInlineNoteInput)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (showInlineNoteInput) palette.accentDark else palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            PrototypeSvgIcon(
                                type = PrototypeIconType.EDIT_PEN,
                                tint = if (showInlineNoteInput) palette.bgApp else palette.textMain,
                                modifier = Modifier.size(14.dp),
                                contentDescription = strings.hlNote
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.hlNote,
                                color = if (showInlineNoteInput) palette.bgApp else palette.textMain,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }

                    // 2. [Copy Icon] Salin
                    Surface(
                        onClick = {
                            clipboard.setText(AnnotatedString(quote))
                            onDismissQuotePopover()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            PrototypeSvgIcon(
                                type = PrototypeIconType.BOOKMARK_OUTLINE,
                                tint = palette.textMain,
                                modifier = Modifier.size(14.dp),
                                contentDescription = strings.hlCopy
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.hlCopy,
                                color = palette.textMain,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }

                    // 3. [Bookmark/List Icon] Di Buku
                    Surface(
                        onClick = {
                            onSaveHighlightNote(
                                quote,
                                existingHl?.noteText.orEmpty(),
                                HighlightColorKey.fromKey(existingHl?.colorKey),
                                existingHl?.id,
                                -1,
                                -1
                            )
                            onDismissQuotePopover()
                            onToggleNotesDrawer(true)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            PrototypeSvgIcon(
                                type = PrototypeIconType.TOC_LIST,
                                tint = palette.textMain,
                                modifier = Modifier.size(14.dp),
                                contentDescription = strings.hlInBook
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.hlInBook,
                                color = palette.textMain,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }

                    // 4. [Search Icon] Cari
                    Surface(
                        onClick = {
                            val q = quote.take(50)
                            onDismissQuotePopover()
                            onOpenInBookSearch(q)
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            PrototypeSvgIcon(
                                type = PrototypeIconType.SEARCH,
                                tint = palette.textMain,
                                modifier = Modifier.size(14.dp),
                                contentDescription = strings.hlFind
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.hlFind,
                                color = palette.textMain,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }

                    // 5. [Translate Icon] Terjemah
                    Surface(
                        onClick = {
                            val nextShow = !showInlineTranslation
                            onShowInlineTranslationChange(nextShow)
                            if (nextShow && inlineTranslationText == null) {
                                scope.launch {
                                    onIsTranslatingInlineChange(true)
                                    onInlineTranslationErrorChange(null)
                                    val res = translationRepo.translate(quote, prefs, context)
                                    if (res.isSuccess) {
                                        onInlineTranslationTextChange(res.getOrNull())
                                    } else {
                                        onInlineTranslationErrorChange(res.exceptionOrNull()?.message ?: "Translation error")
                                    }
                                    onIsTranslatingInlineChange(false)
                                }
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (showInlineTranslation) palette.accentDark else palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            PrototypeSvgIcon(
                                type = PrototypeIconType.TRANSLATE,
                                tint = if (showInlineTranslation) palette.bgApp else palette.textMain,
                                modifier = Modifier.size(14.dp),
                                contentDescription = strings.hlTranslate
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = strings.hlTranslate,
                                color = if (showInlineTranslation) palette.bgApp else palette.textMain,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
