# Especificación de Requisitos de Software (SRS)
**Proyecto:** Sistema Persistente de Asistencia de Lectura en Chino Simplificado (Pinyin & Diccionario Contextual) 
**Nombre**: PinYinLens 
**Plataforma:** Android (API Level $\ge 29$ / Android 10+)  
**Arquitectura:** Híbrida (`AccessibilityService` + `MediaProjection` + OCR Local + SQLite)

---

## 1. Definiciones Estadísticas y Protocolo de Control de Calidad (QA)

Para garantizar la objetividad en las pruebas de aceptación de los Requisitos Funcionales (RF), cada métrica crítica será evaluada mediante pruebas de hipótesis sobre un tamaño de muestra representativo ($n \ge 100$ pantallas/capturas diversas).

* **Nivel de significancia ($\alpha$):** $0.05$ ($p \le 0.05$).
* **Criterio de Aprobación:** Se rechaza la hipótesis nula ($H_0$: el sistema no alcanza el umbral mínimo de calidad) en favor de la hipótesis alternativa ($H_1$: el rendimiento/precisión observado supera el umbral establecido) mediante una prueba binomial unilateral o prueba $t$ de Student de una muestra, según corresponda a la naturaleza de la variable (proporción o continua).
* **Entorno de Prueba Base:** Dispositivo Android físico de gama media (SoC $\ge 6$ núcleos, $\ge 4\text{ GB}$ RAM, densidad de pantalla $\ge 400\text{ dpi}$).

---

## 2. Requisitos Funcionales (RF)

### RF-01: Persistencia y Superposición sobre la Interfaz del Sistema
* **Descripción:** El sistema debe renderizar una capa gráfica transparente (`TYPE_ACCESSIBILITY_OVERLAY`) persistente por encima de todas las aplicaciones de terceros y de la interfaz nativa del sistema operativo (panel de notificaciones, ajustes del sistema, menús emergentes y pantalla de inicio).
* **Criterio de Aceptación (QA):**
  * **Métrica:** Tasa de éxito de renderizado de capa superior ($P_{\text{overlay}}$) sin pérdida de foco ni bloqueo de eventos táctiles subyacentes (*pass-through*).
  * **Umbral:** $P_{\text{overlay}} \ge 0.98$ ($98\%$) en $n = 100$ transiciones entre ventanas del sistema y apps externas.
  * **Validación Estadística:** Prueba binomial unilateral con $H_0: P_{\text{overlay}} \le 0.95$ vs $H_1: P_{\text{overlay}} > 0.95$, exigiendo $p \le 0.05$.

### RF-02: Extracción Nativa de Caracteres Hanzi y Coordenadas en Tiempo Real
* **Descripción:** El servicio de accesibilidad debe inspeccionar el árbol de nodos (`AccessibilityNodeInfo`) activo, filtrar cadenas que contengan caracteres Unicode del bloque CJK Unified Ideographs (`U+4E00` a `U+9FFF`) y extraer sus coordenadas absolutas en pantalla (`Rect`).
* **Criterio de Aceptación (QA):**
  * **Métrica:** Precisión espacial (IoU - *Intersection over Union*) entre el cuadro delimitador del texto nativo y la proyección del pinyin superior, y tasa de detección de nodos visibles ($R_{\text{nativo}}$).
  * **Umbral:** $R_{\text{nativo}} \ge 0.99$ ($99\%$) y error de alineación vertical $\le 4\text{ dp}$.
  * **Validación Estadística:** Prueba $t$ de una muestra sobre el error de desplazamiento en píxeles ($H_0: \mu_{\text{error}} \ge 4\text{ dp}$ vs $H_1: \mu_{\text{error}} < 4\text{ dp}$) con $n = 150$ nodos y $p \le 0.05$.

