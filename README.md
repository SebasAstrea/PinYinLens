# PinYinLens

Asistente persistente de lectura de chino simplificado para Android. Superpone pinyin con tonos encima de cualquier texto Hanzi en pantalla (apps de terceros, sistema, imágenes) y ofrece una tarjeta de diccionario bilingüe (Español/Inglés) al mantener pulsada una palabra. Todo el procesamiento es **100% local y offline**.

## Qué resuelve

Un lector de chino intermedio encuentra constantemente caracteres cuya pronunciación o significado no recuerda. Las soluciones actuales obligan a copiar texto a otra app, no funcionan sobre imágenes ni sobre la interfaz del sistema, y a menudo envían la pantalla a un servidor. PinYinLens anota el pinyin *in situ*, sin salir de la app que estés usando y sin que ningún dato de pantalla salga del dispositivo.

## Cómo funciona

La arquitectura es híbrida y estrictamente orientada a eventos (sin *polling*, 0% CPU en reposo):

```
                 ┌──────────────────────────────────────────────┐
                 │            AccessibilityService               │
                 │  (escucha WINDOW_CONTENT_CHANGED / SCROLLED)   │
                 └───────────────┬──────────────────────────────┘
                                 │
        ┌────────────────────────┴───────────────────────┐
        │                                                 │
  Texto nativo (RF-02)                            Sin texto (RF-03)
  Inspecciona AccessibilityNodeInfo,              ImageView/WebView →
  filtra CJK U+4E00–U+9FFF,                       MediaProjection captura
  extrae Rect absolutos                           1 frame → recorta ROI →
        │                                         OCR local (ML Kit / RapidOCR)
        │                                                 │
        └────────────────────────┬────────────────────────┘
                                 │
                    Segmentación léxica + Pinyin (RF-04)
                    Jieba / Trie CC-CEDICT, desambiguación tonal
                                 │
                    Capa TYPE_ACCESSIBILITY_OVERLAY (RF-01)
                    Dibuja pinyin alineado, con pass-through táctil
                                 │
         ┌───────────────────────┴───────────────────────┐
   Scroll (RF-06)                              Hold 5 s (RF-05)
   Oculta overlay ≤32 ms,                      Tarjeta diccionario:
   regenera al parar                           Hanzi, pinyin, desglose,
                                               traducción ES/EN (SQLite)
```

### Módulos principales

| Módulo | Responsabilidad | Requisito |
|--------|-----------------|-----------|
| `overlay` | Capa transparente persistente con pass-through táctil | RF-01 |
| `nodescan` | Extracción de Hanzi y coordenadas del árbol de accesibilidad | RF-02 |
| `ocr` | Captura por MediaProjection + OCR local sobre imágenes | RF-03 |
| `pinyin` | Segmentación léxica y conversión contextual a pinyin | RF-04 |
| `dictionary` | Diccionario SQLite CC-CEDICT + glosas ES/EN, tarjeta modal | RF-05 |
| `gesture` | Pulsación sostenida 5 s y gestión de scroll | RF-05, RF-06 |
| `render` | Dibujado del pinyin (contraste WCAG AA, escalado adaptativo) | RNF-04 |
| `ui` | Control de activación del servicio (ver más abajo) | — |

## Control de activación (UI)

El servicio se activa/desactiva mediante una de estas dos vías (a decidir durante el diseño, ambas poco invasivas):

1. **Notificación persistente** con acción de encender/apagar el overlay.
2. **Bola flotante** que se esconde y reaparece con un gesto del dedo.

## Requisitos de plataforma

- Android 10 (API 29) hasta Android 16 (API 36).
- Permisos: `BIND_ACCESSIBILITY_SERVICE`, `SYSTEM_ALERT_WINDOW`, captura de `MediaProjection`. **No** se solicita `INTERNET` en el núcleo de procesamiento (RNF-03).
- Ignora nodos `isPassword` y ventanas `FLAG_SECURE` (banca, gestores de contraseñas).

## Objetivos de calidad (del SRS)

Cada requisito tiene un umbral medible validado estadísticamente (α = 0.05). Resumen:

- Latencia texto nativo P95 ≤ 50 ms; OCR P95 ≤ 250 ms; consulta diccionario < 15 ms.
- Precisión pinyin ≥ 94%, CRR OCR ≥ 92%, detección de nodos ≥ 99%.
- RAM activa < 180 MB, reposo < 60 MB; batería < 6% extra/hora; CPU en reposo 0.0%.

La especificación completa está en [`SRS_doc.md`](./SRS_doc.md).

## Entorno de construcción

Todas las herramientas están bajo `C:\Users\Sebastian`:

- **JDK:** `C:\Users\Sebastian\jdk-21` (recomendado para el Android Gradle Plugin).
- **Gradle:** 9.8.2 (`gradle-9.8.2`) o 8.13 según compatibilidad del AGP.
- **Android SDK:** `C:\Users\Sebastian\android-sdk` — build-tools 35.0.0 / 36.0.0, platform `android-36`.
- **NDK:** `C:\Users\Sebastian\android-ndk-r30` (solo necesario si RF-03 usa RapidOCR INT8).
- **ADB:** `C:\Users\Sebastian\platform-tools\adb.exe`.

Compilación e instalación básicas:

```powershell
# Debug build
.\gradlew.bat assembleDebug

# Instalar en dispositivo conectado por ADB
.\gradlew.bat installDebug
```

La configuración concreta de `local.properties`, `JAVA_HOME` y la metodología de trabajo (backlog, pruebas unitarias por módulo y e2e por ADB) están en [`AGENTS.md`](./AGENTS.md).

## Créditos de datos y licencias

El diccionario se construye con `scripts/build_dictionary.ps1` a partir de fuentes de terceros, que deben acreditarse:

- **CC-CEDICT** — diccionario chino-inglés comunitario (MDBG). Licencia CC BY-SA 4.0.
- **complete-hsk-vocabulary** (drkameleon) — listas HSK 1–6 con pinyin e inglés.
- **Apertium** (par `eng-spa`) — traducción automática inglés→español de las glosas, en tiempo de build. Licencia GPL-2.0.

El español de las glosas es traducción automática (mejor-esfuerzo); el inglés y el pinyin provienen de las fuentes HSK/CC-CEDICT. Todo el procesamiento en el dispositivo es offline; estas fuentes solo se usan al construir la base.
