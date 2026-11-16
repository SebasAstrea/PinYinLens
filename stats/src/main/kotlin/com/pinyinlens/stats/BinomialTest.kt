package com.pinyinlens.stats

import kotlin.math.ln
import kotlin.math.exp

/**
 * Prueba binomial unilateral (cola derecha) usada por los criterios de
 * aceptación del SRS que evalúan una proporción (RF-01, RF-05).
 *
 * Contrasta H0: p <= p0  frente a  H1: p > p0.
 * El p-valor es P(X >= k | n, p0), con X ~ Binomial(n, p0). Se rechaza H0 si
 * p-valor <= alfa (por defecto 0.05).
 *
 * Implementación exacta (suma de la cola de la PMF binomial), sin dependencias
 * externas, para que la validación estadística sea reproducible en JVM.
 */
object BinomialTest {

    data class Result(
        val successes: Int,
        val trials: Int,
        val p0: Double,
        val alpha: Double,
        val pValue: Double,
        val observedProportion: Double,
    ) {
        /** True si se rechaza H0 (el rendimiento supera el umbral p0). */
        val rejectsNull: Boolean get() = pValue <= alpha
    }

    /**
     * @param successes k, número de éxitos observados.
     * @param trials n, tamaño de muestra (> 0).
     * @param p0 proporción bajo H0 (0 < p0 < 1).
     * @param alpha nivel de significancia.
     */
    fun upperTail(
        successes: Int,
        trials: Int,
        p0: Double,
        alpha: Double = 0.05,
    ): Result {
        require(trials > 0) { "trials debe ser > 0" }
        require(successes in 0..trials) { "successes fuera de rango" }
        require(p0 in 0.0..1.0) { "p0 fuera de [0,1]" }

        var pValue = 0.0
        for (x in successes..trials) {
            pValue += pmf(x, trials, p0)
        }
        // Clamp por acumulación de error de punto flotante.
        pValue = pValue.coerceIn(0.0, 1.0)

        return Result(
            successes = successes,
            trials = trials,
            p0 = p0,
            alpha = alpha,
            pValue = pValue,
            observedProportion = successes.toDouble() / trials,
        )
    }

    /** PMF binomial C(n,k) p^k (1-p)^(n-k), calculada en log para estabilidad. */
    private fun pmf(k: Int, n: Int, p: Double): Double {
        if (p <= 0.0) return if (k == 0) 1.0 else 0.0
        if (p >= 1.0) return if (k == n) 1.0 else 0.0
        val logProb = logChoose(n, k) + k * ln(p) + (n - k) * ln(1.0 - p)
        return exp(logProb)
    }

    private fun logChoose(n: Int, k: Int): Double =
        logFactorial(n) - logFactorial(k) - logFactorial(n - k)

    private fun logFactorial(n: Int): Double {
        var acc = 0.0
        for (i in 2..n) acc += ln(i.toDouble())
        return acc
    }
}
