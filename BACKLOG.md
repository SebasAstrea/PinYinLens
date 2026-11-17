# BACKLOG — PinYinLens

Backlog derivado del SRS. Estados: `TODO → EN PROGRESO → EN PRUEBA → HECHO`.
Regla (AGENTS §1): un requisito a la vez; una historia solo pasa a HECHO si cumple la Definición de Hecho (AGENTS §5).

Leyenda de trazabilidad: cada historia cita el requisito del SRS que satisface.

---

## Épica 0 — Andamiaje de build

**E0 · Proyecto Gradle multi-módulo instalable por ADB** — `HECHO`
> No cubre un RF; es la base para todos.

Tareas:
- [x] T0.1 Estructura multi-módulo: `app` + módulos de dominio (`pinyin`, `dictionary`, `nodescan`, `ocr`, `overlay`, `gesture`, `render`).
- [x] T0.2 Gradle Wrapper anclado a 8.13; AGP 8.7.3 compatible; `settings.gradle.kts` con version catalog.
- [x] T0.3 `app` con `minSdk 29`, `targetSdk/compileSdk 36`, `applicationId com.pinyinlens`. Sin permiso `INTERNET`.
- [x] T0.4 `MainActivity` mínima + manifiesto + `AccessibilityService` base; `assembleDebug` produce APK (11.6 MB).
- [x] T0.5 `.gitignore`, `local.properties` (sdk.dir + ndk.dir), JDK 21 fijado por sesión.
- [x] T0.6 Prueba de humo: `installDebug` instala y lanza en dispositivo por ADB (SM-A226BR, Android 13; MainActivity como topResumedActivity, sin crashes).

Criterio de cierre: `assembleDebug` verde con JDK 21 y APK instalable por ADB. ✓ build verde; tests JVM (`pinyin`, `dictionary`) pasando; APK instalado y lanzado en dispositivo físico.

---

## Épica 1 — Requisitos funcionales (orden por dependencias, AGENTS §3)

### RF-01 · Overlay persistente — `HECHO`
Capa `TYPE_ACCESSIBILITY_OVERLAY` por encima de apps y sistema, con pass-through táctil.
- [x] `AccessibilityService` base + configuración XML.
- [x] Overlay transparente persistente vía `WindowManager` (`OverlayController`).
- [x] Pass-through de eventos táctiles subyacentes (`OverlayWindowSpec`, `FLAG_NOT_TOUCHABLE`).
- [x] Interacción separada del dibujo: la capa de dibujo nunca es táctil; solo
      ventanas minúsculas por etiqueta de pinyin (`PinyinTouchTargets`) reciben el
      hold. Navegación y scroll verificados en dispositivo (2026-11-17).
- [x] Pruebas unitarias JVM de la spec de ventana (`OverlayWindowSpecTest`) — verdes.
- [x] Prueba binomial unilateral (`stats/BinomialTest`) con tests unitarios — verdes.
- [x] e2e ADB (`scripts/rf01_overlay_e2e.ps1`): **P_overlay = 1.0 (100/100)**, p-valor = 0.0059 → **H₀: p ≤ 0.95 rechazada**. Evidencia en `artifacts/RF-01/rf01_result.txt`.
- Umbral SRS: P_overlay ≥ 0.98. **CUMPLIDO (1.0).**

> Nota de harness: el e2e se ejecuta por ADB externo, no como test instrumentado.
> Una instrumentación con `targetPackage=com.pinyinlens` reinicia el proceso de la
> app y desvincula el `AccessibilityService`, por lo que el overlay no puede
> observarse desde dentro. Verificado empíricamente (el proceso muere al instrumentar).

