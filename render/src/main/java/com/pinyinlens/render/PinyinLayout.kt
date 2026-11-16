package com.pinyinlens.render

/**
 * Una etiqueta de pinyin ya posicionada para dibujar sobre el Hanzi (RF-04 +
 * RNF-04). Coordenadas en píxeles de pantalla.
 */
data class PinyinLabel(
    val text: String,
    val x: Float,          // centro horizontal del texto
    val baselineY: Float,  // línea base del texto
    val textSizePx: Float,
)

/**
 * Cálculo puro del layout de las etiquetas de pinyin sobre los caracteres
 * (RF-04/render + RNF-04). Sin dependencias de Android, testeable en JVM.
 *
 * Reglas del SRS (RNF-04):
 *  - Tamaño de fuente entre 45% y 60% de la altura del carácter Hanzi.
 *  - Mínimo 9 sp (se pasa [minTextPx] ya convertido desde sp).
 *  - El pinyin se dibuja ENCIMA del carácter, centrado horizontalmente, para
 *    quedar alineado con un error pequeño (objetivo RF-02: <= 4 dp).
 */
object PinyinLayout {

    const val MIN_RATIO = 0.45f
    const val MAX_RATIO = 0.60f

    /**
     * Calcula la etiqueta para un carácter/bloque cuyo rectángulo en pantalla es
     * [left,top,right,bottom].
     *
     * @param ratio proporción del tamaño de fuente respecto a la altura (0.45–0.60).
     * @param minTextPx tamaño mínimo en px (equivalente a 9 sp).
     * @param gapPx separación vertical entre el pinyin y el borde superior del Hanzi.
     */
    fun layoutFor(
        text: String,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        ratio: Float = 0.5f,
        minTextPx: Float = 24f,
        gapPx: Float = 2f,
    ): PinyinLabel {
        val height = (bottom - top).coerceAtLeast(1)
        val clampedRatio = ratio.coerceIn(MIN_RATIO, MAX_RATIO)
        val size = (height * clampedRatio).coerceAtLeast(minTextPx)
        val centerX = (left + right) / 2f
        // El pinyin se sitúa sobre el carácter: su línea base queda por encima del
        // borde superior del Hanzi, dejando un pequeño hueco.
        val baseline = top - gapPx
        return PinyinLabel(text = text, x = centerX, baselineY = baseline, textSizePx = size)
    }
}
