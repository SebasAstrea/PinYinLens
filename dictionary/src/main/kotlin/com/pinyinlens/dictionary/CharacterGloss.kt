package com.pinyinlens.dictionary

/**
 * Desglose de una palabra en sus caracteres, cada uno con su pinyin y glosa
 * breve (RF-05: "desglose por carácter" en la tarjeta).
 */
data class CharacterGloss(
    val char: String,
    val pinyin: String,
    val gloss: String,
)

/**
 * Fuente de datos del diccionario (RF-05). Se abstrae en una interfaz para que
 * la lógica de armado de la tarjeta sea testeable sin SQLite; la implementación
 * real (SqliteDictionary) la respalda con la base indexada.
 */
interface DictionarySource {
    /** Entrada de una palabra exacta, o null si no está. */
    fun lookup(word: String): DictionaryEntry?

    /** Entrada de un carácter individual, o null. */
    fun lookupChar(c: Char): DictionaryEntry?

    /**
     * Entrada MÁS LARGA contenida en [text], o null. Permite traducir etiquetas
     * que son frases o cadenas con varios términos (p. ej. "锁定屏幕" → 锁定 o
     * 屏幕). La implementación por defecto no hace búsqueda contenida.
     */
    fun lookupContained(text: String): DictionaryEntry? = null
}
