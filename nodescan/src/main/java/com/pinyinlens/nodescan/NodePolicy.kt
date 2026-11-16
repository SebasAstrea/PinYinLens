package com.pinyinlens.nodescan

import com.pinyinlens.pinyin.CjkText

/**
 * Política pura que decide si un nodo del árbol de accesibilidad debe
 * procesarse para extraer Hanzi (RF-02 + RNF-03).
 *
 * Se modela sobre atributos primitivos (no sobre AccessibilityNodeInfo) para
 * poder probar en JVM las reglas de exclusión sin un árbol Android real:
 *  - Campos sensibles: nunca se leen nodos con isPassword (RNF-03).
 *  - Visibilidad: solo nodos visibles para el usuario con área > 0.
 *  - Contenido: el texto debe contener al menos un carácter Hanzi.
 *
 * La exclusión de ventanas FLAG_SECURE se aplica a nivel de ventana, antes de
 * recorrer sus nodos (ver AccessibilityTreeScanner).
 */
object NodePolicy {

    /**
     * @param text texto del nodo (puede ser null si el nodo no tiene texto).
     * @param isPassword atributo AccessibilityNodeInfo.isPassword.
     * @param isVisibleToUser atributo AccessibilityNodeInfo.isVisibleToUser.
     * @param hasPositiveArea true si el Rect en pantalla tiene ancho y alto > 0.
     */
    fun shouldProcess(
        text: CharSequence?,
        isPassword: Boolean,
        isVisibleToUser: Boolean,
        hasPositiveArea: Boolean,
    ): Boolean {
        if (isPassword) return false          // RNF-03: campos sensibles
        if (!isVisibleToUser) return false     // nodos fuera de pantalla
        if (!hasPositiveArea) return false     // nodos sin área dibujable
        if (text.isNullOrBlank()) return false
        return CjkText.containsHanzi(text)
    }
}