### RF-03: Reconocimiento Óptico de Caracteres (OCR) Local en Imágenes
* **Descripción:** Ante la detección de contenedores gráficos (`ImageView`, `WebView` con contenido multimedia o galerías) sin nodos de texto asociados, y tras detectar inactividad táctil (*debounce* de $400\text{ ms}$), el sistema debe capturar un fotograma único mediante `MediaProjection`, recortar la región de interés (ROI) y ejecutar un modelo OCR local (ML Kit Chinese v2 o RapidOCR INT8) para extraer Hanzi y sus coordenadas.
* **Criterio de Aceptación (QA):**
  * **Métrica:** Tasa de Reconocimiento de Caracteres (CRR - *Character Recognition Rate*) en imágenes estáticas con texto horizontal impreso (capturas de chat, infografías, menús en imagen).
  * **Umbral:** $\text{CRR} \ge 0.92$ ($92\%$) sobre un conjunto de prueba estándar de $n = 200$ imágenes con ruido/compresión variable.
  * **Validación Estadística:** Prueba $t$ unilateral de una muestra ($H_0: \mu_{\text{CRR}} \le 0.90$ vs $H_1: \mu_{\text{CRR}} > 0.90$), exigiendo $p \le 0.05$.

### RF-04: Segmentación Léxica y Conversión Contextual a Pinyin
* **Descripción:** El sistema debe segmentar las cadenas continuas de Hanzi en unidades léxicas (palabras) mediante un motor local (Jieba / Trie CC-CEDICT) y asignar el Pinyin con marcas diacríticas de tono correctas (resolviendo caracteres polifónicos según contexto), dibujándolo inmediatamente encima de cada palabra detectada.
* **Criterio de Aceptación (QA):**
  * **Métrica:** Exactitud de desambiguación tonal y segmentación ($A_{\text{pinyin}}$) evaluada contra un corpus de referencia anotado manualmente (incluyendo caracteres polifónicos frecuentes como 行, 长, 着, 了, 地).
  * **Umbral:** $A_{\text{pinyin}} \ge 0.94$ ($94\%$) en $n = 500$ oraciones de uso cotidiano y técnico.
  * **Validación Estadística:** Prueba de proporciones Z unilateral ($H_0: P_{\text{pinyin}} \le 0.91$ vs $H_1: P_{\text{pinyin}} > 0.91$), exigiendo $p \le 0.05$.

### RF-05: Activación por Pulsación Sostenida (5 Segundos) y Tarjeta Diccionario Bilingüe
* **Descripción:** Al mantener presionado durante $5000\text{ ms}$ ($\pm 150\text{ ms}$) sobre cualquier etiqueta de Pinyin renderizada (tanto de texto nativo como de imagen), el sistema debe interceptar el gesto sin propagar un clic accidental a la app inferior y desplegar un cuadro flotante modal con: palabra en Hanzi (Simplificado/Tradicional), Pinyin, desglose por carácter, traducción al Español y traducción al Inglés.
* **Criterio de Aceptación (QA):**
  * **Métrica:** Tasa de activación exitosa sin falsos positivos durante navegación normal ($T_{\text{hold}}$) y tasa de completitud de datos bilingües ($C_{\text{dic}}$) para vocabulario HSK 1–6.
  * **Umbral:** $T_{\text{hold}} \ge 0.97$ ($97\%$) en $n = 100$ intentos de pulsación sostenida; $0\%$ de falsos positivos en $n = 200$ gestos de *scroll* o toque rápido ($< 500\text{ ms}$); $C_{\text{dic}} \ge 0.98$ para términos HSK 1–6.
  * **Validación Estadística:** Prueba binomial unilateral ($H_0: P_{\text{hold}} \le 0.93$ vs $H_1: P_{\text{hold}} > 0.93$), exigiendo $p \le 0.05$.

### RF-06: Gestión Dinámica durante Desplazamiento (Scroll)
* **Descripción:** Al detectar eventos de desplazamiento (`TYPE_VIEW_SCROLLED`) o gestos de arrastre en pantalla, el sistema debe ocultar temporalmente la capa de Pinyin en $\le 32\text{ ms}$ y regenerarla automáticamente al detenerse el movimiento para evitar desfase visual.
* **Criterio de Aceptación (QA):**
  * **Métrica:** Tiempo de respuesta de ocultamiento ($t_{\text{hide}}$) ante el inicio del evento de *scroll*.
  * **Umbral:** $\mu_{t_{\text{hide}}} \le 32\text{ ms}$ (equivalente a $\le 2$ fotogramas a $60\text{ Hz}$) en $n = 100$ eventos de desplazamiento.
  * **Validación Estadística:** Prueba $t$ unilateral de una muestra ($H_0: \mu_{t_{\text{hide}}} \ge 32\text{ ms}$ vs $H_1: \mu_{t_{\text{hide}}} < 32\text{ ms}$), exigiendo $p \le 0.05$.

