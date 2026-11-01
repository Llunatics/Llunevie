package com.llunaticsys.llunevie.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.llunaticsys.llunevie.model.AppThemeMode
import com.llunaticsys.llunevie.model.LibrarySortOption
import com.llunaticsys.llunevie.model.LibraryViewMode
import com.llunaticsys.llunevie.model.PageAnimStyle
import com.llunaticsys.llunevie.model.ReadingFlowMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.llunevieDataStore: DataStore<Preferences> by preferencesDataStore(name = "llunevie_settings_v02")

data class UserPreferencesState(
    val isLoaded: Boolean = false,
    val lang: String = "id",
    val appTheme: AppThemeMode = AppThemeMode.PAPER,
    val followDeviceTheme: Boolean = false,
    val glassDesign: Boolean = false,
    val amoledBlack: Boolean = false,
    val userName: String = "",
    val userPhotoPath: String? = null,
    val readingFlowMode: ReadingFlowMode = ReadingFlowMode.PAGINATED,
    val animatePageTurn: Boolean = true,
    val pageAnimStyle: PageAnimStyle = PageAnimStyle.FLIPBOOK,
    val volumeKeysTurn: Boolean = true,
    val fontFamily: String = "Literata",
    val fontSizeSp: Int = 18,
    val fontWeight: Int = 400,
    val textAlign: String = "left",
    val lineHeightMultiplier: Float = 1.75f,
    val paraSpacingEm: Float = 1.1f,
    val letterSpacingEm: Float = 0f,
    val wordSpacingPx: Int = 0,
    val pageMarginDp: Int = 24,
    val paraIndent: Boolean = false,
    val libViewMode: LibraryViewMode = LibraryViewMode.SPINE,
    val libShelf: String = "all",
    val libFilter: String = "all",
    val libStatus: String = "all",
    val libSort: LibrarySortOption = LibrarySortOption.RECENT,
    val libGridCols: Int = 0,
    val homeRecentMode: String = "grid",
    val hasCompletedOnboarding: Boolean = false,
    val autoImportFolderEnabled: Boolean = false,
    val autoImportFolderPath: String = "/sdcard/Llunevie/Books",
    val autoImportFolderUri: String? = null,
    val importedFileSignatures: Set<String> = emptySet(),
    val pdfThemeTintEnabled: Boolean = true,
    val pdfProtectIllustrations: Boolean = true
) {
    val lineHeightMult: Float get() = lineHeightMultiplier
    val flowMode: ReadingFlowMode get() = readingFlowMode
    val pageMargin: Int get() = when { pageMarginDp <= 16 -> 0; pageMarginDp <= 28 -> 1; else -> 2 }

    fun effectiveTheme(isSystemDark: Boolean): AppThemeMode {
        return if (followDeviceTheme) {
            if (isSystemDark) AppThemeMode.BLACK else AppThemeMode.WHITE
        } else {
            appTheme
        }
    }
}

class UserPreferencesRepository(private val context: Context) {

    private val bootCache = context.getSharedPreferences("llunevie_boot_cache", Context.MODE_PRIVATE)

    fun getInitialBootState(): UserPreferencesState {
        if (!bootCache.contains("has_completed_onboarding")) {
            return UserPreferencesState(isLoaded = false)
        }
        val hasCompleted = bootCache.getBoolean("has_completed_onboarding", false)
        val themeKey = bootCache.getString("app_theme", null)
        val theme = AppThemeMode.fromKey(themeKey)
        val followDevice = bootCache.getBoolean("follow_device_theme", false)
        val amoled = bootCache.getBoolean("amoled_black", false)
        val lang = bootCache.getString("lang", "id") ?: "id"
        return UserPreferencesState(
            isLoaded = true,
            hasCompletedOnboarding = hasCompleted,
            appTheme = theme,
            followDeviceTheme = followDevice,
            amoledBlack = if (theme == AppThemeMode.BLACK) amoled else false,
            lang = lang
        )
    }

