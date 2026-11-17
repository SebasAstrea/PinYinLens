package com.pinyinlens.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View

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
 * **Toques (RF-01/RF-05):** esta vista NO recibe eventos táctiles; su ventana es
 * FLAG_NOT_TOUCHABLE para que la navegación de la app inferior nunca se bloquee.
 * Tras cada dibujo publica las zonas de las etiquetas ([PinyinHitbox]) mediante
 * [onHitboxes]; el servicio crea con ellas ventanas interactivas minúsculas que
 * capturan el hold de 5 s. Así solo el rectángulo del pinyin intercepta, no la
 * pantalla completa.
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
    // Zonas táctiles de las etiquetas (RF-05)
    // ---------------------------------------------------------------------

    /**
     * Se invoca (en el hilo principal) cada vez que cambian las etiquetas
     * dibujadas, con sus zonas táctiles en coordenadas de pantalla. Lista vacía
     * cuando no hay etiquetas. El servicio las usa para crear/retirar las
     * ventanas interactivas de hold.
     */
    var onHitboxes: ((List<PinyinHitbox>) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var pendingHitboxes: List<PinyinHitbox>? = null

    private val publishHitboxes = Runnable {
        val boxes = pendingHitboxes ?: return@Runnable
        pendingHitboxes = null
        onHitboxes?.invoke(boxes)
    }

    /**
     * Publica las zonas táctiles. Se hace siempre tras cada dibujo (no solo
     * cuando cambian): el servicio puede haberlas retirado durante un scroll o
     * al abrir la tarjeta, y debe poder reconstruirlas aunque el layout sea el
     * mismo. El control de destinos deduplica, así que republicar es barato.
     */
    private fun requestHitboxes(boxes: List<PinyinHitbox>) {
        pendingHitboxes = boxes
        handler.removeCallbacks(publishHitboxes)
        handler.post(publishHitboxes)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        Log.v(TAG, "onSizeChanged w=$w h=$h")
    }

    /** Actualiza las etiquetas a dibujar y repinta. */
    fun setItems(newItems: List<PinyinItem>) {
        Log.v(TAG, "setItems: ${newItems.size} items")
        items = newItems
        invalidate()
    }

    fun clearItems() {
        Log.v(TAG, "clearItems")
        items = emptyList()
        requestHitboxes(emptyList())
        invalidate()
    }

    /** Retira el callback de zonas táctiles (al desvincular el servicio). */
    fun release() {
        handler.removeCallbacks(publishHitboxes)
        pendingHitboxes = null
        onHitboxes = null
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
        val originX = viewLocation[0]
        val originY = viewLocation[1]
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
        Log.v(TAG, "onDraw: layoutAll result=${labels.size} of ${items.size}")
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
        // Zonas táctiles para el hold (mismas etiquetas que las dibujadas).
        val dm = resources.displayMetrics
        requestHitboxes(
            PinyinHitboxes.compute(
                labels = labels,
                measure = measurer,
                density = dm.density,
                screenWidth = dm.widthPixels,
                screenHeight = dm.heightPixels,
            ),
        )
    }
}
