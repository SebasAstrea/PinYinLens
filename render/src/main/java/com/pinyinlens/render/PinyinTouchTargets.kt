package com.pinyinlens.render

import android.content.Context
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import com.pinyinlens.gesture.HoldDetector
import com.pinyinlens.overlay.OverlayWindowSpec

private const val TAG = "PinyinTouchTargets"

/**
 * Ventanas táctiles minúsculas, una por etiqueta de pinyin dibujada (RF-05),
 * que permiten el hold de 5 s sin bloquear la navegación (RF-01).
 *
 * Corrección del bloqueo táctil: la capa de dibujo es FLAG_NOT_TOUCHABLE, así
 * que la app inferior recibe todos los toques. Solo este control añade ventanas
 * interactivas del tamaño del pinyin (con una holgura pequeña), colocadas justo
 * encima de cada etiqueta. Fuera de esos rectángulos no hay ninguna ventana
 * táctil del overlay: scroll, taps y gestos del sistema siguen funcionando.
 */
class PinyinTouchTargets(
    private val context: Context,
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager,
    private val holdMs: Long = HoldDetector.DEFAULT_HOLD_MS,
) {

    /** Se invoca al completar el hold, con la palabra Hanzi de la etiqueta. */
    var onHold: ((String) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var targets: MutableList<View> = mutableListOf()
    private var lastBoxes: List<PinyinHitbox> = emptyList()

    // Mientras un dedo está apoyado no se pueden retirar las ventanas: el
    // WindowManager cancelaría el gesto y el hold de 5 s nunca llegaría a
    // completarse. Los cambios se aplican al levantar el dedo.
    private var pointerDown = false
    private var pendingBoxes: List<PinyinHitbox>? = null

    /**
     * Ajusta las ventanas táctiles a [boxes]. Idempotente: si las zonas no han
     * cambiado no toca el WindowManager (evita parpadeo en re-escaneos).
     */
    fun update(boxes: List<PinyinHitbox>) {
        if (boxes == lastBoxes) return
        if (pointerDown) {
            pendingBoxes = boxes
            return
        }
        applyBoxes(boxes)
    }

    private fun applyBoxes(boxes: List<PinyinHitbox>) {
        removeAll()
        lastBoxes = boxes
        for (box in boxes.take(MAX_TARGETS)) addTarget(box)
        Log.v(TAG, "update: ${targets.size} targets of ${boxes.size}")
    }

    private fun onPointerDown() {
        pointerDown = true
    }

    private fun onPointerFinished() {
        if (!pointerDown) return
        pointerDown = false
        val next = pendingBoxes ?: return
        pendingBoxes = null
        // Fuera del despacho del evento: recrear ventanas desde el ACTION_UP de
        // una de ellas mismas es frágil.
        handler.post {
            if (!pointerDown && next != lastBoxes) applyBoxes(next)
        }
    }

    /** Retira todas las ventanas táctiles (scroll RF-06, tarjeta abierta, etc.). */
    fun clear() {
        pendingBoxes = null
        pointerDown = false
        if (targets.isEmpty() && lastBoxes.isEmpty()) return
        removeAll()
        lastBoxes = emptyList()
        Log.v(TAG, "clear: targets removed")
    }

    /** Libera el control al desvincular el servicio. */
    fun release() {
        clear()
        handler.removeCallbacksAndMessages(null)
        onHold = null
    }

    private fun addTarget(box: PinyinHitbox) {
        val view = TouchTargetView(
            context = context,
            word = box.word,
            holdMs = holdMs,
            onHold = { word -> onHold?.invoke(word) },
            onPressedChange = { down ->
                if (down) onPointerDown() else onPointerFinished()
            },
        )
        val params = OverlayWindowSpec.buildTouchTargetLayoutParams(
            Rect(box.left, box.top, box.right, box.bottom),
        )
        try {
            windowManager.addView(view, params)
            targets.add(view)
        } catch (e: WindowManager.BadTokenException) {
            Log.e(TAG, "No se pudo añadir una zona táctil", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Estado inválido al añadir una zona táctil", e)
        }
    }

    private fun removeAll() {
        for (view in targets) {
            try {
                windowManager.removeView(view)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "La zona táctil ya no estaba adjunta", e)
            }
        }
        targets.clear()
    }

    companion object {
        /** Tope de ventanas por pantalla; en la práctica hay muchas menos. */
        const val MAX_TARGETS = 80
    }
}

/**
 * Vista transparente de una zona táctil. Detecta el hold de 5 s con
 * [HoldDetector] y consume siempre el gesto que empieza dentro (un tap rápido
 * no dispara nada, RF-05: 0 falsos positivos).
 */
private class TouchTargetView(
    context: Context,
    private val word: String,
    private val holdMs: Long,
    private val onHold: (String) -> Unit,
    private val onPressedChange: (Boolean) -> Unit,
) : View(context) {

    private val detector = HoldDetector(holdMs = holdMs)
    private val handler = Handler(Looper.getMainLooper())
    private var fired = false
    private var pressed = false

    private val timeCheck = Runnable {
        if (!fired && detector.onTimeCheck(SystemClock.uptimeMillis())) fire()
    }

    private fun fire() {
        if (fired) return
        fired = true
        handler.removeCallbacks(timeCheck)
        detector.reset()
        Log.v(TAG, "hold fired word='$word'")
        onHold(word)
    }

    private fun finishPress() {
        if (!pressed) return
        pressed = false
        onPressedChange(false)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                fired = false
                if (!pressed) {
                    pressed = true
                    onPressedChange(true)
                }
                detector.onDown(SystemClock.uptimeMillis(), event.x.toInt(), event.y.toInt())
                handler.removeCallbacks(timeCheck)
                handler.postDelayed(timeCheck, holdMs)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (detector.onMove(event.x.toInt(), event.y.toInt()) == HoldDetector.State.CANCELLED) {
                    handler.removeCallbacks(timeCheck)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(timeCheck)
                if (!fired && detector.onUp(SystemClock.uptimeMillis())) fire()
                detector.reset()
                fired = false
                finishPress()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(timeCheck)
                detector.reset()
                fired = false
                finishPress()
                return true
            }
        }
        return false
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacks(timeCheck)
        finishPress()
        super.onDetachedFromWindow()
    }
}
