package com.pinyinlens.overlay

import android.graphics.PixelFormat
import android.view.WindowManager

/**
 * Especificación pura (sin estado Android vivo) de los parámetros de la ventana
 * del overlay de PinYinLens (RF-01).
 *
 * Se separa de la creación real de la vista para poder probar en JVM que la
 * capa usa el tipo y los flags correctos:
 *  - TYPE_ACCESSIBILITY_OVERLAY: se dibuja por encima de apps de terceros y de
 *    la UI del sistema (panel de notificaciones, ajustes, menús).
 *  - FLAG_NOT_FOCUSABLE + FLAG_NOT_TOUCHABLE: pass-through total de eventos
 *    táctiles a la app inferior mientras la capa solo pinta pinyin.
 *  - FLAG_LAYOUT_IN_SCREEN + FLAG_LAYOUT_NO_LIMITS: cubre toda la pantalla,
 *    incluidas zonas de system bars, para alinear el pinyin con cualquier nodo.
 *  - Fondo translúcido (PixelFormat.TRANSLUCENT) para no tapar el contenido.
 *
 * El pass-through se desactivará puntualmente solo cuando se necesite capturar
 * el gesto de pulsación sostenida (RF-05); por defecto la capa no intercepta.
 */
object OverlayWindowSpec {

    /** Flags por defecto: capa visible pero totalmente transparente a toques. */
    const val PASS_THROUGH_FLAGS =
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS

    const val WINDOW_TYPE = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY

    const val PIXEL_FORMAT = PixelFormat.TRANSLUCENT

    /** Construye los LayoutParams del overlay persistente de pantalla completa. */
    fun buildLayoutParams(): WindowManager.LayoutParams =
        WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WINDOW_TYPE,
            PASS_THROUGH_FLAGS,
            PIXEL_FORMAT,
        )

    /** True si [flags] permiten que los toques pasen a la app inferior. */
    fun isPassThrough(flags: Int): Boolean =
        (flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE) != 0
}
