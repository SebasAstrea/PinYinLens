package com.pinyinlens.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProportionZTestTest {

    @Test
    fun rejectsNullWhenObservedAboveThreshold() {
        // RF-04: H0 p<=0.91. 0.94 sobre n=500 debe rechazar.
        val r = ProportionZTest.upperTail(successes = 470, trials = 500, p0 = 0.91)
        assertTrue("0.94 vs 0.91 en n=500 debe rechazar H0", r.rejectsNull)
    }

    @Test
    fun doesNotRejectWhenObservedAtThreshold() {
        // Exactamente en el umbral: Z=0, p-valor=0.5, no rechaza.
        val r = ProportionZTest.upperTail(successes = 455, trials = 500, p0 = 0.91)
        assertFalse(r.rejectsNull)
        assertEquals(0.5, r.pValue, 1e-6)
    }

    @Test
    fun zIsZeroAtThreshold() {
        val r = ProportionZTest.upperTail(successes = 91, trials = 100, p0 = 0.91)
        assertEquals(0.0, r.z, 1e-9)
    }

    @Test
    fun observedProportionComputed() {
        val r = ProportionZTest.upperTail(successes = 470, trials = 500, p0 = 0.91)
        assertEquals(0.94, r.observedProportion, 1e-9)
    }

    @Test
    fun normalCdfApproxIsReasonable() {
        // Z=1.645 => p-valor ~ 0.05 (valor crítico unilateral clásico).
        val r = ProportionZTest.upperTail(successes = 100, trials = 100, p0 = 0.5)
        assertTrue(r.pValue < 1e-6) // 100/100 muy por encima de 0.5
    }
}
