package com.llunaticsys.llunevie.data.parser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import com.llunaticsys.llunevie.data.local.BookChapterEntity
import com.llunaticsys.llunevie.data.local.BookEntity
import com.llunaticsys.llunevie.ui.components.normalizeBookTitle
import com.llunaticsys.llunevie.ui.components.sanitizeBookOrChapterText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.io.FileOutputStream
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipFile

data class ParsedBookBundle(
    val book: BookEntity,
    val chapters: List<BookChapterEntity>
)

object EpubNativeParser {

    private const val MAX_SINGLE_ENTRY_BYTES = 16L * 1024L * 1024L // 16 MB per entry cap
    private const val MAX_CUMULATIVE_EPUB_BYTES = 64L * 1024L * 1024L // 64 MB cumulative budget

    // Byte-bounded in-memory LRU Cache for EPUB illustration images (24 MB max, size in KB)
    private val fullResImageCache = object : LruCache<String, Bitmap>(24 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int =
            (value.allocationByteCount / 1024).coerceAtLeast(1)
    }

    fun clearMemoryCaches() {
        fullResImageCache.evictAll()
    }

    fun getCachedFullResImage(imagePath: String): Bitmap? {
        val cached = fullResImageCache.get(imagePath)
        return if (cached != null && !cached.isRecycled) cached else null
    }

