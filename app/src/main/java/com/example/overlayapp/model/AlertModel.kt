package com.example.overlayapp.model

enum class AlertPriority(val label: String, val emoji: String, val colorHex: String) {
    LOW("BAJA", "🟢", "#2E7D32"),
    MEDIUM("MEDIA", "🟡", "#F57F17"),
    HIGH("ALTA", "🔴", "#B71C1C"),
    CRITICAL("CRÍTICA", "❗", "#4A148C")
}

data class AlertModel(
    val message: String,
    val priority: AlertPriority,
    val title: String = "Alerta del Sistema",
    val sender: String = "Admin",
    val timestamp: Long = System.currentTimeMillis()
)

data class ScheduledAlert(
    val id: Long,
    val message: String,
    val priority: AlertPriority,
    val title: String,
    val triggerAtMillis: Long
)
