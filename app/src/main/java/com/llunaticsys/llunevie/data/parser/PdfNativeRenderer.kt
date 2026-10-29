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
import com.llunaticsys.llunevie.ui.components.sanitizeBookOrChapterText
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

        // Use PDF stream pre-filter: test only candidate pages (capped to 12)
        val candidatePages = scanCandidateIllustrationPagesFromPdfStream(destPdfFile, totalPages).take(12)
        if (candidatePages.isNotEmpty()) {
            runCatching {
                ParcelFileDescriptor.open(destPdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        for (pIdx in candidatePages) {
                            if (pIdx >= renderer.pageCount) continue
                            renderer.openPage(pIdx).use { page ->
                                val thumbW = 360
                                val thumbH = (page.height.toFloat() / page.width.toFloat() * thumbW).toInt().coerceIn(360, 600)
                                val smallBmp = Bitmap.createBitmap(thumbW, thumbH, Bitmap.Config.ARGB_8888)
                                val smallCanvas = Canvas(smallBmp)
                                smallCanvas.drawColor(Color.WHITE)
                                page.render(smallBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                if (isTrueIllustrationPageBitmap(smallBmp)) {
                                    val illustFile = File(coversDir, "illust_p${pIdx + 1}.jpg")
                                    if (!illustFile.exists()) {
                                        val hiW = 540
                                        val hiH = (page.height.toFloat() / page.width.toFloat() * hiW).toInt().coerceIn(400, 1200)
                                        val hiBmp = Bitmap.createBitmap(hiW, hiH, Bitmap.Config.ARGB_8888)
                                        val hiCanvas = Canvas(hiBmp)
                                        hiCanvas.drawColor(Color.WHITE)
                                        page.render(hiBmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                        FileOutputStream(illustFile).use { out ->
                                            hiBmp.compress(Bitmap.CompressFormat.JPEG, 88, out)
                                        }
                                        hiBmp.recycle()
                                    }
                                    extractedIllustrations.add(
                                        BookIllustration(
                                            uriOrPath = illustFile.absolutePath,
                                            caption = "Ilustrasi (Hal ${pIdx + 1})",
                                            pageIndexInBook = pIdx
                                        )
                                    )
                                }
                                smallBmp.recycle()
                            }
                        }
                    }
                }
            }
        }

        // Deterministic PDF outline & bookmark detection with accurate page-tree destination mapping
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

                val clean = sanitizeBookOrChapterText(normalizeBookTitle(parsed.trim()))
                if (clean.length in 3..80 &&
                    !clean.contains("Microsoft Word", true) &&
                    !clean.contains("Acrobat", true) &&
                    !clean.contains("untitled", true) &&
                    !clean.matches(Regex("""^page\s*\d+$""", RegexOption.IGNORE_CASE))
                ) {
                    val destMatch = Regex("""(?:/Dest|/D)\s*(?:\[\s*(\d+)\s+0\s+R|(\d+)\s+0\s+R)""").find(body)
                    var destObj = destMatch?.groupValues?.get(1)?.toIntOrNull()
                        ?: destMatch?.groupValues?.get(2)?.toIntOrNull()
                    if (destObj == null) {
                        val aInlineMatch = Regex("""/A\s*<<[^>]*?/(?:Dest|D)\s*(?:\[\s*(\d+)\s+0\s+R|(\d+)\s+0\s+R)""").find(body)
                        destObj = aInlineMatch?.groupValues?.get(1)?.toIntOrNull() ?: aInlineMatch?.groupValues?.get(2)?.toIntOrNull()
                    }
                    if (destObj == null) {
                        val aRefMatch = Regex("""/A\s+(\d+)\s+0\s+R""").find(body)
                        val aObjId = aRefMatch?.groupValues?.get(1)?.toIntOrNull()
                        if (aObjId != null) {
                            val aBody = objBodies[aObjId].orEmpty()
                            val aDestMatch = Regex("""(?:/Dest|/D)\s*(?:\[\s*(\d+)\s+0\s+R|(\d+)\s+0\s+R)""").find(aBody)
                            destObj = aDestMatch?.groupValues?.get(1)?.toIntOrNull() ?: aDestMatch?.groupValues?.get(2)?.toIntOrNull()
                        }
                    }
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
                sortedBookmarks.add(0, PdfBookmark(sanitizeBookOrChapterText(normalizeBookTitle(defaultTitle)), 0))
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

    fun readPdfContentString(pdfFile: File): String {
        return runCatching {
            String(pdfFile.readBytes(), Charsets.ISO_8859_1)
        }.getOrDefault("")
    }

    fun readPdfHeaderAndTrailer(pdfFile: File): String = readPdfContentString(pdfFile)

    fun isImageStreamHeader(header: String): Boolean {
        return header.contains("/Subtype /Image") ||
            header.contains("/Subtype/Image") ||
            header.contains("/DCTDecode") ||
            header.contains("/JPXDecode") ||
            header.contains("/CCITTFaxDecode") ||
            header.contains("/BitsPerComponent") ||
            (header.contains("/Width") && header.contains("/Height") && !header.contains("/ObjStm"))
    }

    fun decodePdfLiteral(raw: String): String {
        val decoded = java.lang.StringBuilder()
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            if (c == '\\' && i + 1 < raw.length) {
                val next = raw[i + 1]
                if (next in '0'..'7') {
                    var octalLen = 1
                    while (octalLen < 3 && i + 1 + octalLen < raw.length && raw[i + 1 + octalLen] in '0'..'7') {
                        octalLen++
                    }
                    val octalStr = raw.substring(i + 1, i + 1 + octalLen)
                    val code = octalStr.toIntOrNull(8) ?: 32
                    val ch = when (code) {
                        0x91, 0x92 -> '\''
                        0x93, 0x94 -> '"'
                        0x96, 0x97 -> '–'
                        0x85 -> '…'
                        0xA0 -> ' '
                        else -> code.toChar()
                    }
                    decoded.append(ch)
                    i += 1 + octalLen
                    continue
                } else {
                    when (next) {
                        'n' -> decoded.append('\n')
                        'r' -> decoded.append(' ')
                        't' -> decoded.append('\t')
                        '(' -> decoded.append('(')
                        ')' -> decoded.append(')')
                        '\\' -> decoded.append('\\')
                        else -> decoded.append(next)
                    }
                    i += 2
                    continue
                }
            } else {
                val code = c.code
                val ch = when (code) {
                    0x91, 0x92 -> '\''
                    0x93, 0x94 -> '"'
                    0x96, 0x97 -> '–'
                    0x85 -> '…'
                    0xA0 -> ' '
                    else -> c
                }
                decoded.append(ch)
                i++
            }
        }
        return decoded.toString()
    }

    fun decodeHexLiteral(rawHex: String): String {
        val clean = rawHex.replace(Regex("[^0-9a-fA-F]"), "")
        if (clean.length < 2) return ""
        return runCatching {
            val bytes = clean.chunked(2).mapNotNull { it.toIntOrNull(16)?.toByte() }.toByteArray()
            if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
                String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            } else {
                String(bytes, Charsets.UTF_8)
            }
        }.getOrDefault("")
    }

    fun extractTextLinesFromContentStream(streamText: String): List<String> {
        val lines = mutableListOf<String>()
        val currentLine = java.lang.StringBuilder()

        fun flushLine() {
            val s = currentLine.toString().trim()
            if (s.isNotEmpty()) {
                lines.add(s)
            }
            currentLine.setLength(0)
        }

        val opRegex = Regex("""(\((?:[^()\\]|\\.)*\)|<[0-9a-fA-F\s]+>)\s*(\'|\"|Tj)|\[([\s\S]*?)\]\s*TJ|(\bT\*\b|\bET\b|\bTd\b|\bTD\b|\bTm\b)""")

        for (m in opRegex.findAll(streamText)) {
            val tjStr = m.groupValues[1]
            val tjOp = m.groupValues[2]
            val arrayContent = m.groupValues[3]
            val lineBreakOp = m.groupValues[4]

            if (lineBreakOp.isNotEmpty()) {
                flushLine()
                continue
            }

            if (tjStr.isNotEmpty()) {
                val text = if (tjStr.startsWith("(")) {
                    decodePdfLiteral(tjStr.removeSurrounding("(", ")"))
                } else if (tjStr.startsWith("<")) {
                    decodeHexLiteral(tjStr.removeSurrounding("<", ">"))
                } else ""

                if (tjOp == "'" || tjOp == "\"") {
                    flushLine()
                }
                currentLine.append(text)
                if (tjOp == "'" || tjOp == "\"") {
                    flushLine()
                }
            } else if (arrayContent.isNotEmpty()) {
                val elemRegex = Regex("""(\((?:[^()\\]|\\.)*\)|<[0-9a-fA-F\s]+>|[-+]?[0-9]*\.?[0-9]+)""")
                for (em in elemRegex.findAll(arrayContent)) {
                    val token = em.groupValues[1]
                    if (token.startsWith("(")) {
                        currentLine.append(decodePdfLiteral(token.removeSurrounding("(", ")")))
                    } else if (token.startsWith("<")) {
                        currentLine.append(decodeHexLiteral(token.removeSurrounding("<", ">")))
                    } else {
                        val spacing = token.toFloatOrNull()
                        if (spacing != null && spacing < -120f) {
                            if (currentLine.isNotEmpty() && !currentLine.endsWith(" ")) {
                                currentLine.append(" ")
                            }
                        }
                    }
                }
            }
        }
        flushLine()
        return lines.map { it.trim() }.filter { it.length >= 2 }
    }

    fun extractAllPdfChaptersAndOutlines(
        pdfFile: File,
        totalPages: Int,
        defaultTitle: String
    ): List<PdfBookmark> {
        val detectedBookmarks = mutableListOf<PdfBookmark>()
        val tocBookmarks = mutableListOf<PdfBookmark>()

        runCatching {
            val contentStr = readPdfContentString(pdfFile)
            if (contentStr.isEmpty()) return emptyList()

            val objBodies = mutableMapOf<Int, String>()
            val objRegex = Regex("""(\d+)\s+0\s+obj\b([\s\S]*?)\bendobj\b""")
            for (m in objRegex.findAll(contentStr)) {
                val objId = m.groupValues[1].toIntOrNull() ?: continue
                objBodies[objId] = m.groupValues[2]
            }

            val inflatedStreams = mutableMapOf<Int, ByteArray>()
            val objStmTexts = mutableListOf<String>()

            for ((objId, body) in objBodies) {
                val streamIdx = body.indexOf("stream")
                if (streamIdx == -1) continue
                val dictHeader = body.substring(0, streamIdx)
                if (isImageStreamHeader(dictHeader)) continue

                val isCandidate = dictHeader.contains("/ObjStm") ||
                    dictHeader.contains("/Contents") ||
                    dictHeader.contains("/Outlines") ||
                    dictHeader.contains("/Annot") ||
                    dictHeader.contains("/Title") ||
                    !dictHeader.contains("/Subtype")

                if (!isCandidate) continue

                val endStreamIdx = body.lastIndexOf("endstream")
                if (endStreamIdx <= streamIdx) continue

                var start = streamIdx + 6
                if (start < body.length && body[start] == '\r') start++
                if (start < body.length && body[start] == '\n') start++
                var end = endStreamIdx
                if (end > start && body[end - 1] == '\n') end--
                if (end > start && body[end - 1] == '\r') end--
                if (end <= start) continue

                val rawData = body.substring(start, end).toByteArray(Charsets.ISO_8859_1)
                val inflated = if (dictHeader.contains("/FlateDecode")) {
                    inflatePdfStream(rawData) ?: rawData
                } else {
                    rawData
                }
                inflatedStreams[objId] = inflated

                if (dictHeader.contains("/ObjStm")) {
                    objStmTexts.add(String(inflated, Charsets.ISO_8859_1))
                }
            }

            var pagesRootId: Int? = null
            for ((_, body) in objBodies) {
                if (body.contains("/Type") && body.contains("/Catalog")) {
                    val rootPagesMatch = Regex("""/Pages\s+(\d+)\s+0\s+R""").find(body)
                    if (rootPagesMatch != null) {
                        pagesRootId = rootPagesMatch.groupValues[1].toIntOrNull()
                        break
                    }
                }
            }

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

            if (orderedPageObjIds.isEmpty()) {
                val fallbackRegex = Regex("""(\d+)\s+0\s+obj\s*<<[^>]*?/Type\s*/Page\b""")
                for (m in fallbackRegex.findAll(contentStr)) {
                    m.groupValues[1].toIntOrNull()?.let { orderedPageObjIds.add(it) }
                }
            }

            val pageObjToZero = orderedPageObjIds.mapIndexed { idx, objId -> objId to idx }.toMap()

            fun parseBookmarksFromText(text: String) {
                val titleRegex = Regex("""/Title\s*(\((?:[^()\\]|\\.)*\)|<[0-9a-fA-F]+>)([\s\S]*?)(?=/Title|\bendobj\b|>>)""")
                for (m in titleRegex.findAll(text)) {
                    val raw = m.groupValues[1]
                    val context = m.groupValues[2]

                    val parsed = if (raw.startsWith("(")) {
                        decodePdfLiteral(raw.removeSurrounding("(", ")"))
                    } else if (raw.startsWith("<")) {
                        decodeHexLiteral(raw.removeSurrounding("<", ">"))
                    } else ""

                    val clean = sanitizeBookOrChapterText(normalizeBookTitle(parsed.trim()))
                    if (clean.length in 2..80 &&
                        !clean.contains("Microsoft Word", true) &&
                        !clean.contains("Acrobat", true) &&
                        !clean.contains("untitled", true) &&
                        !clean.matches(Regex("""^page\s*\d+$""", RegexOption.IGNORE_CASE))
                    ) {
                        val destMatch = Regex("""(?:/Dest|/D)\s*(?:\[\s*(\d+)\s+0\s+R|(\d+)\s+0\s+R)""").find(context)
                        var destObj = destMatch?.groupValues?.get(1)?.toIntOrNull()
                            ?: destMatch?.groupValues?.get(2)?.toIntOrNull()
                        if (destObj == null) {
                            val aInlineMatch = Regex("""/A\s*<<[^>]*?/(?:Dest|D)\s*(?:\[\s*(\d+)\s+0\s+R|(\d+)\s+0\s+R)""").find(context)
                            destObj = aInlineMatch?.groupValues?.get(1)?.toIntOrNull() ?: aInlineMatch?.groupValues?.get(2)?.toIntOrNull()
                        }
                        if (destObj == null) {
                            val aRefMatch = Regex("""/A\s+(\d+)\s+0\s+R""").find(context)
                            val aObjId = aRefMatch?.groupValues?.get(1)?.toIntOrNull()
                            if (aObjId != null) {
                                val aBody = objBodies[aObjId].orEmpty()
                                val aDestMatch = Regex("""(?:/Dest|/D)\s*(?:\[\s*(\d+)\s+0\s+R|(\d+)\s+0\s+R)""").find(aBody)
                                destObj = aDestMatch?.groupValues?.get(1)?.toIntOrNull() ?: aDestMatch?.groupValues?.get(2)?.toIntOrNull()
                            }
                        }

                        val targetPageZero = destObj?.let { pageObjToZero[it] }
                        if (targetPageZero != null && targetPageZero in 0 until totalPages) {
                            if (detectedBookmarks.none { it.title.equals(clean, ignoreCase = true) && it.startPageZero == targetPageZero }) {
                                detectedBookmarks.add(PdfBookmark(clean, targetPageZero))
                            }
                        } else {
                            val pageMatch = Regex("""(?:hal|page|p\.?)\s*(\d+)""", RegexOption.IGNORE_CASE).find(parsed)
                            val num = pageMatch?.groupValues?.get(1)?.toIntOrNull()
                            val pZero = if (num != null && num in 1..totalPages) num - 1 else null
                            if (pZero != null && detectedBookmarks.none { it.title.equals(clean, ignoreCase = true) && it.startPageZero == pZero }) {
                                detectedBookmarks.add(PdfBookmark(clean, pZero))
                            }
                        }
                    }
                }
            }

            for ((_, body) in objBodies) {
                if (body.contains("/Title")) {
                    parseBookmarksFromText(body)
                }
            }
            for (stmText in objStmTexts) {
                if (stmText.contains("/Title")) {
                    parseBookmarksFromText(stmText)
                }
            }

            val pageTexts = mutableMapOf<Int, List<String>>()
            for (p in 0 until minOf(totalPages, orderedPageObjIds.size)) {
                val pageObjId = orderedPageObjIds[p]
                val pageBody = objBodies[pageObjId].orEmpty()

                val contentObjIds = mutableListOf<Int>()
                val contentsArrayMatch = Regex("""/Contents\s*\[([\s\S]*?)\]""").find(pageBody)
                if (contentsArrayMatch != null) {
                    val refRegex = Regex("""(\d+)\s+0\s+R""")
                    for (ref in refRegex.findAll(contentsArrayMatch.groupValues[1])) {
                        ref.groupValues[1].toIntOrNull()?.let { contentObjIds.add(it) }
                    }
                } else {
                    val singleRefMatch = Regex("""/Contents\s+(\d+)\s+0\s+R""").find(pageBody)
                    singleRefMatch?.groupValues?.get(1)?.toIntOrNull()?.let { contentObjIds.add(it) }
                }

                val pageLines = mutableListOf<String>()
                for (cId in contentObjIds) {
                    val streamData = inflatedStreams[cId]
                    if (streamData != null) {
                        val streamStr = String(streamData, Charsets.ISO_8859_1)
                        pageLines.addAll(extractTextLinesFromContentStream(streamStr))
                    }
                }
                pageTexts[p] = pageLines
            }

            val tocHeaderRegex = Regex("""(?i)^\s*(contents|table of contents|daftar isi|isi buku)\s*$""")
            var tocStartPage: Int? = null

            for (p in 0 until minOf(25, totalPages)) {
                val lines = pageTexts[p] ?: emptyList()
                if (lines.any { tocHeaderRegex.matches(it.trim()) }) {
                    tocStartPage = p
                    break
                }
            }

            if (tocStartPage != null) {
                val tocPages = mutableListOf(tocStartPage)
                var curP = tocStartPage + 1
                while (curP < minOf(tocStartPage + 4, totalPages)) {
                    val lines = pageTexts[curP] ?: emptyList()
                    if (lines.isEmpty()) break
                    val shortLines = lines.count { it.trim().length in 3..68 }
                    val hasLongParagraphs = lines.any { it.trim().length > 130 }
                    if (!hasLongParagraphs && shortLines >= (lines.size * 0.60).toInt().coerceAtLeast(3)) {
                        tocPages.add(curP)
                        curP++
                    } else {
                        break
                    }
                }

                val rawTocLines = tocPages.flatMap { pageTexts[it] ?: emptyList() }
                val tocLineWithNumRegex = Regex("""^(.*?)(?:[.\u2026_\s]{2,}|\t+)\s*(\d{1,4})\s*$""")
                val numberedCount = rawTocLines.count { tocLineWithNumRegex.matches(it.trim()) }

                if (numberedCount >= 3) {
                    val entries = mutableListOf<Pair<String, Int>>()
                    for (line in rawTocLines) {
                        val m = tocLineWithNumRegex.find(line.trim()) ?: continue
                        val rawTitle = m.groupValues[1].trim()
                        val printedPage = m.groupValues[2].toIntOrNull() ?: continue
                        if (rawTitle.length in 3..75 && !tocHeaderRegex.matches(rawTitle)) {
                            entries.add(rawTitle to printedPage)
                        }
                    }
                    if (entries.isNotEmpty()) {
                        val minPrinted = entries.minOf { it.second }
                        val offset = if (minPrinted > 0 && minPrinted < 25) (tocStartPage - minPrinted + 1).coerceAtLeast(0) else 0
                        for ((title, pNum) in entries) {
                            val targetZero = (pNum - 1 + offset).coerceIn(0, totalPages - 1)
                            tocBookmarks.add(PdfBookmark(title, targetZero))
                        }
                    }
                } else {
                    val noiseTitles = setOf(
                        "cover", "title page", "copyright", "a note about this ebook",
                        "contents", "table of contents", "daftar isi", "isi buku"
                    )
                    val candidateTitles = rawTocLines.map { it.trim() }.filter { line ->
                        line.length in 3..68 &&
                            !line.all { it.isDigit() || it.isWhitespace() || it == '.' } &&
                            line.lowercase().replace(Regex("[^a-z0-9]"), "") !in noiseTitles
                    }

                    var lastFoundPage = (tocPages.last() + 1).coerceAtMost(totalPages - 1)
                    for (tocTitle in candidateTitles) {
                        val normToc = tocTitle.lowercase().replace(Regex("[^a-z0-9]"), "")
                        if (normToc.length < 3) continue

                        val existingBm = detectedBookmarks.find {
                            it.title.lowercase().replace(Regex("[^a-z0-9]"), "") == normToc
                        }
                        if (existingBm != null) {
                            tocBookmarks.add(existingBm)
                            lastFoundPage = existingBm.startPageZero
                            continue
                        }

                        var matchedPage: Int? = null
                        for (searchP in lastFoundPage until totalPages) {
                            val linesOnPage = pageTexts[searchP] ?: emptyList()
                            val topLines = linesOnPage.take(15)
                            val matched = topLines.any { line ->
                                val normLine = line.lowercase().replace(Regex("[^a-z0-9]"), "")
                                normLine == normToc ||
                                    (normToc.length >= 6 && normLine.contains(normToc)) ||
                                    (normToc.length >= 6 && normLine.length >= 6 && normToc.contains(normLine))
                            }
                            if (matched) {
                                matchedPage = searchP
                                break
                            }
                        }
                        if (matchedPage != null) {
                            tocBookmarks.add(PdfBookmark(tocTitle, matchedPage))
                            lastFoundPage = matchedPage
                        }
                    }
                }
            }

            if (detectedBookmarks.isEmpty() && tocBookmarks.isEmpty()) {
                for (p in 0 until totalPages) {
                    val lines = pageTexts[p] ?: emptyList()
                    val topLines = lines.take(6)
                    val heading = topLines.firstOrNull { line ->
                        val t = line.trim()
                        val isKeyword = t.startsWith("Chapter", ignoreCase = true) ||
                            t.startsWith("Bab ", ignoreCase = true) ||
                            t.startsWith("Part ", ignoreCase = true) ||
                            t.startsWith("Bagian ", ignoreCase = true) ||
                            t.startsWith("Book ", ignoreCase = true) ||
                            t.startsWith("Prologue", ignoreCase = true) ||
                            t.startsWith("Epilogue", ignoreCase = true) ||
                            t.startsWith("Act ", ignoreCase = true)
                        isKeyword || (t.length in 4..40 && t.all { it.isUpperCase() || it.isWhitespace() || it == '-' || it == '&' || it == '\'' })
                    }
                    if (heading != null) {
                        tocBookmarks.add(PdfBookmark(heading.trim(), p))
                    }
                }
            }
        }

        val allChapters = detectedBookmarks + tocBookmarks
        return deduplicateChapters(allChapters, totalPages, defaultTitle)
    }

    fun extractPdfBookmarksFromPdfFile(
        destPdfFile: File,
        totalPages: Int,
        defaultTitle: String
    ): List<PdfBookmark> {
        return extractAllPdfChaptersAndOutlines(destPdfFile, totalPages, defaultTitle)
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

    /**
     * PDF Stream Pre-Filter:
     * Scans the PDF byte stream without rendering pages. Counts occurrences of /XObject /Image references.
     * Any image referenced on >= 4 pages is a repeating parchment background or header decoration.
     * Returns 0-based page indices of candidate illustration pages (pages with non-repeating image XObjects).
     */
    fun scanCandidateIllustrationPagesFromPdfStream(pdfFile: File, totalPages: Int): List<Int> {
        val candidates = mutableListOf<Int>()
        runCatching {
            val bytes = pdfFile.readBytes()
            val contentStr = String(bytes, Charsets.ISO_8859_1)

            val objBodies = mutableMapOf<Int, String>()
            val objRegex = Regex("""(\d+)\s+0\s+obj\b([\s\S]*?)\bendobj\b""")
            for (m in objRegex.findAll(contentStr)) {
                val id = m.groupValues[1].toIntOrNull()
                if (id != null) objBodies[id] = m.groupValues[2]
            }

            // Find ordered page object IDs
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

            if (pagesRootId != null) traversePages(pagesRootId, mutableSetOf())
            if (orderedPageObjIds.isEmpty()) {
                val fallbackRegex = Regex("""(\d+)\s+0\s+obj\s*<<[^>]*?/Type\s*/Page\b""")
                for (m in fallbackRegex.findAll(contentStr)) {
                    m.groupValues[1].toIntOrNull()?.let { orderedPageObjIds.add(it) }
                }
            }

            // Map image XObject references across pages
            val imageRefOccurrences = mutableMapOf<String, Int>()
            val pageToImageRefs = mutableMapOf<Int, Set<String>>()

            for (pZero in orderedPageObjIds.indices) {
                val pageId = orderedPageObjIds[pZero]
                val pageBody = objBodies[pageId].orEmpty()
                val imageRefsInPage = mutableSetOf<String>()

                // Look for /XObject << ... >> directly in page body or via referenced Resources
                val resRefMatch = Regex("""/Resources\s+(\d+)\s+0\s+R""").find(pageBody)
                val resBody = if (resRefMatch != null) {
                    val resObjId = resRefMatch.groupValues[1].toIntOrNull()
                    resObjId?.let { objBodies[it] }.orEmpty()
                } else pageBody

                val xObjMatch = Regex("""/XObject\s*(?:<<([\s\S]*?)>>|(\d+)\s+0\s+R)""").find(resBody)
                val xObjContent = if (xObjMatch != null) {
                    val directDict = xObjMatch.groupValues[1]
                    val refId = xObjMatch.groupValues[2].toIntOrNull()
                    if (directDict.isNotBlank()) directDict
                    else refId?.let { objBodies[it] }.orEmpty()
                } else ""

                if (xObjContent.isNotBlank()) {
                    val entryRegex = Regex("""(/[\w\.\-]+)\s+(\d+)\s+0\s+R""")
                    for (entry in entryRegex.findAll(xObjContent)) {
                        val refObjId = entry.groupValues[2]
                        val targetObjBody = refObjId.toIntOrNull()?.let { objBodies[it] }.orEmpty()
                        if (targetObjBody.contains(Regex("""/Subtype\s*/Image""")) || !targetObjBody.contains(Regex("""/Subtype\s*/Form"""))) {
                            imageRefsInPage.add(refObjId)
                        }
                    }
                }
                pageToImageRefs[pZero] = imageRefsInPage
                for (img in imageRefsInPage) {
                    imageRefOccurrences[img] = (imageRefOccurrences[img] ?: 0) + 1
                }
            }

            // Candidate pages: pages > 0 with at least one image referenced on < 4 pages
            for (pZero in 1 until minOf(totalPages, orderedPageObjIds.size)) {
                val images = pageToImageRefs[pZero] ?: emptySet()
                val hasUniqueImage = images.any { (imageRefOccurrences[it] ?: 0) < 4 }
                if (hasUniqueImage) {
                    candidates.add(pZero)
                }
            }
        }
        return candidates
    }

    fun inflatePdfStream(compressed: ByteArray): ByteArray? {
        fun tryInflate(nowrap: Boolean): ByteArray? {
            val inflater = java.util.zip.Inflater(nowrap)
            return try {
                inflater.setInput(compressed)
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var iterations = 0
                while (!inflater.finished() && !inflater.needsInput() && !inflater.needsDictionary() && iterations < 128) {
                    val count = inflater.inflate(buffer)
                    if (count <= 0) break // CRITICAL: prevents infinite loop when inflate() returns 0!
                    out.write(buffer, 0, count)
                    if (out.size() > 64 * 1024) break
                    iterations++
                }
                out.toByteArray()
            } catch (_: Exception) {
                null
            } finally {
                inflater.end()
            }
        }
        return tryInflate(false) ?: tryInflate(true)
    }

    fun extractBookmarksFromTableOfContentsPage(pdfFile: File, totalPages: Int): List<PdfBookmark> {
        return extractAllPdfChaptersAndOutlines(pdfFile, totalPages, "PDF Document")
    }

    fun deduplicateChapters(
        rawChapters: List<PdfBookmark>,
        totalPages: Int,
        defaultTitle: String
    ): List<PdfBookmark> {
        val noiseWords = setOf("contents", "tableofcontents", "daftarisi", "isibuku", "copyright", "hakcipta", "cover", "sampul")
        val seenKeys = mutableSetOf<String>()
        val result = mutableListOf<PdfBookmark>()

        for (ch in rawChapters) {
            val clean = sanitizeBookOrChapterText(normalizeBookTitle(ch.title)).trim()
            if (clean.length < 2) continue
            if (clean.all { it.isDigit() || it.isWhitespace() || it == '.' }) continue
            val normKey = clean.lowercase().replace(Regex("[^a-z0-9]"), "")
            if (normKey in noiseWords) continue
            if (normKey.isEmpty()) continue
            if (seenKeys.contains(normKey)) continue
            val page = ch.startPageZero.coerceIn(0, totalPages - 1)

            seenKeys.add(normKey)
            result.add(PdfBookmark(clean, page))
        }

        result.sortBy { it.startPageZero }
        if (result.isNotEmpty() && result.first().startPageZero > 0) {
            result.add(0, PdfBookmark(sanitizeBookOrChapterText(normalizeBookTitle(defaultTitle)), 0))
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
        onProgress: suspend (Int) -> Unit = {},
        onUpdated: suspend (List<BookChapterEntity>, List<BookIllustration>) -> Unit
    ) = withContext(Dispatchers.Default) {
        if (!pdfFile.exists() || totalPages <= 0) return@withContext

        val coversDir = File(context.filesDir, "covers/$bookId").apply { mkdirs() }
        val detectedChapters = mutableListOf<PdfBookmark>()
        val detectedIllusts = mutableListOf<BookIllustration>()

        onProgress(5)
        val allChapters = extractAllPdfChaptersAndOutlines(pdfFile, totalPages, defaultTitle)
        detectedChapters.addAll(allChapters)

        onProgress(20)
        val finalBookmarks = deduplicateChapters(detectedChapters, totalPages, defaultTitle)

        // Fast, Non-Blocking Visual Page Scan (20% -> 100%) Using Dedicated PdfRenderer
        runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                PdfRenderer(pfd).use { renderer ->
                    val pageCount = renderer.pageCount
                    val step = if (pageCount <= 60) 1 else if (pageCount <= 120) 2 else (pageCount / 60).coerceAtLeast(1)
                    val pagesToInspect = (1 until minOf(totalPages, pageCount) step step).toList()

                    for ((idx, pIdx) in pagesToInspect.withIndex()) {
                        kotlinx.coroutines.delay(6L)
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
                        val pct = 20 + ((idx + 1) * 80 / pagesToInspect.size.coerceAtLeast(1))
                        onProgress(pct.coerceIn(20, 99))
                    }
                }
            }
        }

        onProgress(100)

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
                        title = sanitizeBookOrChapterText(bm.title),
                        pagesJson = pagesArr.toString(),
                        illustrationsJson = illustArr.toString()
                    )
                )
            }
            result
        } else {
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
                val title = if (chCount == 1) sanitizeBookOrChapterText(defaultTitle) else if (isEn) "Section ${cIdx + 1} (pp. ${s + 1}–$e)" else "Bagian ${cIdx + 1} (Hal ${s + 1}–$e)"
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
