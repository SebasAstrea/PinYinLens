package com.pinyinlens.gesture

/**
 * Decide la visibilidad de la capa de pinyin durante el desplazamiento (RF-06).
 *
 * Lógica pura y orientada a eventos (RNF-02: sin polling): recibe eventos de
 * scroll y consultas de "¿ha pasado suficiente tiempo en reposo?" y resuelve si
 * la capa debe estar oculta (durante el movimiento) o regenerarse (al parar).
 *
 * El tiempo se pasa como parámetro (milisegundos monótonos) para poder probar
 * la temporización en JVM sin relojes reales. No mantiene hilos; el servicio es
 * quien agenda la comprobación de reposo tras [quietWindowMs].
 */
class ScrollVisibilityController(
    /** Ventana de reposo tras el último scroll para regenerar la capa. */
    val quietWindowMs: Long = DEFAULT_QUIET_WINDOW_MS,
) {

    enum class Visibility { VISIBLE, HIDDEN }

    var visibility: Visibility = Visibility.VISIBLE
        private set

    private var lastScrollAt: Long = Long.MIN_VALUE

    /**
     * Procesa un evento de scroll/arrastre en el instante [nowMs].
     * Oculta la capa de inmediato. Devuelve true si hubo un cambio de estado
     * (VISIBLE -> HIDDEN) que el llamante deba aplicar a la vista.
     */
    fun onScroll(nowMs: Long): Boolean {
        lastScrollAt = nowMs
        if (visibility == Visibility.VISIBLE) {
            visibility = Visibility.HIDDEN
            return true
        }
        return false
    }

    /**
     * Comprueba, en [nowMs], si el movimiento ya se detuvo (pasó la ventana de
     * reposo desde el último scroll) y, de ser así, marca la capa como VISIBLE.
     * Devuelve true si hubo un cambio de estado (HIDDEN -> VISIBLE) que implique
     * regenerar y mostrar la capa.
     */
    fun onQuietCheck(nowMs: Long): Boolean {
        if (visibility != Visibility.HIDDEN) return false
        if (nowMs - lastScrollAt >= quietWindowMs) {
            visibility = Visibility.VISIBLE
            return true
        }
        return false
    }

    /** Fuerza el estado visible (p. ej. al cambiar de ventana). */
    fun reset() {
        visibility = Visibility.VISIBLE
        lastScrollAt = Long.MIN_VALUE
    }

    companion object {
        const val DEFAULT_QUIET_WINDOW_MS = 180L
    }
}
