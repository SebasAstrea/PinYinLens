package com.pinyinlens.render

import kotlin.math.roundToInt

/**
 * Zona táctil de una etiqueta de pinyin ya dibujada (RF-05), en coordenadas de
 * pantalla. La usa el overlay para crear una ventana interactiva minúscula solo
 * encima de la etiqueta; el resto de la pantalla queda pass-through (RF-01).
 */
data class PinyinHitbox(
    val word: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
}

/**
 * Cálculo puro de las zonas táctiles de las etiquetas (sin Android, testeable
 * en JVM). Se deriva de la MISMA métrica usada para dibujar ([PinyinLabel]), de
 * modo que el dedo debe caer sobre el texto visible.
 *
 * Se añade una holgura pequeña y un tamaño mínimo para que el hold de 5 s sea
 * posible en pantallas de alta densidad sin cubrir más que el pinyin. Las cajas
 * se recortan a los límites de la pantalla.
 */
object PinyinHitboxes {

    /** Holgura alrededor del texto dibujado, en dp. */
    const val PADDING_DP = 6f

    /** Tamaño mínimo de la zona táctil, en dp (ancho × alto). */
    const val MIN_WIDTH_DP = 32f
    const val MIN_HEIGHT_DP = 28f

    fun compute(
        labels: List<PinyinLabel>,
        measure: TextMeasurer,
        density: Float,
        screenWidth: Int,
        screenHeight: Int,
    ): List<PinyinHitbox> {
        if (labels.isEmpty()) return emptyList()
        val pad = PADDING_DP * density
        val minW = MIN_WIDTH_DP * density
        val minH = MIN_HEIGHT_DP * density
        val result = ArrayList<PinyinHitbox>(labels.size)
        for (label in labels) {
            if (label.text.isBlank() || label.word.isBlank()) continue
            val width = measure.widthOf(label.text, label.textSizePx).takeIf { it > 0f }
                ?: continue
            var left = label.x - width / 2f - pad
            var right = label.x + width / 2f + pad
            var top = label.baselineY - PinyinLayout.ASCENT_RATIO * label.textSizePx - pad
            var bottom = label.baselineY + PinyinLayout.DESCENT_RATIO * label.textSizePx + pad

            val h = bottom - top
            if (h < minH) {
                val grow = (minH - h) / 2f
                top -= grow
                bottom += grow
            }
            val w = right - left
            if (w < minW) {
                val grow = (minW - w) / 2f
                left -= grow
                right += grow
            }

            val l = left.roundToInt().coerceIn(0, screenWidth)
            val t = top.roundToInt().coerceIn(0, screenHeight)
            val r = right.roundToInt().coerceIn(0, screenWidth)
            val b = bottom.roundToInt().coerceIn(0, screenHeight)
            if (r <= l || b <= t) continue
            result.add(PinyinHitbox(label.word, l, t, r, b))
        }
        return result.distinct()
    }
}