    fun decodeFullResImage(imagePath: String, maxDimPx: Int = 2048): Bitmap? {
        if (imagePath.isBlank()) return null
        fullResImageCache.get(imagePath)?.let {
            if (!it.isRecycled) return it
        }
        val cleanPath = if (imagePath.startsWith("file://")) imagePath.removePrefix("file://") else imagePath
        val file = File(cleanPath)
        if (!file.exists()) return null
        return runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(cleanPath, boundsOpts)
            val rawW = boundsOpts.outWidth.coerceAtLeast(1)
            val rawH = boundsOpts.outHeight.coerceAtLeast(1)
            var sampleSize = 1
            while ((rawW / sampleSize) > maxDimPx || (rawH / sampleSize) > maxDimPx) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bmp = BitmapFactory.decodeFile(cleanPath, options)
            if (bmp != null) {
                fullResImageCache.put(imagePath, bmp)
            }
            bmp
        }.getOrNull()
    }

    private fun resolveRelativeZipPath(baseDir: String, relHref: String): String {
        val clean = relHref.substringBefore("#").substringBefore("?").trimStart('/')
        if (baseDir.isBlank()) return clean
        val stack = baseDir.split("/").filter { it.isNotBlank() }.toMutableList()
        for (part in clean.split("/")) {
            when (part) {
                "", "." -> {}
                ".." -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
                else -> stack.add(part)
            }
        }
        return stack.joinToString("/")
    }

    private fun streamZipEntryToFileBounded(
        zip: ZipFile,
        entry: java.util.zip.ZipEntry,
        destFile: File,
        maxBytes: Long
    ): Long {
        if (maxBytes <= 0L) return -1L
        var totalWritten = 0L
        val buffer = ByteArray(8192)
        zip.getInputStream(entry).use { input ->
            FileOutputStream(destFile).use { output ->
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    totalWritten += read
                    if (totalWritten > maxBytes) {
                        return -1L
                    }
                    output.write(buffer, 0, read)
                }
            }
        }
        return totalWritten
    }

    private fun readZipEntryTextBounded(
        zip: ZipFile,
        entry: java.util.zip.ZipEntry,
        maxBytes: Long
    ): Pair<String, Long>? {
        if (maxBytes <= 0L) return null
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var totalRead = 0L
        zip.getInputStream(entry).use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                totalRead += read
                if (totalRead > maxBytes) {
                    return null
                }
                out.write(buffer, 0, read)
            }
        }
        return out.toString(Charsets.UTF_8.name()) to totalRead
    }

    suspend fun importAndParseEpubWithProgress(
        context: Context,
        uri: Uri,
        fallbackFileName: String,
        targetShelf: String = "eReader",
        isEn: Boolean = false,
        onInitialBookCreated: suspend (BookEntity) -> Unit = {},
        onProgressUpdate: (String, Int) -> Unit = { _, _ -> }
    ): ParsedBookBundle = withContext(Dispatchers.IO) {
        val bookId = "epub-${System.currentTimeMillis()}-${java.util.UUID.randomUUID().toString().take(6)}"
        val coversDir = File(context.filesDir, "covers/$bookId").apply { mkdirs() }

        var tempEpubToDelete: File? = null
        val (zipFile, resolvedLocalFilePath) = if (uri.scheme == "file" && uri.path != null && File(uri.path!!).canRead()) {
            val directFile = File(uri.path!!)
            Pair(ZipFile(directFile), directFile.absolutePath)
        } else {
            val tmp = File(context.cacheDir, "tmp_$bookId.epub")
            tempEpubToDelete = tmp
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(tmp).use { output ->
                    input.copyTo(output)
                }
            }
            Pair(ZipFile(tmp), uri.toString())
        }

        var bookTitle = sanitizeBookOrChapterText(normalizeBookTitle(fallbackFileName))
        val todayKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val yearStr = SimpleDateFormat("yyyy", Locale.US).format(Date())

        val initialBook = BookEntity(
            id = bookId,
            title = bookTitle.ifBlank { "EPUB Document" },
            author = "EPUB Document",
            year = yearStr,
            publisher = "EPUB Edition",
            edition = "EPUB Digital",
            isbn = "—",
            category = "Light Novel",
            shelf = targetShelf,
            format = "EPUB",
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
            synopsis = bookTitle
        )
        onInitialBookCreated(initialBook)
        onProgressUpdate(bookId, 35)

        var bookAuthor = ""
        var bookYear = SimpleDateFormat("yyyy", Locale.US).format(Date())
        var bookPublisher = "EPUB Edition"
        var bookIsbn = "—"
        var bookSynopsis = ""
        var bookGenres = ""
        var coverFilePath: String? = null

        val extractedImagesByZipPath = mutableMapOf<String, String>()
        val extractedImagesByFileName = mutableMapOf<String, String>()
        val chapterEntities = mutableListOf<BookChapterEntity>()

        try {
            zipFile.use { zip ->
            var cumulativeExtractedBytes = 0L
            // 1. Stream images directly to internal storage with a 64 MB cumulative budget (no full ByteArray buffering in RAM)
            val entries = zip.entries()
            var imgCounter = 0
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory && entry.name.matches(Regex(""".*\.(jpe?g|png|webp|gif)$""", RegexOption.IGNORE_CASE))) {
                    val remainingBudget = (MAX_CUMULATIVE_EPUB_BYTES - cumulativeExtractedBytes).coerceAtMost(MAX_SINGLE_ENTRY_BYTES)
                    if (remainingBudget <= 0L) break

                    val normPath = entry.name.trimStart('/')
                    val baseName = normPath.substringAfterLast('/')
                    val ext = normPath.substringAfterLast('.', "jpg").lowercase()

                    runCatching {
                        val streamedFile = File(coversDir, "raw_${imgCounter}.$ext")
                        val writtenBytes = streamZipEntryToFileBounded(zip, entry, streamedFile, remainingBudget)
                        if (writtenBytes <= 0L) {
                            streamedFile.delete()
                            return@runCatching
                        }
                        cumulativeExtractedBytes += writtenBytes

                        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(streamedFile.absolutePath, boundsOpts)
                        val w = boundsOpts.outWidth
                        val h = boundsOpts.outHeight
                        val compressedSize = entry.compressedSize.takeIf { it > 0 } ?: writtenBytes
                        val shouldCompress = (w > 1600 || h > 1600 || compressedSize > 350 * 1024)

                        val outImgFile: File
                        if (shouldCompress && w > 0 && h > 0) {
                            var sampleSize = 1
                            while ((w / sampleSize) > 3200 || (h / sampleSize) > 3200) {
                                sampleSize *= 2
                            }
                            val decodeOpts = BitmapFactory.Options().apply {
                                inSampleSize = sampleSize
                            }
                            val decoded = BitmapFactory.decodeFile(streamedFile.absolutePath, decodeOpts)
                            if (decoded != null) {
                                val maxDim = maxOf(decoded.width, decoded.height)
                                val finalBmp = if (maxDim > 1600) {
                                    val scale = 1600f / maxDim.toFloat()
                                    val sw = (decoded.width * scale).toInt().coerceAtLeast(1)
                                    val sh = (decoded.height * scale).toInt().coerceAtLeast(1)
                                    val scaled = Bitmap.createScaledBitmap(decoded, sw, sh, true)
                                    if (scaled != decoded) decoded.recycle()
                                    scaled
                                } else {
                                    decoded
                                }
                                outImgFile = File(coversDir, "img_${imgCounter++}.jpg")
                                FileOutputStream(outImgFile).use { out ->
                                    finalBmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
                                }
                                finalBmp.recycle()
                                streamedFile.delete()
                            } else {
                                outImgFile = File(coversDir, "img_${imgCounter++}.$ext")
                                if (!streamedFile.renameTo(outImgFile)) {
                                    streamedFile.copyTo(outImgFile, overwrite = true)
                                    streamedFile.delete()
                                }
                            }
                        } else {
                            outImgFile = File(coversDir, "img_${imgCounter++}.$ext")
                            if (!streamedFile.renameTo(outImgFile)) {
                                streamedFile.copyTo(outImgFile, overwrite = true)
                                streamedFile.delete()
                            }
                        }

                        extractedImagesByZipPath[normPath] = outImgFile.absolutePath
                        extractedImagesByZipPath[normPath.lowercase()] = outImgFile.absolutePath
                        extractedImagesByFileName[baseName] = outImgFile.absolutePath
                        extractedImagesByFileName[baseName.lowercase()] = outImgFile.absolutePath
                    }
                }
            }

            var cumulativeTextBytes = 0L

            // 2. Locate OPF package via META-INF/container.xml
            var opfPath: String? = null
            val containerEntry = zip.getEntry("META-INF/container.xml")
            if (containerEntry != null) {
                val pair = readZipEntryTextBounded(
                    zip,
                    containerEntry,
                    (MAX_CUMULATIVE_EPUB_BYTES - cumulativeTextBytes).coerceAtMost(MAX_SINGLE_ENTRY_BYTES)
                )
                if (pair != null) {
                    cumulativeTextBytes += pair.second
                    val doc = Jsoup.parse(pair.first, "", Parser.xmlParser())
                    val rootFile = doc.selectFirst("rootfile[full-path]")
                    if (rootFile != null) {
                        opfPath = rootFile.attr("full-path").trimStart('/')
                    }
                }
            }
            if (opfPath == null) {
                val allEntries = zip.entries()
                while (allEntries.hasMoreElements()) {
                    val e = allEntries.nextElement()
                    if (!e.isDirectory && e.name.endsWith(".opf", ignoreCase = true)) {
                        opfPath = e.name.trimStart('/')
                        break
                    }
                }
            }

            val manifestHrefById = mutableMapOf<String, String>()
            val tocTitlesByHref = mutableMapOf<String, String>()
            val spineHrefs = mutableListOf<String>()
            val opfBaseDir = opfPath?.takeIf { it.contains("/") }?.substringBeforeLast("/") ?: ""

            if (opfPath != null && zip.getEntry(opfPath) != null) {
                val opfXml = zip.getInputStream(zip.getEntry(opfPath)).bufferedReader().use { it.readText() }
                val opfDoc = Jsoup.parse(opfXml, "", Parser.xmlParser())

                opfDoc.selectFirst("dc|title, title")?.text()?.trim()?.takeIf { it.isNotBlank() }?.let { rawTitle ->
                    val cleanOpf = sanitizeBookOrChapterText(rawTitle)
                    val normFallback = normalizeBookTitle(fallbackFileName)
                    val volRegex = Regex("""(?i)\b(?:vol(?:ume)?\.?\s*(\d+(?:\.\d+)?)|jilid\s*(\d+))\b""")
                    val volMatchFallback = volRegex.find(normFallback)
                    val volMatchOpf = volRegex.find(cleanOpf)
                    bookTitle = if (volMatchFallback != null && volMatchOpf == null) {
                        "$cleanOpf — ${volMatchFallback.value.trim()}"
                    } else {
                        cleanOpf
                    }
                }
                opfDoc.selectFirst("dc|creator, creator")?.text()?.trim()?.takeIf { it.isNotBlank() }?.let {
                    bookAuthor = it
                }
                opfDoc.selectFirst("dc|publisher, publisher")?.text()?.trim()?.takeIf { it.isNotBlank() }?.let {
                    bookPublisher = it
                }
                opfDoc.selectFirst("dc|date, date")?.text()?.trim()?.take(4)?.takeIf { it.length == 4 }?.let {
                    bookYear = it
                }
                opfDoc.selectFirst("dc|description, description")?.text()?.let { rawDesc ->
                    val cleaned = Jsoup.parse(rawDesc).text().trim()
                    if (cleaned.isNotBlank()) bookSynopsis = cleaned
                }
                opfDoc.select("dc|identifier, identifier").forEach { idEl ->
                    val txt = idEl.text().trim()
                    if (txt.replace(Regex("[^0-9Xx]"), "").length in 10..13) {
                        bookIsbn = txt
                    }
                }

                val extractedGenres = opfDoc.select("dc|subject, subject")
                    .map { sanitizeBookOrChapterText(it.text().trim()) }
                    .filter { it.length in 2..32 && !it.equals("Light Novel", true) && !it.equals("Novel", true) }
                    .take(4)
                    .joinToString(", ")
                if (extractedGenres.isNotBlank()) {
                    bookGenres = extractedGenres
                }

                val metaCoverId = opfDoc.selectFirst("meta[name=cover]")?.attr("content")?.trim()
                var ncxHref: String? = null
                var navHref: String? = null

                for (item in opfDoc.select("manifest > item, item")) {
                    val id = item.attr("id").trim()
                    val rawHref = item.attr("href").trim()
                    if (rawHref.isBlank()) continue
                    val decodedHref = runCatching { URLDecoder.decode(rawHref, "UTF-8") }.getOrDefault(rawHref)
                    val fullHref = resolveRelativeZipPath(opfBaseDir, decodedHref)
                    val mediaType = item.attr("media-type").lowercase()
                    val props = item.attr("properties").lowercase()

                    if (id.isNotBlank()) {
                        manifestHrefById[id] = fullHref
                    }
                    if (props.contains("cover-image") || (metaCoverId != null && id == metaCoverId) ||
                        (mediaType.startsWith("image/") && (id.contains("cover", true) || rawHref.contains("cover", true)))
                    ) {
                        if (coverFilePath == null) {
                            coverFilePath = extractedImagesByZipPath[fullHref]
                                ?: extractedImagesByZipPath[fullHref.lowercase()]
                                ?: extractedImagesByFileName[fullHref.substringAfterLast('/')]
                        }
                    }
                    if (mediaType.contains("ncx") || fullHref.endsWith(".ncx", true)) {
                        ncxHref = fullHref
                    }
                    if (props.contains("nav")) {
                        navHref = fullHref
                    }
                }

                // Parse NCX Table of Contents
                if (ncxHref != null && zip.getEntry(ncxHref) != null) {
                    runCatching {
                        val ncxXml = zip.getInputStream(zip.getEntry(ncxHref)).bufferedReader().use { it.readText() }
                        val ncxDoc = Jsoup.parse(ncxXml, "", Parser.xmlParser())
                        val ncxDir = if (ncxHref.contains("/")) ncxHref.substringBeforeLast("/") else ""
                        for (np in ncxDoc.select("navPoint")) {
                            val label = np.selectFirst("navLabel > text, text")?.text()?.trim().orEmpty()
                            val src = np.selectFirst("content")?.attr("src")?.trim().orEmpty()
                            if (label.isNotBlank() && src.isNotBlank()) {
                                val resolved = resolveRelativeZipPath(ncxDir, URLDecoder.decode(src, "UTF-8"))
                                tocTitlesByHref[resolved] = label
                                tocTitlesByHref[resolved.lowercase()] = label
                                tocTitlesByHref[resolved.substringAfterLast('/').lowercase()] = label
                            }
                        }
                    }
                }

                // Parse EPUB3 NAV Table of Contents
                if (navHref != null && zip.getEntry(navHref) != null) {
                    runCatching {
                        val navHtml = zip.getInputStream(zip.getEntry(navHref)).bufferedReader().use { it.readText() }
                        val navDoc = Jsoup.parse(navHtml)
                        val navDir = if (navHref.contains("/")) navHref.substringBeforeLast("/") else ""
                        for (a in navDoc.select("nav a[href], ol a[href]")) {
                            val label = a.text().trim()
                            val href = a.attr("href").trim()
                            if (label.isNotBlank() && href.isNotBlank()) {
                                val resolved = resolveRelativeZipPath(navDir, URLDecoder.decode(href, "UTF-8"))
                                tocTitlesByHref.putIfAbsent(resolved, label)
                                tocTitlesByHref.putIfAbsent(resolved.lowercase(), label)
                                tocTitlesByHref.putIfAbsent(resolved.substringAfterLast('/').lowercase(), label)
                            }
                        }
                    }
                }

                // Parse Spine reading order
                for (itemRef in opfDoc.select("spine > itemref, itemref")) {
                    val idref = itemRef.attr("idref").trim()
                    val targetHref = manifestHrefById[idref]
                    if (targetHref != null && zip.getEntry(targetHref) != null) {
                        spineHrefs.add(targetHref)
                    }
                }
            }

            // Fallback if spine was empty: scan all XHTML/HTML entries sorted naturally
            if (spineHrefs.isEmpty()) {
                val htmlEntries = mutableListOf<String>()
                val all = zip.entries()
                while (all.hasMoreElements()) {
                    val e = all.nextElement()
                    if (!e.isDirectory && e.name.matches(Regex(""".*\.(xhtml|html|htm)$""", RegexOption.IGNORE_CASE)) &&
                        !e.name.contains("toc", ignoreCase = true) && !e.name.contains("nav", ignoreCase = true)
                    ) {
                        htmlEntries.add(e.name.trimStart('/'))
                    }
                }
                htmlEntries.sort()
                spineHrefs.addAll(htmlEntries)
            }

            // 3. Parse every chapter in Spine using Jsoup across <p>, headings, blockquote, li, and general div/text structures
            for ((idx, href) in spineHrefs.withIndex()) {
                val entry = zip.getEntry(href) ?: continue
                val remainingTextBudget = (MAX_CUMULATIVE_EPUB_BYTES - cumulativeTextBytes).coerceAtMost(MAX_SINGLE_ENTRY_BYTES)
                if (remainingTextBudget <= 0L) break
                val htmlPair = readZipEntryTextBounded(zip, entry, remainingTextBudget) ?: continue
                cumulativeTextBytes += htmlPair.second
                val rawHtml = htmlPair.first
                val doc = Jsoup.parse(rawHtml)
                val chDir = if (href.contains("/")) href.substringBeforeLast("/") else ""
                val baseName = href.substringAfterLast('/').lowercase()

                val body = doc.body() ?: doc
                val isCoverDoc = (idx == 0 && doc.select("img, image").isNotEmpty() && body.text().trim().length < 30)
                val rawTitle = tocTitlesByHref[href]
                    ?: tocTitlesByHref[href.lowercase()]
                    ?: tocTitlesByHref[baseName]
                    ?: doc.selectFirst("h1, h2, h3, [class*='title'], [class*='chapter'], b, strong, title")?.text()?.trim()?.takeIf {
                        it.isNotBlank() && !it.equals("untitled", true) && !it.equals("chapter", true)
                    }

                var chTitle = if (isCoverDoc) {
                    if (isEn) "Cover" else "Sampul"
                } else if (!rawTitle.isNullOrBlank()) {
                    sanitizeBookOrChapterText(normalizeBookTitle(rawTitle))
                } else {
                    if (isEn) "Chapter ${chapterEntities.size + 1}" else "Bab ${chapterEntities.size + 1}"
                }

                val chapterParagraphs = mutableListOf<String>()
                val chapterIllustrations = JSONArray()
                val seenImages = mutableSetOf<String>()

                // Traverse paragraphs, headings, blockquotes, list items, and images in document order
                val orderedElements: List<Element> = body.select("p, h1, h2, h3, h4, blockquote, li, img, image")
                var hasStructuredTextBlocks = false
                for (el in orderedElements) {
                    val tag = el.tagName()
                    if (tag.equals("img", ignoreCase = true) || tag.equals("image", ignoreCase = true)) {
                        val rawSrc = el.attr("src").ifBlank { el.attr("xlink:href") }.ifBlank { el.attr("href") }
                        if (rawSrc.isNotBlank()) {
                            val dec = runCatching { URLDecoder.decode(rawSrc.substringBefore("#"), "UTF-8") }.getOrDefault(rawSrc)
                            val resolved = resolveRelativeZipPath(chDir, dec)
                            val localImgPath = extractedImagesByZipPath[resolved]
                                ?: extractedImagesByZipPath[resolved.lowercase()]
                                ?: extractedImagesByFileName[dec.substringAfterLast('/')]
                                ?: extractedImagesByFileName[dec.substringAfterLast('/').lowercase()]

                            if (localImgPath != null && seenImages.add(localImgPath)) {
                                if (coverFilePath == null) coverFilePath = localImgPath
                                val alt = el.attr("alt").trim()
                                val caption = if (alt.length > 2) "$chTitle — $alt" else "$chTitle · Ilustrasi #${chapterIllustrations.length() + 1}"
                                chapterIllustrations.put(JSONObject().apply {
                                    put("uriOrPath", localImgPath)
                                    put("caption", caption)
                                })
                                chapterParagraphs.add("[ILLUST:$localImgPath|$caption]")
                            }
                        }
                    } else {
                        hasStructuredTextBlocks = true
                        val txt = sanitizeBookOrChapterText(el.text()).replace(Regex("""\s+"""), " ").trim()
                        if (txt.isNotBlank() && !txt.equals(chTitle, ignoreCase = true)) {
                            chapterParagraphs.add(txt)
                        }
                    }
                }

                if (!hasStructuredTextBlocks) {
                    // Fallback for EPUBs that use <div> or <br>-separated text directly inside <body>
                    val cleanedBody = body.wholeText().lines().map { sanitizeBookOrChapterText(it).replace(Regex("""\s+"""), " ").trim() }.filter { it.isNotBlank() }
                    for (line in cleanedBody) {
                        if (!line.equals(chTitle, ignoreCase = true)) {
                            chapterParagraphs.add(line)
                        }
                    }
                }

                if (chapterParagraphs.isNotEmpty()) {
                    // Paginate paragraphs into comfortable pages (~820 chars or standalone illustration page)
                    val pagesArr = JSONArray()
                    val currentChunk = mutableListOf<String>()
                    var currentChars = 0

                    for (para in chapterParagraphs) {
                        if (para.startsWith("[ILLUST:")) {
                            if (currentChunk.isNotEmpty()) {
                                pagesArr.put(currentChunk.joinToString("\n\n"))
                                currentChunk.clear()
                                currentChars = 0
                            }
                            pagesArr.put(para)
                        } else {
                            // Check BEFORE adding to prevent oversized chunks
                            if (currentChunk.isNotEmpty() && (currentChars + para.length > 580 || currentChunk.size >= 4)) {
                                pagesArr.put(currentChunk.joinToString("\n\n"))
                                currentChunk.clear()
                                currentChars = 0
                            }
                            // Split single oversized paragraphs at sentence boundaries
                            if (para.length > 620) {
                                val sentences = para.split(Regex("""(?<=[.!?…])\s+|(?<=[.!?…]["”’'])\s+"""))
                                var subChunk = StringBuilder()
                                for (sent in sentences) {
                                    if (subChunk.isNotEmpty() && subChunk.length + sent.length > 580) {
                                        if (currentChunk.isNotEmpty()) {
                                            currentChunk.add(subChunk.toString())
                                            pagesArr.put(currentChunk.joinToString("\n\n"))
                                            currentChunk.clear()
                                            currentChars = 0
                                        } else {
                                            pagesArr.put(subChunk.toString())
                                        }
                                        subChunk = StringBuilder()
                                    }
                                    if (subChunk.isNotEmpty()) subChunk.append(" ")
                                    subChunk.append(sent)
                                }
                                if (subChunk.isNotEmpty()) {
                                    currentChunk.add(subChunk.toString())
                                    currentChars += subChunk.length
                                }
                            } else {
                                currentChunk.add(para)
                                currentChars += para.length
                            }
                        }
                    }
                    if (currentChunk.isNotEmpty()) {
                        pagesArr.put(currentChunk.joinToString("\n\n"))
                    }

                    if (pagesArr.length() > 0) {
                        val pagesJsonStr = pagesArr.toString()
                        if (pagesJsonStr.length <= 750_000) {
                            chapterEntities.add(
                                BookChapterEntity(
                                    bookId = bookId,
                                    chapterIndex = chapterEntities.size,
                                    title = chTitle,
                                    pagesJson = pagesJsonStr,
                                    illustrationsJson = chapterIllustrations.toString()
                                )
                            )
                        } else {
                            var chunkArr = JSONArray()
                            var curChars = 2
                            val subChunks = mutableListOf<JSONArray>()
                            for (pIdx in 0 until pagesArr.length()) {
                                val pStr = pagesArr.getString(pIdx)
                                val escLen = JSONObject.quote(pStr).length + 1
                                if (chunkArr.length() > 0 && curChars + escLen > 700_000) {
                                    subChunks.add(chunkArr)
                                    chunkArr = JSONArray()
                                    curChars = 2
                                }
                                chunkArr.put(pStr)
                                curChars += escLen
                            }
                            if (chunkArr.length() > 0) {
                                subChunks.add(chunkArr)
                            }
                            subChunks.forEachIndexed { partIdx, subArr ->
                                val partTitle = if (subChunks.size > 1) "$chTitle — Part ${partIdx + 1}" else chTitle
                                val partIllust = if (partIdx == 0) chapterIllustrations.toString() else "[]"
                                chapterEntities.add(
                                    BookChapterEntity(
                                        bookId = bookId,
                                        chapterIndex = chapterEntities.size,
                                        title = partTitle,
                                        pagesJson = subArr.toString(),
                                        illustrationsJson = partIllust
                                    )
                                )
                            }
                        }
                    }
                }
            }
            }
        } finally {
            tempEpubToDelete?.delete()
        }

        if (coverFilePath == null && extractedImagesByFileName.isNotEmpty()) {
            coverFilePath = extractedImagesByFileName.values.first()
        }

        if (bookAuthor.isBlank()) {
            bookAuthor = "Penulis EPUB"
        }

        if (bookSynopsis.isBlank() && chapterEntities.isNotEmpty()) {
            val firstPages = JSONArray(chapterEntities.first().pagesJson)
            for (i in 0 until firstPages.length()) {
                val p = firstPages.optString(i)
                if (!p.startsWith("[ILLUST:") && p.length > 20) {
                    bookSynopsis = p.take(360)
                    break
                }
            }
        }

        val totalPages = chapterEntities.sumOf { JSONArray(it.pagesJson).length() }.coerceAtLeast(1)
        val coverBitmap = coverFilePath?.let {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            runCatching { BitmapFactory.decodeFile(it, opts) }.getOrNull()
        }
        val (spineHex, spineTextHex) = SpineColorExtractor.extractSpineColors(coverBitmap)
        coverBitmap?.recycle()

        val bookEntity = BookEntity(
            id = bookId,
            title = bookTitle,
            author = bookAuthor,
            year = bookYear,
            publisher = bookPublisher,
            edition = "EPUB Digital",
            isbn = bookIsbn,
            category = "Light Novel",
            shelf = targetShelf,
            format = "EPUB",
            pagesCount = totalPages,
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
            coverImagePath = coverFilePath,
            spineColorHex = spineHex,
            spineTextColorHex = spineTextHex,
            synopsis = bookSynopsis.ifBlank { bookTitle },
            genres = bookGenres
        )
        onInitialBookCreated(bookEntity)
        onProgressUpdate(bookId, 100)

        ParsedBookBundle(book = bookEntity, chapters = chapterEntities)
    }

    suspend fun importAndParseEpub(
        context: Context,
        uri: Uri,
        fallbackFileName: String,
        targetShelf: String = "eReader"
    ): ParsedBookBundle = importAndParseEpubWithProgress(
        context = context,
        uri = uri,
        fallbackFileName = fallbackFileName,
        targetShelf = targetShelf
    )
}
