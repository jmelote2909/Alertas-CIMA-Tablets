package com.example.overlayapp

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.util.Log

class MainService : Service() {

    private val CHANNEL_ID = "OverlayServiceChannel"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, createNotification())
        
        // TODO: Start your network listener here (WebSocket, FCM, etc.)
        startNetworkListener()
    }

    private fun startNetworkListener() {
        // Placeholder for network communication
        Log.d("MainService", "Listening for remote alerts...")
        
        // Example: Simulating an alert received after 10 seconds
        /*
        Thread {
            Thread.sleep(10000)
            AlertState.isAlertActive = true
            Log.d("MainService", "Remote Alert Triggered!")
        }.start()
        */
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Servicio de Alertas Cima",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Alertas Cima Activo")
            .setContentText("Esperando alertas críticas...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
