package com.llunaticsys.llunevie.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val title: String,
    val author: String,
    val year: String,
    val publisher: String,
    val edition: String,
    val isbn: String,
    val category: String,
    val shelf: String,
    val format: String,
    val pagesCount: Int,
    val addedDate: String,
    val progress: Int,
    val completedAllPages: Boolean,
    val onTheGo: Boolean,
    val timeMinutes: Int,
    val readingSeconds: Int,
    val savedChapterIndex: Int,
    val savedPageIndex: Int,
    val bookmarked: Boolean,
    val localFilePath: String?,
    val coverImagePath: String?,
    val spineColorHex: Long,
    val spineTextColorHex: Long,
    val synopsis: String,
    val dimensionsText: String = "",
    val heightCm: Float = 20f,
    val widthCm: Float = 14f,
    val lengthCm: Float = 20f,
    val weightGrams: Float = 300f,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val tocIndexed: Boolean = false
) {
    val readingProgress: Float
        get() = (progress / 100f).coerceIn(0f, 1f)

    fun displayAuthor(isEn: Boolean): String {
        val a = author.trim()
        val isGeneric = a.isEmpty() || a.equals("Dokumen PDF", true) || a.equals("PDF Document", true) ||
            a.equals("Berkas EPUB", true) || a.equals("EPUB Book", true) || a.equals("Penulis EPUB", true) ||
            a.equals("EPUB Author", true)
        if (!isGeneric) return a
        return when (format.uppercase()) {
            "PDF" -> if (isEn) "PDF Document" else "Dokumen PDF"
            "EPUB" -> if (isEn) "EPUB Book" else "Berkas EPUB"
            "TXT" -> if (isEn) "Text Document" else "Dokumen Teks"
            else -> if (isEn) "Physical Collection" else "Koleksi Fisik"
        }
    }

    fun displayFormatLabel(isEn: Boolean): String {
        return when (format.uppercase()) {
            "PDF" -> "PDF"
            "EPUB" -> "EPUB"
            "TXT" -> "TXT"
            else -> if (isEn) "Physical" else "Fisik"
        }
    }

    fun displayCollectionTag(isEn: Boolean): String {
        val rawShelf = shelf.trim()
        val isGenericAll = rawShelf.isEmpty() || rawShelf.equals("All", true) || rawShelf.equals("Semua", true)
        if (!isGenericAll) {
            if (rawShelf.equals("Koleksi Fisik", true) || rawShelf.equals("Physical Collection", true) || rawShelf.equals("Physical", true)) {
                return if (isEn) "Physical" else "Koleksi Fisik"
            }
            return rawShelf
        }
        val isPhys = format.equals("Physical", true) || format.equals("Fisik", true)
        return if (isPhys) (if (isEn) "Physical" else "Koleksi Fisik") else "eReader"
    }
}

fun localizeChapterTitle(title: String, isEn: Boolean): String {
    val idMatch = Regex("""Bagian\s+(\d+)\s*\((?:Hal|Halaman)\s*(\d+)[–-](\d+)\)""", RegexOption.IGNORE_CASE).find(title)
    if (idMatch != null) {
        val (num, p1, p2) = idMatch.destructured
        return if (isEn) "Section $num (pp. $p1–$p2)" else "Bagian $num (Hal $p1–$p2)"
    }
    val enMatch = Regex("""Section\s+(\d+)\s*\((?:pp\.|pages)\s*(\d+)[–-](\d+)\)""", RegexOption.IGNORE_CASE).find(title)
    if (enMatch != null) {
        val (num, p1, p2) = enMatch.destructured
        return if (isEn) "Section $num (pp. $p1–$p2)" else "Bagian $num (Hal $p1–$p2)"
    }
    return title
}

@Entity(
    tableName = "book_chapters",
    primaryKeys = ["bookId", "chapterIndex"],
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class BookChapterEntity(
    val bookId: String,
    val chapterIndex: Int,
    val title: String,
    val pagesJson: String,
    val illustrationsJson: String
)

@Entity(
    tableName = "highlight_notes",
    foreignKeys = [
        ForeignKey(
            entity = BookEntity::class,
            parentColumns = ["id"],
            childColumns = ["bookId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("bookId")]
)
data class HighlightNoteEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val pageIndex: Int,
    val chapterTitle: String,
    val quoteText: String,
    val noteText: String,
    val colorKey: String,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val startCharIndex: Int = -1,
    val endCharIndex: Int = -1
)

@Entity(tableName = "daily_reading_logs")
data class DailyReadingLogEntity(
    @PrimaryKey val dateKey: String,
    val minutesRead: Int,
    val pagesTurned: Int
)

@Entity(tableName = "shelves")
data class ShelfEntity(
    @PrimaryKey val name: String,
    val sortOrder: Int,
    val isDefault: Boolean
)
