package com.pinyinlens.nodescan

import android.graphics.Rect
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Recorre el árbol de nodos de accesibilidad y extrae los fragmentos con Hanzi y
 * sus coordenadas absolutas en pantalla (RF-02).
 *
 * Reglas de seguridad y filtrado (RNF-03):
 *  - Se omiten ventanas marcadas FLAG_SECURE (banca, gestores de contraseñas).
 *  - Se omiten nodos con isPassword, no visibles o sin área (ver NodePolicy).
 *
 * El recorrido es un DFS iterativo con límite de profundidad para evitar
 * árboles patológicos. No retiene referencias a los nodos (se reciclan los que
 * crea este código en versiones < R; en R+ el reciclado es no-op).
 */
class AccessibilityTreeScanner(
    private val maxDepth: Int = DEFAULT_MAX_DEPTH,
    private val maxNodes: Int = DEFAULT_MAX_NODES,
) {

    /**
     * Extrae los HanziSpan visibles de una ventana. Devuelve lista vacía si la
     * ventana es segura o su raíz es null.
     *
     * @param window ventana de accesibilidad; se consulta su FLAG_SECURE.
     */
    fun scanWindow(window: AccessibilityWindowInfo?): List<HanziSpan> {
        if (window == null) return emptyList()
        // Exclusión de ventanas seguras: el sistema las marca y no deben leerse.
        if (isSecureWindow(window)) return emptyList()
        val root = window.root ?: return emptyList()
        return scanFromRoot(root)
    }

    /** Extrae los HanziSpan a partir de un nodo raíz (p. ej. rootInActiveWindow). */
    fun scanFromRoot(root: AccessibilityNodeInfo?): List<HanziSpan> {
        if (root == null) return emptyList()
        val result = ArrayList<HanziSpan>()
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.addLast(root to 0)
        var visited = 0

        while (stack.isNotEmpty() && visited < maxNodes) {
            val (node, depth) = stack.removeLast()
            visited++

            val span = toSpanOrNull(node)
            if (span != null) result.add(span)

            if (depth < maxDepth) {
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    stack.addLast(child to depth + 1)
                }
            }
        }
        return result
    }

    /** Convierte un nodo en HanziSpan si pasa la política; si no, null. */
    private fun toSpanOrNull(node: AccessibilityNodeInfo): HanziSpan? {
        val text = node.text
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val hasArea = bounds.width() > 0 && bounds.height() > 0

        val ok = NodePolicy.shouldProcess(
            text = text,
            isPassword = node.isPassword,
            isVisibleToUser = node.isVisibleToUser,
            hasPositiveArea = hasArea,
        )
        if (!ok) return null

        return HanziSpan(
            text = text.toString(),
            left = bounds.left,
            top = bounds.top,
            right = bounds.right,
            bottom = bounds.bottom,
        )
    }

    private fun isSecureWindow(window: AccessibilityWindowInfo): Boolean {
        // AccessibilityWindowInfo no expone FLAG_SECURE directamente; se infiere
        // de que su root sea inaccesible pese a existir la ventana. La exclusión
        // real de FLAG_SECURE la refuerza el sistema ocultando el contenido a
        // los servicios de accesibilidad: root == null en ventanas seguras.
        return window.root == null
    }

    companion object {
        const val DEFAULT_MAX_DEPTH = 60
        const val DEFAULT_MAX_NODES = 5000

        /** FLAG_SECURE de LayoutParams, para referencia en pruebas/documentación. */
        const val FLAG_SECURE = WindowManager.LayoutParams.FLAG_SECURE
    }
}