    private object Keys {
        val LANG = stringPreferencesKey("lang")
        val APP_THEME = stringPreferencesKey("app_theme")
        val FOLLOW_DEVICE_THEME = booleanPreferencesKey("follow_device_theme")
        val GLASS_DESIGN = booleanPreferencesKey("glass_design")
        val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
        val USER_NAME = stringPreferencesKey("user_name")
        val USER_PHOTO = stringPreferencesKey("user_photo")
        val READING_FLOW = stringPreferencesKey("reading_flow_mode")
        val ANIMATE_PAGE = booleanPreferencesKey("animate_page_turn")
        val PAGE_ANIM_STYLE = stringPreferencesKey("page_anim_style")
        val VOLUME_KEYS = booleanPreferencesKey("volume_keys_turn")
        val FONT_FAMILY = stringPreferencesKey("font_family")
        val FONT_SIZE = intPreferencesKey("font_size_sp")
        val FONT_WEIGHT = intPreferencesKey("font_weight")
        val TEXT_ALIGN = stringPreferencesKey("text_align")
        val LINE_HEIGHT = floatPreferencesKey("line_height")
        val PARA_SPACING = floatPreferencesKey("para_spacing")
        val LETTER_SPACING = floatPreferencesKey("letter_spacing")
        val WORD_SPACING = intPreferencesKey("word_spacing")
        val PAGE_MARGIN = intPreferencesKey("page_margin")
        val PARA_INDENT = booleanPreferencesKey("para_indent")
        val LIB_VIEW_MODE = stringPreferencesKey("lib_view_mode")
        val LIB_SHELF = stringPreferencesKey("lib_shelf")
        val LIB_FILTER = stringPreferencesKey("lib_filter")
        val LIB_STATUS = stringPreferencesKey("lib_status")
        val LIB_SORT = stringPreferencesKey("lib_sort")
        val LIB_GRID_COLS = intPreferencesKey("lib_grid_cols")
        val HOME_RECENT_MODE = stringPreferencesKey("home_recent_mode")
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val AUTO_IMPORT_ENABLED = booleanPreferencesKey("auto_import_folder_enabled")
        val AUTO_IMPORT_PATH = stringPreferencesKey("auto_import_folder_path")
        val AUTO_IMPORT_URI = stringPreferencesKey("auto_import_folder_uri")
        val IMPORTED_FILE_SIGNATURES = stringSetPreferencesKey("imported_file_signatures")
        val PDF_THEME_TINT_ENABLED = booleanPreferencesKey("pdf_theme_tint_enabled")
        val PDF_PROTECT_ILLUSTRATIONS = booleanPreferencesKey("pdf_protect_illustrations")
    }

    val preferencesFlow: Flow<UserPreferencesState> = context.llunevieDataStore.data.map { prefs ->
        val theme = AppThemeMode.fromKey(prefs[Keys.APP_THEME])
        val rawAmoled = prefs[Keys.AMOLED_BLACK] ?: false
        val completed = prefs[Keys.HAS_COMPLETED_ONBOARDING] ?: false
        val followDevice = prefs[Keys.FOLLOW_DEVICE_THEME] ?: false
        val effectiveAmoled = if (theme == AppThemeMode.BLACK) rawAmoled else false
        val lang = prefs[Keys.LANG] ?: "id"

        bootCache.edit()
            .putBoolean("has_completed_onboarding", completed)
            .putString("app_theme", theme.key)
            .putBoolean("follow_device_theme", followDevice)
            .putBoolean("amoled_black", effectiveAmoled)
            .putString("lang", lang)
            .apply()

        UserPreferencesState(
            isLoaded = true,
            lang = lang,
            appTheme = theme,
            followDeviceTheme = followDevice,
            glassDesign = false,
            amoledBlack = effectiveAmoled,
            userName = prefs[Keys.USER_NAME] ?: "",
            userPhotoPath = prefs[Keys.USER_PHOTO],
            readingFlowMode = ReadingFlowMode.fromKey(prefs[Keys.READING_FLOW]),
            animatePageTurn = prefs[Keys.ANIMATE_PAGE] ?: true,
            pageAnimStyle = PageAnimStyle.fromKey(prefs[Keys.PAGE_ANIM_STYLE]),
            volumeKeysTurn = prefs[Keys.VOLUME_KEYS] ?: true,
            fontFamily = prefs[Keys.FONT_FAMILY] ?: "Literata",
            fontSizeSp = prefs[Keys.FONT_SIZE] ?: 18,
            fontWeight = prefs[Keys.FONT_WEIGHT] ?: 400,
            textAlign = prefs[Keys.TEXT_ALIGN] ?: "left",
            lineHeightMultiplier = prefs[Keys.LINE_HEIGHT] ?: 1.75f,
            paraSpacingEm = prefs[Keys.PARA_SPACING] ?: 1.1f,
            letterSpacingEm = prefs[Keys.LETTER_SPACING] ?: 0f,
            wordSpacingPx = prefs[Keys.WORD_SPACING] ?: 0,
            pageMarginDp = prefs[Keys.PAGE_MARGIN] ?: 24,
            paraIndent = prefs[Keys.PARA_INDENT] ?: false,
            libViewMode = LibraryViewMode.fromKey(prefs[Keys.LIB_VIEW_MODE]),
            libShelf = prefs[Keys.LIB_SHELF] ?: "all",
            libFilter = prefs[Keys.LIB_FILTER] ?: "all",
            libStatus = prefs[Keys.LIB_STATUS] ?: "all",
            libSort = LibrarySortOption.fromKey(prefs[Keys.LIB_SORT]),
            libGridCols = prefs[Keys.LIB_GRID_COLS] ?: 0,
            homeRecentMode = prefs[Keys.HOME_RECENT_MODE] ?: "grid",
            hasCompletedOnboarding = completed,
            autoImportFolderEnabled = prefs[Keys.AUTO_IMPORT_ENABLED] ?: false,
            autoImportFolderPath = prefs[Keys.AUTO_IMPORT_PATH] ?: "/sdcard/Llunevie/Books",
            autoImportFolderUri = prefs[Keys.AUTO_IMPORT_URI],
            importedFileSignatures = prefs[Keys.IMPORTED_FILE_SIGNATURES] ?: emptySet(),
            pdfThemeTintEnabled = prefs[Keys.PDF_THEME_TINT_ENABLED] ?: true,
            pdfProtectIllustrations = prefs[Keys.PDF_PROTECT_ILLUSTRATIONS] ?: true
        )
    }

