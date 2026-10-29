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
    val createdAtMillis: Long = System.currentTimeMillis()
)

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
    val createdAtMillis: Long = System.currentTimeMillis()
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
