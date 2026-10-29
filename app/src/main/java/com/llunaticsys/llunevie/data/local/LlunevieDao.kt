package com.llunaticsys.llunevie.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LlunevieDao {

    @Query("SELECT * FROM books ORDER BY createdAtMillis DESC")
    fun observeAllBooks(): Flow<List<BookEntity>>

    @Query("SELECT * FROM books")
    suspend fun getAllBooks(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBookById(bookId: String): BookEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBook(book: BookEntity)

    @Update
    suspend fun updateBook(book: BookEntity)

    @Query("DELETE FROM books WHERE id IN (:bookIds)")
    suspend fun deleteBooksByIds(bookIds: List<String>)

    @Query("UPDATE books SET shelf = :fallbackShelf WHERE shelf = :deletedShelf")
    suspend fun moveBooksFromDeletedShelf(deletedShelf: String, fallbackShelf: String = "eReader")

    @Query("SELECT * FROM book_chapters WHERE bookId = :bookId ORDER BY chapterIndex ASC")
    suspend fun getChaptersForBook(bookId: String): List<BookChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<BookChapterEntity>)

    @Query("DELETE FROM book_chapters WHERE bookId = :bookId")
    suspend fun deleteChaptersForBook(bookId: String)

    @Transaction
    suspend fun saveBookWithChapters(book: BookEntity, chapters: List<BookChapterEntity>) {
        upsertBook(book)
        deleteChaptersForBook(book.id)
        if (chapters.isNotEmpty()) {
            insertChapters(chapters)
        }
    }

    @Query("SELECT * FROM highlight_notes WHERE bookId = :bookId ORDER BY createdAtMillis DESC")
    fun observeHighlightsForBook(bookId: String): Flow<List<HighlightNoteEntity>>

    @Query("SELECT * FROM highlight_notes ORDER BY createdAtMillis DESC")
    fun observeAllHighlights(): Flow<List<HighlightNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertHighlight(highlight: HighlightNoteEntity)

    @Query("DELETE FROM highlight_notes WHERE id = :highlightId")
    suspend fun deleteHighlightById(highlightId: String)

    @Query("SELECT * FROM daily_reading_logs ORDER BY dateKey ASC")
    fun observeDailyLogs(): Flow<List<DailyReadingLogEntity>>

    @Query("SELECT * FROM daily_reading_logs WHERE dateKey = :dateKey LIMIT 1")
    suspend fun getDailyLog(dateKey: String): DailyReadingLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyLog(log: DailyReadingLogEntity)

    @Query("SELECT * FROM shelves ORDER BY sortOrder ASC")
    fun observeShelves(): Flow<List<ShelfEntity>>

    @Query("SELECT * FROM shelves ORDER BY sortOrder ASC")
    suspend fun getAllShelvesOnce(): List<ShelfEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertShelf(shelf: ShelfEntity)

    @Query("DELETE FROM shelves WHERE name = :shelfName AND isDefault = 0")
    suspend fun deleteCustomShelf(shelfName: String)
}
