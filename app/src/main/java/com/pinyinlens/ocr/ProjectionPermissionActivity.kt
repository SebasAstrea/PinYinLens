package com.pinyinlens.ocr

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.util.Log

/**
 * Activity transparente que solicita el permiso de MediaProjection (RF-03).
 *
 * Android exige que el permiso de captura lo conceda el usuario mediante el
 * diálogo del sistema, lanzado desde una Activity. Al conceder, el token se
 * guarda en [ProjectionHolder] para que el servicio capture bajo demanda, se
 * arranca el foreground service de tipo mediaProjection (exigido en Android 10+
 * para capturar) y la Activity se cierra de inmediato (no tiene UI propia).
 *
 * Si el permiso ya estaba concedido en esta sesión, no se muestra el diálogo:
 * solo se asegura el foreground service.
 */
class ProjectionPermissionActivity : Activity() {

    private val requestCode = 7001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ProjectionHolder.isGranted) {
            // Re-activación sin volver a molestar al usuario con el diálogo.
            MediaProjectionService.start(this)
            finish()
            return
        }
        val mpm = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(mpm.createScreenCaptureIntent(), requestCode)
    }

    @Deprecated("startActivityForResult es el flujo estándar para MediaProjection")
    override fun onActivityResult(reqCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(reqCode, resultCode, data)
        if (reqCode == requestCode && resultCode == RESULT_OK && data != null) {
            ProjectionHolder.set(resultCode, data)
            // El FGS de tipo mediaProjection debe estar activo para capturar;
            // se arranca ahora, con la app en primer plano (permiso concedido).
            MediaProjectionService.start(this)
            Log.i(TAG, "RF03 permiso de captura concedido")
        }
        finish()
    }

    companion object {
        const val TAG = "PinYinLens"
    }
}
