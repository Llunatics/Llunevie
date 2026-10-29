package com.llunaticsys.llunevie.model

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
