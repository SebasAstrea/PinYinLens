package com.pinyinlens.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.pinyinlens.overlay.OverlayController

/**
 * Servicio de accesibilidad base.
 *
 * Es el punto de entrada orientado a eventos del sistema (RNF-02: sin polling).
 * Gestiona la capa de overlay persistente (RF-01). La extracción de nodos
 * (RF-02) y la gestión de scroll (RF-06) se conectan en sus historias.
 */
class PinYinLensAccessibilityService : AccessibilityService() {

    private var overlay: OverlayController? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "AccessibilityService conectado")
        // RF-01: levantar la capa persistente en cuanto el servicio está activo.
        overlay = OverlayController(this).also { it.show() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Punto de enganche para RF-02 / RF-06. Andamiaje: no-op trazado.
        if (event != null) {
            Log.v(TAG, "Evento: ${AccessibilityEvent.eventTypeToString(event.eventType)}")
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
