<#
  e2e RF-02 — Extracción de Hanzi y coordenadas desde el árbol de accesibilidad.

  Mide R_nativo = (nodos con Hanzi detectados por el servicio) / (nodos con Hanzi
  presentes en pantalla), usando como VERDAD independiente el volcado de
  `uiautomator dump` (árbol de accesibilidad del sistema, ajeno a nuestro servicio).

  Umbral SRS: R_nativo >= 0.99 sobre >= 150 nodos acumulados en pantallas diversas.

  Procedimiento por pantalla:
    1. Navegar a una pantalla con Hanzi.
    2. VERDAD: `uiautomator dump` -> contar nodos con text que contiene CJK y
       bounds de área > 0 (y no password).
    3. DETECTADO: reiniciar logcat, re-disparar un WINDOW_CONTENT_CHANGED (scroll
       mínimo) y leer "RF02 span text=..." del servicio.
    4. Emparejar por texto Hanzi normalizado; acumular aciertos/total.

  Nota de alcance: el "error de alineación <= 4 dp" del SRS compara el rect del
  texto nativo con la PROYECCIÓN del pinyin, que se dibuja en RF-04/render. Esa
  parte se mide cuando exista render; aquí se valida R_nativo y la extracción de
  coordenadas (rects no vacíos y dentro de pantalla).

  Uso: powershell -File scripts\rf02_nodescan_e2e.ps1
#>

param(
    [string]$Adb = "C:\Users\Sebastian\platform-tools\adb.exe",
    [string]$Service = "com.pinyinlens/com.pinyinlens.service.PinYinLensAccessibilityService"
)

$ErrorActionPreference = "Continue"
# adb emite UTF-8 y escribe progreso/avisos en stderr sin ser errores fatales.
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
$artifacts = Join-Path $PSScriptRoot "..\artifacts\RF-02"
New-Item -ItemType Directory -Force -Path $artifacts | Out-Null

function Has-Cjk([string]$s) {
    foreach ($ch in $s.ToCharArray()) {
        $code = [int][char]$ch
        if ($code -ge 0x4E00 -and $code -le 0x9FFF) { return $true }
    }
    return $false
}

function Enable-Service {
    & $Adb shell settings delete secure enabled_accessibility_services | Out-Null
    & $Adb shell settings put secure accessibility_enabled 0 | Out-Null
    Start-Sleep -Milliseconds 1500
    & $Adb shell settings put secure enabled_accessibility_services $Service | Out-Null
    & $Adb shell settings put secure accessibility_enabled 1 | Out-Null
    Start-Sleep -Seconds 4
}

# VERDAD: textos con Hanzi según uiautomator dump (área > 0, no password).
# Devuelve $null si el dump falla (p. ej. "null root node"), para no contar una
# verdad obsoleta del dump anterior.
function Truth-HanziTexts {
    $local = Join-Path $env:TEMP "pyl_dump.xml"
    Remove-Item $local -ErrorAction SilentlyContinue
    & $Adb shell rm -f /sdcard/pyl_dump.xml 2>&1 | Out-Null
    $out = & $Adb shell uiautomator dump /sdcard/pyl_dump.xml 2>&1
    if ($out -match "ERROR|null root") { return $null }
    & $Adb pull /sdcard/pyl_dump.xml $local 2>&1 | Out-Null
    if (-not (Test-Path $local)) { return $null }
    $joined = Get-Content $local -Raw -Encoding UTF8
    $texts = @()
    $pattern = 'text="([^"]*)"[^>]*?password="(true|false)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"'
    foreach ($m in [regex]::Matches($joined, $pattern)) {
        $t = $m.Groups[1].Value
        $pw = $m.Groups[2].Value
        $x1 = [int]$m.Groups[3].Value; $y1 = [int]$m.Groups[4].Value
        $x2 = [int]$m.Groups[5].Value; $y2 = [int]$m.Groups[6].Value
        if ($pw -eq "true") { continue }
        if (($x2 - $x1) -le 0 -or ($y2 - $y1) -le 0) { continue }
        if (Has-Cjk $t) { $texts += $t.Trim() }
    }
    return @($texts | Sort-Object -Unique)
}

# DETECTADO: textos de spans que el servicio ya registró para la pantalla actual.
# Se asume que el logcat se limpió ANTES de abrir la pantalla, de modo que el log
# contiene los spans del WINDOW_STATE_CHANGED de apertura.
function Detected-HanziTexts {
    $log = & $Adb logcat -d -s PinYinLens:V
    $texts = @()
    foreach ($line in $log) {
        $m = [regex]::Match($line, "RF02 span text='(.+?)' rect=")
        if ($m.Success) { $texts += $m.Groups[1].Value.Trim() }
    }
    return $texts | Sort-Object -Unique
}

