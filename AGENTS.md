# AGENTS.md — Metodología de trabajo de PinYinLens

Este documento define cómo se construye PinYinLens: el flujo de trabajo, las reglas de calidad y el entorno exacto. Cualquier agente (humano o IA) que trabaje en el repositorio debe seguirlo.

## 1. Principios

- **Un requisito a la vez.** El trabajo se organiza en un backlog derivado del SRS. No se empieza un requisito nuevo hasta cerrar el anterior.
- **Nada se da por verificado sin prueba.** Cada módulo tiene pruebas unitarias; cada requisito cerrado tiene una prueba end-to-end en dispositivo real por ADB.
- **Offline y privado por defecto.** No se añade la dependencia de red ni se escribe pantalla a disco. Es una regla de arquitectura, no una preferencia.
- **Orientado a eventos.** Sin `polling`, sin hilos que giren en vacío. 0.0% CPU en reposo es un criterio de aceptación (RNF-02), no un ideal.

## 2. Entorno de construcción

Todas las herramientas están bajo `C:\Users\Sebastian`. El shell del proyecto es **PowerShell** en Windows.

| Herramienta | Ruta | Versión |
|-------------|------|---------|
| JDK (build) | `C:\Users\Sebastian\jdk-21` | 21 |
| Gradle | `C:\Users\Sebastian\gradle-9.8.2` (o `gradle-8.13`) | 9.8.2 / 8.13 |
| Android SDK | `C:\Users\Sebastian\android-sdk` | platform `android-36`, build-tools 35.0.0 / 36.0.0 |
| NDK | `C:\Users\Sebastian\android-ndk-r30` | r30 |
| ADB | `C:\Users\Sebastian\platform-tools\adb.exe` | 1.0.41 |

### Configuración inicial

1. **`JAVA_HOME` para el build.** Aunque el Gradle del sistema arranca con JDK 27, el Android Gradle Plugin se compila con JDK 21. Fijar antes de compilar:

   ```powershell
   $env:JAVA_HOME = "C:\Users\Sebastian\jdk-21"
   ```

2. **`local.properties`** (no se versiona) debe apuntar al SDK:

   ```properties
   sdk.dir=C:\\Users\\Sebastian\\android-sdk
   ```

3. **Gradle Wrapper.** Se usa el wrapper del proyecto (`gradlew.bat`) para fijar la versión por repositorio. Generarlo una sola vez con el Gradle del sistema:

   ```powershell
   C:\Users\Sebastian\gradle-9.8.2\bin\gradle.bat wrapper --gradle-version 8.13
   ```

   Nota: fijamos el wrapper a una versión compatible con el AGP elegido; no asumir que el último Gradle del sistema sirve para Android.

4. **NDK.** Instalado en `C:\Users\Sebastian\android-ndk-r30` (fuera de `android-sdk/ndk`). Solo hace falta si en RF-03 se elige RapidOCR INT8 (binario nativo); con ML Kit Chinese v2 no se usa. Si se necesita, apuntar `ndk.dir` en `local.properties` o `android.ndkVersion`/`ndkPath` en el módulo:

   ```properties
   ndk.dir=C:\\Users\\Sebastian\\android-ndk-r30
   ```

### Comandos de referencia

```powershell
.\gradlew.bat assembleDebug          # compila APK debug
.\gradlew.bat testDebugUnitTest      # pruebas unitarias JVM (por módulo)
.\gradlew.bat connectedDebugAndroidTest   # pruebas instrumentadas en dispositivo
.\gradlew.bat installDebug           # instala en el dispositivo ADB conectado
```

## 3. Flujo de trabajo (backlog)

El backlog vive en `BACKLOG.md` (se crea al arrancar el desarrollo). Cada entrada es una historia trazable a un requisito del SRS.

### Estados

`TODO → EN PROGRESO → EN PRUEBA → HECHO`

Una historia solo pasa a **HECHO** cuando cumple la Definición de Hecho (§5).

### Orden sugerido del backlog

El orden respeta las dependencias técnicas entre módulos:

1. **Andamiaje de build** — proyecto Gradle multi-módulo, wrapper, CI local, APK vacío que instala por ADB.
2. **RF-01 overlay** — capa `TYPE_ACCESSIBILITY_OVERLAY` persistente con pass-through. Base de todo lo visual.
3. **RF-02 nodescan** — extracción de Hanzi y coordenadas del árbol de accesibilidad.
4. **RF-04 pinyin** — segmentación léxica + conversión tonal (puro JVM, muy testeable; se desarrolla en paralelo a RF-02).
5. **RF-06 scroll** — ocultar/regenerar overlay durante desplazamiento.
6. **RF-05 diccionario + hold 5 s** — SQLite CC-CEDICT, gesto sostenido, tarjeta bilingüe.
7. **RF-03 OCR** — MediaProjection + OCR local sobre imágenes (el más pesado; se deja al final).
8. **UI de control** — notificación persistente o bola flotante.
9. **Endurecimiento RNF** — latencia, RAM, batería, privacidad (`FLAG_SECURE`/`isPassword`), contraste WCAG.

Cada ítem se subdivide en tareas pequeñas antes de empezar. No trabajar tareas de dos requisitos a la vez.

## 4. Pruebas

Tres niveles, de barato a caro. Un nivel no reemplaza al siguiente.

### 4.1 Unitarias por módulo (JVM)

