package com.pinyinlens.pinyin

/**
 * Respaldo de pinyin por CARÁCTER (RF-04).
 *
 * El léxico de palabras no cubre todos los caracteres que aparecen en la UI del
 * sistema (p. ej. 星 de 三星, 帐/户, 屏/幕). [PinyinConverter] usa este mapa cuando
 * un carácter Hanzi no forma parte de ninguna palabra conocida, de modo que la
 * etiqueta no pierda sílabas ni invente lecturas.
 *
 * Formato del TSV (`assets/char_pinyin.tsv`, generado por
 * `scripts/build_char_pinyin.ps1`): una línea `CARÁCTER<TAB>pinyin_numérico`.
 */
object CharPinyin {

    /**
     * Parsea las líneas de la tabla. Ignora líneas vacías, comentarios (`#`) y
     * cualquier línea que no sea exactamente un carácter más su pinyin.
     */
    fun fromTsv(lines: Sequence<String>): Map<Char, String> {
        val map = HashMap<Char, String>(4096)
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val tab = line.indexOf('\t')
            if (tab != 1) continue
            val pinyin = line.substring(2).trim()
            if (pinyin.isEmpty()) continue
            map[line[0]] = pinyin
        }
        return map
    }
}