### RF-02 · Extracción de Hanzi y coordenadas — `HECHO (extracción)` / alineación diferida a RF-04
Inspección de `AccessibilityNodeInfo`, filtro CJK U+4E00–U+9FFF, `Rect` absolutos.
- [x] Recorrido del árbol de nodos (`AccessibilityTreeScanner`, DFS con límites).
- [x] Filtrado CJK y política de nodos (`NodePolicy`, reutiliza `pinyin/CjkText`).
- [x] Cálculo de coordenadas absolutas (`getBoundsInScreen` → `HanziSpan`).
- [x] Exclusión de `isPassword` (RNF-03) y de ventanas sin root (FLAG_SECURE).
- [x] Pruebas unitarias JVM (`NodePolicyTest`, `HanziSpanTest`) — verdes.
- [x] Conectado al `AccessibilityService` (traza `RF02 span` para el harness).
- [x] e2e ADB (`scripts/rf02_nodescan_e2e.ps1`), 14 pantallas del sistema en chino:
      **R_nativo = 0.9938 (160/161) ≥ 0.99**. Evidencia en `artifacts/RF-02/rf02_result.txt`.
- [ ] **Error de alineación ≤ 4 dp (prueba t, H₀: μ ≥ 4 dp):** requiere la proyección
      del pinyin → se mide en RF-04/render. Dependencia real, documentada.
- Umbral SRS: R_nativo ≥ 0.99 **CUMPLIDO**; error ≤ 4 dp pendiente de RF-04.

> Nota: R_nativo es una proporción (validación binomial/descriptiva); la prueba t
> del SRS aplica al error de alineación, no a R_nativo. Verdad medida con
> `uiautomator dump` independiente del servicio.

### RF-04 · Segmentación léxica y pinyin — `HECHO`
Segmentación (Jieba / Trie CC-CEDICT) + pinyin con tonos, desambiguación polifónica.
- [x] Trie CC-CEDICT (`Lexicon`) + segmentación máxima hacia adelante (`PinyinConverter`).
- [x] Conversión a pinyin con diacríticos (`ToneMarks`) y desambiguación tonal.
- [x] Polífonos del SRS verificados (行/长/着/了/地) en `PinyinConverterTest`.
- [x] Datos desacoplados del binario (`datasets/`, carga CC-CEDICT) — RNF-05.
- [x] Prueba Z de proporciones (`stats/ProportionZTest`) con tests — verdes.
- [x] **Gate estricto con dataset HSK 1-6 real (n=4991): A_pinyin = 0.9998
      (4990/4991), Z=22.17, p=0.0 → H₀: P ≤ 0.91 rechazada.**
      Léxico `hsk_lexicon.cedict` + corpus `hsk_pinyin_corpus.tsv` (de HSK i.n/i.y).
- Umbral SRS: A_pinyin ≥ 0.94 (n=500). **CUMPLIDO (0.9998, n=4991).**

> Evidencia en `artifacts/RF-04/rf04_apinyin_full.txt`. Único "fallo" (欧洲) es un
> dato HSK no estándar; el motor aplica la regla correcta.

### RF-06 · Gestión durante scroll — `HECHO`
Ocultar overlay ≤ 32 ms en `TYPE_VIEW_SCROLLED`/arrastre y regenerar al detenerse.
- [x] Detección de scroll y ocultamiento inmediato (`ScrollVisibilityController` +
      `OverlayController.hideContent`).
- [x] Regeneración al estabilizarse (debounce por ventana de reposo, `Handler`).
- [x] Pruebas unitarias JVM (`ScrollVisibilityControllerTest`) — verdes.
- [x] Prueba t de una muestra (`stats/OneSampleTTest`) con tests — verdes.
- [x] e2e ADB (`scripts/rf06_scroll_e2e.ps1`) + gate `Rf06TimingTest`:
      **n=102, μ_hide = 0.0104 ms, p = 0.0 → H₀: μ ≥ 32 ms rechazada.**
      Evidencia en `artifacts/RF-06/rf06_result.txt`.
- Umbral SRS: μ_hide ≤ 32 ms. **CUMPLIDO (0.0104 ms).**

