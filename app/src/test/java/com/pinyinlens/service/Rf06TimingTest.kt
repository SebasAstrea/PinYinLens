package com.pinyinlens.service

import com.pinyinlens.stats.OneSampleTTest
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Gate estadístico de RF-06 sobre las muestras t_hide recogidas por
 * `scripts/rf06_scroll_e2e.ps1` en dispositivo (artifacts/RF-06/hide_samples.txt).
 *
 * Aplica la prueba t de una muestra del SRS:
 *   H0: μ_hide >= 32 ms  vs  H1: μ_hide < 32 ms,  α = 0.05.
 * Exige n >= 100 (criterio del SRS) y rechazar H0, además de μ_hide <= 32 ms.
 *
 * Si no hay muestras (no se corrió el e2e), el test se omite (assume), no falla,
 * para no bloquear el build en entornos sin dispositivo.
 */
class Rf06TimingTest {

    private val MU0 = 32.0
    private val MIN_N = 100

    @Test
    fun hideTimeBelowBudget() {
        val file = File(projectRoot(), "artifacts/RF-06/hide_samples.txt")
        assumeTrue("Sin muestras RF-06; ejecuta scripts/rf06_scroll_e2e.ps1", file.exists())

        val samples = file.readLines()
            .mapNotNull { it.trim().toDoubleOrNull() }
            .toDoubleArray()
        assumeTrue("Muestras insuficientes (${samples.size})", samples.size >= 2)

        val r = OneSampleTTest.lowerTail(samples, MU0)
        println("RF-06 n=${r.n} mean=${r.mean} sd=${r.sd} t=${r.t} p=${r.pValue} rechazaH0=${r.rejectsNull}")

        assertTrue("RF-06: se requieren n>=100 muestras (n=${r.n})", r.n >= MIN_N)
        assertTrue("RF-06: μ_hide=${r.mean} ms debe ser <= 32 ms", r.mean <= MU0)
        assertTrue(
            "RF-06: se debe rechazar H0:μ>=32ms (t=${r.t}, p=${r.pValue})",
            r.rejectsNull,
        )
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
