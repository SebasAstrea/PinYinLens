package com.pinyinlens.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.pinyinlens.R

/**
 * Pantalla mínima de arranque (andamiaje E0).
 *
 * Por ahora solo informa del estado y abre los ajustes de accesibilidad para
 * habilitar el servicio. El control real de activación (notificación persistente
 * o bola flotante) se aborda en la Épica 2 (UI-01).
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
        }

        val status = TextView(this).apply {
            text = getString(R.string.main_status_idle)
            textSize = 18f
        }

        val openSettings = Button(this).apply {
            text = getString(R.string.main_open_accessibility)
            setOnClickListener {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }

        root.addView(status)
        root.addView(openSettings)
        setContentView(root)
    }
}