---

## 3. Requisitos No Funcionales (RNF)

### RNF-01: Rendimiento y Latencia de Inferencia
* **Latencia de Texto Nativo:** El tiempo transcurrido desde el evento `TYPE_WINDOW_CONTENT_CHANGED` hasta el dibujado del Pinyin en pantalla no debe superar los **$50\text{ ms}$** en el percentil 95 ($P_{95}$).
* **Latencia de OCR en Imágenes:** El ciclo completo (captura de *frame* único + recorte ROI + inferencia OCR + segmentación + renderizado) no debe superar los **$250\text{ ms}$** en el percentil 95 ($P_{95}$) para regiones de hasta $1080 \times 1080\text{ px}$.
* **Consulta a Diccionario Local:** La búsqueda indexada en SQLite para la tarjeta de definición tras los 5 segundos de pulsación debe ejecutarse en **$< 15\text{ ms}$**.

### RNF-02: Consumo de Recursos y Eficiencia Energética
* **Impacto en Batería:** El consumo energético en segundo plano (uso mixto activo de lectura durante 1 hora) no debe superar el **$6\%$ de descarga adicional** respecto al consumo base del dispositivo sin el servicio activo.
* **Uso de Memoria RAM:** La huella de memoria residente (PSS - *Proportional Set Size*), incluyendo el modelo OCR cargado en memoria y las estructuras Trie del diccionario, debe mantenerse por debajo de **$180\text{ MB}$** en estado activo y **$< 60\text{ MB}$** en reposo.
* **Uso de CPU en Reposo:** Cuando la pantalla está estática o apagada, el uso de CPU del proceso debe ser **$0.0\%$** (arquitectura estrictamente orientada a eventos, sin *polling* activo).

### RNF-03: Operación 100% Offline y Privacidad de Datos
* **Autonomía de Red:** Todas las funciones (detección de nodos, OCR de imágenes, segmentación léxica y diccionario bilingüe Inglés/Español) deben operar **sin conexión a internet** (`android.permission.INTERNET` no requerido en el núcleo de procesamiento).
* **Privacidad de Pantalla:** Los fotogramas capturados mediante `MediaProjection` deben procesarse exclusivamente en memoria volátil (`Bitmap` en RAM) y liberarse (`bitmap.recycle()`) inmediatamente tras la inferencia OCR, prohibiendo su almacenamiento en disco o transmisión externa.
* **Exclusión de Campos Sensibles:** El servicio debe ignorar automáticamente cualquier nodo con el atributo `isPassword == true` o ventanas marcadas con `FLAG_SECURE` (banca móvil, gestores de contraseñas).

### RNF-04: Usabilidad y Ergonomía Visual
* **Legibilidad y Contraste:** El texto Pinyin superpuesto debe contar con un fondo semitransparente o contorno (*stroke*) de alto contraste que garantice una relación mínima de **$4.5:1$** (estándar WCAG AA) independientemente del color de fondo de la aplicación subyacente.
* **Escalado Adaptativo:** El tamaño de fuente del Pinyin debe escalar proporcionalmente entre el **$45\%$ y el $60\%$** de la altura del carácter Hanzi detectado, con un límite mínimo de $9\text{ sp}$ para evitar fatiga visual.

### RNF-05: Mantenibilidad y Compatibilidad
* **Compatibilidad de OS:** Soporte nativo para Android 10 (API 29) hasta Android 16 (API 36), gestionando las restricciones de permisos de accesibilidad restringida introducidas desde Android 13+.
* **Actualización Modular de Diccionarios:** La base de datos léxica (SQLite) debe estar desacoplada del binario ejecutables para permitir actualizaciones independientes de vocabulario (CC-CEDICT / glosarios técnicos) sin recompilar la aplicación.