package com.pinyinlens.dictionary

/**
 * Contenido de la tarjeta flotante que se muestra tras el hold de 5 s (RF-05):
 * Hanzi (simplificado/tradicional), pinyin, desglose por carácter, y las
 * traducciones al español y al inglés.
 */
data class DictionaryCard(
    val simplified: String,
    val traditional: String,
    val pinyin: String,
    val english: List<String>,
    val spanish: List<String>,
    val breakdown: List<CharacterGloss>,
) {
    val hasData: Boolean
        get() = english.isNotEmpty() || spanish.isNotEmpty()
}

/**
 * Arma la [DictionaryCard] de una palabra a partir de una [DictionarySource].
 * Lógica pura (RF-05): resuelve la entrada de la palabra y el desglose carácter
 * a carácter. Testeable en JVM con una fuente simulada.
 */
object DictionaryCardBuilder {

    fun build(word: String, source: DictionarySource): DictionaryCard? {
        // La etiqueta puede ser un nodo de texto completo (una frase): si no hay
        // entrada exacta, se usa la entrada más larga contenida en el texto.
        val entry = source.lookup(word) ?: source.lookupContained(word) ?: return null
        val term = entry.simplified
        val breakdown = term.map { c ->
            val ce = source.lookupChar(c)
            CharacterGloss(
                char = c.toString(),
                pinyin = ce?.pinyin ?: "",
                gloss = ce?.spanish?.firstOrNull() ?: ce?.english?.firstOrNull() ?: "",
            )
        }
        return DictionaryCard(
            simplified = entry.simplified,
            traditional = entry.traditional,
            pinyin = entry.pinyin,
            english = entry.english,
            spanish = entry.spanish,
            breakdown = breakdown,
        )
    }
}
