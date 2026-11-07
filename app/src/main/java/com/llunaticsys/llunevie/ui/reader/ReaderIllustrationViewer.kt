package com.llunaticsys.llunevie.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.llunaticsys.llunevie.LlunevieUiState
import com.llunaticsys.llunevie.data.local.BookEntity
import com.llunaticsys.llunevie.data.parser.EpubNativeParser
import com.llunaticsys.llunevie.data.parser.PdfNativeRenderer
import com.llunaticsys.llunevie.ui.components.PrototypeIconType
import com.llunaticsys.llunevie.ui.components.PrototypeSvgIcon
import com.llunaticsys.llunevie.ui.theme.LlunevieStrings
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun ReaderIllustrationViewer(
    uiState: LlunevieUiState,
    book: BookEntity,
    isPdfBook: Boolean,
    strings: LlunevieStrings,
    onOpenIllustViewer: (Int) -> Unit,
    onCloseIllustViewer: () -> Unit
) {
    val palette = LocalLluneviePalette.current

    AnimatedVisibility(
        visible = uiState.showIllustViewer && uiState.activeBookIllustrations.isNotEmpty(),
        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.95f, animationSpec = tween(200)),
        exit = fadeOut(tween(160)) + scaleOut(targetScale = 0.95f, animationSpec = tween(160)),
        modifier = Modifier.fillMaxSize()
    ) {
        val illusts = uiState.activeBookIllustrations
        val activeItem = illusts.getOrElse(uiState.activeIllustIndex) { illusts.firstOrNull() ?: return@AnimatedVisibility }
        var manualScale by remember(uiState.activeIllustIndex) { mutableFloatStateOf(1f) }
        var panOffset by remember(uiState.activeIllustIndex) { mutableStateOf(Offset.Zero) }
        val currentZoom = manualScale.coerceIn(1f, 4f)

        val pdfPath = book.localFilePath.orEmpty()
        var pdfHighResBmp by remember(activeItem.pageIndexInBook, pdfPath) {
            mutableStateOf(if (isPdfBook && pdfPath.isNotBlank()) PdfNativeRenderer.getCachedHighResIllustration(pdfPath, activeItem.pageIndexInBook) else null)
        }
        var epubHighResBmp by remember(activeItem.uriOrPath) {
            mutableStateOf(if (!isPdfBook) EpubNativeParser.getCachedFullResImage(activeItem.uriOrPath) else null)
        }

        LaunchedEffect(uiState.activeIllustIndex, isPdfBook, pdfPath, activeItem.uriOrPath) {
            withContext(Dispatchers.IO) {
                if (isPdfBook && pdfPath.isNotBlank()) {
                    val bmp = PdfNativeRenderer.renderHighResIllustrationPage(pdfPath, activeItem.pageIndexInBook, 1800)
                    withContext(Dispatchers.Main) {
                        pdfHighResBmp = bmp
                    }
                    val nextIdx = uiState.activeIllustIndex + 1
                    if (nextIdx < illusts.size) {
                        PdfNativeRenderer.renderHighResIllustrationPage(pdfPath, illusts[nextIdx].pageIndexInBook, 1800)
                    }
                    val prevIdx = uiState.activeIllustIndex - 1
                    if (prevIdx >= 0) {
                        PdfNativeRenderer.renderHighResIllustrationPage(pdfPath, illusts[prevIdx].pageIndexInBook, 1800)
                    }
                } else if (!isPdfBook) {
                    val bmp = EpubNativeParser.decodeFullResImage(activeItem.uriOrPath)
                    withContext(Dispatchers.Main) {
                        epubHighResBmp = bmp
                    }
                    val nextIdx = uiState.activeIllustIndex + 1
                    if (nextIdx < illusts.size) {
                        EpubNativeParser.decodeFullResImage(illusts[nextIdx].uriOrPath)
                    }
                    val prevIdx = uiState.activeIllustIndex - 1
                    if (prevIdx >= 0) {
                        EpubNativeParser.decodeFullResImage(illusts[prevIdx].uriOrPath)
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.bgApp)
                .clickable(enabled = false) {}
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val pageNum = activeItem.pageIndexInBook + 1
                val pageLabel = if (strings.isEn) "Page $pageNum" else "Hal $pageNum"

                Surface(
                    shape = CircleShape,
                    color = palette.bgSurface,
                    border = BorderStroke(1.dp, palette.borderSoft),
                    modifier = Modifier.height(34.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${uiState.activeIllustIndex + 1} / ${illusts.size}",
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary
                        )
                        Text(
                            text = " · ",
                            fontSize = 11.sp,
                            color = palette.textMuted
                        )
                        Text(
                            text = pageLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.textSecondary
                        )
                    }
                }

                Surface(
                    onClick = onCloseIllustViewer,
                    shape = CircleShape,
                    color = palette.bgSurface,
                    border = BorderStroke(1.dp, palette.borderSoft),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        PrototypeSvgIcon(
                            type = PrototypeIconType.CLOSE_X,
                            tint = palette.textPrimary,
                            modifier = Modifier.size(12.dp),
                            strokeWidthDp = 1.5f,
                            contentDescription = if (strings.isId) "Tutup penampil ilustrasi" else "Close illustration viewer"
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(palette.bgApp)
                    .clipToBounds()
                    .pointerInput(uiState.activeIllustIndex) {
                        detectTapGestures(
                            onDoubleTap = {
                                if (manualScale > 1.2f) {
                                    manualScale = 1f
                                    panOffset = Offset.Zero
                                } else {
                                    manualScale = 2.2f
                                    panOffset = Offset.Zero
                                }
                            }
                        )
                    }
                    .pointerInput(uiState.activeIllustIndex) {
                        detectTransformGestures { _: Offset, pan: Offset, zoom: Float, _: Float ->
                            val nextScale = (manualScale * zoom).coerceIn(1f, 4f)
                            manualScale = nextScale
                            if (nextScale > 1.02f) {
                                val maxPanX = (size.width.toFloat() * (nextScale - 1f)) / 2f
                                val maxPanY = (size.height.toFloat() * (nextScale - 1f)) / 2f
                                val nextPanX = (panOffset.x + pan.x).coerceIn(-maxPanX, maxPanX)
                                val nextPanY = (panOffset.y + pan.y).coerceIn(-maxPanY, maxPanY)
                                panOffset = Offset(nextPanX, nextPanY)
                            } else {
                                panOffset = Offset.Zero
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                        .graphicsLayer {
                            scaleX = currentZoom
                            scaleY = currentZoom
                            translationX = if (currentZoom > 1.02f) panOffset.x else 0f
                            translationY = if (currentZoom > 1.02f) panOffset.y else 0f
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = File(activeItem.uriOrPath),
                        contentDescription = activeItem.caption,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                    val displayBitmap = if (isPdfBook) pdfHighResBmp else epubHighResBmp
                    if (displayBitmap != null && !displayBitmap.isRecycled) {
                        Image(
                            bitmap = displayBitmap.asImageBitmap(),
                            contentDescription = activeItem.caption,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (activeItem.caption.isNotBlank()) {
                    Text(
                        text = activeItem.caption,
                        color = palette.textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val canGoPrev = uiState.activeIllustIndex > 0
                    val canGoNext = uiState.activeIllustIndex < illusts.size - 1

                    Surface(
                        onClick = {
                            if (canGoPrev) {
                                manualScale = 1f
                                panOffset = Offset.Zero
                                onOpenIllustViewer(uiState.activeIllustIndex - 1)
                            }
                        },
                        enabled = canGoPrev,
                        shape = CircleShape,
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .alpha(if (canGoPrev) 1.0f else 0.4f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (strings.isEn) "‹  Previous" else "‹  Sebelumnya",
                                color = palette.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            if (canGoNext) {
                                manualScale = 1f
                                panOffset = Offset.Zero
                                onOpenIllustViewer(uiState.activeIllustIndex + 1)
                            }
                        },
                        enabled = canGoNext,
                        shape = CircleShape,
                        color = palette.textPrimary,
                        border = BorderStroke(1.dp, palette.textPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .alpha(if (canGoNext) 1.0f else 0.4f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (strings.isEn) "Next  ›" else "Berikutnya  ›",
                                color = palette.bgApp,
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
