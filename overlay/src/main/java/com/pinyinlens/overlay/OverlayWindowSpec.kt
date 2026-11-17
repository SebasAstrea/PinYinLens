package com.pinyinlens.overlay

import android.graphics.PixelFormat
import android.graphics.Rect
import android.view.Gravity
import android.view.WindowManager

/**
 * Especificación pura (sin estado Android vivo) de los parámetros de las
 * ventanas del overlay de PinYinLens (RF-01 + RF-05).
 *
 * El overlay se compone de DOS tipos de ventana:
 *
 *  1. **Capa de dibujo** ([buildLayoutParams]): pantalla completa y
 *     [FLAG_NOT_TOUCHABLE]. Solo pinta el pinyin; el sistema entrega los toques
 *     directamente a la app inferior, así que la navegación (scroll, taps,
 *     gestos del sistema) nunca se bloquea. Esta es la corrección de RF-01: la
 *     capa visible NO intercepta.
 *
 *  2. **Objetivos táctiles** ([buildTouchTargetLayoutParams]): ventanas
 *     minúsculas, una por etiqueta de pinyin dibujada, colocadas exactamente
 *     sobre ella. Solo esas zonas reciben toques (el hold de 5 s de RF-05); el
 *     resto de la pantalla sigue siendo pass-through porque ninguna ventana
 *     interactiva cubre esas coordenadas.
 *
 * Antes se usaba una única ventana interactiva de pantalla completa y se
 * intentaba "devolver" los toques no consumidos desde la vista. En Android eso
 * no funciona: el dispatcher elige la ventana superior táctil y NO reenvía a la
 * ventana inferior los eventos que la vista no consume. De ahí que el overlay
 * bloqueara toda la interacción. Con ventanas por etiqueta, el bloqueo queda
 * reducido al rectángulo de cada pinyin (unos pocos dp).
 */
object OverlayWindowSpec {

    /**
     * Flags de la capa de dibujo: visible pero totalmente transparente a toques.
     */
    const val RENDER_FLAGS =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    /** Alias histórico: la capa de dibujo es pass-through por flags. */
    const val PASS_THROUGH_FLAGS = RENDER_FLAGS

    /**
     * Flags de un objetivo táctil: recibe toques dentro de su rectángulo, pero
     * no roba foco ni es modal (lo de fuera pertenece a otras ventanas).
     */
    const val TOUCH_TARGET_FLAGS =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    /** Alias histórico del modo interactivo (antes, ventana completa). */
    const val INTERACTIVE_FLAGS = TOUCH_TARGET_FLAGS

    const val WINDOW_TYPE = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

    const val PIXEL_FORMAT = PixelFormat.TRANSLUCENT

    /** Construye los LayoutParams de la capa de dibujo (pantalla completa, pass-through). */
    fun buildLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WINDOW_TYPE,
            RENDER_FLAGS,
            PIXEL_FORMAT,
        )

    /**
     * Construye los LayoutParams de un objetivo táctil del tamaño y posición de
     * [rect], en coordenadas absolutas de pantalla.
     */
    fun buildTouchTargetLayoutParams(rect: Rect): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            rect.width().coerceAtLeast(1),
            rect.height().coerceAtLeast(1),
            WINDOW_TYPE,
            TOUCH_TARGET_FLAGS,
            PIXEL_FORMAT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = rect.left
            y = rect.top
        }

    /** True si [flags] hacen que la VENTANA no reciba toques en absoluto. */
    fun isPassThrough(flags: Int): Boolean =
        (flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0

    /** True si [flags] permiten que la ventana reciba toques (los gestos RF-05). */
    fun isTouchInteractive(flags: Int): Boolean = !isPassThrough(flags)
}
