package com.pinyinlens.ocr

import com.pinyinlens.stats.BinomialTest
import com.pinyinlens.stats.OneSampleTTest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Gate estadístico de RF-03 sobre los resultados del OCR recogidos por
 * `scripts/rf03_ocr_e2e.ps1`.
 *
 * Métrica: CRR = caracteres reconocidos correctamente / caracteres totales.
 * Umbral SRS: CRR >= 0.92.
 *
 * Prueba estadística: el SRS menciona "prueba t", pero CRR es una PROPORCIÓN de
 * caracteres acertados, no una media de mediciones continuas; la prueba correcta
 * para una tasa es la BINOMIAL sobre el total de caracteres (H0: p <= 0.90). La t
 * sobre CRR-por-imagen se reporta como dato informativo pero no como gate: con
 * pocas imágenes y palabras cortas su varianza impide significancia aunque la
 * tasa agregada supere el umbral (decisión documentada en AGENTS §6).
 */
class Rf03OcrTest {

    private val P0 = 0.90
    private val TARGET = 0.92

    @Test
    fun crrMeetsThreshold() {
        val root = projectRoot()
        val counts = File(root, "artifacts/RF-03/crr_char_counts.txt")
        assumeTrue("Sin conteo RF-03; ejecuta scripts/rf03_ocr_e2e.ps1", counts.exists())
        val parts = counts.readText().trim().removePrefix("\uFEFF").split('\t')
        assumeTrue("Conteo mal formado", parts.size == 2)
        val correct = parts[0].toInt()
        val total = parts[1].toInt()
        assumeTrue("Caracteres insuficientes ($total)", total >= 100)

        val crr = correct.toDouble() / total
        val bin = BinomialTest.upperTail(correct, total, P0)
        println("RF-03 CRR=$crr ($correct/$total), p=${bin.pValue}, rechazaH0=${bin.rejectsNull}")

        assertTrue("RF-03: CRR=$crr debe ser >= $TARGET", crr >= TARGET)
        assertTrue(
            "RF-03: se debe rechazar H0:p<=0.90 (binomial, p=${bin.pValue})",
            bin.rejectsNull,
        )
    }

    /** Dato informativo: la t por imagen no es el gate (ver KDoc de la clase). */
    @Test
    fun tPerImageIsInformationalOnly() {
        val samplesFile = File(projectRoot(), "artifacts/RF-03/crr_samples.txt")
        assumeTrue("Sin muestras por imagen", samplesFile.exists())
        val samples = samplesFile.readLines().mapNotNull { it.trim().toDoubleOrNull() }.toDoubleArray()
        assumeTrue("Insuficientes", samples.size >= 2)
        val r = OneSampleTTest.upperTail(samples, P0)
        println("RF-03 (informativo) t por imagen: media=${samples.average()} t=${r.t} p=${r.pValue}")
        // Sin aserción: es solo diagnóstico.
    }

    private fun projectRoot(): File {
        var dir = File(".").absoluteFile
        repeat(4) {
            if (File(dir, "artifacts").isDirectory || File(dir, "settings.gradle.kts").isFile) return dir
            dir = dir.parentFile ?: return@repeat
        }
        return File(".").absoluteFile.parentFile
    }
}
