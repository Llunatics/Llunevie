package com.llunaticsys.llunevie.ui.components

import com.llunaticsys.llunevie.model.DEFAULT_TRANSLATION_LANGUAGES
import com.llunaticsys.llunevie.model.EXTRA_TRANSLATION_LANGUAGES
import com.llunaticsys.llunevie.model.normalizeTranslationLangCode
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import java.util.EnumMap
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.runtime.getValue
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import com.llunaticsys.llunevie.ui.theme.LluneviePalette
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.llunaticsys.llunevie.MainNavTab
import com.llunaticsys.llunevie.data.local.BookEntity
import com.llunaticsys.llunevie.ui.theme.LlunevieStrings
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette
import coil.compose.AsyncImage
import java.io.File

/**
 * 1:1 Vector Renderer for the official Llunevie Monogram SVG (`llunevie-icon.svg`)
 * featured in the Home header and Settings profile row in `llunevie_prototype.html`.
 */
@Composable
fun LlunevieAppLogoSvg(modifier: Modifier = Modifier) {
    val lPath1 = remember {
        PathParser().parsePathString(
            "M233 270 H420 V288 C385 291 365 295 365 320 V705 C365 730 380 745 410 745 H480 C530 745 560 715 575 652 L597 656 L578 768 H233 V750 C268 746 290 740 290 705 V320 C290 295 268 291 233 288 Z"
        ).toPath()
    }
    val lPath2 = remember {
        PathParser().parsePathString(
            "M608 276 L718 256 Q722 255 722 262 V637 L677 645 L665 768 H612 V750 C640 746 657 740 657 705 V318 C657 296 635 292 608 290 Z"
        ).toPath()
    }
    val lPath3 = remember {
        PathParser().parsePathString("M696 652 L790 645 L669 768 Z").toPath()
    }

    Canvas(modifier = modifier) {
        drawRoundRect(
            color = Color(0xFFFAF6EB),
            size = size,
            cornerRadius = CornerRadius(size.width * 0.244f, size.height * 0.244f)
        )
        scale(scaleX = size.width / 1024f, scaleY = size.height / 1024f, pivot = Offset.Zero) {
            val fg = Color(0xFF23201A)
            drawPath(path = lPath1, color = fg, style = Fill)
            drawPath(path = lPath2, color = fg, style = Fill)
            drawPath(path = lPath3, color = fg, style = Fill)
        }
    }
}

/**
 * 1:1 Custom SVG Icons matching all 44 inline `<svg viewBox="0 0 24 24">` icons in `llunevie_prototype.html`.
 */
enum class PrototypeIconType {
    NAV_HOME,
    NAV_LIBRARY,
    NAV_STATS,
    NAV_SETTINGS,
    PLUS,
    SPINE_SHELF,
    GRID_2X2,
    FULL_COVER,
    LIST_BULLETS,
    SEARCH,
    SORT_DESC,
    FILTER_FUNNEL,
    EDIT_PEN,
    TRASH_CAN,
    ARROW_LEFT,
    CHEVRON_RIGHT,
    CHEVRON_DOWN,
    CHEVRON_UP,
    CLOSE_X,
    CHECK,
    BOOKMARK_OUTLINE,
    BOOKMARK_FILLED,
    EXPAND_FULL,
    COLLAPSE_FULL,
    TOC_LIST,
    TORCH_BOLT,
    DOWNLOAD_TRAY,
    UPLOAD_TRAY,
    TRANSLATE
}

private val parsedSvgPathCache = ConcurrentHashMap<String, Path>()
private val cachedIconPathsByType = EnumMap<PrototypeIconType, List<Path>>(PrototypeIconType::class.java)

private fun getCachedSvgPath(pathData: String): Path {
    return parsedSvgPathCache.getOrPut(pathData) {
        PathParser().parsePathString(pathData).toPath()
    }
}

private fun getCachedPathsForIcon(type: PrototypeIconType): List<Path> {
    synchronized(cachedIconPathsByType) {
        cachedIconPathsByType[type]?.let { return it }
        val built = when (type) {
            PrototypeIconType.NAV_HOME -> listOf(
                getCachedSvgPath("M3 10.5L12 3l9 7.5v9a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 19.5v-9z"),
                getCachedSvgPath("M9.5 21v-6h5v6")
            )
            PrototypeIconType.NAV_LIBRARY -> listOf(
                getCachedSvgPath("M2 4.5C2 3.67 2.67 3 3.5 3H8.5C10.43 3 12 4.57 12 6.5V20C12 18.62 10.88 17.5 9.5 17.5H3.5C2.67 17.5 2 16.83 2 16V4.5Z"),
                getCachedSvgPath("M22 4.5C22 3.67 21.33 3 20.5 3H15.5C13.57 3 12 4.57 12 6.5V20C12 18.62 13.12 17.5 14.5 17.5H20.5C21.33 17.5 22 16.83 22 16V4.5Z")
            )
            PrototypeIconType.SPINE_SHELF -> listOf(
                getCachedSvgPath("M15.5 5.2l3.3 0.9-2.2 13-3.3-0.9z")
            )
            PrototypeIconType.FULL_COVER -> listOf(
                getCachedSvgPath("M5 16l4-3.5 3 2.5 4-4 3 3")
            )
            PrototypeIconType.SORT_DESC -> listOf(
                getCachedSvgPath("M4 7h10M4 12h7M4 17h4M17 6v12m0 0l-3-3m3 3l3-3")
            )
            PrototypeIconType.FILTER_FUNNEL -> listOf(
                getCachedSvgPath("M22 3L2 3L10 12.46L10 19L14 21L14 12.46Z")
            )
            PrototypeIconType.EDIT_PEN -> listOf(
                getCachedSvgPath("M12 20h9"),
                getCachedSvgPath("M16.5 3.5a2.121 2.121 0 0 1 3 3L7 19l-4 1 1-4L16.5 3.5z")
            )
            PrototypeIconType.TRASH_CAN -> listOf(
                getCachedSvgPath("M3 6h18"),
                getCachedSvgPath("M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2")
            )
            PrototypeIconType.ARROW_LEFT -> listOf(
                getCachedSvgPath("M19 12H5M12 19l-7-7 7-7")
            )
            PrototypeIconType.CHEVRON_RIGHT -> listOf(
                getCachedSvgPath("M9 18l6-6-6-6")
            )
            PrototypeIconType.CHEVRON_DOWN -> listOf(
                getCachedSvgPath("M6 9l6 6 6-6")
            )
            PrototypeIconType.CHEVRON_UP -> listOf(
                getCachedSvgPath("M18 15l-6-6-6 6")
            )
            PrototypeIconType.CHECK -> listOf(
                getCachedSvgPath("M20 6L9 17l-5-5")
            )
            PrototypeIconType.BOOKMARK_OUTLINE, PrototypeIconType.BOOKMARK_FILLED -> listOf(
                getCachedSvgPath("M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z")
            )
            PrototypeIconType.EXPAND_FULL -> listOf(
                getCachedSvgPath("M15 3h6v6M9 21H3v-6M21 3l-7 7M3 21l7-7")
            )
            PrototypeIconType.COLLAPSE_FULL -> listOf(
                getCachedSvgPath("M4 14h6v6M20 10h-6V4M14 10l7-7M3 21l7-7")
            )
            PrototypeIconType.TORCH_BOLT -> listOf(
                getCachedSvgPath("M13 2L3 14h9l-1 8 10-12h-9l1-8z")
            )
            PrototypeIconType.DOWNLOAD_TRAY -> listOf(
                getCachedSvgPath("M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"),
                getCachedSvgPath("M7 10l5 5 5-5")
            )
            PrototypeIconType.UPLOAD_TRAY -> listOf(
                getCachedSvgPath("M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"),
                getCachedSvgPath("M17 8l-5-5-5 5")
            )
            PrototypeIconType.TRANSLATE -> listOf(
                getCachedSvgPath("m5 8 6 6"),
                getCachedSvgPath("m4 14 6-6 2-3"),
                getCachedSvgPath("M2 5h12"),
                getCachedSvgPath("M7 2h1v3"),
                getCachedSvgPath("m22 22-5-10-5 10"),
                getCachedSvgPath("M14 18h6")
            )
            else -> emptyList()
        }
        cachedIconPathsByType[type] = built
        return built
    }
}

