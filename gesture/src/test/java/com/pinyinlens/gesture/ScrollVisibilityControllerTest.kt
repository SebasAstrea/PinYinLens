package com.pinyinlens.gesture

import com.pinyinlens.gesture.ScrollVisibilityController.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScrollVisibilityControllerTest {

    @Test
    fun startsVisible() {
        assertEquals(Visibility.VISIBLE, ScrollVisibilityController().visibility)
    }

    @Test
    fun firstScrollHidesAndReportsChange() {
        val c = ScrollVisibilityController(quietWindowMs = 180)
        assertTrue(c.onScroll(1000))
        assertEquals(Visibility.HIDDEN, c.visibility)
    }

    @Test
    fun subsequentScrollsDoNotReportChange() {
        val c = ScrollVisibilityController(quietWindowMs = 180)
        assertTrue(c.onScroll(1000))
        assertFalse(c.onScroll(1010)) // ya oculta, sin cambio
        assertEquals(Visibility.HIDDEN, c.visibility)
    }

    @Test
    fun doesNotRegenerateBeforeQuietWindow() {
        val c = ScrollVisibilityController(quietWindowMs = 180)
        c.onScroll(1000)
        assertFalse(c.onQuietCheck(1100)) // solo 100 ms de reposo
        assertEquals(Visibility.HIDDEN, c.visibility)
    }

    @Test
    fun regeneratesAfterQuietWindow() {
        val c = ScrollVisibilityController(quietWindowMs = 180)
        c.onScroll(1000)
        assertTrue(c.onQuietCheck(1180)) // 180 ms exactos
        assertEquals(Visibility.VISIBLE, c.visibility)
    }

    @Test
    fun continuousScrollKeepsHidden() {
        val c = ScrollVisibilityController(quietWindowMs = 180)
        c.onScroll(1000)
        // Scrolls encadenados reinician el reloj de reposo.
        c.onScroll(1100)
        assertFalse(c.onQuietCheck(1250)) // 150 ms desde el último scroll
        c.onScroll(1300)
        assertFalse(c.onQuietCheck(1400)) // 100 ms desde el último
        assertEquals(Visibility.HIDDEN, c.visibility)
        // Al fin se detiene.
        assertTrue(c.onQuietCheck(1300 + 180))
        assertEquals(Visibility.VISIBLE, c.visibility)
    }

    @Test
    fun quietCheckWhenVisibleIsNoOp() {
        val c = ScrollVisibilityController()
        assertFalse(c.onQuietCheck(5000))
        assertEquals(Visibility.VISIBLE, c.visibility)
    }

    @Test
    fun resetReturnsToVisible() {
        val c = ScrollVisibilityController()
        c.onScroll(1000)
        c.reset()
        assertEquals(Visibility.VISIBLE, c.visibility)
    }
}
