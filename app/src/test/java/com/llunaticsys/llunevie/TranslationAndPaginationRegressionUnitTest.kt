package com.llunaticsys.llunevie

import com.llunaticsys.llunevie.data.remote.TranslationRepository
import com.llunaticsys.llunevie.model.isPageTranslationComplete
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationAndPaginationRegressionUnitTest {

    @Test
    fun testSentenceSplitRegexPreventsMidSentenceSplittingOnQuotesAndApostrophes() {
        val regex = Regex("""(?<=[.!?…])\s+|(?<=[.!?…]["”’'])\s+""")

        // Standard terminal punctuation
        val standard = "Kalimat pertama. Kalimat kedua! Kalimat ketiga? Kalimat keempat… Selesai."
        val standardSplits = standard.split(regex)
        assertEquals(5, standardSplits.size)
        assertEquals("Kalimat pertama.", standardSplits[0])
        assertEquals("Kalimat kedua!", standardSplits[1])
        assertEquals("Kalimat ketiga?", standardSplits[2])
        assertEquals("Kalimat keempat…", standardSplits[3])
        assertEquals("Selesai.", standardSplits[4])

        // Terminal punctuation followed by closing quote
        val dialogueQuotes = "\"Ini luar biasa!\" \"Apakah kamu yakin?\""
        val dialogueSplits = dialogueQuotes.split(regex)
        assertEquals(2, dialogueSplits.size)
        assertEquals("\"Ini luar biasa!\"", dialogueSplits[0])
        assertEquals("\"Apakah kamu yakin?\"", dialogueSplits[1])

        // Mid-sentence quote, opening quote, or apostrophe MUST NOT be split
        val midSentenceQuotes = "Kata 'buku' itu bermakna \"sumber ilmu\" dan it's very important."
        val midSplits = midSentenceQuotes.split(regex)
        assertEquals(1, midSplits.size)
        assertEquals(midSentenceQuotes, midSplits[0])
    }

    @Test
    fun testSplitTextSafelyByLengthPreservesAllTextAndHonorsBoundaries() {
        // Short text returns single chunk
        val short = "Teks pendek"
        val shortResult = TranslationRepository.splitTextSafelyByLength(short, 100)
        assertEquals(listOf("Teks pendek"), shortResult)

        // Multiple paragraphs split on newline boundaries
        val paras = "Paragraf satu panjangnya sedang.\nParagraf dua juga ada.\nParagraf tiga penutup."
        val paraChunks = TranslationRepository.splitTextSafelyByLength(paras, 45)
        for (chunk in paraChunks) {
            assertTrue("Chunk length must be <= 45, was ${chunk.length}", chunk.length <= 45)
        }
        assertTrue(paraChunks.size >= 2)

        // Sentences within a single long paragraph split on sentence boundaries
        val sentences = "Kalimat satu selesai di sini. Kalimat kedua lebih panjang dan selesai di sini juga. Kalimat ketiga penutup."
        val sentenceChunks = TranslationRepository.splitTextSafelyByLength(sentences, 40)
        for (chunk in sentenceChunks) {
            assertTrue("Chunk length must be <= 40, was ${chunk.length}", chunk.length <= 40)
        }

        // Unbroken string hard-sliced at limit without discarding characters
        val unbroken = "a".repeat(120)
        val unbrokenChunks = TranslationRepository.splitTextSafelyByLength(unbroken, 50)
        assertEquals(3, unbrokenChunks.size)
        assertEquals(50, unbrokenChunks[0].length)
        assertEquals(50, unbrokenChunks[1].length)
        assertEquals(20, unbrokenChunks[2].length)
        assertEquals(unbroken, unbrokenChunks.joinToString(""))
    }

    @Test
    fun testIsInvalidOrUnchangedTranslationRejectsDelimitersAndTruncatedStubs() {
        // Leftover delimiter tokens must be rejected
        assertTrue(TranslationRepository.isInvalidOrUnchangedTranslation("Sumber", "Hasil [[PARA_SPLIT]] sisa"))
        assertTrue(TranslationRepository.isInvalidOrUnchangedTranslation("Sumber", "Hasil PARA SPLIT sisa"))
        assertTrue(TranslationRepository.isInvalidOrUnchangedTranslation("Sumber", "Hasil [[ sisa"))
        assertTrue(TranslationRepository.isInvalidOrUnchangedTranslation("Sumber", "Hasil ［［ sisa"))
        assertTrue(TranslationRepository.isInvalidOrUnchangedTranslation("Sumber", "Hasil 【PARA sisa"))

        // 2-letter orphan stub like "BU" on substantive source (>= 35 chars) must be rejected
        val source35 = "Bu Marto membawa es dawet ke ruang tamu."
        assertTrue(TranslationRepository.isInvalidOrUnchangedTranslation(source35, "BU"))
        assertTrue(TranslationRepository.isInvalidOrUnchangedTranslation(source35, "OK"))

        // Severely truncated translation (< 12% on src >= 80) must be rejected
        val source80 = "Karena mereka tidak tahu arti dari kata tersebut, mereka bertanya kepada orang tua di desa itu."
        assertTrue(source80.length >= 80)
        assertTrue(TranslationRepository.isInvalidOrUnchangedTranslation(source80, "因为不知道"))

        // Valid translation must NOT be rejected
        val validIndo = "Ini adalah sebuah buku yang sangat bagus untuk dibaca santai."
        val validChinese = "这是一本非常适合休闲阅读的好书。"
        assertFalse(TranslationRepository.isInvalidOrUnchangedTranslation(validIndo, validChinese))
    }

    @Test
    fun testGuessSourceLangForFallbackDetectsIndonesianCorrectly() {
        // Conversational Indonesian dialogue should detect "id"
        val indoDialogue = "“Kamu mondar-mandir saja, Mas, ada apa gerangan?” tanya Bu Marto."
        val guessedId = TranslationRepository.guessSourceLangForFallback(indoDialogue, "zh-CN")
        assertEquals("id", guessedId)

        // Short Indonesian line with Asian target defaults to "id" when no English stopwords match
        val shortIndo = "Dawet ice dan getuk lindri"
        val guessedShort = TranslationRepository.guessSourceLangForFallback(shortIndo, "zh-CN")
        assertEquals("id", guessedShort)

        // English text detects "en"
        val englishText = "The quick brown fox jumps over the lazy dog and was very fast."
        val guessedEn = TranslationRepository.guessSourceLangForFallback(englishText, "zh-CN")
        assertEquals("en", guessedEn)
    }

    @Test
    fun testIsPageTranslationCompleteRequiresAllParagraphsTranslated() {
        val completePairs = listOf(
            Pair("Paragraf satu.", "Paragraph one."),
            Pair("Paragraf dua.", "Paragraph two.")
        )
        assertTrue(isPageTranslationComplete(completePairs))

        // Incomplete: one paragraph translation is null
        val incompletePairs = listOf(
            Pair("Paragraf satu.", "Paragraph one."),
            Pair("Paragraf dua.", null)
        )
        assertFalse(isPageTranslationComplete(incompletePairs))

        // Incomplete: translation is identical to source
        val identicalPairs = listOf(
            Pair("Paragraf satu.", "Paragraf satu.")
        )
        assertFalse(isPageTranslationComplete(identicalPairs))

        // Illustration-only page has no translatable paragraphs
        val illustOnly = listOf(
            Pair("[ILLUST:cover.jpg|Cover]", null)
        )
        assertFalse(isPageTranslationComplete(illustOnly))

        // Empty list is not complete
        assertFalse(isPageTranslationComplete(emptyList()))
        assertFalse(isPageTranslationComplete(null))
    }
}
