package com.llunaticsys.llunevie.model

import com.llunaticsys.llunevie.data.remote.TranslationRepository

/**
 * Single Source of Truth for the 4 Reading Flow Modes (Section 1 & Section 8).
 * Used identically in both SettingsScreen and ReaderScreen (Tab ALUR / FLOW).
 */
enum class ReadingFlowMode(
    val key: String,
    val labelId: String,
    val labelEn: String,
    val descId: String,
    val descEn: String
) {
    PAGINATED(
        key = "paginated",
        labelId = "Page Turn",
        labelEn = "Page Turn",
        descId = "1 halaman, geser kiri/kanan",
        descEn = "Single page, swipe left/right"
    ),
    SCROLL(
        key = "scroll",
        labelId = "Vertical Scroll",
        labelEn = "Vertical Scroll",
        descId = "Gulir vertikal berkelanjutan",
        descEn = "Continuous vertical scroll"
    ),
    WEBTOON(
        key = "webtoon",
        labelId = "Webtoon",
        labelEn = "Webtoon",
        descId = "Mulus tanpa jeda bab",
        descEn = "Seamless strip without breaks"
    ),
    SPREAD(
        key = "spread",
        labelId = "2 Halaman",
        labelEn = "Two Pages",
        descId = "Bentangan buku 2 halaman",
        descEn = "Two-page spread landscape"
    );

    fun localizedLabel(lang: String): String = if (lang == "en") labelEn else labelId
    fun localizedDesc(lang: String): String = if (lang == "en") descEn else descId

    companion object {
        fun fromKey(key: String?): ReadingFlowMode =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: PAGINATED
    }
}

/**
 * Single Source of Truth for the 3 Page Animation Styles (Section 1 & Section 8).
 * Labels are strictly identical in ID and EN: "Flipbook", "Slide", "Fade".
 */
enum class PageAnimStyle(val key: String, val label: String) {
    FLIPBOOK("flipbook", "Flipbook"),
    SLIDE("slide", "Slide"),
    FADE("fade", "Fade");

    companion object {
        fun fromKey(key: String?): PageAnimStyle =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: FLIPBOOK
    }
}

enum class AppThemeMode(val key: String, val displayLabel: String) {
    PAPER("paper", "Paper"),
    WHITE("white", "White"),
    SEPIA("sepia", "Sepia"),
    BLACK("black", "Black");

    companion object {
        fun fromKey(key: String?): AppThemeMode =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: PAPER
    }
}

enum class LibraryViewMode(val key: String) {
    SPINE("spine"),
    GRID("grid"),
    COVERS("covers"),
    LIST("list");

    companion object {
        fun fromKey(key: String?): LibraryViewMode =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: SPINE
    }
}

enum class LibrarySortOption(val key: String, val labelId: String, val labelEn: String) {
    RECENT("recent", "Terbaru", "Newest"),
    TITLE("title", "Judul A–Z", "Title A–Z"),
    AUTHOR("author", "Penulis", "Author"),
    PROGRESS("progress", "Progres", "Progress");

    fun localizedLabel(lang: String): String = if (lang == "en") labelEn else labelId

    companion object {
        fun fromKey(key: String?): LibrarySortOption =
            entries.find { it.key.equals(key, ignoreCase = true) } ?: RECENT
    }
}

enum class HighlightColorKey(val key: String, val label: String) {
    GOLD("gold", "Lemon"),
    PEACH("peach", "Peach"),
    SKY("sky", "Sky"),
    MINT("mint", "Mint"),
    LILAC("lilac", "Lilac"),
    APRICOT("apricot", "Apricot"),
    AQUA("aqua", "Aqua"),
    PINK("pink", "Pink");

    companion object {
        fun fromKey(key: String?): HighlightColorKey = when (key?.lowercase()) {
            "gold" -> GOLD
            "peach", "rose" -> PEACH
            "sky" -> SKY
            "mint", "sage" -> MINT
            "lilac" -> LILAC
            "apricot" -> APRICOT
            "aqua" -> AQUA
            "pink" -> PINK
            else -> GOLD
        }
    }
}

data class BookIllustration(
    val uriOrPath: String,
    val caption: String,
    val pageIndexInBook: Int = 0
)

data class BookChapter(
    val index: Int,
    val title: String,
    val pages: List<String>
)

enum class BatchLookupStatus {
    SEARCHING,
    FOUND,
    NOT_FOUND
}

data class CatalogBookCandidate(
    val id: String,
    val title: String,
    val author: String,
    val year: String,
    val pages: Int,
    val rating: String,
    val edition: String,
    val publisher: String,
    val lang: String,
    val isbn: String,
    val category: String,
    val coverUrl: String?,
    val coverBgHex: Long,
    val synopsis: String,
    val heightCm: Float = 20f,
    val widthCm: Float = 14f,
    val lengthCm: Float = 20f,
    val weightGrams: Float = 300f,
    val status: BatchLookupStatus = BatchLookupStatus.FOUND
)

data class TranslationLangOption(
    val code: String,          // API language code: "id", "en", "ja", "ko", "zh-CN", etc.
    val badgeCode: String,     // Short badge in Top Pill: "ID", "EN", "JA", "KO", "CN", etc.
    val nativeName: String,    // Native name: "Bahasa Indonesia", "English", "日本語", "한국어", "简体中文", etc.
    val isDefault: Boolean = false
) {
    // Formatted display label for dropdowns & settings: e.g. "ja (日本語)", "cn (简体中文)"
    val dropdownLabel: String get() = "${badgeCode.lowercase()} ($nativeName)"
    val settingsLabel: String get() = "${badgeCode.uppercase()} ($nativeName)"
}

