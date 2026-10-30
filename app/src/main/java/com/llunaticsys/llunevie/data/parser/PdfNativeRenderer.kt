package com.llunaticsys.llunevie.data.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.graphics.pdf.PdfRenderer
import android.net.Uri
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

object PdfNativeRenderer {

    data class PdfBookmark(val title: String, val startPageZero: Int)

    private val renderMutex = Mutex()

    // High-speed in-memory Bitmap LRU Cache capped to at most 8 page bitmaps
    private val pageBitmapCache = object : LruCache<String, Bitmap>(8) {
        override fun sizeOf(key: String, value: Bitmap): Int = 1
    }

    private val aspectRatioCache = mutableMapOf<String, Float>()

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

    suspend fun importPdfDocumentWithProgress(
        context: Context,
        uri: Uri,
        fallbackFileName: String,
        targetShelf: String = "eReader",
        isEn: Boolean = false,
        onInitialBookCreated: suspend (BookEntity) -> Unit = {},
        onProgressUpdate: (String, Int) -> Unit = { _, _ -> }
    ): ParsedBookBundle = withContext(Dispatchers.IO) {
        val bookId = "pdf-${System.currentTimeMillis()}"
        val booksDir = File(context.filesDir, "books").apply { mkdirs() }
        val coversDir = File(context.filesDir, "covers/$bookId").apply { mkdirs() }
        val cleanTitle = normalizeBookTitle(fallbackFileName)
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val yearStr = SimpleDateFormat("yyyy", Locale.US).format(Date())

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
            localFilePath = null,
            coverImagePath = null,
            spineColorHex = 0xFF2B3A4AL,
            spineTextColorHex = 0xFFFAF6EBL,
            synopsis = cleanTitle
        )
        onInitialBookCreated(initialBook)
        onProgressUpdate(bookId, 15)

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
        onProgressUpdate(bookId, 50)

        var totalPages = 1
        var coverFilePath: String? = null
        var firstPageBitmap: Bitmap? = null

        runCatching {
            ParcelFileDescriptor.open(destPdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    totalPages = renderer.pageCount.coerceAtLeast(1)
                    if (renderer.pageCount > 0) {
                        renderer.openPage(0).use { page ->
                            val w = page.width.coerceAtLeast(1)
                            val h = page.height.coerceAtLeast(1)
                            aspectRatioCache[destPdfFile.absolutePath] = (w.toFloat() / h.toFloat()).coerceIn(0.45f, 1.6f)
                            val targetW = (w * 1.5f).toInt().coerceIn(360, 1080)
                            val targetH = (h * (targetW.toFloat() / w)).toInt().coerceIn(480, 1600)
                            val bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(bmp)
                            canvas.drawColor(Color.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            val coverFile = File(coversDir, "cover_p1.jpg")
                            FileOutputStream(coverFile).use { out ->
                                bmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
                            }
                            coverFilePath = coverFile.absolutePath
                            firstPageBitmap = bmp
                            pageBitmapCache.put("${destPdfFile.absolutePath}:0:720", bmp)
                        }
                    }
                }
            }
        }
        onProgressUpdate(bookId, 80)

        val (spineHex, spineTextHex) = SpineColorExtractor.extractSpineColors(firstPageBitmap)

