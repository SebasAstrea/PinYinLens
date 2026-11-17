package com.pinyinlens.overlay

import android.content.Context
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager

/**
 * Ventana flotante para contenido interactivo del overlay (RF-05: tarjeta del
 * diccionario). Es independiente de la capa de dibujo pass-through, de modo que
 * el pinyin se sigue dibujando sin bloquear toques y el panel solo intercepta
 * los suyos.
 *
 * La ventana es WRAP_CONTENT y no modal: únicamente el rectángulo del panel
 * recibe toques; el resto de la pantalla sigue navegable.
 */
class OverlayPanel(
    private val context: Context,
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager,
) {

    private var content: View? = null

    val isShowing: Boolean
        get() = content != null

    /** Muestra [view] centrada (o con la gravedad indicada). Reemplaza la anterior. */
    fun show(view: View, gravity: Int = Gravity.CENTER) {
        hide()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            OverlayWindowSpec.WINDOW_TYPE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            OverlayWindowSpec.PIXEL_FORMAT,
        ).apply {
            this.gravity = gravity
            // Mantener el panel dentro de la pantalla en dispositivos con cutout.
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        try {
            windowManager.addView(view, params)
            content = view
            Log.i(TAG, "Panel mostrado")
        } catch (e: WindowManager.BadTokenException) {
            Log.e(TAG, "No se pudo añadir el panel", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Estado inválido al añadir el panel", e)
        }
    }

    /** Retira el panel si está visible. Idempotente. */
    fun hide() {
        val view = content ?: return
        content = null
        try {
            windowManager.removeView(view)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "El panel ya no estaba adjunto", e)
        }
    }

    /** Retira el panel y libera la referencia (al desvincular el servicio). */
    fun release() {
        hide()
    }

    companion object {
        const val TAG = "PinYinLens"
    }
}
