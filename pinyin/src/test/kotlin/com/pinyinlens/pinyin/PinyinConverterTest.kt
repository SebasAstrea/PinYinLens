package com.pinyinlens.pinyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica la segmentación y, sobre todo, la desambiguación tonal por contexto
 * de los caracteres polifónicos que cita el SRS (行, 长, 着, 了, 地).
 *
 * El léxico de prueba es pequeño y controlado: aquí se valida el MECANISMO
 * (la palabra manda sobre el carácter aislado), no la cobertura, que depende
 * del CC-CEDICT real cargado en producción.
 */
class PinyinConverterTest {

    private fun converter(): PinyinConverter {
        val lex = Lexicon().apply {
            // Palabras que fijan la lectura contextual de cada polífono.
            put("银行", "yin2 hang2")   // 行 = háng
            put("不行", "bu4 xing2")    // 行 = xíng
            put("长大", "zhang3 da4")   // 长 = zhǎng
            put("长度", "chang2 du4")   // 长 = cháng
            put("了解", "liao3 jie3")   // 了 = liǎo
            put("着急", "zhao2 ji2")    // 着 = zháo
            put("看着", "kan4 zhe5")    // 着 = zhe (neutro)
            put("慢慢地", "man4 man4 de5") // 地 = de (partícula)
            put("地方", "di4 fang1")    // 地 = dì
            put("你好", "ni3 hao3")
        }
        val charFallback = mapOf(
            '了' to "le5",   // 了 aislado = le
            '行' to "xing2",
            '长' to "chang2",
            '着' to "zhe5",
            '地' to "di4",
            '我' to "wo3",
        )
        return PinyinConverter(lex, charFallback)
    }

    @Test
    fun disambiguatesXingVsHang() {
        assertEquals("yín háng", converter().toPinyin("银行"))
        assertEquals("bù xíng", converter().toPinyin("不行"))
    }

    @Test
    fun disambiguatesChangVsZhang() {
        assertEquals("zhǎng dà", converter().toPinyin("长大"))
        assertEquals("cháng dù", converter().toPinyin("长度"))
    }

    @Test
    fun disambiguatesLeVsLiao() {
        // 了解 => liǎo; 了 suelto => le (fallback de carácter).
        assertEquals("liǎo jiě", converter().toPinyin("了解"))
        assertEquals("le", converter().toPinyin("了"))
    }

    @Test
    fun disambiguatesZheVsZhao() {
        assertEquals("zháo jí", converter().toPinyin("着急"))
        assertEquals("kàn zhe", converter().toPinyin("看着"))
    }

    @Test
    fun disambiguatesDeParticleVsDi() {
        assertEquals("màn màn de", converter().toPinyin("慢慢地"))
        assertEquals("dì fāng", converter().toPinyin("地方"))
    }

    @Test
    fun segmentsByMaximumMatching() {
        val words = converter().convert("你好地方")
        assertEquals(listOf("你好", "地方"), words.filter { it.isHanzi }.map { it.word })
    }

    @Test
    fun passesNonHanziThrough() {
        val words = converter().convert("OK你好!")
        assertEquals("OK", words.first().word)
        assertFalse(words.first().isHanzi)
        assertTrue(words.any { it.word == "你好" && it.isHanzi })
    }

    @Test
    fun fallsBackToCharForUnknownWord() {
        // 我 no está como palabra; usa fallback de carácter.
        assertEquals("wǒ", converter().toPinyin("我"))
    }
}