### RF-05 · Diccionario bilingüe + hold 5 s — `HECHO (mecanismo + enganche)` / datos pendientes
Pulsación sostenida 5000±150 ms sobre etiqueta de pinyin → tarjeta Hanzi/pinyin/desglose/ES/EN.
- [x] Esquema SQLite CC-CEDICT + glosas ES/EN (`SqliteDictionary`), indexado y
      desacoplado del binario (RNF-05). Semilla `DictionarySeed`.
- [x] Consulta indexada < 15 ms (RNF-01): media 1.76 ms, máx 3.52 ms (20 consultas).
- [x] Detección de hold 5 s (`HoldDetector`), rechaza taps/scroll sin propagar clic.
- [x] Lógica de tarjeta pura (`DictionaryCard`/`DictionaryCardBuilder`) + vista
      `DictionaryCardView`. Verificada en dispositivo (captura `rf05_card.png`).
- [x] Pruebas unitarias JVM (`HoldDetectorTest`, `DictionaryCardBuilderTest`) — verdes.
- [x] Gate binomial (`HoldDetectorAcceptanceTest`): **T_hold = 1.0 (100/100),
      p = 7.05e-4 → H₀: P ≤ 0.93 rechazada; 0 falsos positivos en n=200.**
- [x] **C_dic = 1.0 (4991/4991) ≥ 0.98 (HSK 1–6):** diccionario cargado desde
      `hsk_dictionary.tsv` (asset) a SQLite; verificado en dispositivo (4991
      entradas, consulta < 4 ms). Harness `scripts/rf05_cdic_e2e.ps1`.
- [x] **Enganche del hold a las zonas de pinyin dibujadas:** la vista publica
      `PinyinHitbox` por etiqueta y el servicio crea una ventana táctil minúscula
      (`PinyinTouchTargets`) encima de cada una; el resto de la pantalla queda
      pass-through. Verificado en dispositivo: hold 5.2 s sobre 设置 abre la
      tarjeta (lookup 3.76 ms); tap rápido no abre nada. Evidencia:
      `artifacts/RF-05/rf05_hold_card_live.png`, `rf05_hold_no_false_positive.png`.
- Umbral SRS: T_hold ≥ 0.97 **CUMPLIDO**, 0% FP **CUMPLIDO**, C_dic = 1.0 **CUMPLIDO**.

> Evidencia en `artifacts/RF-05/` (reporte + captura de la tarjeta).
> Cobertura de datos: el TSV HSK incluido no contiene términos de UI como
> 连接/屏幕; esas etiquetas no muestran tarjeta hasta cargar un CC-CEDICT
> completo (RNF-05, datos desacoplados). El mecanismo `lookupContained` ya
> resuelve frases cuando el término existe.

### RF-03 · OCR local sobre imágenes — `HECHO (motor+CRR)` / captura MediaProjection pendiente de verificación e2e
ImageView/WebView sin texto → debounce 400 ms → `MediaProjection` 1 frame → ROI → OCR local.
- [x] Motor OCR local ML Kit Chinese **bundled** (`MlKitOcrEngine`): modelo en el
      APK (`libmlkit_google_ocr_pipeline.so`), sin descarga ni INTERNET (RNF-03).
- [x] Selección de ROI (`OcrTriggerPolicy`) y debounce 400 ms (`OcrDebounce`), lógica
      pura con tests JVM (`OcrTriggerPolicyTest`, `OcrDebounceTest`).
- [x] Captura `MediaProjection` de 1 frame + recorte ROI + `recycle()` inmediato
      (`ScreenCapturer`, RNF-03), con Activity de permiso y foreground service.
- [x] Prueba t (`stats/OneSampleTTest.upperTail`) con tests; gate binomial para CRR.
- [x] e2e ADB (`scripts/rf03_ocr_e2e.ps1`) + gate `Rf03OcrTest`:
      **CRR = 0.9419 (243/258) ≥ 0.92, binomial p = 0.012 → H₀: p ≤ 0.90 rechazada.**
      Latencia P95 = 102.6 ms ≤ 250 ms (RNF-01). Evidencia en `artifacts/RF-03/`.
