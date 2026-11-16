package com.pinyinlens.dict

import android.database.sqlite.SQLiteDatabase

/**
 * Carga semilla del diccionario (RF-05) para verificación funcional en
 * dispositivo. En producción se sustituye por una base CC-CEDICT completa con
 * glosas ES/EN cargada por separado (RNF-05: datos desacoplados del binario).
 *
 * Formato de cada fila: simplified, traditional, pinyin, english, spanish.
 */
object DictionarySeed {

    private data class Row(
        val s: String, val t: String, val py: String,
        val en: List<String>, val es: List<String>,
    )

    private val rows = listOf(
        Row("中国", "中國", "Zhōngguó", listOf("China"), listOf("China")),
        Row("你好", "你好", "nǐ hǎo", listOf("hello"), listOf("hola")),
        Row("中文", "中文", "Zhōngwén", listOf("Chinese language"), listOf("idioma chino")),
        Row("学习", "學習", "xuéxí", listOf("to study", "to learn"), listOf("estudiar", "aprender")),
        Row("老师", "老師", "lǎoshī", listOf("teacher"), listOf("profesor", "maestro")),
        Row("学生", "學生", "xuéshēng", listOf("student"), listOf("estudiante", "alumno")),
        Row("谢谢", "謝謝", "xièxie", listOf("thanks"), listOf("gracias")),
        Row("今天", "今天", "jīntiān", listOf("today"), listOf("hoy")),
        Row("北京", "北京", "Běijīng", listOf("Beijing"), listOf("Pekín")),
        Row("银行", "銀行", "yínháng", listOf("bank"), listOf("banco")),
        // Caracteres para el desglose.
        Row("中", "中", "zhōng", listOf("middle", "center"), listOf("centro", "medio")),
        Row("国", "國", "guó", listOf("country"), listOf("país")),
        Row("你", "你", "nǐ", listOf("you"), listOf("tú")),
        Row("好", "好", "hǎo", listOf("good"), listOf("bueno")),
        Row("文", "文", "wén", listOf("language", "writing"), listOf("lengua", "escritura")),
        Row("学", "學", "xué", listOf("to learn"), listOf("aprender")),
        Row("习", "習", "xí", listOf("to practice"), listOf("practicar")),
        Row("老", "老", "lǎo", listOf("old"), listOf("viejo")),
        Row("师", "師", "shī", listOf("master"), listOf("maestro")),
        Row("生", "生", "shēng", listOf("to be born", "life"), listOf("nacer", "vida")),
    )

    /** Inserta la semilla si la tabla está vacía. Devuelve filas insertadas. */
    fun loadIfEmpty(db: SQLiteDatabase): Int {
        val count = db.rawQuery("SELECT COUNT(*) FROM entries", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        if (count > 0) return 0
        db.beginTransaction()
        try {
            for (r in rows) {
                SqliteDictionary.insert(db, r.s, r.t, r.py, r.en, r.es)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return rows.size
    }
}