fun defaultPrototypeIconDescription(type: PrototypeIconType): String = when (type) {
    PrototypeIconType.NAV_HOME -> "Home"
    PrototypeIconType.NAV_LIBRARY -> "Library"
    PrototypeIconType.NAV_STATS -> "Statistics"
    PrototypeIconType.NAV_SETTINGS -> "Settings"
    PrototypeIconType.PLUS -> "Add"
    PrototypeIconType.SPINE_SHELF -> "Spine view"
    PrototypeIconType.GRID_2X2 -> "Grid view"
    PrototypeIconType.FULL_COVER -> "Cover view"
    PrototypeIconType.LIST_BULLETS -> "List view"
    PrototypeIconType.SEARCH -> "Search"
    PrototypeIconType.SORT_DESC -> "Sort"
    PrototypeIconType.FILTER_FUNNEL -> "Filter"
    PrototypeIconType.EDIT_PEN -> "Edit"
    PrototypeIconType.TRASH_CAN -> "Delete"
    PrototypeIconType.ARROW_LEFT -> "Back"
    PrototypeIconType.CHEVRON_RIGHT -> "Next"
    PrototypeIconType.CHEVRON_DOWN -> "Expand"
    PrototypeIconType.CHEVRON_UP -> "Collapse"
    PrototypeIconType.CLOSE_X -> "Close"
    PrototypeIconType.CHECK -> "Selected"
    PrototypeIconType.BOOKMARK_OUTLINE -> "Add bookmark"
    PrototypeIconType.BOOKMARK_FILLED -> "Bookmarked"
    PrototypeIconType.EXPAND_FULL -> "Full screen"
    PrototypeIconType.COLLAPSE_FULL -> "Exit full screen"
    PrototypeIconType.TOC_LIST -> "Contents"
    PrototypeIconType.TORCH_BOLT -> "Toggle flashlight"
    PrototypeIconType.DOWNLOAD_TRAY -> "Export"
    PrototypeIconType.UPLOAD_TRAY -> "Import"
    PrototypeIconType.TRANSLATE -> "Translate"
}

