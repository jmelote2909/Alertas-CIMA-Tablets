package com.example.overlayapp.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.overlayapp.model.AlertPriority
import com.example.overlayapp.service.OverlayService
import com.example.overlayapp.viewmodel.AdminViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlertScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(OverlayService.EXTRA_TITLE) ?: "Alertas Cima"
        val message = intent.getStringExtra(OverlayService.EXTRA_MESSAGE) ?: ""
        val priorityStr = intent.getStringExtra(OverlayService.EXTRA_PRIORITY) ?: "HIGH"
        
        Log.d("AlertScheduleReceiver", "Alerta programada activada: $message")

        // 1. Show locally
        val localIntent = Intent(context, OverlayService::class.java).apply {
            putExtras(intent)
        }
        context.startService(localIntent)

        // 2. Broadcast to network
        val networkManager = LanNetworkManager(context)
        val priority = try { AlertPriority.valueOf(priorityStr) } catch (e: Exception) { AlertPriority.HIGH }
        
        val json = org.json.JSONObject().apply {
            put("type", "ALERT")
            put("title", title)
            put("message", message)
            put("priority", priority.name)
        }

        CoroutineScope(Dispatchers.IO).launch {
            networkManager.sendUdpBroadcast(json.toString())
            Log.d("AlertScheduleReceiver", "Alerta programada difundida a la red")
        }
    }
}
