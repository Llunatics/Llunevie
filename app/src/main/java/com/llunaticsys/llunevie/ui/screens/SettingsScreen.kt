package com.llunaticsys.llunevie.ui.screens

import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.graphics.StrokeCap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import kotlin.math.max
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.llunaticsys.llunevie.data.local.UserPreferencesState
import com.llunaticsys.llunevie.model.AppThemeMode
import com.llunaticsys.llunevie.model.PageAnimStyle
import com.llunaticsys.llunevie.model.ReadingFlowMode
import com.llunaticsys.llunevie.ui.components.CustomThemeDropdown
import com.llunaticsys.llunevie.ui.components.LlunevieAppLogoSvg
import com.llunaticsys.llunevie.ui.components.LlunevieThinSlider
import com.llunaticsys.llunevie.ui.components.ModernSwitch
import com.llunaticsys.llunevie.ui.components.PrototypeIconType
import com.llunaticsys.llunevie.ui.components.PrototypeSvgIcon
import com.llunaticsys.llunevie.ui.theme.LlunevieStrings
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    prefs: UserPreferencesState,
    strings: LlunevieStrings,
    booksCount: Int,
    highlightsCount: Int,
    onSetTheme: (AppThemeMode) -> Unit,
    onToggleGlass: () -> Unit = {},
    onToggleFollowDeviceTheme: (Boolean) -> Unit = {},
    onToggleAmoled: () -> Unit,
    onSetLanguage: (String) -> Unit,
    onSetUserName: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onResetPhoto: () -> Unit = {},
    onSavePhotoPath: (String?) -> Unit = {},
    onSetReadingFlowMode: (ReadingFlowMode) -> Unit,
    onSetAnimatePageTurn: (Boolean) -> Unit,
    onSetPageAnimStyle: (PageAnimStyle) -> Unit,
    onSetVolumeKeysTurn: (Boolean) -> Unit,
    onToggleAutoImportFolder: (Boolean) -> Unit = {},
    onSelectAutoImportFolder: (path: String, uri: String?) -> Unit = { _, _ -> },
    onRescanFolder: () -> Unit = {},
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLluneviePalette.current
    val isSystemDark = isSystemInDarkTheme()
    val effectiveTheme = prefs.effectiveTheme(isSystemDark)
    val context = LocalContext.current
    val isEn = prefs.lang == "en"
    var showNameDialog by remember { mutableStateOf(false) }

    var pendingCropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropScale by remember { mutableFloatStateOf(1f) }
    var cropOffset by remember { mutableStateOf(Offset.Zero) }
    var cropRotationDegrees by remember { mutableFloatStateOf(0f) }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val displayPath = resolveDisplayFolderPath(uri)
            onSelectAutoImportFolder(displayPath, uri.toString())
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val stream = context.contentResolver.openInputStream(uri)
                val decoded = BitmapFactory.decodeStream(stream)
                stream?.close()
                if (decoded != null) {
                    pendingCropBitmap = decoded
                    cropScale = 1f
                    cropOffset = Offset.Zero
                    cropRotationDegrees = 0f
                }
            }
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
                        .clickable { photoPickerLauncher.launch("image/*") }
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
                                val active = effectiveTheme == mode
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

                // Follow Device Theme Toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = if (strings.isEn) "Follow Device Theme" else "Ikuti Tema Perangkat",
                            color = palette.textMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (strings.isEn) "Automatically switch between White and Black theme based on phone dark mode"
                                else "Otomatis gunakan tema Putih saat mode terang dan Hitam saat mode gelap",
                            color = palette.textMuted,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp
                        )
                    }
                    ModernSwitch(
                        checked = prefs.followDeviceTheme,
                        onCheckedChange = {
                            onToggleFollowDeviceTheme(it)
                        }
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

        // AUTO-IMPORT FOLDER CARD
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = palette.bgCard,
            border = BorderStroke(1.dp, palette.borderSoft),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                // Top Row — Toggle & Folder Picker Icon
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEn) "Import new books in selected folders to the shelf automatically"
                        else "Impor buku baru di folder terpilih ke rak secara otomatis",
                        color = palette.textPrimary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f).padding(end = 12.dp)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ModernSwitch(
                            checked = prefs.autoImportFolderEnabled,
                            onCheckedChange = { checked ->
                                onToggleAutoImportFolder(checked)
                            }
                        )
                        Surface(
                            onClick = { folderPickerLauncher.launch(null) },
                            shape = CircleShape,
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = "Pick Folder",
                                    tint = palette.textPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(thickness = 0.75.dp, color = palette.borderSoft)

                // Bottom Row — Main Folder Display
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isEn) "Main Folder" else "Folder Utama",
                        color = palette.textSecondary,
                        fontSize = 12.5.sp
                    )
                    val displayFolder = prefs.autoImportFolderPath.ifBlank { "/sdcard/Llunevie/Books" }
                    Column(
                        modifier = Modifier
                            .clickable {
                                onRescanFolder()
                                folderPickerLauncher.launch(null)
                            }
                            .padding(vertical = 2.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = displayFolder,
                            color = palette.textPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .widthIn(min = 20.dp)
                                .height(1.dp)
                                .background(palette.accent)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

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

    // Circular Photo Crop, Drag-Pan, Pinch-Zoom & 90° Rotate Modal
    pendingCropBitmap?.let { cropBmp ->
        val isEn = !strings.isId
        Dialog(onDismissRequest = { pendingCropBitmap = null }) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = palette.bgSurface,
                border = BorderStroke(1.dp, palette.borderSoft),
                shadowElevation = 24.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 340.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Header Row: Title & Subtitle on Left, 32.dp Close Button on Right
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isEn) "Adjust Profile Photo" else "Sesuaikan Foto Profil",
                                color = palette.textPrimary,
                                fontSize = 18.sp,
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Normal
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (isEn) "Drag to reposition, pinch to zoom" else "Geser dan cubit untuk mengatur posisi",
                                color = palette.textSecondary,
                                fontSize = 11.sp
                            )
                        }
                        Surface(
                            onClick = { pendingCropBitmap = null },
                            shape = CircleShape,
                            color = palette.bgCard,
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

                    // Interactive 216.dp x 216.dp Crop Stage
                    val stageSizeDp = 216.dp
                    val stageSizePx = with(LocalDensity.current) { stageSizeDp.toPx() }
                    Box(
                        modifier = Modifier
                            .size(stageSizeDp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black)
                            .pointerInput(cropBmp) {
                                detectTransformGestures { _, pan, zoom, _ ->
                                    val newScale = (cropScale * zoom).coerceIn(1f, 3f)
                                    cropScale = newScale
                                    cropOffset = clampCropPan(
                                        panX = cropOffset.x + pan.x,
                                        panY = cropOffset.y + pan.y,
                                        stageSizePx = stageSizePx,
                                        bmpW = cropBmp.width,
                                        bmpH = cropBmp.height,
                                        zoom = newScale,
                                        rotationDeg = cropRotationDegrees
                                    )
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val stageW = size.width
                            val stageH = size.height
                            val isQuarterTurn = ((cropRotationDegrees.roundToInt() / 90) % 2) != 0
                            val rotW = if (isQuarterTurn) cropBmp.height.toFloat() else cropBmp.width.toFloat()
                            val rotH = if (isQuarterTurn) cropBmp.width.toFloat() else cropBmp.height.toFloat()
                            val baseScale = max(stageW / rotW.coerceAtLeast(1f), stageH / rotH.coerceAtLeast(1f))
                            val totalScale = baseScale * cropScale

                            val matrix = android.graphics.Matrix().apply {
                                postTranslate(-cropBmp.width / 2f, -cropBmp.height / 2f)
                                postRotate(cropRotationDegrees)
                                postScale(totalScale, totalScale)
                                postTranslate(
                                    (stageW / 2f) + cropOffset.x,
                                    (stageH / 2f) + cropOffset.y
                                )
                            }
                            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
                            drawIntoCanvas { canvas ->
                                canvas.nativeCanvas.drawBitmap(cropBmp, matrix, paint)
                            }

                            // Circular Cutout Mask & crisp circular border
                            val r = stageW / 2f
                            val cx = stageW / 2f
                            val cy = stageH / 2f
                            val mask = android.graphics.Path().apply {
                                fillType = android.graphics.Path.FillType.EVEN_ODD
                                addRect(0f, 0f, stageW, stageH, android.graphics.Path.Direction.CW)
                                addCircle(cx, cy, r, android.graphics.Path.Direction.CW)
                            }
                            drawContext.canvas.nativeCanvas.drawPath(
                                mask,
                                android.graphics.Paint().apply {
                                    color = android.graphics.Color.argb((0.68f * 255).roundToInt(), 0, 0, 0)
                                    style = android.graphics.Paint.Style.FILL
                                }
                            )
                            drawCircle(
                                color = Color.White.copy(alpha = 0.85f),
                                radius = r - 1f,
                                center = Offset(cx, cy),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.25.dp.toPx())
                            )
                        }
                    }

                    // Combined Zoom + Rotate Control Row (Single compact row, height = 36.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(36.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${String.format(Locale.US, "%.1f", cropScale)}x",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = palette.textSecondary,
                            modifier = Modifier.width(34.dp)
                        )
                        LlunevieThinSlider(
                            value = cropScale,
                            onValueChange = { newScale ->
                                cropScale = newScale
                                cropOffset = clampCropPan(
                                    panX = cropOffset.x,
                                    panY = cropOffset.y,
                                    stageSizePx = stageSizePx,
                                    bmpW = cropBmp.width,
                                    bmpH = cropBmp.height,
                                    zoom = newScale,
                                    rotationDeg = cropRotationDegrees
                                )
                            },
                            valueRange = 1f..3f,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            onClick = {
                                val newRot = (cropRotationDegrees + 90f) % 360f
                                cropRotationDegrees = newRot
                                cropOffset = clampCropPan(
                                    panX = cropOffset.x,
                                    panY = cropOffset.y,
                                    stageSizePx = stageSizePx,
                                    bmpW = cropBmp.width,
                                    bmpH = cropBmp.height,
                                    zoom = cropScale,
                                    rotationDeg = newRot
                                )
                            },
                            shape = CircleShape,
                            color = palette.bgCard,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("↻", color = palette.textPrimary, fontSize = 12.sp)
                                Text(
                                    text = "90°",
                                    color = palette.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // Bottom Action Row (height = 38.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(38.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = {
                                onResetPhoto()
                                pendingCropBitmap = null
                            },
                            shape = CircleShape,
                            color = palette.bgCard,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isEn) "Default" else "Ikon Default",
                                    color = palette.textSecondary,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                onClick = { pendingCropBitmap = null },
                                shape = CircleShape,
                                color = palette.bgCard,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier.height(38.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = strings.cancelBtn,
                                        color = palette.textPrimary,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Surface(
                                onClick = {
                                    val out = renderCroppedBitmapWithExactMatrix(
                                        src = cropBmp,
                                        outputSizePx = 512,
                                        stageSizePx = stageSizePx,
                                        zoom = cropScale,
                                        clampedPanStagePx = cropOffset,
                                        rotationDeg = cropRotationDegrees,
                                        circularMask = true
                                    )
                                    val dir = File(context.filesDir, "profile").apply { mkdirs() }
                                    val outFile = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
                                    FileOutputStream(outFile).use { fos ->
                                        out.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                                    }
                                    onSavePhotoPath(outFile.absolutePath)
                                    pendingCropBitmap = null
                                },
                                shape = CircleShape,
                                color = palette.textPrimary,
                                modifier = Modifier.height(38.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isEn) "Apply" else "Terapkan",
                                        color = palette.bgApp,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold
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

fun clampCropPan(
    panX: Float,
    panY: Float,
    stageSizePx: Float,
    bmpW: Int,
    bmpH: Int,
    zoom: Float,
    rotationDeg: Float
): Offset {
    val isQuarterTurn = ((rotationDeg.roundToInt() / 90) % 2) != 0
    val rotW = if (isQuarterTurn) bmpH.toFloat() else bmpW.toFloat()
    val rotH = if (isQuarterTurn) bmpW.toFloat() else bmpH.toFloat()
    val baseScale = max(stageSizePx / rotW.coerceAtLeast(1f), stageSizePx / rotH.coerceAtLeast(1f))
    val drawnW = rotW * baseScale * zoom
    val drawnH = rotH * baseScale * zoom
    val maxPanX = ((drawnW - stageSizePx) * 0.5f).coerceAtLeast(0f)
    val maxPanY = ((drawnH - stageSizePx) * 0.5f).coerceAtLeast(0f)
    return Offset(
        x = panX.coerceIn(-maxPanX, maxPanX),
        y = panY.coerceIn(-maxPanY, maxPanY)
    )
}

fun renderCroppedBitmapWithExactMatrix(
    src: Bitmap,
    outputSizePx: Int,
    stageSizePx: Float,
    zoom: Float,
    clampedPanStagePx: Offset,
    rotationDeg: Float,
    circularMask: Boolean = true
): Bitmap {
    val out = Bitmap.createBitmap(outputSizePx, outputSizePx, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(out)
    val ratio = outputSizePx.toFloat() / stageSizePx.coerceAtLeast(1f)
    val isQuarterTurn = ((rotationDeg.roundToInt() / 90) % 2) != 0
    val rotW = if (isQuarterTurn) src.height.toFloat() else src.width.toFloat()
    val rotH = if (isQuarterTurn) src.width.toFloat() else src.height.toFloat()
    val baseScale = max(outputSizePx / rotW.coerceAtLeast(1f), outputSizePx / rotH.coerceAtLeast(1f))
    val totalScale = baseScale * zoom

    if (circularMask) {
        val clipPath = android.graphics.Path().apply {
            addCircle(outputSizePx / 2f, outputSizePx / 2f, outputSizePx / 2f, android.graphics.Path.Direction.CW)
        }
        canvas.clipPath(clipPath)
    }

    val matrix = android.graphics.Matrix().apply {
        // 1. Center source bitmap at origin (0, 0)
        postTranslate(-src.width / 2f, -src.height / 2f)
        // 2. Rotate around origin
        postRotate(rotationDeg)
        // 3. Scale by baseScale * zoom around origin
        postScale(totalScale, totalScale)
        // 4. Translate to center of output canvas + scaled user pan offset
        postTranslate(
            (outputSizePx / 2f) + (clampedPanStagePx.x * ratio),
            (outputSizePx / 2f) + (clampedPanStagePx.y * ratio)
        )
    }
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
    canvas.drawBitmap(src, matrix, paint)
    return out
}

private fun resolveDisplayFolderPath(uri: Uri): String {
    val path = uri.path.orEmpty()
    val decoded = Uri.decode(path)
    return when {
        decoded.startsWith("/tree/primary:") -> "/sdcard/" + decoded.removePrefix("/tree/primary:")
        decoded.startsWith("primary:") -> "/sdcard/" + decoded.removePrefix("primary:")
        decoded.contains(":") -> "/sdcard/" + decoded.substringAfterLast(":")
        else -> "/sdcard/Llunevie/Books"
    }
}