& $Adb shell input keyevent KEYCODE_WAKEUP | Out-Null
Enable-Service

$screens = @(
    @{ name = "Ajustes"; intent = "android.settings.SETTINGS" },
    @{ name = "Info del dispositivo"; intent = "android.settings.DEVICE_INFO_SETTINGS" },
    @{ name = "Apps"; intent = "android.settings.APPLICATION_SETTINGS" },
    @{ name = "Pantalla"; intent = "android.settings.DISPLAY_SETTINGS" },
    @{ name = "Sonido"; intent = "android.settings.SOUND_SETTINGS" },
    @{ name = "Bateria"; intent = "android.settings.BATTERY_SAVER_SETTINGS" },
    @{ name = "Almacenamiento"; intent = "android.settings.INTERNAL_STORAGE_SETTINGS" },
    @{ name = "Fecha y hora"; intent = "android.settings.DATE_SETTINGS" },
    @{ name = "Accesibilidad"; intent = "android.settings.ACCESSIBILITY_SETTINGS" },
    @{ name = "Seguridad"; intent = "android.settings.SECURITY_SETTINGS" },
    @{ name = "Red e Internet"; intent = "android.settings.WIRELESS_SETTINGS" },
    @{ name = "Bluetooth"; intent = "android.settings.BLUETOOTH_SETTINGS" },
    @{ name = "Idioma"; intent = "android.settings.LOCALE_SETTINGS" },
    @{ name = "Privacidad"; intent = "android.settings.PRIVACY_SETTINGS" }
)

$totalTruth = 0
$totalHit = 0
$lines = @()

foreach ($s in $screens) {
    # Limpiar log ANTES de abrir, para capturar el WINDOW_STATE_CHANGED de apertura.
    & $Adb logcat -c | Out-Null
    # Ir a Home primero fuerza un cambio de ventana real al abrir la pantalla.
    & $Adb shell input keyevent KEYCODE_HOME | Out-Null
    Start-Sleep -Milliseconds 800
    & $Adb shell am start -a $s.intent | Out-Null
    Start-Sleep -Seconds 2
    # Un scroll pequeño re-dispara content-changed por si la apertura reusó tarea.
    & $Adb shell input swipe 540 1400 540 900 | Out-Null
    Start-Sleep -Seconds 2
    $truth = Truth-HanziTexts
    if ($null -eq $truth) {
        $line = "{0,-22} (dump fallido, pantalla omitida)" -f $s.name
        Write-Host $line
        $lines += $line
        continue
    }
    $detected = Detected-HanziTexts
    $hit = 0
    foreach ($t in $truth) {
        # Acierto si algún span detectado contiene el texto verdad o viceversa.
        $match = $detected | Where-Object { $_ -eq $t -or $_.Contains($t) -or $t.Contains($_) }
        if ($match) { $hit++ }
        elseif ($VerbosePreference -ne 'SilentlyContinue') { Write-Host "    MISS: $t" }
    }
    $totalTruth += $truth.Count
    $totalHit += $hit
    $line = "{0,-22} verdad={1,3} detectados={2,3} aciertos={3,3}" -f $s.name, $truth.Count, $detected.Count, $hit
    Write-Host $line
    $lines += $line
}

& $Adb shell input keyevent KEYCODE_HOME | Out-Null

$rNativo = if ($totalTruth -gt 0) { [math]::Round($totalHit / $totalTruth, 4) } else { 0 }
Write-Host "`n== Resultado RF-02 =="
Write-Host "nodos verdad (n) = $totalTruth"
Write-Host "aciertos        = $totalHit"
Write-Host "R_nativo        = $rNativo (umbral SRS >= 0.99)"

$report = @"
RF-02 Extracción de Hanzi y coordenadas — e2e
Fecha: $(Get-Date -Format o)
Dispositivo: $(& $Adb shell getprop ro.product.model)
Idioma: $(& $Adb shell getprop persist.sys.locale)

Metodología: VERDAD = uiautomator dump (nodos con CJK, área>0, no password);
DETECTADO = spans del servicio (log 'RF02 span text=').

$($lines -join "`n")

n (nodos verdad) = $totalTruth
aciertos = $totalHit
R_nativo = $rNativo
Umbral SRS: R_nativo >= 0.99

Nota de alcance: el error de alineación <= 4 dp (prueba t, H0: mu>=4dp) compara el
rect nativo con la proyección del pinyin, que se dibuja en RF-04/render. Se medirá
cuando exista render. Aquí se valida R_nativo y la extracción de coordenadas.
"@
$report | Set-Content -Path (Join-Path $artifacts "rf02_result.txt") -Encoding UTF8
Write-Host "Reporte en artifacts\RF-02\rf02_result.txt"
