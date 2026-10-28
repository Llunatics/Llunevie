package com.llunaticsys.llunevie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IsbnAndParserUnitTest {

    private fun isValidIsbn13(code: String): Boolean {
        if (code.length != 13) return false
        if (!code.startsWith("978") && !code.startsWith("979")) return false
        if (!code.all { it.isDigit() }) return false
        var sum = 0
        for (i in 0 until 12) {
            val digit = code[i] - '0'
            sum += if (i % 2 == 0) digit else digit * 3
        }
        val checkDigit = (10 - (sum % 10)) % 10
        return checkDigit == (code[12] - '0')
    }

    private fun isValidIsbn10(code: String): Boolean {
        if (code.length != 10) return false
        var sum = 0
        for (i in 0 until 9) {
            val c = code[i]
            if (!c.isDigit()) return false
            sum += (c - '0') * (10 - i)
        }
        val lastChar = code[9]
        val checkVal = if (lastChar == 'X' || lastChar == 'x') 10 else if (lastChar.isDigit()) lastChar - '0' else return false
        sum += checkVal
        return sum % 11 == 0
    }

    private fun extractIsbn(rawText: String?): String? {
        if (rawText.isNullOrBlank()) return null
        val match13 = Regex("""\b(?:978|979)[-\s]?(?:\d[-\s]?){9}\d\b""").find(rawText)
            ?: Regex("""(?:978|979)[-\s]?(?:\d[-\s]?){9}\d""").find(rawText)
        if (match13 != null) {
            val clean = match13.value.replace(Regex("""[^0-9]"""), "")
            return if (isValidIsbn13(clean)) clean else null
        }
        val match10 = Regex("""\b(?:\d[-\s]?){9}[\dXx]\b""").find(rawText)
        if (match10 != null) {
            val clean = match10.value.replace(Regex("""[^0-9Xx]"""), "").uppercase()
            if (isValidIsbn10(clean)) return clean
        }
        val digitsOnly = rawText.replace(Regex("""[^0-9Xx]"""), "").uppercase()
        if (digitsOnly.length == 13 && isValidIsbn13(digitsOnly)) return digitsOnly
        if (digitsOnly.length == 10 && isValidIsbn10(digitsOnly)) return digitsOnly
        return null
    }

    @Test
    fun testValidIsbn13Accepted() {
        // Real-world valid ISBNs (Starting with 978 and 979)
        assertTrue(isValidIsbn13("9780132350884")) // Clean Code
        assertTrue(isValidIsbn13("9786020633176")) // Indonesian novel
        assertTrue(isValidIsbn13("9791234567896")) // 979 prefix with valid checksum

        assertEquals("9780132350884", extractIsbn("978-0-13-235088-4"))
        assertEquals("9786020633176", extractIsbn("ISBN: 978-602-06-3317-6"))
    }

    @Test
    fun testValidIsbn10Accepted() {
        // Real-world valid ISBN-10s
        assertTrue(isValidIsbn10("0132350882"))
        assertTrue(isValidIsbn10("0201633612")) // Design Patterns Gang of Four
        assertTrue(isValidIsbn10("043942089X")) // Harry Potter with 'X' check digit

        assertEquals("043942089X", extractIsbn("ISBN 0-439-42089-X"))
    }

    @Test
    fun testNonIsbnBarcodesRejected() {
        // Consumer product EAN-13 barcodes that are NOT books (e.g. food/soda starting with 899 or 490)
        assertFalse(isValidIsbn13("8992775311234"))
        assertFalse(isValidIsbn13("4901234567894"))
        assertNull(extractIsbn("8992775311234"))
        assertNull(extractIsbn("4901234567894"))

        // UPC-A (12 digits)
        assertNull(extractIsbn("012345678905"))

        // Code 128 random tracking numbers
        assertNull(extractIsbn("TRACK1234567890"))

        // Invalid ISBN checksum
        assertFalse(isValidIsbn13("9780132350889")) // Bad check digit
        assertNull(extractIsbn("978-0-13-235088-9"))
    }

    @Test
    fun testContinuousPdfChapterPageMapping() {
        val totalPages = 150
        data class Bookmark(val title: String, val start: Int)
        val bookmarks = listOf(
            Bookmark("Cover", 0),
            Bookmark("Bab 1", 12),
            Bookmark("Bab 2", 45),
            Bookmark("Bab 3", 90)
        )

        var totalCalculatedPages = 0
        val chapters = mutableListOf<List<Int>>()
        for (i in bookmarks.indices) {
            val start = bookmarks[i].start
            val nextStart = if (i + 1 < bookmarks.size) bookmarks[i + 1].start else totalPages
            val end = nextStart.coerceIn(start + 1, totalPages)
            val pages = (start until end).toList()
            chapters.add(pages)
            totalCalculatedPages += pages.size
        }

        // Verify all document pages 0..149 are accounted for without any gap or overlap
        assertEquals(totalPages, totalCalculatedPages)
        assertEquals(12, chapters[0].size) // 0..11
        assertEquals(33, chapters[1].size) // 12..44
        assertEquals(45, chapters[2].size) // 45..89
        assertEquals(60, chapters[3].size) // 90..149

        // Verify chapter starting pages
        assertEquals(0, chapters[0].first())
        assertEquals(12, chapters[1].first())
        assertEquals(45, chapters[2].first())
        assertEquals(90, chapters[3].first())
    }
}
