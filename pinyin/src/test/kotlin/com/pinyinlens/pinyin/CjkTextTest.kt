package com.pinyinlens.pinyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CjkTextTest {

    @Test
    fun hanziCharactersAreDetected() {
        assertTrue(CjkText.isHanzi('中'))
        assertTrue(CjkText.isHanzi('文'))
        assertTrue(CjkText.isHanzi('\u4E00')) // límite inferior
        assertTrue(CjkText.isHanzi('\u9FFF')) // límite superior
    }

    @Test
    fun nonHanziCharactersAreRejected() {
        assertFalse(CjkText.isHanzi('A'))
        assertFalse(CjkText.isHanzi('1'))
        assertFalse(CjkText.isHanzi(' '))
        assertFalse(CjkText.isHanzi('\u4DFF')) // justo antes del rango
    }

    @Test
    fun mixedStringsAreCounted() {
        assertTrue(CjkText.containsHanzi("Hola 中国"))
        assertFalse(CjkText.containsHanzi("Hola mundo"))
        assertEquals(2, CjkText.countHanzi("中a国b"))
        assertEquals(0, CjkText.countHanzi("abc 123"))
    }
}
