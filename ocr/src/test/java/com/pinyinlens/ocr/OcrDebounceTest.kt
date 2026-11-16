package com.pinyinlens.ocr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrDebounceTest {

    @Test
    fun doesNotFireWithoutTouch() {
        val d = OcrDebounce(400)
        assertFalse(d.shouldFire(10_000))
    }

    @Test
    fun doesNotFireBeforeWindow() {
        val d = OcrDebounce(400)
        d.onTouch(1000)
        assertFalse(d.shouldFire(1300)) // 300 ms < 400
    }

    @Test
    fun firesAfterQuietWindow() {
        val d = OcrDebounce(400)
        d.onTouch(1000)
        assertTrue(d.shouldFire(1400)) // 400 ms exactos
    }

    @Test
    fun firesOnlyOncePerQuietPeriod() {
        val d = OcrDebounce(400)
        d.onTouch(1000)
        assertTrue(d.shouldFire(1400))
        assertFalse(d.shouldFire(1500)) // misma quietud, no repite
    }

    @Test
    fun newTouchReArmsTheTrigger() {
        val d = OcrDebounce(400)
        d.onTouch(1000)
        assertTrue(d.shouldFire(1400))
        d.onTouch(2000)           // nueva actividad
        assertFalse(d.shouldFire(2300))
        assertTrue(d.shouldFire(2400)) // dispara de nuevo tras la nueva quietud
    }

    @Test
    fun resetClearsState() {
        val d = OcrDebounce(400)
        d.onTouch(1000)
        d.reset()
        assertFalse(d.shouldFire(5000))
    }
}
