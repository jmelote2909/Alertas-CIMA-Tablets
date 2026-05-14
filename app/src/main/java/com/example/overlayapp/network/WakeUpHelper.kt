package com.example.overlayapp.network

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.util.Log

object WakeUpHelper {
    fun wakeUpScreen(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            
            // Acquire wake lock to turn on screen
            @Suppress("DEPRECATION")
            val screenLock = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                "Cima:WakeUpHelper"
            )
            screenLock.acquire(10000) // 10 seconds
            
            // Dismiss keyguard is handled by Activity flags in OverlayService
            // km.requestDismissKeyguard requires an Activity, which we don't have here.
            
            Log.d("WakeUpHelper", "Pantalla despertada y Keyguard solicitado")
        } catch (e: Exception) {
            Log.e("WakeUpHelper", "Error al despertar pantalla", e)
        }
    }
}
