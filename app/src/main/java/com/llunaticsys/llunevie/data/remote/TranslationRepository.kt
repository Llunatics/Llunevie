package com.llunaticsys.llunevie.data.remote

import android.content.Context
import android.util.LruCache
import com.llunaticsys.llunevie.data.local.DEFAULT_AI_TRANSLATOR_PROMPT
import com.llunaticsys.llunevie.data.local.UserPreferencesState
import com.llunaticsys.llunevie.data.parser.PdfNativeRenderer
import com.llunaticsys.llunevie.model.extractCleanPageParagraphs
import com.llunaticsys.llunevie.model.isOrphanParagraphFragment
import com.llunaticsys.llunevie.normalizeTranslationLangCode
import com.llunaticsys.llunevie.ui.components.targetLanguageDisplayName
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

class TranslationRepository private constructor() {

    private val memoryCache = LruCache<String, String>(250)
    private val pdfParagraphsCache = LruCache<String, List<String>>(100)
    private val freeTranslateMutex = Mutex()

    companion object {
        @Volatile
        private var instance: TranslationRepository? = null

        fun getInstance(): TranslationRepository {
            return instance ?: synchronized(this) {
                instance ?: TranslationRepository().also { instance = it }
            }
        }

        fun getInstance(context: Context): TranslationRepository = getInstance()

        private val PUNCT_STRIP_SET = setOf('"', '\'', '“', '”', '‘', '’', '.', ',', '!', '?', '…', ';', ':', '-', '–', '—', '(', ')')
        private val ID_LEAK_PATTERN = Regex(
            "\\b(kamu|saja|pura-pura|harus|ambil|sesuatu|mendorongku|mereka|tidak|dengan|untuk|dalam|adalah|sebagai|karena|ketika|seperti|tetapi|namun|sudah|belum|bisa|masih)\\b",
            RegexOption.IGNORE_CASE
        )

        internal fun isInvalidOrUnchangedTranslation(sourceText: String, translatedText: String, targetLang: String = ""): Boolean {
            val src = sourceText.trim()
            val tr = translatedText.trim()
            if (tr.isEmpty()) return true
            if (tr.contains("INVALID LANGUAGE PAIR SPECIFIED", ignoreCase = true) ||
                tr.contains("MYMEMORY WARNING", ignoreCase = true) ||
                tr.contains("QUERY LENGTH LIMIT EXCEEDED", ignoreCase = true)
            ) {
                return true
            }
            // Reject leftover delimiter tokens
            val sourceHasSplitToken = src.contains("PARA_SPLIT", ignoreCase = true)
            if (!sourceHasSplitToken && (
                    tr.contains("PARA_SPLIT", ignoreCase = true) ||
                        tr.contains("PARA SPLIT", ignoreCase = true) ||
                        tr.contains("[[") || tr.contains("]]") ||
                        tr.contains("［［") || tr.contains("］］") ||
                        tr.contains("【PARA", ignoreCase = true)
                )
            ) {
                return true
            }

            // Normalize quotes, punctuation, and whitespace when comparing src and tr
            val normSrc = src.filterNot { it in PUNCT_STRIP_SET }.replace(Regex("\\s+"), " ").trim()
            val normTr = tr.filterNot { it in PUNCT_STRIP_SET }.replace(Regex("\\s+"), " ").trim()
            if (normSrc.length >= 2 && normSrc.any { it.isLetter() } && normTr.equals(normSrc, ignoreCase = true)) {
                return true
            }
            if (src.length >= 10 && src.any { it.isLetter() } && tr.equals(src, ignoreCase = true)) {
                return true
            }

            // Reject truncated stubs
            if (src.length >= 15 && tr.length <= 4) {
                return true
            }
            if (src.length >= 80 && tr.length < (src.length * 0.12).toInt()) {
                return true
            }

            // Indonesian stopword leakage check
            if (!sourceHasSplitToken && src.length >= 20 && targetLang.isNotBlank()) {
                val normTarget = normalizeTranslationLangCode(targetLang)
                if (normTarget !in setOf("id", "ms")) {
                    val idHits = ID_LEAK_PATTERN.findAll(tr).count()
                    if (idHits >= 2) {
                        return true
                    }
                }
            }

            // Target-Script Verification when targetLang is provided
            if (targetLang.isNotBlank() && !sourceHasSplitToken) {
                val normTarget = normalizeTranslationLangCode(targetLang)
                if (src.count { it.isLetter() } >= 2) {
                    when {
                        normTarget in setOf("zh-CN", "zh-TW", "ja") -> {
                            if (tr.none { c -> c.code in 0x3040..0x30FF || c.code in 0x3400..0x4DBF || c.code in 0x4E00..0x9FFF || c.code in 0xF900..0xFAFF }) {
                                return true
                            }
                        }
                        normTarget == "ko" -> {
                            if (tr.none { c -> c.code in 0xAC00..0xD7AF || c.code in 0x1100..0x11FF || c.code in 0x3130..0x318F || c.code in 0x4E00..0x9FFF }) {
                                return true
                            }
                        }
                        normTarget == "ru" -> {
                            if (tr.none { c -> c.code in 0x0400..0x04FF }) {
                                return true
                            }
                        }
                        normTarget == "ar" -> {
                            if (tr.none { c -> c.code in 0x0600..0x06FF }) {
                                return true
                            }
                        }
                        normTarget == "th" -> {
                            if (tr.none { c -> c.code in 0x0E00..0x0E7F }) {
                                return true
                            }
                        }
                    }
                }
            }

            return false
        }

        internal fun guessSourceLangForFallback(text: String, targetLang: String): String {
            val normTarget = normalizeTranslationLangCode(targetLang)
            val lower = text.lowercase()
            val idPattern = Regex("\\b(yang|dan|di|ke|dari|ini|itu|aku|saya|kamu|kau|dia|ia|mereka|kami|kita|tidak|tak|bukan|belum|sudah|telah|sedang|akan|bisa|dapat|harus|mau|ingin|dengan|untuk|dalam|pada|adalah|sebagai|oleh|karena|kalau|jika|tapi|tetapi|namun|atau|saja|lagi|masih|juga|pun|lah|nya|ibu|bapak|pak|bu|mas|mbak|kak|anak|orang|rumah|kata|bilang|lihat|ambil|pergi|datang|tahu|jadi|sama|ada|kok|sih)\\b")
            val enPattern = Regex("\\b(the|and|of|to|in|is|for|that|with|on|as|are|this|by|from|they|have|had|was|were|would|could|should)\\b")
            val idHits = idPattern.findAll(lower).count()
            val enHits = enPattern.findAll(lower).count()
            val guessed = when {
                idHits > enHits && idHits >= 1 -> "id"
                enHits > idHits && enHits >= 1 -> "en"
                normTarget == "en" -> "id"
                normTarget != "id" && enHits == 0 && (normTarget.startsWith("zh") || normTarget in setOf("ja", "ko", "vi", "th", "ru", "fr", "de", "es", "it", "pt", "ar")) -> "id"
                else -> "en"
            }
            return if (guessed == normTarget) {
                if (normTarget == "en") "id" else "en"
            } else {
                guessed
            }
        }

        internal fun splitTextSafelyByLength(text: String, maxLen: Int): List<String> {
            if (text.length <= maxLen || maxLen <= 0) return listOf(text)
            val chunks = mutableListOf<String>()
            var remaining = text
            while (remaining.isNotEmpty()) {
                if (remaining.length <= maxLen) {
                    chunks.add(remaining)
                    break
                }
                val window = remaining.take(maxLen)
                // 1. Break at last paragraph boundary
                val lastPara = window.lastIndexOf('\n')
                val splitPoint = if (lastPara > 0) {
                    lastPara + 1
                } else {
                    // 2. Break at sentence boundary: terminal punctuation followed by space
                    val sentenceBreak = Regex("""[.!?…][)\]"”’']?\s+""").findAll(window)
                        .map { it.range.last + 1 }
                        .lastOrNull { it > 0 }
                    if (sentenceBreak != null && sentenceBreak > 0) {
                        sentenceBreak
                    } else {
                        // 3. Break at last whitespace
                        val lastSpace = window.indexOfLast { it.isWhitespace() }
                        if (lastSpace > 0) {
                            lastSpace + 1
                        } else {
                            // 4. Hard slice if single unbroken token has no whitespace
                            maxLen
                        }
                    }
                }
                val chunk = remaining.substring(0, splitPoint).trim()
                if (chunk.isNotEmpty()) {
                    chunks.add(chunk)
                }
                remaining = remaining.substring(splitPoint).trimStart()
            }
            return if (chunks.isEmpty()) listOf(text) else chunks
        }
    }

