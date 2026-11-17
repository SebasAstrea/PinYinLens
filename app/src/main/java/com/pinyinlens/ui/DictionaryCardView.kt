package com.pinyinlens.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.pinyinlens.dictionary.DictionaryCard

/**
 * Tarjeta flotante que muestra el contenido del diccionario tras el hold de 5 s
 * (RF-05): Hanzi, pinyin, desglose por carácter y traducciones ES/EN.
 *
 * Vista autónoma que se añade al overlay. El pulido visual (contraste WCAG,
 * escalado) pertenece al endurecimiento RNF-04; aquí prioriza ser funcional y
 * legible.
 *
 * Es MODAL (RF-05): consume los toques que recibe para que no se filtren a la
 * app inferior, y se cierra con un toque.
 */
class DictionaryCardView(context: Context) : LinearLayout(context) {

    /** Se invoca al cerrarse la tarjeta (toque), para que el servicio libere su referencia. */
    var onDismiss: (() -> Unit)? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(Color.parseColor("#F2000000"))
        setPadding(40, 32, 40, 32)
        // Consumir toques: la tarjeta no deja pasar el dedo a la app inferior.
        isClickable = true
        setOnClickListener {
            onDismiss?.invoke()
            dismiss(this)
        }
    }

    fun bind(card: DictionaryCard) {
        removeAllViews()
        addView(line("${card.simplified}  （${card.traditional}）", 26f, Color.WHITE))
        addView(line(card.pinyin, 20f, Color.parseColor("#FFD54F")))

        if (card.breakdown.isNotEmpty()) {
            val bd = card.breakdown.joinToString("   ") { "${it.char} ${it.pinyin} ${it.gloss}".trim() }
            addView(line(bd, 15f, Color.parseColor("#B0BEC5")))
        }
        if (card.spanish.isNotEmpty()) addView(line("ES: " + card.spanish.joinToString("; "), 16f, Color.WHITE))
        if (card.english.isNotEmpty()) addView(line("EN: " + card.english.joinToString("; "), 16f, Color.WHITE))
    }

    private fun line(text: String, sizeSp: Float, color: Int): TextView =
        TextView(context).apply {
            this.text = text
            textSize = sizeSp
            setTextColor(color)
            gravity = Gravity.CENTER_HORIZONTAL
        }

    companion object {
        /** Oculta la vista si está adjunta a un padre. */
        fun dismiss(view: View?) {
            (view?.parent as? ViewGroup)?.removeView(view)
        }
    }
}
