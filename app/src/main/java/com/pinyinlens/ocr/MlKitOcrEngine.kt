package com.pinyinlens.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions

/**
 * Implementación de [OcrEngine] con ML Kit Text Recognition (chino), variante
 * BUNDLED (RF-03 + RNF-03).
 *
 * El modelo va dentro del APK (dependencia com.google.mlkit:text-recognition-
 * chinese), así que el reconocimiento es 100% local: no descarga nada en runtime
 * ni requiere el permiso INTERNET. El Bitmap de entrada no se persiste; el
 * llamante lo recicla tras recibir el resultado (RNF-03).
 */
class MlKitOcrEngine : OcrEngine {

    private val recognizer =
        TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    override fun recognize(
        image: OcrImage,
        onResult: (List<OcrBlock>) -> Unit,
        onError: (Throwable) -> Unit,
    ) {
        val bitmap = image.bitmap as? Bitmap
        if (bitmap == null) {
            onError(IllegalArgumentException("OcrImage.bitmap no es un Bitmap"))
            return
        }
        val input = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(input)
            .addOnSuccessListener { visionText ->
                val blocks = ArrayList<OcrBlock>()
                for (block in visionText.textBlocks) {
                    for (line in block.lines) {
                        val b = line.boundingBox
                        if (b != null && line.text.isNotBlank()) {
                            blocks.add(OcrBlock(line.text, b.left, b.top, b.right, b.bottom))
                        }
                    }
                }
                onResult(blocks)
            }
            .addOnFailureListener { onError(it) }
    }

    override fun close() {
        recognizer.close()
    }
}
