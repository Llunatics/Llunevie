package com.llunaticsys.llunevie.data.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.llunaticsys.llunevie.data.local.BookChapterEntity
import com.llunaticsys.llunevie.data.local.BookEntity
import com.llunaticsys.llunevie.model.BookIllustration
import com.llunaticsys.llunevie.ui.components.normalizeBookTitle
import com.llunaticsys.llunevie.ui.components.sanitizeBookOrChapterText
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDResources
import com.tom_roush.pdfbox.pdmodel.graphics.form.PDFormXObject
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.action.PDActionGoTo
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDNamedDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem
import com.tom_roush.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineNode
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.PDFTextStripperByArea
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.ceil

object PdfNativeRenderer {

    data class PdfBookmark(val title: String, val startPageZero: Int)

    private val renderMutex = Mutex()

    // High-speed in-memory Bitmap LRU Cache capped to at most 32 page bitmaps (capacity >= 16)
    private val pageBitmapCache = object : LruCache<String, Bitmap>(32) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    private val aspectRatioCache = mutableMapOf<String, Float>()

    // Dedicated high-resolution in-memory Bitmap LRU Cache for the full-screen illustration viewer (1600-2048px display resolution)
    private val highResIllustCache = object : LruCache<String, Bitmap>(6) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    fun getCachedHighResIllustration(pdfFilePath: String, pageIndex: Int): Bitmap? {
        val cached = highResIllustCache.get("$pdfFilePath:$pageIndex")
        return if (cached != null && !cached.isRecycled) cached else null
    }

    suspend fun renderHighResIllustrationPage(
        pdfFilePath: String,
        pageIndex: Int,
        targetWidthPx: Int = 1800
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (pageIndex < 0 || pdfFilePath.isBlank()) return@withContext null
        val cacheKey = "$pdfFilePath:$pageIndex"
        highResIllustCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return@withContext cached
        }
        val file = File(pdfFilePath)
        if (!file.exists()) return@withContext null

