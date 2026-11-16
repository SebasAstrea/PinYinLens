package com.pinyinlens.ocr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log

/**
 * Foreground service de tipo mediaProjection (RF-03).
 *
 * Android 10+ exige un servicio en primer plano con type=mediaProjection activo
 * para poder capturar pantalla. Este servicio solo mantiene esa condición; la
 * captura y el OCR los ejecuta el AccessibilityService que sostiene el token.
 */
class MediaProjectionService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundCompat()
        return START_NOT_STICKY
    }

    private fun startForegroundCompat() {
        val channelId = "pyl_ocr"
        val nm = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(channelId, "PinYinLens OCR", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notif: Notification = Notification.Builder(this, channelId)
            .setContentTitle("PinYinLens")
            .setContentText("Lectura de imágenes activa")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(1, notif)
        }
        Log.i(TAG, "MediaProjectionService en primer plano")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val TAG = "PinYinLens"
        fun start(context: Context) {
            val i = Intent(context, MediaProjectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(i)
            } else {
                context.startService(i)
            }
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, MediaProjectionService::class.java))
        }
    }
}
