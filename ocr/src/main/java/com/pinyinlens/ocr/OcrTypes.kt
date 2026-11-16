package com.pinyinlens.ocr

/**
 * Un bloque de texto reconocido por OCR, con su caja en coordenadas de la imagen
 * de entrada (RF-03). El servicio las traslada a coordenadas de pantalla sumando
 * el origen de la ROI capturada.
 */
data class OcrBlock(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

/**
 * Motor de OCR local (RF-03). Se abstrae en interfaz para:
 *  - mantener la orquestación (captura, ROI, privacidad) testeable sin el motor,
 *  - poder cambiar de implementación (ML Kit, Tesseract, RapidOCR) sin tocar el
 *    resto del pipeline.
 *
 * La implementación por defecto es ML Kit Chinese (bundled): el modelo va dentro
 * del APK, no descarga nada en runtime y no requiere INTERNET (RNF-03).
 */
interface OcrEngine {
    /**
     * Reconoce texto en [image]. Devuelve los bloques con caja en coordenadas de
     * la imagen. La implementación NO debe persistir la imagen (RNF-03).
     *
     * @param onResult callback con los bloques (puede ser asíncrono).
     * @param onError callback de error.
     */
    fun recognize(
        image: OcrImage,
        onResult: (List<OcrBlock>) -> Unit,
        onError: (Throwable) -> Unit,
    )

    /** Libera recursos del motor. */
    fun close()
}

/**
 * Envoltorio mínimo de la imagen a reconocer. Mantiene la referencia al Bitmap
 * de Android fuera de la lógica pura (el tipo real se pasa en el módulo app).
 */
class OcrImage(val bitmap: Any, val width: Int, val height: Int)
