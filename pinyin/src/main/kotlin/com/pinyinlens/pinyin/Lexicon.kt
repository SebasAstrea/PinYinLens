package com.pinyinlens.pinyin

/**
 * Léxico de palabras chinas → pinyin (con tono numérico estilo CC-CEDICT).
 *
 * Desacoplado de los datos (RNF-05): se construye a partir de entradas que
 * provienen de un recurso externo (CC-CEDICT / glosario), no del binario. El
 * segmentador (RF-04) lo consulta por prefijos para la máxima coincidencia.
 *
 * Internamente es un Trie sobre caracteres Hanzi para resolver en O(longitud)
 * la palabra más larga que empieza en una posición dada.
 */
class Lexicon {

    private class Node {
        val children = HashMap<Char, Node>()
        var pinyin: String? = null // tono numérico, p. ej. "le5" o "liao3 jie3"
        val isWord: Boolean get() = pinyin != null
    }

    private val root = Node()
    var size: Int = 0
        private set

    /** Inserta una palabra con su pinyin numérico (sobrescribe si ya existe). */
    fun put(word: String, numericPinyin: String) {
        if (word.isEmpty()) return
        var node = root
        for (c in word) {
            node = node.children.getOrPut(c) { Node() }
        }
        if (!node.isWord) size++
        node.pinyin = numericPinyin
    }

    /** Pinyin numérico de una palabra exacta, o null si no está. */
    fun pinyinOf(word: String): String? {
        var node = root
        for (c in word) {
            node = node.children[c] ?: return null
        }
        return node.pinyin
    }

    /**
     * Longitud de la palabra más larga del léxico que es prefijo de [text]
     * a partir de [from]. 0 si ninguna palabra empieza ahí.
     */
    fun longestPrefixLen(text: CharSequence, from: Int): Int {
        var node = root
        var best = 0
        var i = from
        while (i < text.length) {
            node = node.children[text[i]] ?: break
            i++
            if (node.isWord) best = i - from
        }
        return best
    }

    companion object {
        /**
         * Carga un léxico desde líneas en formato CC-CEDICT:
         *   trad simp [pin1 yin1] /gloss/gloss/
         * Solo se usan la columna simplificada y el pinyin entre corchetes.
         * Las líneas que empiezan por '#' o vacías se ignoran.
         */
        fun fromCcCedict(lines: Sequence<String>): Lexicon {
            val lex = Lexicon()
            val re = Regex("""^(\S+)\s+(\S+)\s+\[([^\]]*)\]""")
            for (line in lines) {
                if (line.isBlank() || line.startsWith("#")) continue
                val m = re.find(line) ?: continue
                val simplified = m.groupValues[2]
                val pinyin = m.groupValues[3].trim()
                if (simplified.isNotEmpty() && pinyin.isNotEmpty()) {
                    lex.put(simplified, pinyin)
                }
            }
            return lex
        }
    }
}
