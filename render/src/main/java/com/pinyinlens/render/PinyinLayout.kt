package com.pinyinlens.render

/**
 * Una etiqueta de pinyin ya posicionada para dibujar junto al Hanzi (RF-04 +
 * RNF-04). Coordenadas en píxeles de pantalla.
 */
data class PinyinLabel(
    val text: String,
    val x: Float,          // centro horizontal del texto
    val baselineY: Float,  // línea base del texto
    val textSizePx: Float,
    /** Palabra Hanzi origen (para la tarjeta de diccionario del hold, RF-05). */
    val word: String = "",
)

/**
 * Mide el ancho que ocuparía [text] dibujado con [textSizePx]. Se inyecta para
 * que el layout siga siendo puro (JVM) y el ancho real lo aporte el `Paint` de
 * Android en la vista.
 */
fun interface TextMeasurer {
    fun widthOf(text: String, textSizePx: Float): Float
}

/**
 * Layout puro de las etiquetas de pinyin respecto a los caracteres (RF-04/render
 * + RNF-04). Sin dependencias de Android, testeable en JVM.
 *
 * Reglas:
 *  - **Mismo tamaño que el Hanzi de origen**: la fuente del pinyin parte del
 *    tamaño real del texto Hanzi ([Box.hanziTextPx]) y nunca lo supera. Si se
 *    desconoce, se estima a partir de la altura de la caja. El mínimo legible es
 *    [minTextPx] (9 sp) y hay un tope de seguridad [maxTextPx].
 *  - **Sin solapamientos horizontales**: el tamaño se reduce (solo lo necesario)
 *    para que la etiqueta quepa en el hueco horizontal libre de su palabra. Ese
 *    hueco va desde el inicio del Hanzi hasta la mitad del pasillo que lo separa
 *    de la siguiente palabra de la misma fila (o el borde de pantalla), con un
 *    tope de [MAX_EXPANSION] para no invadir controles ajenos. Si el pinyin es
 *    más ancho que su palabra se alinea a la izquierda del Hanzi (crece hacia la
 *    derecha, nunca hacia el margen); si cabe, se centra. Así un título corto
 *    conserva el tamaño del Hanzi aunque su pinyin sea más ancho.
 *  - **Siempre debajo del Hanzi, sin invadir las filas vecinas**: se ancla a la
 *    TINTA del Hanzi (no al box del nodo), así aprovecha el interlineado que las
 *    listas dejan entre el título y su subtítulo. Si el hueco inferior no
 *    alcanza, se reduce hasta [TIGHT_FLOOR] (algo más pequeño pero pegado a su
 *    Hanzi). Si ni así cabe, la etiqueta no se dibuja: pisar la fila vecina o
 *    dibujar por encima sería peor que dejar el Hanzi sin anotar.
 *  - Coordenadas en píxeles de pantalla.
 */
object PinyinLayout {

    /** Proporción de la altura del Hanzi usada cuando se desconoce su fuente. */
    const val FALLBACK_RATIO = 0.55f

    /**
     * Factor sobre el tamaño real del Hanzi. 1.0 = mismo tamaño que su Hanzi
     * (decisión de UX actual). El ancho ya se ajusta a la palabra, así que en la
     * práctica el pinyin queda en ~45–65% en palabras largas; fijar 0.6 fuerza
     * el rango 45–60% de RNF-04 en todos los casos.
     */
    const val HANZI_SIZE_FACTOR = 1f

    /** Fracción del em por encima de la línea base (aproximación de ascent). */
    const val ASCENT_RATIO = 0.78f

    /** Fracción del em por debajo de la línea base (aproximación de descent). */
    const val DESCENT_RATIO = 0.22f

    /** Parte del ancho disponible que la etiqueta puede ocupar como máximo. */
    const val WIDTH_FILL = 0.98f

    /**
     * Tope de ensanchamiento sobre el ancho de la caja. El pinyin latino mide
     * más que su Hanzi, así que una palabra corta (3 caracteres) necesita hasta
     * ~2× su caja para conservar el tamaño del Hanzi. Más allá de ese tope es
     * preferible reducir el pinyin antes que invadir el control vecino.
     */
    const val MAX_EXPANSION = 1.5f

    /**
     * Relación alto de caja / tamaño de fuente a partir de la cual se asume que
     * el nodo tiene varias líneas (o un relleno tan grande que no conviene
     * anclar a la tinta). En una sola línea, el texto va centrado en la caja.
     * Los `TextView` de listas con padding vertical generoso llegan a 2.0 em de
     * alto en una sola línea; una caja de dos líneas supera 2.2 em. El umbral
     * queda entre ambos.
     */
    const val MULTILINE_RATIO = 2.1f

