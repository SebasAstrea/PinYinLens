package com.pinyinlens.overlay

import android.content.Context
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Gestiona el ciclo de vida de la capa de overlay persistente (RF-01).
 *
 * Añade una vista transparente de pantalla completa al [WindowManager] con tipo
 * TYPE_ACCESSIBILITY_OVERLAY. La vista es el lienzo donde el módulo `render`
 * dibujará el pinyin. Por defecto no intercepta toques (pass-through), así que
 * no bloquea la app inferior (requisito de eventos táctiles subyacentes).
 *
 * Debe crearse desde un contexto con capacidad de overlay, normalmente el propio
 * AccessibilityService.
 */
class OverlayController(
    private val context: Context,
    private val windowManager: WindowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager,
) {

    private var container: FrameLayout? = null

    /** True si la capa está actualmente añadida al WindowManager. */
    val isShowing: Boolean
        get() = container?.isAttachedToWindow == true

    /**
     * Añade la capa si no está presente. Idempotente: llamarlo dos veces no crea
     * capas duplicadas.
     */
    fun show() {
        if (container != null) return
        val view = FrameLayout(context)
        try {
            windowManager.addView(view, OverlayWindowSpec.buildLayoutParams())
            container = view
            Log.i(TAG, "Overlay añadido (TYPE_ACCESSIBILITY_OVERLAY, pass-through)")
        } catch (e: WindowManager.BadTokenException) {
            Log.e(TAG, "No se pudo añadir el overlay", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Estado inválido al añadir el overlay", e)
        }
    }

    /** Devuelve la vista contenedora donde `render` adjunta el pinyin, o null. */
    fun contentView(): View? = container

    /** Quita la capa si está presente. Idempotente. */
    fun hide() {
        val view = container ?: return
        try {
            windowManager.removeView(view)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "El overlay ya no estaba adjunto", e)
        } finally {
            container = null
        }
    }

    companion object {
        const val TAG = "PinYinLens"
    }
}
