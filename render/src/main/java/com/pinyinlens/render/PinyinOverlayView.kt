package com.pinyinlens.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.MotionEvent
import android.view.View
import com.pinyinlens.gesture.HoldDetector

private const val TAG = "PinyinOverlayView"

/**
 * Elemento a anotar: rectángulo del Hanzi (o bloque OCR) en pantalla y su pinyin.
 */
data class PinyinItem(
    val pinyin: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    /** Tamaño de fuente real del Hanzi origen, en px; 0 si se desconoce. */
    val hanziTextPx: Float = 0f,
    /** Palabra Hanzi origen (para la tarjeta de diccionario del hold, RF-05). */
    val word: String = "",
)

/**
 * Vista que dibuja las etiquetas de pinyin debajo de los caracteres (RF-04/
 * render + RNF-04). Es el lienzo del overlay (RF-01).
 *
 * El pinyin usa el mismo tamaño de fuente que el Hanzi de origen (acotado a
 * [minTextPx] y [maxTextPx]) y se reduce solo lo necesario para no solaparse con
 * la etiqueta vecina. Ver [PinyinLayout].
 *
 * Legibilidad (RNF-04): cada etiqueta se dibuja con un CONTORNO (stroke) de color
 * opuesto en luminancia al relleno, garantizando contraste alto sobre cualquier
 * fondo de la app inferior sin conocer su color.
 *
 * **Gesto de hold (RF-05):** la ventana del overlay es interactiva, así que esta
 * vista decide toque a toque: un `ACTION_DOWN` que cae sobre el rectángulo de una
 * etiqueta se consume y se entrega a un [HoldDetector]; al completar 5 s con el
 * dedo quieto dispara [onHold] con la palabra origen (y el `UP` se consume, de
 * modo que no se propaga un clic accidental a la app inferior). Cualquier otro
 * toque se devuelve sin consumir y el dispatcher lo re-encamina a la ventana
 * inferior (pass-through, RF-01).
 */
class PinyinOverlayView(context: Context) : View(context) {

    private var items: List<PinyinItem> = emptyList()
    private val viewLocation = IntArray(2)
    private val minTextPx = 9f * resources.displayMetrics.scaledDensity // 9 sp
    private val maxTextPx = 48f * resources.displayMetrics.scaledDensity // tope de seguridad
    private val gapPx = 2f * resources.displayMetrics.density

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK // texto blanco -> contorno negro (Contrast.preferBlackStroke)
        textAlign = Paint.Align.CENTER
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    /** Mide con el mismo `Paint` del relleno para que el ajuste de ancho sea exacto. */
    private val measurer = TextMeasurer { text, size ->
        fill.textSize = size
        fill.measureText(text)
    }

    // ---------------------------------------------------------------------
    // Hold de 5 s sobre la etiqueta (RF-05)
    // ---------------------------------------------------------------------

    /** Callback al completar el hold: recibe la palabra Hanzi de la etiqueta. */
    var onHold: ((String) -> Unit)? = null

    private val holdDetector = HoldDetector()
    private val handler = Handler(Looper.getMainLooper())
    private var tracking = false
    private var holdWord: String? = null

    /** Etiqueta y origen de la última pasada de [onDraw], para el hit-testing. */
    private var drawnLabels: List<PinyinLabel> = emptyList()
    private var originX = 0
    private var originY = 0

    private val holdCheck = Runnable {
        if (holdDetector.onTimeCheck(SystemClock.uptimeMillis())) fireHold()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Log.v(TAG, "onSizeChanged w=$w h=$h")
    }

    /** Actualiza las etiquetas a dibujar y repinta. */
    fun setItems(newItems: List<PinyinItem>) {
        Log.v(TAG, "setItems: ${newItems.size} items")
        cancelTracking() // las etiquetas han cambiado: el hold en curso es inválido
        items = newItems
        invalidate()
    }

    fun clearItems() {
        Log.v(TAG, "clearItems (was tracking=$tracking)")
        cancelTracking()
        cancelTracking()
        items = emptyList()
        invalidate()
    }

    /** Cancela un hold en curso sin disparar (scroll, cambio de contenido, etc.). */
    private fun cancelTracking() {
        tracking = false
        holdWord = null
        handler.removeCallbacks(holdCheck)
        holdDetector.reset()
    }

    /** Dispara el hold (dedo aún abajo) y limpia el seguimiento. */
    private fun fireHold() {
        val word = holdWord
        tracking = false
        holdWord = null
        handler.removeCallbacks(holdCheck)
        holdDetector.reset()
        if (word != null) onHold?.invoke(word)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val label = hitLabel(event.x, event.y) ?: run {
                    return false // fuera de etiqueta → pass-through total
                }
                Log.v(TAG, "  DOWN consumed on '${label.text}' word='${label.word}'")
                tracking = true
                holdWord = label.word
                holdDetector.onDown(SystemClock.uptimeMillis(), event.x.toInt(), event.y.toInt())
                handler.removeCallbacks(holdCheck)
                handler.postDelayed(holdCheck, holdDetector.holdMs)
                Log.v(TAG, "  DOWN consumed on label='${label.text}' word='$holdWord'")
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val cancelled = holdDetector.onMove(event.x.toInt(), event.y.toInt()) == HoldDetector.State.CANCELLED
                if (cancelled) {
                    tracking = false
                    holdWord = null
                    handler.removeCallbacks(holdCheck)
                    Log.v(TAG, "  MOVE cancel -> pass-through")
                } else {
                    Log.v(TAG, "  MOVE consumed (hold)")
                }
                return !cancelled // si canceló → passthrough; mientras hold, consumimos
            }

