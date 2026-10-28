package com.llunaticsys.llunevie.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.llunaticsys.llunevie.data.local.UserPreferencesState
import com.llunaticsys.llunevie.model.AppThemeMode
import com.llunaticsys.llunevie.model.PageAnimStyle
import com.llunaticsys.llunevie.model.ReadingFlowMode
import com.llunaticsys.llunevie.ui.components.CustomThemeDropdown
import com.llunaticsys.llunevie.ui.components.LlunevieAppLogoSvg
import com.llunaticsys.llunevie.ui.components.ModernSwitch
import com.llunaticsys.llunevie.ui.components.PrototypeIconType
import com.llunaticsys.llunevie.ui.components.PrototypeSvgIcon
import com.llunaticsys.llunevie.ui.theme.LlunevieStrings
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette
import coil.compose.AsyncImage
import java.io.File

@Composable
fun SettingsScreen(
    prefs: UserPreferencesState,
    strings: LlunevieStrings,
    booksCount: Int,
    highlightsCount: Int,
    onSetTheme: (AppThemeMode) -> Unit,
    onToggleGlass: () -> Unit,
    onToggleAmoled: () -> Unit,
    onSetLanguage: (String) -> Unit,
    onSetUserName: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onResetPhoto: () -> Unit = {},
    onSetReadingFlowMode: (ReadingFlowMode) -> Unit,
    onSetAnimatePageTurn: (Boolean) -> Unit,
    onSetPageAnimStyle: (PageAnimStyle) -> Unit,
    onSetVolumeKeysTurn: (Boolean) -> Unit,
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLluneviePalette.current
    var showNameDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 108.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = strings.settingsHeader,
                color = palette.textMain,
                fontSize = 26.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = strings.settingsSub,
            color = palette.textMuted,
            fontSize = 12.sp
        )

        // SECTION 1: PROFILE & APPEARANCE (`#i18nSecYou`)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = strings.secYou,
            color = palette.textMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = palette.bgCard,
            border = BorderStroke(1.dp, palette.borderSoft),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                // Set Name
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showNameDialog = true }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = strings.setName, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = prefs.userName.ifBlank { if (strings.isId) "Belum diatur (Llunevie)" else "Not set (Llunevie)" },
                            color = palette.textMuted,
                            fontSize = 13.sp
                        )
                        Text("›", color = palette.textMuted.copy(alpha = 0.6f), fontSize = 14.sp)
                    }
                }
                HorizontalDivider(color = palette.borderSoft)

                // Set Photo (`#settingCycleAvatarRow` with official LlunevieAppLogoSvg default)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickPhoto() }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = strings.setPhoto, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = palette.bgCard,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (!prefs.userPhotoPath.isNullOrBlank() && File(prefs.userPhotoPath).exists()) {
                                    AsyncImage(
                                        model = File(prefs.userPhotoPath),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    LlunevieAppLogoSvg(modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                        Text("›", color = palette.textMuted.copy(alpha = 0.6f), fontSize = 14.sp)
                    }
                }
                HorizontalDivider(color = palette.borderSoft)

                // Theme (Paper, White, Sepia, Black)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = strings.themeLabel, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Surface(
                        shape = CircleShape,
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft)
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            AppThemeMode.entries.forEach { mode ->
                                val active = prefs.appTheme == mode
                                Surface(
                                    shape = CircleShape,
                                    color = if (active) palette.pillActiveBg else Color.Transparent,
                                    shadowElevation = if (active) 1.dp else 0.dp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { onSetTheme(mode) }
                                ) {
                                    Text(
                                        text = mode.displayLabel,
                                        color = if (active) palette.textMain else palette.textMuted,
                                        fontSize = 11.sp,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = palette.borderSoft)

                // Glass Design Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(text = strings.glassToggleLabel, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = strings.glassToggleSub, color = palette.textMuted, fontSize = 11.5.sp, lineHeight = 17.sp)
                    }
                    ModernSwitch(
                        checked = prefs.glassDesign,
                        onCheckedChange = { onToggleGlass() }
                    )
                }
                HorizontalDivider(color = palette.borderSoft)

                // AMOLED Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(text = strings.amoledToggleLabel, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = strings.amoledToggleSub, color = palette.textMuted, fontSize = 11.5.sp, lineHeight = 17.sp)
                    }
                    ModernSwitch(
                        checked = prefs.amoledBlack,
                        onCheckedChange = { onToggleAmoled() }
                    )
                }
                HorizontalDivider(color = palette.borderSoft)

                // Language (Indonesia / English)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = strings.langLabel, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Surface(
                        shape = CircleShape,
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft)
                    ) {
                        Row(modifier = Modifier.padding(4.dp)) {
                            listOf("id" to "Indonesia", "en" to "English").forEach { (code, label) ->
                                val active = prefs.lang == code
                                Surface(
                                    shape = CircleShape,
                                    color = if (active) palette.pillActiveBg else Color.Transparent,
                                    shadowElevation = if (active) 1.dp else 0.dp,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .clickable { onSetLanguage(code) }
                                ) {
                                    Text(
                                        text = label,
                                        color = if (active) palette.textMain else palette.textMuted,
                                        fontSize = 11.sp,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // SECTION 2: READING & LIGHT NOVEL (`#i18nSecReading`)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = strings.secReading,
            color = palette.textMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = palette.bgCard,
            border = BorderStroke(1.dp, palette.borderSoft),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                // Reading Mode (4 Unified Modes)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(text = strings.readingModeLabel, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = strings.readingModeSub, color = palette.textMuted, fontSize = 11.5.sp, lineHeight = 17.sp)
                    }
                    CustomThemeDropdown(
                        title = strings.readingModeLabel,
                        selectedValue = prefs.readingFlowMode,
                        options = ReadingFlowMode.entries.map { it to it.localizedLabel(prefs.lang) },
                        onValueSelected = onSetReadingFlowMode
                    )
                }
                HorizontalDivider(color = palette.borderSoft)

                // Animate Page Turn Switch
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(text = strings.animPageLabel, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = strings.animPageSub, color = palette.textMuted, fontSize = 11.5.sp, lineHeight = 17.sp)
                    }
                    ModernSwitch(
                        checked = prefs.animatePageTurn,
                        onCheckedChange = onSetAnimatePageTurn
                    )
                }
                HorizontalDivider(color = palette.borderSoft)

                // Page Animation Style (Flipbook, Slide, Fade)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (prefs.animatePageTurn) 1f else 0.45f)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(text = strings.animStyleLabel, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = strings.animStyleSub, color = palette.textMuted, fontSize = 11.5.sp, lineHeight = 17.sp)
                    }
                    CustomThemeDropdown(
                        title = strings.animStyleLabel,
                        selectedValue = prefs.pageAnimStyle,
                        options = PageAnimStyle.entries.map { it to it.label },
                        onValueSelected = onSetPageAnimStyle
                    )
                }
                HorizontalDivider(color = palette.borderSoft)

                // Hardware Volume Keys Turn Page
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(text = strings.volKeyLabel, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = strings.volKeySub, color = palette.textMuted, fontSize = 11.5.sp, lineHeight = 17.sp)
                    }
                    ModernSwitch(
                        checked = prefs.volumeKeysTurn,
                        onCheckedChange = onSetVolumeKeysTurn
                    )
                }
            }
        }

        // SECTION 3: LOCAL DATA & BACKUP (`#i18nSecJourney` in `llunevie_prototype.html`)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = strings.secJourney,
            color = palette.textMuted,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
        )

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = palette.bgCard,
            border = BorderStroke(1.dp, palette.borderSoft),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = strings.keptLabel, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = if (strings.isId) "$booksCount buku · $highlightsCount sorotan"
                        else "$booksCount books · $highlightsCount highlights",
                        color = palette.textMuted,
                        fontSize = 12.sp
                    )
                }
                HorizontalDivider(color = palette.borderSoft)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = strings.lastSavedLabel, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    Text(
                        text = strings.justNowLabel,
                        color = palette.textMuted,
                        fontSize = 12.sp
                    )
                }
                HorizontalDivider(color = palette.borderSoft)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onExportBackup() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = strings.saveCopyLabel, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    PrototypeSvgIcon(
                        type = PrototypeIconType.DOWNLOAD_TRAY,
                        tint = palette.textMuted.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
                HorizontalDivider(color = palette.borderSoft)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRestoreBackup() }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = strings.restoreFileLabel, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    PrototypeSvgIcon(
                        type = PrototypeIconType.UPLOAD_TRAY,
                        tint = palette.textMuted.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }

    // SET NAME MODAL (`#setNameModal` in `llunevie_prototype.html`)
    if (showNameDialog) {
        var nameInput by remember(prefs.userName) { mutableStateOf(prefs.userName) }
        Dialog(onDismissRequest = { showNameDialog = false }) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = palette.sheetBg,
                border = BorderStroke(1.dp, palette.borderSoft),
                shadowElevation = 24.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.setName,
                            color = palette.textMain,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium
                        )
                        Surface(
                            onClick = { showNameDialog = false },
                            shape = CircleShape,
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.size(28.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("✕", color = palette.textMain, fontSize = 11.sp)
                            }
                        }
                    }

                    Text(
                        text = if (strings.isId) "Kosongkan jika ingin tetap menampilkan nama aplikasi Llunevie di kiri atas beranda."
                        else "Leave blank to display the default Llunevie app title at the top left of Home.",
                        color = palette.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Surface(
                        shape = CircleShape,
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            if (nameInput.isEmpty()) {
                                Text(
                                    text = if (strings.isId) "Contoh: Feyi" else "e.g., Feyi",
                                    color = palette.textMuted.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                            BasicTextField(
                                value = nameInput,
                                onValueChange = { nameInput = it },
                                singleLine = true,
                                textStyle = TextStyle(color = palette.textMain, fontSize = 12.sp),
                                cursorBrush = SolidColor(palette.textMain),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = {
                                onSetUserName("")
                                showNameDialog = false
                            },
                            shape = CircleShape,
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = "Default (Llunevie)",
                                color = palette.textMain,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                        Surface(
                            onClick = {
                                onSetUserName(nameInput)
                                showNameDialog = false
                            },
                            shape = CircleShape,
                            color = palette.accentDark,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = if (strings.isId) "Simpan" else "Save",
                                color = palette.bgApp,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
