package com.pinyinlens.ocr

import android.content.Intent

/**
 * Guarda el resultado del permiso de MediaProjection concedido por el usuario
 * (RF-03). La captura necesita el (resultCode, data) del diálogo del sistema;
 * la Activity de permiso los deposita aquí y el servicio los consume.
 *
 * No guarda ningún contenido de pantalla, solo el token de autorización.
 */
object ProjectionHolder {
    @Volatile var resultCode: Int = Int.MIN_VALUE
        private set

    @Volatile var data: Intent? = null
        private set

    val isGranted: Boolean get() = data != null

    fun set(code: Int, intent: Intent) {
        resultCode = code
        data = intent
    }

    fun clear() {
        resultCode = Int.MIN_VALUE
        data = null
    }
}
