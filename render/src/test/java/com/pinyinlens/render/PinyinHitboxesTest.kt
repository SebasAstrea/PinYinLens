package com.pinyinlens.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas JVM del cálculo de zonas táctiles de las etiquetas de pinyin (RF-05),
 * la parte que decide qué rectángulo minúsculo intercepta el hold sin bloquear
 * el resto de la pantalla (RF-01).
 */
class PinyinHitboxesTest {

    private val measure = TextMeasurer { text, size -> text.length * size * 0.6f }

    private fun label(
        text: String = "hǎo",
        word: String = "好",
        x: Float = 100f,
        baselineY: Float = 200f,
        size: Float = 10f,
    ) = PinyinLabel(text, x, baselineY, size, word)

    @Test
    fun boxCoversTheDrawnLabelWithPadding() {
        val boxes = PinyinHitboxes.compute(
            labels = listOf(label()),
            measure = measure,
            density = 1f,
            screenWidth = 1080,
            screenHeight = 1920,
        )
        assertEquals(1, boxes.size)
        val box = boxes.single()
        assertEquals("好", box.word)
        // El centro del texto queda dentro de la zona táctil.
        assertTrue(box.left < 100 && box.right > 100)
        assertTrue(box.top < 200 && box.bottom > 200)
        // Incluye el texto completo más la holgura.
        assertTrue(box.left <= (100 - 9 - 6))
        assertTrue(box.right >= (100 + 9 + 6))
    }

    @Test
    fun enforcesMinimumTouchSize() {
        val box = PinyinHitboxes.compute(
            labels = listOf(label(text = "a", size = 9f)),
            measure = measure,
            density = 1f,
            screenWidth = 1080,
            screenHeight = 1920,
        ).single()
        assertTrue("ancho mínimo", box.width >= PinyinHitboxes.MIN_WIDTH_DP.toInt())
        assertTrue("alto mínimo", box.height >= PinyinHitboxes.MIN_HEIGHT_DP.toInt())
    }

    @Test
    fun clampsToScreenBounds() {
        val box = PinyinHitboxes.compute(
            labels = listOf(label(x = 2f, baselineY = 1900f)),
            measure = measure,
            density = 1f,
            screenWidth = 1080,
            screenHeight = 1920,
        ).single()
        assertTrue(box.left >= 0)
        assertTrue(box.top >= 0)
        assertTrue(box.right <= 1080)
        assertTrue(box.bottom <= 1920)
    }

    @Test
    fun skipsLabelsWithoutWordOrText() {
        val boxes = PinyinHitboxes.compute(
            labels = listOf(
                label(word = ""),
                label(text = ""),
                label(),
            ),
            measure = measure,
            density = 1f,
            screenWidth = 1080,
            screenHeight = 1920,
        )
        assertEquals(1, boxes.size)
    }

    @Test
    fun emptyInputProducesNoTargets() {
        assertTrue(
            PinyinHitboxes.compute(
                labels = emptyList(),
                measure = measure,
                density = 2f,
                screenWidth = 1080,
                screenHeight = 1920,
            ).isEmpty(),
        )
    }
}
