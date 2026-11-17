package com.pinyinlens.overlay

import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pruebas JVM de la especificación de ventanas del overlay (RF-01 + RF-05).
 * Las FLAG_* de WindowManager son constantes primitivas, por lo que se inlinean
 * en compilación y no requieren framework Android en runtime.
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
    fun renderLayerIsPassThrough() {
        // La capa que dibuja el pinyin NO recibe toques: la app inferior navega
        // con normalidad aunque el overlay esté activo (corrección del bloqueo).
        val flags = OverlayWindowSpec.RENDER_FLAGS
        assertTrue(OverlayWindowSpec.isPassThrough(flags))
        assertFalse(OverlayWindowSpec.isTouchInteractive(flags))
    }

    @Test
    fun renderLayerIsNotFocusableAndCoversScreen() {
        val flags = OverlayWindowSpec.RENDER_FLAGS
        assertTrue((flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0)
        assertTrue((flags and WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN) != 0)
        assertTrue((flags and WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS) != 0)
    }

    @Test
    fun touchTargetsAreInteractiveButNotModal() {
        // Las ventanas de hold sí reciben toques, solo dentro de su rectángulo.
        val flags = OverlayWindowSpec.TOUCH_TARGET_FLAGS
        assertFalse(OverlayWindowSpec.isPassThrough(flags))
        assertTrue(OverlayWindowSpec.isTouchInteractive(flags))
        assertTrue((flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) != 0)
        assertTrue((flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL) != 0)
    }

    @Test
    fun legacyAliasesKeepTheirMeaning() {
        // PASS_THROUGH_FLAGS era la capa de dibujo; INTERACTIVE_FLAGS el modo
        // táctil. Ahora el táctil se aplica a ventanas pequeñas, no a la capa.
        assertEquals(OverlayWindowSpec.RENDER_FLAGS, OverlayWindowSpec.PASS_THROUGH_FLAGS)
        assertEquals(OverlayWindowSpec.TOUCH_TARGET_FLAGS, OverlayWindowSpec.INTERACTIVE_FLAGS)
    }
}
