package com.llunaticsys.llunevie.data.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.text.Layout
import android.text.SpannableString
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.util.LruCache
import com.llunaticsys.llunevie.data.local.BookChapterEntity
import com.llunaticsys.llunevie.data.local.BookEntity
import com.llunaticsys.llunevie.normalizeTranslationLangCode
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
import kotlin.math.roundToInt

object PdfNativeRenderer {

    const val CURRENT_PDF_TOC_VERSION = 12

    data class PdfBookmark(val title: String, val startPageZero: Int)

    private data class PatternCCandidate(
        val bookmark: PdfBookmark,
        val isBoldOrLargerFont: Boolean
    )

    data class VisualLine(
        val text: String,
        val yTop: Float,
        val xLeft: Float,
        val xRight: Float,
        val maxFontSize: Float,
        val isBold: Boolean,
        val isCentered: Boolean
    )

    data class PdfParagraphBlock(
        val text: String,
        val bulletPrefix: String,
        val pdfLeft: Float,
        val pdfTop: Float,
        val pdfRight: Float,
        val pdfBottom: Float,
        val colRight: Float,
        val firstLineIndentPdf: Float,
        val hangingIndentPdf: Float,
        val avgFontSizePt: Float,
        val lineSpacingRatio: Float,
        val isBold: Boolean,
        val isCentered: Boolean,
        val pageWidthPt: Float,
        val pageHeightPt: Float
    )

    private var cachedDocPath: String? = null
    private var cachedDoc: PDDocument? = null
    private val docCacheLock = Any()
    private val pageParagraphBlocksCache = object : LruCache<String, List<PdfParagraphBlock>>(64) {
        override fun sizeOf(key: String, value: List<PdfParagraphBlock>): Int = 1
    }

    private data class WordChunk(
        val text: String,
        val yTop: Float,
        val xLeft: Float,
        val xRight: Float,
        val height: Float,
        val maxFontSize: Float,
        val isBold: Boolean
    )

    private val renderMutex = Mutex()

    // High-speed in-memory Bitmap LRU Cache capped to at most 32 page bitmaps (capacity >= 16)
    private val pageBitmapCache = object : LruCache<String, Bitmap>(32) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    // Dedicated in-memory Bitmap LRU Cache for translated PDF page bitmaps
    val translatedPdfBitmapCache = object : LruCache<String, Bitmap>(16) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    fun getCachedTranslatedBitmap(key: String): Bitmap? {
        val cached = translatedPdfBitmapCache.get(key)
        return if (cached != null && !cached.isRecycled) cached else null
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
        val bmp = pageBitmapCache.get("$pdfFilePath:$pageIndex:$widthBucket")
        if (bmp != null && !bmp.isRecycled) return bmp
        return null
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

    fun renderPage(
        pdfFilePath: String,
        pageIndex: Int,
        targetWidthPx: Int = 960
    ): Bitmap? {
        if (pageIndex < 0) return null
        val boundedTargetW = targetWidthPx.coerceIn(360, 1080)
        val widthBucket = ((boundedTargetW / 120) * 120).coerceAtLeast(360)
        val cacheKey = "$pdfFilePath:$pageIndex:$widthBucket"
        pageBitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return cached
        }

        val file = File(pdfFilePath)
        if (!file.exists()) return null

        return runCatching {
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
            renderPage(pdfFilePath, pageIndex, targetWidthPx)
        }
    }

    fun getOrRenderTranslatedPdfPageBitmap(
        pdfFilePath: String,
        pageIndex: Int,
        blocks: List<PdfParagraphBlock>,
        translatedTexts: List<String>,
        displayMode: String,
        targetWidthPx: Int = 960,
        isIllustrationPage: Boolean = false,
        targetLang: String = "",
        engine: String = ""
    ): Bitmap? {
        if (pageIndex < 0 || blocks.isEmpty() || translatedTexts.isEmpty()) return null
        val normLang = normalizeTranslationLangCode(targetLang)
        val normMode = displayMode.trim().lowercase()
        val cacheKey = "$pdfFilePath|$pageIndex|$normLang|$engine|$normMode|$targetWidthPx"
        translatedPdfBitmapCache.get(cacheKey)?.let { cached ->
            if (!cached.isRecycled) return cached
        }

        val boundedTargetW = targetWidthPx.coerceIn(360, 1080)
        val widthBucket = ((boundedTargetW / 120) * 120).coerceAtLeast(360)
        val origBmp = getCachedBitmap(pdfFilePath, pageIndex, widthBucket)
            ?: renderPage(pdfFilePath, pageIndex, targetWidthPx)
            ?: return null

        val rendered = renderTranslatedPdfPageBitmap(
            origBmp = origBmp,
            blocks = blocks,
            translatedTexts = translatedTexts,
            displayMode = displayMode,
            isIllustrationPage = isIllustrationPage
        )
        translatedPdfBitmapCache.put(cacheKey, rendered)
        return rendered
    }

    val LEADING_BULLET_REGEX = Regex(
        "^(?:[•●○◦◯⚬∙⋅°º¤▪▫■□◆◇❖★☆✦✧▸▹►▻▶▷❥❧\\u25A0-\\u25FF\\u2600-\\u27BF\\uF000-\\uF0FF]+|[oO](?=\\s+[A-Z0-9]))\\s*"
    )

