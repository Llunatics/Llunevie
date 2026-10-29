package com.llunaticsys.llunevie.data.parser

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
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

    suspend fun importAndParseEpubWithProgress(
        context: Context,
        uri: Uri,
        fallbackFileName: String,
        targetShelf: String = "eReader",
        isEn: Boolean = false,
        onInitialBookCreated: suspend (BookEntity) -> Unit = {},
        onProgressUpdate: (String, Int) -> Unit = { _, _ -> }
    ): ParsedBookBundle = withContext(Dispatchers.IO) {
        val bookId = "epub-${System.currentTimeMillis()}"
        val booksDir = File(context.filesDir, "books").apply { mkdirs() }
        val coversDir = File(context.filesDir, "covers/$bookId").apply { mkdirs() }

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
            synopsis = bookTitle
        )
        onInitialBookCreated(initialBook)
        onProgressUpdate(bookId, 15)

        val destEpubFile = File(booksDir, "$bookId.epub")
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(destEpubFile).use { output ->
                input.copyTo(output)
            }
        }
        onProgressUpdate(bookId, 35)
        var bookAuthor = ""
        var bookYear = SimpleDateFormat("yyyy", Locale.US).format(Date())
        var bookPublisher = "EPUB Edition"
        var bookIsbn = "—"
        var bookSynopsis = ""
        var coverFilePath: String? = null

        val extractedImagesByZipPath = mutableMapOf<String, String>()
        val extractedImagesByFileName = mutableMapOf<String, String>()
        val chapterEntities = mutableListOf<BookChapterEntity>()

        ZipFile(destEpubFile).use { zip ->
            // 1. Extract images to internal storage so covers & chapter illustrations persist
            val entries = zip.entries()
            var imgCounter = 0
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (!entry.isDirectory && entry.name.matches(Regex(""".*\.(jpe?g|png|webp|gif)$""", RegexOption.IGNORE_CASE))) {
                    val normPath = entry.name.trimStart('/')
                    val ext = normPath.substringAfterLast('.', "jpg")
                    val outImgFile = File(coversDir, "img_${imgCounter++}.$ext")
                    runCatching {
                        zip.getInputStream(entry).use { input ->
                            FileOutputStream(outImgFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        extractedImagesByZipPath[normPath] = outImgFile.absolutePath
                        extractedImagesByZipPath[normPath.lowercase()] = outImgFile.absolutePath
                        val baseName = normPath.substringAfterLast('/')
                        extractedImagesByFileName[baseName] = outImgFile.absolutePath
                        extractedImagesByFileName[baseName.lowercase()] = outImgFile.absolutePath
                    }
                }
            }

            // 2. Locate OPF package via META-INF/container.xml
            var opfPath: String? = null
            val containerEntry = zip.getEntry("META-INF/container.xml")
            if (containerEntry != null) {
                val xml = zip.getInputStream(containerEntry).bufferedReader().use { it.readText() }
                val doc = Jsoup.parse(xml, "", Parser.xmlParser())
                val rootFile = doc.selectFirst("rootfile[full-path]")
                if (rootFile != null) {
                    opfPath = rootFile.attr("full-path").trimStart('/')
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

                opfDoc.selectFirst("dc|title, title")?.text()?.trim()?.takeIf { it.isNotBlank() }?.let {
                    bookTitle = sanitizeBookOrChapterText(it)
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
                val rawHtml = zip.getInputStream(entry).bufferedReader().use { it.readText() }
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
                // Collect images inside this chapter
                for (imgEl in body.select("img, image")) {
                    val rawSrc = imgEl.attr("src").ifBlank { imgEl.attr("xlink:href") }.ifBlank { imgEl.attr("href") }
                    if (rawSrc.isNotBlank()) {
                        val dec = runCatching { URLDecoder.decode(rawSrc.substringBefore("#"), "UTF-8") }.getOrDefault(rawSrc)
                        val resolved = resolveRelativeZipPath(chDir, dec)
                        val localImgPath = extractedImagesByZipPath[resolved]
                            ?: extractedImagesByZipPath[resolved.lowercase()]
                            ?: extractedImagesByFileName[dec.substringAfterLast('/')]
                            ?: extractedImagesByFileName[dec.substringAfterLast('/').lowercase()]

                        if (localImgPath != null && seenImages.add(localImgPath)) {
                            if (coverFilePath == null) coverFilePath = localImgPath
                            val alt = imgEl.attr("alt").trim()
                            val caption = if (alt.length > 2) "$chTitle — $alt" else "$chTitle · Ilustrasi #${chapterIllustrations.length() + 1}"
                            chapterIllustrations.put(JSONObject().apply {
                                put("uriOrPath", localImgPath)
                                put("caption", caption)
                            })
                            chapterParagraphs.add("[ILLUST:$localImgPath|$caption]")
                        }
                    }
                }

                // Extract structured paragraphs from <p>, <h1>..<h6>, <blockquote>, <li>, or fallback leaf blocks
                val blockElements: List<Element> = body.select("p, h1, h2, h3, h4, blockquote, li")
                if (blockElements.isNotEmpty()) {
                    for (el in blockElements) {
                        val txt = sanitizeBookOrChapterText(el.text()).replace(Regex("""\s+"""), " ").trim()
                        if (txt.isNotBlank() && !txt.equals(chTitle, ignoreCase = true)) {
                            chapterParagraphs.add(txt)
                        }
                    }
                } else {
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
                            currentChunk.add(para)
                            currentChars += para.length
                            if (currentChars >= 820 || currentChunk.size >= 5) {
                                pagesArr.put(currentChunk.joinToString("\n\n"))
                                currentChunk.clear()
                                currentChars = 0
                            }
                        }
                    }
                    if (currentChunk.isNotEmpty()) {
                        pagesArr.put(currentChunk.joinToString("\n\n"))
                    }

                    if (pagesArr.length() > 0) {
                        chapterEntities.add(
                            BookChapterEntity(
                                bookId = bookId,
                                chapterIndex = chapterEntities.size,
                                title = chTitle,
                                pagesJson = pagesArr.toString(),
                                illustrationsJson = chapterIllustrations.toString()
                            )
                        )
                    }
                }
            }
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
        val coverBitmap = coverFilePath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
        val (spineHex, spineTextHex) = SpineColorExtractor.extractSpineColors(coverBitmap)

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
            onTheGo = true,
            timeMinutes = 0,
            readingSeconds = 0,
            savedChapterIndex = 0,
            savedPageIndex = 0,
            bookmarked = false,
            localFilePath = destEpubFile.absolutePath,
            coverImagePath = coverFilePath,
            spineColorHex = spineHex,
            spineTextColorHex = spineTextHex,
            synopsis = bookSynopsis.ifBlank { bookTitle }
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
