package com.pinyinlens.stats

import kotlin.math.sqrt

/**
 * Prueba Z de proporciones unilateral (cola derecha) para el criterio de
 * aceptación de RF-04.
 *
 * Contrasta H0: p <= p0  vs  H1: p > p0, con el estadístico
 *   Z = (p_hat - p0) / sqrt(p0 (1 - p0) / n)
 * y p-valor = 1 - Φ(Z). Se rechaza H0 si p-valor <= alfa.
 *
 * Es la aproximación normal que indica el SRS para A_pinyin (n grande, 500).
 */
object ProportionZTest {

    data class Result(
        val successes: Int,
        val trials: Int,
        val p0: Double,
        val alpha: Double,
        val z: Double,
        val pValue: Double,
        val observedProportion: Double,
    ) {
        val rejectsNull: Boolean get() = pValue <= alpha
    }

    fun upperTail(
        successes: Int,
        trials: Int,
        p0: Double,
        alpha: Double = 0.05,
    ): Result {
        require(trials > 0) { "trials debe ser > 0" }
        require(successes in 0..trials) { "successes fuera de rango" }
        require(p0 in 0.0..1.0) { "p0 fuera de [0,1]" }

        val pHat = successes.toDouble() / trials
        val se = sqrt(p0 * (1.0 - p0) / trials)
        val z = if (se == 0.0) Double.POSITIVE_INFINITY else (pHat - p0) / se
        val pValue = 1.0 - normalCdf(z)

        return Result(successes, trials, p0, alpha, z, pValue, pHat)
    }

    /** Φ(x): CDF normal estándar vía erf (aproximación de Abramowitz–Stegun 7.1.26). */
    private fun normalCdf(x: Double): Double = 0.5 * (1.0 + erf(x / sqrt(2.0)))

    private fun erf(x: Double): Double {
        val sign = if (x < 0) -1.0 else 1.0
        val ax = kotlin.math.abs(x)
        val t = 1.0 / (1.0 + 0.3275911 * ax)
        val y = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t
            - 0.284496736) * t + 0.254829592) * t * kotlin.math.exp(-ax * ax)
        return sign * y
    }
}
