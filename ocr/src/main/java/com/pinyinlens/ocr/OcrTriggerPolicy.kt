package com.pinyinlens.ocr

/**
 * Decide, con lógica pura, cuándo un contenedor gráfico es candidato a OCR
 * (RF-03) y gestiona el debounce de inactividad táctil antes de disparar.
 *
 * Reglas de candidatura (RF-03): un nodo es ROI de OCR si es un contenedor
 * gráfico (ImageView, WebView con multimedia, galería) que NO tiene texto propio
 * ni descendientes con texto, y es visible con área > 0. La detección de "sin
 * texto" la resuelve el llamante sobre el árbol; aquí se evalúa sobre atributos.
 */
object OcrTriggerPolicy {

    /** Clases de contenedor gráfico candidatas (coincidencia por sufijo simple). */
    private val GRAPHIC_CLASSES = listOf("ImageView", "WebView", "ImageButton", "SurfaceView", "TextureView")

    /**
     * @param className nombre de clase del nodo (AccessibilityNodeInfo.className).
     * @param hasOwnText el nodo tiene texto propio.
     * @param hasTextDescendant algún descendiente tiene texto Hanzi.
     * @param isVisible visible para el usuario.
     * @param hasPositiveArea Rect con área > 0.
     */
    fun isOcrCandidate(
        className: CharSequence?,
        hasOwnText: Boolean,
        hasTextDescendant: Boolean,
        isVisible: Boolean,
        hasPositiveArea: Boolean,
    ): Boolean {
        if (!isVisible || !hasPositiveArea) return false
        if (hasOwnText || hasTextDescendant) return false // ya hay texto nativo (RF-02)
        val name = className?.toString() ?: return false
        return GRAPHIC_CLASSES.any { name.endsWith(it) }
    }
}

/**
 * Debounce de inactividad táctil para disparar el OCR (RF-03: esperar 400 ms sin
 * toques antes de capturar). Lógica pura con tiempo inyectado.
 *
 * El servicio llama [onTouch] en cada evento táctil/scroll y agenda una
 * comprobación a los [windowMs]; [shouldFire] indica si ya pasó la ventana de
 * quietud desde el último toque.
 */
class OcrDebounce(val windowMs: Long = DEFAULT_WINDOW_MS) {

    private var lastTouchAt: Long = Long.MIN_VALUE
    private var firedFor: Long = Long.MIN_VALUE

    /** Registra actividad táctil en [nowMs]. */
    fun onTouch(nowMs: Long) {
        lastTouchAt = nowMs
    }

    /**
     * True si, en [nowMs], ha transcurrido la ventana de quietud desde el último
     * toque y aún no se disparó para esta quietud. Marca el disparo para no
     * repetir hasta el próximo toque.
     */
    fun shouldFire(nowMs: Long): Boolean {
        if (lastTouchAt == Long.MIN_VALUE) return false
        if (nowMs - lastTouchAt < windowMs) return false
        if (firedFor == lastTouchAt) return false // ya disparado para esta quietud
        firedFor = lastTouchAt
        return true
    }

    /**
     * Momento (ms) a partir del cual la quietud es completa, o null si no ha
     * habido actividad. Permite al llamante re-agendar la comprobación exacta
     * sin sondeo continuo (RNF-02): si aún no se cumplió la ventana, se
     * re-postea a este instante en lugar de volver a preguntar periódicamente.
     */
    fun quietCompletesAt(nowMs: Long): Long? {
        if (lastTouchAt == Long.MIN_VALUE) return null
        return lastTouchAt + windowMs
    }

    fun reset() {
        lastTouchAt = Long.MIN_VALUE
        firedFor = Long.MIN_VALUE
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 400L
    }
}