    suspend fun setTheme(theme: AppThemeMode) {
        bootCache.edit()
            .putString("app_theme", theme.key)
            .putBoolean("follow_device_theme", false)
            .apply()
        if (theme != AppThemeMode.BLACK) {
            bootCache.edit().putBoolean("amoled_black", false).apply()
        }
        context.llunevieDataStore.edit { prefs ->
            prefs[Keys.APP_THEME] = theme.key
            prefs[Keys.FOLLOW_DEVICE_THEME] = false
            if (theme != AppThemeMode.BLACK) {
                prefs[Keys.AMOLED_BLACK] = false
            }
        }
    }

    suspend fun setFollowDeviceTheme(enabled: Boolean) {
        bootCache.edit().putBoolean("follow_device_theme", enabled).apply()
        context.llunevieDataStore.edit { prefs ->
            prefs[Keys.FOLLOW_DEVICE_THEME] = enabled
        }
    }

    suspend fun setGlassDesign(enabled: Boolean) {
        context.llunevieDataStore.edit { prefs ->
            prefs[Keys.GLASS_DESIGN] = false
        }
    }

    suspend fun setAmoledBlack(enabled: Boolean) {
        bootCache.edit().putBoolean("amoled_black", enabled).apply()
        if (enabled) {
            bootCache.edit().putString("app_theme", AppThemeMode.BLACK.key).apply()
        }
        context.llunevieDataStore.edit { prefs ->
            prefs[Keys.AMOLED_BLACK] = enabled
            if (enabled) {
                prefs[Keys.APP_THEME] = AppThemeMode.BLACK.key
            }
        }
    }

    suspend fun setLanguage(lang: String) {
        val safeLang = if (lang == "en") "en" else "id"
        bootCache.edit().putString("lang", safeLang).apply()
        context.llunevieDataStore.edit { it[Keys.LANG] = safeLang }
    }

    suspend fun setUserName(name: String) {
        context.llunevieDataStore.edit { it[Keys.USER_NAME] = name.trim() }
    }

    suspend fun setUserPhotoPath(path: String?) {
        context.llunevieDataStore.edit {
            if (path.isNullOrBlank()) it.remove(Keys.USER_PHOTO) else it[Keys.USER_PHOTO] = path
        }
    }

    suspend fun setReadingFlowMode(mode: ReadingFlowMode) {
        context.llunevieDataStore.edit { it[Keys.READING_FLOW] = mode.key }
    }