@Composable
fun PrototypeSvgIcon(
    type: PrototypeIconType,
    tint: Color,
    modifier: Modifier = Modifier.size(18.dp),
    strokeWidthDp: Float = 1.6f,
    badgeFillColor: Color = Color.Transparent,
    contentDescription: String? = defaultPrototypeIconDescription(type),
    onClick: (() -> Unit)? = null
) {
    val paths = remember(type) { getCachedPathsForIcon(type) }
    val semanticsModifier = if (!contentDescription.isNullOrBlank()) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            this.role = if (onClick != null) Role.Button else Role.Image
        }
    } else {
        Modifier
    }
    val clickModifier = if (onClick != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick)
    } else {
        Modifier
    }

    Canvas(modifier = clickModifier.then(modifier).then(semanticsModifier)) {
        val sx = size.width / 24f
        val sy = size.height / 24f
        val stroke = Stroke(
            width = strokeWidthDp,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        scale(scaleX = sx, scaleY = sy, pivot = Offset.Zero) {
            when (type) {
                PrototypeIconType.NAV_HOME -> {
                    paths.forEach { drawPath(it, tint, style = stroke) }
                }
                PrototypeIconType.NAV_LIBRARY -> {
                    paths.forEach { drawPath(it, tint, style = stroke) }
                }
                PrototypeIconType.NAV_STATS -> {
                    drawLine(tint, Offset(7f, 17f), Offset(7f, 11f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(tint, Offset(12f, 17f), Offset(12f, 6f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(tint, Offset(17f, 17f), Offset(17f, 9f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(tint, Offset(4f, 19.5f), Offset(20f, 19.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
                PrototypeIconType.NAV_SETTINGS -> {
                    drawLine(tint, Offset(4f, 8.5f), Offset(20f, 8.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(tint, Offset(4f, 15.5f), Offset(20f, 15.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    if (badgeFillColor != Color.Transparent) {
                        drawCircle(badgeFillColor, radius = 2.2f, center = Offset(9f, 8.5f))
                        drawCircle(badgeFillColor, radius = 2.2f, center = Offset(15f, 15.5f))
                    }
                    drawCircle(tint, radius = 2.2f, center = Offset(9f, 8.5f), style = stroke)
                    drawCircle(tint, radius = 2.2f, center = Offset(15f, 15.5f), style = stroke)
                }
                PrototypeIconType.PLUS -> {
                    drawLine(tint, Offset(12f, 6.5f), Offset(12f, 17.5f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(tint, Offset(6.5f, 12f), Offset(17.5f, 12f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
                PrototypeIconType.SPINE_SHELF -> {
                    drawRoundRect(tint, Offset(4f, 5f), Size(3.5f, 14f), CornerRadius(1f, 1f), style = stroke)
                    drawRoundRect(tint, Offset(9.5f, 5f), Size(3.5f, 14f), CornerRadius(1f, 1f), style = stroke)
                    paths.firstOrNull()?.let { drawPath(it, tint, style = stroke) }
                }
                PrototypeIconType.GRID_2X2 -> {
                    drawRoundRect(tint, Offset(4f, 4f), Size(6.5f, 6.5f), CornerRadius(1.5f, 1.5f), style = stroke)
                    drawRoundRect(tint, Offset(13.5f, 4f), Size(6.5f, 6.5f), CornerRadius(1.5f, 1.5f), style = stroke)
                    drawRoundRect(tint, Offset(4f, 13.5f), Size(6.5f, 6.5f), CornerRadius(1.5f, 1.5f), style = stroke)
                    drawRoundRect(tint, Offset(13.5f, 13.5f), Size(6.5f, 6.5f), CornerRadius(1.5f, 1.5f), style = stroke)
                }
                PrototypeIconType.FULL_COVER -> {
                    drawRoundRect(tint, Offset(5f, 3.5f), Size(14f, 17f), CornerRadius(2f, 2f), style = stroke)
                    drawCircle(tint, radius = 1.5f, center = Offset(9.5f, 8.5f), style = stroke)
                    paths.firstOrNull()?.let { drawPath(it, tint, style = stroke) }
                }
                PrototypeIconType.LIST_BULLETS -> {
                    drawCircle(tint, radius = 1.4f, center = Offset(6f, 7f), style = stroke)
                    drawCircle(tint, radius = 1.4f, center = Offset(6f, 12f), style = stroke)
                    drawCircle(tint, radius = 1.4f, center = Offset(6f, 17f), style = stroke)
                    drawLine(tint, Offset(11f, 7f), Offset(19f, 7f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(tint, Offset(11f, 12f), Offset(19f, 12f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(tint, Offset(11f, 17f), Offset(17f, 17f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
                PrototypeIconType.SEARCH -> {
                    drawCircle(tint, radius = 8f, center = Offset(11f, 11f), style = stroke)
                    drawLine(tint, Offset(21f, 21f), Offset(16.65f, 16.65f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
                PrototypeIconType.SORT_DESC,
                PrototypeIconType.FILTER_FUNNEL,
                PrototypeIconType.EDIT_PEN,
                PrototypeIconType.ARROW_LEFT,
                PrototypeIconType.CHEVRON_RIGHT,
                PrototypeIconType.CHEVRON_DOWN,
                PrototypeIconType.CHEVRON_UP,
                PrototypeIconType.CHECK,
                PrototypeIconType.BOOKMARK_OUTLINE,
                PrototypeIconType.EXPAND_FULL,
                PrototypeIconType.COLLAPSE_FULL,
                PrototypeIconType.TORCH_BOLT,
                PrototypeIconType.TRANSLATE -> {
                    paths.forEach { drawPath(it, tint, style = stroke) }
                }
                PrototypeIconType.TRASH_CAN -> {
                    paths.forEach { drawPath(it, tint, style = stroke) }
                    drawLine(tint, Offset(10f, 11f), Offset(10f, 17f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(tint, Offset(14f, 11f), Offset(14f, 17f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
                PrototypeIconType.CLOSE_X -> {
                    drawLine(tint, Offset(18f, 6f), Offset(6f, 18f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(tint, Offset(6f, 6f), Offset(18f, 18f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
                PrototypeIconType.BOOKMARK_FILLED -> {
                    paths.firstOrNull()?.let { bm ->
                        drawPath(bm, tint, style = Fill)
                        drawPath(bm, tint, style = stroke)
                    }
                }
                PrototypeIconType.TOC_LIST -> {
                    drawLine(tint, Offset(8f, 6f), Offset(21f, 6f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(tint, Offset(8f, 12f), Offset(21f, 12f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawLine(tint, Offset(8f, 18f), Offset(21f, 18f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                    drawCircle(tint, radius = 1.2f, center = Offset(3.5f, 6f), style = Fill)
                    drawCircle(tint, radius = 1.2f, center = Offset(3.5f, 12f), style = Fill)
                    drawCircle(tint, radius = 1.2f, center = Offset(3.5f, 18f), style = Fill)
                }
                PrototypeIconType.DOWNLOAD_TRAY -> {
                    paths.forEach { drawPath(it, tint, style = stroke) }
                    drawLine(tint, Offset(12f, 15f), Offset(12f, 3f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
                PrototypeIconType.UPLOAD_TRAY -> {
                    paths.forEach { drawPath(it, tint, style = stroke) }
                    drawLine(tint, Offset(12f, 3f), Offset(12f, 15f), strokeWidth = stroke.width, cap = StrokeCap.Round)
                }
            }
        }
    }
}

fun targetLanguageDisplayName(code: String, isEnglish: Boolean = false): String {
    val norm = normalizeTranslationLangCode(code)
    val option = (DEFAULT_TRANSLATION_LANGUAGES + EXTRA_TRANSLATION_LANGUAGES).find { normalizeTranslationLangCode(it.code) == norm }
    if (option != null) {
        if (norm == "id" && isEnglish) return "Indonesian"
        return option.nativeName
    }
    return code.uppercase()
}

fun targetLanguageBadgeLabel(code: String): String {
    val norm = normalizeTranslationLangCode(code)
    val option = (DEFAULT_TRANSLATION_LANGUAGES + EXTRA_TRANSLATION_LANGUAGES).find { normalizeTranslationLangCode(it.code) == norm }
    return option?.badgeCode ?: norm.uppercase().take(2)
}

@Composable
fun ModernSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLluneviePalette.current
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "switchThumb"
    )
    val trackColor by animateColorAsState(
        targetValue = if (checked) palette.textMain else palette.bgCard,
        animationSpec = tween(180),
        label = "switchTrack"
    )
    val thumbColor = if (checked) palette.bgApp else Color.White

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .semantics { role = Role.Switch }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(46.dp)
                .height(26.dp)
                .clip(CircleShape)
                .background(trackColor)
                .border(1.dp, palette.borderSoft, CircleShape)
                .padding(2.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(20.dp)
                    .shadow(2.dp, CircleShape)
                    .clip(CircleShape)
                    .background(thumbColor)
            )
        }
    }
}

@Composable
fun <T> CustomThemeDropdown(
    title: String,
    selectedValue: T,
    options: List<Pair<T, String>>,
    onValueSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 999.dp
) {
    val palette = LocalLluneviePalette.current
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.find { it.first == selectedValue }?.second ?: options.firstOrNull()?.second.orEmpty()
    val shape = RoundedCornerShape(cornerRadius)

    Surface(
        modifier = modifier
            .clip(shape)
            .clickable { expanded = true },
        shape = shape,
        color = palette.bgSurface,
        border = BorderStroke(1.dp, palette.borderSoft)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = currentLabel,
                color = palette.textMain,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(6.dp))
            PrototypeSvgIcon(
                type = PrototypeIconType.CHEVRON_DOWN,
                tint = palette.textMuted.copy(alpha = 0.75f),
                modifier = Modifier.size(14.dp)
            )
        }
    }

    if (expanded) {
        Dialog(onDismissRequest = { expanded = false }) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = palette.sheetBg,
                border = BorderStroke(1.dp, palette.borderSoft),
                shadowElevation = 20.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = title.uppercase(),
                            color = palette.textMuted,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.3.sp
                        )
                        Box(
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .size(24.dp)
                                .clip(CircleShape)
                                .semantics {
                                    contentDescription = "Close"
                                    role = Role.Button
                                }
                                .clickable { expanded = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = palette.textMuted, fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    options.forEach { (value, label) ->
                        val isSelected = value == selectedValue
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) palette.pillActiveBg else Color.Transparent,
                            border = if (isSelected) BorderStroke(1.dp, palette.pillActiveBorder) else null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .clickable {
                                    expanded = false
                                    onValueSelected(value)
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = label,
                                    color = palette.textMain,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium
                                )
                                if (isSelected) {
                                    PrototypeSvgIcon(
                                        type = PrototypeIconType.CHECK,
                                        tint = palette.textMain,
                                        modifier = Modifier.size(16.dp)
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

/**
 * Renders a book cover with novel aspect ratio (2:3) matching `renderBookCoverHTML` in `llunevie_prototype.html`.
 * Supports 3 distinct cover styles: "rounded", "flat_crease", and "book_3d".
 * Supports an optional minimal bottom loading bar (`importProgress`) while a local PDF/EPUB is importing.
 */
@Composable
fun BookCoverCard(
    book: BookEntity,
    showTitleInsideFallback: Boolean = false,
    importProgress: Int? = null,
    coverStyle: String = "rounded",
    cornerRadius: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    val palette = LocalLluneviePalette.current
    val context = LocalContext.current
    val bgColor = Color(book.spineColorHex)
    val textColor = Color(book.spineTextColorHex)
    val initials = remember(book.title) {
        book.title.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(2)
            .joinToString("") { it.take(1).uppercase() }
            .ifBlank { "L" }
    }

    val imageRequest = remember(book.coverImagePath) {
        val path = book.coverImagePath
        if (path.isNullOrBlank()) null
        else {
            val dataModel: Any = if (path.startsWith("http", ignoreCase = true) || path.startsWith("content://", ignoreCase = true)) {
                path
            } else {
                File(path)
            }
            coil.request.ImageRequest.Builder(context)
                .data(dataModel)
                .size(420, 630)
                .precision(coil.size.Precision.INEXACT)
                .memoryCacheKey("cover_thumb_$path")
                .diskCacheKey("cover_thumb_$path")
                .crossfade(false)
                .build()
        }
    }

    Column(modifier = modifier) {
        when (coverStyle) {
            "flat_crease" -> {
                // MOON+ READER 2D PAPERBACK STYLE: Crisp 0.dp RectangleShape + Natural Subtle Left Spine Crease
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .shadow(5.dp, androidx.compose.ui.graphics.RectangleShape, clip = false)
                        .clip(androidx.compose.ui.graphics.RectangleShape)
                        .background(bgColor)
                        .drawWithContent {
                            drawContent()
                            val w = size.width
                            val h = size.height
                            val creaseW = (w * 0.088f).coerceIn(8.dp.toPx(), 18.dp.toPx())

                            // Single continuous multi-stop photographic paperback crease gradient
                            val creaseBrush = Brush.horizontalGradient(
                                0.00f to Color.Black.copy(alpha = 0.26f),
                                0.16f to Color.Black.copy(alpha = 0.05f),
                                0.34f to Color.White.copy(alpha = 0.16f),
                                0.50f to Color.White.copy(alpha = 0.02f),
                                0.68f to Color.Black.copy(alpha = 0.13f),
                                0.84f to Color.White.copy(alpha = 0.05f),
                                1.00f to Color.Transparent,
                                startX = 0f,
                                endX = creaseW
                            )
                            drawRect(
                                brush = creaseBrush,
                                topLeft = Offset.Zero,
                                size = androidx.compose.ui.geometry.Size(creaseW, h)
                            )

                            // Ultra-fine 0.5dp natural paper rim around sharp edges
                            drawRect(
                                color = Color.White.copy(alpha = 0.09f),
                                topLeft = Offset.Zero,
                                size = size,
                                style = Stroke(width = 0.5.dp.toPx())
                            )
                        }
                ) {
                    BookCoverInnerContent(
                        book = book,
                        bgColor = bgColor,
                        textColor = textColor,
                        initials = initials,
                        showTitleInsideFallback = showTitleInsideFallback,
                        imageRequest = imageRequest,
                        contentScale = ContentScale.Crop
                    )
                }
            }

            "book_3d" -> {
                // PHOTOREALISTIC 3D STANDING BOOK WITH PERSPECTIVE & PAPER PAGE BLOCK (Vagabond Reference 1:1)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .drawWithCache {
                            val totalW = size.width
                            val totalH = size.height
                            val pageDepth = (totalW * 0.095f).coerceIn(7.dp.toPx(), 16.dp.toPx())
                            val bottomShadowSpace = (totalH * 0.032f).coerceIn(4.dp.toPx(), 9.dp.toPx())
                            val coverW = totalW - pageDepth
                            val coverH = totalH - bottomShadowSpace
                            val topTilt = (coverH * 0.032f).coerceIn(3.dp.toPx(), 8.dp.toPx())
                            val bottomTilt = (coverH * 0.038f).coerceIn(3.5.dp.toPx(), 9.dp.toPx())

                            val pageTopRightY = topTilt * 0.85f
                            val pageBottomRightY = coverH - bottomTilt * 0.78f
                            val pageRightX = totalW - 1.dp.toPx()

                            val pageBlockPath = androidx.compose.ui.graphics.Path().apply {
                                moveTo(coverW - 0.5f, 0.5.dp.toPx())
                                lineTo(pageRightX, pageTopRightY)
                                lineTo(pageRightX, pageBottomRightY)
                                lineTo(coverW - 0.5f, coverH - 0.5.dp.toPx())
                                close()
                            }

                            val backCoverPath = androidx.compose.ui.graphics.Path().apply {
                                val rimW = 1.1.dp.toPx()
                                moveTo(pageRightX - rimW, pageTopRightY - 0.5.dp.toPx())
                                lineTo(totalW, pageTopRightY)
                                lineTo(totalW, pageBottomRightY)
                                lineTo(pageRightX - rimW, pageBottomRightY + 0.5.dp.toPx())
                                close()
                            }

                            val paperBaseBrush = Brush.horizontalGradient(
                                0.00f to Color(0xFFD8CFC0),
                                0.22f to Color(0xFFF5F0E6),
                                0.65f to Color(0xFFE6DECF),
                                0.92f to Color(0xFFCBC0B0),
                                1.00f to Color(0xFFB5A998),
                                startX = coverW,
                                endX = pageRightX
                            )

                            val overhangShadowBrush = Brush.horizontalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.22f),
                                    Color.Black.copy(alpha = 0.05f),
                                    Color.Transparent
                                ),
                                startX = coverW,
                                endX = coverW + pageDepth * 0.35f
                            )

                            onDrawBehind {
                                // 1. Soft Ground Cast Shadow under bottom-right corner & page block
                                val shadowLeft = coverW * 0.12f
                                val shadowTop = coverH - bottomTilt * 1.1f
                                drawOval(
                                    brush = Brush.radialGradient(
                                        0.0f to Color.Black.copy(alpha = 0.36f),
                                        0.55f to Color.Black.copy(alpha = 0.16f),
                                        1.0f to Color.Transparent,
                                        center = Offset(coverW * 0.88f, coverH - 1.dp.toPx()),
                                        radius = (totalW - shadowLeft) * 0.62f
                                    ),
                                    topLeft = Offset(shadowLeft, shadowTop),
                                    size = androidx.compose.ui.geometry.Size(totalW - shadowLeft, totalH - shadowTop)
                                )

                                // 2. Back cover board subtle rim behind paper block
                                val darkenedSpine = Color(
                                    red = (bgColor.red * 0.65f).coerceIn(0f, 1f),
                                    green = (bgColor.green * 0.65f).coerceIn(0f, 1f),
                                    blue = (bgColor.blue * 0.65f).coerceIn(0f, 1f),
                                    alpha = 1f
                                )
                                drawPath(path = backCoverPath, color = darkenedSpine)

                                // 3. 3D Paper Page Block base fill
                                drawPath(path = pageBlockPath, brush = paperBaseBrush)

                                // 4. 10 Ultra-fine paper sheet micro-striation lines following perspective slope
                                val sheetLines = 10
                                for (i in 1..sheetLines) {
                                    val t = i.toFloat() / (sheetLines + 1).toFloat()
                                    val x = coverW + (pageRightX - coverW) * t
                                    val topY = 0.8.dp.toPx() + (pageTopRightY - 0.8.dp.toPx()) * t
                                    val botY = (coverH - 0.8.dp.toPx()) + (pageBottomRightY - (coverH - 0.8.dp.toPx())) * t
                                    val lineColor = if (i % 2 == 0) {
                                        Color(0xFF786E60).copy(alpha = 0.13f)
                                    } else {
                                        Color.White.copy(alpha = 0.18f)
                                    }
                                    drawLine(
                                        color = lineColor,
                                        start = Offset(x, topY),
                                        end = Offset(x, botY),
                                        strokeWidth = 0.5.dp.toPx()
                                    )
                                }

                                // 5. Front cover overhang contact shadow onto the paper block
                                drawPath(path = pageBlockPath, brush = overhangShadowBrush)
                            }
                        }
                ) {
                    // Front Cover Face warped via Matrix.setPolyToPoly so 0% of cover artwork is cropped!
                    val perspectiveMatrix = remember { android.graphics.Matrix() }
                    val srcPts = remember { FloatArray(8) }
                    val dstPts = remember { FloatArray(8) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawWithContent {
                                val totalW = size.width
                                val totalH = size.height
                                val pageDepth = (totalW * 0.095f).coerceIn(7.dp.toPx(), 16.dp.toPx())
                                val bottomShadowSpace = (totalH * 0.032f).coerceIn(4.dp.toPx(), 9.dp.toPx())
                                val coverW = totalW - pageDepth
                                val coverH = totalH - bottomShadowSpace
                                val topTilt = (coverH * 0.032f).coerceIn(3.dp.toPx(), 8.dp.toPx())
                                val bottomTilt = (coverH * 0.038f).coerceIn(3.5.dp.toPx(), 9.dp.toPx())

                                // Map full (0..totalW, 0..totalH) child content onto the 3D perspective front cover quad!
                                srcPts[0] = 0f;     srcPts[1] = 0f
                                srcPts[2] = totalW; srcPts[3] = 0f
                                srcPts[4] = totalW; srcPts[5] = totalH
                                srcPts[6] = 0f;     srcPts[7] = totalH

                                dstPts[0] = 0f;     dstPts[1] = topTilt
                                dstPts[2] = coverW; dstPts[3] = 0f
                                dstPts[4] = coverW; dstPts[5] = coverH
                                dstPts[6] = 0f;     dstPts[7] = coverH - bottomTilt

                                perspectiveMatrix.reset()
                                perspectiveMatrix.setPolyToPoly(srcPts, 0, dstPts, 0, 4)

                                val nativeCanvas = drawContext.canvas.nativeCanvas
                                val saveCount = nativeCanvas.save()
                                nativeCanvas.concat(perspectiveMatrix)

                                // Draw 100% uncropped cover content inside the perspective quad
                                drawContent()

                                // Subtle natural left spine hinge lighting on the 3D cover
                                val creaseW = (totalW * 0.075f).coerceIn(6.dp.toPx(), 15.dp.toPx())
                                val creaseBrush = Brush.horizontalGradient(
                                    0.00f to Color.Black.copy(alpha = 0.24f),
                                    0.22f to Color.White.copy(alpha = 0.15f),
                                    0.58f to Color.Black.copy(alpha = 0.11f),
                                    1.00f to Color.Transparent,
                                    startX = 0f,
                                    endX = creaseW
                                )
                                drawRect(
                                    brush = creaseBrush,
                                    topLeft = Offset.Zero,
                                    size = androidx.compose.ui.geometry.Size(creaseW, totalH)
                                )

                                // Crisp front-cover right fore-edge paper-board rim highlight
                                drawLine(
                                    color = Color(0xFFF5F0E6).copy(alpha = 0.45f),
                                    start = Offset(totalW - 0.6.dp.toPx(), 0f),
                                    end = Offset(totalW - 0.6.dp.toPx(), totalH),
                                    strokeWidth = 0.8.dp.toPx()
                                )

                                nativeCanvas.restoreToCount(saveCount)
                            }
                    ) {
                        BookCoverInnerContent(
                            book = book,
                            bgColor = bgColor,
                            textColor = textColor,
                            initials = initials,
                            showTitleInsideFallback = showTitleInsideFallback,
                            imageRequest = imageRequest,
                            contentScale = ContentScale.FillBounds
                        )
                    }
                }
            }

            else -> {
                // DEFAULT "rounded" STYLE
                val shape = RoundedCornerShape(cornerRadius)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .shadow(6.dp, shape)
                        .clip(shape)
                        .background(bgColor)
                        .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
                ) {
                    BookCoverInnerContent(
                        book = book,
                        bgColor = bgColor,
                        textColor = textColor,
                        initials = initials,
                        showTitleInsideFallback = showTitleInsideFallback,
                        imageRequest = imageRequest,
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        if (importProgress != null && importProgress in 1..99) {
            Spacer(modifier = Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { (importProgress / 100f).coerceIn(0.05f, 1f) },
                color = palette.textMain,
                trackColor = palette.borderSoft,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
            )
        }
    }
}

@Composable
private fun BoxScope.BookCoverInnerContent(
    book: BookEntity,
    bgColor: Color,
    textColor: Color,
    initials: String,
    showTitleInsideFallback: Boolean,
    imageRequest: coil.request.ImageRequest?,
    contentScale: ContentScale
) {
    if (showTitleInsideFallback) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp)
                .border(1.dp, textColor.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "✦", color = textColor.copy(alpha = 0.6f), fontSize = 9.sp)
            Text(
                text = book.title,
                color = textColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = "✦", color = textColor.copy(alpha = 0.6f), fontSize = 9.sp)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(10.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = book.category.uppercase(),
                color = textColor.copy(alpha = 0.7f),
                fontSize = 7.5.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .border(1.dp, textColor.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    color = textColor,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = book.year,
                color = textColor.copy(alpha = 0.7f),
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    if (imageRequest != null) {
        AsyncImage(
            model = imageRequest,
            contentDescription = book.title,
            contentScale = contentScale,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Dual Selector Architecture: Classic Design vs. Glass Design
 * Classic: solid dark fill for active, clean surface with 1.dp border for inactive
 * Glass: frosted glass track (38.dp, CircleShape, uniform 3.dp padding) and symmetrical active pill (32.dp, specular border)
 */
@Composable
fun <T> ClassicGlassSegmentedControl(
    items: List<Pair<T, String>>,
    selectedItem: T,
    onItemSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 36.dp
) {
    val palette = LocalLluneviePalette.current
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = palette.bgCard,
        border = BorderStroke(1.dp, palette.borderSoft),
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.forEach { (item, label) ->
                val isSelected = item == selectedItem
                val targetBg = if (isSelected) palette.accentDark else Color.Transparent
                val animatedBg by animateColorAsState(targetValue = targetBg, animationSpec = tween(160), label = "segmentBg")
                val targetText = if (isSelected) palette.bgApp else palette.textMuted
                val animatedText by animateColorAsState(targetValue = targetText, animationSpec = tween(160), label = "segmentText")
                Surface(
                    onClick = { onItemSelected(item) },
                    shape = RoundedCornerShape(8.dp),
                    color = animatedBg,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            color = animatedText,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

fun Modifier.llunevieSurface(
    palette: LluneviePalette,
    isGlass: Boolean = palette.isGlass,
    shape: Shape = RoundedCornerShape(16.dp),
    shadowElevation: androidx.compose.ui.unit.Dp = if (isGlass) 10.dp else 4.dp
): Modifier {
    return if (isGlass) {
        val glassBg = if (palette.isDark) Color(0xFF1E2128).copy(alpha = 0.68f) else palette.bgSurface
        val glassBorderBrush = if (palette.isDark) {
            Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f)),
                start = Offset.Zero,
                end = Offset.Infinite
            )
        } else {
            Brush.linearGradient(
                colors = listOf(palette.textMain.copy(alpha = 0.12f), palette.textMain.copy(alpha = 0.04f)),
                start = Offset.Zero,
                end = Offset.Infinite
            )
        }
        this
            .shadow(shadowElevation, shape)
            .clip(shape)
            .background(glassBg)
            .border(BorderStroke(1.dp, glassBorderBrush), shape)
    } else {
        this
            .shadow(shadowElevation, shape)
            .clip(shape)
            .background(palette.bgCard)
            .border(BorderStroke(1.dp, palette.borderSoft), shape)
    }
}

@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    shadowElevation: androidx.compose.ui.unit.Dp = 8.dp,
    content: @Composable () -> Unit
) {
    val palette = LocalLluneviePalette.current
    val isGlass = palette.isGlass
    val glassBg = if (isGlass) {
        if (palette.isDark) Color(0xFF1E2128).copy(alpha = 0.68f) else palette.bgSurface
    } else {
        palette.bgCard
    }
    val borderStroke = if (isGlass) {
        val brush = if (palette.isDark) {
            Brush.linearGradient(
                colors = listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.04f)),
                start = Offset.Zero,
                end = Offset.Infinite
            )
        } else {
            Brush.linearGradient(
                colors = listOf(palette.textMain.copy(alpha = 0.12f), palette.textMain.copy(alpha = 0.04f)),
                start = Offset.Zero,
                end = Offset.Infinite
            )
        }
        BorderStroke(1.dp, brush)
    } else {
        BorderStroke(1.dp, palette.borderSoft)
    }

    Surface(
        shape = shape,
        color = glassBg,
        border = borderStroke,
        shadowElevation = if (isGlass) (shadowElevation * 1.4f) else shadowElevation,
        modifier = modifier
    ) {
        content()
    }
}

/**
 * 1:1 Bottom Floating Navigation Dock (`#bottomNavContainer` in `llunevie_prototype.html`)
 * - Respects `.navigationBarsPadding()` so it never collides with Android system navigation bar
 * - 62.dp capsule height, 6.dp inner padding, sliding active pill with 20.dp icon and 13.5.sp label
 * - Asymmetric `31/31/31/14dp` `.llunevie-starry-fab` (62x62.dp) with low-opacity ✦ sparkles
 */
@Composable
fun BottomFloatingNavDock(
    activeTab: MainNavTab,
    strings: LlunevieStrings,
    onSelectTab: (MainNavTab) -> Unit,
    onOpenAddBook: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLluneviePalette.current
    val items = listOf(
        Triple(MainNavTab.HOME, strings.navHome, PrototypeIconType.NAV_HOME),
        Triple(MainNavTab.LIBRARY, strings.navLibrary, PrototypeIconType.NAV_LIBRARY),
        Triple(MainNavTab.STATS, strings.navStats, PrototypeIconType.NAV_STATS),
        Triple(MainNavTab.SETTINGS, strings.navSettings, PrototypeIconType.NAV_SETTINGS)
    )

    val dockBg = if (palette.isGlass) {
        if (palette.isDark) Color(0xFF1C1F26).copy(alpha = 0.88f)
        else palette.dockBg
    } else {
        palette.dockBg
    }
    val dockBorder = if (palette.isGlass) {
        if (palette.isDark) Color.White.copy(alpha = 0.14f)
        else palette.dockBorder
    } else {
        palette.dockBorder
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 12.dp, top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(62.dp),
            shape = CircleShape,
            color = dockBg,
            border = BorderStroke(1.dp, dockBorder),
            shadowElevation = if (palette.isGlass) 12.dp else 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { (tab, label, iconType) ->
                    val isSelected = activeTab == tab
                    val activePillBg = if (palette.isGlass) {
                        if (palette.isDark) Color.White.copy(alpha = 0.14f) else palette.pillActiveBg
                    } else {
                        palette.pillActiveBg
                    }
                    val activePillBorder = if (palette.isGlass) {
                        if (palette.isDark) Color.White.copy(alpha = 0.18f) else palette.pillActiveBorder
                    } else {
                        palette.pillActiveBorder
                    }

                    val iconScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.08f else 1f,
                        animationSpec = spring(dampingRatio = 0.6f),
                        label = "iconBounce"
                    )

                    val targetBtnBg = if (isSelected) activePillBg else Color.Transparent
                    val animatedBtnBg by animateColorAsState(
                        targetValue = targetBtnBg,
                        animationSpec = tween(180),
                        label = "dockBtnBg"
                    )
                    val tabScale by animateFloatAsState(
                        targetValue = if (isSelected) 1.0f else 0.95f,
                        animationSpec = spring(dampingRatio = 0.75f, stiffness = 500f),
                        label = "dockTabScale"
                    )

                    Surface(
                        shape = RoundedCornerShape(25.dp),
                        color = animatedBtnBg,
                        border = if (isSelected) BorderStroke(1.dp, activePillBorder) else null,
                        shadowElevation = if (isSelected) 1.5.dp else 0.dp,
                        modifier = Modifier
                            .fillMaxHeight()
                            .scale(tabScale)
                            .clip(RoundedCornerShape(25.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { onSelectTab(tab) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(horizontal = if (isSelected) 16.dp else 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            PrototypeSvgIcon(
                                type = iconType,
                                tint = if (isSelected) palette.textMain else palette.textMuted,
                                modifier = Modifier
                                    .size(20.dp)
                                    .scale(iconScale),
                                strokeWidthDp = if (isSelected) 1.8f else 1.6f,
                                badgeFillColor = if (isSelected) (if (palette.isGlass) Color.White.copy(alpha = 0.5f) else palette.pillActiveBg) else palette.dockBg
                            )
                            AnimatedVisibility(
                                visible = isSelected,
                                enter = expandHorizontally(spring(dampingRatio = 0.8f, stiffness = 420f)) + fadeIn(tween(180)),
                                exit = shrinkHorizontally(tween(150)) + fadeOut(tween(120))
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Spacer(modifier = Modifier.width(7.dp))
                                    Text(
                                        text = label,
                                        color = palette.textMain,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        val isEn = strings.isEn
        Box(
            modifier = Modifier
                .size(52.dp)
                .shadow(elevation = 6.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(color = palette.textPrimary, shape = CircleShape)
                .clickable { onOpenAddBook() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = if (isEn) "Add Book" else "Tambah Buku",
                tint = palette.bgApp,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

private val BROKEN_APOSTROPHE_REGEX =
    Regex("([A-Za-z])[\\uFFFD\\u0080-\\u009F\\u00D5\\uF000-\\uFFFF]+([sStTdDmMrRvVeE]\\b)")
private val CONTROL_CHARS_REGEX = Regex("[\\u0000-\\u001F\\u007F-\\u009F]")
private val MULTI_WHITESPACE_REGEX = Regex("\\s+")
private val NUMERIC_HTML_ENTITY_REGEX = Regex("&#(x?[0-9a-fA-F]+);")

fun sanitizeBookOrChapterText(raw: String): String {
    if (raw.isBlank()) return raw
    val hasAmp = raw.contains('&')
    val pre = if (hasAmp) {
        val named = raw
            .replace("&apos;", "'", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&nbsp;", " ", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&hellip;", "…", ignoreCase = true)
            .replace("&#39;", "'")
            .replace("&#039;", "'")
            .replace("&#34;", "\"")
            .replace("&#160;", " ")
            .replace("&rsquo;", "'")
            .replace("&lsquo;", "'")
            .replace("&ldquo;", "\"")
            .replace("&rdquo;", "\"")
            .replace("&ndash;", "–")
            .replace("&mdash;", "—")
        val numDecoded = if (named.contains("&#")) {
            NUMERIC_HTML_ENTITY_REGEX.replace(named) { match ->
                val token = match.groupValues[1]
                val codePoint = runCatching {
                    if (token.startsWith("x", ignoreCase = true)) {
                        token.substring(1).toInt(16)
                    } else {
                        token.toInt(10)
                    }
                }.getOrNull()
                if (codePoint != null && codePoint in 0x20..0x10FFFF) {
                    String(Character.toChars(codePoint))
                } else {
                    match.value
                }
            }
        } else {
            named
        }
        numDecoded.replace("&amp;", "&", ignoreCase = true)
    } else {
        raw
    }

    return pre
        // Windows-1252 / MacRoman / Private Use / Replacement char apostrophes between letters (e.g., Storm▯s End -> Storm's End)
        .replace(BROKEN_APOSTROPHE_REGEX, "$1'$2")
        .replace('\u0091', '\'')
        .replace('\u0092', '\'')
        .replace('\u2018', '\'')
        .replace('\u2019', '\'')
        .replace('\u0093', '"')
        .replace('\u0094', '"')
        .replace('\u0096', '–')
        .replace('\u0097', '—')
        .replace('\uFFFD', '\'')
        .replace(CONTROL_CHARS_REGEX, "")
        .replace(MULTI_WHITESPACE_REGEX, " ")
        .replace(" Amp ", " & ")
        .trim()
}

fun normalizeBookTitle(fileName: String): String {
    val sanitized = sanitizeBookOrChapterText(fileName)
    var s = sanitized.replace(Regex("""\.(epub|pdf|txt|json|mobi|azw3|cbz|cbr|fb2|djvu)$""", RegexOption.IGNORE_CASE), "")
    // Decode common HTML entities and URL encoding first
    s = sanitizeBookOrChapterText(s)
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
    s = runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)
    s = sanitizeBookOrChapterText(s)
    // Replace " - " or " _ " or " -- " with ": "
    s = s.replace(Regex("""\s*[-_–—]{2,}\s*|\s+[-–—]\s+"""), ": ")
    // Replace remaining single underscores and dashes with spaces
    s = s.replace('_', ' ').replace('-', ' ')
    // Collapse multiple colons or whitespace
    s = s.replace(Regex(""":+"""), ":")
    s = s.replace(Regex("""\s+"""), " ").trim()

    val minorWords = setOf(
        "a", "an", "the", "and", "but", "or", "for", "nor", "on", "at", "to", "by", "of", "in", "up",
        "dan", "di", "ke", "dari", "yang", "untuk", "pada", "dengan"
    )
    val words = s.split(" ")
    val result = words.mapIndexed { i, w ->
        val cleanWord = w.trim()
        val hasColon = cleanWord.endsWith(":")
        val core = if (hasColon) cleanWord.dropLast(1) else cleanWord
        val coreLower = core.lowercase(java.util.Locale.ROOT)

        val res = if (core == "&" || core.equals("amp", ignoreCase = true)) {
            "&"
        } else if (i > 0 && coreLower in minorWords) {
            coreLower
        } else {
            core.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.ROOT) else it.toString() }
        }
        if (hasColon) "$res:" else res
    }.joinToString(" ").ifBlank { "Untitled" }
    return sanitizeBookOrChapterText(result).replace(" Amp ", " & ")
}

/**
 * 1:1 `.flyleaf-slider` matching HTML prototype:
 * 3.dp slim track with 16.dp thumb, 2.dp border, and subtle shadow.
 */
@Composable
fun FlyleafThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val palette = LocalLluneviePalette.current
    val currentValueRange by rememberUpdatedState(valueRange)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)

    val rangeSpan = (valueRange.endInclusive - valueRange.start).coerceAtLeast(0.0001f)
    val externalFraction = ((value - valueRange.start) / rangeSpan).coerceIn(0f, 1f)

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(externalFraction) }

    if (!isDragging) {
        dragFraction = externalFraction
    }

    val fraction = dragFraction

    BoxWithConstraints(
        modifier = modifier
            .height(28.dp)
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    down.consume()
                    isDragging = true
                    val trackStartPx = 8.dp.toPx()
                    val trackWidthPx = (size.width.toFloat() - trackStartPx * 2f).coerceAtLeast(1f)
                    fun updateFromTouchX(x: Float) {
                        val span = (currentValueRange.endInclusive - currentValueRange.start).coerceAtLeast(0.0001f)
                        val frac = ((x - trackStartPx) / trackWidthPx).coerceIn(0f, 1f)
                        dragFraction = frac
                        val mappedVal = currentValueRange.start + frac * span
                        currentOnValueChange(mappedVal)
                    }
                    updateFromTouchX(down.position.x)

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            change.consume()
                            break
                        }
                        change.consume()
                        updateFromTouchX(change.position.x)
                    }
                    isDragging = false
                    currentOnValueChangeFinished?.invoke()
                }
            },
        contentAlignment = Alignment.CenterStart
    ) {
        val thumbRadiusDp = 8.dp
        val availableW = (maxWidth - thumbRadiusDp * 2).coerceAtLeast(0.dp)

        // Track container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = thumbRadiusDp)
                .height(3.dp)
                .clip(CircleShape)
                .background(palette.borderSoft)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction)
                    .height(3.dp)
                    .background(palette.textMain)
            )
        }

        // Thumb container
        val thumbOffset = availableW * fraction
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(16.dp)
                .shadow(elevation = 3.dp, shape = CircleShape)
                .background(palette.textMain, CircleShape)
                .border(2.dp, palette.bgApp, CircleShape)
        )
    }
}

@Composable
fun LlunevieThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChangeFinished: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    FlyleafThinSlider(
        value = value,
        onValueChange = onValueChange,
        valueRange = valueRange,
        steps = steps,
        onValueChangeFinished = onValueChangeFinished,
        modifier = modifier
    )
}

/**
 * Centered text field with PlatformTextStyle and LineHeightStyle
 * guaranteeing the blinking cursor | is 100% vertically centered on the baseline.
 */
@Composable
fun LlunevieCenteredTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    fontSize: TextUnit = 13.sp,
    fontFamily: FontFamily = FontFamily.Default,
    textColor: Color = LocalLluneviePalette.current.textMain,
    placeholderColor: Color = LocalLluneviePalette.current.textMuted
) {
    val palette = LocalLluneviePalette.current
    val alignedInputTextStyle = TextStyle(
        fontSize = fontSize,
        lineHeight = fontSize * 1.25f,
        fontFamily = fontFamily,
        color = textColor,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both
        )
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = singleLine,
        textStyle = alignedInputTextStyle,
        cursorBrush = SolidColor(palette.accentDark),
        modifier = modifier,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxHeight(),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = alignedInputTextStyle.copy(color = placeholderColor),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                innerTextField()
            }
        }
    )
}
