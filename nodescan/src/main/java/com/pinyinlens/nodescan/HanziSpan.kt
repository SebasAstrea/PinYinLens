package com.pinyinlens.nodescan

/**
 * Un fragmento de texto que contiene Hanzi, con su posición absoluta en pantalla
 * (RF-02). Es la unidad que `pinyin` (RF-04) segmentará y sobre la que `render`
 * dibujará la anotación.
 *
 * Las coordenadas están en píxeles de pantalla (como las devuelve
 * `AccessibilityNodeInfo.getBoundsInScreen`). El rango [start, end) indexa el
 * texto completo del nodo origen, por si el Hanzi es solo una parte.
 */
data class HanziSpan(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    /**
     * Tamaño de fuente real del texto origen, en px (0 si el nodo no lo expone).
     * El render lo usa para dibujar el pinyin al mismo tamaño que su Hanzi.
     */
    val textSizePx: Float = 0f,
) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top

    init {
        require(text.isNotEmpty()) { "HanziSpan.text no puede estar vacío" }
    }
}
