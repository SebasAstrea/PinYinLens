package com.pinyinlens.pinyin

/**
 * Utilidades de detección de caracteres Hanzi (CJK Unified Ideographs).
 *
 * Relacionado con RF-02 (filtrado de cadenas con caracteres U+4E00–U+9FFF) y
 * usado como entrada por la segmentación de RF-04. Lógica pura, cubierta por
 * pruebas unitarias JVM (AGENTS §4.1).
 */
object CjkText {

    /** Rango CJK Unified Ideographs del SRS: U+4E00 a U+9FFF. */
    private const val CJK_START = 0x4E00
    private const val CJK_END = 0x9FFF

    /** True si [c] es un ideograma Han del bloque CJK Unified Ideographs. */
    fun isHanzi(c: Char): Boolean = c.code in CJK_START..CJK_END

    /** True si [text] contiene al menos un carácter Hanzi. */
    fun containsHanzi(text: CharSequence): Boolean = text.any { isHanzi(it) }

    /** Número de caracteres Hanzi en [text]. */
    fun countHanzi(text: CharSequence): Int = text.count { isHanzi(it) }
}
