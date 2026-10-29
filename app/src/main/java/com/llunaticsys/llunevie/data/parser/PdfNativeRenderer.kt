package com.llunaticsys.llunevie.data.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import com.llunaticsys.llunevie.data.local.BookChapterEntity
import com.llunaticsys.llunevie.data.local.BookEntity
import com.llunaticsys.llunevie.model.BookIllustration
import com.llunaticsys.llunevie.ui.components.normalizeBookTitle
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
import kotlin.math.ceil

object PdfNativeRenderer {

    private val renderMutex = Mutex()

    // High-speed in-memory Bitmap LRU Cache (up to ~32MB) for zero-lag PDF scrolling & page turns
    private val pageBitmapCache = object : LruCache<String, Bitmap>(32 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int {
            return (value.allocationByteCount / 1024).coerceAtLeast(1)
        }
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
        defaultTitle: String = "Dokumen PDF"
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
                "Bagian ${chIdx + 1} (Hal ${startIdx + 1}–$endIdx)"
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
            title = cleanTitle.ifBlank { "PDF Document" },
            author = "Dokumen PDF",
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
            author = "Dokumen PDF",
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
            defaultTitle = cleanTitle
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

    suspend fun extractLogicalPdfChapters(
        destPdfFile: File,
        totalPages: Int,
        bookId: String,
        coversDir: File,
        coverFilePath: String?,
        defaultTitle: String = "Dokumen PDF"
    ): Pair<List<BookChapterEntity>, List<BookIllustration>> = withContext(Dispatchers.IO) {
        val extractedIllustrations = mutableListOf<BookIllustration>()
        if (coverFilePath != null && File(coverFilePath).exists()) {
            extractedIllustrations.add(BookIllustration(uriOrPath = coverFilePath, caption = "Cover (Hal 1)"))
        }

        // Fast & thorough scan for illustrations: test low-res thumbnail first for speed
        val maxPagesToScan = minOf(totalPages, 300)
        runCatching {
            ParcelFileDescriptor.open(destPdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val testBmp = Bitmap.createBitmap(180, 240, Bitmap.Config.ARGB_8888)
                    val testCanvas = Canvas(testBmp)

                    for (pIdx in 1 until maxPagesToScan) {
                        renderer.openPage(pIdx).use { page ->
                            testCanvas.drawColor(Color.WHITE)
                            page.render(testBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                            if (isIllustrationOrArtworkPage(testBmp, pIdx)) {
                                val rawW = page.width.coerceAtLeast(1)
                                val rawH = page.height.coerceAtLeast(1)
                                val hiW = 720
                                val hiH = (rawH * (hiW.toFloat() / rawW)).toInt().coerceIn(480, 1600)
                                val hiBmp = Bitmap.createBitmap(hiW, hiH, Bitmap.Config.ARGB_8888)
                                val hiCanvas = Canvas(hiBmp)
                                hiCanvas.drawColor(Color.WHITE)
                                page.render(hiBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                val illustFile = File(coversDir, "illust_p${pIdx + 1}.jpg")
                                FileOutputStream(illustFile).use { out ->
                                    hiBmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                                }
                                hiBmp.recycle()

                                extractedIllustrations.add(
                                    BookIllustration(
                                        uriOrPath = illustFile.absolutePath,
                                        caption = "Ilustrasi (Hal ${pIdx + 1})"
                                    )
                                )
                            }
                        }
                    }
                    testBmp.recycle()
                }
            }
        }

        // Deterministic PDF outline & bookmark detection with accurate page-tree destination mapping
        data class PdfBookmark(val title: String, val startPageZero: Int)
        val detectedBookmarks = mutableListOf<PdfBookmark>()

        runCatching {
            val bytes = destPdfFile.readBytes()
            val contentStr = String(bytes, Charsets.ISO_8859_1)

            // 1. Build an index of object body definitions: objId -> content
            val objBodies = mutableMapOf<Int, String>()
            val objRegex = Regex("""(\d+)\s+0\s+obj\b([\s\S]*?)\bendobj\b""")
            for (m in objRegex.findAll(contentStr)) {
                val objId = m.groupValues[1].toIntOrNull()
                if (objId != null) {
                    objBodies[objId] = m.groupValues[2]
                }
            }

            // 2. Resolve PDF /Pages tree root from Catalog or /Pages objects
            var pagesRootId: Int? = null
            for ((id, body) in objBodies) {
                if (body.contains("/Type") && body.contains("/Catalog")) {
                    val rootPagesMatch = Regex("""/Pages\s+(\d+)\s+0\s+R""").find(body)
                    if (rootPagesMatch != null) {
                        pagesRootId = rootPagesMatch.groupValues[1].toIntOrNull()
                        break
                    }
                }
            }

            // 3. Traverse /Pages tree depth-first to obtain EXACT 1-to-1 sequential list of /Page leaf objects
            val orderedPageObjIds = mutableListOf<Int>()
            fun traversePages(nodeId: Int, visited: MutableSet<Int>) {
                if (!visited.add(nodeId)) return
                val body = objBodies[nodeId] ?: return
                val kidsMatch = Regex("""/Kids\s*\[([\s\S]*?)\]""").find(body)
                if (kidsMatch != null) {
                    val refRegex = Regex("""(\d+)\s+0\s+R""")
                    for (ref in refRegex.findAll(kidsMatch.groupValues[1])) {
                        val kidId = ref.groupValues[1].toIntOrNull() ?: continue
                        val kidBody = objBodies[kidId].orEmpty()
                        if (kidBody.contains("/Type") && kidBody.contains("/Pages")) {
                            traversePages(kidId, visited)
                        } else {
                            orderedPageObjIds.add(kidId)
                        }
                    }
                } else if (body.contains("/Type") && body.contains("/Page\b")) {
                    orderedPageObjIds.add(nodeId)
                }
            }

            if (pagesRootId != null) {
                traversePages(pagesRootId, mutableSetOf())
            }

            // Fallback if /Pages tree could not be traversed: scan /Type /Page objects in document order
            if (orderedPageObjIds.isEmpty()) {
                val fallbackRegex = Regex("""(\d+)\s+0\s+obj\s*<<[^>]*?/Type\s*/Page\b""")
                for (m in fallbackRegex.findAll(contentStr)) {
                    m.groupValues[1].toIntOrNull()?.let { orderedPageObjIds.add(it) }
                }
            }

            val pageObjToZero = orderedPageObjIds.mapIndexed { idx, objId -> objId to idx }.toMap()

            // 4. Find all outline bookmark objects and map to exact 0-based page index
            for ((_, body) in objBodies) {
                if (!body.contains("/Title")) continue
                val titleMatch = Regex("""/Title\s*(\((?:[^()\\]|\\.)*\)|<[0-9a-fA-F]+>)""").find(body) ?: continue
                val raw = titleMatch.groupValues[1]
                val parsed = if (raw.startsWith("(")) {
                    raw.removeSurrounding("(", ")")
                        .replace("\\(", "(")
                        .replace("\\)", ")")
                        .replace("\\n", " ")
                        .replace("\\r", " ")
                        .trim()
                } else if (raw.startsWith("<") && raw.endsWith(">")) {
                    val hex = raw.removeSurrounding("<", ">").trim()
                    runCatching {
                        val byteArray = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                        if (byteArray.size >= 2 && byteArray[0] == 0xFE.toByte() && byteArray[1] == 0xFF.toByte()) {
                            String(byteArray, 2, byteArray.size - 2, Charsets.UTF_16BE)
                        } else {
                            String(byteArray, Charsets.UTF_8)
                        }
                    }.getOrDefault("")
                } else ""

                val clean = normalizeBookTitle(parsed.trim())
                if (clean.length in 3..80 &&
                    !clean.contains("Microsoft Word", true) &&
                    !clean.contains("Acrobat", true) &&
                    !clean.contains("untitled", true) &&
                    !clean.matches(Regex("""^page\s*\d+$""", RegexOption.IGNORE_CASE))
                ) {
                    val destMatch = Regex("""(?:/Dest|/D)\s*(?:\[\s*(\d+)\s+0\s+R|(\d+)\s+0\s+R)""").find(body)
                    val destObj = destMatch?.groupValues?.get(1)?.toIntOrNull()
                        ?: destMatch?.groupValues?.get(2)?.toIntOrNull()
                    val targetPageZero = destObj?.let { pageObjToZero[it] }

                    if (targetPageZero != null && targetPageZero in 0 until totalPages) {
                        if (detectedBookmarks.none { it.startPageZero == targetPageZero }) {
                            detectedBookmarks.add(PdfBookmark(clean, targetPageZero))
                        }
                    } else {
                        // Check if title has explicit page number like "Hal 45" or "Page 45"
                        val pageMatch = Regex("""(?:hal|page|p\.?)\s*(\d+)""", RegexOption.IGNORE_CASE).find(parsed)
                        val num = pageMatch?.groupValues?.get(1)?.toIntOrNull()
                        val pZero = if (num != null && num in 1..totalPages) num - 1 else null
                        if (pZero != null && detectedBookmarks.none { it.startPageZero == pZero }) {
                            detectedBookmarks.add(PdfBookmark(clean, pZero))
                        }
                    }
                }
            }
        }

        // Sort bookmarks strictly by startPageZero and eliminate duplicates
        val sortedBookmarks = detectedBookmarks
            .distinctBy { it.startPageZero }
            .sortedBy { it.startPageZero }
            .toMutableList()

        val chapters = mutableListOf<BookChapterEntity>()
        if (sortedBookmarks.isNotEmpty()) {
            if (sortedBookmarks.first().startPageZero > 0) {
                sortedBookmarks.add(0, PdfBookmark(normalizeBookTitle(defaultTitle), 0))
            }
            for (i in sortedBookmarks.indices) {
                val bm = sortedBookmarks[i]
                val startIdx = bm.startPageZero
                val nextStartIdx = if (i + 1 < sortedBookmarks.size) sortedBookmarks[i + 1].startPageZero else totalPages
                val endIdx = nextStartIdx.coerceIn(startIdx + 1, totalPages)

                val pagesArr = JSONArray()
                for (pIdx in startIdx until endIdx) {
                    pagesArr.put("[PDF_PAGE:$pIdx]")
                }
                val illustArr = JSONArray()
                if (i == 0) {
                    extractedIllustrations.forEach { il ->
                        illustArr.put(JSONObject().apply {
                            put("uriOrPath", il.uriOrPath)
                            put("caption", il.caption)
                        })
                    }
                }
                chapters.add(
                    BookChapterEntity(
                        bookId = bookId,
                        chapterIndex = i,
                        title = bm.title,
                        pagesJson = pagesArr.toString(),
                        illustrationsJson = illustArr.toString()
                    )
                )
            }
        } else {
            // Document without embedded bookmarks: use natural chapter/bagian subdivisions
            val chaptersCount = when {
                totalPages <= 24 -> 1
                totalPages <= 60 -> 2
                totalPages <= 120 -> 4
                totalPages <= 240 -> 6
                else -> 10
            }
            val pagesPerChapter = ceil(totalPages.toDouble() / chaptersCount.toDouble()).toInt().coerceAtLeast(1)
            var startIdx = 0
            var chIdx = 0
            while (startIdx < totalPages) {
                val endIdx = (startIdx + pagesPerChapter).coerceAtMost(totalPages)
                val pagesArr = JSONArray()
                for (pIdx in startIdx until endIdx) {
                    pagesArr.put("[PDF_PAGE:$pIdx]")
                }
                val illustArr = JSONArray()
                if (chIdx == 0) {
                    extractedIllustrations.forEach { il ->
                        illustArr.put(JSONObject().apply {
                            put("uriOrPath", il.uriOrPath)
                            put("caption", il.caption)
                        })
                    }
                }
                val title = if (chaptersCount == 1) {
                    defaultTitle
                } else {
                    "Bagian ${chIdx + 1} (Hal ${startIdx + 1})"
                }
                chapters.add(
                    BookChapterEntity(
                        bookId = bookId,
                        chapterIndex = chIdx,
                        title = title,
                        pagesJson = pagesArr.toString(),
                        illustrationsJson = illustArr.toString()
                    )
                )
                chIdx++
                startIdx = endIdx
            }
        }

        Pair(chapters, extractedIllustrations)
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
        val widthBucket = ((targetWidthPx.coerceIn(360, 1280) / 120) * 120).coerceAtLeast(360)
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
                            val scale = (widthBucket.toFloat() / rawW.toFloat()).coerceIn(0.85f, 2.2f)
                            val bmpW = (rawW * scale).toInt().coerceIn(240, 1440)
                            val bmpH = (rawH * scale).toInt().coerceIn(320, 2160)
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
}
