package com.pinyinlens.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import com.pinyinlens.dict.SqliteDictionary
import com.pinyinlens.dictionary.DictionaryCard
import com.pinyinlens.dictionary.DictionaryCardBuilder
import com.pinyinlens.gesture.ScrollVisibilityController
import com.pinyinlens.nodescan.AccessibilityTreeScanner
import com.pinyinlens.nodescan.HanziSpan
import com.pinyinlens.ocr.MlKitOcrEngine
import com.pinyinlens.ocr.OcrBlock
import com.pinyinlens.ocr.OcrDebounce
import com.pinyinlens.ocr.OcrEngine
import com.pinyinlens.ocr.OcrImage
import com.pinyinlens.ocr.ProjectionHolder
import com.pinyinlens.ocr.ScreenCapturer
import com.pinyinlens.overlay.OverlayController
import com.pinyinlens.overlay.OverlayPanel
import com.pinyinlens.pinyin.CharPinyin
import com.pinyinlens.pinyin.Lexicon
import com.pinyinlens.pinyin.PinyinConverter
import com.pinyinlens.render.PinyinItem
import com.pinyinlens.render.PinyinOverlayView
import com.pinyinlens.render.PinyinTouchTargets
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
    private var cardPanel: OverlayPanel? = null
    private var cardOpen = false
    private var cardDismiss: Runnable? = null
    private var ocr: OcrEngine? = null
    private var pinyinConverter: PinyinConverter? = null
    private var pinyinView: PinyinOverlayView? = null
    private var touchTargets: PinyinTouchTargets? = null

    // RF-03: cadena de OCR sobre imagen sin texto (candidatos -> debounce ->
    // captura MediaProjection de 1 frame -> ROI -> OCR local -> pinyin).
    private var ocrItems: List<PinyinItem> = emptyList()
    private var lastOcrRoi: Rect? = null
    private var pendingOcrRoi: Rect? = null
    private val ocrDebounce = OcrDebounce()
    private var mediaProjection: MediaProjection? = null

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
     * Re-escaneo diferido tras un cambio de ventana/contenido. Al navegar, la
     * app anima su cabecera (p. ej. toolbar que colapsa) y el primer escaneo
     * puede coger coordenadas intermedias: la etiqueta quedaría unos píxeles
     * sobre el Hanzi. Un único escaneo cuando el layout ya está quieto corrige
     * la posición sin sondeo continuo (RNF-02).
     */
    private val settleScan = Runnable { extractHanzi() }

    /**
     * Comprobación diferida del OCR (RF-03): se ejecuta 400 ms tras la última
     * actividad (candidato detectado, toque o scroll). Si en ese momento la
     * quietud aún no se ha cumplido, se re-agenda al instante exacto en que se
     * cumpla, sin sondeo continuo (RNF-02).
     */
    private val ocrCheck: Runnable = Runnable {
        val roi = pendingOcrRoi ?: return@Runnable
        val now = SystemClock.uptimeMillis()
        val quietAt = ocrDebounce.quietCompletesAt(now) ?: return@Runnable
        if (now < quietAt) {
            handler.postDelayed(ocrCheck, quietAt - now)
            return@Runnable
        }
        if (!ocrDebounce.shouldFire(now)) return@Runnable
        pendingOcrRoi = null
        captureOcr(roi)
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
        cardPanel = OverlayPanel(this)
        // RF-01/RF-05: la capa de dibujo es pass-through; la interacción vive en
        // ventanas minúsculas, una por etiqueta de pinyin (hold de 5 s).
        touchTargets = PinyinTouchTargets(this).also { targets ->
            targets.onHold = { word -> showCardFor(word) }
        }
        // RF-04/render: lienzo de pinyin dentro del overlay + motor de conversión.
        (overlay?.contentView() as? ViewGroup)?.let { container ->
            val view = PinyinOverlayView(this)
            // RF-05: cada etiqueta dibujada publica su zona táctil. Con la
            // tarjeta abierta se retiran para que no queden encima de ella.
            view.onHitboxes = { boxes ->
                if (cardOpen) touchTargets?.clear() else touchTargets?.update(boxes)
            }
            container.addView(
                view,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT,
                ),
            )
            pinyinView = view
        }
        pinyinConverter = loadPinyinConverter()
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
        // Las propias ventanas del overlay (capa de dibujo y zonas táctiles)
        // generan eventos de contenido al añadirse o retirarse. Re-escanear por
        // ellos realimenta el ciclo y puede retirar una zona táctil en pleno
        // hold: se ignoran.
        if (event.packageName?.toString() == packageName) return
        when (event.eventType) {
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> onScroll()
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                    // Nueva ventana: la tarjeta de la anterior no aplica y los
                    // resultados OCR quedan obsoletos.
                    dismissCard(rebuildTargets = false)
                    ocrItems = emptyList()
                    lastOcrRoi = null
                    pendingOcrRoi = null
                    ocrDebounce.reset()
                }
                scroll.reset()
                // Un cambio de contenido/ventana también termina cualquier
                // ocultamiento por scroll (RF-06): si no, tras ocultar la capa
                // una navegación la dejaba invisible para siempre.
                overlay?.showContent()
                extractHanzi()
                handler.removeCallbacks(settleScan)
                handler.postDelayed(settleScan, SETTLE_RESCAN_MS)
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

        handler.post { showCardView(card) }
    }

    /**
     * Muestra la tarjeta en su propia ventana (RF-05). Al abrirla se retiran las
     * zonas táctiles del pinyin para que no queden por encima; al cerrarla se
     * reconstruyen desde el último layout.
     */
    private fun showCardView(card: DictionaryCard) {
        val panel = cardPanel ?: return
        cardDismiss?.let { handler.removeCallbacks(it) }
        cardView?.let {
            it.onDismiss = null
            panel.hide()
        }
        cardView = null
        cardOpen = true
        touchTargets?.clear()

        val view = DictionaryCardView(this).also {
            it.onDismiss = { dismissCard() }
            it.bind(card)
        }
        panel.show(view)
        cardView = view
        Log.i(TAG, "RF05 card shown for '${card.simplified}'")

        // Auto-cierre de respaldo (el cierre real lo da el toque sobre la tarjeta).
        val autoClose = Runnable {
            if (cardView === view) dismissCard()
        }
        cardDismiss = autoClose
        handler.postDelayed(autoClose, CARD_AUTO_CLOSE_MS)
    }

    /** Cierra la tarjeta y, opcionalmente, reconstruye las zonas táctiles. */
    private fun dismissCard(rebuildTargets: Boolean = true) {
        cardDismiss?.let { handler.removeCallbacks(it) }
        cardDismiss = null
        val wasOpen = cardOpen
        cardOpen = false
        cardView?.let {
            it.onDismiss = null
            cardPanel?.hide()
        }
        cardView = null
        if (wasOpen && rebuildTargets) extractHanzi()
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
        // La tarjeta y las zonas táctiles no deben quedar flotando sobre el
        // contenido mientras la capa está oculta (RF-05/RF-06).
        dismissCard(rebuildTargets = false)
        touchTargets?.clear()
        val changed = scroll.onScroll(now)
        if (changed) {
            val t0 = SystemClock.elapsedRealtimeNanos()
            overlay?.hideContent()
            val hideMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
            Log.i(TAG, "RF06 hide ms=$hideMs")
        }
        handler.removeCallbacks(quietCheck)
        handler.postDelayed(quietCheck, scroll.quietWindowMs)
        // RF-03: un scroll es actividad táctil; el OCR espera a que termine la
        // inactividad (debounce 400 ms). quietCheck -> extractHanzi re-agenda.
        ocrDebounce.onTouch(now)
        handler.removeCallbacks(ocrCheck)
    }

    /**
     * Carga el motor de pinyin con el léxico HSK y la tabla de respaldo por
     * carácter, ambos empaquetados como assets (RNF-05: datos fuera del código).
     */
    private fun loadPinyinConverter(): PinyinConverter {
        val lexicon = try {
            assets.open("hsk_lexicon.cedict").bufferedReader(Charsets.UTF_8).useLines {
                Lexicon.fromCcCedict(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo cargar hsk_lexicon.cedict", e)
            Lexicon()
        }
        // Respaldo por carácter: cubre los Hanzi que no forman parte de ninguna
        // palabra del léxico (三星, 帐/户, 屏/幕, 定/位…), sin los que la etiqueta
        // salía corta o con una lectura equivocada.
        val charFallback = try {
            assets.open("char_pinyin.tsv").bufferedReader(Charsets.UTF_8).useLines {
                CharPinyin.fromTsv(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo cargar char_pinyin.tsv", e)
            emptyMap()
        }
        Log.i(
            TAG,
            "RF04 léxico cargado: ${lexicon.size} entradas, respaldo: ${charFallback.size} caracteres",
        )
        return PinyinConverter(lexicon, charFallback)
    }

    private fun extractHanzi() {
        val root = rootInActiveWindow ?: return
        val t0 = SystemClock.elapsedRealtimeNanos()
        val spans: List<HanziSpan> = scanner.scanFromRoot(root)
        val scanMs = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
        val converter = pinyinConverter
        if (spans.isNotEmpty() && converter != null) {
            Log.i(TAG, "RF02 spans=${spans.size} scanMs=$scanMs")
            // RF-04/render: convertir cada span a pinyin y dibujarlo alineado.
            // Se envían TODOS los spans, también los que se quedan sin pinyin
            // (subtítulos, texto sin entrada léxica): no se les dibuja etiqueta,
            // pero el layout los necesita como obstáculos para no pisarlos.
            val items = ArrayList<PinyinItem>(spans.size + ocrItems.size)
            for (s in spans) {
                val py = converter.toPinyin(s.text)
                items.add(PinyinItem(py, s.left, s.top, s.right, s.bottom, s.textSizePx, s.text))
                Log.v(TAG, "RF02 span text='${s.text}' rect=${s.left},${s.top},${s.right},${s.bottom} size=${s.textSizePx} py='$py'")
            }
            // RF-03: los bloques OCR de imágenes sin texto se dibujan junto a
            // los spans nativos (y también son objetivo del hold, RF-05).
            items.addAll(ocrItems)
            pinyinView?.setItems(items)
        } else {
            if (ocrItems.isEmpty()) pinyinView?.clearItems() else pinyinView?.setItems(ocrItems)
        }
        // RF-03: si hay contenedores gráficos sin texto, programar el OCR
        // (debounce de inactividad de 400 ms antes de capturar).
        maybeScheduleOcr(scanner.scanOcrCandidates(root))
    }

    /**
     * RF-03: programa la captura OCR si hay candidatos y el ROI ha cambiado
     * respecto al último capturado. El disparo real ocurre 400 ms tras la
     * última actividad (toque/scroll/cambio de contenido), sin polling.
     */
    private fun maybeScheduleOcr(candidates: List<Rect>) {
        val roi = candidates.maxByOrNull { it.width().toLong() * it.height() }
        if (roi == null) {
            pendingOcrRoi = null
            ocrDebounce.reset()
            return
        }
        // Ya hay OCR para esta región: no recapturar en cada cambio de contenido.
        if (roi == lastOcrRoi && ocrItems.isNotEmpty()) {
            pendingOcrRoi = null
            return
        }
        pendingOcrRoi = roi
        ocrDebounce.onTouch(SystemClock.uptimeMillis())
        handler.removeCallbacks(ocrCheck)
        handler.postDelayed(ocrCheck, ocrDebounce.windowMs)
    }

    /**
     * RF-03: captura UN frame de la ROI con MediaProjection, recorta y corre el
     * OCR local. Privacidad (RNF-03): el frame vive solo en RAM y se recicla en
     * cuanto termina la inferencia; nunca se escribe a disco.
     */
    private fun captureOcr(roi: Rect) {
        if (!ProjectionHolder.isGranted) {
            Log.i(TAG, "RF03 candidato a OCR pero sin permiso de MediaProjection; se omite")
            return
        }
        val mpm = getSystemService(MediaProjectionManager::class.java) ?: return
        val projection = mediaProjection ?: run {
            val data = ProjectionHolder.data
            if (data == null) {
                Log.w(TAG, "RF03 no se pudo obtener la MediaProjection")
                return
            }
            val p = mpm.getMediaProjection(ProjectionHolder.resultCode, data) ?: return
            mediaProjection = p
            p
        }
        val dm = resources.displayMetrics
        val t0 = SystemClock.elapsedRealtimeNanos()
        ScreenCapturer(projection, dm.widthPixels, dm.heightPixels, dm.densityDpi)
            .captureRegion(roi) { bmp ->
                if (bmp == null) {
                    Log.w(TAG, "RF03 no llegó frame de captura")
                    return@captureRegion
                }
                val engine = ocr
                if (engine == null) {
                    bmp.recycle()
                    return@captureRegion
                }
                engine.recognize(
                    OcrImage(bmp, bmp.width, bmp.height),
                    onResult = { blocks ->
                        val ms = (SystemClock.elapsedRealtimeNanos() - t0) / 1_000_000.0
                        val converter = pinyinConverter
                        val items = blocks.mapNotNull { b ->
                            val py = converter?.toPinyin(b.text) ?: ""
                            if (py.isBlank()) null
                            else PinyinItem(
                                pinyin = py,
                                left = b.left + roi.left,
                                top = b.top + roi.top,
                                right = b.right + roi.left,
                                bottom = b.bottom + roi.top,
                                word = b.text,
                            )
                        }
                        bmp.recycle() // RNF-03: liberar en cuanto termina la inferencia
                        Log.i(TAG, "RF03 ocr pantalla ms=$ms blocks=${blocks.size} items=${items.size}")
                        if (items.isNotEmpty()) {
                            lastOcrRoi = Rect(roi)
                            ocrItems = items
                            handler.post { extractHanzi() }
                        }
                    },
                    onError = { e ->
                        bmp.recycle()
                        Log.e(TAG, "RF03 error OCR de pantalla", e)
                    },
                )
            }
    }

    override fun onInterrupt() {
        Log.i(TAG, "AccessibilityService interrumpido")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(quietCheck)
        handler.removeCallbacks(settleScan)
        handler.removeCallbacks(ocrCheck)
        cardDismiss?.let { handler.removeCallbacks(it) }
        cardDismiss = null
        cardOpen = false
        cardView = null
        cardPanel?.release()
        cardPanel = null
        touchTargets?.release()
        touchTargets = null
        pinyinView?.release()
        pinyinView = null
        runCatching { unregisterReceiver(holdTestReceiver) }
        runCatching { unregisterReceiver(ocrTestReceiver) }
        ocr?.close()
        ocr = null
        mediaProjection?.stop()
        mediaProjection = null
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

        /** Margen para que la animación de navegación de la app termine. */
        private const val SETTLE_RESCAN_MS = 400L

        /** Auto-cierre de respaldo de la tarjeta (RF-05). */
        private const val CARD_AUTO_CLOSE_MS = 8000L
    }
}