        renderMutex.withLock {
            highResIllustCache.get(cacheKey)?.let { cached ->
                if (!cached.isRecycled) return@withLock cached
            }
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (pageIndex !in 0 until renderer.pageCount) return@use null
                        renderer.openPage(pageIndex).use { page ->
                            val rawW = page.width.coerceAtLeast(1)
                            val rawH = page.height.coerceAtLeast(1)
                            val targetW = targetWidthPx.coerceIn(1600, 2048)
                            val scale = (targetW.toFloat() / rawW.toFloat()).coerceAtLeast(1.0f)
                            var bmpW = (rawW * scale).toInt().coerceAtLeast(1)
                            var bmpH = (rawH * scale).toInt().coerceAtLeast(1)
                            val maxDim = 3840
                            if (maxOf(bmpW, bmpH) > maxDim) {
                                val downscale = maxDim.toFloat() / maxOf(bmpW, bmpH).toFloat()
                                bmpW = (bmpW * downscale).toInt().coerceAtLeast(1)
                                bmpH = (bmpH * downscale).toInt().coerceAtLeast(1)
                            }
                            val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            canvas.drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            highResIllustCache.put(cacheKey, bitmap)
                            bitmap
                        }
                    }
                }
            }.getOrNull()
        }
    }

    fun getCachedBitmap(pdfFilePath: String, pageIndex: Int, widthBucket: Int): Bitmap? {
        return pageBitmapCache.get("$pdfFilePath:$pageIndex:$widthBucket")
    }

    fun getCachedPageAspectRatio(pdfFilePath: String): Float {
        return aspectRatioCache[pdfFilePath] ?: 0.707f
    }

    fun buildPdfChapters(
        bookId: String,
        totalPages: Int,
        defaultTitle: String = "Dokumen PDF",
        isEn: Boolean = false
    ): List<BookChapterEntity> {
        val safePages = totalPages.coerceAtLeast(1)
        val chaptersCount = when {
            safePages <= 16 -> 1
            safePages <= 32 -> 2
            safePages <= 64 -> 4
            safePages <= 128 -> 8
            safePages <= 256 -> 12
            safePages <= 512 -> 16
            else -> 20
        }
        val pagesPerChapter = ceil(safePages.toDouble() / chaptersCount.toDouble()).toInt().coerceAtLeast(1)
        val chapters = mutableListOf<BookChapterEntity>()
        var startIdx = 0
        var chIdx = 0
        while (startIdx < safePages) {
            val endIdx = (startIdx + pagesPerChapter).coerceAtMost(safePages)
            val pagesArr = JSONArray()
            for (pIdx in startIdx until endIdx) {
                pagesArr.put("[PDF_PAGE:$pIdx]")
            }
            val title = if (chaptersCount == 1) {
                defaultTitle
            } else {
                if (isEn) "Section ${chIdx + 1} (pp. ${startIdx + 1}–$endIdx)"
                else "Bagian ${chIdx + 1} (Hal ${startIdx + 1}–$endIdx)"
            }
            chapters.add(
                BookChapterEntity(
                    bookId = bookId,
                    chapterIndex = chIdx,
                    title = title,
                    pagesJson = pagesArr.toString(),
                    illustrationsJson = "[]"
                )
            )
            chIdx++
            startIdx = endIdx
        }
        return chapters
    }

    fun resolveDirectPdfFile(uri: Uri): File? {
        if (uri.scheme == "file" && uri.path != null) {
            val f = File(uri.path!!)
            if (f.canRead()) return f
        }
        if (uri.scheme == "content") {
            val hasAllFiles = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()
            if (hasAllFiles) {
                val path = uri.path.orEmpty()
                if (path.contains("primary:")) {
                    val rel = path.substringAfter("primary:")
                    val candidate = File(Environment.getExternalStorageDirectory(), rel)
                    if (candidate.canRead()) return candidate
                } else if (path.contains("raw:")) {
                    val rawPath = path.substringAfter("raw:")
                    val candidate = File(rawPath)
                    if (candidate.canRead()) return candidate
                }
                val fullDecoded = runCatching { URLDecoder.decode(uri.toString(), "UTF-8") }.getOrDefault(uri.toString())
                val storageIdx = fullDecoded.indexOf("/storage/emulated/0/")
                if (storageIdx != -1) {
                    val candidate = File(fullDecoded.substring(storageIdx))
                    if (candidate.canRead()) return candidate
                }
                val sdcardIdx = fullDecoded.indexOf("/sdcard/")
                if (sdcardIdx != -1) {
                    val candidate = File(fullDecoded.substring(sdcardIdx))
                    if (candidate.canRead()) return candidate
                }
            }
        }
        return null
    }

    suspend fun importPdfDocumentWithProgress(
        context: Context,
        uri: Uri,
        fallbackFileName: String,
        targetShelf: String = "eReader",
        isEn: Boolean = false,
        onInitialBookCreated: suspend (BookEntity) -> Unit = {},
        onProgressUpdate: (String, Int) -> Unit = { _, _ -> }
    ): ParsedBookBundle = withContext(Dispatchers.IO) {
        val bookId = "pdf-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(6)}"
        val coversDir = File(context.filesDir, "covers/$bookId").apply { mkdirs() }
        val cleanTitle = normalizeBookTitle(fallbackFileName)
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val yearStr = SimpleDateFormat("yyyy", Locale.US).format(Date())

        val directFile = resolveDirectPdfFile(uri)
        val pdfFile: File
        if (directFile != null) {
            pdfFile = directFile
        } else {
            val booksDir = File(context.filesDir, "books").apply { mkdirs() }
            val destPdfFile = File(booksDir, "$bookId.pdf")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destPdfFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                    }
                }
            }
            pdfFile = destPdfFile
        }
        val resolvedLocalFilePath = pdfFile.absolutePath

        // Insert initial placeholder book immediately so user sees it in Home & Library right away
        val initialBook = BookEntity(
            id = bookId,
            title = cleanTitle.ifBlank { if (isEn) "PDF Document" else "Dokumen PDF" },
            author = if (isEn) "PDF Document" else "Dokumen PDF",
            year = yearStr,
            publisher = "PDF",
            edition = "PDF",
            isbn = "—",
            category = "Novel",
            shelf = targetShelf,
            format = "PDF",
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
            localFilePath = resolvedLocalFilePath,
            coverImagePath = null,
            spineColorHex = 0xFF2B3A4AL,
            spineTextColorHex = 0xFFFAF6EBL,
            synopsis = cleanTitle
        )
        onInitialBookCreated(initialBook)
        onProgressUpdate(bookId, 50)

        var totalPages = 1
        var coverFilePath: String? = null
        var firstPageBitmap: Bitmap? = null

        runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    totalPages = renderer.pageCount.coerceAtLeast(1)
                    if (renderer.pageCount > 0) {
                        renderer.openPage(0).use { page ->
                            val w = page.width.coerceAtLeast(1)
                            val h = page.height.coerceAtLeast(1)
                            aspectRatioCache[pdfFile.absolutePath] = (w.toFloat() / h.toFloat()).coerceIn(0.45f, 1.6f)
                            val targetW = (w * 1.5f).toInt().coerceIn(360, 1080)
                            val targetH = (h * (targetW.toFloat() / w)).toInt().coerceIn(480, 1600)
                            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bmp)
                            canvas.drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val coverFile = File(coversDir, "cover_p1.jpg")
                            FileOutputStream(coverFile).use { out ->
                                bmp.compress(Bitmap.CompressFormat.JPEG, 78, out)
                            }
                            coverFilePath = coverFile.absolutePath
                            firstPageBitmap = bmp
                            pageBitmapCache.put("${pdfFile.absolutePath}:0:720", bmp)
                        }
                    }
                }
            }
        }
        onProgressUpdate(bookId, 80)

        val (spineHex, spineTextHex) = SpineColorExtractor.extractSpineColors(firstPageBitmap)
        if (firstPageBitmap != null && pageBitmapCache.get("${pdfFile.absolutePath}:0:720") == null) {
            firstPageBitmap?.recycle()
        }

        val bookWithCover = initialBook.copy(
            author = if (isEn) "PDF Document" else "Dokumen PDF",
            pagesCount = totalPages,
            localFilePath = resolvedLocalFilePath,
            coverImagePath = coverFilePath,
            spineColorHex = spineHex,
            spineTextColorHex = spineTextHex,
            synopsis = "$cleanTitle ($totalPages PDF pages)."
        )

        // Instantly generate logical chapters covering ALL pages 0 until totalPages (< 10ms!)
        val chapters = buildPdfChapters(
            bookId = bookId,
            totalPages = totalPages,
            defaultTitle = cleanTitle,
            isEn = isEn
        )

        onInitialBookCreated(bookWithCover)
        onProgressUpdate(bookId, 100)

        ParsedBookBundle(book = bookWithCover, chapters = chapters)
    }

    fun isIllustrationOrArtworkPage(bitmap: Bitmap, pageIndex: Int): Boolean {
        if (pageIndex == 0) return true // Page 0 is always the front cover artwork
        return isTrueIllustrationPageBitmap(bitmap)
    }


    suspend fun importPdfDocument(
        context: Context,
        uri: Uri,
        fallbackFileName: String,
        targetShelf: String = "eReader"
    ): ParsedBookBundle = importPdfDocumentWithProgress(
        context = context,
        uri = uri,
        fallbackFileName = fallbackFileName,
        targetShelf = targetShelf
    )

    suspend fun renderPageBitmap(
        pdfFilePath: String,
        pageIndex: Int,
        targetWidthPx: Int = 960
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (pageIndex < 0) return@withContext null
        val boundedTargetW = targetWidthPx.coerceIn(360, 1080)
        val widthBucket = ((boundedTargetW / 120) * 120).coerceAtLeast(360)
        val cacheKey = "$pdfFilePath:$pageIndex:$widthBucket"
        pageBitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return@withContext cached
        }

        val file = File(pdfFilePath)
        if (!file.exists()) return@withContext null

        renderMutex.withLock {
            pageBitmapCache.get(cacheKey)?.let { cached ->
                if (!cached.isRecycled) return@withLock cached
            }
            runCatching {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (pageIndex !in 0 until renderer.pageCount) return@use null
                        renderer.openPage(pageIndex).use { page ->
                            val rawW = page.width.coerceAtLeast(1)
                            val rawH = page.height.coerceAtLeast(1)
                            aspectRatioCache[pdfFilePath] = (rawW.toFloat() / rawH.toFloat()).coerceIn(0.45f, 1.6f)
                            val scale = minOf(1.8f, 1080f / rawW.toFloat()).coerceAtLeast(0.5f)
                            val bmpW = (rawW * scale).toInt().coerceIn(240, 1080)
                            val bmpH = (rawH * scale).toInt().coerceIn(320, 1920)
                            val bitmap = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bitmap)
                            // Always fill PDF page background with pure white paper (#FFFFFF)
                            canvas.drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            pageBitmapCache.put(cacheKey, bitmap)
                            bitmap
                        }
                    }
                }
            }.getOrNull()
        }
    }

    fun sanitizeChapterTitle(raw: String): String {
        var t = raw
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
            .replace("’", "'")
            .replace("‘", "'")
            .replace("“", "\"")
            .replace("”", "\"")
            .replace("—", " — ")
            .replace("–", " – ")
            .replace(Regex("""\s+"""), " ")
            .trim()
        t = t.replace(Regex("""(?:\s*[.·…_]{2,}\s*\d{1,4}|\s+\d{1,4})$"""), "").trim()
        return sanitizeBookOrChapterText(t)
    }

    fun isPlausibleChapterTitle(text: String): Boolean {
        val lower = text.lowercase()
        if (lower.startsWith("chapter") || lower.startsWith("bab") || lower.startsWith("bagian") ||
            lower.startsWith("part") || lower.startsWith("act") || lower.startsWith("section") ||
            lower.startsWith("prologue") || lower.startsWith("epilogue") || lower.startsWith("pengantar") ||
            lower.startsWith("pendahuluan") || lower.startsWith("prakata")
        ) return true
        return text.length in 3..60 && !text.contains("...") && text.first().isLetter()
    }

    private fun resolveDestinationPage(document: PDDocument, destination: PDDestination): Int? {
        val pageDest = when (destination) {
            is PDPageDestination -> destination
            is PDNamedDestination -> {
                runCatching { document.documentCatalog.findNamedDestinationPage(destination) }.getOrNull()
            }
            else -> null
        } ?: return null

        // 1. Destination page object
        runCatching {
            val page = pageDest.page
            if (page != null) {
                val idx = document.pages.indexOf(page)
                if (idx >= 0) return idx
            }
        }

        // 2. retrievePageNumber() (0-based)
        runCatching {
            val num = pageDest.retrievePageNumber()
            if (num >= 0 && num < document.numberOfPages) return num
        }

        // 3. pageNumber
        runCatching {
            val num = pageDest.pageNumber
            if (num >= 0 && num < document.numberOfPages) return num
        }

        // 4. findPageNumber() (1-based fallback)
        runCatching {
            val num = pageDest.findPageNumber()
            if (num > 0 && num <= document.numberOfPages) return num - 1
        }

        return null
    }

    fun cleanFooterTitle(line: String): String {
        val s = line.trim()
            .replace(Regex("^\\d{1,4}\\s+"), "")
            .replace(Regex("\\s+\\d{1,4}$"), "")
            .replace(Regex("^[|•\\-–—]+\\s*"), "")
            .replace(Regex("\\s*[|•\\-–—]+$"), "")
            .trim()
        if (s.all { it.isDigit() || it.isWhitespace() || it == '.' || it == '-' || it == '–' || it == '—' || it == '|' || it == '•' }) {
            return ""
        }
        return sanitizeChapterTitle(s)
    }

    val CHAPTER_KEYWORD_REGEX = Regex(
        "(?i)^(prolog|prologue|monolog|monologue|soliloquy|epilog|epilogue|chapter\\s+[\\dIVXLCDM]+|bab\\s+[\\dIVXLCDM]+|bagian\\s+[\\dIVXLCDM]+|part\\s+[\\dIVXLCDM]+|act\\s+[\\dIVXLCDM]+|volume\\s+[\\dIVXLCDM]+|interlude|side\\s+story|short\\s+story|ss\\b|extra|bonus|afterword|kata\\s+penutup|penutup|prakata|pendahuluan|daftar\\s+isi|table\\s+of\\s+contents)\\b.*"
    )
    
    val CHAPTER_KEYWORD_SUFFIX_REGEX = Regex(
        "(?i).*\\b(monologue|monolog|prologue|prolog|epilogue|epilog|soliloquy)$"
    )

    fun isLikelyBodySentenceOrWatermark(candidateTitle: String): Boolean {
        if (CHAPTER_KEYWORD_REGEX.matches(candidateTitle) || CHAPTER_KEYWORD_SUFFIX_REGEX.matches(candidateTitle)) return false
        val trimmed = candidateTitle.trim()
        if (trimmed.endsWith(".") || trimmed.endsWith(",") || trimmed.endsWith(";") || trimmed.endsWith("—") || trimmed.endsWith("–")) return true
        if (trimmed.startsWith("—") || trimmed.startsWith("-") || trimmed.startsWith("\"") || trimmed.startsWith(""") || trimmed.startsWith(""") || trimmed.startsWith("«")) return true
        if (trimmed.any { it == '"' || it == '"' || it == '"' || it == '«' || it == '»' }) return true
        val words = trimmed.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        if (words.size > 7 || trimmed.length > 48) return true
        val proseConjunctionRegex = Regex("""(?i)\b(yang|akan|untuk|dengan|karena|tetapi|namun|bahwa|ketika|seperti|mereka|dirinya|tidak\s+tahu|memiliki|mempersiapkan|menemukannya|terjadi)\b""")
        val explicitPrefixRegex = Regex("""(?i)^(bab|chapter|bagian|part|prolog|prologue|monolog|monologue|epilog|epilogue|interlude|side\s+story|afterword|kata\s+penutup)\b""")
        if (proseConjunctionRegex.containsMatchIn(trimmed) && !explicitPrefixRegex.containsMatchIn(trimmed)) {
            return true
        }
        return false
    }

    fun deduplicateChapters(
        rawChapters: List<PdfBookmark>,
        totalPages: Int,
        defaultTitle: String,
        globalWatermarkBlacklist: Set<String> = emptySet()
    ): List<PdfBookmark> {
        val noiseWords = setOf("contents", "tableofcontents", "daftarisi", "isibuku", "copyright", "hakcipta", "cover", "sampul")
        val normBook = defaultTitle.lowercase().replace(Regex("[^a-z0-9]"), "")
        val chapterKeywordRegex = CHAPTER_KEYWORD_REGEX

        // 1. Group by page and pick the best candidate per page
        val pageMap = mutableMapOf<Int, PdfBookmark>()
        for (ch in rawChapters) {
            val clean = sanitizeChapterTitle(ch.title)
            if (clean.length < 2) continue
            if (clean.all { it.isDigit() || it.isWhitespace() || it == '.' }) continue
            val normKey = clean.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (normKey in noiseWords || normKey.isEmpty() || normKey in globalWatermarkBlacklist) continue

            val page = ch.startPageZero.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
            val existing = pageMap[page]
            if (existing == null) {
                pageMap[page] = PdfBookmark(clean, page)
            } else {
                val existingIsKeyword = chapterKeywordRegex.matches(existing.title)
                val newIsKeyword = chapterKeywordRegex.matches(clean)
                if ((newIsKeyword && !existingIsKeyword) || (newIsKeyword == existingIsKeyword && clean.length > existing.title.length)) {
                    pageMap[page] = PdfBookmark(clean, page)
                }
            }
        }

        // 2. Remove generic fallback entries spanning areas where real chapters exist
        val sorted = pageMap.values.sortedBy { it.startPageZero }.toMutableList()
        val realChaptersExist = sorted.any {
            val n = it.title.lowercase().replace(Regex("[^a-z0-9]"), "")
            n != normBook && !it.title.contains(defaultTitle, ignoreCase = true)
        }
        if (realChaptersExist) {
            sorted.removeAll { ch ->
                val n = ch.title.lowercase().replace(Regex("[^a-z0-9]"), "")
                ch.startPageZero > 0 && (n == normBook || ch.title.contains(defaultTitle, ignoreCase = true))
            }
        }

        // 3. Deduplicate by normalized title (keep earliest start page)
        val seenKeys = mutableSetOf<String>()
        val deduped = mutableListOf<PdfBookmark>()
        for (ch in sorted) {
            val normKey = ch.title.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (seenKeys.add(normKey)) {
                deduped.add(ch)
            }
        }

        deduped.sortBy { it.startPageZero }

        // Filter out any non-keyword chapter whose span is only 1..2 pages if surrounded by explicit Bab/Chapter/Prolog/Epilog chapters
        val specialStandaloneRegex = Regex("(?i)^(prolog|prologue|epilog|epilogue|kata\\s+penutup|afterword)\\b.*")
        val result = mutableListOf<PdfBookmark>()
        for (i in deduped.indices) {
            val ch = deduped[i]
            val span = (if (i + 1 < deduped.size) deduped[i + 1].startPageZero else totalPages) - ch.startPageZero
            val isKeyword = chapterKeywordRegex.matches(ch.title)
            val isSpecial = specialStandaloneRegex.matches(ch.title)
            val surroundedByKeywords = (i > 0 && chapterKeywordRegex.matches(deduped[i - 1].title)) ||
                    (i + 1 < deduped.size && chapterKeywordRegex.matches(deduped[i + 1].title))

            if (!isKeyword && !isSpecial && span in 1..2 && surroundedByKeywords) {
                // Skip noisy 1-2 page non-keyword candidate
                continue
            }
            result.add(ch)
        }

        result.sortBy { it.startPageZero }
        if (result.isNotEmpty() && result.first().startPageZero > 0) {
            result.add(0, PdfBookmark(sanitizeChapterTitle(defaultTitle), 0))
        }
        return result
    }

    suspend fun extractChaptersWithPdfBox(
        context: Context,
        pdfFile: File,
        totalPages: Int,
        defaultTitle: String,
        onStageProgress: suspend (Int) -> Unit = {}
    ): List<PdfBookmark> {
        val detectedBookmarks = mutableListOf<PdfBookmark>()
        val tocBookmarks = mutableListOf<PdfBookmark>()
        val additiveBookmarks = mutableListOf<PdfBookmark>()
        var finalResultChapters: List<PdfBookmark>? = null

        runCatching {
            PDFBoxResourceLoader.init(context.applicationContext)
            val memSetting = MemoryUsageSetting.setupTempFileOnly()
                .setTempDir(context.cacheDir)

            PDDocument.load(pdfFile, memSetting).use { document ->
                val pageCount = document.numberOfPages.coerceAtLeast(1)
                val safeTotalPages = if (totalPages > 0) minOf(totalPages, pageCount) else pageCount

                // Stage 1: Native PDF Outline / Bookmark Tree (5% -> 25%)
                onStageProgress(10)
                val outline = runCatching { document.documentCatalog.documentOutline }.getOrNull()
                if (outline != null) {
                    fun walkOutline(node: PDOutlineNode) {
                        var cur = node.firstChild
                        while (cur != null) {
                            val rawTitle = cur.title?.trim().orEmpty()
                            val dest = cur.destination ?: (cur.action as? PDActionGoTo)?.destination
                            if (dest != null && rawTitle.isNotBlank()) {
                                val targetPage = resolveDestinationPage(document, dest)
                                if (targetPage != null && targetPage in 0 until safeTotalPages) {
                                    val clean = sanitizeChapterTitle(rawTitle)
                                    if (clean.length in 2..120) {
                                        detectedBookmarks.add(PdfBookmark(clean, targetPage))
                                    }
                                }
                            }
                            if (cur.hasChildren()) {
                                walkOutline(cur)
                            }
                            cur = cur.nextSibling
                        }
                    }
                    walkOutline(outline)
                }

                onStageProgress(25)

                // Stage 2: Contents / Table of Contents / Daftar Isi Pages (25% -> 55%)
                val maxScan = minOf(25, safeTotalPages)
                val textStripper = runCatching { PDFTextStripper() }.getOrNull()
                val linkPageOccurrences = mutableMapOf<String, MutableSet<Int>>()

                if (textStripper != null) {
                    val tocHeadingRegex = Regex(
                        "\\b(Table\\s+of\\s+Contents|Contents|Daftar\\s+Isi|Table\\s+des\\s+mati[èe]res|Inhaltsverzeichnis|Índice|Indice)\\b",
                        RegexOption.IGNORE_CASE
                    )
                    val tocPages = mutableListOf<Int>()

                    for (p in 0 until maxScan) {
                        textStripper.startPage = p + 1
                        textStripper.endPage = p + 1
                        val text = runCatching { textStripper.getText(document) }.getOrDefault("")
                        if (tocHeadingRegex.containsMatchIn(text)) {
                            tocPages.add(p)
                        } else if (tocPages.isNotEmpty() && tocPages.last() == p - 1) {
                            val pdPage = runCatching { document.getPage(p) }.getOrNull()
                            val hasLinks = pdPage?.annotations?.filterIsInstance<PDAnnotationLink>()?.isNotEmpty() == true
                            val hasNumberedLines = text.lines().count { line ->
                                Regex("(?:[.\\s_\\-–—·…]{2,}|\\t+)\\s*(\\d{1,4})\\s*$").containsMatchIn(line.trim())
                            } >= 3
                            if (hasLinks || hasNumberedLines) {
                                tocPages.add(p)
                            }
                        }
                    }

                    onStageProgress(35)

                    val pageLabels = runCatching { document.documentCatalog.pageLabels?.pageIndicesByLabels }.getOrNull()
                    val numberedLineRegex = Regex("^(.+?)(?:[.\\s_\\-–—·…]{2,}|\\t+)\\s*(\\d{1,4})\\s*$")
                    val unnumberedCandidates = mutableListOf<String>()

                    for (tocPageZero in tocPages) {
                        val pdPage = runCatching { document.getPage(tocPageZero) }.getOrNull() ?: continue

                        // Strategy 2A: Interactive Link Annotations (PDAnnotationLink)
                        val links = runCatching { pdPage.annotations.filterIsInstance<PDAnnotationLink>() }.getOrDefault(emptyList())
                        if (links.isNotEmpty()) {
                            val mediaBox = pdPage.mediaBox ?: pdPage.cropBox
                            val pageH = mediaBox.height
                            val areaStripper = runCatching { PDFTextStripperByArea() }.getOrNull()

                            for ((linkIdx, link) in links.withIndex()) {
                                val dest = link.destination ?: (link.action as? PDActionGoTo)?.destination ?: continue
                                val targetPage = resolveDestinationPage(document, dest) ?: continue
                                if (targetPage !in 0 until safeTotalPages) continue

                                var linkText: String? = null
                                if (areaStripper != null) {
                                    val rect = link.rectangle
                                    if (rect != null) {
                                        val left = minOf(rect.lowerLeftX, rect.upperRightX)
                                        val right = maxOf(rect.lowerLeftX, rect.upperRightX)
                                        val top = (pageH - maxOf(rect.lowerLeftY, rect.upperRightY)).coerceAtLeast(0f)
                                        val bottom = (pageH - minOf(rect.lowerLeftY, rect.upperRightY)).coerceAtLeast(0f)
                                        val rectF = RectF(left - 2f, top - 2f, right + 2f, bottom + 2f)
                                        val rKey = "link_$linkIdx"
                                        areaStripper.addRegion(rKey, rectF)
                                        runCatching { areaStripper.extractRegions(pdPage) }
                                        linkText = areaStripper.getTextForRegion(rKey)?.trim()
                                        areaStripper.removeRegion(rKey)
                                    }
                                }
                                if (!linkText.isNullOrBlank()) {
                                    val clean = sanitizeChapterTitle(linkText)
                                    val normL = clean.lowercase().replace(Regex("[^a-z0-9]"), "")
                                    if (normL.isNotEmpty()) {
                                        linkPageOccurrences.getOrPut(normL) { mutableSetOf() }.add(tocPageZero)
                                    }
                                    if (clean.length in 2..120) {
                                        tocBookmarks.add(PdfBookmark(clean, targetPage))
                                    }
                                }
                            }
                        }

                        // Strategy 2B: Numbered ToC lines
                        textStripper.startPage = tocPageZero + 1
                        textStripper.endPage = tocPageZero + 1
                        val text = runCatching { textStripper.getText(document) }.getOrDefault("")

                        for (line in text.lines()) {
                            val trimmed = line.trim()
                            if (trimmed.length < 3) continue
                            val match = numberedLineRegex.find(trimmed)
                            if (match != null) {
                                val rawTitle = match.groupValues[1].trim()
                                val pageStr = match.groupValues[2]
                                val clean = sanitizeChapterTitle(rawTitle)
                                if (clean.length in 2..120) {
                                    val targetPage: Int? = if (pageLabels != null && pageLabels.containsKey(pageStr)) {
                                        pageLabels[pageStr]
                                    } else {
                                        val pNum = pageStr.toIntOrNull()
                                        if (pNum != null && pNum in 1..safeTotalPages) {
                                            pNum - 1
                                        } else null
                                    }
                                    if (targetPage != null && targetPage in 0 until safeTotalPages) {
                                        tocBookmarks.add(PdfBookmark(clean, targetPage))
                                    } else {
                                        unnumberedCandidates.add(clean)
                                    }
                                }
                            } else {
                                val clean = sanitizeChapterTitle(trimmed)
                                if (clean.length in 3..80 && isPlausibleChapterTitle(clean)) {
                                    unnumberedCandidates.add(clean)
                                }
                            }
                        }
                    }

                    onStageProgress(45)

                    // Strategy 2C: Unnumbered ToC Lines Fallback
                    if (tocBookmarks.isEmpty() && unnumberedCandidates.isNotEmpty() && detectedBookmarks.size < 2) {
                        var searchStart = (tocPages.maxOrNull() ?: 0) + 1
                        for (cand in unnumberedCandidates) {
                            val normCand = cand.lowercase().replace(Regex("[^a-z0-9]"), "")
                            if (normCand.length < 3) continue
                            for (p in searchStart until minOf(safeTotalPages, searchStart + 80)) {
                                textStripper.startPage = p + 1
                                textStripper.endPage = p + 1
                                val pageText = runCatching { textStripper.getText(document) }.getOrDefault("")
                                val topLines = pageText.lines().take(6).map { it.lowercase().replace(Regex("[^a-z0-9]"), "") }
                                if (topLines.any { it.contains(normCand) || (normCand.contains(it) && it.length > 5) }) {
                                    tocBookmarks.add(PdfBookmark(cand, p))
                                    searchStart = p + 1
                                    break
                                }
                            }
                        }
                    }
                }

                onStageProgress(45)

                val globalWatermarkBlacklist = mutableSetOf<String>()
                for ((nLink, pages) in linkPageOccurrences) {
                    if (pages.size >= 5) {
                        globalWatermarkBlacklist.add(nLink)
                    }
                }

                val normBookTitle = defaultTitle.lowercase().replace(Regex("[^a-z0-9]"), "")
                fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")
                fun isValidChapterTitle(t: String): Boolean {
                    val n = norm(t)
                    return t.length in 4..85 && n != normBookTitle && n.isNotEmpty() &&
                        !t.all { it.isDigit() || it.isWhitespace() || it == '.' }
                }

                val pageLines = arrayOfNulls<List<String>>(safeTotalPages)
                val pageBottomTitles = arrayOfNulls<String>(safeTotalPages)
                val linePageOccurrences = mutableMapOf<String, MutableSet<Int>>()
                val conditionBBookmarks = mutableListOf<PdfBookmark>()

                if (textStripper != null) {
                    for (p in 0 until safeTotalPages) {
                        textStripper.startPage = p + 1
                        textStripper.endPage = p + 1
                        val pageText = runCatching { textStripper.getText(document) }.getOrDefault("")
                        val lines = pageText.lines().map { it.trim() }.filter { it.isNotEmpty() }
                        pageLines[p] = lines

                        if (lines.isNotEmpty()) {
                            val topCands = lines.take(2).map { cleanFooterTitle(it) }.filter { it.isNotBlank() }
                            val botCands = lines.takeLast(2).map { cleanFooterTitle(it) }.filter { it.isNotBlank() }
                            for (c in (topCands + botCands)) {
                                val n = norm(c)
                                if (n.isNotEmpty()) {
                                    linePageOccurrences.getOrPut(n) { mutableSetOf() }.add(p)
                                }
                            }
                            val bestFooter = botCands.lastOrNull { isValidChapterTitle(it) }
                            if (bestFooter != null) {
                                pageBottomTitles[p] = bestFooter
                            }

                            // Always run Condition B: explicit chapter headings in topLines AND cleaned bottom footers
                            val topHeadingLines = lines.take(6)
                            for (line in topHeadingLines) {
                                val cleanLine = sanitizeChapterTitle(line)
                                if (cleanLine.length in 4..85 && (CHAPTER_KEYWORD_REGEX.matches(cleanLine) || CHAPTER_KEYWORD_SUFFIX_REGEX.matches(cleanLine))) {
                                    val n = norm(cleanLine)
                                    if (n != normBookTitle && !cleanLine.all { it.isDigit() || it.isWhitespace() || it == '.' }) {
                                        conditionBBookmarks.add(PdfBookmark(cleanLine, p))
                                        break
                                    }
                                }
                            }
                            
                            // Also check cleaned bottom running footer
                            if (bestFooter != null) {
                                val cleanFooter = sanitizeChapterTitle(bestFooter)
                                if (cleanFooter.length in 4..85 && (CHAPTER_KEYWORD_REGEX.matches(cleanFooter) || CHAPTER_KEYWORD_SUFFIX_REGEX.matches(cleanFooter))) {
                                    val n = norm(cleanFooter)
                                    if (n != normBookTitle && !conditionBBookmarks.any { norm(it.title) == n }) {
                                        conditionBBookmarks.add(PdfBookmark(cleanFooter, p))
                                    }
                                }
                            }
                        }

                        if (p % 20 == 0 || p == safeTotalPages - 1) {
                            val pct = 45 + (p * 10 / safeTotalPages.coerceAtLeast(1))
                            onStageProgress(pct.coerceIn(45, 55))
                        }
                    }
                }

                // NEVER blacklist chapter keywords - they are real chapters, not watermarks!
                val lineSpanTracking = mutableMapOf<String, Pair<Int, Int>>() // normalized -> (firstPage, lastPage)
                for ((nLine, pages) in linePageOccurrences) {
                    if (pages.isNotEmpty()) {
                        val firstPage = pages.minOrNull() ?: 0
                        val lastPage = pages.maxOrNull() ?: 0
                        lineSpanTracking[nLine] = Pair(firstPage, lastPage)
                    }
                }
                
                for ((nLine, pages) in linkPageOccurrences) {
                    if (pages.size >= 5) {
                        globalWatermarkBlacklist.add(nLine)
                    }
                }
                
                // Only blacklist non-keyword strings that span > 35% of the book from front to back
                for ((nLine, pages) in linePageOccurrences) {
                    val occurrenceCount = pages.size
                    if (occurrenceCount < 5) continue
                    
                    val (firstPage, lastPage) = lineSpanTracking[nLine] ?: continue
                    val span = lastPage - firstPage
                    
                    // Check if this normalized line matches any chapter keyword
                    val matchesKeyword = linePageOccurrences.keys
                        .filter { it == nLine }
                        .any { normalizedKey ->
                            // Find original titles with this normalized key
                            conditionBBookmarks.any { bm ->
                                norm(bm.title) == normalizedKey && 
                                (CHAPTER_KEYWORD_REGEX.matches(bm.title) || CHAPTER_KEYWORD_SUFFIX_REGEX.matches(bm.title))
                            } || 
                            pageBottomTitles.filterNotNull().any { title ->
                                norm(cleanFooterTitle(title)) == normalizedKey &&
                                (CHAPTER_KEYWORD_REGEX.matches(title) || CHAPTER_KEYWORD_SUFFIX_REGEX.matches(title))
                            }
                        }
                    
                    if (matchesKeyword) continue // Never blacklist chapter keywords!
                    
                    // Only blacklist if it spans > 35% of book AND has at least 5 occurrences
                    if (span > (safeTotalPages * 0.35f) && occurrenceCount >= 5) {
                        globalWatermarkBlacklist.add(nLine)
                    }
                }

                val validConditionB = conditionBBookmarks.filter { norm(it.title) !in globalWatermarkBlacklist }
                val explicitCombined = detectedBookmarks + tocBookmarks + validConditionB
                val structuredChapters = deduplicateChapters(explicitCombined, safeTotalPages, defaultTitle, globalWatermarkBlacklist)

                // If detectedBookmarks + tocBookmarks + Condition B already yield >= 3 chapters,
                // do NOT include Condition A or Condition C candidates at all!
                if (structuredChapters.size >= 3) {
                    onStageProgress(55)
                    finalResultChapters = structuredChapters
                    return@use
                }

                // Fallback for books that have fewer than 3 explicit chapters
                if (textStripper != null) {
                    // Condition A — Top + Bottom Matching Chapter Title on the Same Page
                    for (p in 0 until safeTotalPages) {
                        val lines = pageLines[p] ?: continue
                        if (lines.size < 10) continue // Substantial body text check
                        val topLines = lines.take(2).map { cleanFooterTitle(it) }.filter { isValidChapterTitle(it) }
                        val bottomLines = lines.takeLast(1).map { cleanFooterTitle(it) }.filter { isValidChapterTitle(it) }
                        var conditionAMatched = false
                        for (topClean in topLines) {
                            val topNorm = norm(topClean)
                            if (topNorm in globalWatermarkBlacklist || isLikelyBodySentenceOrWatermark(topClean)) continue
                            for (botClean in bottomLines) {
                                val botNorm = norm(botClean)
                                if (botNorm in globalWatermarkBlacklist || isLikelyBodySentenceOrWatermark(botClean)) continue
                                if (topNorm == botNorm) {
                                    val candidateTitle = if (topClean.length >= botClean.length) topClean else botClean
                                    additiveBookmarks.add(PdfBookmark(candidateTitle, p))
                                    conditionAMatched = true
                                    break
                                }
                            }
                            if (conditionAMatched) break
                        }
                    }

                    // Condition C — Running Bottom Footer Chapter Name Transition repeating on at least 4 consecutive pages
                    for (p in 0 until safeTotalPages - 3) {
                        val t = pageBottomTitles[p] ?: continue
                        val normT = norm(t)
                        if (normT in globalWatermarkBlacklist || isLikelyBodySentenceOrWatermark(t)) continue
                        val prevT = if (p > 0) pageBottomTitles[p - 1]?.let { norm(it) } else null
                        if (normT != prevT) {
                            val next1 = pageBottomTitles[p + 1]?.let { norm(it) }
                            val next2 = pageBottomTitles[p + 2]?.let { norm(it) }
                            val next3 = pageBottomTitles[p + 3]?.let { norm(it) }
                            if (next1 == normT && next2 == normT && next3 == normT) {
                                additiveBookmarks.add(PdfBookmark(t, p))
                            }
                        }
                    }
                }

                onStageProgress(55)
                finalResultChapters = deduplicateChapters(explicitCombined + additiveBookmarks, safeTotalPages, defaultTitle, globalWatermarkBlacklist)
            }
        }

        return finalResultChapters ?: deduplicateChapters(detectedBookmarks + tocBookmarks + additiveBookmarks, totalPages, defaultTitle)
    }

    data class EmbeddedImageSpec(
        val key: String,
        val width: Int,
        val height: Int,
        val isSubstantial: Boolean
    )

    private fun inspectPageEmbeddedImages(
        resources: PDResources?,
        pageArea: Float,
        visited: MutableSet<PDResources> = mutableSetOf()
    ): List<EmbeddedImageSpec> {
        if (resources == null || !visited.add(resources)) return emptyList()
        val result = mutableListOf<EmbeddedImageSpec>()
        runCatching {
            for (cosName in resources.xObjectNames) {
                val xobj = runCatching { resources.getXObject(cosName) }.getOrNull() ?: continue
                if (xobj is PDImageXObject) {
                    val w = runCatching { xobj.width }.getOrDefault(0)
                    val h = runCatching { xobj.height }.getOrDefault(0)
                    val len = runCatching { xobj.cosObject?.length ?: 0 }.getOrDefault(0)
                    val key = "${w}x${h}_$len"
                    val areaRatio = if (pageArea > 0f) (w.toFloat() * h.toFloat()) / pageArea else 0f
                    val isSubstantial = (w >= 150 && h >= 150) || areaRatio >= 0.15f
                    result.add(EmbeddedImageSpec(key, w, h, isSubstantial))
                } else if (xobj is PDFormXObject) {
                    result.addAll(inspectPageEmbeddedImages(xobj.resources, pageArea, visited))
                }
            }
        }
        return result
    }

    private fun evaluateZoneForArtwork(
        bmp: Bitmap,
        zStart: Int,
        zEnd: Int,
        paperLum: Int,
        w: Int,
        h: Int
    ): Boolean {
        val zoneH = zEnd - zStart
        if (zoneH < (h * 0.22f).toInt()) return false

        val xMinBound = (w * 0.05f).toInt()
        val xMaxBound = (w * 0.95f).toInt()
        val zonePixelCount = zoneH * (xMaxBound - xMinBound).coerceAtLeast(1)

        var minX = Int.MAX_VALUE
        var maxX = -1
        var minY = Int.MAX_VALUE
        var maxY = -1
        var nonPaperCount = 0

        // 1. Count non-paper pixels and compute bounding box
        for (y in zStart until zEnd) {
            for (x in xMinBound until xMaxBound) {
                val pix = bmp.getPixel(x, y)
                val r = (pix shr 16) and 0xFF
                val g = (pix shr 8) and 0xFF
                val b = pix and 0xFF
                val lum = (r * 299 + g * 587 + b * 114) / 1000
                val diff = abs(lum - paperLum)
                val maxC = maxOf(r, g, b)
                val minC = minOf(r, g, b)
                val sat = if (maxC > 0) (maxC - minC).toFloat() / maxC else 0f

                if (diff >= 8 || sat >= 0.08f) {
                    nonPaperCount++
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }

        if (minX > maxX || minY > maxY) return false

        val boxW = (maxX - minX + 1).toFloat() / w
        val boxH = (maxY - minY + 1).toFloat() / h

        // Must span height >= 22% of page and width >= 20% of page
        if (boxH < 0.22f || boxW < 0.20f) return false

        // Account for at least 6% of pixels inside the zone
        val density = nonPaperCount.toFloat() / zonePixelCount
        if (density < 0.06f) return false

        // 2. Vertical 2D continuity: connected vertical strokes/shading spanning >= 15% of page height
        val gridRows = 24
        val gridCols = 20
        val cellW = maxOf(1, (maxX - minX + 1) / gridCols)
        val cellH = maxOf(1, (maxY - minY + 1) / gridRows)
        val hasArtCell = Array(gridRows) { BooleanArray(gridCols) }

        for (r in 0 until gridRows) {
            val cyStart = minY + r * cellH
            val cyEnd = minOf(cyStart + cellH, maxY + 1)
            for (c in 0 until gridCols) {
                val cxStart = minX + c * cellW
                val cxEnd = minOf(cxStart + cellW, maxX + 1)
                var cellNonPaper = 0
                for (py in cyStart until cyEnd) {
                    for (px in cxStart until cxEnd) {
                        val pix = bmp.getPixel(px, py)
                        val rC = (pix shr 16) and 0xFF
                        val gC = (pix shr 8) and 0xFF
                        val bC = pix and 0xFF
                        val lum = (rC * 299 + gC * 587 + bC * 114) / 1000
                        val diff = abs(lum - paperLum)
                        val maxC = maxOf(rC, gC, bC)
                        val minC = minOf(rC, gC, bC)
                        val sat = if (maxC > 0) (maxC - minC).toFloat() / maxC else 0f
                        if (diff >= 8 || sat >= 0.08f) {
                            cellNonPaper++
                            if (cellNonPaper >= 2) break
                        }
                    }
                    if (cellNonPaper >= 2) break
                }
                if (cellNonPaper >= 2) {
                    hasArtCell[r][c] = true
                }
            }
        }

        // BFS connected components on active cells
        val visited = Array(gridRows) { BooleanArray(gridCols) }
        val minContSpanPx = (h * 0.15f).toInt()

        for (r in 0 until gridRows) {
            for (c in 0 until gridCols) {
                if (hasArtCell[r][c] && !visited[r][c]) {
                    var minR = r
                    var maxR = r
                    val q = ArrayDeque<Pair<Int, Int>>()
                    q.add(r to c)
                    visited[r][c] = true

                    while (q.isNotEmpty()) {
                        val (cr, cc) = q.removeFirst()
                        if (cr < minR) minR = cr
                        if (cr > maxR) maxR = cr

                        val neighbors = listOf(
                            cr - 1 to cc, cr + 1 to cc, cr to cc - 1, cr to cc + 1,
                            cr - 1 to cc - 1, cr - 1 to cc + 1, cr + 1 to cc - 1, cr + 1 to cc + 1
                        )
                        for ((nr, nc) in neighbors) {
                            if (nr in 0 until gridRows && nc in 0 until gridCols && hasArtCell[nr][nc] && !visited[nr][nc]) {
                                visited[nr][nc] = true
                                q.add(nr to nc)
                            }
                        }
                    }

                    val compSpanPx = (maxR - minR + 1) * cellH
                    if (compSpanPx >= minContSpanPx) {
                        return true
                    }
                }
            }
        }

        return false
    }

    private fun hasNonPaperContent(bmp: Bitmap): Boolean {
        val w = bmp.width
        val h = bmp.height
        val pBot = bmp.getPixel(0, 0)
        val paperLum = ((pBot shr 16 and 0xFF) * 299 + (pBot shr 8 and 0xFF) * 587 + (pBot and 0xFF) * 114) / 1000
        var nonPaperCount = 0
        var total = 0
        for (y in (h * 0.10f).toInt() until (h * 0.85f).toInt() step 4) {
            for (x in (w * 0.10f).toInt() until (w * 0.90f).toInt() step 4) {
                total++
                val pix = bmp.getPixel(x, y)
                val lum = ((pix shr 16 and 0xFF) * 299 + (pix shr 8 and 0xFF) * 587 + (pix and 0xFF) * 114) / 1000
                if (abs(lum - paperLum) >= 8) nonPaperCount++
            }
        }
        return total > 0 && (nonPaperCount.toFloat() / total) >= 0.05f
    }

    fun isTrueIllustrationPage(bmp: Bitmap): Boolean = isTrueIllustrationPageBitmap(bmp)

    fun isTrueIllustrationPageBitmap(bmp: Bitmap): Boolean {
        val w = bmp.width
        val h = bmp.height
        if (w < 40 || h < 40) return false

        // 1. Measure Paper Background Luminance (paperLum) via outer margin ring (4% margins)
        val marginY = (h * 0.04f).toInt().coerceAtLeast(1)
        val marginX = (w * 0.04f).toInt().coerceAtLeast(1)
        val marginLums = IntArray((2 * marginY * w) + (2 * marginX * (h - 2 * marginY).coerceAtLeast(0)))
        var mIdx = 0
        for (y in 0 until marginY) {
            for (x in 0 until w) {
                val pix = bmp.getPixel(x, y)
                marginLums[mIdx++] = ((pix shr 16 and 0xFF) * 299 + (pix shr 8 and 0xFF) * 587 + (pix and 0xFF) * 114) / 1000
            }
        }
        for (y in (h - marginY) until h) {
            for (x in 0 until w) {
                val pix = bmp.getPixel(x, y)
                marginLums[mIdx++] = ((pix shr 16 and 0xFF) * 299 + (pix shr 8 and 0xFF) * 587 + (pix and 0xFF) * 114) / 1000
            }
        }
        for (y in marginY until (h - marginY)) {
            for (x in 0 until marginX) {
                val pix = bmp.getPixel(x, y)
                marginLums[mIdx++] = ((pix shr 16 and 0xFF) * 299 + (pix shr 8 and 0xFF) * 587 + (pix and 0xFF) * 114) / 1000
            }
            for (x in (w - marginX) until w) {
                val pix = bmp.getPixel(x, y)
                marginLums[mIdx++] = ((pix shr 16 and 0xFF) * 299 + (pix shr 8 and 0xFF) * 587 + (pix and 0xFF) * 114) / 1000
            }
        }
        marginLums.sort(0, mIdx)
        val paperLum = if (mIdx > 0) marginLums[mIdx / 2] else 255

        val yStart = (h * 0.06f).toInt()
        val yEnd = (h * 0.92f).toInt()
        val totalInteriorRows = (yEnd - yStart).coerceAtLeast(1)

        // 2. Classify horizontal rows y (6% .. 92% of height) into Text-Line vs Non-Text
        val isTextBandRow = BooleanArray(h)

        val singleXStart = (w * 0.08f).toInt()
        val singleXEnd = (w * 0.92f).toInt()
        val col1XStart = (w * 0.08f).toInt()
        val col1XEnd = (w * 0.48f).toInt()
        val col2XStart = (w * 0.52f).toInt()
        val col2XEnd = (w * 0.92f).toInt()

        fun analyzeRowSegment(y: Int, xS: Int, xE: Int): Pair<Boolean, Boolean> {
            val count = (xE - xS).coerceAtLeast(1)
            var paperPixels = 0
            var inkPixels = 0
            var edgeTransitions = 0
            var prevLum = -1

            for (x in xS until xE) {
                val pix = bmp.getPixel(x, y)
                val r = (pix shr 16) and 0xFF
                val g = (pix shr 8) and 0xFF
                val b = pix and 0xFF
                val lum = (r * 299 + g * 587 + b * 114) / 1000
                val diff = abs(lum - paperLum)
                if (diff < 8) {
                    paperPixels++
                } else if (diff >= 18) {
                    inkPixels++
                }
                if (prevLum >= 0 && abs(lum - prevLum) > 22) {
                    edgeTransitions++
                }
                prevLum = lum
            }

            val paperRatio = paperPixels.toFloat() / count
            val inkRatio = inkPixels.toFloat() / count
            val isWhitespace = paperRatio >= 0.94f
            val isTextStroke = edgeTransitions >= 8 && inkRatio in 0.02f..0.45f
            return isWhitespace to isTextStroke
        }

        val singleWhitespace = BooleanArray(h)
        val singleTextStroke = BooleanArray(h)
        val col1Whitespace = BooleanArray(h)
        val col1TextStroke = BooleanArray(h)
        val col2Whitespace = BooleanArray(h)
        val col2TextStroke = BooleanArray(h)

        for (y in yStart..yEnd) {
            val (sw, st) = analyzeRowSegment(y, singleXStart, singleXEnd)
            singleWhitespace[y] = sw
            singleTextStroke[y] = st

            val (c1w, c1t) = analyzeRowSegment(y, col1XStart, col1XEnd)
            col1Whitespace[y] = c1w
            col1TextStroke[y] = c1t

            val (c2w, c2t) = analyzeRowSegment(y, col2XStart, col2XEnd)
            col2Whitespace[y] = c2w
            col2TextStroke[y] = c2t
        }

        fun markRhythmicBands(isWhitespace: BooleanArray, isStroke: BooleanArray) {
            var y = yStart
            while (y <= yEnd - 20) {
                var strokePeaks = 0
                var whitespaceGaps = 0
                var inStroke = false
                var inGap = false
                val windowEnd = minOf(y + 40, yEnd)

                for (k in y..windowEnd) {
                    if (isStroke[k]) {
                        if (!inStroke) {
                            strokePeaks++
                            inStroke = true
                            inGap = false
                        }
                    } else if (isWhitespace[k]) {
                        if (!inGap) {
                            whitespaceGaps++
                            inGap = true
                            inStroke = false
                        }
                    }
                }

                if (strokePeaks >= 3 && whitespaceGaps >= 2) {
                    for (k in y..windowEnd) {
                        isTextBandRow[k] = true
                    }
                    y += 20
                } else {
                    y += 4
                }
            }
        }

        markRhythmicBands(singleWhitespace, singleTextStroke)
        markRhythmicBands(col1Whitespace, col1TextStroke)
        markRhythmicBands(col2Whitespace, col2TextStroke)

        // 4. White-Background & Grayscale-Sensitive Artwork Detection in Non-Text Zones
        val minZoneH = (h * 0.22f).toInt()
        var hasArtworkZone = false

        var zoneStart = -1
        for (y in yStart..yEnd) {
            if (!isTextBandRow[y]) {
                if (zoneStart < 0) zoneStart = y
            } else {
                if (zoneStart >= 0) {
                    val zoneH = y - zoneStart
                    if (zoneH >= minZoneH) {
                        if (evaluateZoneForArtwork(bmp, zoneStart, y, paperLum, w, h)) {
                            hasArtworkZone = true
                            break
                        }
                    }
                    zoneStart = -1
                }
            }
        }
        if (zoneStart >= 0 && !hasArtworkZone) {
            val zoneH = yEnd - zoneStart + 1
            if (zoneH >= minZoneH) {
                if (evaluateZoneForArtwork(bmp, zoneStart, yEnd + 1, paperLum, w, h)) {
                    hasArtworkZone = true
                }
            }
        }

        // 3. Strict Full-Text Page Exclusion
        var textBandRowCount = 0
        for (y in yStart..yEnd) {
            if (isTextBandRow[y]) textBandRowCount++
        }
        val textBandRatio = textBandRowCount.toFloat() / totalInteriorRows
        if (textBandRatio >= 0.68f && !hasArtworkZone) {
            return false // Full-Text Page!
        }

        // Exclude almost-blank pages: copyright page / chapter title page with 2-5 lines and > 96% empty paper
        var nonPaperSampleCount = 0
        var totalInteriorSampleCount = 0
        for (y in yStart..yEnd step 3) {
            for (x in singleXStart..singleXEnd step 3) {
                totalInteriorSampleCount++
                val pix = bmp.getPixel(x, y)
                val lum = ((pix shr 16 and 0xFF) * 299 + (pix shr 8 and 0xFF) * 587 + (pix and 0xFF) * 114) / 1000
                if (abs(lum - paperLum) >= 8) nonPaperSampleCount++
            }
        }
        val interiorNonPaperRatio = if (totalInteriorSampleCount > 0) nonPaperSampleCount.toFloat() / totalInteriorSampleCount else 0f
        if (interiorNonPaperRatio < 0.04f && !hasArtworkZone) {
            return false
        }

        if (hasArtworkZone) return true

        // Full color insert / high saturation artwork detection
        var colorPixelCount = 0
        var totalSampled = 0
        for (y in yStart..yEnd step 3) {
            for (x in singleXStart..singleXEnd step 3) {
                totalSampled++
                val pix = bmp.getPixel(x, y)
                val r = (pix shr 16) and 0xFF
                val g = (pix shr 8) and 0xFF
                val b = pix and 0xFF
                val maxC = maxOf(r, g, b)
                val minC = minOf(r, g, b)
                val sat = if (maxC > 0) (maxC - minC).toFloat() / maxC else 0f
                if (sat >= 0.18f && maxC > 50) colorPixelCount++
            }
        }
        if (totalSampled > 0 && (colorPixelCount.toFloat() / totalSampled) >= 0.035f) {
            return true
        }

        return false
    }

    private fun isPureTextBitmap(bmp: Bitmap): Boolean {
        return !isTrueIllustrationPageBitmap(bmp)
    }

    suspend fun scanPdfChaptersAndIllustrationsAsync(
        context: Context,
        bookId: String,
        pdfFile: File,
        totalPages: Int,
        defaultTitle: String,
        isEn: Boolean,
        existingChapters: List<BookChapterEntity>? = null,
        onProgress: suspend (Int) -> Unit = {},
        onUpdated: suspend (List<BookChapterEntity>, List<BookIllustration>) -> Unit
    ) = withContext(Dispatchers.Default) {
        if (!pdfFile.exists() || totalPages <= 0) return@withContext

        val coversDir = File(context.filesDir, "covers/$bookId").apply { mkdirs() }
        val detectedIllusts = mutableListOf<BookIllustration>()

        onProgress(5)

        // Stage 1 & 2: Extract Outline & TOC bookmarks (or preserve existing chapters if already indexed)
        val finalBookmarks = if (existingChapters != null && existingChapters.isNotEmpty()) {
            onProgress(45)
            existingChapters.map { ch ->
                val pArr = runCatching { JSONArray(ch.pagesJson) }.getOrNull() ?: JSONArray()
                var startPage = 0
                for (k in 0 until pArr.length()) {
                    val pStr = pArr.optString(k)
                    val m = Regex("\\[PDF_PAGE:(\\d+)\\]").find(pStr)
                    if (m != null) {
                        startPage = m.groupValues[1].toIntOrNull() ?: 0
                        break
                    }
                }
                PdfBookmark(sanitizeChapterTitle(ch.title), startPage)
            }
        } else {
            extractChaptersWithPdfBox(
                context = context,
                pdfFile = pdfFile,
                totalPages = totalPages,
                defaultTitle = defaultTitle,
                onStageProgress = onProgress
            )
        }

        onProgress(45)

        // Stage 2B: Multi-Signal Pre-pass using PDFBox:
        // Signal 1 (Embedded Image Resources) & Signal 2 (Captions)
        val signal1Pages = mutableSetOf<Int>()
        val signal2Pages = mutableSetOf<Int>()

        runCatching {
            PDFBoxResourceLoader.init(context.applicationContext)
            val memSetting = MemoryUsageSetting.setupTempFileOnly().setTempDir(context.cacheDir)
            PDDocument.load(pdfFile, memSetting).use { document ->
                val safeTotal = if (totalPages > 0) minOf(totalPages, document.numberOfPages) else document.numberOfPages
                val imageOccurrences = mutableMapOf<String, Int>()
                val pageImages = mutableMapOf<Int, MutableList<EmbeddedImageSpec>>()

                val captionRegex = Regex("(?i)\\b(illustration\\s+credit|art\\s+credit|ilustrasi\\s+oleh|illustrated\\s+by|fig\\.|figure\\s+\\d+)\\b")
                val artCreditsHeadingRegex = Regex("(?i)\\b(art\\s+credits|illustration\\s+credits|daftar\\s+ilustrasi|credits)\\b")
                val textStripper = runCatching { PDFTextStripper() }.getOrNull()

                for (p in 1 until safeTotal) {
                    val pdPage = runCatching { document.getPage(p) }.getOrNull() ?: continue
                    val mediaBox = pdPage.mediaBox ?: pdPage.cropBox
                    val pageArea = ((mediaBox?.width ?: 600f) * (mediaBox?.height ?: 800f)).coerceAtLeast(1f)
                    val imgs = inspectPageEmbeddedImages(pdPage.resources, pageArea)
                    if (imgs.isNotEmpty()) {
                        pageImages[p] = imgs.toMutableList()
                        for (img in imgs.distinctBy { it.key }) {
                            imageOccurrences[img.key] = (imageOccurrences[img.key] ?: 0) + 1
                        }
                    }

                    // Signal 2: Caption check
                    if (textStripper != null) {
                        runCatching {
                            textStripper.startPage = p + 1
                            textStripper.endPage = p + 1
                            val pageText = textStripper.getText(document)
                            if (captionRegex.containsMatchIn(pageText)) {
                                val isBackOfBookCredits = p >= (safeTotal - 15) && artCreditsHeadingRegex.containsMatchIn(pageText)
                                if (!isBackOfBookCredits) {
                                    signal2Pages.add(p)
                                }
                            }
                        }
                    }
                }

                // Any non-repeating (unique) image appearing on <= 3 pages across the PDF
                for ((p, imgs) in pageImages) {
                    val hasUniqueSubstantialImage = imgs.any { img ->
                        val occ = imageOccurrences[img.key] ?: 0
                        occ <= 3 && img.isSubstantial
                    }
                    if (hasUniqueSubstantialImage) {
                        signal1Pages.add(p)
                    }
                }
            }
        }

        onProgress(55)

        // Stage 3 (55% -> 95%): Illustration Detection using dedicated PdfRenderer
        // Evaluates EVERY page from 1 until minOf(totalPages, pageCount) — NO SKIPPING!
        runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pageCount = renderer.pageCount
                    val totalToInspect = (minOf(totalPages, pageCount) - 1).coerceAtLeast(1)

                    for (pIdx in 1 until minOf(totalPages, pageCount)) {
                        renderer.openPage(pIdx).use { page ->
                            val thumbW = 280
                            val thumbH = 400
                            val smallBmp = Bitmap.createBitmap(thumbW, thumbH, Bitmap.Config.ARGB_8888)
                            val smallCanvas = Canvas(smallBmp)
                            smallCanvas.drawColor(Color.WHITE)
                            page.render(smallBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            val isSig1 = signal1Pages.contains(pIdx)
                            val isSig2 = signal2Pages.contains(pIdx)
                            val isSig3 = isTrueIllustrationPageBitmap(smallBmp)

                            // Classify as True Illustration Page if Signal 1 OR Signal 3 holds
                            // (or Signal 2 caption with verified non-paper artwork content)
                            val isIllustration = if (isSig3) {
                                true
                            } else if (isSig1) {
                                !isPureTextBitmap(smallBmp)
                            } else if (isSig2) {
                                hasNonPaperContent(smallBmp) && !isPureTextBitmap(smallBmp)
                            } else {
                                false
                            }

                            if (isIllustration) {
                                val illustFile = File(coversDir, "${bookId}_illust_p${pIdx + 1}.jpg")
                                if (!illustFile.exists()) {
                                    FileOutputStream(illustFile).use { out ->
                                        smallBmp.compress(Bitmap.CompressFormat.JPEG, 78, out)
                                    }
                                }
                                val caption = if (isEn) "Illustration — Page ${pIdx + 1}" else "Ilustrasi — Hal ${pIdx + 1}"
                                detectedIllusts.add(
                                    BookIllustration(
                                        uriOrPath = illustFile.absolutePath,
                                        caption = caption,
                                        pageIndexInBook = pIdx
                                    )
                                )
                            }
                            smallBmp.recycle()
                        }
                        if (pIdx % 5 == 0 || pIdx == totalToInspect) {
                            val pct = 55 + (pIdx * 40 / totalToInspect)
                            onProgress(pct.coerceIn(55, 95))
                        }
                    }
                }
            }
        }

        onProgress(100)

        // Build BookChapterEntity list with bookmarks & detected illustrations
        val finalChapters = if (finalBookmarks.isNotEmpty()) {
            val result = mutableListOf<BookChapterEntity>()
            for (i in finalBookmarks.indices) {
                val bm = finalBookmarks[i]
                val sIdx = bm.startPageZero
                val eIdx = if (i + 1 < finalBookmarks.size) finalBookmarks[i + 1].startPageZero.coerceIn(sIdx + 1, totalPages) else totalPages
                val pagesArr = JSONArray()
                for (p in sIdx until eIdx) {
                    val matchingIllust = detectedIllusts.find { it.pageIndexInBook == p || it.caption.contains(" ${p + 1}") }
                    if (matchingIllust != null) {
                        pagesArr.put("[ILLUST:${matchingIllust.uriOrPath}|${matchingIllust.caption}]\n\n[PDF_PAGE:$p]")
                    } else {
                        pagesArr.put("[PDF_PAGE:$p]")
                    }
                }
                val illustArr = JSONArray()
                detectedIllusts.filter { il ->
                    il.pageIndexInBook in sIdx until eIdx
                }.forEach { il ->
                    illustArr.put(JSONObject().apply {
                        put("uriOrPath", il.uriOrPath)
                        put("caption", il.caption)
                        put("pageIndexInBook", il.pageIndexInBook)
                    })
                }
                result.add(
                    BookChapterEntity(
                        bookId = bookId,
                        chapterIndex = i,
                        title = sanitizeChapterTitle(bm.title),
                        pagesJson = pagesArr.toString(),
                        illustrationsJson = illustArr.toString()
                    )
                )
            }
            result
        } else {
            // Fallback sections if no bookmarks found anywhere
            val chCount = when {
                totalPages <= 16 -> 1
                totalPages <= 32 -> 2
                totalPages <= 64 -> 4
                totalPages <= 128 -> 8
                totalPages <= 256 -> 12
                totalPages <= 512 -> 16
                else -> 20
            }
            val pagesPerChapter = ceil(totalPages.toDouble() / chCount.toDouble()).toInt().coerceAtLeast(1)
            val result = mutableListOf<BookChapterEntity>()
            var s = 0
            var cIdx = 0
            while (s < totalPages) {
                val e = (s + pagesPerChapter).coerceAtMost(totalPages)
                val pagesArr = JSONArray()
                for (p in s until e) {
                    val matchingIllust = detectedIllusts.find { it.pageIndexInBook == p || it.caption.contains(" ${p + 1}") }
                    if (matchingIllust != null) {
                        pagesArr.put("[ILLUST:${matchingIllust.uriOrPath}|${matchingIllust.caption}]\n\n[PDF_PAGE:$p]")
                    } else {
                        pagesArr.put("[PDF_PAGE:$p]")
                    }
                }
                val illustArr = JSONArray()
                detectedIllusts.filter { il ->
                    il.pageIndexInBook in s until e
                }.forEach { il ->
                    illustArr.put(JSONObject().apply {
                        put("uriOrPath", il.uriOrPath)
                        put("caption", il.caption)
                        put("pageIndexInBook", il.pageIndexInBook)
                    })
                }
                val title = if (chCount == 1) sanitizeChapterTitle(defaultTitle) else if (isEn) "Section ${cIdx + 1} (pp. ${s + 1}–$e)" else "Bagian ${cIdx + 1} (Hal ${s + 1}–$e)"
                result.add(
                    BookChapterEntity(
                        bookId = bookId,
                        chapterIndex = cIdx,
                        title = title,
                        pagesJson = pagesArr.toString(),
                        illustrationsJson = illustArr.toString()
                    )
                )
                cIdx++
                s = e
            }
            result
        }

        onUpdated(finalChapters, detectedIllusts)
    }
}
