# datasets/ — Corpus y léxicos de validación

Datos de referencia para las pruebas de aceptación del SRS, versionados aparte
del código (RNF-05: diccionario desacoplado del binario).

## RF-04 — Exactitud de pinyin (A_pinyin ≥ 0.94)

Dos archivos:

### `lexicon_seed.cedict`
Léxico en formato CC-CEDICT (`trad simp [pin1 yin1] /gloss/`). El motor
(`Lexicon.fromCcCedict`) solo usa la columna simplificada y el pinyin numérico.
Este es un **léxico semilla** pequeño, suficiente para las oraciones del corpus
semilla. En producción se sustituye por un CC-CEDICT completo (~120k entradas)
colocándolo aquí con el mismo formato; no hay que recompilar.

### `pinyin_corpus.tsv`
Corpus anotado, una oración por línea, TAB-separado:

```
<hanzi>\t<pinyin diacrítico esperado, sílabas con espacio>
```

El test `PinyinAccuracyDatasetTest` convierte cada oración con el motor y la
compara con la columna esperada; A_pinyin = oraciones correctas / total. Aplica
la **prueba Z de proporciones** (H₀: P ≤ 0.91 vs H₁: P > 0.91, α = 0.05).

### `char_pinyin.tsv` — respaldo por carácter

El léxico HSK solo contiene palabras: un carácter que no forma parte de ninguna
palabra conocida (星 de 三星, 帐/户, 屏/幕, 纹, 若…) se quedaba sin pinyin y la
etiqueta salía corta o con una lectura equivocada.

`scripts/build_char_pinyin.ps1` deriva la lectura de cada carácter desde
`complete.min.json` (votando la sílaba de cada palabra; las entradas de un solo
carácter pesan más) y escribe:

```
<char>TAB<pinyin_numérico>     # p. ej. 星\txing1
```

Se versiona aquí y se copia a `app/src/main/assets/char_pinyin.tsv` (la app lo
carga junto al léxico de palabras). Es un respaldo: si hay palabra en el léxico,
manda la palabra (desambigua polífonos como 行/长/了/地).

## Estado y alcance

El corpus semilla incluido es pequeño (valida el MECANISMO: segmentación y
desambiguación polifónica de 行/长/着/了/地). **La cifra de aceptación formal
A_pinyin ≥ 0.94 sobre n = 500 requiere:**

1. Un CC-CEDICT completo en `lexicon_seed.cedict` (o archivo equivalente).
2. 500 oraciones reales anotadas en `pinyin_corpus.tsv`.

Con ambos presentes, `PinyinAccuracyDatasetTest` aplica el gate automáticamente:
si n ≥ 100 y se rechaza H₀, el requisito queda validado; si no, el test informa
la A_pinyin observada sin bloquear el build (ver anotación en el test).

## Fuente de CC-CEDICT

CC-CEDICT se distribuye bajo licencia CC-BY-SA. Descargar la versión vigente y
colocarla aquí. No se incluye en el repositorio por tamaño y licencia.
