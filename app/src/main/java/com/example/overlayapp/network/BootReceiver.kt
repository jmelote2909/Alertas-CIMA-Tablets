package com.example.overlayapp.network

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val bootAction = intent.action
        if (bootAction == Intent.ACTION_BOOT_COMPLETED || 
            bootAction == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
            bootAction == "android.intent.action.QUICKBOOT_POWERON" ||
            bootAction == "com.htc.intent.action.QUICKBOOT_POWERON") {
            // Start the service to listen for alerts
            val serviceIntent = Intent(context, LanListenerService::class.java).apply {
                action = LanListenerService.ACTION_START
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }

            // Also try to launch the main activity
            val activityIntent = Intent(context, com.example.overlayapp.ui.ConnectActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(activityIntent)
            } catch (e: Exception) {
                // Background activity start may be restricted on some Android versions
            }
            
            Toast.makeText(context, "ALERTAS CIMA ACTIVADO", Toast.LENGTH_LONG).show()
        }
    }
}
