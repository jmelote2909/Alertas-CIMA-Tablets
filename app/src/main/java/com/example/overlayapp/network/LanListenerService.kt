package com.example.overlayapp.network

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.overlayapp.R
import android.app.PendingIntent
import com.example.overlayapp.api.ApiClient
import com.example.overlayapp.model.AlertPriority
import com.example.overlayapp.service.OverlayService
import android.os.Vibrator
import android.os.VibratorManager
import android.os.VibrationEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import android.os.PowerManager
import com.example.overlayapp.data.AlertEntity
import com.example.overlayapp.data.AppDatabase

class LanListenerService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var socket: DatagramSocket? = null
    private var isListening = false
    private val activeDevices = mutableMapOf<String, Long>() // IP -> Last seen timestamp
    private lateinit var networkManager: LanNetworkManager
    private var multicastLock: WifiManager.MulticastLock? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private val shownAlertIds = mutableSetOf<String>()
    private val deviceId = java.util.UUID.randomUUID().toString()

    companion object {
        private const val TAG = "LanListener"
        private const val NOTIFICATION_ID = 101
        private const val CHANNEL_ID = "lan_listener_channel"
        const val ACTION_START = "ACTION_START_LISTENING"
        const val ACTION_STOP = "ACTION_STOP_LISTENING"
        const val ACTION_NEW_ALERT = "com.example.overlayapp.NEW_ALERT"
        const val ACTION_DEVICE_COUNT_CHANGED = "com.example.overlayapp.DEVICE_COUNT_CHANGED"
        const val ACTION_SEND_ALERT = "com.example.overlayapp.SEND_ALERT"
        const val ACTION_SEND_PING = "com.example.overlayapp.SEND_PING"
        const val ACTION_ALERT_ACK_RECEIVED = "com.example.overlayapp.ALERT_ACK_RECEIVED"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        networkManager = LanNetworkManager(this)
        // Ensure boot persistence
        Log.d(TAG, "LanListenerService creado")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIFICATION_ID, createNotification())
                startListening()
                startHeartbeat()
                startPruning()
            }
            ACTION_STOP -> {
                stopListening()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_SEND_ALERT -> {
                val title = intent.getStringExtra("title") ?: "Alertas Cima"
                val message = intent.getStringExtra("message") ?: ""
                val priority = intent.getStringExtra("priority") ?: "HIGH"
                sendAlertToNetwork(title, message, priority)
            }
            ACTION_SEND_PING -> {
                sendPingToNetwork()
            }
            else -> {
                startForeground(NOTIFICATION_ID, createNotification())
                startListening()
                startHeartbeat()
                startPruning()
            }
        }

        acquireMulticastLock()
        acquireWakeLock()
        acquireWifiLock()
        return START_STICKY
    }

    private fun acquireWifiLock() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wifiManager.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "CimaWifiLock")
        wifiLock?.acquire()
        Log.d(TAG, "WifiLock adquirido")
    }

    private fun releaseWifiLock() {
        wifiLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wifiLock = null
        Log.d(TAG, "WifiLock liberado")
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "CimaWakeLock")
        wakeLock?.acquire()
        Log.d(TAG, "WakeLock adquirido")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        wakeLock = null
        Log.d(TAG, "WakeLock liberado")
    }

    private fun acquireMulticastLock() {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        multicastLock = wifiManager.createMulticastLock("CimaMulticastLock")
        multicastLock?.setReferenceCounted(true)
        multicastLock?.acquire()
        Log.d(TAG, "MulticastLock adquirido")
    }

    private fun releaseMulticastLock() {
        multicastLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        multicastLock = null
        Log.d(TAG, "MulticastLock liberado")
    }

    private fun startListening() {
        if (isListening) return
        isListening = true

        serviceScope.launch {
            try {
                socket = DatagramSocket(null)
                socket?.reuseAddress = true
                socket?.bind(InetSocketAddress(LanNetworkManager.PORT))
                socket?.broadcast = true

                val buffer = ByteArray(4096)
                Log.d(TAG, "Escuchando en puerto ${LanNetworkManager.PORT}...")

                while (isListening) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket?.receive(packet)

                    val message = String(packet.data, 0, packet.length, Charsets.UTF_8)
                    handleIncomingMessage(message, packet.address.hostAddress)
                }
            } catch (e: Exception) {
                if (isListening) {
                    Log.e(TAG, "Error en el socket UDP", e)
                }
            } finally {
                socket?.close()
                isListening = false
            }
        }
    }

    private fun startHeartbeat() {
        serviceScope.launch {
            while (isListening) {
                networkManager.sendHeartbeat()
                kotlinx.coroutines.delay(5000) // Every 5 seconds
            }
        }
    }

    private fun startPruning() {
        serviceScope.launch {
            while (isListening) {
                val now = System.currentTimeMillis()
                val iterator = activeDevices.entries.iterator()
                var changed = false
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if (now - entry.value > 15000) { // Timeout 15s
                        iterator.remove()
                        changed = true
                    }
                }
                if (changed) broadcastDeviceCount()
                kotlinx.coroutines.delay(5000)
            }
        }
    }

    private fun broadcastDeviceCount() {
        val count = activeDevices.size + 1 // +1 for self
        val intent = Intent(ACTION_DEVICE_COUNT_CHANGED)
        intent.putExtra("count", count)
        sendBroadcast(intent)
    }

    private fun handleIncomingMessage(jsonStr: String, fromIp: String) {
        try {
            val json = JSONObject(jsonStr)
            val type = json.optString("type", "ALERT")
            
            if (type == "HEARTBEAT") {
                val oldCount = activeDevices.size
                activeDevices[fromIp] = System.currentTimeMillis()
                if (activeDevices.size != oldCount) {
                    broadcastDeviceCount()
                }
                return
            }
            
            if (type == "ACK") {
                val intent = Intent(ACTION_ALERT_ACK_RECEIVED).apply {
                    putExtra("fromIp", fromIp)
                }
                sendBroadcast(intent)
                return
            }

            if (type == "PING_ACK") {
                val intent = Intent("com.example.overlayapp.PING_ACK_RECEIVED").apply {
                    putExtra("fromIp", fromIp)
                }
                sendBroadcast(intent)
                return
            }

            if (type == "PING") {
                Log.d(TAG, "Ping recibido")
                sendPingAckTo(fromIp)
                return
            }
            
            if (type == "ALERT") {
                val senderId = json.optString("senderId", "")
                if (senderId == deviceId) {
                    Log.d(TAG, "Ignorando alerta propia")
                    return
                }
                
                val alertId = json.optString("id", "")
                if (alertId.isNotEmpty() && shownAlertIds.contains(alertId)) {
                    Log.d(TAG, "Alerta duplicada ignorada: $alertId")
                    return
                }
                if (alertId.isNotEmpty()) {
                    shownAlertIds.add(alertId)
                    // Keep cache small
                    if (shownAlertIds.size > 50) shownAlertIds.remove(shownAlertIds.first())
                }

                val title = json.optString("title", "Alertas Cima")
                val message = json.optString("message", "Mensaje crítico")
                val priority = json.optString("priority", "HIGH")
                
                serviceScope.launch(Dispatchers.Main) {
                    Toast.makeText(this@LanListenerService, "Alerta recibida por red", Toast.LENGTH_SHORT).show()
                }
                
                Log.d(TAG, "Lanzando OverlayService...")
                
                // Wake up screen
                wakeUpScreen()
                
                // Immediate Vibration (backup for Overlay)
                triggerImmediateVibration()
                
                // Show High Priority Notification with Full Screen Intent
                showHighPriorityNotification(title, message)

                // Save to History Database
                serviceScope.launch(Dispatchers.IO) {
                    try {
                        val alertEntity = AlertEntity(
                            title = title,
                            message = message,
                            time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                            date = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                        )
                        // Local Save
                        AppDatabase.getDatabase(this@LanListenerService).alertDao().insertAlert(alertEntity)
                        
                        // Central Server Save (Postgres)
                        try {
                            ApiClient.service.sendAlert(alertEntity)
                            Log.d(TAG, "Alerta sincronizada con servidor central")
                        } catch (e: Exception) {
                            Log.e(TAG, "Fallo al sincronizar con servidor central (¿Está encendido?)", e)
                        }
                        
                        Log.d(TAG, "Alerta guardada en base de datos local")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error guardando en BD", e)
                    }
                }
                
                // Launch Overlay
                val overlayIntent = Intent(this, OverlayService::class.java).apply {
                    putExtra(OverlayService.EXTRA_MESSAGE, message)
                    putExtra(OverlayService.EXTRA_PRIORITY, priority)
                    putExtra(OverlayService.EXTRA_TITLE, title)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startService(overlayIntent)

                // Broadcast to update history in UserActivity
                val broadcastIntent = Intent(ACTION_NEW_ALERT).apply {
                    putExtra("title", title)
                    putExtra("message", message)
                    putExtra("time", java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()))
                    putExtra("date", java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date()))
                }
                sendBroadcast(broadcastIntent)
                
                // Send ACK back to sender
                sendAckTo(fromIp)
            }
        } catch (e: Exception) {
            Log.e(TAG, "JSON inválido recibido", e)
        }
    }

    private fun stopListening() {
        isListening = false
        socket?.close()
        socket = null
        serviceJob.cancel()
    }

    private fun createNotification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setContentTitle("Alertas Cima - Red Local")
        .setContentText("Escuchando alertas...")
        .setSmallIcon(android.R.drawable.ic_dialog_info)
        .setPriority(NotificationCompat.PRIORITY_MAX)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .build()
    private fun sendAckTo(targetIp: String) {
        serviceScope.launch {
            try {
                val json = JSONObject().apply {
                    put("type", "ACK")
                    put("device", android.os.Build.MODEL)
                }
                val payload = json.toString()
                // Use NetworkManager to send directly to that IP if possible, or just broadcast
                // For simplicity, let's just broadcast the ACK as well, or send directly
                networkManager.sendToIp(payload, targetIp)
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando ACK", e)
            }
        }
    }

    private fun sendPingAckTo(targetIp: String) {
        serviceScope.launch {
            try {
                val json = JSONObject().apply {
                    put("type", "PING_ACK")
                    put("device", android.os.Build.MODEL)
                }
                networkManager.sendToIp(json.toString(), targetIp)
            } catch (e: Exception) {}
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Servicio de Alertas Cima",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Mantiene la conexión activa para recibir alertas en tiempo real."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun sendAlertToNetwork(title: String, message: String, priority: String) {
        val alertId = System.currentTimeMillis().toString()
        serviceScope.launch {
            try {
                val json = JSONObject().apply {
                    put("id", alertId)
                    put("type", "ALERT")
                    put("senderId", deviceId)
                    put("title", title)
                    put("message", message)
                    put("priority", priority)
                }
                val payload = json.toString()
                
                // Send 6 times with more spaced delays for maximum reliability
                repeat(6) { i ->
                    networkManager.sendUdpBroadcast(payload)
                    kotlinx.coroutines.delay(250L * (i + 1))
                }
                Log.d(TAG, "Alerta $alertId enviada 5 veces")
            } catch (e: Exception) {
                Log.e(TAG, "Error enviando alerta desde el servicio", e)
            }
        }
    }

    private fun sendPingToNetwork() {
        serviceScope.launch {
            try {
                val json = JSONObject().apply {
                    put("type", "PING")
                    put("senderId", deviceId)
                }
                networkManager.sendUdpBroadcast(json.toString())
            } catch (e: Exception) {}
        }
    }

    private fun wakeUpScreen() {
        WakeUpHelper.wakeUpScreen(this)
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
        } catch (e: Exception) {
            Log.e(TAG, "Error en vibración inmediata", e)
        }
    }

    private fun showHighPriorityNotification(title: String, message: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Full screen intent (standard Android way to wake up for critical alerts)
        val fullScreenIntent = Intent(this, com.example.overlayapp.ui.UserActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            this, 0, fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0)
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID + 1, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopListening()
        releaseMulticastLock()
        releaseWakeLock()
        releaseWifiLock()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
