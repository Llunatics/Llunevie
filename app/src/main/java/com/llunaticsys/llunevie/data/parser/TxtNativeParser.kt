package com.llunaticsys.llunevie.data.parser

import android.content.Context
import android.net.Uri
import com.llunaticsys.llunevie.data.local.BookChapterEntity
import com.llunaticsys.llunevie.data.local.BookEntity
import com.llunaticsys.llunevie.ui.components.normalizeBookTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TxtNativeParser {

    suspend fun importAndParseTxtWithProgress(
        context: Context,
        uri: Uri,
        fallbackFileName: String,
        targetShelf: String = "eReader",
        onInitialBookCreated: suspend (BookEntity) -> Unit = {},
        onProgressUpdate: (String, Int) -> Unit = { _, _ -> }
    ): ParsedBookBundle = withContext(Dispatchers.IO) {
        val bookId = "txt-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(6)}"
        val resolvedPath = if (uri.scheme == "file") uri.path else uri.toString()
        val cleanTitle = normalizeBookTitle(fallbackFileName)
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val yearStr = SimpleDateFormat("yyyy", Locale.US).format(Date())

        val initialBook = BookEntity(
            id = bookId,
            title = cleanTitle.ifBlank { "Dokumen Teks" },
            author = "Dokumen TXT",
            year = yearStr,
            publisher = "Local TXT",
            edition = "Teks",
            isbn = "—",
            category = "Novel",
            shelf = targetShelf,
            format = "TXT",
            pagesCount = 1,
            addedDate = todayKey,
            progress = 0,
            completedAllPages = false,
            onTheGo = true,
            timeMinutes = 0,
            readingSeconds = 0,
            savedChapterIndex = 0,
            savedPageIndex = 0,
            bookmarked = false,
            localFilePath = resolvedPath,
            coverImagePath = null,
            spineColorHex = 0xFF2D3A3AL,
            spineTextColorHex = 0xFFFAF6EBL,
            synopsis = cleanTitle
        )
        onInitialBookCreated(initialBook)
        onProgressUpdate(bookId, 25)

        val rawText = context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
        onProgressUpdate(bookId, 60)

        val paragraphs = rawText
            .split(Regex("""\r?\n\s*\r?\n|\r?\n"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val allPages = mutableListOf<String>()
        val chunk = mutableListOf<String>()
        var charCount = 0
        for (para in paragraphs) {
            chunk.add(para)
            charCount += para.length
            if (charCount >= 820 || chunk.size >= 5) {
                allPages.add(chunk.joinToString("\n\n"))
                chunk.clear()
                charCount = 0
            }
        }
        if (chunk.isNotEmpty()) {
            allPages.add(chunk.joinToString("\n\n"))
        }
        if (allPages.isEmpty()) {
            allPages.add(rawText.trim())
        }

        val chapters = mutableListOf<BookChapterEntity>()
        val pagesPerChapter = 12
        var idx = 0
        var chNum = 1
        while (idx < allPages.size) {
            val sub = allPages.subList(idx, (idx + pagesPerChapter).coerceAtMost(allPages.size))
            val arr = JSONArray()
            sub.forEach { arr.put(it) }
            chapters.add(
                BookChapterEntity(
                    bookId = bookId,
                    chapterIndex = chNum - 1,
                    title = if (allPages.size <= pagesPerChapter) cleanTitle else "Bagian $chNum",
                    pagesJson = arr.toString(),
                    illustrationsJson = "[]"
                )
            )
            idx += pagesPerChapter
            chNum++
        }

        val bookEntity = BookEntity(
            id = bookId,
            title = cleanTitle.ifBlank { "Dokumen Teks" },
            author = "Dokumen TXT",
            year = yearStr,
            publisher = "Local TXT",
            edition = "Teks",
            isbn = "—",
            category = "Novel",
            shelf = targetShelf,
            format = "TXT",
            pagesCount = allPages.size,
            addedDate = todayKey,
            progress = 0,
            completedAllPages = false,
            onTheGo = true,
            timeMinutes = 0,
            readingSeconds = 0,
            savedChapterIndex = 0,
            savedPageIndex = 0,
            bookmarked = false,
            localFilePath = resolvedPath,
            coverImagePath = null,
            spineColorHex = 0xFF2D3A3AL,
            spineTextColorHex = 0xFFFAF6EBL,
            synopsis = paragraphs.firstOrNull()?.take(320) ?: cleanTitle
        )
        onInitialBookCreated(bookEntity)
        onProgressUpdate(bookId, 100)

        ParsedBookBundle(book = bookEntity, chapters = chapters)
    }

    suspend fun importAndParseTxt(
        context: Context,
        uri: Uri,
        fallbackFileName: String,
        targetShelf: String = "eReader"
    ): ParsedBookBundle = importAndParseTxtWithProgress(
        context = context,
        uri = uri,
        fallbackFileName = fallbackFileName,
        targetShelf = targetShelf
    )
}
