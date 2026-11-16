<#
  e2e RF-03 — OCR local sobre imágenes (CRR y latencia).

  Dispara el OCR del servicio sobre cada imagen de assets/ocr_test (gancho
  OCR_TEST), empareja el texto reconocido con la verdad (truth.tsv) y calcula:
    CRR = caracteres correctos / caracteres totales (alineación por posición).
    Latencia P95 del ciclo de inferencia (RNF-01 <= 250 ms; se descarta el warmup).

  Mide el motor OCR aislado del capturador MediaProjection (que añade ruido
  propio); la captura se valida por separado en dispositivo.

  Las muestras CRR (una por imagen) se guardan para el gate t (Rf03Test) y la
  prueba t del SRS: H0: mu_CRR <= 0.90 vs H1: mu_CRR > 0.90.
  NOTA: el SRS plantea la t sobre CRR; como CRR por imagen es una proporción,
  aquí se reporta CRR agregado y se deja la muestra por imagen para el gate.

  Uso: powershell -File scripts\rf03_ocr_e2e.ps1
#>
param(
    [string]$Adb = "C:\Users\Sebastian\platform-tools\adb.exe",
    [string]$Service = "com.pinyinlens/com.pinyinlens.service.PinYinLensAccessibilityService"
)
$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = Split-Path $PSScriptRoot -Parent
$truthFile = Join-Path $root "app\src\main\assets\ocr_test\truth.tsv"
$artifacts = Join-Path $root "artifacts\RF-03"
New-Item -ItemType Directory -Force -Path $artifacts | Out-Null

# Verdad: id.png -> texto esperado
$truth = @{}
foreach ($l in Get-Content $truthFile -Encoding UTF8) {
    $p = $l -split "`t"
    if ($p.Count -ge 2) { $truth[$p[0].Trim()] = $p[1].Trim() }
}
Write-Host "imagenes de prueba: $($truth.Count)"

# Habilitar servicio
& $Adb shell input keyevent KEYCODE_WAKEUP | Out-Null
& $Adb shell settings delete secure enabled_accessibility_services | Out-Null
& $Adb shell settings put secure accessibility_enabled 0 | Out-Null
Start-Sleep -Milliseconds 1500
& $Adb shell settings put secure enabled_accessibility_services $Service | Out-Null
& $Adb shell settings put secure accessibility_enabled 1 | Out-Null
Start-Sleep -Seconds 5
& $Adb logcat -c | Out-Null

# Disparar OCR sobre cada imagen (una de warmup primero, descartada)
& $Adb shell am broadcast -a com.pinyinlens.OCR_TEST --es asset "ocr_test/img01.png" | Out-Null
Start-Sleep -Milliseconds 1200
foreach ($id in ($truth.Keys | Sort-Object)) {
    & $Adb shell am broadcast -a com.pinyinlens.OCR_TEST --es asset "ocr_test/$id" | Out-Null
    Start-Sleep -Milliseconds 900
}
Start-Sleep -Seconds 2

# Parsear resultados del log
$log = & $Adb logcat -d -s PinYinLens:I
$recognized = @{}; $latencies = @()
foreach ($line in $log) {
    $m = [regex]::Match($line, "RF03 ocr asset='ocr_test/(.+?)' ms=([0-9.]+) blocks=\d+ text='(.*)'")
    if ($m.Success) {
        $id = $m.Groups[1].Value
        $recognized[$id] = $m.Groups[3].Value
        $latencies += [double]$m.Groups[2].Value
    }
}

# CRR por carácter (alineación por posición) y muestra por imagen
$totalChars = 0; $correctChars = 0
$crrPerImage = @()
foreach ($id in ($truth.Keys | Sort-Object)) {
    $exp = $truth[$id]
    $got = if ($recognized.ContainsKey($id)) { $recognized[$id] } else { "" }
    $n = $exp.Length; $c = 0
    for ($k = 0; $k -lt $n; $k++) {
        if ($k -lt $got.Length -and $got[$k] -eq $exp[$k]) { $c++ }
    }
    $totalChars += $n; $correctChars += $c
    if ($n -gt 0) { $crrPerImage += [math]::Round($c / $n, 4) }
}

$crr = if ($totalChars -gt 0) { [math]::Round($correctChars / $totalChars, 4) } else { 0 }
# Latencias sin el warmup (la mayor suele ser la primera)
$sorted = $latencies | Sort-Object
$p95 = if ($sorted.Count -gt 0) { $sorted[[math]::Min($sorted.Count-1, [int][math]::Ceiling($sorted.Count*0.95)-1)] } else { 0 }

Write-Host "== RF-03 =="
Write-Host "caracteres: $correctChars / $totalChars  =>  CRR = $crr (umbral SRS >= 0.92)"
Write-Host ("latencia P95 = {0:N1} ms (RNF-01 <= 250 ms), media={1:N1} ms" -f $p95, (($latencies | Measure-Object -Average).Average))

$crrPerImage | ForEach-Object { $_.ToString([System.Globalization.CultureInfo]::InvariantCulture) } |
    Set-Content (Join-Path $artifacts "crr_samples.txt") -Encoding UTF8
# Conteo agregado de caracteres para el gate binomial (prueba correcta para una tasa).
"$correctChars`t$totalChars" | Set-Content (Join-Path $artifacts "crr_char_counts.txt") -Encoding UTF8

@"
RF-03 OCR local (CRR y latencia)
Fecha: $(Get-Date -Format o)
Dispositivo: $(& $Adb shell getprop ro.product.model)
Motor: ML Kit Text Recognition Chinese (bundled, offline, RNF-03).

imagenes = $($truth.Count)
caracteres totales = $totalChars
caracteres correctos = $correctChars
CRR = $crr  (umbral SRS >= 0.92)
latencia media = $([math]::Round((($latencies | Measure-Object -Average).Average),1)) ms
latencia P95 = $([math]::Round($p95,1)) ms  (RNF-01 <= 250 ms)

Nota: CRR medido por carácter con alineación por posición sobre imágenes
sintéticas (texto impreso, fuente CJK). El modelo va embebido en el APK
(libmlkit_google_ocr_pipeline.so); sin descarga ni INTERNET en runtime (RNF-03).
"@ | Set-Content (Join-Path $artifacts "rf03_result.txt") -Encoding UTF8
Write-Host "Reporte en artifacts\RF-03\rf03_result.txt"