    private fun getDiskCacheDir(context: Context): File {
        return File(context.filesDir, "translation_cache").apply {
            if (!exists()) mkdirs()
        }
    }

    private fun computeCacheKey(
        engine: String,
        model: String,
        targetLang: String,
        systemPrompt: String,
        sourceText: String
    ): String {
        val modelPart = if (engine == "free") "gtx" else model.trim()
        val raw = "v3|$engine|$modelPart|$targetLang|${systemPrompt.hashCode()}|${sourceText.trim()}"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun getFromCache(
        context: Context,
        engine: String,
        model: String,
        targetLang: String,
        systemPrompt: String,
        sourceText: String
    ): String? {
        val key = computeCacheKey(engine, model, targetLang, systemPrompt, sourceText)
        val ramVal = memoryCache.get(key)
        if (ramVal != null) {
            if (!isInvalidOrUnchangedTranslation(sourceText, ramVal, targetLang)) {
                return ramVal
            } else {
                memoryCache.remove(key)
            }
        }

        val diskFile = File(getDiskCacheDir(context), "$key.txt")
        if (diskFile.exists() && diskFile.isFile) {
            val content = runCatching { diskFile.readText(Charsets.UTF_8) }.getOrNull()
            if (!content.isNullOrBlank()) {
                if (!isInvalidOrUnchangedTranslation(sourceText, content, targetLang)) {
                    memoryCache.put(key, content)
                    return content
                } else {
                    runCatching { diskFile.delete() }
                }
            }
        }
        return null
    }

    fun putInCache(
        context: Context,
        engine: String,
        model: String,
        targetLang: String,
        systemPrompt: String,
        sourceText: String,
        translatedText: String
    ) {
        if (isInvalidOrUnchangedTranslation(sourceText, translatedText, targetLang)) return
        val key = computeCacheKey(engine, model, targetLang, systemPrompt, sourceText)
        memoryCache.put(key, translatedText)
        runCatching {
            val file = File(getDiskCacheDir(context), "$key.txt")
            file.writeText(translatedText, Charsets.UTF_8)
        }
    }

    fun getCacheStats(context: Context): Pair<Int, Long> {
        val dir = File(context.filesDir, "translation_cache")
        if (!dir.exists() || !dir.isDirectory) return Pair(0, 0L)
        val files = dir.listFiles() ?: return Pair(0, 0L)
        val count = files.size
        val bytes = files.sumOf { it.length() }
        return Pair(count, bytes)
    }

    suspend fun clearCache(context: Context) = withContext(Dispatchers.IO) {
        memoryCache.evictAll()
        val dir = File(context.filesDir, "translation_cache")
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    suspend fun translate(
        text: String,
        prefs: UserPreferencesState,
        context: Context,
        explicitTargetLang: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return@withContext Result.success("")

        val engine = prefs.translatorEngine.ifBlank { "free" }
        val model = prefs.aiModelName.ifBlank { "gemini-2.5-flash" }
        val targetLang = normalizeTranslationLangCode(explicitTargetLang ?: prefs.translatorTargetLang.ifBlank { "id" })
        val systemPrompt = prefs.aiSystemPrompt.ifBlank { DEFAULT_AI_TRANSLATOR_PROMPT }

        val cached = getFromCache(context, engine, model, targetLang, systemPrompt, cleanText)
        if (cached != null) {
            return@withContext Result.success(cached)
        }

        val primaryResult = when (engine) {
            "gemini" -> translateGemini(cleanText, prefs, targetLang)
            "openrouter" -> translateOpenRouter(cleanText, prefs, targetLang)
            "custom" -> translateCustom(cleanText, prefs, targetLang)
            else -> translateFree(cleanText, targetLang)
        }

        val result = if (primaryResult.isSuccess && !isInvalidOrUnchangedTranslation(cleanText, primaryResult.getOrNull().orEmpty(), targetLang)) {
            primaryResult
        } else if (engine != "free") {
            val fallbackFree = translateFree(cleanText, targetLang)
            if (fallbackFree.isSuccess && !isInvalidOrUnchangedTranslation(cleanText, fallbackFree.getOrNull().orEmpty(), targetLang)) {
                fallbackFree
            } else {
                primaryResult
            }
        } else {
            primaryResult
        }

        result.onSuccess { translated ->
            if (!isInvalidOrUnchangedTranslation(cleanText, translated, targetLang)) {
                putInCache(context, engine, model, targetLang, systemPrompt, cleanText, translated)
            }
        }
        result
    }

    private suspend inline fun <T> withCancellableConn(conn: HttpURLConnection, block: (HttpURLConnection) -> T): T {
        val handle = kotlinx.coroutines.currentCoroutineContext()[kotlinx.coroutines.Job]?.invokeOnCompletion { cause ->
            if (cause is kotlinx.coroutines.CancellationException) {
                runCatching { conn.disconnect() }
            }
        }
        return try {
            block(conn)
        } finally {
            handle?.dispose()
            runCatching { conn.disconnect() }
        }
    }

    private suspend fun translateFree(text: String, targetLang: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return@withContext Result.success("")

        if (cleanText.length > 900) {
            translateFreeChunked(cleanText, targetLang)
        } else {
            translateFreeSingleChunk(cleanText, targetLang)
        }
    }

    private suspend fun translateFreeChunked(text: String, targetLang: String): Result<String> {
        val chunks = splitTextSafelyByLength(text, 900)
        val results = mutableListOf<String>()
        for (chunk in chunks) {
            var chunkRes = translateFreeSingleChunk(chunk, targetLang)
            if (!chunkRes.isSuccess) {
                delay(150L)
                chunkRes = translateFreeSingleChunk(chunk, targetLang)
            }
            if (chunkRes.isSuccess) {
                results.add(chunkRes.getOrNull().orEmpty())
            } else {
                return Result.failure(chunkRes.exceptionOrNull() ?: Exception("Chunk translation failed"))
            }
        }
        return Result.success(results.joinToString("\n"))
    }

    private suspend fun requestGoogleGtx(chunk: String, sourceLang: String, targetLang: String): Result<String> = runCatching {
        val encodedText = URLEncoder.encode(chunk, "UTF-8")
        val encodedSource = URLEncoder.encode(sourceLang, "UTF-8")
        val encodedTarget = URLEncoder.encode(targetLang, "UTF-8")
        val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=$encodedSource&tl=$encodedTarget&dt=t&q=$encodedText")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0 Mobile Safari/537.36")
            setRequestProperty("Accept", "*/*")
        }
        withCancellableConn(conn) {
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val rootArr = runCatching { JSONArray(body) }.getOrNull()
                val segments = rootArr?.optJSONArray(0)
                if (segments != null && segments.length() > 0) {
                    val sb = StringBuilder()
                    for (i in 0 until segments.length()) {
                        val seg = segments.optJSONArray(i)
                        if (seg != null) {
                            val part = seg.optString(0, "")
                            if (part.isNotEmpty()) sb.append(part)
                        }
                    }
                    val full = sb.toString().trim()
                    if (full.isNotEmpty() && !isInvalidOrUnchangedTranslation(chunk, full, targetLang)) {
                        return@withCancellableConn full
                    }
                }
            }
            error("GTX translation empty or returned code ${conn.responseCode}")
        }
    }

    private suspend fun translateFreeSingleChunk(text: String, targetLang: String): Result<String> = freeTranslateMutex.withLock {
        val clean = text.trim()
        if (clean.isEmpty()) return@withLock Result.success("")
        val chunkToTranslate = clean

        // 1. Primary: Google GTX with sl=auto
        val gtxAuto = requestGoogleGtx(chunkToTranslate, "auto", targetLang)
        if (gtxAuto.isSuccess) {
            return@withLock gtxAuto
        }

        // 2. Retry with explicit source language if auto failed
        val guessedSrc = guessSourceLangForFallback(chunkToTranslate, targetLang)
        val gtxExplicit = if (guessedSrc.isNotBlank() && guessedSrc != "auto") {
            requestGoogleGtx(chunkToTranslate, guessedSrc, targetLang)
        } else {
            gtxAuto
        }
        if (gtxExplicit.isSuccess) {
            return@withLock gtxExplicit
        }

        // If the input contains PARA_SPLIT (a multi-paragraph batch) and Google GTX fails,
        // do NOT pass the [[PARA_SPLIT]] block to MyMemory; fail immediately so caller translates individually
        if (chunkToTranslate.contains("PARA_SPLIT", ignoreCase = true)) {
            return@withLock Result.failure(gtxExplicit.exceptionOrNull() ?: gtxAuto.exceptionOrNull() ?: Exception("GTX batch translation failed; skipping MyMemory for batch"))
        }

        // Fallback: MyMemory (requires explicit source language ISO code, never 'autodetect')
        val subPieces = if (chunkToTranslate.length > 450) {
            splitTextSafelyByLength(chunkToTranslate, 450)
        } else {
            listOf(chunkToTranslate)
        }

        val translatedPieces = mutableListOf<String>()
        for (piece in subPieces) {
            val pRes = translateMyMemorySinglePiece(piece, targetLang)
            if (pRes.isSuccess) {
                translatedPieces.add(pRes.getOrNull().orEmpty())
            } else {
                return@withLock Result.failure(pRes.exceptionOrNull() ?: gtxExplicit.exceptionOrNull() ?: gtxAuto.exceptionOrNull() ?: Exception("MyMemory fallback failed"))
            }
        }
        val fullMyMemory = translatedPieces.joinToString(" ").trim()
        if (fullMyMemory.isNotEmpty() && !isInvalidOrUnchangedTranslation(chunkToTranslate, fullMyMemory, targetLang)) {
            Result.success(fullMyMemory)
        } else {
            Result.failure(gtxExplicit.exceptionOrNull() ?: gtxAuto.exceptionOrNull() ?: Exception("Translation failed"))
        }
    }

    private suspend fun translateMyMemorySinglePiece(piece: String, targetLang: String): Result<String> = runCatching {
        val srcLang = guessSourceLangForFallback(piece, targetLang)
        val encodedQ = URLEncoder.encode(piece, "UTF-8")
        val encodedLang = URLEncoder.encode(targetLang, "UTF-8")
        val url = URL("https://api.mymemory.translated.net/get?q=$encodedQ&langpair=$srcLang|$encodedLang")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "Mozilla/5.0")
        }
        withCancellableConn(conn) {
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val json = runCatching { JSONObject(body) }.getOrNull()
                val status = json?.optInt("responseStatus", 200) ?: 200
                val trans = json?.optJSONObject("responseData")?.optString("translatedText")?.trim()
                if (status in 200..299 && !trans.isNullOrEmpty() && !isInvalidOrUnchangedTranslation(piece, trans, targetLang)) {
                    return@withCancellableConn trans
                }
            }
            error("MyMemory returned code ${conn.responseCode} or invalid response")
        }
    }

    private suspend fun translateGemini(text: String, prefs: UserPreferencesState, targetLang: String): Result<String> {
        val apiKey = prefs.aiApiKey.trim()
        if (apiKey.isBlank()) {
            val msg = if (prefs.lang == "en") {
                "Please set your Gemini API Key in Settings → Translation & AI."
            } else {
                "Masukkan API Key Gemini terlebih dahulu di Pengaturan → Terjemahan & AI."
            }
            return Result.failure(IllegalStateException(msg))
        }

        val model = prefs.aiModelName.ifBlank { "gemini-2.5-flash" }.trim()
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent"

        return runCatching {
            val isEn = prefs.lang == "en"
            val resolvedPrompt = prefs.aiSystemPrompt.ifBlank { DEFAULT_AI_TRANSLATOR_PROMPT }
                .replace("{{TARGET_LANG}}", targetLanguageDisplayName(targetLang, isEn))

            val payload = JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", resolvedPrompt)))
                })
                put("contents", JSONArray().put(JSONObject().apply {
                    put("role", "user")
                    put("parts", JSONArray().put(JSONObject().put("text", text)))
                }))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.3)
                })
            }

            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 25000
                readTimeout = 25000
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", apiKey)
                doOutput = true
            }

            withCancellableConn(conn) {
                conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }

                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val json = JSONObject(body)
                    val candidates = json.optJSONArray("candidates")
                    val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    val resultText = parts?.optJSONObject(0)?.optString("text")?.trim()
                    if (!resultText.isNullOrEmpty()) {
                        return@withCancellableConn resultText
                    }
                    error("Gemini returned empty response")
                } else {
                    val errBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    val cleanError = runCatching {
                        JSONObject(errBody).optJSONObject("error")?.optString("message")
                    }.getOrNull()?.ifBlank { null } ?: "HTTP ${conn.responseCode}: $errBody"
                    error(cleanError)
                }
            }
        }
    }

    private suspend fun translateOpenRouter(text: String, prefs: UserPreferencesState, targetLang: String): Result<String> {
        val apiKey = prefs.aiApiKey.trim()
        if (apiKey.isBlank()) {
            val msg = if (prefs.lang == "en") {
                "Please set your OpenRouter API Key in Settings → Translation & AI."
            } else {
                "Masukkan API Key OpenRouter terlebih dahulu di Pengaturan → Terjemahan & AI."
            }
            return Result.failure(IllegalStateException(msg))
        }

        val endpoint = "https://openrouter.ai/api/v1/chat/completions"
        val model = prefs.aiModelName.ifBlank { "google/gemini-2.5-flash" }.trim()
        return executeOpenAiChatCompletion(endpoint, model, text, prefs, targetLang)
    }

    private suspend fun translateCustom(text: String, prefs: UserPreferencesState, targetLang: String): Result<String> {
        val baseUrl = prefs.aiCustomBaseUrl.ifBlank { "https://openrouter.ai/api/v1" }.trimEnd('/')
        val endpoint = "$baseUrl/chat/completions"
        val model = prefs.aiModelName.ifBlank { "gpt-4o-mini" }.trim()
        return executeOpenAiChatCompletion(endpoint, model, text, prefs, targetLang)
    }

    private suspend fun executeOpenAiChatCompletion(
        endpoint: String,
        model: String,
        text: String,
        prefs: UserPreferencesState,
        targetLang: String
    ): Result<String> {
        return runCatching {
            val isEn = prefs.lang == "en"
            val resolvedPrompt = prefs.aiSystemPrompt.ifBlank { DEFAULT_AI_TRANSLATOR_PROMPT }
                .replace("{{TARGET_LANG}}", targetLanguageDisplayName(targetLang, isEn))

            val payload = JSONObject().apply {
                put("model", model)
                put("temperature", 0.3)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", resolvedPrompt)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", text)
                    })
                })
            }

            val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 25000
                readTimeout = 25000
                setRequestProperty("Content-Type", "application/json")
                if (prefs.aiApiKey.isNotBlank()) {
                    setRequestProperty("Authorization", "Bearer ${prefs.aiApiKey.trim()}")
                }
                setRequestProperty("HTTP-Referer", "https://github.com/Llunatics/Llunevie")
                setRequestProperty("X-Title", "Llunevie")
                doOutput = true
            }

            withCancellableConn(conn) {
                conn.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(payload.toString()) }

                if (conn.responseCode in 200..299) {
                    val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val json = JSONObject(body)
                    val choices = json.optJSONArray("choices")
                    val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content")?.trim()
                    if (!content.isNullOrEmpty()) {
                        return@withCancellableConn content
                    }
                    error("AI model returned empty response")
                } else {
                    val errBody = conn.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    val cleanError = runCatching {
                        val obj = JSONObject(errBody)
                        obj.optJSONObject("error")?.optString("message") ?: obj.optString("message")
                    }.getOrNull()?.ifBlank { null } ?: "HTTP ${conn.responseCode}: $errBody"
                    error(cleanError)
                }
            }
        }
    }

    suspend fun translateParagraphList(
        paragraphs: List<String>,
        prefs: UserPreferencesState,
        context: Context,
        explicitTargetLang: String? = null
    ): Result<List<Pair<String, String?>>> = withContext(Dispatchers.IO) {
        val filteredParas = if (paragraphs.size > 1) {
            paragraphs.filterNot { isOrphanParagraphFragment(it) }
        } else {
            paragraphs
        }
        val rawParagraphs = filteredParas.map { it.trim() }.filter { it.isNotBlank() }
        if (rawParagraphs.isEmpty()) {
            return@withContext Result.success(emptyList())
        }

        val engine = prefs.translatorEngine.ifBlank { "free" }
        val model = prefs.aiModelName.ifBlank { "gemini-2.5-flash" }
        val targetLang = normalizeTranslationLangCode(explicitTargetLang ?: prefs.translatorTargetLang.ifBlank { "id" })
        val systemPrompt = prefs.aiSystemPrompt.ifBlank { DEFAULT_AI_TRANSLATOR_PROMPT }

        val uncachedIndices = mutableListOf<Int>()
        val resultList = mutableListOf<Pair<String, String?>>()

        for ((idx, para) in rawParagraphs.withIndex()) {
            if (para.startsWith("[ILLUST:") || para.startsWith("[PDF_PAGE:")) {
                resultList.add(Pair(para, null))
            } else {
                val cached = getFromCache(context, engine, model, targetLang, systemPrompt, para)
                if (cached != null) {
                    resultList.add(Pair(para, cached))
                } else {
                    resultList.add(Pair(para, null))
                    uncachedIndices.add(idx)
                }
            }
        }

        if (uncachedIndices.isEmpty()) {
            return@withContext Result.success(resultList)
        }

        // For uncached paragraphs: translate in safe batches (NEVER fall back to source text on failure)
        val uncachedTexts = uncachedIndices.map { rawParagraphs[it] }
        val paraSplitRegex = Regex("\\s*(?:[\\[\\uFF3B\\u3010]+\\s*PARA[_\\s\\uFF3F-]*SPLIT\\s*[\\]\\uFF3D\\u3011]+)\\s*", RegexOption.IGNORE_CASE)

        val batches = mutableListOf<MutableList<String>>()
        var curBatch = mutableListOf<String>()
        var curBatchLen = 0
        for (uText in uncachedTexts) {
            val needed = uText.length + 20
            if (curBatch.isNotEmpty() && (curBatchLen + needed > 1000)) {
                batches.add(curBatch)
                curBatch = mutableListOf()
                curBatchLen = 0
            }
            curBatch.add(uText)
            curBatchLen += needed
        }
        if (curBatch.isNotEmpty()) {
            batches.add(curBatch)
        }

        val translatedUncached = batches.flatMap { batch ->
            if (batch.size == 1) {
                val text = batch[0]
                val tr = runCatching { translate(text, prefs, context, targetLang).getOrNull()?.trim() }.getOrNull()
                listOf(if (!tr.isNullOrBlank() && !isInvalidOrUnchangedTranslation(text, tr, targetLang)) tr else null)
            } else {
                val joined = batch.joinToString("\n[[PARA_SPLIT]]\n")
                val trResult = runCatching { translate(joined, prefs, context, targetLang) }.getOrElse { Result.failure(it) }
                if (trResult.isSuccess) {
                    val translatedBlock = trResult.getOrNull().orEmpty()
                    var splits = translatedBlock.split(paraSplitRegex).map { it.trim() }
                    if (splits.size != batch.size) {
                        val fallbackSplits = translatedBlock.split(Regex("\n\\s*\n")).map { it.trim() }
                        if (fallbackSplits.size == batch.size) {
                            splits = fallbackSplits
                        }
                    }
                    if (splits.size == batch.size) {
                        splits.mapIndexed { i, splitText ->
                            val cleanedSplit = splitText.replace(paraSplitRegex, "").trim()
                            if (cleanedSplit.isNotBlank() && !isInvalidOrUnchangedTranslation(batch[i], cleanedSplit, targetLang)) {
                                putInCache(context, engine, model, targetLang, systemPrompt, batch[i], cleanedSplit)
                                cleanedSplit
                            } else {
                                val single = runCatching { translate(batch[i], prefs, context, targetLang).getOrNull()?.trim() }.getOrNull()
                                if (!single.isNullOrBlank() && !isInvalidOrUnchangedTranslation(batch[i], single, targetLang)) {
                                    putInCache(context, engine, model, targetLang, systemPrompt, batch[i], single)
                                    single
                                } else {
                                    null
                                }
                            }
                        }
                    } else {
                        batch.map { text ->
                            val single = runCatching { translate(text, prefs, context, targetLang).getOrNull()?.trim() }.getOrNull()
                            if (!single.isNullOrBlank() && !isInvalidOrUnchangedTranslation(text, single, targetLang)) {
                                putInCache(context, engine, model, targetLang, systemPrompt, text, single)
                                single
                            } else {
                                null
                            }
                        }
                    }
                } else {
                    batch.map { text ->
                        val single = runCatching { translate(text, prefs, context, targetLang).getOrNull()?.trim() }.getOrNull()
                        if (!single.isNullOrBlank() && !isInvalidOrUnchangedTranslation(text, single, targetLang)) {
                            putInCache(context, engine, model, targetLang, systemPrompt, text, single)
                            single
                        } else {
                            null
                        }
                    }
                }
            }
        }

        // Merge translated paragraphs back into resultList
        uncachedIndices.forEachIndexed { i, originalIdx ->
            val srcPara = rawParagraphs[originalIdx]
            val transText = translatedUncached.getOrNull(i)?.trim()
            if (!transText.isNullOrBlank() && !isInvalidOrUnchangedTranslation(srcPara, transText, targetLang)) {
                resultList[originalIdx] = Pair(srcPara, transText)
                putInCache(context, engine, model, targetLang, systemPrompt, srcPara, transText)
            }
        }

        // Retry translating any missing individual paragraph once
        for (idx in resultList.indices) {
            val (orig, trans) = resultList[idx]
            if (trans.isNullOrBlank() && !orig.startsWith("[ILLUST:") && !orig.startsWith("[PDF_PAGE:")) {
                delay(100L)
                val retryRes = runCatching { translate(orig, prefs, context, targetLang).getOrNull()?.trim() }.getOrNull()
                if (!retryRes.isNullOrBlank() && !isInvalidOrUnchangedTranslation(orig, retryRes, targetLang)) {
                    resultList[idx] = Pair(orig, retryRes)
                    putInCache(context, engine, model, targetLang, systemPrompt, orig, retryRes)
                }
            }
        }

        val hasTranslatableParagraphs = rawParagraphs.any { !it.startsWith("[ILLUST:") && !it.startsWith("[PDF_PAGE:") }
        val hasAnyValidTranslation = resultList.any { (orig, trans) ->
            !orig.startsWith("[ILLUST:") && !orig.startsWith("[PDF_PAGE:") &&
                !trans.isNullOrBlank() && !isInvalidOrUnchangedTranslation(orig, trans, targetLang)
        }
        if (hasTranslatableParagraphs && !hasAnyValidTranslation) {
            return@withContext Result.failure(Exception("No valid translation produced for page"))
        }

        Result.success(resultList)
    }

    suspend fun translatePageParagraphs(
        pageRaw: String,
        prefs: UserPreferencesState,
        context: Context,
        explicitTargetLang: String? = null
    ): Result<List<Pair<String, String?>>> {
        val rawParagraphs = extractCleanPageParagraphs(pageRaw)
        return translateParagraphList(rawParagraphs, prefs, context, explicitTargetLang)
    }

    suspend fun extractPdfPageParagraphBlocks(
        context: Context,
        pdfFilePath: String,
        pageIndexZero: Int,
        bookTitle: String = ""
    ): List<PdfNativeRenderer.PdfParagraphBlock> = withContext(Dispatchers.IO) {
        PdfNativeRenderer.extractPdfPageParagraphBlocks(context, pdfFilePath, pageIndexZero, bookTitle)
    }

    suspend fun extractPdfPageParagraphs(
        context: Context,
        pdfFilePath: String,
        pageIndexZero: Int,
        bookTitle: String = ""
    ): List<String> = withContext(Dispatchers.IO) {
        val cacheKey = "$pdfFilePath:$pageIndexZero"
        val cached = pdfParagraphsCache.get(cacheKey)
        if (cached != null) return@withContext cached

        val blocks = extractPdfPageParagraphBlocks(context, pdfFilePath, pageIndexZero, bookTitle)
        val paras = blocks.map { it.text }
        pdfParagraphsCache.put(cacheKey, paras)
        paras
    }
}
