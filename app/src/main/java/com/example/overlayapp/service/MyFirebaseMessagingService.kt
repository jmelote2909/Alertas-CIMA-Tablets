package com.example.overlayapp.service

import android.content.Intent
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

import com.example.overlayapp.data.AlertEntity
import com.example.overlayapp.data.AppDatabase
import com.example.overlayapp.network.WakeUpHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d("FCM_SERVICE", "Message received from: ${remoteMessage.from}")

        // Extract data from payload
        val data = remoteMessage.data
        val title = data["title"] ?: "Alertas Cima"
        val message = data["message"] ?: "Nueva Alerta"
        val priority = data["priority"] ?: "MEDIUM"

        // Wake up screen
        WakeUpHelper.wakeUpScreen(this)
        
        // Immediate Vibration
        triggerImmediateVibration()
        
        // High Priority Notification with Full Screen Intent
        showHighPriorityNotification(title, message)

        // Save to History Database
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val alertEntity = AlertEntity(
                    title = title,
                    message = message,
                    time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                    date = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                )
                AppDatabase.getDatabase(this@MyFirebaseMessagingService).alertDao().insertAlert(alertEntity)
                Log.d("FCM_SERVICE", "Alerta Firebase guardada en BD")
            } catch (e: Exception) {
                Log.e("FCM_SERVICE", "Error guardando alerta Firebase en BD", e)
            }
        }

        // Launch the OverlayService
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_TITLE, title)
            putExtra(OverlayService.EXTRA_MESSAGE, message)
            putExtra(OverlayService.EXTRA_PRIORITY, priority)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        startService(intent)
    }

    private fun triggerImmediateVibration() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(1000)
            }
        } catch (_: Exception) {}
    }

    private fun showHighPriorityNotification(title: String, message: String) {
        val channelId = "firebase_alerts_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Alertas Firebase", NotificationManager.IMPORTANCE_HIGH)
            notificationManager.createNotificationChannel(channel)
        }

        val fullScreenIntent = Intent(this, com.example.overlayapp.ui.UserActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(102, notification)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM_SERVICE", "New Token: $token")
        // Normally you'd send this token to your backend
    }
}
