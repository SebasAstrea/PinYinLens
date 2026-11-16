package com.pinyinlens.overlay

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas JVM de la especificación de ventana del overlay (RF-01).
 * Las FLAG_* de WindowManager son constantes primitivas, por lo que se
 * inlinean en compilación y no requieren framework Android en runtime.
 */
class OverlayWindowSpecTest {

    @Test
    fun usesAccessibilityOverlayWindowType() {
        assertEquals(
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            OverlayWindowSpec.WINDOW_TYPE,
        )
    }

    @Test
    fun defaultFlagsArePassThrough() {
        // La capa por defecto no debe interceptar toques.
        assertTrue(OverlayWindowSpec.isPassThrough(OverlayWindowSpec.PASS_THROUGH_FLAGS))
    }

    @Test
    fun defaultFlagsAreNotFocusable() {
        val flags = OverlayWindowSpec.PASS_THROUGH_FLAGS
        assertTrue((flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0)
    }

    @Test
    fun defaultFlagsLayoutFullScreen() {
        val flags = OverlayWindowSpec.PASS_THROUGH_FLAGS
        assertTrue((flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN) != 0)
        assertTrue((flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) != 0)
    }

    @Test
    fun touchableFlagsAreNotPassThrough() {
        // Un overlay que sí intercepta (p.ej. para RF-05) no es pass-through.
        val interceptingFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        assertFalse(OverlayWindowSpec.isPassThrough(interceptingFlags))
    }
}