    /** Semialto de la tinta del glifo Hanzi respecto al centro de la caja (em). */
    const val HANZI_INK_RATIO = 0.5f

    /**
     * Suelo del tamaño cuando una fila apretada obliga a reducir por debajo de
     * [minTextPx]. Preferimos pinyin algo más pequeño pero pegado a su Hanzi
     * antes que una etiqueta grande descolgada en el hueco de otra fila.
     */
    const val TIGHT_FLOOR = 0.6f

    /**
     * @param pinyin texto del pinyin de la palabra.
     * @param hanziTextPx tamaño de fuente real del Hanzi origen, en px; 0 si se
     *        desconoce.
     */
    data class Box(
        val pinyin: String,
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val hanziTextPx: Float = 0f,
        /** Palabra Hanzi origen (para la tarjeta de diccionario del hold, RF-05). */
        val word: String = "",
    )

    /**
     * @param minTextPx tamaño mínimo legible (9 sp en px).
     * @param maxTextPx tope de seguridad del tamaño.
     * @param gapPx separación entre el pinyin y el borde del carácter.
     * @param measure medida real del texto a un tamaño dado.
     * @param screenBottomPx borde inferior utilizable (normalmente alto de pantalla).
     * @param screenLeftPx borde izquierdo utilizable.
     * @param screenRightPx borde derecho utilizable.
     */
    fun layoutAll(
        boxes: List<Box>,
        minTextPx: Float,
        maxTextPx: Float,
        gapPx: Float,
        measure: TextMeasurer,
        screenBottomPx: Int = Int.MAX_VALUE,
        screenLeftPx: Int = 0,
        screenRightPx: Int = Int.MAX_VALUE,
    ): List<PinyinLabel> {
        val slotOf = arrayOfNulls<Slot>(boxes.size)
        for (i in boxes.indices) {
            val b = boxes[i]
            if (b.pinyin.isBlank()) continue

            // Ancho que puede ocupar la etiqueta: su caja más el hueco libre
            // hasta la palabra vecina de la misma fila (o el borde de pantalla).
            val maxWidth = horizontalRoom(i, boxes, screenLeftPx.toFloat(), screenRightPx.toFloat())
            val size = sizeFor(b, minTextPx, maxTextPx, maxWidth, measure)
            // Si el pinyin no cabe a lo ancho ni al mínimo legible (p. ej. un
            // párrafo de 50 Hanzi), no se dibuja: mejor sin anotación que una
            // línea de pinyin desbordada por encima del resto de la app.
            if (size <= 0f) continue
            val labelWidth = measure.widthOf(b.pinyin, size)
                .takeIf { it > 0f }
                ?: (b.right - b.left).toFloat()
            val centerX = (b.left + b.right) / 2f
            // Si el pinyin es más ancho que su palabra, crece hacia la derecha
            // desde el inicio del Hanzi (no hacia el margen izquierdo).
            val alignStart = labelWidth > (b.right - b.left)
            val labelLeft = if (alignStart) b.left.toFloat() else centerX - labelWidth / 2f
            val labelRight = labelLeft + labelWidth

            // Suelo: la tinta del nodo vecino inmediatamente inferior que se
            // solape horizontalmente CON LA ETIQUETA (que puede ser más ancha
            // que su caja), o el borde de pantalla. Se mide sobre la TINTA del
            // Hanzi, no sobre el box del nodo: las filas de lista (título +
            // subtítulo) tienen el subtítulo pegado al box del título, pero
            // dejan libre el interlineado, justo donde debe ir el pinyin.
            val bBottom = b.inkBottom()
            var floor = screenBottomPx.toFloat()
            for (j in boxes.indices) {
                if (j == i) continue
                val o = boxes[j]
                val overlapX = o.left < labelRight && o.right > labelLeft
                if (!overlapX) continue
                val oTop = o.inkTop()
                if (oTop >= bBottom && oTop < floor) floor = oTop
            }
            val spaceBelow = (floor - bBottom).coerceAtLeast(0f)
            // Garantía de "sin solapamiento": si ni al suelo de legibilidad cabe
            // debajo, no se dibuja. Nunca se coloca por encima del Hanzi.
            if (spaceBelow < gapPx + minTextPx * TIGHT_FLOOR) continue
            slotOf[i] = Slot(b, size, alignStart, spaceBelow)
        }

        val result = ArrayList<PinyinLabel>(boxes.size)
        for (i in boxes.indices) {
            val s = slotOf[i] ?: continue
            result.add(labelFor(s, minTextPx, gapPx, measure))
        }
        return result
    }

