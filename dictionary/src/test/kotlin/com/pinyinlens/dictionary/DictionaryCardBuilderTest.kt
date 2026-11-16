package com.pinyinlens.dictionary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DictionaryCardBuilderTest {

    private val fake = object : DictionarySource {
        val words = mapOf(
            "中国" to DictionaryEntry("中国", "中國", "Zhōngguó", listOf("China"), listOf("China")),
            "你好" to DictionaryEntry("你好", "你好", "nǐ hǎo", listOf("hello"), listOf("hola")),
        )
        val chars = mapOf(
            '中' to DictionaryEntry("中", "中", "zhōng", listOf("middle"), listOf("medio")),
            '国' to DictionaryEntry("国", "國", "guó", listOf("country"), listOf("país")),
        )
        override fun lookup(word: String) = words[word]
        override fun lookupChar(c: Char) = chars[c]
    }

    @Test
    fun buildsCardWithBilingualGlossesAndBreakdown() {
        val card = DictionaryCardBuilder.build("中国", fake)!!
        assertEquals("中国", card.simplified)
        assertEquals("中國", card.traditional)
        assertEquals("Zhōngguó", card.pinyin)
        assertEquals(listOf("China"), card.english)
        assertEquals(listOf("China"), card.spanish)
        assertTrue(card.hasData)

        assertEquals(2, card.breakdown.size)
        assertEquals("中", card.breakdown[0].char)
        assertEquals("zhōng", card.breakdown[0].pinyin)
        assertEquals("medio", card.breakdown[0].gloss) // prefiere español
        assertEquals("país", card.breakdown[1].gloss)
    }

    @Test
    fun returnsNullForUnknownWord() {
        assertNull(DictionaryCardBuilder.build("美国", fake))
    }

    @Test
    fun breakdownToleratesMissingCharEntries() {
        // 你好: los caracteres no están en el mapa de chars -> glosa vacía, sin fallo.
        val card = DictionaryCardBuilder.build("你好", fake)!!
        assertEquals(2, card.breakdown.size)
        assertEquals("", card.breakdown[0].gloss)
        assertEquals("", card.breakdown[0].pinyin)
    }

    @Test
    fun hasDataFalseWhenNoGlosses() {
        val card = DictionaryCard("x", "x", "x", emptyList(), emptyList(), emptyList())
        assertFalse(card.hasData)
    }
}
