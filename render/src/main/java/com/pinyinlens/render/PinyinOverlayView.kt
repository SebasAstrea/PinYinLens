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
)

/**
 * Vista que dibuja las etiquetas de pinyin encima de los caracteres (RF-04/
 * render + RNF-04). Es el lienzo del overlay (RF-01).
 *
 * Legibilidad (RNF-04): cada etiqueta se dibuja con un CONTORNO (stroke) de color
 * opuesto en luminancia al relleno, garantizando contraste alto sobre cualquier
 * fondo de la app inferior sin conocer su color. Tamaño de fuente 45–60% de la
 * altura del Hanzi, mínimo 9 sp (PinyinLayout).
 */
class PinyinOverlayView(context: Context) : View(context) {

    private var items: List<PinyinItem> = emptyList()
    private val minTextPx = 9f * resources.displayMetrics.scaledDensity // 9 sp
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
        for (item in items) {
            if (item.pinyin.isBlank()) continue
            val label = PinyinLayout.layoutFor(
                text = item.pinyin,
                left = item.left, top = item.top, right = item.right, bottom = item.bottom,
                minTextPx = minTextPx, gapPx = gapPx,
            )
            fill.textSize = label.textSizePx
            stroke.textSize = label.textSizePx
            // Primero el contorno (alto contraste), luego el relleno.
            canvas.drawText(label.text, label.x, label.baselineY, stroke)
            canvas.drawText(label.text, label.x, label.baselineY, fill)
        }
    }
}
