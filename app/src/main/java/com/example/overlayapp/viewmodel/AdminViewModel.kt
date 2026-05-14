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
        val intent = Intent(getApplication(), LanListenerService::class.java).apply {
            action = LanListenerService.ACTION_SEND_ALERT
            putExtra("title", title)
            putExtra("message", message)
            putExtra("priority", priority.name)
        }
        getApplication<Application>().startService(intent)
    }

    fun sendPing() {
        val intent = Intent(getApplication(), LanListenerService::class.java).apply {
            action = LanListenerService.ACTION_SEND_PING
        }
        getApplication<Application>().startService(intent)
    }
}
