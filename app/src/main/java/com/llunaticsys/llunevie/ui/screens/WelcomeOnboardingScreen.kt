package com.llunaticsys.llunevie.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.llunaticsys.llunevie.data.local.UserPreferencesState
import com.llunaticsys.llunevie.model.AppThemeMode
import com.llunaticsys.llunevie.ui.components.ModernSwitch
import com.llunaticsys.llunevie.ui.theme.LocalLluneviePalette

@Composable
fun WelcomeOnboardingScreen(
    prefs: UserPreferencesState,
    onSetLanguage: (String) -> Unit,
    onSetTheme: (AppThemeMode) -> Unit,
    onComplete: (autoImportEnabled: Boolean, path: String, uri: String?) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalLluneviePalette.current
    val context = LocalContext.current
    val isEn = prefs.lang == "en"

    var autoImportEnabled by remember { mutableStateOf(true) }
    var selectedPath by remember { mutableStateOf("/sdcard/Llunevie/Books") }
    var selectedUri by remember { mutableStateOf<String?>(null) }

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
            selectedUri = uri.toString()
            selectedPath = resolveDisplayFolderPath(uri)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.bgApp)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // 1. Top Brand Header
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.bgCard)
                    .border(1.dp, palette.borderSoft, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ll",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = palette.textPrimary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = if (isEn) "Welcome to Llunevie" else "Selamat datang di Llunevie",
                fontSize = 28.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                color = palette.textPrimary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isEn) "A quiet, tactile space for your novels, light novels, and personal book collection."
                else "Ruang baca yang tenang dan nyaman untuk novel, light novel, dan koleksi bukumu.",
                fontSize = 13.5.sp,
                lineHeight = 20.sp,
                color = palette.textSecondary
            )

            Spacer(modifier = Modifier.height(28.dp))

            // 2. Center Setup Card — Watched Books Folder (`Llunevie/Books` by Default)
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = palette.bgCard,
                border = BorderStroke(1.dp, palette.borderSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Card Header Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f).padding(end = 12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(palette.bgSurface)
                                    .border(1.dp, palette.borderSoft, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    tint = palette.textPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                Text(
                                    text = if (isEn) "Automatic Books Folder" else "Folder Buku Otomatis",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textPrimary
                                )
                                Text(
                                    text = if (isEn) "Auto-import new EPUB, PDF & TXT files" else "Impor otomatis file EPUB, PDF & TXT baru",
                                    fontSize = 11.5.sp,
                                    color = palette.textSecondary
                                )
                            }
                        }

                        ModernSwitch(
                            checked = autoImportEnabled,
                            onCheckedChange = { autoImportEnabled = it }
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Folder Path Selector Box
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = palette.bgSurface,
                        border = BorderStroke(1.dp, palette.borderSoft),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                                Text(
                                    text = if (isEn) "MAIN FOLDER" else "FOLDER UTAMA",
                                    fontSize = 9.sp,
                                    letterSpacing = 1.2.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = palette.textMuted
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = selectedPath,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = palette.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Surface(
                                onClick = { folderPickerLauncher.launch(null) },
                                shape = CircleShape,
                                color = palette.bgCard,
                                border = BorderStroke(1.dp, palette.borderSoft),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isEn) "Change" else "Ubah",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = palette.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // 3. Compact Language & Theme Quick-Select Card
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = palette.bgCard,
                border = BorderStroke(1.dp, palette.borderSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Language Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEn) "Language" else "Bahasa",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.textSecondary
                        )
                        Surface(
                            shape = CircleShape,
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft)
                        ) {
                            Row(modifier = Modifier.padding(3.dp)) {
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
                                            color = if (active) palette.textPrimary else palette.textMuted,
                                            fontSize = 11.sp,
                                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Theme Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isEn) "Theme" else "Tema",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = palette.textSecondary
                        )
                        Surface(
                            shape = CircleShape,
                            color = palette.bgSurface,
                            border = BorderStroke(1.dp, palette.borderSoft)
                        ) {
                            Row(modifier = Modifier.padding(3.dp)) {
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
                                            color = if (active) palette.textPrimary else palette.textMuted,
                                            fontSize = 11.sp,
                                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f, fill = false))
            Spacer(modifier = Modifier.height(28.dp))

            // 4. Bottom Action Buttons
            Surface(
                onClick = {
                    onComplete(autoImportEnabled, selectedPath, selectedUri)
                },
                shape = CircleShape,
                color = palette.textPrimary,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (isEn) "Use Folder & Start Reading" else "Gunakan Folder & Mulai Membaca",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.bgApp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSkip() }
                    .padding(top = 10.dp, bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isEn) "Skip for now (configure later in Settings)" else "Lewati dulu (atur nanti di Pengaturan)",
                    fontSize = 12.sp,
                    color = palette.textMuted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
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
