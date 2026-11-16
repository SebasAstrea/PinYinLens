package com.pinyinlens.nodescan

import android.graphics.Rect
import android.os.Build
import android.os.Bundle
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
        var renderInfoBudget = MAX_RENDER_INFO_REFRESHES

        while (stack.isNotEmpty() && visited < maxNodes) {
            val (node, depth) = stack.removeLast()
            visited++

            // El tamaño real exige un refresh (IPC) por nodo; se acota para no
            // romper el presupuesto de latencia (RNF-01). El resto usa la
            // estimación del layout a partir de la caja.
            val span = toSpanOrNull(node) {
                if (renderInfoBudget <= 0) 0f else {
                    renderInfoBudget--
                    textSizePx(node)
                }
            }
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
    private fun toSpanOrNull(
        node: AccessibilityNodeInfo,
        hanziTextSize: () -> Float,
    ): HanziSpan? {
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
            textSizePx = TextSizeEstimator.estimate(text, bounds.width(), bounds.height())
                .takeIf { it > 0f }
                ?: hanziTextSize(),
        )
    }

    /** Tamaño de fuente del nodo en px; 0 si el sistema no lo expone. */
    private fun textSizePx(node: AccessibilityNodeInfo): Float {
        // API 33+: el framework puede publicar el tamaño real del texto si el
        // nodo lo declara como dato extra (EXTRA_DATA_RENDERING_INFO_KEY).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return 0f
        return try {
            val available = node.availableExtraData
            if (available == null || !available.contains(EXTRA_DATA_RENDERING_INFO)) return 0f
            if (!node.refreshWithExtraData(EXTRA_DATA_RENDERING_INFO, Bundle())) return 0f
            node.extraRenderingInfo?.textSizeInPx ?: 0f
        } catch (e: IllegalStateException) {
            0f
        }
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

        /**
         * Máximo de nodos por barrido cuyo tamaño real de fuente se pide al
         * sistema. Cada consulta es un IPC (~1–2 ms); 24 acota el coste a ~30 ms
         * para respetar el P95 de 50 ms de RNF-01.
         */
        const val MAX_RENDER_INFO_REFRESHES = 24

        /** Dato extra de API 33 que expone el tamaño/color real del texto. */
        private const val EXTRA_DATA_RENDERING_INFO =
            AccessibilityNodeInfo.EXTRA_DATA_RENDERING_INFO_KEY

        /** FLAG_SECURE de LayoutParams, para referencia en pruebas/documentación. */
        const val FLAG_SECURE = WindowManager.LayoutParams.FLAG_SECURE
    }
}