        val bookWithCover = initialBook.copy(
            author = if (isEn) "PDF Document" else "Dokumen PDF",
            pagesCount = totalPages,
            localFilePath = destPdfFile.absolutePath,
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
        val w = bitmap.width
        val h = bitmap.height
        val step = 8
        var totalSamples = 0
        var whiteCount = 0
        var colorCount = 0
        var darkCount = 0
        var midToneCount = 0
        var maxDarkCluster = 0
        var curDarkCluster = 0

        for (y in 0 until h step step) {
            curDarkCluster = 0
            for (x in 0 until w step step) {
                totalSamples++
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt()

                if (r > 242 && g > 242 && b > 242) {
                    whiteCount++
                    curDarkCluster = 0
                } else {
                    val maxC = maxOf(r, g, b)
                    val minC = minOf(r, g, b)
                    if ((maxC - minC) > 24 && maxC > 40) {
                        colorCount++
                    }
                    if (lum < 115) {
                        darkCount++
                        curDarkCluster++
                        if (curDarkCluster > maxDarkCluster) maxDarkCluster = curDarkCluster
                    } else {
                        curDarkCluster = 0
                        if (lum in 115..220) {
                            midToneCount++
                        }
                    }
                }
            }
        }
        if (totalSamples == 0) return false
        val whiteRatio = whiteCount.toFloat() / totalSamples
        val colorRatio = colorCount.toFloat() / totalSamples
        val darkRatio = darkCount.toFloat() / totalSamples
        val midToneRatio = midToneCount.toFloat() / totalSamples

        // 1. Color insert / color illustration
        if (colorRatio > 0.022f) return true

        // 2. Full-bleed or dense illustration / manga spread / dark background
        if (whiteRatio < 0.82f && (darkRatio > 0.05f || midToneRatio > 0.05f)) return true

        // 3. Monochrome illustration or lineart character sketch with white paper background:
        // A standard novel text page has tiny letters (maxDarkCluster <= 2) and almost zero midtones.
        // Drawings have continuous hair/body contours (maxDarkCluster >= 4) or shaded areas/wash.
        if (maxDarkCluster >= 4 && (darkRatio >= 0.032f || midToneRatio >= 0.035f)) return true
        if (midToneRatio >= 0.048f) return true
        if (darkRatio >= 0.085f) return true

        return false
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
            .replace(Regex("\\s+"), " ")
            .trim()
        return sanitizeBookOrChapterText(normalizeBookTitle(t))
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

    fun deduplicateChapters(
        rawChapters: List<PdfBookmark>,
        totalPages: Int,
        defaultTitle: String
    ): List<PdfBookmark> {
        val noiseWords = setOf("contents", "tableofcontents", "daftarisi", "isibuku", "copyright", "hakcipta", "cover", "sampul")
        val seenKeys = mutableSetOf<String>()
        val seenPages = mutableSetOf<Int>()
        val result = mutableListOf<PdfBookmark>()

        for (ch in rawChapters) {
            val clean = sanitizeChapterTitle(ch.title)
            if (clean.length < 2) continue
            if (clean.all { it.isDigit() || it.isWhitespace() || it == '.' }) continue
            val normKey = clean.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (normKey in noiseWords) continue
            if (normKey.isEmpty()) continue
            if (seenKeys.contains(normKey)) continue

            val page = ch.startPageZero.coerceIn(0, totalPages - 1)
            if (seenPages.contains(page)) continue

            seenKeys.add(normKey)
            seenPages.add(page)
            result.add(PdfBookmark(clean, page))
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

                onStageProgress(55)
            }
        }

        val combined = when {
            detectedBookmarks.size >= 2 -> detectedBookmarks
            tocBookmarks.size >= 2 -> tocBookmarks
            else -> detectedBookmarks + tocBookmarks
        }

        return deduplicateChapters(combined, totalPages, defaultTitle)
    }

    fun isTrueIllustrationPage(bmp: Bitmap): Boolean = isTrueIllustrationPageBitmap(bmp)