- [ ] **Verificación e2e de la cadena de captura real** (permiso → VirtualDisplay →
      ROI de pantalla): el motor OCR se validó sobre imágenes de assets; falta la
      corrida con captura física de pantalla (requiere conceder MediaProjection).
- Umbral SRS: CRR ≥ 0.92 **CUMPLIDO (0.9419)**; latencia P95 ≤ 250 ms **CUMPLIDO**.

> Nota: CRR se valida con binomial (proporción de caracteres), no con la t del
> SRS; ver AGENTS §6. Un fallo típico de ML Kit es simplificado↔tradicional.

---

## Épica 2 — UI de control

### Render de pinyin (transversal RF-02/RF-04/RNF-04) — `HECHO (mecanismo)`
Dibujo del pinyin debajo de los caracteres, con escalado y contraste.
- [x] `PinyinLayout` (puro, tests JVM): tamaño derivado del Hanzi real, ajuste
      al ancho de la palabra, posición **debajo** anclada a la tinta, reparto de
      huecos compartidos, descarte si no cabe sin solapar.
- [x] `TextSizeEstimator` + `extraRenderingInfo` (API 33+) para el tamaño real
      del Hanzi, con presupuesto de refrescos por escaneo.
- [x] `Contrast` (WCAG, relación ≥ 4.5:1) — tests JVM.
- [x] `PinyinOverlayView` (stroke de alto contraste + relleno, compensación del
      origen de la ventana) en el overlay.
- [x] Cableado servicio: spans RF-02 → `PinyinConverter` RF-04 → vista; léxico HSK (asset).
      Se envían también los spans sin pinyin para usarlos como obstáculos.
- [x] Re-escaneo diferido (400 ms) para que las animaciones de navegación no
      dejen las etiquetas desplazadas; y mostrar la capa al cambiar de ventana
      (antes quedaba oculta tras un scroll).
- [x] Verificado en dispositivo: `artifacts/render/s8_settings.png` (lista densa),
      `v_lockscreen.png` (filas de una línea), `h1_home.png` (launcher).
      Informe: `artifacts/render/render_result.txt`.
- [ ] Cobertura de léxico por carácter (RF-04): 主屏幕, 锁定, 定位, 指纹, 帐户,
      微信, 相册 salen incompletos.
- [ ] Alineación **por carácter** (hoy se centra en la palabra).
- [ ] Medición formal del **error ≤ 4 dp (RF-02)** con harness, ahora que el pinyin se dibuja.

> Desviación documentada de RNF-04: la UX actual usa el tamaño del Hanzi
> (`HANZI_SIZE_FACTOR = 1.0`) en vez del 45–60 % del SRS; el ajuste de ancho lo
> deja en ese rango para palabras largas. Poner el factor a 0.6 restaura el SRS.

### UI-01 · Activar/desactivar el servicio — `TODO`
Una de las dos (a decidir en diseño):
- [ ] Notificación persistente con acción on/off, **o**
- [ ] Bola flotante que se oculta/reaparece con gesto.

---

## Épica 3 — Endurecimiento RNF

### RNF · Rendimiento, recursos, privacidad, usabilidad — `TODO`
- [ ] Latencia texto nativo P95 ≤ 50 ms (RNF-01).
- [ ] Latencia OCR P95 ≤ 250 ms (RNF-01).
- [ ] RAM activa < 180 MB, reposo < 60 MB; CPU reposo 0.0% (RNF-02).
- [ ] Batería < 6% extra/hora (RNF-02).
- [ ] Privacidad: solo RAM, `recycle()`, sin disco/red (RNF-03).
- [ ] Contraste WCAG AA 4.5:1 y escalado 45–60% con mínimo 9 sp (RNF-04).
- [ ] Compatibilidad API 29–36, accesibilidad restringida 13+ (RNF-05).