    /** Etiqueta final: siempre debajo, reducida al hueco disponible. */
    private fun labelFor(
        s: Slot,
        minTextPx: Float,
        gapPx: Float,
        measure: TextMeasurer,
    ): PinyinLabel {
        val b = s.box
        val centerX = (b.left + b.right) / 2f
        val floor = minTextPx * TIGHT_FLOOR
        // La posición horizontal se recalcula con el tamaño final: si el hueco
        // vertical obliga a reducir, la etiqueta se mantiene pegada al inicio
        // del Hanzi en vez de quedar desplazada.
        fun xFor(sizePx: Float): Float {
            if (!s.alignStart) return centerX
            val width = measure.widthOf(b.pinyin, sizePx).takeIf { it > 0f }
                ?: (b.right - b.left).toFloat()
            return b.left + width / 2f
        }
        // Si el hueco es menor que el tamaño ideal, se reduce al hueco
        // disponible (sin bajar del suelo de legibilidad).
        val fitted = (s.spaceBelow - gapPx).coerceIn(floor, s.size)
        return PinyinLabel(
            text = b.pinyin,
            x = xFor(fitted),
            baselineY = b.inkBottom() + gapPx + ASCENT_RATIO * fitted,
            textSizePx = fitted,
            word = b.word,
        )
    }

    /** `true` si la caja es una sola línea con el texto centrado verticalmente. */
    private fun Box.isSingleLine(): Boolean =
        hanziTextPx > 0f && (bottom - top) <= hanziTextPx * MULTILINE_RATIO

    /** Borde superior de la tinta del Hanzi (no del box del nodo). */
    private fun Box.inkTop(): Float =
        if (isSingleLine()) (top + bottom) / 2f - HANZI_INK_RATIO * hanziTextPx else top.toFloat()

    /** Borde inferior de la tinta del Hanzi (no del box del nodo). */
    private fun Box.inkBottom(): Float =
        if (isSingleLine()) (top + bottom) / 2f + HANZI_INK_RATIO * hanziTextPx else bottom.toFloat()

    /** `true` si ambas cajas están en la misma fila (sus tintas se solapan en Y). */
    private fun sameRow(a: Box, b: Box): Boolean =
        a.inkTop() < b.inkBottom() && a.inkBottom() > b.inkTop()

    /**
     * Ancho máximo que puede ocupar la etiqueta de [index] sin invadir a la
     * palabra siguiente de la misma fila. La etiqueta empieza en el borde
     * izquierdo de su Hanzi y crece hacia la derecha hasta la mitad del pasillo
     * (o el borde de pantalla), con tope [MAX_EXPANSION] sobre la caja.
     */
    private fun horizontalRoom(
        index: Int,
        boxes: List<Box>,
        screenLeft: Float,
        screenRight: Float,
    ): Float {
        val b = boxes[index]
        val boxWidth = (b.right - b.left).coerceAtLeast(1).toFloat()
        val start = maxOf(b.left.toFloat(), screenLeft)
        var right = screenRight
        for (j in boxes.indices) {
            if (j == index) continue
            val o = boxes[j]
            if (!sameRow(b, o)) continue
            // Punto medio del pasillo: las dos etiquetas pueden llegar a él
            // como mucho, sin tocarse.
            if (o.left >= b.right) right = minOf(right, (o.left + b.right) / 2f)
        }
        val room = (right - start).coerceAtLeast(1f)
        return room.coerceIn(boxWidth, boxWidth * MAX_EXPANSION)
    }

    /**
     * Datos de layout de una caja antes de resolver el hueco compartido.
     */
    private data class Slot(
        val box: Box,
        val size: Float,
        val alignStart: Boolean,
        val spaceBelow: Float,
    )

    /**
     * Tamaño del pinyin: el del Hanzi de origen, reducido lo justo para caber en
     * el ancho disponible (evita solaparse con la etiqueta vecina).
     *
     * @return 0 si ni al tamaño mínimo cabe en ese ancho (texto demasiado largo),
     *         en cuyo caso no procede dibujar la etiqueta.
     */
    private fun sizeFor(
        b: Box,
        minTextPx: Float,
        maxTextPx: Float,
        maxWidthPx: Float,
        measure: TextMeasurer,
    ): Float {
        val height = (b.bottom - b.top).coerceAtLeast(1)
        val source = if (b.hanziTextPx > 0f) {
            b.hanziTextPx * HANZI_SIZE_FACTOR
        } else {
            height * FALLBACK_RATIO
        }
        val base = source.coerceIn(minTextPx, maxTextPx)

        val measured = measure.widthOf(b.pinyin, base)
        if (measured <= 0f) return base

        val fit = (maxWidthPx * WIDTH_FILL) / measured
        if (fit >= 1f) return base
        val fitted = base * fit
        return if (fitted < minTextPx) 0f else fitted
    }
}
