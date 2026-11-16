package com.pinyinlens.dictionary

import org.junit.Assert.assertEquals
import org.junit.Test

class DictionaryEntryTest {

    @Test
    fun entryHoldsBilingualGlosses() {
        val entry = DictionaryEntry(
            simplified = "中国",
            traditional = "中國",
            pinyin = "Zhōngguó",
            english = listOf("China"),
            spanish = listOf("China"),
        )
        assertEquals("中国", entry.simplified)
        assertEquals("Zhōngguó", entry.pinyin)
        assertEquals(listOf("China"), entry.english)
        assertEquals(listOf("China"), entry.spanish)
    }
}
