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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.heightIn
import com.llunaticsys.llunevie.model.TranslationLangOption
import com.llunaticsys.llunevie.model.DEFAULT_TRANSLATION_LANGUAGES
import com.llunaticsys.llunevie.model.EXTRA_TRANSLATION_LANGUAGES
import com.llunaticsys.llunevie.model.getActiveTranslationLanguages
import com.llunaticsys.llunevie.model.normalizeTranslationLangCode
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.platform.LocalClipboardManager
import com.llunaticsys.llunevie.data.local.DEFAULT_AI_TRANSLATOR_PROMPT
import com.llunaticsys.llunevie.data.remote.TranslationRepository
import com.llunaticsys.llunevie.ui.components.CustomThemeDropdown
import com.llunaticsys.llunevie.ui.components.LlunevieAppLogoSvg
import com.llunaticsys.llunevie.ui.components.LlunevieThinSlider
import com.llunaticsys.llunevie.ui.components.ModernSwitch
import com.llunaticsys.llunevie.ui.components.PrototypeIconType
import com.llunaticsys.llunevie.ui.components.PrototypeSvgIcon
import com.llunaticsys.llunevie.ui.components.resolveDisplayFolderPath
import com.llunaticsys.llunevie.ui.theme.LlunevieStrings
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
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
    onSetPdfThemeTintEnabled: (Boolean) -> Unit = {},
    onSetPdfProtectIllustrations: (Boolean) -> Unit = {},
    onToggleAutoImportFolder: (Boolean) -> Unit = {},
    onSelectAutoImportFolder: (path: String, uri: String?) -> Unit = { _, _ -> },
    onRescanFolder: () -> Unit = {},
    onExportBackup: () -> Unit,
    onRestoreBackup: () -> Unit,
    onUpdateTranslatorPrefs: (
        engine: String?,
        targetLang: String?,
        displayMode: String?,
        apiKey: String?,
        modelName: String?,
        customBaseUrl: String?,
        systemPrompt: String?
    ) -> Unit = { _, _, _, _, _, _, _ -> },
    onAddCustomTranslationLang: (String) -> Unit = {},
    onRemoveCustomTranslationLang: (String) -> Unit = {},
    onClearTranslationCache: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalLluneviePalette.current
    val isSystemDark = isSystemInDarkTheme()
    val effectiveTheme = prefs.effectiveTheme(isSystemDark)
    val context = LocalContext.current
    val isEn = prefs.lang == "en"
    var showNameDialog by remember { mutableStateOf(false) }

    val translationRepo = remember { TranslationRepository.getInstance() }
    var cacheStats by remember { mutableStateOf(translationRepo.getCacheStats(context)) }
    var showTargetLangDialog by remember { mutableStateOf(false) }
    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showModelDialog by remember { mutableStateOf(false) }
    var showCustomUrlDialog by remember { mutableStateOf(false) }
    var showSystemPromptDialog by remember { mutableStateOf(false) }

    var pendingCropBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var cropScale by remember { mutableFloatStateOf(1f) }
    var cropOffset by remember { mutableStateOf(Offset.Zero) }
    var cropRotationDegrees by remember { mutableFloatStateOf(0f) }
    val coroutineScope = rememberCoroutineScope()

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
            coroutineScope.launch {
                val decoded = withContext(Dispatchers.IO) {
                    runCatching {
                        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream, null, boundsOpts)
                        }
                        val rawW = boundsOpts.outWidth.coerceAtLeast(1)
                        val rawH = boundsOpts.outHeight.coerceAtLeast(1)
                        var sampleSize = 1
                        while (maxOf(rawW / sampleSize, rawH / sampleSize) > 1024) {
                            sampleSize *= 2
                        }
                        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                        context.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream, null, decodeOpts)
                        }
                    }.getOrNull()
                }
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
                                val hasUserPhoto = remember(prefs.userPhotoPath) {
                                    !prefs.userPhotoPath.isNullOrBlank() && File(prefs.userPhotoPath).exists()
                                }
                                if (hasUserPhoto) {
                                    AsyncImage(
                                        model = File(prefs.userPhotoPath!!),
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
                HorizontalDivider(color = palette.borderSoft)

                // PDF Theme Tint Enabled
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = if (strings.isId) "Adaptasi Tema pada Kertas PDF" else "Apply Theme to PDF Pages",
                            color = palette.textMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (strings.isId) "Sesuaikan warna halaman teks PDF dengan tema Paper, Sepia, atau Gelap" else "Tint or invert PDF text pages to match Paper, Sepia, or Dark theme",
                            color = palette.textMuted,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp
                        )
                    }
                    ModernSwitch(
                        checked = prefs.pdfThemeTintEnabled,
                        onCheckedChange = { onSetPdfThemeTintEnabled(it) }
                    )
                }
                HorizontalDivider(color = palette.borderSoft)

                // PDF Protect Illustrations
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(if (prefs.pdfThemeTintEnabled) 1f else 0.45f)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            text = if (strings.isId) "Jaga Warna Asli Ilustrasi & Sampul" else "Preserve Original Illustration Colors",
                            color = palette.textMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (strings.isId) "Jangan inversi halaman sampul dan gambar ilustrasi saat mode gelap/sepia aktif" else "Keep original colors on cover and artwork pages in dark/sepia mode",
                            color = palette.textMuted,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp
                        )
                    }
                    ModernSwitch(
                        checked = prefs.pdfProtectIllustrations,
                        onCheckedChange = { onSetPdfProtectIllustrations(it) }
                    )
                }
            }
        }

        // SECTION: TRANSLATION & AI (`#secTranslation`)
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = strings.secTranslation,
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
                // 1. Mesin Penerjemah / Translation Engine
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(text = strings.transEngineLabel, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (strings.isId) "Pilih mesin instant tanpa kunci atau AI canggih" else "Choose free instant or advanced AI engine",
                            color = palette.textMuted,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp
                        )
                    }
                    val engineOptions = listOf(
                        "free" to (if (strings.isId) "Free Instant (Tanpa API Key)" else "Free Instant (No API Key)"),
                        "gemini" to "Google Gemini AI",
                        "openrouter" to "OpenRouter AI",
                        "custom" to "Custom OpenAI-Compatible"
                    )
                    CustomThemeDropdown(
                        title = strings.transEngineLabel,
                        selectedValue = prefs.translatorEngine,
                        options = engineOptions,
                        onValueSelected = { onUpdateTranslatorPrefs(it, null, null, null, null, null, null) }
                    )
                }
                HorizontalDivider(color = palette.borderSoft)

                // 2. Bahasa Tujuan / Target Language
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(text = strings.transTargetLangLabel, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (strings.isId) "Bahasa hasil terjemahan kutipan dan halaman" else "Target output language for reader translations",
                            color = palette.textMuted,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp
                        )
                    }
                    val activeLangs = remember(prefs.customTranslationLangs) {
                        getActiveTranslationLanguages(prefs.customTranslationLangs)
                    }
                    val activeNormCode = normalizeTranslationLangCode(prefs.translatorTargetLang)
                    val currentLangOpt = activeLangs.find { normalizeTranslationLangCode(it.code) == activeNormCode }
                        ?: DEFAULT_TRANSLATION_LANGUAGES.find { normalizeTranslationLangCode(it.code) == activeNormCode }
                        ?: TranslationLangOption(code = activeNormCode, badgeCode = activeNormCode.uppercase().take(2), nativeName = activeNormCode)

                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .clickable { showTargetLangDialog = true },
                        shape = RoundedCornerShape(999.dp),
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = currentLangOpt.settingsLabel,
                                color = palette.textMain,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            PrototypeSvgIcon(
                                type = PrototypeIconType.CHEVRON_DOWN,
                                tint = palette.textMuted.copy(alpha = 0.75f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(color = palette.borderSoft)

                // 3. Mode Tampilan Halaman / Page Display Mode
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(text = strings.transDisplayModeLabel, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (strings.isId) "Tampilkan paragraf asli + terjemahan atau ganti teks langsung" else "Show original + translated paragraph or replace text directly",
                            color = palette.textMuted,
                            fontSize = 11.5.sp,
                            lineHeight = 17.sp
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft)
                    ) {
                        Row(modifier = Modifier.padding(3.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            val isBilingual = prefs.translatorDisplayMode == "bilingual"
                            Surface(
                                onClick = { onUpdateTranslatorPrefs(null, null, "bilingual", null, null, null, null) },
                                shape = RoundedCornerShape(999.dp),
                                color = if (isBilingual) palette.accentDark else Color.Transparent
                            ) {
                                Text(
                                    text = if (strings.isId) "Dwibahasa" else "Bilingual",
                                    color = if (isBilingual) palette.bgApp else palette.textMain,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isBilingual) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                            Surface(
                                onClick = { onUpdateTranslatorPrefs(null, null, "replace", null, null, null, null) },
                                shape = RoundedCornerShape(999.dp),
                                color = if (!isBilingual) palette.accentDark else Color.Transparent
                            ) {
                                Text(
                                    text = if (strings.isId) "Ganti Teks" else "Replace",
                                    color = if (!isBilingual) palette.bgApp else palette.textMain,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (!isBilingual) FontWeight.SemiBold else FontWeight.Normal,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                // 4. AI Configuration Rows (wrapped in AnimatedVisibility)
                AnimatedVisibility(visible = prefs.translatorEngine != "free") {
                    Column {
                        HorizontalDivider(color = palette.borderSoft)

                        // API Key row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showApiKeyDialog = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(text = strings.transApiKeyLabel, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                val keyText = if (prefs.aiApiKey.isBlank()) {
                                    if (strings.isId) "Belum diatur" else "Not set"
                                } else {
                                    (if (strings.isId) "Tersimpan (••••" else "Saved (••••") + prefs.aiApiKey.takeLast(4) + ")"
                                }
                                Text(text = keyText, color = palette.textMuted, fontSize = 11.5.sp)
                            }
                            PrototypeSvgIcon(
                                type = PrototypeIconType.CHEVRON_RIGHT,
                                tint = palette.textMuted.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        HorizontalDivider(color = palette.borderSoft)

                        // Model AI row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showModelDialog = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(text = strings.transAiModelLabel, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = prefs.aiModelName, color = palette.textMuted, fontSize = 11.5.sp)
                            }
                            PrototypeSvgIcon(
                                type = PrototypeIconType.CHEVRON_RIGHT,
                                tint = palette.textMuted.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }

                        // Endpoint Base URL (visible when prefs.translatorEngine == "custom")
                        if (prefs.translatorEngine == "custom") {
                            HorizontalDivider(color = palette.borderSoft)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showCustomUrlDialog = true }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                    Text(text = strings.transCustomUrlLabel, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = prefs.aiCustomBaseUrl, color = palette.textMuted, fontSize = 11.5.sp)
                                }
                                PrototypeSvgIcon(
                                    type = PrototypeIconType.CHEVRON_RIGHT,
                                    tint = palette.textMuted.copy(alpha = 0.7f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }

                        HorizontalDivider(color = palette.borderSoft)

                        // System Prompt row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showSystemPromptDialog = true }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(text = strings.transSystemPromptLabel, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (strings.isId) "Kustomisasi instruksi dan persona penerjemah sastra" else "Customize literary translation instructions and persona",
                                    color = palette.textMuted,
                                    fontSize = 11.5.sp
                                )
                            }
                            PrototypeSvgIcon(
                                type = PrototypeIconType.CHEVRON_RIGHT,
                                tint = palette.textMuted.copy(alpha = 0.7f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = palette.borderSoft)

                // 5. Cache Terjemahan Lokal
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                        Text(text = strings.transCacheLabel, color = palette.textMain, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        val count = cacheStats.first
                        val bytes = cacheStats.second
                        val sizeStr = if (bytes < 1024 * 1024) {
                            "%.1f KB".format(bytes / 1024f)
                        } else {
                            "%.1f MB".format(bytes / (1024f * 1024f))
                        }
                        Text(
                            text = if (strings.isId) "$count item tersimpan ($sizeStr)" else "$count items cached ($sizeStr)",
                            color = palette.textMuted,
                            fontSize = 11.5.sp
                        )
                    }
                    Surface(
                        onClick = {
                            onClearTranslationCache()
                            cacheStats = Pair(0, 0L)
                        },
                        shape = RoundedCornerShape(999.dp),
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft)
                    ) {
                        Text(
                            text = strings.transClearBtn,
                            color = palette.textMain,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
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

    // TARGET LANGUAGE SELECTOR & CUSTOM LANGUAGE MANAGER MODAL
    if (showTargetLangDialog) {
        var showAddMoreLangs by remember { mutableStateOf(false) }
        val activeLangs = remember(prefs.customTranslationLangs) {
            getActiveTranslationLanguages(prefs.customTranslationLangs)
        }
        val activeNormCode = normalizeTranslationLangCode(prefs.translatorTargetLang)
        val normCustom = prefs.customTranslationLangs.map { normalizeTranslationLangCode(it) }.toSet()
        val remainingExtras = EXTRA_TRANSLATION_LANGUAGES.filter { normalizeTranslationLangCode(it.code) !in normCustom }

        Dialog(onDismissRequest = { showTargetLangDialog = false }) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = palette.sheetBg,
                border = BorderStroke(1.dp, palette.borderSoft),
                shadowElevation = 24.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = strings.transTargetLangLabel.uppercase(),
                            color = palette.textMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.3.sp
                        )
                        Surface(
                            onClick = { showTargetLangDialog = false },
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

                    // Scrollable List of active and extra languages
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 440.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // 1. Active Languages List
                        activeLangs.forEach { langOpt ->
                            val isSelected = normalizeTranslationLangCode(langOpt.code) == activeNormCode
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = if (isSelected) palette.pillActiveBg else Color.Transparent,
                                border = if (isSelected) BorderStroke(1.dp, palette.pillActiveBorder) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        onUpdateTranslatorPrefs(null, langOpt.code, null, null, null, null, null)
                                        showTargetLangDialog = false
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Text(
                                            text = langOpt.badgeCode.uppercase(),
                                            color = if (isSelected) palette.textMain else palette.textSecondary,
                                            fontSize = 12.5.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "(${langOpt.nativeName})",
                                            color = if (isSelected) palette.textMain else palette.textMuted,
                                            fontSize = 13.sp,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        if (isSelected) {
                                            PrototypeSvgIcon(
                                                type = PrototypeIconType.CHECK,
                                                tint = palette.textMain,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        if (!langOpt.isDefault) {
                                            Surface(
                                                onClick = {
                                                    onRemoveCustomTranslationLang(langOpt.code)
                                                },
                                                shape = CircleShape,
                                                color = palette.bgSurface,
                                                border = BorderStroke(1.dp, palette.borderSoft),
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "✕",
                                                        color = palette.textMuted,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // 2. Inline Expandable "+ Tambah Bahasa Lain" / "+ Add More Languages" Section
                        if (remainingExtras.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                onClick = { showAddMoreLangs = !showAddMoreLangs },
                                shape = RoundedCornerShape(14.dp),
                                color = palette.bgSurface,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (strings.isId) "+ Tambah Bahasa Lain" else "+ Add More Languages",
                                        color = palette.accentDark,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            AnimatedVisibility(
                                visible = showAddMoreLangs,
                                enter = expandVertically() + fadeIn(),
                                exit = shrinkVertically() + fadeOut()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    remainingExtras.forEach { extra ->
                                        Surface(
                                            onClick = {
                                                onAddCustomTranslationLang(extra.code)
                                                onUpdateTranslatorPrefs(null, extra.code, null, null, null, null, null)
                                            },
                                            shape = RoundedCornerShape(12.dp),
                                            color = palette.bgSurface.copy(alpha = 0.6f),
                                            border = BorderStroke(1.dp, palette.borderSoft.copy(alpha = 0.6f)),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 14.dp, vertical = 9.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = extra.badgeCode.uppercase(),
                                                        color = palette.textSecondary,
                                                        fontSize = 12.sp,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = "(${extra.nativeName})",
                                                        color = palette.textMuted,
                                                        fontSize = 12.5.sp,
                                                        fontWeight = FontWeight.Normal
                                                    )
                                                }
                                                PrototypeSvgIcon(
                                                    type = PrototypeIconType.PLUS,
                                                    tint = palette.accentDark,
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
    }

    // SET API KEY MODAL
    if (showApiKeyDialog) {
        var keyInput by remember(prefs.aiApiKey) { mutableStateOf(prefs.aiApiKey) }
        val clipboard = LocalClipboardManager.current
        Dialog(onDismissRequest = { showApiKeyDialog = false }) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = palette.sheetBg,
                border = BorderStroke(1.dp, palette.borderSoft),
                shadowElevation = 24.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (strings.isId) "Atur API Key" else "Set API Key",
                            color = palette.textMain,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium
                        )
                        Surface(
                            onClick = { showApiKeyDialog = false },
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
                        text = if (strings.isId) "Kunci API disimpan secara lokal di perangkat ini dan tidak pernah dikirim ke pihak ketiga."
                        else "API key is stored locally on this device and never shared with third parties.",
                        color = palette.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            if (keyInput.isEmpty()) {
                                Text(
                                    text = if (strings.isId) "Tempel atau ketik API Key..." else "Paste or type API Key...",
                                    color = palette.textMuted.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                            BasicTextField(
                                value = keyInput,
                                onValueChange = { keyInput = it },
                                singleLine = true,
                                textStyle = TextStyle(color = palette.textMain, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
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
                                val clip = clipboard.getText()?.text.orEmpty()
                                if (clip.isNotBlank()) keyInput = clip.trim()
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (strings.isId) "Tempel" else "Paste",
                                    color = palette.textMain,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                onUpdateTranslatorPrefs(null, null, null, keyInput.trim(), null, null, null)
                                showApiKeyDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = palette.accentDark,
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (strings.isId) "Simpan" else "Save",
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

    // SELECT AI MODEL MODAL
    if (showModelDialog) {
        var modelInput by remember(prefs.aiModelName) { mutableStateOf(prefs.aiModelName) }
        val presets = if (prefs.translatorEngine == "gemini") {
            listOf("gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash")
        } else {
            listOf(
                "google/gemini-2.5-flash",
                "deepseek/deepseek-chat-v3-0324:free",
                "meta-llama/llama-3.3-70b-instruct:free",
                "openai/gpt-4o-mini",
                "anthropic/claude-3.5-haiku"
            )
        }

        Dialog(onDismissRequest = { showModelDialog = false }) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = palette.sheetBg,
                border = BorderStroke(1.dp, palette.borderSoft),
                shadowElevation = 24.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (strings.isId) "Pilih Model AI" else "Select AI Model",
                            color = palette.textMain,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium
                        )
                        Surface(
                            onClick = { showModelDialog = false },
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
                        text = if (strings.isId) "Pilih salah satu preset cepat atau ketik model kustom:" else "Choose a quick preset or type any custom model slug:",
                        color = palette.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        presets.forEach { preset ->
                            val isSelected = modelInput.equals(preset, ignoreCase = true)
                            Surface(
                                onClick = { modelInput = preset },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) palette.accentDark else palette.bgSurface,
                                border = BorderStroke(1.dp, if (isSelected) palette.accentDark else palette.borderSoft)
                            ) {
                                Text(
                                    text = preset,
                                    fontSize = 10.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSelected) palette.bgApp else palette.textMain,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            if (modelInput.isEmpty()) {
                                Text(
                                    text = "model-slug...",
                                    color = palette.textMuted.copy(alpha = 0.7f),
                                    fontSize = 12.sp
                                )
                            }
                            BasicTextField(
                                value = modelInput,
                                onValueChange = { modelInput = it },
                                singleLine = true,
                                textStyle = TextStyle(color = palette.textMain, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                                cursorBrush = SolidColor(palette.textMain),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    Surface(
                        onClick = {
                            if (modelInput.isNotBlank()) {
                                onUpdateTranslatorPrefs(null, null, null, null, modelInput.trim(), null, null)
                            }
                            showModelDialog = false
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = palette.accentDark,
                        modifier = Modifier.fillMaxWidth().height(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (strings.isId) "Simpan" else "Save",
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

    // CUSTOM ENDPOINT BASE URL MODAL
    if (showCustomUrlDialog) {
        var urlInput by remember(prefs.aiCustomBaseUrl) { mutableStateOf(prefs.aiCustomBaseUrl) }
        Dialog(onDismissRequest = { showCustomUrlDialog = false }) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = palette.sheetBg,
                border = BorderStroke(1.dp, palette.borderSoft),
                shadowElevation = 24.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Endpoint Base URL",
                            color = palette.textMain,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium
                        )
                        Surface(
                            onClick = { showCustomUrlDialog = false },
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
                        text = if (strings.isId) "Masukkan base URL endpoint OpenAI-compatible (misal https://openrouter.ai/api/v1 atau http://localhost:11434/v1):"
                        else "Enter base URL for OpenAI-compatible endpoint (e.g. https://openrouter.ai/api/v1 or http://localhost:11434/v1):",
                        color = palette.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            BasicTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                singleLine = true,
                                textStyle = TextStyle(color = palette.textMain, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
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
                            onClick = { urlInput = "https://openrouter.ai/api/v1" },
                            shape = RoundedCornerShape(12.dp),
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Reset Default",
                                    color = palette.textMain,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                if (urlInput.isNotBlank()) {
                                    onUpdateTranslatorPrefs(null, null, null, null, null, urlInput.trim(), null)
                                }
                                showCustomUrlDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = palette.accentDark,
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (strings.isId) "Simpan" else "Save",
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

    // SYSTEM PROMPT MODAL
    if (showSystemPromptDialog) {
        var promptInput by remember(prefs.aiSystemPrompt) { mutableStateOf(prefs.aiSystemPrompt) }
        val promptLightNovel = DEFAULT_AI_TRANSLATOR_PROMPT
        val promptAcademic = "You are a professional academic and non-fiction translator. Translate the text into {{TARGET_LANG}} with precision, objective tone, and accurate terminology. Preserve original paragraph structure and citations. Output ONLY the translated text without commentary."
        val promptNatural = "You are a skilled translator. Translate the text into natural, modern, concise {{TARGET_LANG}}. Maintain clarity and readable everyday flow while preserving paragraph breaks. Output ONLY the translated text."

        Dialog(onDismissRequest = { showSystemPromptDialog = false }) {
            Surface(
                shape = RoundedCornerShape(26.dp),
                color = palette.sheetBg,
                border = BorderStroke(1.dp, palette.borderSoft),
                shadowElevation = 24.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (strings.isId) "Prompt Sistem AI" else "AI System Prompt",
                            color = palette.textMain,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Medium
                        )
                        Surface(
                            onClick = { showSystemPromptDialog = false },
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
                        text = if (strings.isId) "Gunakan {{TARGET_LANG}} sebagai variabel bahasa tujuan otomatis. Pilih gaya preset:"
                        else "Use {{TARGET_LANG}} as the target language variable. Choose a style preset:",
                        color = palette.textMuted,
                        fontSize = 12.sp,
                        lineHeight = 17.sp
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val chip1 = if (strings.isId) "Light Novel / Fiksi" else "Light Novel / Fiction"
                        val chip2 = if (strings.isId) "Akademik" else "Academic"
                        val chip3 = if (strings.isId) "Natural Ringkas" else "Natural Concise"

                        Surface(
                            onClick = { promptInput = promptLightNovel },
                            shape = RoundedCornerShape(8.dp),
                            color = if (promptInput == promptLightNovel) palette.accentDark else palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    text = chip1,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (promptInput == promptLightNovel) palette.bgApp else palette.textMain
                                )
                            }
                        }

                        Surface(
                            onClick = { promptInput = promptAcademic },
                            shape = RoundedCornerShape(8.dp),
                            color = if (promptInput == promptAcademic) palette.accentDark else palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    text = chip2,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (promptInput == promptAcademic) palette.bgApp else palette.textMain
                                )
                            }
                        }

                        Surface(
                            onClick = { promptInput = promptNatural },
                            shape = RoundedCornerShape(8.dp),
                            color = if (promptInput == promptNatural) palette.accentDark else palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 6.dp)) {
                                Text(
                                    text = chip3,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (promptInput == promptNatural) palette.bgApp else palette.textMain
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.fillMaxWidth().height(140.dp)
                    ) {
                        Box(modifier = Modifier.padding(12.dp)) {
                            BasicTextField(
                                value = promptInput,
                                onValueChange = { promptInput = it },
                                textStyle = TextStyle(color = palette.textMain, fontSize = 12.sp, lineHeight = 17.sp),
                                cursorBrush = SolidColor(palette.textMain),
                                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = { promptInput = DEFAULT_AI_TRANSLATOR_PROMPT },
                            shape = RoundedCornerShape(12.dp),
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft),
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Reset Default",
                                    color = palette.textMain,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                if (promptInput.isNotBlank()) {
                                    onUpdateTranslatorPrefs(null, null, null, null, null, null, promptInput.trim())
                                }
                                showSystemPromptDialog = false
                            },
                            shape = RoundedCornerShape(12.dp),
                            color = palette.accentDark,
                            modifier = Modifier.weight(1f).height(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (strings.isId) "Simpan" else "Save",
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
                                    val capturedScale = cropScale
                                    val capturedOffset = cropOffset
                                    val capturedRotation = cropRotationDegrees
                                    coroutineScope.launch {
                                        val savedPath = withContext(Dispatchers.IO) {
                                            runCatching {
                                                val out = renderCroppedBitmapWithExactMatrix(
                                                    src = cropBmp,
                                                    outputSizePx = 512,
                                                    stageSizePx = stageSizePx,
                                                    zoom = capturedScale,
                                                    clampedPanStagePx = capturedOffset,
                                                    rotationDeg = capturedRotation,
                                                    circularMask = true
                                                )
                                                val dir = File(context.filesDir, "profile").apply { mkdirs() }
                                                val outFile = File(dir, "avatar_${System.currentTimeMillis()}.jpg")
                                                FileOutputStream(outFile).use { fos ->
                                                    out.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                                                }
                                                dir.listFiles()?.forEach { existing ->
                                                    if (existing != outFile && existing.name.startsWith("avatar_")) {
                                                        runCatching { existing.delete() }
                                                    }
                                                }
                                                outFile.absolutePath
                                            }.getOrNull()
                                        }
                                        if (savedPath != null) {
                                            onSavePhotoPath(savedPath)
                                        }
                                        pendingCropBitmap = null
                                    }
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
