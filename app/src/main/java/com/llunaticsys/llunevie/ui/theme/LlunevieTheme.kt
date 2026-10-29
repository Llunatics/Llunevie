package com.llunaticsys.llunevie.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.llunaticsys.llunevie.model.AppThemeMode
import com.llunaticsys.llunevie.model.HighlightColorKey

@Immutable
data class LluneviePalette(
    val themeMode: AppThemeMode,
    val isGlass: Boolean,
    val isAmoled: Boolean,
    val isDark: Boolean,
    val bgApp: Color,
    val bgCard: Color,
    val bgSurface: Color,
    val sheetBg: Color,
    val dockBg: Color,
    val dockBorder: Color,
    val textMain: Color,
    val textMuted: Color,
    val borderSoft: Color,
    val accentDark: Color,
    val pillActiveBg: Color,
    val pillActiveBorder: Color,
    val flipBackPaper: Color,
    val dotEmpty: Color,
    val dotL1: Color,
    val dotL2: Color,
    val dotL3: Color,
    val dotL4: Color,
    val hlGoldBg: Color,
    val hlGoldBorder: Color,
    val hlRoseBg: Color,
    val hlRoseBorder: Color,
    val hlSkyBg: Color,
    val hlSkyBorder: Color,
    val hlSageBg: Color,
    val hlSageBorder: Color,
    val hlText: Color
) {
    val textPrimary: Color get() = textMain
    val textSecondary: Color get() = textMuted
    fun highlightBg(key: HighlightColorKey): Color = when (key) {
        HighlightColorKey.GOLD -> Color(0xFFFEF08A)
        HighlightColorKey.PEACH -> Color(0xFFFECDD3)
        HighlightColorKey.SKY -> Color(0xFFBAE6FD)
        HighlightColorKey.MINT -> Color(0xFFA7F3D0)
        HighlightColorKey.LILAC -> Color(0xFFDDD6FE)
        HighlightColorKey.APRICOT -> Color(0xFFFED7AA)
        HighlightColorKey.AQUA -> Color(0xFF99F6E4)
        HighlightColorKey.PINK -> Color(0xFFF5D0FE)
    }

    fun highlightBorder(key: HighlightColorKey): Color = when (key) {
        HighlightColorKey.GOLD -> Color(0xFFFACC15)
        HighlightColorKey.PEACH -> Color(0xFFFB7185)
        HighlightColorKey.SKY -> Color(0xFF38BDF8)
        HighlightColorKey.MINT -> Color(0xFF34D399)
        HighlightColorKey.LILAC -> Color(0xFFA78BFA)
        HighlightColorKey.APRICOT -> Color(0xFFFB923C)
        HighlightColorKey.AQUA -> Color(0xFF2DD4BF)
        HighlightColorKey.PINK -> Color(0xFFE879F9)
    }

    fun highlightDot(key: HighlightColorKey): Color = highlightBorder(key)
}

val LocalLluneviePalette = staticCompositionLocalOf {
    buildLluneviePalette(AppThemeMode.PAPER, isGlass = false, isAmoled = false)
}

