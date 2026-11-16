package com.pinyinlens.render

import com.pinyinlens.render.PinyinLayout.Box
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinLayoutTest {

    private val MAX = 120f

    /** Medida aproximada: 0.5 em por carácter latino. */
    private val measure = TextMeasurer { text, size -> text.length * size * 0.5f }

    private fun layout(
        boxes: List<Box>,
        min: Float = 9f,
        max: Float = MAX,
        gap: Float = 4f,
        screenBottom: Int = 2000,
        screenLeft: Int = 0,
        screenRight: Int = Int.MAX_VALUE,
    ) = PinyinLayout.layoutAll(
        boxes,
        minTextPx = min,
        maxTextPx = max,
        gapPx = gap,
        measure = measure,
        screenBottomPx = screenBottom,
        screenLeftPx = screenLeft,
        screenRightPx = screenRight,
    )

    @Test
    fun usesTheSourceHanziTextSize() {
        // Caja ancha y pinyin corto: cabe al mismo tamaño que el Hanzi (60 px).
        val l = layout(listOf(Box("nǐ", 0, 300, 400, 380, hanziTextPx = 60f))).single()
        assertEquals(60f, l.textSizePx, 0.01f)
    }

    @Test
    fun neverExceedsTheSourceHanziTextSize() {
        // Pinyin mucho más ancho que la palabra: se reduce, nunca se agranda.
        val l = layout(listOf(Box("zhāng dēng jié cǎi", 0, 300, 120, 380, 60f))).single()
        assertTrue("no supera el tamaño del Hanzi", l.textSizePx <= 60f)
    }

    @Test
    fun labelsInTheSameRowNeverOverlap() {
        // Dos etiquetas vecinas en la misma fila comparten el pasillo: cada una
        // llega como mucho a su mitad, sin solaparse.
        val labels = layout(
            listOf(
                Box("shāng diàn", 0, 300, 200, 380, 40f),
                Box("zhào piàn", 210, 300, 410, 380, 40f),
            ),
        )
        val left = labels.first { it.text == "shāng diàn" }
        val right = labels.first { it.text == "zhào piàn" }
        val leftEdge = left.x - measure.widthOf(left.text, left.textSizePx) / 2f
        val leftRight = left.x + measure.widthOf(left.text, left.textSizePx) / 2f
        val rightEdge = right.x - measure.widthOf(right.text, right.textSizePx) / 2f
        val rightRight = right.x + measure.widthOf(right.text, right.textSizePx) / 2f
        assertTrue("izquierda $leftEdge >= 0", leftEdge >= -0.01f)
        assertTrue("derecha $rightRight <= 410", rightRight <= 410.01f)
        assertTrue("no se tocan: $leftRight <= $rightEdge", leftRight <= rightEdge + 0.01f)
    }

    @Test
    fun usesFreeSpaceToKeepTheSourceSize() {
        // Título corto (3 Hanzi) con espacio libre al lado: el pinyin es más
        // ancho que su caja, pero conserva el tamaño del Hanzi en vez de
        // encogerse o desaparecer.
        val l = layout(listOf(Box("zhǔ píng mù", 100, 40, 238, 100, 40f))).single()
        assertEquals(40f, l.textSizePx, 0.01f)
        // Arranca en el borde izquierdo del Hanzi (crece hacia la derecha).
        val left = l.x - measure.widthOf(l.text, l.textSizePx) / 2f
        assertEquals(100f, left, 0.01f)
    }

    @Test
    fun paddedSingleLineGoesBelowLikeTheRest() {
        // TextView de una sola línea con padding generoso (alto = 2.0 em) y otro
        // nodo pegado debajo: debe anclarse a la tinta y dibujar DEBAJO, no
        // saltar encima del Hanzi.
        val labels = layout(
            listOf(
                Box("píng mù xiè suǒ lèi xíng", 68, 329, 372, 399, 35f),
                Box("", 68, 399, 341, 453, 34f),
            ),
            gap = 4f,
            screenTop = 290,
            screenBottom = 2208,
        )
        val l = labels.single()
        assertEquals(Placement.BELOW, l.placement)
        val bottom = l.baselineY + PinyinLayout.DESCENT_RATIO * l.textSizePx
        assertTrue("no pisa el enlace: $bottom", bottom <= 409.01f)
    }

    @Test
    fun neverDrawsBeyondTheScreenBounds() {
        // Etiqueta junto al borde derecho: se reduce lo justo para no salirse.
        val l = layout(
            listOf(Box("zhāng dēng jié cǎi", 900, 300, 1000, 380, 40f)),
            screenRight = 1080,
        ).single()
        val half = measure.widthOf(l.text, l.textSizePx) / 2f
        assertTrue("borde derecho: ${l.x + half}", l.x + half <= 1080.01f)
        assertTrue("borde izquierdo: ${l.x - half}", l.x - half >= -0.01f)
    }

    @Test
    fun shortTitleInDenseRowKeepsPinyinAtFloor() {
        // Fila de lista: título de 3 Hanzi (caja estrecha) pegado al subtítulo.
        // Antes desaparecía porque su pinyin no cabía a 9 sp en la caja; ahora
        // se dibuja al suelo (~40% del título), como el resto de filas densas.
        val labels = layout(
            listOf(
                Box("zhǔ píng mù", 180, 728, 318, 798, 46f),
                Box("", 180, 798, 508, 852, 34f),
            ),
            gap = 4f,
            screenBottom = 2000,
        )
        val l = labels.single()
        assertEquals(Placement.BELOW, l.placement)
        assertTrue(
            "tamaño ${l.textSizePx} >= suelo",
            l.textSizePx >= 9f * PinyinLayout.TIGHT_FLOOR - 0.01f,
        )
        // Tinta del subtítulo: centro 825 ± 0.5 em (34 px) = 808.
        val bottom = l.baselineY + PinyinLayout.DESCENT_RATIO * l.textSizePx
        assertTrue("no pisa el subtítulo: $bottom", bottom <= 808.01f)
    }

    @Test
    fun placesLabelRightBelowTheHanzi() {
        // Caja con relleno vertical: la etiqueta se ancla a la tinta (340±20),
        // no al box (300..380), para no descolgarse de su Hanzi.
        val l = layout(listOf(Box("nǐ hǎo", 0, 300, 200, 380, 40f))).single()
        assertEquals(Placement.BELOW, l.placement)
        assertEquals(100f, l.x, 0.01f)
        val top = l.baselineY - PinyinLayout.ASCENT_RATIO * l.textSizePx
        assertTrue("pegado a la tinta: $top", top >= 360f + 4f - 0.01f)
        assertEquals(40f, l.textSizePx, 0.01f)
    }

    @Test
    fun usesTheLineLeadingToStayBelowTheHanzi() {
        // Fila de lista: título 40..100 (texto 40 px) y subtítulo pegado en 100.
        // Aunque los boxes están pegados, la tinta deja hueco: el pinyin va
        // debajo, no encima.
        val labels = layout(
            listOf(
                Box("lián jiē", 0, 40, 200, 100, 40f),
                Box("", 0, 100, 200, 154, 34f),
            ),
            gap = 3f,
        )
        val l = labels.single()
        assertEquals(Placement.BELOW, l.placement)
        val bottom = l.baselineY + PinyinLayout.DESCENT_RATIO * l.textSizePx
        assertTrue("no pisa el subtítulo: $bottom", bottom <= 110f + 0.01f)
    }

    @Test
    fun shrinksToFitTightRow() {
        // Fila apretada: la etiqueta de arriba debe caber entre su caja y la
        // siguiente fila, reduciendo el tamaño si hace falta.
        val labels = layout(
            listOf(
                Box("shàng", 0, 40, 200, 100, 60f),
                Box("xià", 0, 116, 200, 176, 60f),
            ),
            gap = 3f,
        )
        val upper = labels.first { it.text == "shàng" }
        assertEquals(Placement.BELOW, upper.placement)
        val bottom = upper.baselineY + PinyinLayout.DESCENT_RATIO * upper.textSizePx
        assertTrue("no invade la fila inferior", bottom <= 116f + 0.01f)
    }

    @Test
    fun sharedGapIsSplitBetweenAdjacentLabels() {
        // La fila de arriba dibuja debajo y la de abajo encima, en el mismo
        // hueco: deben repartírselo y no solaparse.
        val labels = layout(
            listOf(
                Box("shàng", 0, 40, 200, 100, 40f),
                Box("xià", 0, 160, 200, 220, 40f),
            ),
            gap = 3f,
            // Fila inferior casi pegada al borde: no puede dibujar debajo.
            screenBottom = 212,
        )
        val upper = labels.first { it.text == "shàng" }
        val lower = labels.first { it.text == "xià" }
        assertEquals(Placement.BELOW, upper.placement)
        assertEquals(Placement.ABOVE, lower.placement)
        val upperBottom = upper.baselineY + PinyinLayout.DESCENT_RATIO * upper.textSizePx
        val lowerTop = lower.baselineY - PinyinLayout.ASCENT_RATIO * lower.textSizePx
        assertTrue("techo $upperBottom <= $lowerTop", upperBottom <= lowerTop + 0.01f)
    }

    @Test
    fun fallsBackAboveWhenNoRoomBelow() {
        // Bloque alto (p. ej. párrafo) pegado al borde inferior: sin hueco real.
        val l = layout(
            listOf(Box("dǐ", 0, 1000, 200, 1180, 180f)),
            min = 20f,
            screenBottom = 1180,
        ).single()
        assertEquals(Placement.ABOVE, l.placement)
        assertTrue(l.baselineY <= 1000)
    }

    @Test
    fun keepsMinimumLegibleSize() {
        val l = layout(listOf(Box("a", 0, 500, 20, 510, 4f))).single()
        assertTrue(l.textSizePx >= 9f)
    }

    @Test
    fun capsAtSafetyMaximum() {
        val l = layout(listOf(Box("nǐ", 0, 300, 400, 380, 200f)), max = 48f).single()
        assertEquals(48f, l.textSizePx, 0.01f)
    }

    @Test
    fun estimatesSizeWhenSourceIsUnknown() {
        val l = layout(listOf(Box("nǐ", 0, 300, 400, 380))).single()
        assertEquals(80f * PinyinLayout.FALLBACK_RATIO, l.textSizePx, 0.01f)
    }

    @Test
    fun skipsLabelsThatCannotFitEvenAtMinimumSize() {
        // Párrafo largo en un único nodo (sin división por palabras): ni al
        // mínimo legible cabe en su ancho; no se dibuja (una etiqueta desbordada
        // por la pantalla sería peor).
        val long = "gēn jù nín zhèng zài zhí xíng de cāo zuò nín suǒ zài de wèi zhì"
        val huge = "$long $long $long $long"
        val labels = layout(
            listOf(Box(huge, 68, 710, 1012, 842, 34f)),
            screenRight = 1080,
        )
        assertTrue("debe descartarse: ${labels.size}", labels.isEmpty())
    }

    @Test
    fun skipsWhenNeitherSideHasRoomEvenAtMinimumSize() {
        // Filas contiguas sin hueco: la etiqueta no cabe ni debajo ni arriba sin
        // pisar la fila vecina. No se dibuja (sin solapamientos, RNF-04).
        val labels = layout(
            listOf(
                // Bloques de una sola pieza pegados: tinta contra tinta.
                Box("shàng", 0, 100, 200, 180, 80f),
                Box("xià", 0, 180, 200, 260, 80f),
            ),
            min = 10f,
            gap = 4f,
            screenTop = 100,
            screenBottom = 260,
        )
        assertTrue(labels.isEmpty())
    }

    @Test
    fun blankPinyinIsSkipped() {
        val boxes = listOf(Box("", 0, 0, 100, 100), Box("hǎo", 0, 200, 100, 300))
        val labels = layout(boxes)
        assertEquals(1, labels.size)
        assertEquals("hǎo", labels.single().text)
    }
}
