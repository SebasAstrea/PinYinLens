package com.pinyinlens.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {

    @Test
    fun blackOnWhiteIsMaxContrast() {
        // 21:1 es el máximo WCAG.
        assertEquals(21.0, Contrast.ratio(0, 0, 0, 255, 255, 255), 0.1)
    }

    @Test
    fun blackWhitePairMeetsAA() {
        assertTrue(Contrast.meetsAA(0, 0, 0, 255, 255, 255))
    }

    @Test
    fun lowContrastPairFailsAA() {
        // Gris medio sobre gris medio: no cumple.
        assertFalse(Contrast.meetsAA(128, 128, 128, 140, 140, 140))
    }

    @Test
    fun whiteTextPrefersBlackStroke() {
        // Texto blanco: el contorno negro da más contraste que el blanco.
        assertTrue(Contrast.preferBlackStroke(255, 255, 255))
    }

    @Test
    fun blackTextPrefersWhiteStroke() {
        assertFalse(Contrast.preferBlackStroke(0, 0, 0))
    }

    @Test
    fun yellowTextPrefersBlackStroke() {
        // Amarillo (usado en la tarjeta) es claro -> contorno negro.
        assertTrue(Contrast.preferBlackStroke(255, 213, 79))
    }
}
