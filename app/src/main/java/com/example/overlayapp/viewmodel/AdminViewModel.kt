package com.example.overlayapp.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.overlayapp.model.AlertPriority
import com.example.overlayapp.network.LanNetworkManager
import kotlinx.coroutines.launch
import android.util.Log
import org.json.JSONObject
import android.content.Intent
import com.example.overlayapp.network.LanListenerService

class AdminViewModel(application: Application) : AndroidViewModel(application) {

    private val networkManager = LanNetworkManager(application)

    fun sendAlert(message: String, priority: AlertPriority, title: String = "Alertas Cima") {
        // 1. Enviar por red local (UDP)
        val intent = Intent(getApplication(), LanListenerService::class.java).apply {
            action = LanListenerService.ACTION_SEND_ALERT
            putExtra("title", title)
            putExtra("message", message)
            putExtra("priority", priority.name)
        }
        getApplication<Application>().startService(intent)

        // 2. Guardar en el servidor central (Postgres)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val alert = com.example.overlayapp.data.AlertEntity(
                    title = title,
                    message = message,
                    time = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()),
                    date = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date())
                )
                com.example.overlayapp.api.ApiClient.service.sendAlert(alert)
                Log.d("AdminViewModel", "Alerta sincronizada con servidor central")
            } catch (e: Exception) {
                Log.e("AdminViewModel", "Fallo al sincronizar con servidor central", e)
            }
        }
    }

    fun sendPing() {
        val intent = Intent(getApplication(), LanListenerService::class.java).apply {
            action = LanListenerService.ACTION_SEND_PING
        }
        getApplication<Application>().startService(intent)
    }
}
