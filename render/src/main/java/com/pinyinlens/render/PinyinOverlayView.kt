package com.pinyinlens.render

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

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

    /** Actualiza las etiquetas a dibujar y repinta. */
    fun setItems(newItems: List<PinyinItem>) {
        items = newItems
        invalidate()
    }

    fun clearItems() {
        items = emptyList()
        invalidate()
    }

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
