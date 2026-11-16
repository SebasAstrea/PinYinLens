package com.pinyinlens.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OneSampleTTestTest {

    @Test
    fun rejectsNullWhenMeanWellBelowThreshold() {
        // RF-06: tiempos de ocultamiento muy por debajo de 32 ms.
        val samples = DoubleArray(100) { 1.0 } // 1 ms, con ligera varianza abajo
        val jittered = samples.mapIndexed { i, v -> v + (i % 3) * 0.1 }.toDoubleArray()
        val r = OneSampleTTest.lowerTail(jittered, mu0 = 32.0)
        assertTrue("media ~1 ms << 32 ms debe rechazar H0", r.rejectsNull)
        assertTrue(r.pValue < 0.001)
    }

    @Test
    fun doesNotRejectWhenMeanAboveThreshold() {
        val samples = DoubleArray(50) { 40.0 + (it % 5) }
        val r = OneSampleTTest.lowerTail(samples, mu0 = 32.0)
        assertFalse(r.rejectsNull)
    }

    @Test
    fun meanAndSdComputed() {
        val r = OneSampleTTest.lowerTail(doubleArrayOf(2.0, 4.0, 4.0, 4.0, 5.0, 5.0, 7.0, 9.0), mu0 = 10.0)
        assertEquals(5.0, r.mean, 1e-9)
        assertEquals(2.138, r.sd, 1e-3)
    }

    @Test
    fun pValueNearHalfWhenMeanEqualsMu0() {
        // Media == μ0 => t=0 => p-valor = 0.5.
        val samples = doubleArrayOf(31.0, 32.0, 33.0, 31.0, 33.0) // media 32
        val r = OneSampleTTest.lowerTail(samples, mu0 = 32.0)
        assertEquals(0.5, r.pValue, 1e-6)
    }

    @Test
    fun upperTailRejectsWhenMeanAboveThreshold() {
        // RF-03: CRR por imagen, mayoría en 1.0, frente a H0 mu<=0.90.
        val samples = DoubleArray(60) { if (it % 12 == 0) 0.8 else 1.0 }
        val r = OneSampleTTest.upperTail(samples, mu0 = 0.90)
        assertTrue("media > 0.90 debe rechazar H0", r.rejectsNull)
    }

    @Test
    fun upperTailDoesNotRejectAtThreshold() {
        val samples = doubleArrayOf(0.90, 0.90, 0.90, 0.90, 0.90, 0.90)
        val r = OneSampleTTest.upperTail(samples, mu0 = 0.90)
        assertFalse(r.rejectsNull)
    }
}
