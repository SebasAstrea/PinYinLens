package com.pinyinlens.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinLayoutTest {

    @Test
    fun fontSizeIsWithinSrsRatioRange() {
        // Hanzi de 100 px de alto, ratio 0.5 => 50 px (dentro de 45–60%).
        val l = PinyinLayout.layoutFor("nǐ", 0, 0, 80, 100, ratio = 0.5f, minTextPx = 9f)
        assertEquals(50f, l.textSizePx, 0.01f)
    }

    @Test
    fun ratioIsClampedToSrsBounds() {
        val tooSmall = PinyinLayout.layoutFor("a", 0, 0, 100, 100, ratio = 0.2f, minTextPx = 1f)
        assertEquals(45f, tooSmall.textSizePx, 0.01f) // clamp a 0.45
        val tooBig = PinyinLayout.layoutFor("a", 0, 0, 100, 100, ratio = 0.9f, minTextPx = 1f)
        assertEquals(60f, tooBig.textSizePx, 0.01f)   // clamp a 0.60
    }

    @Test
    fun respectsMinimumTextSize() {
        // Hanzi muy pequeño: 10 px de alto * 0.5 = 5, pero mínimo 9.
        val l = PinyinLayout.layoutFor("a", 0, 0, 10, 10, ratio = 0.5f, minTextPx = 9f)
        assertEquals(9f, l.textSizePx, 0.01f)
    }

    @Test
    fun labelIsCenteredHorizontally() {
        val l = PinyinLayout.layoutFor("hǎo", 100, 200, 200, 300, minTextPx = 9f)
        assertEquals(150f, l.x, 0.01f) // centro de [100,200]
    }

    @Test
    fun labelSitsAboveTheCharacter() {
        val l = PinyinLayout.layoutFor("hǎo", 0, 200, 100, 300, gapPx = 2f, minTextPx = 9f)
        assertTrue("baseline debe estar por encima del top del Hanzi", l.baselineY < 200)
    }
}