    fun isTrueIllustrationPageBitmap(bmp: Bitmap): Boolean {
        val w = bmp.width
        val h = bmp.height
        if (w < 40 || h < 40) return false

        // Compute page border background luminance
        val borderLums = mutableListOf<Int>()
        for (x in 0 until w step 4) {
            val pTop = bmp.getPixel(x, 0)
            val pBot = bmp.getPixel(x, h - 1)
            borderLums.add((Color.red(pTop) * 299 + Color.green(pTop) * 587 + Color.blue(pTop) * 114) / 1000)
            borderLums.add((Color.red(pBot) * 299 + Color.green(pBot) * 587 + Color.blue(pBot) * 114) / 1000)
        }
        for (y in 0 until h step 4) {
            val pLeft = bmp.getPixel(0, y)
            val pRight = bmp.getPixel(w - 1, y)
            borderLums.add((Color.red(pLeft) * 299 + Color.green(pLeft) * 587 + Color.blue(pLeft) * 114) / 1000)
            borderLums.add((Color.red(pRight) * 299 + Color.green(pRight) * 587 + Color.blue(pRight) * 114) / 1000)
        }
        borderLums.sort()
        val paperLum = if (borderLums.isNotEmpty()) borderLums[(borderLums.size * 0.85).toInt().coerceIn(0, borderLums.size - 1)] else 245

        val yStart = (h * 0.08f).toInt()
        val yEnd = (h * 0.92f).toInt()
        val leftXStart = (w * 0.08f).toInt()
        val leftXEnd = (w * 0.48f).toInt()
        val rightXStart = (w * 0.52f).toInt()
        val rightXEnd = (w * 0.92f).toInt()

        val leftEdgeEnergy = FloatArray(h)
        val rightEdgeEnergy = FloatArray(h)
        val isRowOscillating = BooleanArray(h)

        // Compute horizontal gradient edge energy row by row for left and right columns
        for (y in yStart..yEnd) {
            var leftGradSum = 0f
            var leftCols = 0
            for (x in leftXStart until leftXEnd step 2) {
                val p1 = bmp.getPixel(x, y)
                val p2 = bmp.getPixel(x + 1, y)
                val l1 = (Color.red(p1) * 299 + Color.green(p1) * 587 + Color.blue(p1) * 114) / 1000
                val l2 = (Color.red(p2) * 299 + Color.green(p2) * 587 + Color.blue(p2) * 114) / 1000
                leftGradSum += abs(l2 - l1)
                leftCols++
            }
            leftEdgeEnergy[y] = if (leftCols > 0) leftGradSum / leftCols else 0f

            var rightGradSum = 0f
            var rightCols = 0
            for (x in rightXStart until rightXEnd step 2) {
                val p1 = bmp.getPixel(x, y)
                val p2 = bmp.getPixel(x + 1, y)
                val l1 = (Color.red(p1) * 299 + Color.green(p1) * 587 + Color.blue(p1) * 114) / 1000
                val l2 = (Color.red(p2) * 299 + Color.green(p2) * 587 + Color.blue(p2) * 114) / 1000
                rightGradSum += abs(l2 - l1)
                rightCols++
            }
            rightEdgeEnergy[y] = if (rightCols > 0) rightGradSum / rightCols else 0f
        }

        // Count oscillating text-line peaks in left and right columns
        var leftTextLines = 0
        var inLeftPeak = false
        var leftPeakStart = 0
        for (y in yStart..yEnd) {
            val e = leftEdgeEnergy[y]
            if (e > 16f) {
                if (!inLeftPeak) {
                    inLeftPeak = true
                    leftPeakStart = y
                }
            } else if (e < 7f && inLeftPeak) {
                val dur = y - leftPeakStart
                if (dur in 3..20) {
                    leftTextLines++
                    for (k in leftPeakStart..y) isRowOscillating[k] = true
                }
                inLeftPeak = false
            }
        }

        var rightTextLines = 0
        var inRightPeak = false
        var rightPeakStart = 0
        for (y in yStart..yEnd) {
            val e = rightEdgeEnergy[y]
            if (e > 16f) {
                if (!inRightPeak) {
                    inRightPeak = true
                    rightPeakStart = y
                }
            } else if (e < 7f && inRightPeak) {
                val dur = y - rightPeakStart
                if (dur in 3..20) {
                    rightTextLines++
                    for (k in rightPeakStart..y) isRowOscillating[k] = true
                }
                inRightPeak = false
            }
        }

        // Immediate full-text rejection rules:
        // Rule 1A: Two-column dense text page (e.g. Page 21, 23, 25, 223, 241, 451, 457, 461)
        if (leftTextLines >= 14 && rightTextLines >= 14) return false

        // Rule 1B: Dense single- or two-column text page with >= 24 text lines and < 30% free vertical space
        val totalTextLines = leftTextLines + rightTextLines
        var oscillatingRows = 0
        for (y in yStart..yEnd) {
            if (isRowOscillating[y]) oscillatingRows++
        }
        val totalVerticalSpan = (yEnd - yStart).coerceAtLeast(1)
        val freeVerticalRatio = 1f - (oscillatingRows.toFloat() / totalVerticalSpan)
        if (totalTextLines >= 24 && freeVerticalRatio < 0.30f) return false
        if (totalTextLines >= 18 && freeVerticalRatio < 0.20f) return false

        // Rule 2 — Require a contiguous non-text artwork region covering >= 22% of page area
        // Mask out oscillating text paragraphs
        val isTextParagraphRow = BooleanArray(h)
        for (y in yStart..(yEnd - 55)) {
            var peaksInWin = 0
            for (k in y..(y + 55)) {
                if (leftEdgeEnergy[k] > 16f || rightEdgeEnergy[k] > 16f) peaksInWin++
            }
            if (peaksInWin >= 6) {
                for (k in y..(y + 55)) isTextParagraphRow[k] = true
            }
        }

        // Check remaining non-text area for contiguous artwork region
        val gridCols = 18
        val gridRows = 25
        val cellW = (w / gridCols).coerceAtLeast(1)
        val cellH = (h / gridRows).coerceAtLeast(1)
        val isArtGrid = Array(gridRows) { BooleanArray(gridCols) }
        val hsv = FloatArray(3)

        for (r in 0 until gridRows) {
            val cyStart = r * cellH
            val cyEnd = (cyStart + cellH).coerceAtMost(h)
            val isTextRow = (cyStart until cyEnd).any { isTextParagraphRow[it] }
            if (isTextRow) continue

            for (c in 0 until gridCols) {
                val cxStart = c * cellW
                val cxEnd = (cxStart + cellW).coerceAtMost(w)
                var diffPixels = 0
                var saturatedPixels = 0
                var totalSampled = 0

                for (py in cyStart until cyEnd step 3) {
                    for (px in cxStart until cxEnd step 3) {
                        totalSampled++
                        val pix = bmp.getPixel(px, py)
                        val lum = (Color.red(pix) * 299 + Color.green(pix) * 587 + Color.blue(pix) * 114) / 1000
                        val diff = abs(lum - paperLum)
                        if (diff > 32) diffPixels++
                        Color.colorToHSV(pix, hsv)
                        if (hsv[1] > 0.18f && diff > 20) saturatedPixels++
                    }
                }

                if (totalSampled > 0) {
                    val diffRatio = diffPixels.toFloat() / totalSampled
                    val satRatio = saturatedPixels.toFloat() / totalSampled
                    if (diffRatio >= 0.35f || satRatio >= 0.28f) {
                        isArtGrid[r][c] = true
                    }
                }
            }
        }

        // Find largest contiguous 2D bounding box of artwork cells
        val visited = Array(gridRows) { BooleanArray(gridCols) }
        var maxBoxAreaCells = 0

        for (r in 0 until gridRows) {
            for (c in 0 until gridCols) {
                if (isArtGrid[r][c] && !visited[r][c]) {
                    var minR = r
                    var maxR = r
                    var minC = c
                    var maxC = c
                    var count = 0
                    val q = ArrayDeque<Pair<Int, Int>>()
                    q.add(r to c)
                    visited[r][c] = true

                    while (q.isNotEmpty()) {
                        val (cr, cc) = q.removeFirst()
                        count++
                        if (cr < minR) minR = cr
                        if (cr > maxR) maxR = cr
                        if (cc < minC) minC = cc
                        if (cc > maxC) maxC = cc

                        val neighbors = listOf(cr - 1 to cc, cr + 1 to cc, cr to cc - 1, cr to cc + 1)
                        for ((nr, nc) in neighbors) {
                            if (nr in 0 until gridRows && nc in 0 until gridCols && isArtGrid[nr][nc] && !visited[nr][nc]) {
                                visited[nr][nc] = true
                                q.add(nr to nc)
                            }
                        }
                    }

                    val boxW = (maxC - minC + 1).toFloat() / gridCols
                    val boxH = (maxR - minR + 1).toFloat() / gridRows
                    val boxArea = boxW * boxH
                    val cellRatio = count.toFloat() / (gridRows * gridCols)

                    if (boxArea >= 0.22f && boxW >= 0.35f && boxH >= 0.24f && cellRatio >= 0.14f) {
                        return true
                    }
                    if (count > maxBoxAreaCells) maxBoxAreaCells = count
                }
            }
        }

        return false
    }

