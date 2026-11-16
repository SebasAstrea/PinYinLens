<#
  build_dictionary.ps1 — Construye el diccionario bilingüe de PinYinLens (RF-05).

  Fuentes (verificadas):
   - HSK 1-6 (2.0) + pinyin + inglés: drkameleon/complete-hsk-vocabulary
       complete.min.json, filtrando niveles o1..o6 (HSK clásico 1-6).
   - Traducción EN->ES: Apertium LOCAL en WSL (par eng-spa), vía
       scripts/apertium_translate.sh. Sin red, sin rate-limit. El runtime de la
       app sigue siendo 100% offline (RNF-03); aquí solo se construyen los datos.
   - CC-CEDICT completo (opcional, -WithCedict): MDBG, para vocabulario no-HSK.

  Salida: datasets/hsk_dictionary.tsv con columnas TAB-separadas:
     simplified  traditional  pinyin  english(0x1f)  spanish(0x1f)  hsk

  Uso:
    powershell -File scripts\build_dictionary.ps1 -Limit 50   # prueba
    powershell -File scripts\build_dictionary.ps1             # completo
#>

param(
    [int]$Limit = 0,              # 0 = todas las palabras HSK 1-6
    [switch]$WithCedict,          # también volcar CC-CEDICT (solo inglés)
    [string]$WslScript = "/mnt/c/Users/Sebastian/Downloads/Proyectos/Android/PinYinLens/scripts/apertium_translate.sh"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$root = Split-Path $PSScriptRoot -Parent
$datasets = Join-Path $root "datasets"
New-Item -ItemType Directory -Force -Path $datasets | Out-Null

$hskUrl = "https://raw.githubusercontent.com/drkameleon/complete-hsk-vocabulary/main/complete.min.json"
$hskLocal = Join-Path $datasets "complete.min.json"
$cedictUrl = "https://www.mdbg.net/chinese/export/cedict/cedict_1_0_ts_utf-8_mdbg.txt.gz"
$outPath = Join-Path $datasets "hsk_dictionary.tsv"
$GLOSS_SEP = [char]0x1f

# Rutas temporales para el intercambio con Apertium (en el proyecto, visibles en WSL).
$glossInWin = Join-Path $root "scripts\.gloss_in.txt"
$glossOutWin = Join-Path $root "scripts\.gloss_out.txt"
$glossInWsl = "/mnt/c/Users/Sebastian/Downloads/Proyectos/Android/PinYinLens/scripts/.gloss_in.txt"
$glossOutWsl = "/mnt/c/Users/Sebastian/Downloads/Proyectos/Android/PinYinLens/scripts/.gloss_out.txt"

<#
  Traduce una lista de glosas EN->ES en UNA pasada por Apertium local.
  Apertium conserva la alineación línea-a-línea, así que se trocea la salida por
  líneas. Las glosas se sanean para que no contengan saltos de línea.
#>
function Translate-Batch([string[]]$glosses) {
    if ($glosses.Count -eq 0) { return @{} }
    # Sanear: una glosa por línea, sin saltos internos.
    $clean = $glosses | ForEach-Object { ($_ -replace "[\r\n\t]", " ").Trim() }
    [System.IO.File]::WriteAllLines($glossInWin, $clean, (New-Object System.Text.UTF8Encoding($false)))
    wsl -e bash $WslScript $glossInWsl $glossOutWsl
    $translated = [System.IO.File]::ReadAllLines($glossOutWin, [System.Text.Encoding]::UTF8)
    $map = @{}
    for ($i = 0; $i -lt $clean.Count; $i++) {
        $es = if ($i -lt $translated.Count) { $translated[$i] } else { "" }
        # Limpiar el marcador '*' de palabra desconocida de Apertium.
        $es = ($es -replace "\*", "").Trim()
        $map[$glosses[$i]] = $es
    }
    return $map
}

# --- 1. Descargar HSK ---
if (-not (Test-Path $hskLocal)) {
    Write-Host "Descargando HSK..."
    Invoke-WebRequest -Uri $hskUrl -OutFile $hskLocal -UseBasicParsing -TimeoutSec 120
}
$hsk = Get-Content $hskLocal -Raw -Encoding UTF8 | ConvertFrom-Json

# Filtrar HSK 1-6 (niveles o1..o6) y quedarse con el menor nivel de cada palabra.
$oLevels = @("o1","o2","o3","o4","o5","o6")
$hsk16 = $hsk | Where-Object { $_.l | Where-Object { $_ -in $oLevels } }
Write-Host "HSK 1-6: $($hsk16.Count) palabras"
if ($Limit -gt 0) { $hsk16 = $hsk16 | Select-Object -First $Limit; Write-Host "Modo prueba: $($hsk16.Count) palabras" }

# --- 2. Recolectar glosas EN únicas y traducirlas en UNA pasada (Apertium local) ---
$uniqueGlosses = New-Object System.Collections.Generic.HashSet[string]
foreach ($e in $hsk16) {
    $form = $e.f | Select-Object -First 1
    foreach ($m in $form.m) { if ($m) { [void]$uniqueGlosses.Add($m) } }
}
$glossArray = @($uniqueGlosses)
Write-Host "Glosas EN únicas: $($glossArray.Count). Traduciendo con Apertium local..."
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$cache = Translate-Batch $glossArray
Write-Host "Traducción completada en $([math]::Round($sw.Elapsed.TotalSeconds,1)) s."

# --- 3. Ensamblar el TSV desde HSK + traducciones ---
$rows = New-Object System.Collections.Generic.List[string]
foreach ($e in $hsk16) {
    $simp = $e.s
    $form = $e.f | Select-Object -First 1
    $trad = if ($form.t) { $form.t } else { $simp }
    $pinyin = $form.i.y
    $enList = @($form.m | Where-Object { $_ })
    $hskLevel = ($e.l | Where-Object { $_ -in $oLevels } | Sort-Object | Select-Object -First 1) -replace "o",""

    $esList = @()
    foreach ($en in $enList) { if ($cache.ContainsKey($en) -and $cache[$en]) { $esList += $cache[$en] } }

    $row = @(
        $simp, $trad, $pinyin,
        ($enList -join $GLOSS_SEP),
        ($esList -join $GLOSS_SEP),
        $hskLevel
    ) -join "`t"
    $rows.Add($row)
}

# --- 4. CC-CEDICT opcional (solo inglés, vocabulario no-HSK) ---
if ($WithCedict) {
    Write-Host "Descargando CC-CEDICT..."
    $gz = Join-Path $datasets "cedict.txt.gz"
    Invoke-WebRequest -Uri $cedictUrl -OutFile $gz -UseBasicParsing -TimeoutSec 120
    $seen = @{}; foreach ($e in $hsk16) { $seen[$e.s] = $true }
    $fin = [System.IO.File]::OpenRead($gz)
    $gzs = New-Object System.IO.Compression.GzipStream($fin, [System.IO.Compression.CompressionMode]::Decompress)
    $sr = New-Object System.IO.StreamReader($gzs, [System.Text.Encoding]::UTF8)
    $re = [regex]'^(\S+)\s+(\S+)\s+\[([^\]]*)\]\s+/(.+)/$'
    while (-not $sr.EndOfStream) {
        $l = $sr.ReadLine()
        if ($l.StartsWith("#")) { continue }
        $m = $re.Match($l); if (-not $m.Success) { continue }
        $simp = $m.Groups[2].Value
        if ($seen.ContainsKey($simp)) { continue }
        $trad = $m.Groups[1].Value
        $pinyin = $m.Groups[3].Value
        $en = ($m.Groups[4].Value -split "/") -join $GLOSS_SEP
        $rows.Add((@($simp,$trad,$pinyin,$en,"","") -join "`t"))
    }
    $sr.Close()
    Write-Host "CC-CEDICT añadido (solo inglés)."
}

# --- Escribir salida ---
Set-Content -Path $outPath -Value $rows -Encoding UTF8
Write-Host "`nListo: $($rows.Count) entradas -> $outPath"

# --- 5. Generar léxico y corpus para el gate A_pinyin de RF-04 ---
# El motor de pinyin (Lexicon.fromCcCedict) necesita tono NUMÉRICO (Zhong1 guo2);
# el HSK lo trae en i.n. El corpus usa el pinyin DIACRÍTICO (i.y) como esperado.
$lexPath = Join-Path $datasets "hsk_lexicon.cedict"
$corpusPath = Join-Path $datasets "hsk_pinyin_corpus.tsv"
$lexRows = New-Object System.Collections.Generic.List[string]
$corpusRows = New-Object System.Collections.Generic.List[string]
foreach ($e in $hsk16) {
    $simp = $e.s
    $form = $e.f | Select-Object -First 1
    $trad = if ($form.t) { $form.t } else { $simp }
    $numeric = ($form.i.n -replace "\s+", " ").Trim().ToLower()   # "zhong1 guo2"
    $diacritic = ($form.i.y -replace "\s+", " ").Trim()           # "Zhōngguó" -> normalizar espacios
    if (-not $numeric -or -not $diacritic) { continue }
    # Normalizar 'u:' -> 'ü' en el esperado (el motor produce la forma diacrítica).
    $diacritic = $diacritic -replace "u:", ([char]0x00FC)
    # Línea CC-CEDICT: TRAD SIMP [pin1 yin1] /x/
    $lexRows.Add("$trad $simp [$numeric] /hsk/")
    # Corpus: hanzi TAB pinyin-diacrítico-esperado (en minúsculas, sílabas con espacio).
    $corpusRows.Add("$simp`t$($diacritic.ToLower())")
}
Set-Content -Path $lexPath -Value $lexRows -Encoding UTF8
Set-Content -Path $corpusPath -Value $corpusRows -Encoding UTF8
Write-Host "Léxico RF-04: $($lexRows.Count) entradas -> $lexPath"
Write-Host "Corpus RF-04: $($corpusRows.Count) oraciones -> $corpusPath"
Write-Host "Caché EN->ES: $($cache.Count) traducciones en $cachePath"
