package com.pinyinlens.dict

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.Context
import android.util.Log
import com.pinyinlens.dictionary.DictionaryEntry
import com.pinyinlens.dictionary.DictionarySource

/**
 * Diccionario bilingüe respaldado por SQLite (RF-05 + RNF-05).
 *
 * La base está DESACOPLADA del binario: se instala por separado (o se copia a la
 * ruta de datos) y puede actualizarse sin recompilar. El esquema está indexado
 * por la columna `simplified` para que la consulta sea < 15 ms (RNF-01).
 *
 * Esquema:
 *   entries(simplified TEXT PRIMARY KEY, traditional TEXT, pinyin TEXT,
 *           english TEXT, spanish TEXT)   -- glosas separadas por '\u001f'
 *
 * Si la base no existe, se crea vacía; las consultas devuelven null sin fallar.
 */
class SqliteDictionary private constructor(
    private val db: SQLiteDatabase,
) : DictionarySource {

    override fun lookup(word: String): DictionaryEntry? = queryOne(word)

    override fun lookupChar(c: Char): DictionaryEntry? = queryOne(c.toString())

    private fun queryOne(key: String): DictionaryEntry? {
        db.rawQuery(
            "SELECT simplified, traditional, pinyin, english, spanish FROM entries WHERE simplified = ? LIMIT 1",
            arrayOf(key),
        ).use { cur ->
            if (!cur.moveToFirst()) return null
            return DictionaryEntry(
                simplified = cur.getString(0),
                traditional = cur.getString(1) ?: cur.getString(0),
                pinyin = cur.getString(2) ?: "",
                english = splitGlosses(cur.getString(3)),
                spanish = splitGlosses(cur.getString(4)),
            )
        }
    }

    private fun splitGlosses(raw: String?): List<String> =
        raw?.split(GLOSS_SEP)?.filter { it.isNotBlank() } ?: emptyList()

    fun close() = db.close()

    companion object {
        const val TAG = "PinYinLens"
        const val GLOSS_SEP = "\u001f"
        const val DB_NAME = "pinyinlens_dict.db"
        const val DB_VERSION = 1

        /** Abre (o crea) la base en el almacenamiento de la app. */
        fun open(context: Context): SqliteDictionary {
            val helper = object : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {
                override fun onCreate(db: SQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS entries (" +
                            "simplified TEXT PRIMARY KEY, traditional TEXT, pinyin TEXT, " +
                            "english TEXT, spanish TEXT)",
                    )
                    db.execSQL("CREATE INDEX IF NOT EXISTS idx_simplified ON entries(simplified)")
                    Log.i(TAG, "Diccionario SQLite creado (vacío)")
                }

                override fun onUpgrade(db: SQLiteDatabase, oldV: Int, newV: Int) {
                    // Actualizaciones de esquema futuras.
                }
            }
            return SqliteDictionary(helper.writableDatabase)
        }

        /** Carga la semilla de desarrollo si la base está vacía. */
        fun seedIfEmpty(dict: SqliteDictionary): Int =
            DictionarySeed.loadIfEmpty(dict.db)

        /** Inserta o reemplaza una entrada (usado por la carga de datos). */
        fun insert(
            db: SQLiteDatabase,
            simplified: String,
            traditional: String,
            pinyin: String,
            english: List<String>,
            spanish: List<String>,
        ) {
            db.execSQL(
                "INSERT OR REPLACE INTO entries VALUES (?,?,?,?,?)",
                arrayOf(
                    simplified, traditional, pinyin,
                    english.joinToString(GLOSS_SEP), spanish.joinToString(GLOSS_SEP),
                ),
            )
        }
    }
}
