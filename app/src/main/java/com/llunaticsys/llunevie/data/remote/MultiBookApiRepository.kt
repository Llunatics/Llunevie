package com.llunaticsys.llunevie.data.remote

import com.llunaticsys.llunevie.model.CatalogBookCandidate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MultiBookApiRepository {

    private fun openConnection(urlStr: String): HttpURLConnection {
        val url = URL(urlStr)
        return (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 7000
            readTimeout = 7000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
            setRequestProperty("Accept", "application/json")
        }
    }

    private fun cleanAuthor(raw: String?): String {
        val s = raw?.trim().orEmpty()
        if (s.isBlank()) return ""
        if (s.equals("Author", ignoreCase = true) ||
            s.equals("Unknown", ignoreCase = true) ||
            s.equals("Unknown Author", ignoreCase = true)
        ) return ""
        if (s.contains(",") && !s.contains(" & ")) {
            val parts = s.split(",").map { it.trim() }
            if (parts.size == 2 && parts[0].isNotEmpty() && parts[1].isNotEmpty()) {
                return "${parts[1]} ${parts[0]}"
            }
        }
        return s
    }

    suspend fun searchGramedia(query: String): List<CatalogBookCandidate> = withContext(Dispatchers.IO) {
        val out = mutableListOf<CatalogBookCandidate>()
        runCatching {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val conn = openConnection("https://www.gramedia.com/api/algolia/search/products/?q=$encoded&page=1&per_page=12")
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                val hits = when {
                    body.startsWith("[") -> JSONArray(body)
                    body.startsWith("{") -> {
                        val obj = JSONObject(body)
                        obj.optJSONArray("results") ?: obj.optJSONArray("hits") ?: JSONArray()
                    }
                    else -> JSONArray()
                }
                for (i in 0 until hits.length()) {
                    val h = hits.optJSONObject(i) ?: continue
                    val title = h.optString("name").ifBlank { h.optString("title") }.trim()
                    if (title.isBlank()) continue
                    val author = cleanAuthor(h.optString("authors").ifBlank { h.optString("author") }).ifBlank { "Gramedia Pustaka Utama" }
                    val coverUrl = h.optString("thumbnail").ifBlank { h.optString("image") }.replace("http://", "https://").ifBlank { null }
                    val isbn = h.optString("isbn").ifBlank { h.optString("sku", "—") }
                    val desc = h.optString("description", "").replace(Regex("<[^>]+>"), "").trim()

                    out.add(
                        CatalogBookCandidate(
                            id = "gramedia-$i-${h.optString("id", isbn)}",
                            title = title,
                            author = author,
                            year = h.optString("publish_date", "2022").take(4).ifBlank { "2022" },
                            pages = h.optInt("pages", 320).takeIf { it > 0 } ?: 320,
                            rating = "4.85",
                            edition = "Gramedia",
                            publisher = h.optString("publisher", "Gramedia Pustaka Utama"),
                            lang = "id",
                            isbn = isbn,
                            category = "Novel",
                            coverUrl = coverUrl,
                            coverBgHex = 0xFF1E3A5FL,
                            synopsis = desc.ifBlank { "$title — $author (Gramedia Pustaka Utama)." }
                        )
                    )
                }
            }
        }
        out
    }

    suspend fun searchGoogleBooks(query: String, langRestrict: String = ""): List<CatalogBookCandidate> = withContext(Dispatchers.IO) {
        val out = mutableListOf<CatalogBookCandidate>()
        runCatching {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val langParam = if (langRestrict.isNotBlank()) "&langRestrict=$langRestrict&maxResults=15" else "&maxResults=20"
            val conn = openConnection("https://www.googleapis.com/books/v1/volumes?q=$encoded$langParam")
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val items = JSONObject(body).optJSONArray("items") ?: return@runCatching
                for (i in 0 until items.length()) {
                    val g = items.getJSONObject(i)
                    val volId = g.optString("id")
                    val info = g.optJSONObject("volumeInfo") ?: continue
                    val rawTitle = info.optString("title", "").trim()
                    if (rawTitle.isBlank()) continue
                    val subtitle = info.optString("subtitle", "").trim()
                    val fullTitle = if (subtitle.isNotBlank() && !rawTitle.contains(subtitle, ignoreCase = true) && subtitle.length < 48)
                        "$rawTitle: $subtitle" else rawTitle

                    val authorsArr = info.optJSONArray("authors")
                    val rawAuthor = if (authorsArr != null && authorsArr.length() > 0) {
                        (0 until authorsArr.length()).mapNotNull { idx -> cleanAuthor(authorsArr.optString(idx)) }.filter { it.isNotBlank() }.joinToString(", ")
                    } else {
                        cleanAuthor(info.optString("publisher"))
                    }
                    if (rawAuthor.isBlank()) continue

                    val lang = info.optString("language", langRestrict.ifBlank { "en" }).lowercase()
                    val imgLinks = info.optJSONObject("imageLinks")
                    val rawThumb = imgLinks?.optString("extraLarge")?.ifBlank { null }
                        ?: imgLinks?.optString("large")?.ifBlank { null }
                        ?: imgLinks?.optString("medium")?.ifBlank { null }
                        ?: imgLinks?.optString("thumbnail")?.ifBlank { null }
                        ?: imgLinks?.optString("smallThumbnail")?.ifBlank { null }
                        ?: if (volId.isNotBlank()) "https://books.google.com/books/content?id=$volId&printsec=frontcover&img=1&zoom=1" else null
                    val coverUrl = rawThumb?.replace("http://", "https://")?.replace("&edge=curl", "")

                    val isbnArr = info.optJSONArray("industryIdentifiers")
                    val isbn = if (isbnArr != null && isbnArr.length() > 0) {
                        (0 until isbnArr.length())
                            .map { isbnArr.getJSONObject(it).optString("identifier", "") }
                            .maxByOrNull { it.length }
                            ?.ifBlank { "—" } ?: "—"
                    } else "—"

                    val publisher = info.optString("publisher", if (lang == "id") "Penerbit Indonesia" else "Standard Edition")
                    val desc = info.optString("description", "").replace(Regex("<[^>]+>"), "").trim()

                    out.add(
                        CatalogBookCandidate(
                            id = "gb-$volId",
                            title = fullTitle,
                            author = rawAuthor,
                            year = info.optString("publishedDate", "2022").take(4).ifBlank { "2022" },
                            pages = info.optInt("pageCount", 320).takeIf { it > 0 } ?: 320,
                            rating = info.optDouble("averageRating", 4.80).let { if (it.isNaN() || it <= 0.0) "4.80" else String.format("%.2f", it) },
                            edition = publisher.take(24).ifBlank { "Cetakan I" },
                            publisher = publisher,
                            lang = lang,
                            isbn = isbn,
                            category = "Novel",
                            coverUrl = coverUrl,
                            coverBgHex = if (lang == "id") 0xFF1E3A5FL else 0xFF3A4D39L,
                            synopsis = desc.ifBlank { "$fullTitle — $rawAuthor ($publisher)." }
                        )
                    )
                }
            }
        }
        out
    }

    suspend fun searchOpenLibrary(query: String, isIsbn: Boolean = false): List<CatalogBookCandidate> = withContext(Dispatchers.IO) {
        val out = mutableListOf<CatalogBookCandidate>()
        runCatching {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val url = if (isIsbn) {
                "https://openlibrary.org/search.json?isbn=$encoded&limit=8&fields=key,title,subtitle,author_name,first_publish_year,number_of_pages_median,isbn,cover_i,publisher,first_sentence"
            } else {
                "https://openlibrary.org/search.json?q=$encoded&limit=12&fields=key,title,subtitle,author_name,first_publish_year,number_of_pages_median,isbn,cover_i,publisher,first_sentence"
            }
            val conn = openConnection(url)
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val docs = JSONObject(body).optJSONArray("docs") ?: return@runCatching
                for (i in 0 until docs.length()) {
                    val d = docs.getJSONObject(i)
                    val title = d.optString("title", "").trim()
                    if (title.isBlank()) continue
                    val authorArr = d.optJSONArray("author_name")
                    val author = if (authorArr != null && authorArr.length() > 0) cleanAuthor(authorArr.optString(0)) else ""
                    if (author.isBlank()) continue

                    val year = d.optInt("first_publish_year", 2020).takeIf { it > 1400 }?.toString() ?: "2020"
                    val coverId = d.optInt("cover_i", -1)
                    val isbnArr = d.optJSONArray("isbn")
                    val isbn = if (isbnArr != null && isbnArr.length() > 0) isbnArr.optString(0) else ""
                    val coverUrl = when {
                        coverId > 0 -> "https://covers.openlibrary.org/b/id/$coverId-L.jpg?default=false"
                        isbn.isNotBlank() -> "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg?default=false"
                        else -> null
                    }
                    val pubArr = d.optJSONArray("publisher")
                    val pub = if (pubArr != null && pubArr.length() > 0) pubArr.optString(0) else "Open Library"
                    val sentArr = d.optJSONArray("first_sentence")
                    val firstSent = if (sentArr != null && sentArr.length() > 0) sentArr.optString(0) else d.optString("first_sentence", "")

                    out.add(
                        CatalogBookCandidate(
                            id = "ol-$i-$coverId-$isbn",
                            title = title,
                            author = author,
                            year = year,
                            pages = d.optInt("number_of_pages_median", 312).takeIf { it > 0 } ?: 312,
                            rating = "4.75",
                            edition = pub.take(22).ifBlank { "First Edition" },
                            publisher = pub,
                            lang = "en",
                            isbn = isbn.ifBlank { "—" },
                            category = "Novel",
                            coverUrl = coverUrl,
                            coverBgHex = 0xFF2D4A43L,
                            synopsis = firstSent.ifBlank { "$title — $author ($pub)." }
                        )
                    )
                }
            }
        }
        out
    }

    suspend fun searchJikanLightNovels(query: String): List<CatalogBookCandidate> = withContext(Dispatchers.IO) {
        val out = mutableListOf<CatalogBookCandidate>()
        runCatching {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val conn = openConnection("https://api.jikan.moe/v4/manga?q=$encoded&type=lightnovel&limit=10")
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val data = JSONObject(body).optJSONArray("data") ?: return@runCatching
                for (i in 0 until data.length()) {
                    val m = data.getJSONObject(i)
                    val malId = m.optInt("mal_id")
                    val authorsArr = m.optJSONArray("authors")
                    val author = if (authorsArr != null && authorsArr.length() > 0)
                        cleanAuthor(authorsArr.getJSONObject(0).optString("name", ""))
                    else "Light Novel Author"

                    val year = m.optJSONObject("published")
                        ?.optJSONObject("prop")
                        ?.optJSONObject("from")
                        ?.optInt("year", 2023)
                        ?.takeIf { it > 1900 }
                        ?.toString() ?: "2023"
                    val jpgObj = m.optJSONObject("images")?.optJSONObject("jpg")
                    val coverUrl = jpgObj?.optString("large_image_url")?.ifBlank { null } ?: jpgObj?.optString("image_url")
                    val cleanSyn = m.optString("synopsis", "").replace("[Written by MAL Rewrite]", "").trim()
                    val title = m.optString("title", query)

                    out.add(
                        CatalogBookCandidate(
                            id = "mal-$malId",
                            title = title,
                            author = author,
                            year = year,
                            pages = (m.optInt("volumes", 1).coerceAtLeast(1)) * 260,
                            rating = m.optDouble("score", 4.82).let { if (it.isNaN() || it <= 0.0) "4.82" else String.format("%.2f", it) },
                            edition = "Light Novel",
                            publisher = "MyAnimeList · LN",
                            lang = "en",
                            isbn = "MAL-$malId",
                            category = "Light Novel",
                            coverUrl = coverUrl,
                            coverBgHex = 0xFF1E293BL,
                            synopsis = cleanSyn.ifBlank { "$title — $author (Light Novel)." }
                        )
                    )
                }
            }
        }
        out
    }

    suspend fun searchGoodreadsAutocomplete(query: String): List<CatalogBookCandidate> = withContext(Dispatchers.IO) {
        val out = mutableListOf<CatalogBookCandidate>()
        runCatching {
            val encoded = URLEncoder.encode(query.trim(), "UTF-8")
            val digitsOnly = query.replace(Regex("[^0-9Xx]"), "").uppercase()
            val isIsbn = digitsOnly.length in 10..13
            val conn = openConnection("https://www.goodreads.com/book/auto_complete?format=json&q=$encoded")
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }.trim()
                if (body.startsWith("[")) {
                    val items = JSONArray(body)
                    for (i in 0 until items.length()) {
                        val b = items.optJSONObject(i) ?: continue
                        val title = b.optString("title").ifBlank { b.optString("bookTitleBare") }.trim()
                        if (title.isBlank()) continue
                        val author = cleanAuthor(b.optJSONObject("author")?.optString("name"))
                        if (author.isBlank()) continue
                        val rawImg = b.optString("imageUrl", "")
                            .replace(Regex("""\._S[XY]\d+_\."""), "._SY475_.")
                            .replace(Regex("""\._S[XY]\d+_"""), "._SY475_")
                            .replace("http://", "https://")
                            .ifBlank { null }
                        val desc = b.optJSONObject("description")?.optString("html", "")?.replace(Regex("<[^>]+>"), "")?.trim().orEmpty()
                        out.add(
                            CatalogBookCandidate(
                                id = "gr-${b.optString("bookId", i.toString())}",
                                title = title,
                                author = author,
                                year = "2022",
                                pages = b.optInt("numPages", 320).takeIf { it > 0 } ?: 320,
                                rating = b.optString("avgRating", "4.82"),
                                edition = "Goodreads",
                                publisher = "Novel Catalog",
                                lang = "id",
                                isbn = if (isIsbn) digitsOnly else "—",
                                category = "Novel",
                                coverUrl = rawImg,
                                coverBgHex = 0xFF1E3A5FL,
                                synopsis = desc.ifBlank { "$title — $author." }
                            )
                        )
                    }
                }
            }
        }
        out
    }
}
