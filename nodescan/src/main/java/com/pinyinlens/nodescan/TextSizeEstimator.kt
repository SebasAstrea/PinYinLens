package com.pinyinlens.nodescan

import com.pinyinlens.pinyin.CjkText

/**
 * Estima el tamaño de fuente (em, en px) de un nodo de texto a partir de su
 * caja en pantalla, sin IPC.
 *
 * Las cajas de `AccessibilityNodeInfo` de un `TextView` ajustan su ancho al
 * texto, así que `ancho / unidades_de_avance` aproxima el tamaño de fuente. Se
 * descartan las cajas donde la estimación no es fiable (texto centrado en una
 * caja ancha, iconos con etiqueta, contenedores altos): en esos casos el
 * llamador debe preguntar al framework (`ExtraRenderingInfo`) o usar el
 * fallback del layout.
 *
 * Lógica pura, testeable en JVM.
 */
object TextSizeEstimator {

    /** Fracción del ancho a partir de la cual una caja se considera "alta". */
    const val TALL_BOX_RATIO = 0.8f

    /**
     * Por encima de este em la estimación geométrica deja de ser fiable (títulos
     * gigantes o iconos de launcher con etiqueta); se delega al framework.
     * ~40 sp en un móvil de densidad 2.75.
     */
    const val MAX_PLAUSIBLE_EM = 110f

    /**
     * @return tamaño de fuente estimado en px, o 0 si no es fiable.
     */
    fun estimate(text: CharSequence?, widthPx: Int, heightPx: Int): Float {
        if (text.isNullOrEmpty() || widthPx <= 0 || heightPx <= 0) return 0f
        // Caja más alta que ancha: icono + etiqueta, tarjeta o texto en columna.
        if (heightPx > widthPx * TALL_BOX_RATIO) return 0f

        val units = advanceUnits(text)
        if (units <= 0f) return 0f

        val em = widthPx / units
        // El em de una línea nunca supera la altura de su caja; si lo supera, el
        // texto no ocupa todo el ancho (centrado, alineado a la derecha, padding).
        if (em > heightPx) return 0f
        // Un em enorme delata una etiqueta centrada sobre un icono o una caja
        // que no corresponde a una línea de texto.
        if (em > MAX_PLAUSIBLE_EM) return 0f
        return em
    }

    /** Ancho aproximado del texto en em (1 em por Hanzi). */
    fun advanceUnits(text: CharSequence): Float {
        var units = 0f
        for (c in text) {
            units += when {
                CjkText.isHanzi(c) -> 1f
                c == ' ' -> 0.3f
                c in 'A'..'Z' || c in 'a'..'z' -> 0.55f
                c in '0'..'9' -> 0.55f
                else -> 0.4f
            }
        }
        return units
    }
}
