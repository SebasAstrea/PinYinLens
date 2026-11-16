package com.pinyinlens.ocr

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle

/**
 * Activity transparente que solicita el permiso de MediaProjection (RF-03).
 *
 * Android exige que el permiso de captura lo conceda el usuario mediante el
 * diálogo del sistema, lanzado desde una Activity. Al conceder, el token se
 * guarda en [ProjectionHolder] para que el servicio capture bajo demanda, y la
 * Activity se cierra de inmediato (no tiene UI propia).
 */
class ProjectionPermissionActivity : Activity() {

    private val requestCode = 7001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mpm = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(mpm.createScreenCaptureIntent(), requestCode)
    }

    @Deprecated("startActivityForResult es el flujo estándar para MediaProjection")
    override fun onActivityResult(reqCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(reqCode, resultCode, data)
        if (reqCode == requestCode && resultCode == RESULT_OK && data != null) {
            ProjectionHolder.set(resultCode, data)
        }
        finish()
    }
}