val DEFAULT_TRANSLATION_LANGUAGES = listOf(
    TranslationLangOption(code = "id", badgeCode = "ID", nativeName = "Bahasa Indonesia", isDefault = true),
    TranslationLangOption(code = "en", badgeCode = "EN", nativeName = "English", isDefault = true),
    TranslationLangOption(code = "ja", badgeCode = "JA", nativeName = "日本語", isDefault = true),
    TranslationLangOption(code = "ko", badgeCode = "KO", nativeName = "한국어", isDefault = true),
    TranslationLangOption(code = "zh-CN", badgeCode = "CN", nativeName = "简体中文", isDefault = true)
)

val EXTRA_TRANSLATION_LANGUAGES = listOf(
    TranslationLangOption(code = "zh-TW", badgeCode = "TW", nativeName = "繁體中文"),
    TranslationLangOption(code = "es", badgeCode = "ES", nativeName = "Español"),
    TranslationLangOption(code = "fr", badgeCode = "FR", nativeName = "Français"),
    TranslationLangOption(code = "de", badgeCode = "DE", nativeName = "Deutsch"),
    TranslationLangOption(code = "ru", badgeCode = "RU", nativeName = "Русский"),
    TranslationLangOption(code = "ar", badgeCode = "AR", nativeName = "العربية"),
    TranslationLangOption(code = "pt", badgeCode = "PT", nativeName = "Português"),
    TranslationLangOption(code = "it", badgeCode = "IT", nativeName = "Italiano"),
    TranslationLangOption(code = "th", badgeCode = "TH", nativeName = "ไทย"),
    TranslationLangOption(code = "vi", badgeCode = "VI", nativeName = "Tiếng Việt"),
    TranslationLangOption(code = "ms", badgeCode = "MS", nativeName = "Bahasa Melayu"),
    TranslationLangOption(code = "tr", badgeCode = "TR", nativeName = "Türkçe"),
    TranslationLangOption(code = "hi", badgeCode = "HI", nativeName = "हिन्दी"),
    TranslationLangOption(code = "nl", badgeCode = "NL", nativeName = "Nederlands")
)

fun normalizeTranslationLangCode(rawLang: String): String {
    return when (rawLang.trim().lowercase()) {
        "id", "in", "indonesian", "bahasa indonesia" -> "id"
        "en", "eng", "english" -> "en"
        "ja", "jp", "jpn", "japanese" -> "ja"
        "ko", "kr", "kor", "korean" -> "ko"
        "cn", "zh", "zh-cn", "chinese" -> "zh-CN"
        "tw", "zh-tw" -> "zh-TW"
        "es", "fr", "de", "ru", "ar", "pt", "it", "th", "vi", "ms", "tr", "hi", "nl" -> rawLang.trim().lowercase()
        else -> "en"
    }
}

private val ROMAN_NUMERAL_REGEX = Regex("^(?:I|II|III|IV|V|VI|VII|VIII|IX|X|XI|XII|XIII|XIV|XV|XVI|XVII|XVIII|XIX|XX)$")

fun isOrphanParagraphFragment(para: String): Boolean {
    val t = para.trim()
    if (t.isEmpty()) return true
    if (t.startsWith("[ILLUST:") || t.startsWith("[PDF_PAGE:")) return false
    // A bare 1-2 character pure ASCII letter token with no punctuation (e.g. "BU", "b")
    // that is not a Roman numeral chapter heading is an OCR/pagination split artifact
    if (t.length in 1..2 && t.all { it in 'A'..'Z' || it in 'a'..'z' } && !ROMAN_NUMERAL_REGEX.matches(t)) {
        return true
    }
    return false
}

fun extractCleanPageParagraphs(pageText: String): List<String> {
    val raw = pageText.split(Regex("\n\\s*\n"))
        .map { it.trim() }
        .filter { it.isNotBlank() }
    if (raw.size <= 1) return raw
    val filtered = raw.filterNot { isOrphanParagraphFragment(it) }
    return filtered.ifEmpty { raw }
}

fun isPageTranslationComplete(pairs: List<Pair<String, String?>>?, targetLang: String = ""): Boolean {
    if (pairs.isNullOrEmpty()) return false
    val translatable = pairs.filter { (orig, _) ->
        !orig.startsWith("[ILLUST:") &&
            !orig.startsWith("[PDF_PAGE:") &&
            orig.any { it.isLetter() } &&
            (pairs.size <= 1 || !isOrphanParagraphFragment(orig))
    }
    if (translatable.isEmpty()) return false
    return translatable.all { (orig, trans) ->
        !trans.isNullOrBlank() && !TranslationRepository.isInvalidOrUnchangedTranslation(orig, trans, targetLang)
    }
}

fun getActiveTranslationLanguages(customCodes: Set<String>): List<TranslationLangOption> {
    val normCustom = customCodes.map { normalizeTranslationLangCode(it) }.toSet()
    val extras = EXTRA_TRANSLATION_LANGUAGES.filter { normalizeTranslationLangCode(it.code) in normCustom }
    return DEFAULT_TRANSLATION_LANGUAGES + extras
}
