package com.pinyinlens.pinyin

import com.pinyinlens.stats.ProportionZTest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Validación de A_pinyin (RF-04) contra el corpus anotado de `datasets/`.
 *
 * Carga el léxico (CC-CEDICT) y el corpus, convierte cada oración y compara con
 * el pinyin esperado. A_pinyin = correctas / total. Aplica la prueba Z de
 * proporciones del SRS (H₀: P ≤ 0.91, α = 0.05).
 *
 * Gate (ver datasets/README.md):
 *  - Si el corpus tiene n ≥ 100, se EXIGE rechazar H₀ y A_pinyin ≥ 0.94.
 *  - Con el corpus semilla (n < 100), no se bloquea el build: se verifica que el
 *    mecanismo funciona (A_pinyin alta) pero el gate formal queda a la espera de
 *    un CC-CEDICT completo + 500 oraciones reales.
 */
class PinyinAccuracyDatasetTest {

    private val P0 = 0.91
    private val TARGET = 0.94
    private val FORMAL_N = 100

    /** Fallback de caracteres sueltos frecuentes (lectura más común). */
    private val charFallback = mapOf(
        '的' to "de5", '是' to "shi4", '了' to "le5", '我' to "wo3",
        '你' to "ni3", '他' to "ta1", '她' to "ta1", '们' to "men5",
    )

    @Test
    fun aPinyinMeetsThreshold() {
        val root = projectRoot()
        val lexFile = File(root, "datasets/lexicon_seed.cedict")
        val corpusFile = File(root, "datasets/pinyin_corpus.tsv")
        assumeTrue("Falta datasets/lexicon_seed.cedict", lexFile.exists())
        assumeTrue("Falta datasets/pinyin_corpus.tsv", corpusFile.exists())

        val lexicon = lexFile.useLines { Lexicon.fromCcCedict(it) }
        val converter = PinyinConverter(lexicon, charFallback)

        var total = 0
        var correct = 0
        val misses = ArrayList<String>()
        corpusFile.forEachLine { line ->
            if (line.isBlank() || line.startsWith("#")) return@forEachLine
            val parts = line.split('\t')
            if (parts.size < 2) return@forEachLine
            val hanzi = parts[0].trim()
            val expected = parts[1].trim()
            val actual = converter.toPinyin(hanzi)
            total++
            if (actual == expected) correct++ else misses.add("$hanzi => '$actual' (esperado '$expected')")
        }

        val aPinyin = if (total > 0) correct.toDouble() / total else 0.0
        val z = ProportionZTest.upperTail(correct, total, P0)
        println("RF-04 A_pinyin=$aPinyin ($correct/$total), Z=${z.z}, p=${z.pValue}, rechazaH0=${z.rejectsNull}")
        if (misses.isNotEmpty()) println("Fallos:\n" + misses.joinToString("\n"))

        if (total >= FORMAL_N) {
            // Corpus de tamaño formal: gate estadístico estricto del SRS.
            assertTrue(
                "RF-04: A_pinyin=$aPinyin debe ser >= $TARGET sobre n=$total",
                aPinyin >= TARGET,
            )
            assertTrue(
                "RF-04: se debe rechazar H0:P<=0.91 (Z=${z.z}, p=${z.pValue})",
                z.rejectsNull,
            )
        } else {
            // Corpus semilla: no bloquea el build; valida el mecanismo.
            assertTrue(
                "Corpus semilla: el motor debe acertar >= 90% de las oraciones " +
                    "controladas (A_pinyin=$aPinyin). Gate formal pendiente de n>=100.",
                aPinyin >= 0.90,
            )
        }
    }

    /** Raíz del proyecto: el working dir de los tests es el módulo `pinyin`. */
    private fun projectRoot(): File {
        var dir = File(".").absoluteFile
        repeat(4) {
            if (File(dir, "datasets").isDirectory) return dir
            dir = dir.parentFile ?: return@repeat
        }
        // Fallback: subir un nivel desde el módulo.
        return File(".").absoluteFile.parentFile
    }
}
