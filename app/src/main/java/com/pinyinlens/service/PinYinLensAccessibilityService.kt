package com.pinyinlens.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import com.pinyinlens.dict.SqliteDictionary
import com.pinyinlens.dictionary.DictionaryCardBuilder
import com.pinyinlens.gesture.ScrollVisibilityController
import com.pinyinlens.nodescan.AccessibilityTreeScanner
import com.pinyinlens.nodescan.HanziSpan
import com.pinyinlens.ocr.MlKitOcrEngine
import com.pinyinlens.ocr.OcrBlock
import com.pinyinlens.ocr.OcrEngine
import com.pinyinlens.ocr.OcrImage
import com.pinyinlens.overlay.OverlayController
import com.pinyinlens.ui.DictionaryCardView

/**
 * Servicio de accesibilidad base.
 *
 * Punto de entrada orientado a eventos del sistema (RNF-02: sin polling).
 * Gestiona la capa persistente (RF-01), extrae Hanzi y coordenadas (RF-02),
 * oculta/regenera la capa durante el scroll (RF-06) y muestra la tarjeta del
 * diccionario tras el hold de 5 s (RF-05).
 */
class PinYinLensAccessibilityService : AccessibilityService() {

    private var overlay: OverlayController? = null
    private val scanner = AccessibilityTreeScanner()
    private val scroll = ScrollVisibilityController()
    private val handler = Handler(Looper.getMainLooper())
    private var dictionary: SqliteDictionary? = null
    private var cardView: DictionaryCardView? = null
    private var ocr: OcrEngine? = null

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

    /**
     * Gancho de prueba (RF-05): recibe un broadcast con una palabra y muestra su
     * tarjeta de diccionario, simulando el resultado de un hold completado. Sirve
     * para verificar el camino diccionario->tarjeta en dispositivo. La detección
     * táctil del hold la valida HoldDetector(+gate). Solo activo en debug.
     */
    private val holdTestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val word = intent?.getStringExtra("word") ?: return
            showCardFor(word)
        }
    }

    /**
     * Gancho de prueba (RF-03): recibe un broadcast con la ruta de un asset de
     * imagen, corre el OCR local y registra el texto reconocido para medir CRR.
     * Evalúa el motor OCR aislado del capturador (que añadiría ruido propio).
     * Solo para desarrollo; restringir/eliminar antes de release.
     */
    private val ocrTestReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val asset = intent?.getStringExtra("asset") ?: return
            runOcrOnAsset(asset)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "AccessibilityService conectado")
        overlay = OverlayController(this).also { it.show() }
        dictionary = SqliteDictionary.open(this).also {
            val n = SqliteDictionary.loadHskIfNeeded(this, it)
            if (n > 0) Log.i(TAG, "RF05 diccionario HSK cargado: $n entradas")
        }
        registerReceiver(
            holdTestReceiver,
            IntentFilter(ACTION_HOLD_TEST),
            Context.RECEIVER_EXPORTED,
        )
        ocr = MlKitOcrEngine()
        registerReceiver(
            ocrTestReceiver,
            IntentFilter(ACTION_OCR_TEST),
            Context.RECEIVER_EXPORTED,
        )
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

    /** RF-05: consulta el diccionario y muestra la tarjeta sobre el overlay. */
    private fun showCardFor(word: String) {
        val dict = dictionary ?: return
        val t0 = SystemClock.elapsedRealtimeNanos()
        val card = DictionaryCardBuilder.build(word, dict)
        val lookupMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
        Log.i(TAG, "RF05 lookup word='$word' ms=$lookupMs found=${card != null}")
        if (card == null) return

        handler.post {
            val container = overlay?.contentView() as? ViewGroup ?: return@post
            cardView?.let { DictionaryCardView.dismiss(it) }
            val view = DictionaryCardView(this).also { it.bind(card) }
            val lp = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER }
            container.addView(view, lp)
            cardView = view
            Log.i(TAG, "RF05 card shown for '$word'")
            // Auto-cierre tras unos segundos (la interacción real la dará el toque).
            handler.postDelayed({ DictionaryCardView.dismiss(view) }, 4000)
        }
    }

    /**
     * RF-03 (gancho de prueba): carga una imagen de assets, corre OCR local,
     * registra el texto reconocido y la latencia, y recicla el Bitmap (RNF-03).
     */
    private fun runOcrOnAsset(asset: String) {
        val engine = ocr ?: return
        val bitmap = try {
            assets.open(asset).use { android.graphics.BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            Log.e(TAG, "RF03 no se pudo abrir asset '$asset'", e); return
        }
        if (bitmap == null) { Log.e(TAG, "RF03 bitmap nulo '$asset'"); return }
        val t0 = SystemClock.elapsedRealtimeNanos()
        engine.recognize(
            OcrImage(bitmap, bitmap.width, bitmap.height),
            onResult = { blocks: List<OcrBlock> ->
                val ms = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
                val text = blocks.joinToString("") { it.text }.replace("\\s".toRegex(), "")
                Log.i(TAG, "RF03 ocr asset='$asset' ms=$ms blocks=${blocks.size} text='$text'")
                bitmap.recycle() // RNF-03: liberar en cuanto termina la inferencia
            },
            onError = { e ->
                Log.e(TAG, "RF03 OCR error '$asset'", e)
                bitmap.recycle()
            },
        )
    }

    private fun onScroll() {
        val now = SystemClock.uptimeMillis()
        val changed = scroll.onScroll(now)
        if (changed) {
            val t0 = SystemClock.elapsedRealtimeNanos()
            overlay?.hideContent()
            val hideMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
            Log.i(TAG, "RF06 hide ms=$hideMs")
        }
        handler.removeCallbacks(quietCheck)
        handler.postDelayed(quietCheck, scroll.quietWindowMs)
    }

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

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(quietCheck)
        runCatching { unregisterReceiver(holdTestReceiver) }
        runCatching { unregisterReceiver(ocrTestReceiver) }
        ocr?.close()
        ocr = null
        dictionary?.close()
        dictionary = null
        overlay?.hide()
        overlay = null
        return super.onUnbind(intent)
    }

    companion object {
        const val TAG = "PinYinLens"
        const val ACTION_HOLD_TEST = "com.pinyinlens.HOLD_TEST"
        const val ACTION_OCR_TEST = "com.pinyinlens.OCR_TEST"
    }
}
