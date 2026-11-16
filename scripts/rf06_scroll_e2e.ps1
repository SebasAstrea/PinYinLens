<#
  e2e RF-06 — Ocultamiento de la capa durante scroll.

  Genera >= 100 eventos de scroll en una pantalla con contenido desplazable y
  recoge del log del servicio los tiempos de ocultamiento (RF06 hide ms=...).
  Escribe las muestras a artifacts/RF-06/hide_samples.txt para que el test JVM
  Rf06TimingTest aplique la prueba t del SRS:
    H0: mu_hide >= 32 ms  vs  H1: mu_hide < 32 ms,  alpha = 0.05.

  Mide por ADB externo (no instrumentación), igual que RF-01/RF-02.
  Uso: powershell -File scripts\rf06_scroll_e2e.ps1
#>

param(
    [int]$Target = 100,
    [string]$Adb = "C:\Users\Sebastian\platform-tools\adb.exe",
    [string]$Service = "com.pinyinlens/com.pinyinlens.service.PinYinLensAccessibilityService"
)

$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$artifacts = Join-Path $PSScriptRoot "..\artifacts\RF-06"
New-Item -ItemType Directory -Force -Path $artifacts | Out-Null

function Enable-Service {
    & $Adb shell settings delete secure enabled_accessibility_services | Out-Null
    & $Adb shell settings put secure accessibility_enabled 0 | Out-Null
    Start-Sleep -Milliseconds 1500
    & $Adb shell settings put secure enabled_accessibility_services $Service | Out-Null
    & $Adb shell settings put secure accessibility_enabled 1 | Out-Null
    Start-Sleep -Seconds 4
}

& $Adb shell input keyevent KEYCODE_WAKEUP | Out-Null
Enable-Service
# Pantalla con lista larga desplazable y Hanzi (Apps instaladas).
& $Adb shell am start -a android.settings.APPLICATION_SETTINGS | Out-Null
Start-Sleep -Seconds 2
& $Adb logcat -c | Out-Null

$samples = New-Object System.Collections.Generic.List[double]
$iter = 0
while ($samples.Count -lt $Target -and $iter -lt ($Target * 3)) {
    $iter++
    # Alternar dirección para generar scroll continuo con paradas intermedias.
    if ($iter % 2 -eq 0) {
        & $Adb shell input swipe 540 1700 540 500 150 | Out-Null
    } else {
        & $Adb shell input swipe 540 500 540 1700 150 | Out-Null
    }
    Start-Sleep -Milliseconds 350  # permite regenerar entre ráfagas -> nuevos hide
    $log = & $Adb logcat -d -s PinYinLens:I
    $samples.Clear()
    foreach ($line in $log) {
        $m = [regex]::Match($line, "RF06 hide ms=([0-9]+\.?[0-9]*)")
        if ($m.Success) { $samples.Add([double]$m.Groups[1].Value) }
    }
}

& $Adb shell input keyevent KEYCODE_HOME | Out-Null

$file = Join-Path $artifacts "hide_samples.txt"
$samples | ForEach-Object { $_.ToString([System.Globalization.CultureInfo]::InvariantCulture) } |
    Set-Content -Path $file -Encoding UTF8

$mean = if ($samples.Count -gt 0) { ($samples | Measure-Object -Average).Average } else { 0 }
Write-Host "== RF-06 e2e =="
Write-Host "muestras t_hide recogidas: $($samples.Count)"
Write-Host ("media t_hide = {0:N4} ms (umbral SRS <= 32 ms)" -f $mean)
Write-Host "muestras en $file"
Write-Host "Ejecuta el gate: gradlew :app:testDebugUnitTest --tests *Rf06TimingTest"
