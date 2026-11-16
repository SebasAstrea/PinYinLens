package com.pinyinlens.gesture

import kotlin.math.abs

/**
 * Detecta la pulsación sostenida de 5 s sobre una etiqueta de pinyin (RF-05).
 *
 * Lógica pura y testeable en JVM: recibe eventos táctiles (down/move/up) con su
 * instante en milisegundos y su posición, y decide si se completó un hold
 * válido. Rechaza:
 *  - taps rápidos (up antes de completar la duración),
 *  - gestos con desplazamiento mayor que [slopPx] (son scroll/arrastre, no hold).
 *
 * La duración objetivo es 5000 ms con tolerancia ±150 ms (RF-05). Un hold se
 * considera disparado en cuanto se alcanza [holdMs] estando el dedo quieto; la
 * tolerancia superior (±150) la cubre el margen de muestreo del sistema.
 */
class HoldDetector(
    val holdMs: Long = DEFAULT_HOLD_MS,
    val toleranceMs: Long = DEFAULT_TOLERANCE_MS,
    val slopPx: Int = DEFAULT_SLOP_PX,
) {

    enum class State { IDLE, TRACKING, FIRED, CANCELLED }

    var state: State = State.IDLE
        private set

    private var downAt: Long = 0
    private var downX: Int = 0
    private var downY: Int = 0

    /** Inicio del contacto. Reinicia el seguimiento. */
    fun onDown(nowMs: Long, x: Int, y: Int) {
        state = State.TRACKING
        downAt = nowMs
        downX = x
        downY = y
    }

    /**
     * Movimiento del dedo. Si se supera el slop, se cancela (es scroll).
     * Devuelve el estado resultante.
     */
    fun onMove(x: Int, y: Int): State {
        if (state != State.TRACKING) return state
        if (abs(x - downX) > slopPx || abs(y - downY) > slopPx) {
            state = State.CANCELLED
        }
        return state
    }

    /**
     * Comprobación temporal (la agenda el servicio a los [holdMs]). Si el dedo
     * sigue abajo y quieto y ha transcurrido la duración, dispara el hold.
     * Devuelve true si disparó en esta llamada.
     */
    fun onTimeCheck(nowMs: Long): Boolean {
        if (state != State.TRACKING) return false
        if (nowMs - downAt >= holdMs - toleranceMs) {
            state = State.FIRED
            return true
        }
        return false
    }

    /**
     * Fin del contacto. Determina si fue un hold válido (ya disparado) o un tap
     * (demasiado corto). Devuelve true solo si el gesto completó un hold.
     *
     * Un up que llega habiendo alcanzado la duración (dentro de tolerancia)
     * cuenta como hold aunque no haya habido onTimeCheck previo.
     */
    fun onUp(nowMs: Long): Boolean {
        val wasTracking = state == State.TRACKING
        val fired = state == State.FIRED
        val heldLongEnough = wasTracking && (nowMs - downAt >= holdMs - toleranceMs)
        val result = fired || heldLongEnough
        state = if (result) State.FIRED else State.IDLE
        return result
    }

    /** Vuelve a IDLE (p. ej. tras consumir el evento). */
    fun reset() {
        state = State.IDLE
    }

    companion object {
        const val DEFAULT_HOLD_MS = 5000L
        const val DEFAULT_TOLERANCE_MS = 150L
        const val DEFAULT_SLOP_PX = 24
    }
}
