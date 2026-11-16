package com.pinyinlens.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.pinyinlens.nodescan.AccessibilityTreeScanner
import com.pinyinlens.nodescan.HanziSpan
import com.pinyinlens.overlay.OverlayController

/**
 * Servicio de accesibilidad base.
 *
 * Es el punto de entrada orientado a eventos del sistema (RNF-02: sin polling).
 * Gestiona la capa de overlay persistente (RF-01) y, ante cambios de contenido,
 * extrae los fragmentos Hanzi y sus coordenadas (RF-02). La gestión de scroll
 * (RF-06) y el dibujado del pinyin (RF-04/render) se conectan en sus historias.
 */
class PinYinLensAccessibilityService : AccessibilityService() {

    private var overlay: OverlayController? = null
    private val scanner = AccessibilityTreeScanner()

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "AccessibilityService conectado")
        // RF-01: levantar la capa persistente en cuanto el servicio está activo.
        overlay = OverlayController(this).also { it.show() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> extractHanzi()
            // RF-06 gestionará TYPE_VIEW_SCROLLED en su historia.
        }
    }

    /** RF-02: escanea la ventana activa y registra los spans Hanzi detectados. */
    private fun extractHanzi() {
        val root = rootInActiveWindow ?: return
        val spans: List<HanziSpan> = scanner.scanFromRoot(root)
        if (spans.isNotEmpty()) {
            // Traza estructurada para el harness e2e de RF-02 (medición R_nativo).
            Log.i(TAG, "RF02 spans=${spans.size}")
            for (s in spans) {
                Log.v(TAG, "RF02 span text='${s.text}' rect=${s.left},${s.top},${s.right},${s.bottom}")
            }
        }
    }

    override fun onInterrupt() {
        Log.i(TAG, "AccessibilityService interrumpido")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        overlay?.hide()
        overlay = null
        return super.onUnbind(intent)
    }

    companion object {
        const val TAG = "PinYinLens"
    }
}
