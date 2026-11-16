package com.pinyinlens.pinyin

/**
 * Una palabra segmentada con su pinyin ya en forma diacrítica (RF-04).
 *
 * @param word texto Hanzi (o no-Hanzi, que se pasa tal cual).
 * @param pinyin pinyin diacrítico por sílabas separadas por espacio; vacío si
 *               no es Hanzi o no hay dato.
 * @param isHanzi true si la palabra es texto chino convertido.
 */
data class PinyinWord(
    val word: String,
    val pinyin: String,
    val isHanzi: Boolean,
)

/**
 * Convierte texto chino en una secuencia de palabras con pinyin, resolviendo la
 * desambiguación tonal por contexto (RF-04).
 *
 * Algoritmo: segmentación por máxima coincidencia hacia adelante sobre el
 * [Lexicon]. La clave de la desambiguación polifónica (行, 长, 着, 了, 地, …) es
 * que, al emparejar la palabra más larga del léxico, se usa el pinyin de ESA
 * palabra, no el del carácter aislado. Ejemplos:
 *   - 了解 => "liǎo jiě"   (了 = liǎo), frente a 了 suelto => "le".
 *   - 银行 => "yín háng"    (行 = háng), frente a 不行 => "bù xíng".
 *   - 长大 => "zhǎng dà"    (长 = zhǎng), frente a 长度 => "cháng dù".
 *
 * Los caracteres Hanzi sin palabra en el léxico caen al mapa de caracteres
 * individuales (pinyin más frecuente). El texto no-Hanzi se devuelve intacto.
 */
class PinyinConverter(
    private val lexicon: Lexicon,
    /** Carácter individual -> pinyin numérico por defecto (polifónico: lectura frecuente). */
    private val charFallback: Map<Char, String> = emptyMap(),
) {

    /** Segmenta [text] y devuelve las palabras con su pinyin diacrítico. */
    fun convert(text: String): List<PinyinWord> {
        val result = ArrayList<PinyinWord>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (!CjkText.isHanzi(c)) {
                // Acumular corrida no-Hanzi como una sola "palabra" opaca.
                val start = i
                while (i < text.length && !CjkText.isHanzi(text[i])) i++
                result.add(PinyinWord(text.substring(start, i), "", isHanzi = false))
                continue
            }

            val len = lexicon.longestPrefixLen(text, i)
            if (len > 0) {
                val word = text.substring(i, i + len)
                val numeric = lexicon.pinyinOf(word) ?: ""
                result.add(PinyinWord(word, ToneMarks.syllablesToDiacritic(numeric), isHanzi = true))
                i += len
            } else {
                // Carácter Hanzi suelto: usar fallback por carácter.
                val numeric = charFallback[c] ?: ""
                val py = if (numeric.isNotEmpty()) ToneMarks.toDiacritic(numeric) else ""
                result.add(PinyinWord(c.toString(), py, isHanzi = true))
                i++
            }
        }
        return result
    }

    /** Pinyin diacrítico completo de [text], sílabas separadas por espacio. */
    fun toPinyin(text: String): String =
        convert(text).filter { it.isHanzi && it.pinyin.isNotEmpty() }
            .joinToString(" ") { it.pinyin }
}
