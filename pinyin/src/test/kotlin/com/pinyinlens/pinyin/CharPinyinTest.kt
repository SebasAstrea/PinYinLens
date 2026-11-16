package com.pinyinlens.pinyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CharPinyinTest {

    @Test
    fun parsesCharacterTable() {
        val map = CharPinyin.fromTsv(
            sequenceOf(
                "一\tyi1",
                "星\txing1",
                "帐\tzhang4",
            ),
        )
        assertEquals(3, map.size)
        assertEquals("xing1", map['星'])
        assertEquals("zhang4", map['帐'])
    }

    @Test
    fun ignoresCommentsBlankAndMalformedLines() {
        val map = CharPinyin.fromTsv(
            sequenceOf(
                "# tabla generada",
                "",
                "   ",
                "星\txing1",
                "星xing1",      // sin TAB
                "\tzhang4",     // sin carácter
                "户\thu4",
            ),
        )
        assertEquals(2, map.size)
        assertEquals("xing1", map['星'])
        assertEquals("hu4", map['户'])
    }

    @Test
    fun feedsTheConverterFallback() {
        // El respaldo se usa cuando el carácter no está en el léxico de palabras.
        val map = CharPinyin.fromTsv(sequenceOf("星\txing1", "帐\tzhang4", "户\thu4"))
        val converter = PinyinConverter(Lexicon(), map)
        assertEquals("xīng zhàng hù", converter.toPinyin("星帐户"))
    }

    @Test
    fun datasetTableCoversUiCharacters() {
        // Regresión: los caracteres que la UI del sistema mostraba sin pinyin.
        val file = listOf("../datasets/char_pinyin.tsv", "datasets/char_pinyin.tsv")
            .map { java.io.File(it) }
            .firstOrNull { it.exists() } ?: return
        val table = file.bufferedReader(Charsets.UTF_8).useLines { CharPinyin.fromTsv(it) }
        for (ch in "星帐户屏幕纹定位若配置影睡院") {
            assertTrue("falta pinyin para '$ch'", table.containsKey(ch))
        }
    }
}
