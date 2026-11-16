package com.pinyinlens.dictionary

/**
 * Entrada de diccionario bilingüe (RF-05).
 *
 * Modela una unidad léxica con su pinyin, desglose por carácter y glosas en
 * español e inglés. La carga real desde SQLite (CC-CEDICT + glosas) se añade en
 * la historia RF-05; este modelo puro es testeable en JVM (AGENTS §4.1).
 */
data class DictionaryEntry(
    val simplified: String,
    val traditional: String,
    val pinyin: String,
    val english: List<String>,
    val spanish: List<String>,
)
