package com.pinyinlens.ocr

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Captura UN fotograma de pantalla vía MediaProjection, recorta la ROI y entrega
 * el Bitmap (RF-03). Privacidad (RNF-03): el frame vive solo en RAM; el llamante
 * recicla el Bitmap tras la inferencia y este capturador no escribe a disco.
 *
 * Usa un ImageReader de un solo uso por captura para no mantener un flujo activo
 * (coherente con la arquitectura orientada a eventos, sin streaming continuo).
 */
class ScreenCapturer(
    private val projection: MediaProjection,
    private val screenWidth: Int,
    private val screenHeight: Int,
    private val densityDpi: Int,
) {

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Captura un frame y devuelve el recorte [roi] como Bitmap por [onBitmap].
     * El llamante DEBE reciclar el Bitmap tras usarlo.
     */
    fun captureRegion(roi: Rect, onBitmap: (Bitmap?) -> Unit) {
        val reader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
        var virtualDisplay: VirtualDisplay? = null
        var delivered = false

        fun cleanup() {
            try { virtualDisplay?.release() } catch (_: Exception) {}
            try { reader.close() } catch (_: Exception) {}
        }

        reader.setOnImageAvailableListener({ r ->
            if (delivered) return@setOnImageAvailableListener
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val rowStride = plane.rowStride
                val pixelStride = plane.pixelStride
                val rowPadding = rowStride - pixelStride * screenWidth
                val full = Bitmap.createBitmap(
                    screenWidth + rowPadding / pixelStride, screenHeight, Bitmap.Config.ARGB_8888,
                )
                full.copyPixelsFromBuffer(plane.buffer)
                // Recorte seguro de la ROI dentro de los límites de la pantalla.
                val left = roi.left.coerceIn(0, screenWidth - 1)
                val top = roi.top.coerceIn(0, screenHeight - 1)
                val w = roi.width().coerceIn(1, screenWidth - left)
                val h = roi.height().coerceIn(1, screenHeight - top)
                val cropped = Bitmap.createBitmap(full, left, top, w, h)
                full.recycle() // RNF-03: liberar el frame completo de inmediato
                delivered = true
                handler.post { onBitmap(cropped) }
            } catch (e: Exception) {
                Log.e(TAG, "Error procesando el frame", e)
                handler.post { onBitmap(null) }
            } finally {
                image.close()
                cleanup()
            }
        }, handler)

        virtualDisplay = projection.createVirtualDisplay(
            "PinYinLensCapture",
            screenWidth, screenHeight, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface, null, handler,
        )

        // Salvaguarda: si no llega frame en 1 s, limpiar y reportar null.
        handler.postDelayed({
            if (!delivered) { delivered = true; cleanup(); onBitmap(null) }
        }, 1000)
    }

    companion object {
        const val TAG = "PinYinLens"
    }
}
