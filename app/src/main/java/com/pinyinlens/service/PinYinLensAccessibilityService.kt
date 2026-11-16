package com.pinyinlens.service

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.pinyinlens.gesture.ScrollVisibilityController
import com.pinyinlens.nodescan.AccessibilityTreeScanner
import com.pinyinlens.nodescan.HanziSpan
import com.pinyinlens.overlay.OverlayController

/**
 * Servicio de accesibilidad base.
 *
 * Punto de entrada orientado a eventos del sistema (RNF-02: sin polling).
 * Gestiona la capa persistente (RF-01), extrae Hanzi y coordenadas ante cambios
 * de contenido (RF-02) y oculta/regenera la capa durante el scroll (RF-06).
 */
class PinYinLensAccessibilityService : AccessibilityService() {

    private var overlay: OverlayController? = null
    private val scanner = AccessibilityTreeScanner()
    private val scroll = ScrollVisibilityController()
    private val handler = Handler(Looper.getMainLooper())

    /** Reagenda la comprobación de reposo tras cada scroll (RF-06). */
    private val quietCheck = object : Runnable {
        override fun run() {
            if (scroll.onQuietCheck(SystemClock.uptimeMillis())) {
                overlay?.showContent()
                Log.i(TAG, "RF06 regenerate")
                extractHanzi()
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "AccessibilityService conectado")
        // RF-01: levantar la capa persistente en cuanto el servicio está activo.
        overlay = OverlayController(this).also { it.show() }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScroll()
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                scroll.reset()
                extractHanzi()
            }
        }
    }

    /**
     * RF-06: oculta la capa de inmediato al iniciar scroll y programa la
     * regeneración tras la ventana de reposo. Mide el tiempo de ocultamiento.
     */
    private fun onScroll() {
        val now = SystemClock.uptimeMillis()
        val changed = scroll.onScroll(now)
        if (changed) {
            val t0 = SystemClock.elapsedRealtimeNanos()
            overlay?.hideContent()
            val hideMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
            // Traza para el harness e2e de RF-06 (medición t_hide).
            Log.i(TAG, "RF06 hide ms=$hideMs")
        }
        // Reprogramar la comprobación de reposo.
        handler.removeCallbacks(quietCheck)
        handler.postDelayed(quietCheck, scroll.quietWindowMs)
    }

    /** RF-02: escanea la ventana activa y registra los spans Hanzi detectados. */
    private fun extractHanzi() {
        val root = rootInActiveWindow ?: return
        val spans: List<HanziSpan> = scanner.scanFromRoot(root)
        if (spans.isNotEmpty()) {
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
        handler.removeCallbacks(quietCheck)
        overlay?.hide()
        overlay = null
        return super.onUnbind(intent)
    }

    companion object {
        const val TAG = "PinYinLens"
    }
}
