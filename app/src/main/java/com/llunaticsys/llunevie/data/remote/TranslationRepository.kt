package com.llunaticsys.llunevie.data.remote

import android.content.Context
import android.util.LruCache
import com.llunaticsys.llunevie.data.local.DEFAULT_AI_TRANSLATOR_PROMPT
import com.llunaticsys.llunevie.data.local.UserPreferencesState
import com.llunaticsys.llunevie.data.parser.PdfNativeRenderer
import com.llunaticsys.llunevie.normalizeTranslationLangCode
import com.llunaticsys.llunevie.ui.components.targetLanguageDisplayName
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
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

    companion object {
        @Volatile
        private var instance: TranslationRepository? = null

        fun getInstance(): TranslationRepository {
            return instance ?: synchronized(this) {
                instance ?: TranslationRepository().also { instance = it }
            }
        }

        fun getInstance(context: Context): TranslationRepository = getInstance()
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
        val raw = "$engine|$modelPart|$targetLang|${systemPrompt.hashCode()}|${sourceText.trim()}"
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(raw.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun isInvalidOrUnchangedTranslation(sourceText: String, translatedText: String): Boolean {
        val src = sourceText.trim()
        val tr = translatedText.trim()
        if (tr.isEmpty()) return true
        if (tr.contains("INVALID LANGUAGE PAIR SPECIFIED", ignoreCase = true) ||
            tr.contains("MYMEMORY WARNING", ignoreCase = true) ||
            tr.contains("QUERY LENGTH LIMIT EXCEEDED", ignoreCase = true)
        ) {
            return true
        }
        if (src.length >= 10 && src.any { it.isLetter() } && tr.equals(src, ignoreCase = true)) {
            return true
        }
        return false
    }

    private fun guessSourceLangForFallback(text: String, targetLang: String): String {
        val normTarget = normalizeTranslationLangCode(targetLang)
        val lower = text.lowercase()
        val idPattern = Regex("\\b(yang|dan|di|ini|itu|aku|tidak|dengan|untuk|dari|dalam|pada|adalah|sebagai|oleh|karena|sebuah|seorang|mereka|dia|kami|kita|bisa|akan|sudah)\\b")
        val enPattern = Regex("\\b(the|and|of|to|in|is|for|that|with|on|as|are|this|by|from|they|have|had|was|were|would|could|should)\\b")
        val idHits = idPattern.findAll(lower).count()
        val enHits = enPattern.findAll(lower).count()
        val guessed = when {
            idHits > enHits && idHits >= 1 -> "id"
            enHits > idHits && enHits >= 1 -> "en"
            normTarget == "en" -> "id"
            else -> "en"
        }
        return if (guessed == normTarget) {
            if (normTarget == "en") "id" else "en"
        } else {
            guessed
        }
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
            if (!isInvalidOrUnchangedTranslation(sourceText, ramVal)) {
                return ramVal
            } else {
                memoryCache.remove(key)
            }
        }

        val diskFile = File(getDiskCacheDir(context), "$key.txt")
        if (diskFile.exists() && diskFile.isFile) {
            val content = runCatching { diskFile.readText(Charsets.UTF_8) }.getOrNull()
            if (!content.isNullOrBlank()) {
                if (!isInvalidOrUnchangedTranslation(sourceText, content)) {
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
        if (isInvalidOrUnchangedTranslation(sourceText, translatedText)) return
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

        val result = if (primaryResult.isSuccess && !isInvalidOrUnchangedTranslation(cleanText, primaryResult.getOrNull().orEmpty())) {
            primaryResult
        } else if (engine != "free") {
            val fallbackFree = translateFree(cleanText, targetLang)
            if (fallbackFree.isSuccess && !isInvalidOrUnchangedTranslation(cleanText, fallbackFree.getOrNull().orEmpty())) {
                fallbackFree
            } else {
                primaryResult
            }
        } else {
            primaryResult
        }

        result.onSuccess { translated ->
            if (!isInvalidOrUnchangedTranslation(cleanText, translated)) {
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

    private suspend fun translateFree(text: String, targetLang: String): Result<String> {
        // Primary: Google GTX
        val gtxResult = runCatching {
            val encodedText = URLEncoder.encode(text, "UTF-8")
            val encodedLang = URLEncoder.encode(targetLang, "UTF-8")
            val url = URL("https://translate.googleapis.com/translate_a/single?client=gtx&sl=auto&tl=$encodedLang&dt=t&q=$encodedText")
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
                    val rootArr = JSONArray(body)
                    val segments = rootArr.optJSONArray(0)
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
                        if (full.isNotEmpty() && !isInvalidOrUnchangedTranslation(text, full)) {
                            return@withCancellableConn full
                        }
                    }
                }
                error("GTX translation empty or returned code ${conn.responseCode}")
            }
        }

        if (gtxResult.isSuccess) {
            return gtxResult
        }

        // Fallback: MyMemory (requires explicit source language ISO code, never 'autodetect')
        return runCatching {
            val truncated = text.take(480)
            val srcLang = guessSourceLangForFallback(truncated, targetLang)
            val encodedQ = URLEncoder.encode(truncated, "UTF-8")
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
                    val json = JSONObject(body)
                    val status = json.optInt("responseStatus", 200)
                    val trans = json.optJSONObject("responseData")?.optString("translatedText")?.trim()
                    if (status in 200..299 && !trans.isNullOrEmpty() && !isInvalidOrUnchangedTranslation(truncated, trans)) {
                        return@withCancellableConn trans
                    }
                }
                error(gtxResult.exceptionOrNull()?.message ?: "Translation failed")
            }
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
        val rawParagraphs = paragraphs.map { it.trim() }.filter { it.isNotBlank() }
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

        // For uncached paragraphs: translate them (NEVER fall back to source text on failure)
        val uncachedTexts = uncachedIndices.map { rawParagraphs[it] }
        val paraSplitRegex = Regex("\\s*(?:[\\[\\uFF3B\\u3010]+\\s*PARA[_\\s\\uFF3F-]*SPLIT\\s*[\\]\\uFF3D\\u3011]+)\\s*", RegexOption.IGNORE_CASE)

        val translatedUncached: List<String?> = if (uncachedTexts.size == 1) {
            uncachedTexts.map { text ->
                val tr = translate(text, prefs, context, targetLang).getOrNull()?.trim()
                if (!tr.isNullOrBlank() && !isInvalidOrUnchangedTranslation(text, tr)) tr else null
            }
        } else {
            // Join with [[PARA_SPLIT]] for coherent page context in 1 single network request!
            val joined = uncachedTexts.joinToString("\n[[PARA_SPLIT]]\n")
            val trResult = translate(joined, prefs, context, targetLang)
            if (trResult.isSuccess) {
                val translatedBlock = trResult.getOrNull().orEmpty()
                var splits = translatedBlock.split(paraSplitRegex).map { it.trim() }
                if (splits.size != uncachedTexts.size) {
                    val fallbackSplits = translatedBlock.split(Regex("\n\\s*\n")).map { it.trim() }
                    if (fallbackSplits.size == uncachedTexts.size) {
                        splits = fallbackSplits
                    }
                }
                if (splits.size == uncachedTexts.size) {
                    splits.mapIndexed { i, splitText ->
                        val cleanedSplit = splitText.replace(paraSplitRegex, "").trim()
                        if (cleanedSplit.isNotBlank() && !isInvalidOrUnchangedTranslation(uncachedTexts[i], cleanedSplit)) {
                            putInCache(context, engine, model, targetLang, systemPrompt, uncachedTexts[i], cleanedSplit)
                            cleanedSplit
                        } else {
                            val single = translate(uncachedTexts[i], prefs, context, targetLang).getOrNull()?.trim()
                            if (!single.isNullOrBlank() && !isInvalidOrUnchangedTranslation(uncachedTexts[i], single)) {
                                putInCache(context, engine, model, targetLang, systemPrompt, uncachedTexts[i], single)
                                single
                            } else {
                                null
                            }
                        }
                    }
                } else {
                    // Fallback to individual translations if split count mismatched (never substitute source text!)
                    uncachedTexts.map { text ->
                        val single = translate(text, prefs, context, targetLang).getOrNull()?.trim()
                        if (!single.isNullOrBlank() && !isInvalidOrUnchangedTranslation(text, single)) {
                            putInCache(context, engine, model, targetLang, systemPrompt, text, single)
                            single
                        } else {
                            null
                        }
                    }
                }
            } else {
                // Try individual paragraphs if joined block failed
                val fallbackList = uncachedTexts.map { text ->
                    val single = translate(text, prefs, context, targetLang).getOrNull()?.trim()
                    if (!single.isNullOrBlank() && !isInvalidOrUnchangedTranslation(text, single)) {
                        putInCache(context, engine, model, targetLang, systemPrompt, text, single)
                        single
                    } else {
                        null
                    }
                }
                if (fallbackList.all { it == null }) {
                    return@withContext Result.failure(trResult.exceptionOrNull() ?: Exception("Page translation failed"))
                }
                fallbackList
            }
        }

        // Merge translated paragraphs back into resultList
        uncachedIndices.forEachIndexed { i, originalIdx ->
            val srcPara = rawParagraphs[originalIdx]
            val transText = translatedUncached.getOrNull(i)?.trim()
            if (!transText.isNullOrBlank() && !isInvalidOrUnchangedTranslation(srcPara, transText)) {
                resultList[originalIdx] = Pair(srcPara, transText)
                putInCache(context, engine, model, targetLang, systemPrompt, srcPara, transText)
            }
        }

        val hasTranslatableParagraphs = rawParagraphs.any { !it.startsWith("[ILLUST:") && !it.startsWith("[PDF_PAGE:") }
        val hasAnyValidTranslation = resultList.any { (orig, trans) ->
            !orig.startsWith("[ILLUST:") && !orig.startsWith("[PDF_PAGE:") &&
                !trans.isNullOrBlank() && !isInvalidOrUnchangedTranslation(orig, trans)
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
        val rawParagraphs = pageRaw.split(Regex("\n\\s*\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
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
