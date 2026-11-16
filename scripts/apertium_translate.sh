#!/bin/bash
# Traduce EN->ES con Apertium local. Lee líneas de $1 (entrada) y escribe la
# traducción línea a línea en $2 (salida). Una línea de entrada -> una de salida.
# Usado por build_dictionary.ps1 para evitar el rate-limit de la API web.
set -euo pipefail
IN="$1"
OUT="$2"
apertium eng-spa < "$IN" > "$OUT"
