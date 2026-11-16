package com.pinyinlens.pinyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LexiconTest {

    @Test
    fun storesAndRetrievesWords() {
        val lex = Lexicon()
        lex.put("中国", "zhong1 guo2")
        assertEquals("zhong1 guo2", lex.pinyinOf("中国"))
        assertNull(lex.pinyinOf("美国"))
    }

    @Test
    fun longestPrefixPrefersLongerWord() {
        val lex = Lexicon()
        lex.put("中", "zhong1")
        lex.put("中国", "zhong1 guo2")
        lex.put("中国人", "zhong1 guo2 ren2")
        // Desde 0 en "中国人民", la más larga es 中国人 (len 3).
        assertEquals(3, lex.longestPrefixLen("中国人民", 0))
    }

    @Test
    fun longestPrefixZeroWhenNoWordStartsHere() {
        val lex = Lexicon()
        lex.put("你好", "ni3 hao3")
        assertEquals(0, lex.longestPrefixLen("中国", 0))
    }

    @Test
    fun sizeCountsDistinctWords() {
        val lex = Lexicon()
        lex.put("中", "zhong1")
        lex.put("中", "zhong4") // sobrescribe, no incrementa
        lex.put("国", "guo2")
        assertEquals(2, lex.size)
    }

    @Test
    fun parsesCcCedictLines() {
        val lines = sequenceOf(
            "# comentario",
            "中國 中国 [zhong1 guo2] /China/",
            "你好 你好 [ni3 hao3] /hello/",
            "",
        )
        val lex = Lexicon.fromCcCedict(lines)
        assertEquals("zhong1 guo2", lex.pinyinOf("中国"))
        assertEquals("ni3 hao3", lex.pinyinOf("你好"))
        assertEquals(2, lex.size)
    }
}