- Toda lógica pura (segmentación pinyin, filtrado CJK, desambiguación tonal, parsing CC-CEDICT, cálculo de escalado y contraste) se prueba con JUnit en `src/test`, sin dispositivo.
- Objetivo de cobertura de la lógica de dominio: **≥ 80% de líneas** en los módulos `pinyin`, `dictionary`, `nodescan` (parte pura).
- Se ejecutan en cada commit:

  ```powershell
  .\gradlew.bat testDebugUnitTest
  ```

- Los umbrales del SRS que son medibles sin hardware (p. ej. exactitud de pinyin RF-04 contra el corpus anotado) se validan aquí como pruebas de dataset, incluyendo la prueba estadística correspondiente (ver §6).

### 4.2 Instrumentadas (Android, en dispositivo/emulador)

- Comportamiento que requiere framework Android (overlay, gestos, accesibilidad, SQLite real) se prueba con Espresso/UIAutomator en `src/androidTest`.

  ```powershell
  .\gradlew.bat connectedDebugAndroidTest
  ```

### 4.3 End-to-end por ADB en dispositivo físico

Al **cerrar cada requisito funcional** se ejecuta una prueba e2e en un dispositivo Android físico conectado por ADB, que reproduce el criterio de aceptación del SRS sobre la muestra indicada (`n ≥ 100`, etc.).

Guion base de una corrida e2e:

```powershell
# 0. Verificar dispositivo
C:\Users\Sebastian\platform-tools\adb.exe devices

# 1. Instalar build limpio
.\gradlew.bat installDebug

# 2. Conceder permisos y habilitar el servicio de accesibilidad
adb shell settings put secure enabled_accessibility_services <pkg>/<Service>
adb shell appops set <pkg> SYSTEM_ALERT_WINDOW allow

# 3. Ejecutar el escenario del requisito y recolectar métricas
adb shell am instrument -w -e scenario RF-0X <pkg>.test/androidx.test.runner.AndroidJUnitRunner

# 4. Recoger logs/latencias/trazas para el análisis estadístico
adb logcat -d -s PinYinLens > artifacts\RF-0X_e2e.log
adb pull /sdcard/pinyinlens/metrics RF-0X_metrics
```

Los artefactos de cada corrida e2e se guardan en `artifacts/<RF>/` como evidencia de aceptación.

> **Restricción con el AccessibilityService (aprendido en RF-01):** un
> `am instrument` cuyo `targetPackage` es `com.pinyinlens` **reinicia el proceso
> de la app y desvincula el servicio de accesibilidad**, así que el overlay y
> cualquier estado del servicio desaparecen justo antes del test. Verificado:
> el PID del proceso muere al arrancar la instrumentación. Por eso los e2e que
> dependen del servicio (RF-01, RF-02, RF-05, RF-06) se ejecutan con un **script
> ADB externo** (`scripts/rfXX_*.ps1`) que habilita el servicio, navega con
> `input`/`cmd` y observa el sistema con `dumpsys`, sin reiniciar el proceso. El
> cálculo estadístico reutiliza el módulo `:stats` (mismas pruebas que en unit).
> Habilitar el servicio por ADB requiere un **toggle real** (borrar el setting y
> re-escribirlo); reescribir el mismo valor es un no-op para el framework.

## 5. Definición de Hecho (DoD)

Una historia de requisito está **HECHA** cuando:

1. El código compila con `assembleDebug` usando JDK 21 sin warnings nuevos de deprecación relevantes.
2. Las pruebas unitarias del módulo pasan y mantienen la cobertura objetivo.
3. Las pruebas instrumentadas relevantes pasan.
4. La prueba e2e por ADB en dispositivo físico reproduce el criterio de aceptación del SRS y **la prueba estadística rechaza H₀ con p ≤ 0.05**.
5. Los RNF afectados se miden y cumplen (latencia, RAM, CPU en reposo, contraste).
6. La evidencia (logs, métricas, resultado del test estadístico) queda en `artifacts/`.
7. El backlog se actualiza y los cambios se registran en el control de versiones con un mensaje que cita el requisito (p. ej. `RF-02: extracción de nodos CJK`).

## 6. Validación estadística

El SRS exige pruebas de hipótesis (α = 0.05) para los umbrales de calidad. La aceptación de un requisito **no** es "parece que funciona", es rechazar H₀:

| Requisito | Prueba | H₀ a rechazar |
|-----------|--------|---------------|
| RF-01 overlay | binomial unilateral | P_overlay ≤ 0.95 |
| RF-02 nodos | t de una muestra | μ_error ≥ 4 dp |
| RF-03 OCR | t unilateral | μ_CRR ≤ 0.90 |
| RF-04 pinyin | proporciones Z | P_pinyin ≤ 0.91 |
| RF-05 hold | binomial unilateral | P_hold ≤ 0.93 |
| RF-06 scroll | t unilateral | μ_hide ≥ 32 ms |

Las muestras (`n`) y los conjuntos de datos de referencia (corpus pinyin, 200 imágenes OCR, etc.) se versionan o se documentan en `datasets/` para que las corridas sean reproducibles.

## 7. Reglas de código

- Seguir las convenciones idiomáticas de Android/Kotlin y la estructura multi-módulo del proyecto.
- No introducir `android.permission.INTERNET` en el núcleo de procesamiento.
- Los `Bitmap` de `MediaProjection` se liberan con `recycle()` en cuanto termina la inferencia; nunca se escriben a disco.
- Antes de tocar `build.gradle`, el manifiesto o la configuración de accesibilidad, leer el archivo existente.
- Cambios reversibles y de alcance acotado; nada de refactors no pedidos (YAGNI).
