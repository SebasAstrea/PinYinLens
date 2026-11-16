package com.pinyinlens.pinyin

import org.junit.Assert.assertEquals
import org.junit.Test

class ToneMarksTest {

    @Test
    fun placesToneOnAAndE() {
        assertEquals("hǎo", ToneMarks.toDiacritic("hao3"))
        assertEquals("māo", ToneMarks.toDiacritic("mao1"))
        assertEquals("běn", ToneMarks.toDiacritic("ben3"))
    }

    @Test
    fun placesToneOnOuGroupOnO() {
        assertEquals("gǒu", ToneMarks.toDiacritic("gou3"))
        assertEquals("zhōu", ToneMarks.toDiacritic("zhou1"))
    }

    @Test
    fun placesToneOnLastVowelOtherwise() {
        assertEquals("nǐ", ToneMarks.toDiacritic("ni3"))
        assertEquals("guì", ToneMarks.toDiacritic("gui4"))
        assertEquals("duì", ToneMarks.toDiacritic("dui4"))
    }

    @Test
    fun handlesUmlautUWrittenAsColonOrV() {
        assertEquals("lǜ", ToneMarks.toDiacritic("lu:4"))
        assertEquals("lǜ", ToneMarks.toDiacritic("lv4"))
        assertEquals("nǚ", ToneMarks.toDiacritic("nv3"))
    }

    @Test
    fun neutralToneHasNoMark() {
        assertEquals("le", ToneMarks.toDiacritic("le5"))
        assertEquals("de", ToneMarks.toDiacritic("de5"))
        assertEquals("ma", ToneMarks.toDiacritic("ma"))
    }

    @Test
    fun allFourTonesOnA() {
        assertEquals("ā", ToneMarks.toDiacritic("a1"))
        assertEquals("á", ToneMarks.toDiacritic("a2"))
        assertEquals("ǎ", ToneMarks.toDiacritic("a3"))
        assertEquals("à", ToneMarks.toDiacritic("a4"))
    }

    @Test
    fun convertsSyllableSequences() {
        assertEquals("nǐ hǎo", ToneMarks.syllablesToDiacritic("ni3 hao3"))
        assertEquals("zhōng guó", ToneMarks.syllablesToDiacritic("zhong1 guo2"))
    }
}
