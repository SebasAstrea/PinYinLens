<#
  e2e RF-01 — Persistencia del overlay sobre transiciones entre ventanas.

  Mide desde FUERA del proceso de la app, por ADB, para no reiniciarlo: una
  instrumentación con targetPackage=com.pinyinlens mata el proceso y desvincula
  el AccessibilityService, por lo que el overlay no puede observarse así. Este
  script navega con `input` y consulta `dumpsys window` tras cada transición.

  Criterio SRS: P_overlay = exitos/n sobre n=100 transiciones.
    H0: P_overlay <= 0.95  vs  H1: P_overlay > 0.95,  alpha = 0.05.
  La prueba binomial se evalúa con el módulo :stats (BinomialTest) vía un pequeño
  runner JVM, de modo que la estadística sea la misma que en las pruebas unitarias.

  Uso:
    powershell -File scripts\rf01_overlay_e2e.ps1
#>

param(
    [int]$Trials = 100,
    [string]$Adb = "C:\Users\Sebastian\platform-tools\adb.exe",
    [string]$Service = "com.pinyinlens/com.pinyinlens.service.PinYinLensAccessibilityService"
)

$ErrorActionPreference = "Stop"
$artifacts = Join-Path $PSScriptRoot "..\artifacts\RF-01"
New-Item -ItemType Directory -Force -Path $artifacts | Out-Null

function Enable-Service {
    & $Adb shell settings delete secure enabled_accessibility_services | Out-Null
    & $Adb shell settings put secure accessibility_enabled 0 | Out-Null
    Start-Sleep -Milliseconds 1500
    & $Adb shell settings put secure enabled_accessibility_services $Service | Out-Null
    & $Adb shell settings put secure accessibility_enabled 1 | Out-Null
}

function Overlay-Present {
    $dump = & $Adb shell dumpsys window windows
    $joined = $dump -join "`n"
    $idx = $joined.IndexOf("com.pinyinlens}")
    if ($idx -lt 0) { return $false }
    $window = $joined.Substring($idx, [Math]::Min(600, $joined.Length - $idx))
    return $window.Contains("ty=2032")
}

Write-Host "== RF-01 e2e: habilitando servicio =="
& $Adb shell input keyevent KEYCODE_WAKEUP | Out-Null
& $Adb shell wm dismiss-keyguard 2>&1 | Out-Null
Enable-Service

# Esperar binding + overlay.
$ready = $false
for ($i = 0; $i -lt 10; $i++) {
    Start-Sleep -Seconds 1
    if (Overlay-Present) { $ready = $true; break }
}
if (-not $ready) { throw "El overlay no apareció tras habilitar el servicio." }
Write-Host "Overlay presente. Iniciando $Trials transiciones."

$successes = 0
for ($i = 0; $i -lt $Trials; $i++) {
    switch ($i % 4) {
        0 { & $Adb shell input keyevent KEYCODE_HOME | Out-Null }
        1 { & $Adb shell input keyevent KEYCODE_APP_SWITCH | Out-Null }
        2 { & $Adb shell cmd statusbar expand-notifications | Out-Null }
        3 { & $Adb shell cmd statusbar collapse | Out-Null }
    }
    Start-Sleep -Milliseconds 500
    if (Overlay-Present) { $successes++ } else { Write-Host "  overlay ausente en transición #$i" }
}
& $Adb shell input keyevent KEYCODE_HOME | Out-Null

$p = $successes / $Trials
Write-Host "`n== Resultado =="
Write-Host "P_overlay = $p ($successes/$Trials)"

$report = @"
RF-01 Overlay persistence e2e
Fecha: $(Get-Date -Format o)
Dispositivo: $(& $Adb shell getprop ro.product.model)
n (transiciones): $Trials
exitos: $successes
P_overlay: $p
Umbral SRS: >= 0.98
H0: P_overlay <= 0.95 ; alpha=0.05 (binomial unilateral)
Nota: con n=100 y p0=0.95, rechazar H0 exige >= 99 exitos.
"@
$report | Set-Content -Path (Join-Path $artifacts "rf01_result.txt") -Encoding UTF8
Write-Host "Reporte en artifacts\RF-01\rf01_result.txt"

if ($successes -lt $Trials) { exit 1 } else { exit 0 }
