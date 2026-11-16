package com.pinyinlens.pinyin

/**
 * Conversión de pinyin con tono numérico (estilo CC-CEDICT, p. ej. "ni3",
 * "hao3", "lu:4") a pinyin con marca diacrítica ("nǐ", "hǎo", "lǜ").
 *
 * Parte de RF-04. Es 100% algorítmica y se prueba exhaustivamente en JVM.
 *
 * Reglas de colocación del tono (estándar):
 *  1. Si hay 'a' o 'e', la marca va sobre esa vocal.
 *  2. Si hay "ou", va sobre la 'o'.
 *  3. En otro caso, sobre la última vocal.
 *  'ü' se escribe "u:" o "v" en la entrada numérica.
 */
object ToneMarks {

    // Vocales con marca por tono: índice 0 = tono 1 ... 3 = tono 4; tono 5/0 = sin marca.
    private val TONE_TABLE = mapOf(
        'a' to charArrayOf('ā', 'á', 'ǎ', 'à'),
        'e' to charArrayOf('ē', 'é', 'ě', 'è'),
        'i' to charArrayOf('ī', 'í', 'ǐ', 'ì'),
        'o' to charArrayOf('ō', 'ó', 'ǒ', 'ò'),
        'u' to charArrayOf('ū', 'ú', 'ǔ', 'ù'),
        'ü' to charArrayOf('ǖ', 'ǘ', 'ǚ', 'ǜ'),
    )

    /**
     * Convierte una sílaba con tono numérico a diacrítica.
     * Acepta "u:" y "v" como 'ü'. Tono 5 o ausente => sin marca.
     */
    fun toDiacritic(numeric: String): String {
        if (numeric.isEmpty()) return numeric
        val last = numeric.last()
        val tone = if (last.isDigit()) last - '0' else 0
        val bodyRaw = if (last.isDigit()) numeric.dropLast(1) else numeric

        // Normalizar ü escrito como "u:" o "v".
        val body = bodyRaw.replace("u:", "ü").replace("v", "ü")

        if (tone < 1 || tone > 4) return body // neutro/5: sin diacrítico

        val idx = tonePosition(body)
        if (idx < 0) return body

        val vowel = body[idx]
        val marked = TONE_TABLE[vowel]?.get(tone - 1) ?: return body
        return body.substring(0, idx) + marked + body.substring(idx + 1)
    }

    /** Convierte una secuencia de sílabas separadas por espacio. */
    fun syllablesToDiacritic(numericSpaced: String): String =
        numericSpaced.trim().split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ") { toDiacritic(it) }

    /** Índice de la vocal que lleva la marca de tono, según las reglas estándar. */
    private fun tonePosition(body: String): Int {
        val a = body.indexOf('a')
        if (a >= 0) return a
        val e = body.indexOf('e')
        if (e >= 0) return e
        val ou = body.indexOf("ou")
        if (ou >= 0) return ou // la 'o' de "ou"
        // Última vocal (incluye ü).
        for (i in body.indices.reversed()) {
            if (body[i] in "aeiouü") return i
        }
        return -1
    }
}
