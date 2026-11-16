package com.pinyinlens.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BinomialTestTest {

    @Test
    fun rejectsNullWhenObservedFarAboveThreshold() {
        // RF-01: H0 p<=0.95. 100/100 éxitos debe rechazar con holgura.
        val r = BinomialTest.upperTail(successes = 100, trials = 100, p0 = 0.95)
        assertTrue(r.rejectsNull)
        assertTrue("p-valor debe ser pequeño", r.pValue <= 0.05)
    }

    @Test
    fun boundaryForRejectionAtN100() {
        // Con n=100 y H0 p<=0.95, hacen falta 99 éxitos para rechazar a alpha=0.05.
        // 98/100 da p-valor > 0.05: NO rechaza. 99/100 sí rechaza.
        val r98 = BinomialTest.upperTail(successes = 98, trials = 100, p0 = 0.95)
        assertFalse("98/100 no alcanza a rechazar H0:p<=0.95 a alpha=0.05", r98.rejectsNull)

        val r99 = BinomialTest.upperTail(successes = 99, trials = 100, p0 = 0.95)
        assertTrue("99/100 debe rechazar H0:p<=0.95 a alpha=0.05", r99.rejectsNull)
    }

    @Test
    fun failsToRejectWhenObservedAtThreshold() {
        // 95/100 == umbral: no debe rechazar H0 (p-valor alto).
        val r = BinomialTest.upperTail(successes = 95, trials = 100, p0 = 0.95)
        assertFalse(r.rejectsNull)
    }

    @Test
    fun pmfSumsToOne() {
        // Suma de toda la cola desde 0 == 1 (coherencia de la distribución).
        val r = BinomialTest.upperTail(successes = 0, trials = 50, p0 = 0.3)
        assertEquals(1.0, r.pValue, 1e-9)
    }

    @Test
    fun observedProportionIsComputed() {
        val r = BinomialTest.upperTail(successes = 97, trials = 100, p0 = 0.93)
        assertEquals(0.97, r.observedProportion, 1e-12)
    }
}
