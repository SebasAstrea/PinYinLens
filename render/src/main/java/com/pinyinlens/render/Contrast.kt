package com.pinyinlens.render

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Cálculo de contraste WCAG (RNF-04): el pinyin debe tener una relación de
 * contraste >= 4.5:1 frente al fondo, independientemente del color de la app.
 *
 * Lógica pura. El servicio no conoce el color real del fondo en cada píxel, así
 * que la estrategia es: texto con un contorno (stroke) de color opuesto en
 * luminancia, de modo que el par texto/contorno siempre supere 4.5:1. Estas
 * funciones permiten verificarlo y elegir blanco/negro según convenga.
 */
object Contrast {

    /** Luminancia relativa WCAG de un color RGB (componentes 0–255). */
    fun relativeLuminance(r: Int, g: Int, b: Int): Double {
        fun channel(c: Int): Double {
            val s = c / 255.0
            return if (s <= 0.03928) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)
    }

    /** Relación de contraste entre dos colores RGB (>= 1.0). */
    fun ratio(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Double {
        val l1 = relativeLuminance(r1, g1, b1)
        val l2 = relativeLuminance(r2, g2, b2)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    /** True si el par de colores cumple WCAG AA para texto (>= 4.5:1). */
    fun meetsAA(r1: Int, g1: Int, b1: Int, r2: Int, g2: Int, b2: Int): Boolean =
        ratio(r1, g1, b1, r2, g2, b2) >= 4.5

    /**
     * Elige el color de contorno (blanco o negro) que maximiza el contraste con
     * el color de texto dado, para garantizar legibilidad sobre cualquier fondo.
     * Devuelve true si debe usarse contorno NEGRO, false si BLANCO.
     */
    fun preferBlackStroke(textR: Int, textG: Int, textB: Int): Boolean {
        val vsBlack = ratio(textR, textG, textB, 0, 0, 0)
        val vsWhite = ratio(textR, textG, textB, 255, 255, 255)
        return vsBlack >= vsWhite
    }
}