            MotionEvent.ACTION_UP -> {
                if (!tracking) {
                    val label = hitLabel(event.x, event.y) ?: run {
                        return false // no estábamos rastreando → pass-through
                    }
                    Log.v(TAG, "  UP consumed on '${label.text}' (not tracking but inside label hitbox)")
                    return true
                }
                handler.removeCallbacks(holdCheck)
                val label = hitLabel(event.x, event.y) ?: run {
                    // El dedo se movió a un área vacía: es scroll/arrastre. Consumir el UP para que la ventana inferior reciba su gesto completo de liberación.
                    tracking = false
                    holdWord = null
                    holdDetector.reset()
                    Log.v(TAG, "  UP pass-through (moved to empty area)")
                    return false
                }
                val completed = holdDetector.onUp(SystemClock.uptimeMillis())
                val word = holdWord
                tracking = false
                holdWord = null
                holdDetector.reset()
                if (completed && word != null) onHold?.invoke(word)
                Log.v(TAG, "  UP consumed on '${label.text}' completed=$completed")
                true // dentro de una etiqueta → consumimos UP (el usuario soltó sobre el pinyin)
            }

            MotionEvent.ACTION_CANCEL -> {
                cancelTracking()
                Log.v(TAG, "  CANCEL -> consumed")
                return true
            }
        }
        return false
    }

    /**
     * Etiqueta cuyo rectángulo (con holgura) contiene el punto (en coords de la
     * vista), o null. El rectángulo se deriva de la misma métrica que [onDraw].
     */
    private fun hitLabel(x: Float, y: Float): PinyinLabel? {
        val pad = 8f * resources.displayMetrics.density
        Log.v(TAG, "hitLabel x=$x y=$y originX=$originX originY=$originY drawnLabels=${drawnLabels.size}")
        for (label in drawnLabels.asReversed()) {
            val halfWidth = measurer.widthOf(label.text, label.textSizePx) / 2f
            val left = label.x - originX - halfWidth
            val right = label.x - originX + halfWidth
            val top = label.baselineY - originY - PinyinLayout.ASCENT_RATIO * label.textSizePx
            val bottom = label.baselineY - originY + PinyinLayout.DESCENT_RATIO * label.textSizePx
            Log.v(TAG, "  check '${label.text}' x=$x y=$y left=$left right=$right top=$top bottom=$bottom halfW=$halfWidth textSize=${label.textSizePx} baselineY=${label.baselineY}")
            if (x >= left - pad && x <= right + pad && y >= top - pad && y <= bottom + pad) {
                Log.v(TAG, "  HIT '${label.text}'")
                return label
            }
        }
        Log.v(TAG, "  no hit found in ${drawnLabels.size} labels")
        return null
    }

    // ---------------------------------------------------------------------
    // Dibujo
    // ---------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // La ventana del overlay no empieza necesariamente en y=0 de la pantalla
        // (p. ej. queda desplazada por la barra de estado). Las cajas de
        // accesibilidad están en coordenadas de PANTALLA, así que se compensa el
        // origen de la vista para que el pinyin caiga exactamente bajo su Hanzi.
        getLocationOnScreen(viewLocation)
        originX = viewLocation[0]
        originY = viewLocation[1]
        val labels = PinyinLayout.layoutAll(
            items.map {
                PinyinLayout.Box(
                    pinyin = it.pinyin,
                    left = it.left,
                    top = it.top,
                    right = it.right,
                    bottom = it.bottom,
                    hanziTextPx = it.hanziTextPx,
                    word = it.word,
                )
            },
            minTextPx = minTextPx,
            maxTextPx = maxTextPx,
            gapPx = gapPx,
            measure = measurer,
            screenBottomPx = originY + height,
            screenLeftPx = originX,
            screenRightPx = originX + width,
        )
        // Se cachea para el hit-testing del hold (mismas etiquetas que las dibujadas).
        Log.v(TAG, "onDraw: layoutAll result=${labels.size} of ${items.size}")
        drawnLabels = labels
        for (label in labels) {
            if (label.text.isBlank()) continue
            fill.textSize = label.textSizePx
            stroke.textSize = label.textSizePx
            stroke.strokeWidth = (label.textSizePx * 0.10f).coerceAtLeast(2f)
            val x = label.x - originX
            val y = label.baselineY - originY
            canvas.drawText(label.text, x, y, stroke)
            canvas.drawText(label.text, x, y, fill)
        }
    }
}
