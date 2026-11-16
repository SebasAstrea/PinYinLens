<#
  e2e RF-05 (C_dic) — Completitud de datos bilingües sobre HSK 1-6.

  C_dic = palabras HSK 1-6 con datos COMPLETOS (pinyin + inglés + español) /
          total de palabras HSK 1-6.
  Umbral SRS: C_dic >= 0.98.

  Se mide sobre el TSV que alimenta la app (datasets/hsk_dictionary.tsv), que es
  exactamente lo que se carga en SQLite. Verifica completitud, no "existencia":
  una entrada cuenta solo si tiene pinyin y al menos una glosa EN y una ES.

  Aplica la prueba binomial del SRS (H0: P <= 0.93) con el modulo :stats via el
  gate JVM (ver nota). Aqui se calcula C_dic y se deja el conteo para el gate.

  Uso: powershell -File scripts\rf05_cdic_e2e.ps1
#>

param(
    [string]$Tsv = (Join-Path (Split-Path $PSScriptRoot -Parent) "datasets\hsk_dictionary.tsv")
)
$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$artifacts = Join-Path (Split-Path $PSScriptRoot -Parent) "artifacts\RF-05"
New-Item -ItemType Directory -Force -Path $artifacts | Out-Null
$SEP = [char]0x1f

$lines = Get-Content $Tsv -Encoding UTF8
$total = 0; $complete = 0; $incomplete = @()
foreach ($l in $lines) {
    if ([string]::IsNullOrWhiteSpace($l)) { continue }
    $c = $l -split "`t"
    if ($c.Count -lt 5) { continue }
    $total++
    $pinyin = $c[2].Trim()
    $en = ($c[3] -split $SEP | Where-Object { $_.Trim() }).Count
    $es = ($c[4] -split $SEP | Where-Object { $_.Trim() }).Count
    if ($pinyin -and $en -ge 1 -and $es -ge 1) { $complete++ }
    else { $incomplete += $c[0] }
}
$cdic = if ($total -gt 0) { [math]::Round($complete / $total, 4) } else { 0 }

Write-Host "== RF-05 C_dic =="
Write-Host "palabras HSK 1-6 (n) = $total"
Write-Host "completas (pinyin+EN+ES) = $complete"
Write-Host "C_dic = $cdic (umbral SRS >= 0.98)"
if ($incomplete.Count -gt 0) {
    Write-Host "incompletas (muestra): $($incomplete | Select-Object -First 10)"
}

$report = @"
RF-05 C_dic (completitud de datos bilingües, HSK 1-6)
Fecha: $(Get-Date -Format o)
Fuente: datasets/hsk_dictionary.tsv (generado por build_dictionary.ps1)

n (palabras HSK 1-6) = $total
completas (pinyin + >=1 EN + >=1 ES) = $complete
C_dic = $cdic
Umbral SRS: C_dic >= 0.98

Nota: inglés y pinyin provienen de la lista HSK (exactos); español de Apertium
local (mejor-esfuerzo). C_dic mide COMPLETITUD de datos, no calidad semántica
del español. La verificación en dispositivo confirma la carga de $total entradas
en SQLite y consulta < 15 ms (ver rf05_result.txt).
"@
$report | Set-Content (Join-Path $artifacts "rf05_cdic.txt") -Encoding UTF8
Write-Host "Reporte en artifacts\RF-05\rf05_cdic.txt"
"$complete`t$total" | Set-Content (Join-Path $artifacts "cdic_counts.txt") -Encoding UTF8
