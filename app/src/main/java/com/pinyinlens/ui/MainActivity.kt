package com.pinyinlens.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pinyinlens.R
import com.pinyinlens.ocr.MediaProjectionService
import com.pinyinlens.ocr.ProjectionHolder
import com.pinyinlens.ocr.ProjectionPermissionActivity

/**
 * Pantalla de control de PinYinLens.
 *
 * - Activa/desactiva el servicio de accesibilidad (abre los ajustes del sistema).
 * - **RF-03:** activa la lectura de imágenes (OCR). Android exige que el permiso
 *   de MediaProjection se conceda mediante el diálogo del sistema, lanzado desde
 *   una Activity; por eso hay un botón explícito. Concedido el permiso, el
 *   servicio detecta automáticamente las imágenes sin texto y las procesa (no
 *   hay que pulsar nada más).
 */
class MainActivity : AppCompatActivity() {

    private var statusView: TextView? = null
    private var ocrButton: Button? = null
    private var ocrStatus: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        statusView = TextView(this).apply {
            textSize = 18f
        }

        val openSettings = Button(this).apply {
            text = getString(R.string.main_open_accessibility)
            setOnClickListener {
                startActivity(android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        ocrStatus = TextView(this).apply {
            textSize = 16f
        }

        ocrButton = Button(this).apply {
            text = getString(R.string.main_enable_ocr)
            setOnClickListener { enableOcr() }
        }

        val disableOcr = Button(this).apply {
            text = getString(R.string.main_disable_ocr)
            setOnClickListener { disableOcr() }
        }

        root.addView(statusView)
        root.addView(openSettings)
        root.addView(ocrStatus)
        root.addView(ocrButton)
        root.addView(disableOcr)
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val accessibilityOn = isAccessibilityEnabled()
        statusView?.text = getString(
            if (accessibilityOn) R.string.main_status_on else R.string.main_status_off,
        )
        val ocrOn = ProjectionHolder.isGranted
        ocrStatus?.text = getString(
            if (ocrOn) R.string.main_ocr_on else R.string.main_ocr_off,
        )
        ocrButton?.isEnabled = !ocrOn
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            "enabled_accessibility_services",
        ) ?: return false
        return enabled.split(':').any { it.endsWith("/.service.PinYinLensAccessibilityService") }
    }

    /** RF-03: pide (si hace falta) notificaciones y lanza el diálogo de captura. */
    private fun enableOcr() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_REQUEST_CODE,
            )
            return
        }
        launchProjectionDialog()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == NOTIFICATION_REQUEST_CODE) {
            // Con o sin notificaciones se puede capturar; se continúa el flujo.
            launchProjectionDialog()
        }
    }

    private fun launchProjectionDialog() {
        startActivity(android.content.Intent(this, ProjectionPermissionActivity::class.java))
    }

    private fun disableOcr() {
        MediaProjectionService.stop(this)
        ProjectionHolder.clear()
        refreshStatus()
    }

    companion object {
        private const val NOTIFICATION_REQUEST_CODE = 7002
    }
}
