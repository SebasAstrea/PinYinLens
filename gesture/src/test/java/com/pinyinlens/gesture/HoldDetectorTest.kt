package com.pinyinlens.gesture

import com.pinyinlens.gesture.HoldDetector.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HoldDetectorTest {

    @Test
    fun firesAfterFiveSecondsHeldStill() {
        val d = HoldDetector()
        d.onDown(0, 100, 100)
        assertTrue(d.onTimeCheck(5000))
        assertEquals(State.FIRED, d.state)
    }

    @Test
    fun firesWithinLowerTolerance() {
        // 4850 ms (5000 - 150) ya debe disparar: tolerancia ±150.
        val d = HoldDetector()
        d.onDown(0, 100, 100)
        assertTrue(d.onTimeCheck(4850))
    }

    @Test
    fun doesNotFireBeforeDuration() {
        val d = HoldDetector()
        d.onDown(0, 100, 100)
        assertFalse(d.onTimeCheck(4000))
        assertEquals(State.TRACKING, d.state)
    }

    @Test
    fun quickTapIsNotHold() {
        // Tap de 200 ms: up muy pronto, no es hold (0 falsos positivos).
        val d = HoldDetector()
        d.onDown(0, 100, 100)
        assertFalse(d.onUp(200))
        assertEquals(State.IDLE, d.state)
    }

    @Test
    fun fastTapUnder500msNeverFires() {
        // Barrido de taps rápidos: ninguno dispara.
        for (dur in longArrayOf(50, 120, 300, 499)) {
            val d = HoldDetector()
            d.onDown(0, 10, 10)
            assertFalse("tap de $dur ms no debe disparar", d.onUp(dur))
        }
    }

    @Test
    fun scrollGestureCancelsHold() {
        // Desplazamiento mayor que el slop => cancelado (es scroll, no hold).
        val d = HoldDetector()
        d.onDown(0, 100, 100)
        assertEquals(State.CANCELLED, d.onMove(100, 400))
        // Aunque pase el tiempo, no dispara tras cancelar.
        assertFalse(d.onTimeCheck(6000))
        assertFalse(d.onUp(6000))
    }

    @Test
    fun smallJitterWithinSlopDoesNotCancel() {
        val d = HoldDetector()
        d.onDown(0, 100, 100)
        assertEquals(State.TRACKING, d.onMove(110, 115)) // dentro del slop (24)
        assertTrue(d.onTimeCheck(5000))
    }

    @Test
    fun upAfterDurationCountsAsHoldWithoutTimeCheck() {
        // Si no hubo onTimeCheck pero el up llega tras 5 s, cuenta como hold.
        val d = HoldDetector()
        d.onDown(0, 100, 100)
        assertTrue(d.onUp(5100))
    }

    @Test
    fun resetReturnsToIdle() {
        val d = HoldDetector()
        d.onDown(0, 100, 100)
        d.onTimeCheck(5000)
        d.reset()
        assertEquals(State.IDLE, d.state)
    }
}
