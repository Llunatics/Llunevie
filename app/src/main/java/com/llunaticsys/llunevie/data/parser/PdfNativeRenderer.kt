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
import com.tom_roush.pdfbox.text.TextPosition
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

    const val CURRENT_PDF_TOC_VERSION = 8

    data class PdfBookmark(val title: String, val startPageZero: Int)

    data class VisualLine(
        val text: String,
        val yTop: Float,
        val xLeft: Float,
        val xRight: Float,
        val maxFontSize: Float,
        val isBold: Boolean,
        val isCentered: Boolean
    )

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
            onTheGo = false,
            lastReadAtMillis = 0L,
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

    val LEADING_BULLET_REGEX = Regex("^[•●▪◆❖★☆■□▸►▶✦✧]+\\s*")

    val BARE_NUMBERED_CHAPTER_REGEX = Regex(
        "(?i)^(bab|chapter|bagian|part|act|vol|volume|jilid|section)\\s+(?:\\d+[a-z0-9.-]*|[ivxlcdm]+)$"
    )

    fun sanitizeChapterTitle(raw: String): String {
        var t = raw.trim().replace(LEADING_BULLET_REGEX, "")
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
            .replace(LEADING_BULLET_REGEX, "")
            .trim()

        // Always strip dot-leader page numbers at the end of ToC lines:
        t = t.replace(Regex("""\s*[.·…_]{2,}\s*\d{1,4}$"""), "").trim()

        // Only strip a trailing whitespace + number if not a bare numbered chapter
        // AND the prefix before the trailing number has at least 2 words (or contains :)
        // and is not a bare keyword (bab, chapter, bagian, part, act, volume, jilid).
        val trailingNumMatch = Regex("""\s+\d{1,4}$""").find(t)
        if (trailingNumMatch != null && !BARE_NUMBERED_CHAPTER_REGEX.matches(t)) {
            val prefix = t.substring(0, trailingNumMatch.range.first).trim()
            val words = prefix.split(Regex("""\s+""")).filter { it.isNotEmpty() }
            val bareKeywords = setOf("bab", "chapter", "bagian", "part", "act", "vol", "volume", "jilid", "section")
            val isBareKeyword = words.size == 1 && words[0].lowercase() in bareKeywords
            val hasAtLeastTwoWordsOrColon = words.size >= 2 || prefix.contains(":")
            if (hasAtLeastTwoWordsOrColon && !isBareKeyword) {
                t = prefix
            } else if (!isBareKeyword && words.isNotEmpty()) {
                val specialStandalone = setOf("prolog", "prologue", "epilog", "epilogue", "afterword", "penutup")
                if (words.size == 1 && words[0].lowercase() in specialStandalone) {
                    t = prefix
                }
            }
        }

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
        var s = line.trim()
            .replace(LEADING_BULLET_REGEX, "")
            .replace(Regex("^[|•\\-–—]+\\s*"), "")
            .replace(Regex("\\s*[|•\\-–—]+$"), "")
            .trim()
        if (s.all { it.isDigit() || it.isWhitespace() || it == '.' || it == '-' || it == '–' || it == '—' || it == '|' || it == '•' }) {
            return ""
        }
        if (BARE_NUMBERED_CHAPTER_REGEX.matches(s)) {
            return sanitizeChapterTitle(s)
        }
        val withoutLeadingPage = s.replace(Regex("^\\d{1,4}\\s+"), "").trim()
        if (BARE_NUMBERED_CHAPTER_REGEX.matches(withoutLeadingPage)) {
            return sanitizeChapterTitle(withoutLeadingPage)
        }
        s = withoutLeadingPage

        val trailingNumMatch = Regex("""\s+\d{1,4}$""").find(s)
        if (trailingNumMatch != null && !BARE_NUMBERED_CHAPTER_REGEX.matches(s)) {
            val prefix = tPrefix(s, trailingNumMatch.range.first)
            if (prefix.isNotEmpty()) {
                s = prefix
            }
        }
        s = s.replace(LEADING_BULLET_REGEX, "")
            .replace(Regex("^[|•\\-–—]+\\s*"), "")
            .replace(Regex("\\s*[|•\\-–—]+$"), "")
            .trim()
        if (s.all { it.isDigit() || it.isWhitespace() || it == '.' || it == '-' || it == '–' || it == '—' || it == '|' || it == '•' }) {
            return ""
        }
        return sanitizeChapterTitle(s)
    }

    private fun tPrefix(s: String, endIdx: Int): String {
        val prefix = s.substring(0, endIdx).trim()
        val words = prefix.split(Regex("""\s+""")).filter { it.isNotEmpty() }
        val bareKeywords = setOf("bab", "chapter", "bagian", "part", "act", "vol", "volume", "jilid", "section")
        val isBareKeyword = words.size == 1 && words[0].lowercase() in bareKeywords
        val hasAtLeastTwoWordsOrColon = words.size >= 2 || prefix.contains(":")
        if (hasAtLeastTwoWordsOrColon && !isBareKeyword) {
            return prefix
        } else if (!isBareKeyword && words.isNotEmpty()) {
            val specialStandalone = setOf("prolog", "prologue", "epilog", "epilogue", "afterword", "penutup")
            if (words.size == 1 && words[0].lowercase() in specialStandalone) {
                return prefix
            }
        }
        return ""
    }

    private val CHAPTER_KEYWORD_REGEX = Regex(
        "(?i)^(prolog|prologue|epilog|epilogue|chapter\\s+(?:\\d+[a-z0-9.-]*|[ivxlcdm]+)|bab\\s+(?:\\d+[a-z0-9.-]*|[ivxlcdm]+)|bagian\\s+(?:\\d+[a-z0-9.-]*|[ivxlcdm]+)|part\\s+(?:\\d+[a-z0-9.-]*|[ivxlcdm]+)|act\\s+(?:\\d+[a-z0-9.-]*|[ivxlcdm]+)|interlude|side\\s+story|short\\s+story|ss\\s*[:\\-–—]|afterword|kata\\s+penutup|penutup)\\b.*"
    )

    private val STANDALONE_PREFIX_REGEX = Regex(
        "(?i)^(prolog|prologue|epilog|epilogue|chapter\\s+(?:\\d+[a-z0-9.-]*|[ivxlcdm]+)|bab\\s+(?:\\d+[a-z0-9.-]*|[ivxlcdm]+)|bagian\\s+(?:\\d+[a-z0-9.-]*|[ivxlcdm]+)|part\\s+(?:\\d+[a-z0-9.-]*|[ivxlcdm]+)|act\\s+(?:\\d+[a-z0-9.-]*|[ivxlcdm]+)|interlude|side\\s+story|short\\s+story)[:.\\-–—]?$"
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

    private fun canonicalChapterIdentity(title: String): String {
        val clean = sanitizeChapterTitle(title).lowercase().trim()
        val numMatch = Regex("^(chapter|bab|bagian|part|act)\\s+([\\divxlcdm.-]+)\\b").find(clean)
        if (numMatch != null) {
            val kind = when (val k = numMatch.groupValues[1]) {
                "bab", "chapter" -> "ch"
                else -> k
            }
            return "${kind}_${numMatch.groupValues[2]}"
        }
        if (clean.startsWith("prolog") || clean.startsWith("prologue")) return "prolog"
        if (clean.startsWith("epilog") || clean.startsWith("epilogue")) return "epilog"
        if (clean.startsWith("kata penutup") || clean.startsWith("afterword") || clean == "penutup") return "afterword"
        if (clean.startsWith("monolog") || clean.startsWith("monologue") || clean.endsWith("monologue") || clean.endsWith("monolog")) return "monolog"
        return clean.replace(Regex("[^a-z0-9]"), "")
    }

    private fun isBetterChapterTitle(candidate: String, current: String): Boolean {
        val candTrim = candidate.trim()
        val currTrim = current.trim()
        if (candTrim == currTrim) return false

        val bareWords = setOf("prolog", "prologue", "epilog", "epilogue", "kata penutup", "afterword", "penutup")
        val isCurrBare = BARE_NUMBERED_CHAPTER_REGEX.matches(currTrim) || currTrim.lowercase() in bareWords

        val candHasEllipsis = candTrim.endsWith("...") || candTrim.endsWith("…") || candTrim.endsWith("..")
        val currHasEllipsis = currTrim.endsWith("...") || currTrim.endsWith("…") || currTrim.endsWith("..")

        // 1. If existing title is a bare prefix without a subtitle while candidate has a clean subtitle with length <= 55
        if (isCurrBare && !candHasEllipsis && candTrim.length in (currTrim.length + 2)..55) {
            return true
        }

        // 2. If existing title was longer/noisier (e.g. truncated with ellipsis) than a clean candidate
        if (currHasEllipsis && !candHasEllipsis && candTrim.length in 3..65) {
            return true
        }

        // 3. If neither has ellipsis, but existing title has trailing punctuation while candidate is clean
        if (!candHasEllipsis && !currHasEllipsis) {
            if ((currTrim.endsWith(".") || currTrim.endsWith(",")) && !candTrim.endsWith(".") && !candTrim.endsWith(",")) {
                return true
            }
            if (currTrim.length > 55 && candTrim.length in 5..55 && (candTrim.contains(":") || candTrim.contains("–") || candTrim.contains("-"))) {
                return true
            }
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

        // 3. Deduplicate by normalized title & canonical identity (keep earliest start page)
        val deduped = mutableListOf<PdfBookmark>()
        for (ch in sorted) {
            val chNorm = ch.title.lowercase().replace(Regex("[^a-z0-9]"), "")
            val chCanonical = canonicalChapterIdentity(ch.title)

            val existingIndex = deduped.indexOfFirst { existing ->
                val existingNorm = existing.title.lowercase().replace(Regex("[^a-z0-9]"), "")
                val existingCanonical = canonicalChapterIdentity(existing.title)

                val canonicalMatch = existingCanonical.isNotEmpty() && existingCanonical == chCanonical
                val prefixMatch = existingNorm.length >= 12 && chNorm.length >= 12 &&
                    existingNorm.substring(0, 12) == chNorm.substring(0, 12)
                val normExactMatch = existingNorm.isNotEmpty() && existingNorm == chNorm

                canonicalMatch || prefixMatch || normExactMatch
            }

            if (existingIndex >= 0) {
                val existing = deduped[existingIndex]
                val earliestPage = minOf(existing.startPageZero, ch.startPageZero)
                if (isBetterChapterTitle(ch.title, existing.title)) {
                    deduped[existingIndex] = PdfBookmark(ch.title, earliestPage)
                } else if (earliestPage != existing.startPageZero) {
                    deduped[existingIndex] = PdfBookmark(existing.title, earliestPage)
                }
            } else {
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

    fun extractVisualLinesForPages(
        document: PDDocument,
        startPageZero: Int,
        endPageZeroInclusive: Int
    ): Map<Int, List<VisualLine>> {
        val totalPages = document.numberOfPages
        if (totalPages <= 0 || startPageZero > endPageZeroInclusive || startPageZero >= totalPages) {
            return emptyMap()
        }
        val safeStart = startPageZero.coerceIn(0, totalPages - 1)
        val safeEnd = endPageZeroInclusive.coerceIn(safeStart, totalPages - 1)

        val resultMap = mutableMapOf<Int, List<VisualLine>>()

        val stripper = object : PDFTextStripper() {
            private val currentPagePositions = mutableListOf<TextPosition>()

            init {
                sortByPosition = true
            }

            override fun startPage(page: PDPage) {
                currentPagePositions.clear()
            }

            override fun processTextPosition(tp: TextPosition) {
                if (tp.unicode?.isNotBlank() == true) {
                    currentPagePositions.add(tp)
                }
            }

            override fun endPage(page: PDPage) {
                val pZero = currentPageNo - 1
                if (currentPagePositions.isEmpty()) {
                    resultMap[pZero] = emptyList()
                    return
                }

                // 1. Sort the collected TextPosition list by yDirAdj ascending, then xDirAdj ascending
                val sortedPositions = currentPagePositions.sortedWith(
                    compareBy<TextPosition> { it.yDirAdj }
                        .thenBy { it.xDirAdj }
                )

                // 2. Group consecutive TextPositions into horizontal line clusters
                val clusters = mutableListOf<MutableList<TextPosition>>()
                for (tp in sortedPositions) {
                    val currentCluster = clusters.lastOrNull()
                    if (currentCluster == null) {
                        clusters.add(mutableListOf(tp))
                    } else {
                        val clusterAvgY = currentCluster.map { it.yDirAdj }.average().toFloat()
                        val clusterMaxHeight = currentCluster.maxOf {
                            val h = it.heightDir
                            if (h > 0f) h else it.fontSizeInPt
                        }
                        if (abs(tp.yDirAdj - clusterAvgY) <= maxOf(3.8f, clusterMaxHeight * 0.48f)) {
                            currentCluster.add(tp)
                        } else {
                            clusters.add(mutableListOf(tp))
                        }
                    }
                }

                val pageW = (page.mediaBox?.width ?: 600f).coerceAtLeast(1f)
                val lines = mutableListOf<VisualLine>()

                for (cluster in clusters) {
                    if (cluster.isEmpty()) continue
                    val linePositions = cluster.sortedBy { it.xDirAdj }
                    val sb = StringBuilder()
                    var prevEndX = Float.NaN

                    for (tp in linePositions) {
                        val u = tp.unicode.orEmpty()
                        if (u.isEmpty()) continue
                        if (!prevEndX.isNaN()) {
                            val gap = tp.xDirAdj - prevEndX
                            val threshold = maxOf(tp.widthOfSpace * 0.28f, tp.fontSizeInPt * 0.12f)
                            if (gap > threshold && !sb.endsWith(" ") && !sb.endsWith("-") && !sb.endsWith("–") && !sb.endsWith("—")) {
                                sb.append(" ")
                            }
                        }
                        sb.append(u)
                        prevEndX = tp.xDirAdj + tp.widthDirAdj
                    }

                    val xLeft = linePositions.first().xDirAdj
                    val xRight = linePositions.maxOf { it.xDirAdj + it.widthDirAdj }
                    val yTop = linePositions.map { it.yDirAdj }.average().toFloat()
                    val maxFontSize = linePositions.maxOf { it.fontSizeInPt }
                    val isBold = linePositions.any {
                        val fn = it.font?.name.orEmpty()
                        fn.contains("Bold", true) ||
                            fn.contains("Semibold", true) ||
                            fn.contains("Medium", true) ||
                            (it.font?.fontDescriptor?.fontWeight ?: 400f) >= 600f
                    }
                    val lineW = (xRight - xLeft).coerceAtLeast(0f)
                    val midX = (xLeft + xRight) / 2f
                    val isCentered = lineW < pageW * 0.82f && abs(midX - pageW / 2f) <= pageW * 0.18f
                    val cleanText = sb.toString().replace(Regex("\\s+"), " ").trim()

                    if (cleanText.isNotEmpty()) {
                        lines.add(
                            VisualLine(
                                text = cleanText,
                                yTop = yTop,
                                xLeft = xLeft,
                                xRight = xRight,
                                maxFontSize = maxFontSize,
                                isBold = isBold,
                                isCentered = isCentered
                            )
                        )
                    }
                }

                resultMap[pZero] = lines.sortedBy { it.yTop }
            }
        }

        stripper.startPage = safeStart + 1
        stripper.endPage = safeEnd + 1
        runCatching {
            stripper.writeText(document, java.io.StringWriter())
        }

        return resultMap
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

                // Extract Visual Lines for all pages 0 until safeTotalPages
                val visualLinesMap = extractVisualLinesForPages(document, 0, safeTotalPages - 1)

                // Stage 2: Contents / Table of Contents / Daftar Isi Pages (25% -> 35%)
                val maxScan = minOf(25, safeTotalPages)
                val tocHeadingRegex = Regex(
                    "\\b(Table\\s+of\\s+Contents|Contents|Daftar\\s+Isi|Table\\s+des\\s+mati[èe]res|Inhaltsverzeichnis|Índice|Indice)\\b",
                    RegexOption.IGNORE_CASE
                )
                val tocPages = mutableListOf<Int>()
                val unnumberedTocCandidatesMap = mutableMapOf<String, String>()
                val pageLabels = runCatching { document.documentCatalog.pageLabels?.pageIndicesByLabels }.getOrNull()
                val numberedLineRegex = Regex("^(.+?)(?:[.\\s_\\-–—·…]{2,}|\\t+)\\s*(\\d{1,4})\\s*$")

                for (p in 0 until maxScan) {
                    val pageVLines = visualLinesMap[p] ?: emptyList()
                    val hasHeading = pageVLines.any { tocHeadingRegex.containsMatchIn(it.text) }
                    val keywordMatchCount = pageVLines.count { CHAPTER_KEYWORD_REGEX.matches(it.text.replace(LEADING_BULLET_REGEX, "").trim()) }
                    if (hasHeading || keywordMatchCount >= 3) {
                        tocPages.add(p)
                    } else if (tocPages.isNotEmpty() && tocPages.last() == p - 1) {
                        val pdPage = runCatching { document.getPage(p) }.getOrNull()
                        val hasLinks = pdPage?.annotations?.filterIsInstance<PDAnnotationLink>()?.isNotEmpty() == true
                        val hasNumberedLines = pageVLines.count { vl ->
                            numberedLineRegex.containsMatchIn(vl.text)
                        } >= 3
                        if (hasLinks || hasNumberedLines) {
                            tocPages.add(p)
                        }
                    }
                }

                onStageProgress(35)

                // Process TOC Pages: Links, Numbered TOC lines, and Unnumbered TOC lines
                val linkPageOccurrences = mutableMapOf<String, MutableSet<Int>>()
                val normBookTitle = defaultTitle.lowercase().replace(Regex("[^a-z0-9]"), "")
                fun norm(s: String) = s.lowercase().replace(Regex("[^a-z0-9]"), "")

                fun isNoiseOrWatermark(text: String): Boolean {
                    val lower = text.lowercase()
                    if (lower.contains("http://") || lower.contains("https://") || lower.contains("www.") ||
                        lower.contains(".com") || lower.contains(".org") || lower.contains(".net") || lower.contains(".id") ||
                        lower.contains("arakiyota") || lower.contains("youzitsu")) {
                        return true
                    }
                    val n = norm(text)
                    if (normBookTitle.isNotEmpty() && (n == normBookTitle || (n.contains(normBookTitle) && n.length < normBookTitle.length + 5))) {
                        return true
                    }
                    return false
                }

                for (tocPageZero in tocPages) {
                    val pdPage = runCatching { document.getPage(tocPageZero) }.getOrNull() ?: continue
                    val pageVLines = visualLinesMap[tocPageZero] ?: emptyList()
                    val mediaBox = pdPage.mediaBox ?: pdPage.cropBox
                    val pageH = (mediaBox?.height ?: 800f).coerceAtLeast(1f)

                    // Strategy 2A: Interactive Link Annotations (PDAnnotationLink)
                    val links = runCatching { pdPage.annotations.filterIsInstance<PDAnnotationLink>() }.getOrDefault(emptyList())
                    for (link in links) {
                        val dest = link.destination ?: (link.action as? PDActionGoTo)?.destination ?: continue
                        val targetPage = resolveDestinationPage(document, dest) ?: continue
                        if (targetPage !in 0 until safeTotalPages) continue

                        val rect = link.rectangle
                        var linkText: String? = null
                        if (rect != null) {
                            val linkTop = (pageH - maxOf(rect.lowerLeftY, rect.upperRightY)).coerceAtLeast(0f)
                            val linkBottom = (pageH - minOf(rect.lowerLeftY, rect.upperRightY)).coerceAtLeast(0f)
                            val overlappingLine = pageVLines.find { vl ->
                                vl.yTop in (linkTop - 6f)..(linkBottom + 6f)
                            }
                            linkText = overlappingLine?.text
                        }

                        if (!linkText.isNullOrBlank()) {
                            val clean = sanitizeChapterTitle(linkText)
                            val normL = norm(clean)
                            if (normL.isNotEmpty()) {
                                linkPageOccurrences.getOrPut(normL) { mutableSetOf() }.add(tocPageZero)
                            }
                            if (clean.length in 2..120) {
                                tocBookmarks.add(PdfBookmark(clean, targetPage))
                            }
                        }
                    }

                    // Strategy 2B & 2C: Numbered & Unnumbered TOC Lines
                    for (vl in pageVLines) {
                        val trimmed = vl.text.trim()
                        if (trimmed.length < 3) continue
                        if (tocHeadingRegex.containsMatchIn(trimmed)) {
                            continue
                        }

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
                                } else if (clean.length in 3..75 && isPlausibleChapterTitle(clean) && !isNoiseOrWatermark(clean)) {
                                    val n = norm(clean)
                                    if (n.length >= 3) {
                                        unnumberedTocCandidatesMap[n] = clean
                                    }
                                }
                            }
                        } else {
                            val clean = sanitizeChapterTitle(trimmed)
                            if (clean.length in 3..75 && isPlausibleChapterTitle(clean) && !isNoiseOrWatermark(clean)) {
                                val n = norm(clean)
                                if (n.length >= 3) {
                                    unnumberedTocCandidatesMap[n] = clean
                                }
                            }
                        }
                    }
                }

                // Stage 3: Repeating Running Header / Watermark Frequency Analysis Across All Pages
                onStageProgress(38)

                val top2Freq = mutableMapOf<String, MutableSet<Int>>()
                val bot2Freq = mutableMapOf<String, MutableSet<Int>>()
                val pageBottomTitles = arrayOfNulls<String>(safeTotalPages)

                fun isValidChapterTitle(t: String): Boolean {
                    val n = norm(t)
                    return t.length in 3..85 && n != normBookTitle && n.isNotEmpty() &&
                        !t.all { it.isDigit() || it.isWhitespace() || it == '.' }
                }

                for (p in 0 until safeTotalPages) {
                    val vLines = visualLinesMap[p] ?: emptyList()
                    val top2 = vLines.take(2)
                    for (line in top2) {
                        val n = norm(line.text)
                        if (n.isNotEmpty()) {
                            top2Freq.getOrPut(n) { mutableSetOf() }.add(p)
                        }
                    }
                    val bot2 = vLines.takeLast(2)
                    for (line in bot2) {
                        val n = norm(line.text)
                        if (n.isNotEmpty()) {
                            bot2Freq.getOrPut(n) { mutableSetOf() }.add(p)
                        }
                    }
                    val botCands = bot2.map { cleanFooterTitle(it.text) }.filter { it.isNotBlank() }
                    val bestFooter = botCands.lastOrNull { isValidChapterTitle(it) }
                    if (bestFooter != null) {
                        pageBottomTitles[p] = bestFooter
                    }
                }

                val repeatingTopHeaders = mutableSetOf<String>()
                for ((n, pages) in top2Freq) {
                    if (pages.size >= 5) {
                        if (!BARE_NUMBERED_CHAPTER_REGEX.matches(n) && !CHAPTER_KEYWORD_REGEX.matches(n)) {
                            repeatingTopHeaders.add(n)
                        }
                    }
                }

                val globalWatermarkBlacklist = mutableSetOf<String>()
                for ((nLink, pages) in linkPageOccurrences) {
                    if (pages.size >= 5) {
                        globalWatermarkBlacklist.add(nLink)
                    }
                }
                for ((nBot, pages) in bot2Freq) {
                    if (pages.size >= 5 && !CHAPTER_KEYWORD_REGEX.matches(nBot) && !BARE_NUMBERED_CHAPTER_REGEX.matches(nBot)) {
                        globalWatermarkBlacklist.add(nBot)
                    }
                }

                // Stage 4: 4-Pattern Full-Book Scan
                val conditionBBookmarks = mutableListOf<PdfBookmark>()
                val tocMaxPage = tocPages.maxOrNull() ?: -1

                for (p in 0 until safeTotalPages) {
                    val pdPage = runCatching { document.getPage(p) }.getOrNull()
                    val mediaBox = pdPage?.mediaBox ?: pdPage?.cropBox
                    val pageH = (mediaBox?.height ?: 800f).coerceAtLeast(1f)
                    val rawVLines = visualLinesMap[p] ?: emptyList()
                    if (rawVLines.isEmpty()) continue

                    val contentLines = rawVLines.filterIndexed { index, vl ->
                        val text = vl.text
                        val isPurePageNum = text.all { it.isDigit() || it.isWhitespace() || it in ".-–—|•[]" }
                        if (isPurePageNum) return@filterIndexed false
                        if (isNoiseOrWatermark(text)) return@filterIndexed false
                        val n = norm(text)
                        if (index < 3 && (n in repeatingTopHeaders || n == normBookTitle)) return@filterIndexed false
                        true
                    }

                    if (contentLines.isEmpty()) continue

                    val isSeparatorPage = contentLines.size in 1..5
                    val candidateTopLines = if (isSeparatorPage) contentLines else contentLines.filter { it.yTop <= pageH * 0.42f }.take(5)
                    val pageMedianFontSize = contentLines.map { it.maxFontSize }.sorted().let {
                        if (it.isEmpty()) 12f else it[it.size / 2]
                    }

                    var patternAMatched = false

                    // Pattern A — Keyword Chapter Heading + 2-Line Subtitle Lookahead (Bab 1 + Lagu Kesepian)
                    for (line in candidateTopLines) {
                        val stripped = line.text.replace(LEADING_BULLET_REGEX, "").trim()
                        if (CHAPTER_KEYWORD_REGEX.matches(stripped)) {
                            var title = sanitizeChapterTitle(stripped)
                            if (STANDALONE_PREFIX_REGEX.matches(stripped)) {
                                val lineIdx = contentLines.indexOf(line)
                                val nextLine = contentLines.getOrNull(lineIdx + 1)
                                if (nextLine != null && (nextLine.yTop - line.yTop) in 2f..65f) {
                                    val nextClean = sanitizeChapterTitle(nextLine.text.replace(LEADING_BULLET_REGEX, ""))
                                    val nextWords = nextClean.split(Regex("\\s+")).filter { it.isNotEmpty() }
                                    val isCenteredOrBold = nextLine.isCentered || nextLine.isBold
                                    if (nextClean.length in 2..60 &&
                                        nextWords.size <= 8 &&
                                        !CHAPTER_KEYWORD_REGEX.matches(nextClean) &&
                                        (!nextClean.endsWith(".") || isCenteredOrBold)
                                    ) {
                                        title = "${title.trimEnd(':', '.', '-', '–', '—').trim()}: $nextClean"
                                    }
                                }
                            }
                            val bm = PdfBookmark(title, p)
                            conditionBBookmarks.add(bm)
                            additiveBookmarks.add(bm)
                            patternAMatched = true
                            break
                        }
                    }

                    var patternBMatched = false
                    // Pattern B — Top-of-Page Standalone Bulleted Heading (• Dua Guru, Ujian Khusus Yang Ditakdirkan)
                    if (!patternAMatched) {
                        val firstLine = contentLines.firstOrNull()
                        if (firstLine != null && (firstLine.yTop <= pageH * 0.32f || isSeparatorPage) && LEADING_BULLET_REGEX.containsMatchIn(firstLine.text)) {
                            val secondLine = contentLines.getOrNull(1)
                            val secondHasBullet = secondLine != null && LEADING_BULLET_REGEX.containsMatchIn(secondLine.text)
                            if (!secondHasBullet) {
                                val bulletTitle = sanitizeChapterTitle(firstLine.text.replace(LEADING_BULLET_REGEX, ""))
                                val words = bulletTitle.split(Regex("\\s+")).filter { it.isNotEmpty() }
                                val startsWithAlnum = bulletTitle.firstOrNull()?.isLetterOrDigit() == true
                                val endsWithoutPunct = !bulletTitle.endsWith(",") && !bulletTitle.endsWith(";")
                                val isDistinctHeading = firstLine.isCentered || firstLine.isBold || firstLine.maxFontSize >= pageMedianFontSize + 0.3f || isSeparatorPage
                                if (bulletTitle.length in 4..75 && words.size <= 10 && startsWithAlnum && endsWithoutPunct && isDistinctHeading) {
                                    val bm = PdfBookmark(bulletTitle, p)
                                    conditionBBookmarks.add(bm)
                                    additiveBookmarks.add(bm)
                                    patternBMatched = true
                                }
                            }
                        }
                    }

                    // Pattern C — Unnumbered Daftar Isi / Table of Contents Title Match
                    if (!patternAMatched && !patternBMatched && p > tocMaxPage && unnumberedTocCandidatesMap.isNotEmpty()) {
                        for (line in candidateTopLines) {
                            val n = norm(line.text)
                            val matchedCand = unnumberedTocCandidatesMap[n]
                            if (matchedCand != null) {
                                val bm = PdfBookmark(matchedCand, p)
                                additiveBookmarks.add(bm)
                                break
                            } else {
                                val lineIdx = contentLines.indexOf(line)
                                val nextLine = contentLines.getOrNull(lineIdx + 1)
                                if (nextLine != null && (nextLine.yTop - line.yTop) in 2f..65f) {
                                    val twoLineNorm = norm("${line.text} ${nextLine.text}")
                                    val matchedTwo = unnumberedTocCandidatesMap[twoLineNorm]
                                    if (matchedTwo != null) {
                                        val bm = PdfBookmark(matchedTwo, p)
                                        additiveBookmarks.add(bm)
                                        break
                                    }
                                }
                            }
                        }
                    }

                    // Pattern D — Running Bottom Footer Transition & Top/Bottom Match
                    val bestFooter = pageBottomTitles[p]
                    if (bestFooter != null && !conditionBBookmarks.any { it.startPageZero == p }) {
                        val cleanFooter = sanitizeChapterTitle(bestFooter)
                        if (cleanFooter.length in 3..85 && (CHAPTER_KEYWORD_REGEX.matches(cleanFooter) || CHAPTER_KEYWORD_SUFFIX_REGEX.matches(cleanFooter))) {
                            val n = norm(cleanFooter)
                            if (n != normBookTitle && n !in globalWatermarkBlacklist && !cleanFooter.all { it.isDigit() || it.isWhitespace() || it == '.' }) {
                                if (!conditionBBookmarks.any { norm(it.title) == n }) {
                                    val bm = PdfBookmark(cleanFooter, p)
                                    conditionBBookmarks.add(bm)
                                    additiveBookmarks.add(bm)
                                }
                            }
                        }
                    }
                }

                // Running bottom footer transition check repeating across >= 4 pages (Pattern D footer transition)
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

                onStageProgress(45)

                val validConditionB = conditionBBookmarks.filter { norm(it.title) !in globalWatermarkBlacklist }
                val explicitCombined = detectedBookmarks + tocBookmarks + validConditionB
                val structuredChapters = deduplicateChapters(explicitCombined + additiveBookmarks, safeTotalPages, defaultTitle, globalWatermarkBlacklist)

                finalResultChapters = structuredChapters
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

    private fun buildBookChaptersFromBookmarks(
        bookId: String,
        bookmarks: List<PdfBookmark>,
        totalPages: Int,
        illustrations: List<BookIllustration>,
        defaultTitle: String,
        isEn: Boolean
    ): List<BookChapterEntity> {
        if (bookmarks.isEmpty()) {
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
                    val matchingIllust = illustrations.find { it.pageIndexInBook == p }
                    if (matchingIllust != null) {
                        pagesArr.put("[ILLUST:${matchingIllust.uriOrPath}|${matchingIllust.caption}]\n\n[PDF_PAGE:$p]")
                    } else {
                        pagesArr.put("[PDF_PAGE:$p]")
                    }
                }
                val illustArr = JSONArray()
                illustrations.filter { il ->
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
            return result
        }

        val sorted = bookmarks
            .filter { it.startPageZero in 0 until totalPages }
            .distinctBy { it.startPageZero }
            .sortedBy { it.startPageZero }
            .toMutableList()

        if (sorted.isNotEmpty() && sorted.first().startPageZero > 0) {
            sorted.add(0, PdfBookmark(sanitizeChapterTitle(defaultTitle), 0))
        }

        val result = mutableListOf<BookChapterEntity>()
        for (i in sorted.indices) {
            val bm = sorted[i]
            val sIdx = bm.startPageZero
            val eIdx = if (i + 1 < sorted.size) {
                sorted[i + 1].startPageZero.coerceIn(sIdx + 1, totalPages)
            } else {
                totalPages
            }
            val pagesArr = JSONArray()
            for (p in sIdx until eIdx) {
                val matchingIllust = illustrations.find { it.pageIndexInBook == p }
                if (matchingIllust != null) {
                    pagesArr.put("[ILLUST:${matchingIllust.uriOrPath}|${matchingIllust.caption}]\n\n[PDF_PAGE:$p]")
                } else {
                    pagesArr.put("[PDF_PAGE:$p]")
                }
            }
            val illustArr = JSONArray()
            illustrations.filter { il ->
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
        return result
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

        // Stage 1 & 2: Extract Outline & TOC bookmarks
        val rawBookmarks = extractChaptersWithPdfBox(
            context = context,
            pdfFile = pdfFile,
            totalPages = totalPages,
            defaultTitle = defaultTitle,
            onStageProgress = onProgress
        )

        val finalBookmarks = rawBookmarks
            .filter { it.startPageZero in 0 until totalPages }
            .distinctBy { it.startPageZero }
            .sortedBy { it.startPageZero }
            .toMutableList()
            .apply {
                if (isNotEmpty() && first().startPageZero > 0) {
                    add(0, PdfBookmark(sanitizeChapterTitle(defaultTitle), 0))
                }
            }

        onProgress(45)

        if (finalBookmarks.size >= 2) {
            val preliminaryChapters = buildBookChaptersFromBookmarks(
                bookId = bookId,
                bookmarks = finalBookmarks,
                totalPages = totalPages,
                illustrations = emptyList(),
                defaultTitle = defaultTitle,
                isEn = isEn
            )
            onUpdated(preliminaryChapters, emptyList())
        }

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

        val uniqueIllusts = detectedIllusts.distinctBy { it.pageIndexInBook }.sortedBy { it.pageIndexInBook }
        detectedIllusts.clear()
        detectedIllusts.addAll(uniqueIllusts)
        detectedIllusts.sortBy { it.pageIndexInBook }

        val finalChapters = buildBookChaptersFromBookmarks(
            bookId = bookId,
            bookmarks = finalBookmarks,
            totalPages = totalPages,
            illustrations = detectedIllusts,
            defaultTitle = defaultTitle,
            isEn = isEn
        )

        onUpdated(finalChapters, detectedIllusts)
    }

    fun isPdfPageProtectedIllustration(
        pageIndex: Int,
        knownIllustrationPages: Set<Int>
    ): Boolean {
        return pageIndex == 0 || pageIndex in knownIllustrationPages
    }

    fun buildPdfThemeColorFilter(
        effectiveTheme: com.llunaticsys.llunevie.model.AppThemeMode,
        amoledBlack: Boolean
    ): androidx.compose.ui.graphics.ColorFilter? {
        // WHITE theme uses original PDF colors (no filter)
        if (effectiveTheme == com.llunaticsys.llunevie.model.AppThemeMode.WHITE) {
            return null
        }

        // Define target paper (bg) and ink (fg) colors for each theme
        val themeColors = when (effectiveTheme) {
            com.llunaticsys.llunevie.model.AppThemeMode.PAPER -> {
                // Paper theme: #F5F2EA background, #23201A text
                arrayOf(245f, 242f, 234f, 35f, 32f, 26f)
            }
            com.llunaticsys.llunevie.model.AppThemeMode.SEPIA -> {
                // Sepia theme: #EFE6D5 background, #2C2217 text
                arrayOf(239f, 230f, 213f, 44f, 34f, 23f)
            }
            com.llunaticsys.llunevie.model.AppThemeMode.BLACK -> {
                if (amoledBlack) {
                    // AMOLED Black: #000000 background, #E8E4DB text
                    arrayOf(0f, 0f, 0f, 232f, 228f, 219f)
                } else {
                    // Regular Black: #141413 background, #EDEAE2 text
                    arrayOf(20f, 20f, 19f, 237f, 234f, 226f)
                }
            }
            else -> return null // WHITE already handled above
        }
        val bgR = themeColors[0]
        val bgG = themeColors[1]
        val bgB = themeColors[2]
        val fgR = themeColors[3]
        val fgG = themeColors[4]
        val fgB = themeColors[5]

        // Build ColorMatrix that maps:
        // - input black (0, 0, 0) -> foreground color (text)
        // - input white (255, 255, 255) -> background color (paper)
        val sR = (bgR - fgR) / 255f
        val sG = (bgG - fgG) / 255f
        val sB = (bgB - fgB) / 255f

        val matrix = androidx.compose.ui.graphics.ColorMatrix(
            floatArrayOf(
                sR, 0f, 0f, 0f, fgR,
                0f, sG, 0f, 0f, fgG,
                0f, 0f, sB, 0f, fgB,
                0f, 0f, 0f, 1f, 0f
            )
        )

        return androidx.compose.ui.graphics.ColorFilter.colorMatrix(matrix)
    }

    fun buildAndroidPdfThemeColorFilter(
        effectiveTheme: com.llunaticsys.llunevie.model.AppThemeMode,
        amoledBlack: Boolean
    ): android.graphics.ColorMatrixColorFilter? {
        if (effectiveTheme == com.llunaticsys.llunevie.model.AppThemeMode.WHITE) {
            return null
        }

        val themeColors = when (effectiveTheme) {
            com.llunaticsys.llunevie.model.AppThemeMode.PAPER -> {
                arrayOf(245f, 242f, 234f, 35f, 32f, 26f)
            }
            com.llunaticsys.llunevie.model.AppThemeMode.SEPIA -> {
                arrayOf(239f, 230f, 213f, 44f, 34f, 23f)
            }
            com.llunaticsys.llunevie.model.AppThemeMode.BLACK -> {
                if (amoledBlack) {
                    arrayOf(0f, 0f, 0f, 232f, 228f, 219f)
                } else {
                    arrayOf(20f, 20f, 19f, 237f, 234f, 226f)
                }
            }
            else -> return null
        }
        val bgR = themeColors[0]
        val bgG = themeColors[1]
        val bgB = themeColors[2]
        val fgR = themeColors[3]
        val fgG = themeColors[4]
        val fgB = themeColors[5]

        val sR = (bgR - fgR) / 255f
        val sG = (bgG - fgG) / 255f
        val sB = (bgB - fgB) / 255f

        val matrix = android.graphics.ColorMatrix(
            floatArrayOf(
                sR, 0f, 0f, 0f, fgR,
                0f, sG, 0f, 0f, fgG,
                0f, 0f, sB, 0f, fgB,
                0f, 0f, 0f, 1f, 0f
            )
        )

        return android.graphics.ColorMatrixColorFilter(matrix)
    }
}
