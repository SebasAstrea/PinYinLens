package com.pinyinlens.gesture

import com.pinyinlens.stats.BinomialTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Gate de aceptación de RF-05 para el detector de hold, sobre las muestras que
 * exige el SRS:
 *  - n = 100 pulsaciones sostenidas (duración 5000 ±150 ms, dedo casi quieto):
 *    T_hold = disparos / 100 debe ser >= 0.97, y binomial H0: P<=0.93 rechazada.
 *  - n = 200 gestos de tap rápido (<500 ms) o scroll: 0 falsos positivos.
 *
 * Es determinista (semilla fija). Valida la LÓGICA del detector, que es la que
 * fija T_hold; la inyección física de 300 gestos añadiría ruido del inyector,
 * no del detector. El cableado al servicio se verifica aparte en dispositivo.
 */
class HoldDetectorAcceptanceTest {

    private val rnd = Random(42)

    @Test
    fun holdSuccessRateMeetsThreshold() {
        var fired = 0
        repeat(100) {
            val d = HoldDetector()
            d.onDown(0, 500, 500)
            // Jitter dentro del slop y duración dentro de ±150 de 5000.
            val jx = 500 + rnd.nextInt(-20, 21)
            val jy = 500 + rnd.nextInt(-20, 21)
            d.onMove(jx, jy)
            val dur = 5000L + rnd.nextInt(-150, 151)
            if (d.onTimeCheck(dur)) fired++
        }
        val tHold = fired / 100.0
        val bin = BinomialTest.upperTail(fired, 100, p0 = 0.93)
        println("RF-05 T_hold=$tHold ($fired/100), p=${bin.pValue}, rechazaH0=${bin.rejectsNull}")

        assertTrue("T_hold=$tHold debe ser >= 0.97", tHold >= 0.97)
        assertTrue("debe rechazar H0:P<=0.93 (p=${bin.pValue})", bin.rejectsNull)
    }

    @Test
    fun zeroFalsePositivesOnQuickTapsAndScroll() {
        var falsePositives = 0
        repeat(200) { i ->
            val d = HoldDetector()
            d.onDown(0, 500, 500)
            if (i % 2 == 0) {
                // Tap rápido < 500 ms.
                val dur = rnd.nextLong(30, 500)
                if (d.onUp(dur)) falsePositives++
            } else {
                // Scroll: desplazamiento mayor que el slop, luego up tardío.
                d.onMove(500, 500 + rnd.nextInt(60, 400))
                d.onTimeCheck(5200)
                if (d.onUp(5300)) falsePositives++
            }
        }
        assertEquals("0 falsos positivos exigidos por el SRS", 0, falsePositives)
    }
}