    val BARE_NUMBERED_CHAPTER_REGEX = Regex(
        "(?i)^(?:bab|chapter|bagian|part|act|episode|vol|volume|jilid|section|selingan)\\s*(?:\\d+[a-z0-9.-]*|[ivxlcdm]+)$"
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

    private val NORM_CHAPTER_KEYWORD_REGEX = Regex(
        "(?i)^(?:prolog|prologue|epilog|epilogue|monolog|monologue|interlude|selingan[0-9ivxlcdm]*|sidestory|shortstory|afterword|katapenutup|penutup|catatantambahanpenulis|(?:chapter|bab|bagian|part|act|episode)\\d+).*"
    )

    private val CHAPTER_KEYWORD_REGEX = Regex(
        "(?i)^(?:(?:bab|chapter|bagian|part|act|episode)\\s*(?:\\d+[a-z0-9.-]*|[ivxlcdm]+)(?:\\s*$|\\s*[:.\\-\\u2013\\u2014~|]\\s*.*|\\s+.*)|(?:prolog|prologue|epilog|epilogue|monolog|monologue|interlude|selingan(?:\\s+[0-9ivxlcdm]+)?|side\\s+story|short\\s+story|afterword|kata\\s+penutup|penutup|catatan\\s+tambahan\\s+penulis)(?:\\s*$|\\s*[:.\\-\\u2013\\u2014~|]\\s*.*|\\s+.*)|ss\\s*[:.\\-\\u2013\\u2014]\\s*\\S+.*)"
    )

    private val STANDALONE_PREFIX_REGEX = Regex(
        "(?i)^(?:(?:bab|chapter|bagian|part|act|episode)\\s*(?:\\d+[a-z0-9.-]*|[ivxlcdm]+)|prolog|prologue|epilog|epilogue|monolog|monologue|interlude|selingan(?:\\s+[0-9ivxlcdm]+)?|side\\s+story|short\\s+story)\\s*[:.\\-\\u2013\\u2014]?$"
    )

    private val NARRATIVE_STARTER_REGEX = Regex(
        "(?i)^(?:aku|saya|gue|gua|kau|kamu|dia|ia|mereka|kami|kita|kalau|jika|bila|saat|ketika|setelah|sebelum|meski|meskipun|walaupun|walau|namun|tetapi|tapi|karena|sebab|dengan|tanpa|sudah|belum|tidak|tak|bukan|jangan|dan|atau|lalu|kemudian|sementara|sedangkan|mungkin|sepertinya|tampaknya|rasanya|entah|yah|nah|oh|ah|eh|hei|halo|maaf|terima\\s+kasih|itulah|inilah|begitu|begitulah|pada\\s+akhirnya|dulu\\s+saat|hari\\s+itu\\s+aku)\\b.*"
    )

    private fun isLikelyNarrativeOrDialogueLine(rawText: String, cleanedText: String): Boolean {
        val r = rawText.trim()
        val c = cleanedText.trim()
        if (r.isEmpty() || c.isEmpty()) return true

        // 1. Starts or ends with dialogue quotes, dashes, or ellipsis in raw text BEFORE sanitization
        val dialogueAndDashChars = setOf('"', '“', '”', '\'', '‘', '’', '「', '『', '」', '』', '-', '\u2013', '\u2014', '\u2015', '\u2026')
        if (r.first() in dialogueAndDashChars || r.last() in dialogueAndDashChars) return true
        if (r.endsWith("..") || r.endsWith(",") || r.endsWith(";") || r.endsWith("!") || r.endsWith("?")) return true

        // 2. Contains quotes, em-dash, ellipsis, or mid-line sentence boundary (". ", "? ", "! ")
        if (r.contains('"') || r.contains('“') || r.contains('”') || r.contains('「') || r.contains('『') ||
            r.contains('\u2014') || r.contains('\u2015') || r.contains('\u2026') || r.contains("...")
        ) return true
        if (Regex("[.?!]\\s+[A-Z0-9\"“']").containsMatchIn(r)) return true

        // 3. Starts with common first-person pronouns or narrative conjunctions/adverbs
        if (NARRATIVE_STARTER_REGEX.matches(c)) return true

        return false
    }
    
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

    private fun extractChapterPrefixAndSubtitle(title: String): Pair<String, String> {
        val clean = sanitizeChapterTitle(title).lowercase().trim()
        val numMatch = Regex("^(chapter|bab|bagian|part|act)\\s+([\\divxlcdm.-]+)\\b").find(clean)
        if (numMatch != null) {
            val kind = when (val k = numMatch.groupValues[1]) {
                "bab", "chapter" -> "ch"
                else -> k
            }
            val numStr = numMatch.groupValues[2]
            val normNum = numStr.toIntOrNull()?.toString() ?: numStr
            val prefixKey = "${kind}_${normNum}"
            val remainder = clean.substring(numMatch.range.last + 1).trim()
            val subtitleNorm = remainder.replace(Regex("[^a-z0-9]"), "")
            return Pair(prefixKey, subtitleNorm)
        }
        val specialList = listOf(
            "prologue" to "prolog",
            "prolog" to "prolog",
            "epilogue" to "epilog",
            "epilog" to "epilog",
            "kata penutup" to "afterword",
            "afterword" to "afterword"
        )
        for ((word, key) in specialList) {
            if (clean.startsWith(word)) {
                val remainder = clean.removePrefix(word).trim()
                val subtitleNorm = remainder.replace(Regex("[^a-z0-9]"), "")
                return Pair(key, subtitleNorm)
            }
        }
        if (clean == "penutup") {
            return Pair("afterword", "")
        }
        return Pair("", "")
    }

    private fun canonicalChapterIdentity(title: String): String {
        val (prefix, _) = extractChapterPrefixAndSubtitle(title)
        if (prefix.isNotEmpty()) return prefix
        val clean = sanitizeChapterTitle(title).lowercase().trim()
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
            val (chPrefix, chSubtitleNorm) = extractChapterPrefixAndSubtitle(ch.title)

            val existingIndex = deduped.indexOfFirst { existing ->
                val existingNorm = existing.title.lowercase().replace(Regex("[^a-z0-9]"), "")
                val (existingPrefix, existingSubtitleNorm) = extractChapterPrefixAndSubtitle(existing.title)

                val fullNormMatch = chNorm.isNotEmpty() && chNorm == existingNorm
                val prefixKeyMatch = chPrefix.isNotEmpty() && chPrefix == existingPrefix && (
                    chSubtitleNorm.isEmpty() ||
                    existingSubtitleNorm.isEmpty() ||
                    chSubtitleNorm.startsWith(existingSubtitleNorm.take(10)) ||
                    existingSubtitleNorm.startsWith(chSubtitleNorm.take(10)) ||
                    abs(ch.startPageZero - existing.startPageZero) <= 2
                )

                fullNormMatch || prefixKeyMatch
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
            private val currentPageChunks = mutableListOf<WordChunk>()

            init {
                sortByPosition = true
            }

            override fun startPage(page: PDPage) {
                currentPageChunks.clear()
            }

            override fun writeString(text: String, textPositions: MutableList<TextPosition>?) {
                val trimmed = text.trim()
                if (trimmed.isEmpty() || textPositions.isNullOrEmpty()) return
                val validPos = textPositions.filter { !it.unicode.isNullOrBlank() }
                if (validPos.isEmpty()) return

                val yTop = validPos.map { it.yDirAdj }.average().toFloat()
                val xLeft = validPos.minOf { it.xDirAdj }
                val xRight = validPos.maxOf { it.xDirAdj + it.widthDirAdj }
                val height = validPos.maxOf { if (it.heightDir > 0f) it.heightDir else it.fontSizeInPt }
                val maxFontSize = validPos.maxOf { if (it.fontSizeInPt > 0f) it.fontSizeInPt else it.yScale }
                val isBold = validPos.any { tp ->
                    val fn = (tp.font?.name.orEmpty() + " " + (tp.font?.fontDescriptor?.fontName.orEmpty()))
                    fn.contains("Bold", true) ||
                        fn.contains("Bd", true) ||
                        fn.contains("Semibold", true) ||
                        fn.contains("Medium", true) ||
                        fn.contains("Heavy", true) ||
                        fn.contains("Black", true) ||
                        (tp.font?.fontDescriptor?.fontWeight ?: 400f) >= 600f ||
                        tp.font?.fontDescriptor?.isForceBold == true
                }
                currentPageChunks.add(
                    WordChunk(
                        text = trimmed,
                        yTop = yTop,
                        xLeft = xLeft,
                        xRight = xRight,
                        height = height,
                        maxFontSize = maxFontSize,
                        isBold = isBold
                    )
                )
            }

            override fun endPage(page: PDPage) {
                val pZero = currentPageNo - 1
                if (currentPageChunks.isEmpty()) {
                    resultMap[pZero] = emptyList()
                    return
                }

                // 1. Sort currentPageChunks by yTop ascending, then xLeft ascending
                val sortedChunks = currentPageChunks.sortedWith(
                    compareBy<WordChunk> { it.yTop }
                        .thenBy { it.xLeft }
                )

                // 2. Group WordChunks into horizontal visual line clusters: a chunk belongs to currentCluster if abs(chunk.yTop - clusterAvgY) <= maxOf(4.2f, clusterMaxHeight * 0.55f).
                val clusters = mutableListOf<MutableList<WordChunk>>()
                for (chunk in sortedChunks) {
                    val currentCluster = clusters.lastOrNull()
                    if (currentCluster == null) {
                        clusters.add(mutableListOf(chunk))
                    } else {
                        val clusterAvgY = currentCluster.map { it.yTop }.average().toFloat()
                        val clusterMaxHeight = currentCluster.maxOf { it.height }
                        if (abs(chunk.yTop - clusterAvgY) <= maxOf(4.2f, clusterMaxHeight * 0.55f)) {
                            currentCluster.add(chunk)
                        } else {
                            clusters.add(mutableListOf(chunk))
                        }
                    }
                }

                val pageW = (page.mediaBox?.width ?: 600f).coerceAtLeast(1f)
                val lines = mutableListOf<VisualLine>()

                for (cluster in clusters) {
                    if (cluster.isEmpty()) continue
                    val sortedLineChunks = cluster.sortedBy { it.xLeft }
                    val sb = StringBuilder()
                    var prevRight = Float.NaN
                    for (chunk in sortedLineChunks) {
                        val cleanChunkText = chunk.text
                            .replace(Regex("[\\u00A0\\u2000-\\u200B\\u202F\\u205F\\u3000\\uFEFF]"), " ")
                            .trim()
                        if (cleanChunkText.isEmpty()) continue
                        if (!prevRight.isNaN()) {
                            val gap = chunk.xLeft - prevRight
                            val wordGapThreshold = maxOf(1.2f, chunk.maxFontSize * 0.14f)
                            if (gap > wordGapThreshold && !sb.endsWith(" ") && !sb.endsWith("-")) {
                                sb.append(" ")
                            }
                        }
                        sb.append(cleanChunkText)
                        prevRight = if (prevRight.isNaN()) chunk.xRight else maxOf(prevRight, chunk.xRight)
                    }
                    val rawLineText = sb.toString().replace(Regex("\\s+"), " ").trim()
                    val lineText = rawLineText
                        .replace(Regex("(?i)\\b([bB])\\s+([aA])\\s+([bB])\\s*(\\d+)"), "Bab $4")
                        .replace(Regex("(?i)\\b([cC])\\s+([hH])\\s+([aA])\\s+([pP])\\s+([tT])\\s+([eE])\\s+([rR])\\s*(\\d+)"), "Chapter $8")
                        .replace(Regex("(?i)\\b([pP])\\s+([rR])\\s+([oO])\\s+([lL])\\s+([oO])\\s+([gG])\\b"), "Prolog")
                        .replace(Regex("(?i)\\b([eE])\\s+([pP])\\s+([iI])\\s+([lL])\\s+([oO])\\s+([gG])\\b"), "Epilog")
                        .replace(Regex("(?i)\\b(bab|chapter|bagian|part|act)(\\d+)"), "$1 $2")
                    if (lineText.isEmpty()) continue

                    val xLeft = cluster.minOf { it.xLeft }
                    val xRight = cluster.maxOf { it.xRight }
                    val yTop = cluster.map { it.yTop }.average().toFloat()
                    val maxFontSize = cluster.maxOf { it.maxFontSize }
                    val isBold = cluster.any { it.isBold }
                    val lineW = (xRight - xLeft).coerceAtLeast(0f)
                    val midX = (xLeft + xRight) / 2f
                    val isCentered = lineW < pageW * 0.82f && abs(midX - pageW / 2f) <= pageW * 0.18f

                    lines.add(
                        VisualLine(
                            text = lineText,
                            yTop = yTop,
                            xLeft = xLeft,
                            xRight = xRight,
                            maxFontSize = maxFontSize,
                            isBold = isBold,
                            isCentered = isCentered
                        )
                    )
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

    fun getOrCreateCachedDoc(context: Context, pdfFilePath: String): PDDocument? {
        synchronized(docCacheLock) {
            if (cachedDocPath == pdfFilePath && cachedDoc != null) {
                return cachedDoc
            }
            runCatching { cachedDoc?.close() }
            cachedDoc = null
            cachedDocPath = null
            val file = File(pdfFilePath)
            if (!file.exists()) return null
            PDFBoxResourceLoader.init(context.applicationContext)
            val doc = runCatching { PDDocument.load(file) }.getOrNull()
            if (doc != null) {
                cachedDoc = doc
                cachedDocPath = pdfFilePath
            }
            return cachedDoc
        }
    }

    fun closeCachedDoc() {
        synchronized(docCacheLock) {
            runCatching { cachedDoc?.close() }
            cachedDoc = null
            cachedDocPath = null
            pageParagraphBlocksCache.evictAll()
            translatedPdfBitmapCache.evictAll()
        }
    }

    fun clearTranslatedPdfBitmaps() {
        translatedPdfBitmapCache.evictAll()
        pageParagraphBlocksCache.evictAll()
    }

    fun extractPdfPageParagraphBlocks(
        context: Context,
        pdfFilePath: String,
        pageIndexZero: Int,
        bookTitle: String = ""
    ): List<PdfParagraphBlock> {
        val cacheKey = "$pdfFilePath:$pageIndexZero"
        val cached = pageParagraphBlocksCache.get(cacheKey)
        if (cached != null) return cached

        val doc = getOrCreateCachedDoc(context, pdfFilePath) ?: return emptyList()

        return synchronized(docCacheLock) {
            if (pageIndexZero < 0 || pageIndexZero >= doc.numberOfPages) return emptyList()

            val linesMap = extractVisualLinesForPages(doc, pageIndexZero, pageIndexZero)
            val allLines = linesMap[pageIndexZero].orEmpty()
            if (allLines.isEmpty()) {
                pageParagraphBlocksCache.put(cacheKey, emptyList())
                return emptyList()
            }

            val pdPage = runCatching { doc.getPage(pageIndexZero) }.getOrNull()
            val mediaBox = pdPage?.mediaBox ?: pdPage?.cropBox
            val pageW = (mediaBox?.width ?: 600f).coerceAtLeast(1f)
            val pageH = (mediaBox?.height ?: 800f).coerceAtLeast(1f)

            val fontSizes = allLines.map { it.maxFontSize }.sorted()
            val bodyMedianFontSize = if (fontSizes.isNotEmpty()) fontSizes[fontSizes.size / 2] else 12f

            val allPositiveYDiffs = mutableListOf<Float>()
            for (i in 1 until allLines.size) {
                val diff = allLines[i].yTop - allLines[i - 1].yTop
                if (diff > 0f) allPositiveYDiffs.add(diff)
            }
            allPositiveYDiffs.sort()
            val normalLineStep = if (allPositiveYDiffs.isNotEmpty()) {
                allPositiveYDiffs[allPositiveYDiffs.size / 4]
            } else 14f

            val titleClean = bookTitle.trim().lowercase()
            val bodyLines = allLines.filter { vl ->
                val txt = vl.text.trim()
                if (txt.isEmpty()) return@filter false
                val lower = txt.lowercase()

                if (lower.contains("http://") || lower.contains("https://") || lower.contains("www.") ||
                    lower.contains("arakiyota") || lower.contains("youzitsu") || lower.contains("bulan kabur")) {
                    return@filter false
                }

                // Top header zone: yTop < pageH * 0.088f
                if (vl.yTop < pageH * 0.088f) {
                    val isTitleMatch = titleClean.length >= 3 && (lower == titleClean || lower.contains(titleClean))
                    val isPageNum = txt.matches(Regex("""^(?:[\-–—#\s]*\d+[\-–—#\s]*|page\s*\d+|\d+\s*\|\s*\d+)$""", RegexOption.IGNORE_CASE))
                    val isVolumeOrTrans = lower.contains("translation") || (lower.contains("volume") && (lower.contains("|") || lower.contains("vol")))
                    val isDistantSmallFirstLine = allLines.indexOf(vl) == 0 && allLines.size >= 2 &&
                        vl.maxFontSize < bodyMedianFontSize - 0.6f &&
                        (allLines[1].yTop - vl.yTop) > normalLineStep * 1.65f

                    if (isTitleMatch || isPageNum || isVolumeOrTrans || isDistantSmallFirstLine) {
                        return@filter false
                    }
                }

                // Bottom footer zone: yTop > pageH * 0.885f
                if (vl.yTop > pageH * 0.885f) {
                    if (txt.matches(Regex("""^(?:[-—–~#\s]*\d+[-—–~#\s]*|page\s*\d+|\d+\s*/\s*\d+)$""", RegexOption.IGNORE_CASE))) return@filter false
                    if (lower.contains("chapter") || lower.contains("bab") || lower.contains("pusat perhatian") || txt.matches(Regex(""".*\b\d+\s*$"""))) return@filter false
                }

                true
            }

            if (bodyLines.isEmpty()) {
                pageParagraphBlocksCache.put(cacheKey, emptyList())
                return emptyList()
            }

            val colLeft = bodyLines.minOf { it.xLeft }
            val colRight = bodyLines.maxOf { it.xRight }
            val colWidth = (colRight - colLeft).coerceAtLeast(1f)
            val colMidX = (colLeft + colRight) / 2f

            val positiveYDiffs = mutableListOf<Float>()
            for (i in 1 until bodyLines.size) {
                val diff = bodyLines[i].yTop - bodyLines[i - 1].yTop
                if (diff > 0f) positiveYDiffs.add(diff)
            }
            positiveYDiffs.sort()
            val normalStep = if (positiveYDiffs.isNotEmpty()) {
                positiveYDiffs[positiveYDiffs.size / 4]
            } else 14f

            val BULLET_PREFIX_REGEX = Regex("""^([➢➤•●○◦▪▫■□◆◇❖★☆▸►▶\u25A0-\u27BF\uF000-\uF0FF]+|\d+\.|\([0-9a-zA-Z]+\))\s+""")

            val isLineCentered: (VisualLine) -> Boolean = { vl ->
                vl.yTop <= pageH * 0.28f &&
                    vl.xLeft >= colLeft + 48f &&
                    vl.xRight <= colRight - 48f &&
                    abs((vl.xLeft + vl.xRight) / 2f - colMidX) <= colWidth * 0.10f
            }

            val clusters = mutableListOf<MutableList<VisualLine>>()
            for (curr in bodyLines) {
                val currentCluster = clusters.lastOrNull()
                if (currentCluster == null) {
                    clusters.add(mutableListOf(curr))
                } else {
                    val prev = currentCluster.last()
                    val vertDist = curr.yTop - prev.yTop
                    val notNewBullet = !BULLET_PREFIX_REGEX.containsMatchIn(curr.text.trim())
                    val sameCentered = isLineCentered(curr) == isLineCentered(prev)
                    val prevTextStartX = if (BULLET_PREFIX_REGEX.containsMatchIn(prev.text)) prev.xLeft + 18f else prev.xLeft
                    val indentOk = curr.xLeft <= prevTextStartX + 6f
                    val prevReachedMargin = prev.xRight >= colRight - (colWidth * 0.18f)

                    val sameParagraph = vertDist <= normalStep * 1.34f &&
                        notNewBullet &&
                        sameCentered &&
                        indentOk &&
                        prevReachedMargin

                    if (sameParagraph) {
                        currentCluster.add(curr)
                    } else {
                        clusters.add(mutableListOf(curr))
                    }
                }
            }

            val blocks = clusters.mapNotNull { cluster ->
                if (cluster.isEmpty()) return@mapNotNull null
                val sb = StringBuilder()
                for (vl in cluster) {
                    val t = vl.text.trim()
                    if (t.isEmpty()) continue
                    if (sb.isEmpty()) {
                        sb.append(t)
                    } else {
                        if (sb.endsWith("-")) {
                            val beforeHyphen = sb.dropLast(1).toString()
                            val firstWord = t.substringBefore(" ")
                            if (beforeHyphen.lastOrNull()?.isLetter() == true && firstWord.firstOrNull()?.isLetter() == true) {
                                sb.setLength(sb.length - 1)
                                sb.append(t)
                            } else {
                                sb.append(" ").append(t)
                            }
                        } else {
                            sb.append(" ").append(t)
                        }
                    }
                }
                val fullText = sb.toString().trim()
                if (fullText.isEmpty()) return@mapNotNull null

                val bulletPrefix = BULLET_PREFIX_REGEX.find(fullText)?.value.orEmpty()
                val pdfLeft = cluster.minOf { it.xLeft }
                val pdfRight = cluster.maxOf { it.xRight }
                val firstLine = cluster.first()
                val lastLine = cluster.last()
                val pdfTop = (firstLine.yTop - firstLine.maxFontSize * 0.92f).coerceAtLeast(0f)
                val pdfBottom = (lastLine.yTop + lastLine.maxFontSize * 0.35f).coerceAtMost(pageH)
                val firstLineIndentPdf = if (bulletPrefix.isEmpty() && cluster.size > 1) {
                    (cluster.first().xLeft - cluster.drop(1).minOf { it.xLeft }).coerceAtLeast(0f)
                } else 0f
                val hangingIndentPdf = if (bulletPrefix.isNotEmpty() && cluster.size > 1) {
                    (cluster[1].xLeft - cluster[0].xLeft).coerceIn(10f, 36f)
                } else if (bulletPrefix.isNotEmpty()) 16f else 0f
                val avgFontSizePt = cluster.map { it.maxFontSize }.average().toFloat()
                val lineSpacingRatio = if (cluster.size > 1) {
                    ((lastLine.yTop - firstLine.yTop) / (cluster.size - 1)) / avgFontSizePt.coerceAtLeast(1f)
                } else 1.2f
                val isBold = cluster.count { it.isBold } >= (cluster.size + 1) / 2
                val isCentered = cluster.size <= 2 &&
                    cluster.first().yTop <= pageH * 0.28f &&
                    cluster.all {
                        it.xLeft >= colLeft + 48f &&
                        it.xRight <= colRight - 48f &&
                        abs((it.xLeft + it.xRight) / 2f - colMidX) <= colWidth * 0.10f
                    }

                PdfParagraphBlock(
                    text = fullText,
                    bulletPrefix = bulletPrefix,
                    pdfLeft = pdfLeft,
                    pdfTop = pdfTop,
                    pdfRight = pdfRight,
                    pdfBottom = pdfBottom,
                    colRight = colRight,
                    firstLineIndentPdf = firstLineIndentPdf,
                    hangingIndentPdf = hangingIndentPdf,
                    avgFontSizePt = avgFontSizePt,
                    lineSpacingRatio = lineSpacingRatio,
                    isBold = isBold,
                    isCentered = isCentered,
                    pageWidthPt = pageW,
                    pageHeightPt = pageH
                )
            }

            val transPrefix = "$pdfFilePath|$pageIndexZero|"
            translatedPdfBitmapCache.snapshot().keys.filter { it.startsWith(transPrefix) }.forEach {
                translatedPdfBitmapCache.remove(it)
            }

            pageParagraphBlocksCache.put(cacheKey, blocks)
            blocks
        }
    }

    fun renderTranslatedPdfPageBitmap(
        origBmp: Bitmap,
        blocks: List<PdfParagraphBlock>,
        translatedTexts: List<String>,
        displayMode: String,
        isIllustrationPage: Boolean = false
    ): Bitmap {
        if (blocks.isEmpty() || translatedTexts.isEmpty()) return origBmp

        val outBmp = origBmp.copy(Bitmap.Config.ARGB_8888, true) ?: return origBmp
        val canvas = Canvas(outBmp)
        val firstBlock = blocks.first()
        val scaleX = outBmp.width.toFloat() / firstBlock.pageWidthPt
        val scaleY = outBmp.height.toFloat() / firstBlock.pageHeightPt

        val sampleCoords = listOf(
            Pair((outBmp.width * 0.02f).toInt(), (outBmp.height * 0.02f).toInt()),
            Pair((outBmp.width * 0.98f).toInt(), (outBmp.height * 0.02f).toInt()),
            Pair((outBmp.width * 0.02f).toInt(), (outBmp.height * 0.98f).toInt()),
            Pair((outBmp.width * 0.98f).toInt(), (outBmp.height * 0.98f).toInt()),
            Pair(outBmp.width / 2, (outBmp.height * 0.02f).toInt())
        )
        val validColors = sampleCoords.mapNotNull { (x, y) ->
            val sx = x.coerceIn(0, outBmp.width - 1)
            val sy = y.coerceIn(0, outBmp.height - 1)
            runCatching { outBmp.getPixel(sx, sy) }.getOrNull()
        }
        val paperBgColorInt = if (validColors.isNotEmpty()) {
            validColors.sortedBy { Color.red(it) + Color.green(it) + Color.blue(it) }[validColors.size / 2]
        } else Color.WHITE

        val lum = Color.red(paperBgColorInt) * 0.299 + Color.green(paperBgColorInt) * 0.587 + Color.blue(paperBgColorInt) * 0.114
        val isDarkPaper = lum < 128
        val darkInkColor = if (isDarkPaper) Color.rgb(235, 235, 235) else Color.rgb(24, 24, 26)
        val mutedInkColor = if (isDarkPaper) Color.rgb(180, 180, 190) else Color.rgb(95, 95, 105)
        val erasePaint = Paint().apply {
            style = Paint.Style.FILL
            color = paperBgColorInt
        }

        val isBilingual = displayMode == "bilingual"
        val lineSpacingMultiplier = if (isBilingual) 1.0f else 1.12f

        val spannables = ArrayList<SpannableString>(blocks.size)
        val layoutWidths = ArrayList<Int>(blocks.size)
        val leftPxs = ArrayList<Float>(blocks.size)

        for (i in blocks.indices) {
            val b = blocks[i]
            val rawTrans = translatedTexts.getOrNull(i)?.trim().orEmpty()
            val transText = if (b.bulletPrefix.isNotEmpty() && !rawTrans.startsWith(b.bulletPrefix.trim())) {
                b.bulletPrefix + rawTrans
            } else if (rawTrans.isNotEmpty()) {
                rawTrans
            } else {
                b.text
            }

            val textToDraw = if (isBilingual) "${b.text}\n$transText" else transText
            val spannable = SpannableString(textToDraw)
            val firstIndentPx = (b.firstLineIndentPdf * scaleX).roundToInt()
            val restIndentPx = (b.hangingIndentPdf * scaleX).roundToInt()
            if (firstIndentPx > 0 || restIndentPx > 0) {
                spannable.setSpan(
                    LeadingMarginSpan.Standard(firstIndentPx, restIndentPx),
                    0,
                    spannable.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            if (isBilingual) {
                val transStart = b.text.length + 1
                if (transStart < spannable.length) {
                    spannable.setSpan(
                        ForegroundColorSpan(mutedInkColor),
                        transStart,
                        spannable.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    spannable.setSpan(
                        RelativeSizeSpan(0.90f),
                        transStart,
                        spannable.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            spannables.add(spannable)

            val leftPx = (b.pdfLeft * scaleX).coerceAtLeast(8f)
            val targetRightPx = (maxOf(b.pdfRight, b.colRight) * scaleX).coerceAtMost(outBmp.width - 8f)
            val layoutWidthPx = (targetRightPx - leftPx).toInt().coerceAtLeast(50)
            leftPxs.add(leftPx)
            layoutWidths.add(layoutWidthPx)
        }

        if (!isIllustrationPage) {
            // Case A — Normal Text Page (!isIllustrationPage)
            val minBlockLeftPx = (blocks.minOf { it.pdfLeft } * scaleX - 6f).coerceAtLeast(4f)
            val maxBlockRightPx = (blocks.maxOf { maxOf(it.pdfRight, it.colRight) } * scaleX + 6f).coerceAtMost(outBmp.width - 4f)
            val firstBlockTopPx = (blocks.minOf { it.pdfTop } * scaleY - 4f).coerceAtLeast(4f)
            val lastBlockBottomPx = (blocks.maxOf { it.pdfBottom } * scaleY + 6f).coerceAtMost(outBmp.height - 4f)

            // Erase the entire body text column zone cleanly in ONE single rectangle
            canvas.drawRect(RectF(minBlockLeftPx, firstBlockTopPx, maxBlockRightPx, lastBlockBottomPx), erasePaint)

            val totalAvailH = lastBlockBottomPx - firstBlockTopPx

            fun buildLayoutsForScale(scale: Float): Pair<List<StaticLayout>, Float> {
                var curH = 0f
                val layouts = ArrayList<StaticLayout>(blocks.size)
                for (i in blocks.indices) {
                    val b = blocks[i]
                    val spannable = spannables[i]
                    val widthPx = layoutWidths[i]
                    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                        color = darkInkColor
                        typeface = if (b.isBold) Typeface.create(Typeface.SERIF, Typeface.BOLD) else Typeface.SERIF
                        textSize = b.avgFontSizePt * scaleY * scale
                    }
                    val sl = StaticLayout.Builder.obtain(spannable, 0, spannable.length, paint, widthPx)
                        .setAlignment(if (b.isCentered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(0f, lineSpacingMultiplier)
                        .setIncludePad(false)
                        .build()
                    layouts.add(sl)
                    val paraGapPx = (b.avgFontSizePt * scaleY * 0.55f).coerceAtLeast(8f)
                    curH += sl.height + if (i < blocks.size - 1) paraGapPx else 0f
                }
                return Pair(layouts, curH)
            }

            var (layouts, totalH) = buildLayoutsForScale(1.0f)
            var fontScale = 1.0f

            if (totalH > totalAvailH) {
                val candidateScales = listOf(0.96f, 0.92f, 0.88f, 0.84f, 0.80f, 0.76f)
                for (cand in candidateScales) {
                    val (candLayouts, candH) = buildLayoutsForScale(cand)
                    layouts = candLayouts
                    totalH = candH
                    fontScale = cand
                    if (candH <= totalAvailH) break
                }
            }

            var cursorY = firstBlockTopPx
            for (i in blocks.indices) {
                val b = blocks[i]
                val sl = layouts[i]
                val leftPx = leftPxs[i]
                val idealTopPx = b.pdfTop * scaleY
                val drawTopPx = if (fontScale < 0.96f) cursorY else maxOf(cursorY, idealTopPx)

                val bottomAfterDraw = drawTopPx + sl.height
                if (bottomAfterDraw > lastBlockBottomPx) {
                    val extraBottom = minOf(bottomAfterDraw + 4f, outBmp.height * 0.91f)
                    if (extraBottom > lastBlockBottomPx) {
                        canvas.drawRect(
                            RectF(minBlockLeftPx, lastBlockBottomPx, maxBlockRightPx, extraBottom),
                            erasePaint
                        )
                    }
                }

                canvas.save()
                canvas.translate(leftPx, drawTopPx)
                sl.draw(canvas)
                canvas.restore()

                val paraGapPx = (b.avgFontSizePt * scaleY * 0.55f).coerceAtLeast(8f)
                cursorY = drawTopPx + sl.height + paraGapPx
            }
        } else {
            // Case B — Illustration Page with Text Overlay (isIllustrationPage == true)
            val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(225, 255, 255, 255)
            }
            for (i in blocks.indices) {
                val b = blocks[i]
                val spannable = spannables[i]
                val widthPx = layoutWidths[i]
                val leftPx = leftPxs[i]
                val topPx = (b.pdfTop * scaleY).coerceAtLeast(4f)

                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
                    color = Color.rgb(24, 24, 26)
                    typeface = if (b.isBold) Typeface.create(Typeface.SERIF, Typeface.BOLD) else Typeface.SERIF
                    textSize = b.avgFontSizePt * scaleY
                }

                val sl = StaticLayout.Builder.obtain(spannable, 0, spannable.length, textPaint, widthPx)
                    .setAlignment(if (b.isCentered) Layout.Alignment.ALIGN_CENTER else Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, lineSpacingMultiplier)
                    .setIncludePad(false)
                    .build()

                val pillRect = RectF(
                    leftPx - 6f,
                    topPx - 3f,
                    leftPx + widthPx + 6f,
                    topPx + sl.height + 3f
                )
                canvas.drawRoundRect(pillRect, 8f, 8f, pillPaint)

                canvas.save()
                canvas.translate(leftPx, topPx)
                sl.draw(canvas)
                canvas.restore()
            }
        }

        return outBmp
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
                            if (normL.isNotEmpty() &&
                                !NORM_CHAPTER_KEYWORD_REGEX.matches(normL) &&
                                !CHAPTER_KEYWORD_REGEX.matches(clean) &&
                                !BARE_NUMBERED_CHAPTER_REGEX.matches(clean) &&
                                !CHAPTER_KEYWORD_REGEX.matches(normL) &&
                                !BARE_NUMBERED_CHAPTER_REGEX.matches(normL)
                            ) {
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
                        val lineText = line.text.trim()
                        val n = norm(lineText)
                        if (n.isNotEmpty() &&
                            !NORM_CHAPTER_KEYWORD_REGEX.matches(n) &&
                            !CHAPTER_KEYWORD_REGEX.matches(lineText) &&
                            !BARE_NUMBERED_CHAPTER_REGEX.matches(lineText) &&
                            !CHAPTER_KEYWORD_REGEX.matches(n) &&
                            !BARE_NUMBERED_CHAPTER_REGEX.matches(n)
                        ) {
                            top2Freq.getOrPut(n) { mutableSetOf() }.add(p)
                        }
                    }
                    val bot2 = vLines.takeLast(2)
                    for (line in bot2) {
                        val lineText = line.text.trim()
                        val n = norm(lineText)
                        if (n.isNotEmpty() &&
                            !NORM_CHAPTER_KEYWORD_REGEX.matches(n) &&
                            !CHAPTER_KEYWORD_REGEX.matches(lineText) &&
                            !BARE_NUMBERED_CHAPTER_REGEX.matches(lineText) &&
                            !CHAPTER_KEYWORD_REGEX.matches(n) &&
                            !BARE_NUMBERED_CHAPTER_REGEX.matches(n)
                        ) {
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
                        if (!NORM_CHAPTER_KEYWORD_REGEX.matches(n) &&
                            !BARE_NUMBERED_CHAPTER_REGEX.matches(n) &&
                            !CHAPTER_KEYWORD_REGEX.matches(n)
                        ) {
                            repeatingTopHeaders.add(n)
                        }
                    }
                }

                val globalWatermarkBlacklist = mutableSetOf<String>()
                for ((nLink, pages) in linkPageOccurrences) {
                    if (pages.size >= 5) {
                        if (!NORM_CHAPTER_KEYWORD_REGEX.matches(nLink) &&
                            !BARE_NUMBERED_CHAPTER_REGEX.matches(nLink) &&
                            !CHAPTER_KEYWORD_REGEX.matches(nLink)
                        ) {
                            globalWatermarkBlacklist.add(nLink)
                        }
                    }
                }
                for ((nBot, pages) in bot2Freq) {
                    if (pages.size >= 5 &&
                        !NORM_CHAPTER_KEYWORD_REGEX.matches(nBot) &&
                        !CHAPTER_KEYWORD_REGEX.matches(nBot) &&
                        !BARE_NUMBERED_CHAPTER_REGEX.matches(nBot)
                    ) {
                        globalWatermarkBlacklist.add(nBot)
                    }
                }

                // Stage 4: 4-Pattern Full-Book Scan
                val patternABookmarks = mutableListOf<PdfBookmark>()
                val patternBBookmarks = mutableListOf<PdfBookmark>()
                val patternCBookmarks = mutableListOf<PatternCCandidate>()
                val patternDBookmarks = mutableListOf<PdfBookmark>()
                val footerBookmarks = mutableListOf<PdfBookmark>()
                val footerTransitionBookmarks = mutableListOf<PdfBookmark>()
                val tocMaxPage = tocPages.maxOrNull() ?: -1

                for (p in 0 until safeTotalPages) {
                    val pdPage = runCatching { document.getPage(p) }.getOrNull()
                    val mediaBox = pdPage?.mediaBox ?: pdPage?.cropBox
                    val pageH = (mediaBox?.height ?: 800f).coerceAtLeast(1f)
                    val pageW = (mediaBox?.width ?: 600f).coerceAtLeast(1f)
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

                    val isSeparatorPage = contentLines.size in 1..5 && contentLines.all { it.text.length <= 80 }
                    val candidateTopLines = if (isSeparatorPage) contentLines else contentLines.filter { it.yTop <= pageH * 0.42f }.take(5)
                    val pageMedianFontSize = contentLines.map { it.maxFontSize }.sorted().let {
                        if (it.isEmpty()) 12f else it[it.size / 2]
                    }

                    var patternAMatched = false
                    val maxScanIdx = if (isSeparatorPage) contentLines.size else minOf(4, contentLines.size)
                    for (idx in 0 until maxScanIdx) {
                        val line = contentLines[idx]
                        val maxAllowedY = if (isSeparatorPage) pageH * 0.85f else pageH * 0.45f
                        if (line.yTop > maxAllowedY) continue
                        val stripped = line.text.replace(LEADING_BULLET_REGEX, "").trim()
                        if (CHAPTER_KEYWORD_REGEX.matches(stripped)) {
                            var title = sanitizeChapterTitle(stripped)
                            if (STANDALONE_PREFIX_REGEX.matches(stripped)) {
                                val nextLine = contentLines.getOrNull(idx + 1)
                                if (nextLine != null && (nextLine.yTop - line.yTop) in 2f..65f) {
                                    val nextClean = sanitizeChapterTitle(nextLine.text.replace(LEADING_BULLET_REGEX, ""))
                                    val nextWords = nextClean.split(Regex("\\s+")).filter { it.isNotEmpty() }
                                    val endsWithAbbrevDot = Regex("(?i).*\\b(a\\.m\\.|p\\.m\\.|am\\.|pm\\.|etc\\.|vol\\.|no\\.|jr\\.|sr\\.|dr\\.|mr\\.|mrs\\.|ms\\.|st\\.)$").matches(nextClean)
                                    val allowsTrailingDot = endsWithAbbrevDot || isSeparatorPage || (nextLine.isCentered && nextWords.size <= 6)
                                    val isAligned = isSeparatorPage || nextLine.isBold || nextLine.isCentered ||
                                            abs((nextLine.xLeft + nextLine.xRight) / 2f - (line.xLeft + line.xRight) / 2f) <= 60f
                                    if (nextClean.length in 2..70 && nextWords.size <= 10 &&
                                        !isLikelyNarrativeOrDialogueLine(nextLine.text, nextClean) &&
                                        !CHAPTER_KEYWORD_REGEX.matches(nextClean) &&
                                        (!nextClean.endsWith(".") || allowsTrailingDot) &&
                                        isAligned
                                    ) {
                                        title = "${title.trimEnd(':', '.', '-', '–', '—').trim()}: $nextClean"
                                    }
                                }
                            }
                            if (title.length in 3..120) {
                                val bm = PdfBookmark(title, p)
                                patternABookmarks.add(bm)
                                patternAMatched = true
                                break
                            }
                        }
                    }

                    val bodySample = if (contentLines.size >= 5) contentLines.drop(2) else contentLines
                    val colLeft = bodySample.minOf { it.xLeft }
                    val colRight = bodySample.maxOf { it.xRight }.coerceAtLeast(colLeft + 100f)
                    val colWidth = (colRight - colLeft).coerceAtLeast(1f)
                    val colMidX = (colLeft + colRight) / 2f

                    var patternBMatched = false
                    // Pattern B — Top-of-Page Standalone Bulleted Heading (• Dua Guru, Ujian Khusus Yang Ditakdirkan)
                    if (!patternAMatched) {
                        val firstLine = contentLines.firstOrNull()
                        if (firstLine != null && (firstLine.yTop <= pageH * 0.40f || isSeparatorPage) && LEADING_BULLET_REGEX.containsMatchIn(firstLine.text)) {
                            val secondLine = contentLines.getOrNull(1)
                            val secondHasBullet = secondLine != null && LEADING_BULLET_REGEX.containsMatchIn(secondLine.text)
                            if (!secondHasBullet) {
                                val rawBulletTitle = sanitizeChapterTitle(firstLine.text.replace(LEADING_BULLET_REGEX, ""))
                                val dialogueStartQuotes = setOf('"', '“', '‘', '\'', '「', '『', '(')
                                val bulletTitle = rawBulletTitle.dropWhile { !it.isLetterOrDigit() && it !in dialogueStartQuotes }.trim()
                                val words = bulletTitle.split(Regex("\\s+")).filter { it.isNotEmpty() }
                                val startsWithAlnum = bulletTitle.firstOrNull()?.let { it.isUpperCase() || it.isDigit() } == true
                                val endsWithoutPunct = !bulletTitle.endsWith(",") && !bulletTitle.endsWith(";")
                                val isDistinctHeading = firstLine.isCentered || firstLine.isBold || firstLine.maxFontSize >= pageMedianFontSize + 0.3f || firstLine.xLeft >= colLeft + 12f || isSeparatorPage
                                val rawWithoutBullet = firstLine.text.replace(LEADING_BULLET_REGEX, "")
                                if (bulletTitle.length in 4..75 && words.size <= 10 && startsWithAlnum && endsWithoutPunct && isDistinctHeading &&
                                    !isLikelyNarrativeOrDialogueLine(rawWithoutBullet, bulletTitle)
                                ) {
                                    val bm = PdfBookmark(bulletTitle, p)
                                    patternBBookmarks.add(bm)
                                    patternBMatched = true
                                }
                            }
                        }
                    }

                    var patternCenteredMatched = false
                    // Pattern C – Top-of-Page Centered or Bold Heading (1 or 2 Lines, e.g. "Wawancara sekilas dua arah", "Permintaan dari Horikita dan permintaan dari Ayanokouji")
                    if (!patternAMatched && !patternBMatched && contentLines.size >= 3) {
                        val pageMidX = pageW / 2f

                        // Determine normal paragraph first-line indent on this page (wrapped body lines sit near colLeft)
                        val wrappedBodyLines = bodySample.filter { it.xLeft <= colLeft + 8f && it.xRight >= colRight - colWidth * 0.18f }
                        val hasNormalBodyParagraphs = wrappedBodyLines.isNotEmpty() || bodySample.count { !it.isBold } >= 2

                        // A heading line is visually centered/indented-centered if:
                        // 1) It has a clear left indent (`xLeft >= colLeft + 12f`) AND clear right margin (`xRight <= colRight - 14f`) with midpoint in `[colMidX - 18% .. colMidX + 25%]` (accounting for Word's first-line indent on centered headings), OR
                        // 2) It is bold and indented (`isBold && xLeft >= colLeft + 12f`)
                        fun isHeadingAlignedLine(l: VisualLine, allowNearRightEdgeIfTwoLine: Boolean = false): Boolean {
                            val lineW = l.xRight - l.xLeft
                            val mid = (l.xLeft + l.xRight) / 2f
                            val hasRightMargin = l.xRight <= colRight - 14f || (allowNearRightEdgeIfTwoLine && lineW <= colWidth * 0.92f)
                            val isMidCentered = (mid >= colMidX - colWidth * 0.18f && mid <= colMidX + colWidth * 0.25f) ||
                                    abs(mid - pageMidX) <= pageW * 0.20f
                            return l.xLeft >= colLeft + 12f && hasRightMargin && isMidCentered
                        }

                        val firstLine = contentLines.firstOrNull()
                        val secondLine = contentLines.getOrNull(1)
                        if (firstLine != null && firstLine.yTop <= pageH * 0.35f && hasNormalBodyParagraphs) {
                            val secondLineStrictlyCentered = secondLine != null &&
                                    secondLine.xLeft >= colLeft + 25f &&
                                    secondLine.xRight <= colRight - 25f &&
                                    isHeadingAlignedLine(secondLine, allowNearRightEdgeIfTwoLine = false) &&
                                    (secondLine.yTop - firstLine.yTop) in 2f..44f &&
                                    !secondLine.text.endsWith(".")

                            val firstLineMatches = isHeadingAlignedLine(firstLine, allowNearRightEdgeIfTwoLine = secondLineStrictlyCentered)
                            if (firstLineMatches && !firstLine.text.endsWith(".")) {
                                val secondLineIsHeading = secondLineStrictlyCentered &&
                                        (firstLine.isBold == secondLine!!.isBold || abs(firstLine.maxFontSize - secondLine.maxFontSize) <= 1.5f)
                                val lastHeadingIdx = if (secondLineIsHeading) 1 else 0
                                val lastHeadingLine = contentLines[lastHeadingIdx]
                                val firstBodyLine = contentLines.getOrNull(lastHeadingIdx + 1)
                                val bodyLines = contentLines.drop(lastHeadingIdx + 1)

                                // Ensure firstBodyLine is NOT a third centered short heading line
                                val firstBodyIsNormalParagraph = firstBodyLine != null && (
                                    !firstBodyLine.isBold ||
                                    firstBodyLine.xRight >= colRight - 24f ||
                                    firstBodyLine.xLeft <= colLeft + 10f ||
                                    firstBodyLine.text.endsWith(".") ||
                                    firstBodyLine.text.endsWith(":") ||
                                    firstBodyLine.text.endsWith("\"") ||
                                    firstBodyLine.text.endsWith("”")
                                )

                                if (firstBodyLine != null && firstBodyIsNormalParagraph) {
                                    val bodyMedianStep = if (bodyLines.size >= 2) {
                                        val steps = bodyLines.zipWithNext { a, b -> b.yTop - a.yTop }.filter { it in 4f..45f }.sorted()
                                        if (steps.isNotEmpty()) steps[steps.size / 4] else 16f
                                    } else 16f
                                    val bodyFontSizes = bodyLines.map { it.maxFontSize }.sorted()
                                    val bodyMedianFontSize = if (bodyFontSizes.isNotEmpty()) bodyFontSizes[bodyFontSizes.size / 2] else 12f

                                    // Guard B — Reject Lines That Sit at the Page's Normal Paragraph First-Line Indent Unless Bold or Larger Font
                                    val indentedBodyLines = bodyLines.filter { it.xLeft in (colLeft + 8f)..(colLeft + 55f) }
                                    val matchesBodyParaIndent = indentedBodyLines.count { abs(it.xLeft - firstLine.xLeft) <= 6.5f } >= 2
                                    val isVisuallyDistinctFromBody = (firstLine.isBold && !firstBodyLine.isBold) ||
                                            firstLine.maxFontSize >= bodyMedianFontSize + 0.5f ||
                                            (secondLineIsHeading && secondLine!!.xLeft >= firstLine.xLeft + 20f)

                                    if (!(matchesBodyParaIndent && !isVisuallyDistinctFromBody)) {
                                        // Guard C — Require True Centering Symmetry for Non-Bold, Same-Font-Size Single-Line Pattern C Candidates
                                        val passesGuardC = if (!firstLine.isBold && firstLine.maxFontSize < bodyMedianFontSize + 0.5f && !secondLineIsHeading) {
                                            val leftMarginFromCol = firstLine.xLeft - colLeft
                                            val rightMarginFromCol = colRight - firstLine.xRight
                                            leftMarginFromCol >= 45f && rightMarginFromCol >= 45f && abs(leftMarginFromCol - rightMarginFromCol) <= colWidth * 0.14f
                                        } else {
                                            true
                                        }

                                        if (passesGuardC) {
                                            val firstLineStrictlyCentered = firstLine.xLeft >= colLeft + 55f &&
                                                    firstLine.xRight <= colRight - 55f &&
                                                    abs((firstLine.xLeft + firstLine.xRight) / 2f - colMidX) <= colWidth * 0.14f
                                            val secondLineCheck = if (secondLineIsHeading) {
                                                secondLine!!.xLeft >= colLeft + 55f &&
                                                        secondLine.xRight <= colRight - 55f &&
                                                        abs((secondLine.xLeft + secondLine.xRight) / 2f - colMidX) <= colWidth * 0.14f
                                            } else true
                                            val strictlyCenteredSignal = firstLineStrictlyCentered && secondLineCheck

                                            val prominenceSignal = (firstLine.isBold && !firstBodyLine.isBold) ||
                                                    (lastHeadingLine.isBold && !firstBodyLine.isBold) ||
                                                    firstLine.maxFontSize >= bodyMedianFontSize + 0.4f ||
                                                    ((firstBodyLine.yTop - lastHeadingLine.yTop) >= bodyMedianStep * 1.18f && lastHeadingLine.xRight <= colRight - 35f) ||
                                                    strictlyCenteredSignal

                                            if (prominenceSignal) {
                                                val combinedHeading = if (secondLineIsHeading) {
                                                    val fTrim = firstLine.text.trim()
                                                    if (STANDALONE_PREFIX_REGEX.matches(fTrim)) "${fTrim.trimEnd(':', '.', '-', '–', '—').trim()}: ${secondLine!!.text.trim()}"
                                                    else "$fTrim ${secondLine!!.text.trim()}"
                                                } else firstLine.text.trim()

                                                val rawCleanHeading = sanitizeChapterTitle(combinedHeading.replace(LEADING_BULLET_REGEX, ""))
                                                val dialogueStartQuotes = setOf('"', '“', '‘', '\'', '「', '『', '(')
                                                val cleanHeading = rawCleanHeading.dropWhile { !it.isLetterOrDigit() && it !in dialogueStartQuotes }.trim()
                                                val words = cleanHeading.split(Regex("\\s+")).filter { it.isNotEmpty() }
                                                val sentenceEndPunct = setOf('.', ',', ';', '!', '?', ':', '"', '”', ']', '』', '」')

                                                // Guard A — Reject Narrative/Dialogue Lines in Pattern C
                                                val notNarrativeOrDialogue = !isLikelyNarrativeOrDialogueLine(firstLine.text, cleanHeading) &&
                                                        (!secondLineIsHeading || !isLikelyNarrativeOrDialogueLine(secondLine!!.text, cleanHeading))

                                                val isValid = cleanHeading.length in 4..110 &&
                                                        words.size in 1..16 &&
                                                        cleanHeading.firstOrNull()?.let { it.isUpperCase() || it.isDigit() } == true &&
                                                        cleanHeading.firstOrNull() !in dialogueStartQuotes &&
                                                        cleanHeading.lastOrNull() !in sentenceEndPunct &&
                                                        notNarrativeOrDialogue

                                                if (isValid) {
                                                    val isBoldOrLargerFont = (firstLine.isBold && !firstBodyLine.isBold) ||
                                                            (secondLineIsHeading && secondLine!!.isBold && !firstBodyLine.isBold) ||
                                                            firstLine.maxFontSize >= bodyMedianFontSize + 0.5f
                                                    val bm = PdfBookmark(cleanHeading, p)
                                                    patternCBookmarks.add(PatternCCandidate(bm, isBoldOrLargerFont))
                                                    patternCenteredMatched = true
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Pattern D (TOC Match) — Unnumbered Daftar Isi / Table of Contents Title Match
                    if (!patternAMatched && !patternBMatched && !patternCenteredMatched && p > tocMaxPage && unnumberedTocCandidatesMap.isNotEmpty()) {
                        for (line in candidateTopLines) {
                            val n = norm(line.text)
                            val matchedCand = unnumberedTocCandidatesMap[n]
                            if (matchedCand != null) {
                                val bm = PdfBookmark(matchedCand, p)
                                patternDBookmarks.add(bm)
                                break
                            } else {
                                val lineIdx = contentLines.indexOf(line)
                                val nextLine = contentLines.getOrNull(lineIdx + 1)
                                if (nextLine != null && (nextLine.yTop - line.yTop) in 2f..65f) {
                                    val twoLineNorm = norm("${line.text} ${nextLine.text}")
                                    val matchedTwo = unnumberedTocCandidatesMap[twoLineNorm]
                                    if (matchedTwo != null) {
                                        val bm = PdfBookmark(matchedTwo, p)
                                        patternDBookmarks.add(bm)
                                        break
                                    }
                                }
                            }
                        }
                    }

                    // Pattern D — Running Bottom Footer Transition & Top/Bottom Match
                    val bestFooter = pageBottomTitles[p]
                    val pageHasStage4Match = patternAMatched || patternBMatched || patternCenteredMatched
                    if (bestFooter != null && !pageHasStage4Match) {
                        val cleanFooter = sanitizeChapterTitle(bestFooter)
                        if (cleanFooter.length in 3..85 && (CHAPTER_KEYWORD_REGEX.matches(cleanFooter) || CHAPTER_KEYWORD_SUFFIX_REGEX.matches(cleanFooter))) {
                            val n = norm(cleanFooter)
                            if (n != normBookTitle && n !in globalWatermarkBlacklist && !cleanFooter.all { it.isDigit() || it.isWhitespace() || it == '.' }) {
                                val titleAlreadyMatched = patternABookmarks.any { norm(it.title) == n } ||
                                        patternBBookmarks.any { norm(it.title) == n } ||
                                        patternCBookmarks.any { norm(it.bookmark.title) == n }
                                if (!titleAlreadyMatched) {
                                    val bm = PdfBookmark(cleanFooter, p)
                                    footerBookmarks.add(bm)
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
                            footerTransitionBookmarks.add(PdfBookmark(t, p))
                        }
                    }
                }

                onStageProgress(45)

                // Guard D — Book-Level Chapter Style Consistency Filter (Before deduplicateChapters)
                val numberedKeywordPages = (detectedBookmarks + tocBookmarks + patternABookmarks)
                    .filter { Regex("(?i)^(?:bab|chapter|bagian|part|act|selingan)\\s*(?:\\d+|[ivxlcdm]+)\\b.*").matches(it.title) }
                    .map { it.startPageZero }

                val filteredPatternC = if (numberedKeywordPages.size >= 3) {
                    val lastNumberedPage = numberedKeywordPages.maxOrNull() ?: 0
                    patternCBookmarks.filter { cand ->
                        if (cand.bookmark.startPageZero <= lastNumberedPage) {
                            cand.isBoldOrLargerFont && patternABookmarks.none { abs(it.startPageZero - cand.bookmark.startPageZero) <= 20 }
                        } else {
                            true
                        }
                    }.map { it.bookmark }
                } else {
                    patternCBookmarks.map { it.bookmark }
                }

                val stage4Bookmarks = mutableListOf<PdfBookmark>().apply {
                    addAll(patternABookmarks)
                    addAll(patternBBookmarks)
                    addAll(filteredPatternC)
                    addAll(footerBookmarks)
                }

                val validConditionB = stage4Bookmarks.filter { norm(it.title) !in globalWatermarkBlacklist }
                val additiveBookmarks = mutableListOf<PdfBookmark>().apply {
                    addAll(patternABookmarks)
                    addAll(patternBBookmarks)
                    addAll(filteredPatternC)
                    addAll(footerBookmarks)
                    addAll(patternDBookmarks)
                    addAll(footerTransitionBookmarks)
                }

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

    fun buildBookChaptersFromBookmarks(
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