    suspend fun setAnimatePageTurn(enabled: Boolean) {
        context.llunevieDataStore.edit { it[Keys.ANIMATE_PAGE] = enabled }
    }

    suspend fun setPageAnimStyle(style: PageAnimStyle) {
        context.llunevieDataStore.edit {
            it[Keys.PAGE_ANIM_STYLE] = style.key
            it[Keys.ANIMATE_PAGE] = true
        }
    }

    suspend fun setVolumeKeysTurn(enabled: Boolean) {
        context.llunevieDataStore.edit { it[Keys.VOLUME_KEYS] = enabled }
    }

    suspend fun updateReaderTypography(
        fontFamily: String? = null,
        fontSizeSp: Int? = null,
        fontWeight: Int? = null,
        textAlign: String? = null,
        lineHeight: Float? = null,
        paraSpacing: Float? = null,
        letterSpacing: Float? = null,
        wordSpacing: Int? = null,
        pageMargin: Int? = null,
        paraIndent: Boolean? = null
    ) {
        context.llunevieDataStore.edit { prefs ->
            fontFamily?.let { prefs[Keys.FONT_FAMILY] = it }
            fontSizeSp?.let { prefs[Keys.FONT_SIZE] = it }
            fontWeight?.let { prefs[Keys.FONT_WEIGHT] = it }
            textAlign?.let { prefs[Keys.TEXT_ALIGN] = it }
            lineHeight?.let { prefs[Keys.LINE_HEIGHT] = it }
            paraSpacing?.let { prefs[Keys.PARA_SPACING] = it }
            letterSpacing?.let { prefs[Keys.LETTER_SPACING] = it }
            wordSpacing?.let { prefs[Keys.WORD_SPACING] = it }
            pageMargin?.let { prefs[Keys.PAGE_MARGIN] = it }
            paraIndent?.let { prefs[Keys.PARA_INDENT] = it }
        }
    }

    suspend fun updateLibraryViewPrefs(
        viewMode: LibraryViewMode? = null,
        shelf: String? = null,
        filter: String? = null,
        status: String? = null,
        sort: LibrarySortOption? = null,
        gridCols: Int? = null,
        homeRecentMode: String? = null
    ) {
        context.llunevieDataStore.edit { prefs ->
            viewMode?.let { prefs[Keys.LIB_VIEW_MODE] = it.key }
            shelf?.let { prefs[Keys.LIB_SHELF] = it }
            filter?.let { prefs[Keys.LIB_FILTER] = it }
            status?.let { prefs[Keys.LIB_STATUS] = it }
            sort?.let { prefs[Keys.LIB_SORT] = it.key }
            gridCols?.let { prefs[Keys.LIB_GRID_COLS] = it }
            homeRecentMode?.let { prefs[Keys.HOME_RECENT_MODE] = it }
        }
    }

    suspend fun setHasCompletedOnboarding(completed: Boolean) {
        bootCache.edit().putBoolean("has_completed_onboarding", completed).apply()
        context.llunevieDataStore.edit { it[Keys.HAS_COMPLETED_ONBOARDING] = completed }
    }

    suspend fun setAutoImportFolderEnabled(enabled: Boolean) {
        context.llunevieDataStore.edit { it[Keys.AUTO_IMPORT_ENABLED] = enabled }
    }

    suspend fun setAutoImportFolder(path: String, uri: String? = null) {
        context.llunevieDataStore.edit {
            it[Keys.AUTO_IMPORT_PATH] = path.trim()
            if (uri.isNullOrBlank()) {
                it.remove(Keys.AUTO_IMPORT_URI)
            } else {
                it[Keys.AUTO_IMPORT_URI] = uri
            }
        }
    }

    suspend fun addImportedSignatures(signatures: Set<String>) {
        context.llunevieDataStore.edit { prefs ->
            val current = prefs[Keys.IMPORTED_FILE_SIGNATURES] ?: emptySet()
            prefs[Keys.IMPORTED_FILE_SIGNATURES] = current + signatures
        }
    }

    suspend fun setPdfThemeTintEnabled(enabled: Boolean) {
        context.llunevieDataStore.edit { it[Keys.PDF_THEME_TINT_ENABLED] = enabled }
    }

    suspend fun setPdfProtectIllustrations(enabled: Boolean) {
        context.llunevieDataStore.edit { it[Keys.PDF_PROTECT_ILLUSTRATIONS] = enabled }
    }
}
