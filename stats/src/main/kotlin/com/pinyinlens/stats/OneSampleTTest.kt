package com.pinyinlens.stats

import kotlin.math.sqrt

/**
 * Prueba t de Student de una muestra, unilateral (cola izquierda), para los
 * criterios del SRS sobre una media (RF-02 error de alineación, RF-06 t_hide).
 *
 * Contrasta H0: μ >= μ0  vs  H1: μ < μ0, con
 *   t = (x̄ - μ0) / (s / sqrt(n))
 * y p-valor = P(T <= t) con n-1 grados de libertad. Se rechaza H0 si p <= alfa.
 *
 * El p-valor se obtiene de la CDF de la t de Student vía la función beta
 * incompleta regularizada, sin dependencias externas.
 */
object OneSampleTTest {

    data class Result(
        val n: Int,
        val mean: Double,
        val sd: Double,
        val mu0: Double,
        val alpha: Double,
        val t: Double,
        val df: Int,
        val pValue: Double,
    ) {
        /** True si se rechaza H0 (la media es menor que el umbral μ0). */
        val rejectsNull: Boolean get() = pValue <= alpha
    }

    /** @param samples observaciones (n >= 2). @param mu0 umbral bajo H0. */
    fun lowerTail(samples: DoubleArray, mu0: Double, alpha: Double = 0.05): Result {
        require(samples.size >= 2) { "se requieren >= 2 observaciones" }
        val n = samples.size
        val mean = samples.average()
        val variance = samples.sumOf { (it - mean) * (it - mean) } / (n - 1)
        val sd = sqrt(variance)
        val df = n - 1
        val se = sd / sqrt(n.toDouble())
        val t = if (se == 0.0) {
            if (mean < mu0) Double.NEGATIVE_INFINITY else Double.POSITIVE_INFINITY
        } else {
            (mean - mu0) / se
        }
        val pValue = studentCdf(t, df)
        return Result(n, mean, sd, mu0, alpha, t, df, pValue)
    }

    /** CDF de la t de Student: P(T <= t) con df grados de libertad. */
    private fun studentCdf(t: Double, df: Int): Double {
        if (t == Double.POSITIVE_INFINITY) return 1.0
        if (t == Double.NEGATIVE_INFINITY) return 0.0
        val x = df / (df + t * t)
        val ib = 0.5 * regularizedIncompleteBeta(x, df / 2.0, 0.5)
        return if (t > 0) 1.0 - ib else ib
    }

    /** Beta incompleta regularizada I_x(a,b) por fracción continua (Lentz). */
    private fun regularizedIncompleteBeta(x: Double, a: Double, b: Double): Double {
        if (x <= 0.0) return 0.0
        if (x >= 1.0) return 1.0
        val lbeta = lgamma(a) + lgamma(b) - lgamma(a + b)
        val front = kotlin.math.exp(
            a * kotlin.math.ln(x) + b * kotlin.math.ln(1.0 - x) - lbeta,
        ) / a
        return front * betaContinuedFraction(x, a, b)
    }

    private fun betaContinuedFraction(x: Double, a: Double, b: Double): Double {
        val tiny = 1e-30
        var c = 1.0
        var d = 1.0 - (a + b) * x / (a + 1.0)
        if (kotlin.math.abs(d) < tiny) d = tiny
        d = 1.0 / d
        var result = d
        for (m in 1..200) {
            val m2 = 2 * m
            var aa = m * (b - m) * x / ((a + m2 - 1) * (a + m2))
            d = 1.0 + aa * d; if (kotlin.math.abs(d) < tiny) d = tiny
            c = 1.0 + aa / c; if (kotlin.math.abs(c) < tiny) c = tiny
            d = 1.0 / d; result *= d * c
            aa = -(a + m) * (a + b + m) * x / ((a + m2) * (a + m2 + 1))
            d = 1.0 + aa * d; if (kotlin.math.abs(d) < tiny) d = tiny
            c = 1.0 + aa / c; if (kotlin.math.abs(c) < tiny) c = tiny
            d = 1.0 / d
            val del = d * c
            result *= del
            if (kotlin.math.abs(del - 1.0) < 1e-12) break
        }
        return result
    }

    /** ln Γ(x) (Lanczos). */
    private fun lgamma(x: Double): Double {
        val g = 7.0
        val c = doubleArrayOf(
            0.99999999999980993, 676.5203681218851, -1259.1392167224028,
            771.32342877765313, -176.61502916214059, 12.507343278686905,
            -0.13857109526572012, 9.9843695780195716e-6, 1.5056327351493116e-7,
        )
        if (x < 0.5) {
            return kotlin.math.ln(Math.PI / kotlin.math.sin(Math.PI * x)) - lgamma(1.0 - x)
        }
        var xx = x - 1.0
        var a = c[0]
        val tmp = xx + g + 0.5
        for (i in 1 until c.size) a += c[i] / (xx + i)
        return 0.5 * kotlin.math.ln(2 * Math.PI) + (xx + 0.5) * kotlin.math.ln(tmp) - tmp + kotlin.math.ln(a)
    }
}
