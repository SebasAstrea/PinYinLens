<#
  build_char_pinyin.ps1 — Tabla de pinyin por CARÁCTER para PinYinLens (RF-04).

  El léxico de palabras (hsk_lexicon.cedict) no cubre todos los caracteres que
  aparecen en la UI del sistema (p. ej. 星 de 三星, 帐/户, 屏/幕, 纹). Sin este
  respaldo, PinyinConverter descarta esos caracteres y la etiqueta sale corta o
  con una lectura equivocada.

  Fuente: datasets/complete.min.json (drkameleon/complete-hsk-vocabulary), que
  trae pinyin numérico (i.n) por entrada. Para cada entrada cuya cuenta de
  sílabas coincide con la de caracteres, cada carácter "vota" por su sílaba.
  Las entradas de UN carácter pesan mucho más (son la lectura canónica).

  Salida: datasets/char_pinyin.tsv y copia en app/src/main/assets/char_pinyin.tsv
  (formato: CARÁCTER<TAB>pinyin_numérico, una línea por carácter).

  Uso:
    powershell -File scripts\build_char_pinyin.ps1
#>

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = Split-Path $PSScriptRoot -Parent
$datasets = Join-Path $root "datasets"
$jsonPath = Join-Path $datasets "complete.min.json"
$outPath = Join-Path $datasets "char_pinyin.tsv"
$assetDir = Join-Path $root "app\src\main\assets"
$assetPath = Join-Path $assetDir "char_pinyin.tsv"

if (-not (Test-Path $jsonPath)) {
    Write-Host "Descargando complete.min.json..."
    $url = "https://raw.githubusercontent.com/drkameleon/complete-hsk-vocabulary/main/complete.min.json"
    Invoke-WebRequest -Uri $url -OutFile $jsonPath -UseBasicParsing -TimeoutSec 120
}

$entries = Get-Content $jsonPath -Raw -Encoding UTF8 | ConvertFrom-Json
Write-Host "Entradas: $($entries.Count)"

# Solo Hanzi del rango que filtra el escáner (U+4E00..U+9FFF).
function Test-Hanzi([char]$c) {
    $code = [int][char]$c
    return ($code -ge 0x4E00 -and $code -le 0x9FFF)
}

# votes[char] = @{ syllable = peso }
$votes = @{}
$used = 0
foreach ($e in $entries) {
    $simp = $e.s
    if (-not $simp) { continue }
    $pinyin = $e.f[0].i.n
    if (-not $pinyin) { continue }
    $syllables = @($pinyin.Trim().ToLower() -split '\s+' | Where-Object { $_ -ne '' })
    if ($syllables.Count -ne $simp.Length) { continue }   # erhua/contracciones: se omite
    $weight = if ($simp.Length -eq 1) { 1000 } else { 1 }
    $ok = $true
    for ($i = 0; $i -lt $simp.Length; $i++) {
        if (-not (Test-Hanzi $simp[$i])) { $ok = $false; break }
    }
    if (-not $ok) { continue }
    $used++
    for ($i = 0; $i -lt $simp.Length; $i++) {
        $ch = [string]$simp[$i]
        $syl = $syllables[$i]
        if (-not $votes.ContainsKey($ch)) { $votes[$ch] = @{} }
        $table = $votes[$ch]
        $table[$syl] = ($table[$syl] + $weight)
    }
}

$rows = New-Object System.Collections.Generic.List[string]
foreach ($ch in ($votes.Keys | Sort-Object)) {
    $table = $votes[$ch]
    $best = ($table.GetEnumerator() | Sort-Object -Property Value -Descending | Select-Object -First 1)
    $rows.Add("$ch`t$($best.Key)")
}

# Sin BOM: la app lee el TSV con UTF-8 y un BOM contaminaría el primer carácter.
[System.IO.File]::WriteAllLines($outPath, $rows, (New-Object System.Text.UTF8Encoding($false)))
New-Item -ItemType Directory -Force -Path $assetDir | Out-Null
Copy-Item -Path $outPath -Destination $assetPath -Force

Write-Host "Entradas usadas: $used"
Write-Host "Caracteres con pinyin: $($rows.Count) -> $outPath"
Write-Host "Copia en assets: $assetPath"