fun buildLluneviePalette(
    mode: AppThemeMode,
    isGlass: Boolean = false,
    isAmoled: Boolean
): LluneviePalette {
    val effectiveGlass = false
    val effectiveAmoled = mode == AppThemeMode.BLACK && isAmoled
    return when (mode) {
        AppThemeMode.PAPER -> LluneviePalette(
            themeMode = mode,
            isGlass = false,
            isAmoled = false,
            isDark = false,
            bgApp = Color(0xFFF5F2EA),
            bgCard = Color(0xFFECE7DA),
            bgSurface = Color(0xFFFAF7F0),
            sheetBg = Color(0xFFF5F2EA),
            dockBg = Color(0xFFFAF7F0),
            dockBorder = Color(0xFFDDD6C5),
            textMain = Color(0xFF23201A),
            textMuted = Color(0xFF7C776B),
            borderSoft = Color(0xFFDDD6C5),
            accentDark = Color(0xFF181A1F),
            pillActiveBg = Color(0xFFE5DFD0),
            pillActiveBorder = Color(0xFF23201A).copy(alpha = 0.08f),
            flipBackPaper = Color(0xFFEAE4D6),
            dotEmpty = Color(0xFFE4E0D5),
            dotL1 = Color(0xFFC9C3B4),
            dotL2 = Color(0xFF9E9787),
            dotL3 = Color(0xFF6E685B),
            dotL4 = Color(0xFF23201A),
            hlGoldBg = Color(0xFFD4A74A).copy(alpha = 0.36f),
            hlGoldBorder = Color(0xFFC4922F),
            hlRoseBg = Color(0xFFDA8A9A).copy(alpha = 0.34f),
            hlRoseBorder = Color(0xFFC2677A),
            hlSkyBg = Color(0xFF7DA4D6).copy(alpha = 0.34f),
            hlSkyBorder = Color(0xFF5B87BE),
            hlSageBg = Color(0xFF8FB384).copy(alpha = 0.36f),
            hlSageBorder = Color(0xFF68945B),
            hlText = Color(0xFF18181B)
        )

        AppThemeMode.WHITE -> LluneviePalette(
            themeMode = mode,
            isGlass = false,
            isAmoled = false,
            isDark = false,
            bgApp = Color(0xFFF8FAFC),
            bgCard = Color(0xFFEEF2F6),
            bgSurface = Color(0xFFFFFFFF),
            sheetBg = Color(0xFFFFFFFF),
            dockBg = Color(0xFFFFFFFF),
            dockBorder = Color(0xFFE2E8F0),
            textMain = Color(0xFF18202C),
            textMuted = Color(0xFF64748B),
            borderSoft = Color(0xFFE2E8F0),
            accentDark = Color(0xFF18202C),
            pillActiveBg = Color(0xFFF2F2F5),
            pillActiveBorder = Color(0xFF18202C).copy(alpha = 0.08f),
            flipBackPaper = Color(0xFFEEF2F7),
            dotEmpty = Color(0xFFDCE6F2),
            dotL1 = Color(0xFFB8CBE0),
            dotL2 = Color(0xFF84A2C4),
            dotL3 = Color(0xFF4B6B94),
            dotL4 = Color(0xFF18202C),
            hlGoldBg = Color(0xFFEABC52).copy(alpha = 0.36f),
            hlGoldBorder = Color(0xFFC89628),
            hlRoseBg = Color(0xFFE694A6).copy(alpha = 0.34f),
            hlRoseBorder = Color(0xFFC8647A),
            hlSkyBg = Color(0xFF84AFE4).copy(alpha = 0.34f),
            hlSkyBorder = Color(0xFF4F81BD),
            hlSageBg = Color(0xFF96C08C).copy(alpha = 0.36f),
            hlSageBorder = Color(0xFF5E9450),
            hlText = Color(0xFF18181B)
        )

        AppThemeMode.SEPIA -> LluneviePalette(
            themeMode = mode,
            isGlass = false,
            isAmoled = false,
            isDark = false,
            bgApp = Color(0xFFEFE6D5),
            bgCard = Color(0xFFE4D8C1),
            bgSurface = Color(0xFFF6EFE2),
            sheetBg = Color(0xFFEFE6D5),
            dockBg = Color(0xFFF6EFE2),
            dockBorder = Color(0xFFD2C2A6),
            textMain = Color(0xFF2C2217),
            textMuted = Color(0xFF7B6A56),
            borderSoft = Color(0xFFD2C2A6),
            accentDark = Color(0xFF181A1F),
            pillActiveBg = Color(0xFFDCCEB4),
            pillActiveBorder = Color(0xFF2C2217).copy(alpha = 0.09f),
            flipBackPaper = Color(0xFFE3D7C1),
            dotEmpty = Color(0xFFDFD2BA),
            dotL1 = Color(0xFFC4B293),
            dotL2 = Color(0xFF9C8665),
            dotL3 = Color(0xFF69553B),
            dotL4 = Color(0xFF2C2217),
            hlGoldBg = Color(0xFFCC9C3E).copy(alpha = 0.38f),
            hlGoldBorder = Color(0xFFB58222),
            hlRoseBg = Color(0xFFCE8090).copy(alpha = 0.36f),
            hlRoseBorder = Color(0xFFB35A6D),
            hlSkyBg = Color(0xFF769CCA).copy(alpha = 0.36f),
            hlSkyBorder = Color(0xFF517CAE),
            hlSageBg = Color(0xFF88AA7C).copy(alpha = 0.38f),
            hlSageBorder = Color(0xFF5F8852),
            hlText = Color(0xFF18181B)
        )

        AppThemeMode.BLACK -> LluneviePalette(
            themeMode = mode,
            isGlass = false,
            isAmoled = effectiveAmoled,
            isDark = true,
            bgApp = if (effectiveAmoled) Color(0xFF000000) else Color(0xFF141518),
            bgCard = if (effectiveAmoled) Color(0xFF0B0C0E) else Color(0xFF1C1E22),
            bgSurface = if (effectiveAmoled) Color(0xFF121316) else Color(0xFF22252A),
            sheetBg = if (effectiveAmoled) Color(0xFF0B0C0E) else Color(0xFF1C1E22),
            dockBg = if (effectiveAmoled) Color(0xFF0D0E11) else Color(0xFF1E2025),
            dockBorder = if (effectiveAmoled) Color(0xFF1F2127) else Color(0xFF2D313A),
            textMain = if (effectiveAmoled) Color(0xFFF2EFE9) else Color(0xFFECEAE2),
            textMuted = if (effectiveAmoled) Color(0xFF828690) else Color(0xFF8C909B),
            borderSoft = if (effectiveAmoled) Color(0xFF1D1F24) else Color(0xFF2A2D34),
            accentDark = if (effectiveAmoled) Color(0xFFF2EFE9) else Color(0xFFECEAE2),
            pillActiveBg = if (effectiveAmoled) Color(0xFF18191D) else Color(0xFF26282E),
            pillActiveBorder = Color.White.copy(alpha = 0.08f),
            flipBackPaper = if (effectiveAmoled) Color(0xFF0E1014) else Color(0xFF1C1E24),
            dotEmpty = if (effectiveAmoled) Color(0xFF16181D) else Color(0xFF23262E),
            dotL1 = Color(0xFF3E4452),
            dotL2 = Color(0xFF687086),
            dotL3 = Color(0xFF9BA3B8),
            dotL4 = Color(0xFFECEAE2),
            hlGoldBg = Color(0xFFE6B858).copy(alpha = 0.28f),
            hlGoldBorder = Color(0xFFE6B858),
            hlRoseBg = Color(0xFFE890A4).copy(alpha = 0.28f),
            hlRoseBorder = Color(0xFFE890A4),
            hlSkyBg = Color(0xFF82B2EC).copy(alpha = 0.28f),
            hlSkyBorder = Color(0xFF82B2EC),
            hlSageBg = Color(0xFF92C488).copy(alpha = 0.28f),
            hlSageBorder = Color(0xFF92C488),
            hlText = Color(0xFF18181B)
        )
    }
}

@Composable
fun LlunevieTheme(
    themeMode: AppThemeMode,
    glassDesign: Boolean,
    amoledBlack: Boolean,
    content: @Composable () -> Unit
) {
    val palette = buildLluneviePalette(themeMode, isGlass = false, isAmoled = amoledBlack)
    val colorScheme = if (palette.isDark) {
        darkColorScheme(
            background = palette.bgApp,
            surface = palette.bgCard,
            onBackground = palette.textMain,
            onSurface = palette.textMain,
            primary = palette.textMain,
            onPrimary = palette.bgApp
        )
    } else {
        lightColorScheme(
            background = palette.bgApp,
            surface = palette.bgCard,
            onBackground = palette.textMain,
            onSurface = palette.textMain,
            primary = palette.textMain,
            onPrimary = palette.bgApp
        )
    }

    CompositionLocalProvider(LocalLluneviePalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
