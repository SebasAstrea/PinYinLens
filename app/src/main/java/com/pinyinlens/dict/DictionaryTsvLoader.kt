package com.pinyinlens.dict

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log

/**
 * Carga el diccionario HSK 1-6 desde el asset `hsk_dictionary.tsv` al SQLite
 * (RF-05 + RNF-05). El asset se genera por `scripts/build_dictionary.ps1`.
 *
 * Formato TSV (TAB-separado, glosas separadas por U+001F):
 *   simplified  traditional  pinyin  english  spanish  hsk
 *
 * La carga es idempotente: solo corre si la tabla está (casi) vacía, para no
 * duplicar en cada arranque. También inserta cada carácter individual que no
 * tenga ya entrada propia, para alimentar el desglose de la tarjeta.
 */
object DictionaryTsvLoader {

    const val TAG = "PinYinLens"
    const val ASSET = "hsk_dictionary.tsv"
    private const val SEP = "\u001f"

    /** Umbral por debajo del cual se considera que hay que (re)cargar. */
    private const val SEED_SIZE = 32

    /**
     * Carga el TSV si la base tiene menos de [SEED_SIZE] entradas (es decir, solo
     * la semilla de desarrollo o vacía). Devuelve el número de filas insertadas.
     */
    fun loadIfNeeded(context: Context, db: SQLiteDatabase): Int {
        val count = db.rawQuery("SELECT COUNT(*) FROM entries", null).use {
            if (it.moveToFirst()) it.getInt(0) else 0
        }
        if (count >= SEED_SIZE) return 0

        var inserted = 0
        db.beginTransaction()
        try {
            context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).useLines { lines ->
                for (line in lines) {
                    if (line.isBlank()) continue
                    val cols = line.split('\t')
                    if (cols.size < 5) continue
                    val simplified = cols[0]
                    val traditional = cols[1].ifEmpty { simplified }
                    val pinyin = cols[2]
                    val english = cols[3].split(SEP).filter { it.isNotBlank() }
                    val spanish = cols[4].split(SEP).filter { it.isNotBlank() }
                    SqliteDictionary.insert(db, simplified, traditional, pinyin, english, spanish)
                    inserted++
                }
            }
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            Log.e(TAG, "Error cargando $ASSET", e)
        } finally {
            db.endTransaction()
        }
        Log.i(TAG, "Diccionario HSK cargado: $inserted entradas")
        return inserted
    }
}