    suspend fun scanPdfChaptersAndIllustrationsAsync(
        context: Context,
        bookId: String,
        pdfFile: File,
        totalPages: Int,
        defaultTitle: String,
        isEn: Boolean,
        onProgress: suspend (Int) -> Unit = {},
        onUpdated: suspend (List<BookChapterEntity>, List<BookIllustration>) -> Unit
    ) = withContext(Dispatchers.Default) {
        if (!pdfFile.exists() || totalPages <= 0) return@withContext

        val coversDir = File(context.filesDir, "covers/$bookId").apply { mkdirs() }
        val detectedIllusts = mutableListOf<BookIllustration>()

        onProgress(5)

        // Stage 1 & 2: Extract Outline & TOC via memory-safe PDFBox
        val finalBookmarks = extractChaptersWithPdfBox(
            context = context,
            pdfFile = pdfFile,
            totalPages = totalPages,
            defaultTitle = defaultTitle,
            onStageProgress = onProgress
        )

        onProgress(55)

        // Stage 3 (55% -> 95%): Illustration Detection using dedicated PdfRenderer
        runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pageCount = renderer.pageCount
                    val step = if (pageCount <= 60) 1 else if (pageCount <= 120) 2 else (pageCount / 60).coerceAtLeast(1)
                    val pagesToInspect = (1 until minOf(totalPages, pageCount) step step).toList()

                    for ((idx, pIdx) in pagesToInspect.withIndex()) {
                        kotlinx.coroutines.delay(4L)
                        renderer.openPage(pIdx).use { page ->
                            val thumbW = 200
                            val thumbH = 280
                            val smallBmp = Bitmap.createBitmap(thumbW, thumbH, Bitmap.Config.ARGB_8888)
                            val smallCanvas = Canvas(smallBmp)
                            smallCanvas.drawColor(Color.WHITE)
                            page.render(smallBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            if (isTrueIllustrationPageBitmap(smallBmp)) {
                                val illustFile = File(coversDir, "${bookId}_illust_p${pIdx + 1}.jpg")
                                if (!illustFile.exists()) {
                                    FileOutputStream(illustFile).use { out ->
                                        smallBmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
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
                        val pct = 55 + ((idx + 1) * 40 / pagesToInspect.size.coerceAtLeast(1))
                        onProgress(pct.coerceIn(55, 95))
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
