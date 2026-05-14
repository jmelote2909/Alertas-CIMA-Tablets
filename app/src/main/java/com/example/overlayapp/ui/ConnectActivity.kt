package com.example.overlayapp.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.overlayapp.databinding.ActivityConnectBinding
import com.example.overlayapp.network.LanListenerService

class ConnectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConnectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnConnect.setOnClickListener {
            // Simulate connection process
            binding.btnConnect.isEnabled = false
            binding.btnConnect.text = "CONECTANDO..."
            binding.progressBar.visibility = View.VISIBLE
            
            Handler(Looper.getMainLooper()).postDelayed({
                startApp()
            }, 1500)
        }
        
        // Start background service immediately so it's always connected
        startServiceImmediately()
    }

    private fun startServiceImmediately() {
        val serviceIntent = Intent(this, LanListenerService::class.java).apply {
            action = LanListenerService.ACTION_START
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }


    private fun startApp() {
        // Start background service
        val serviceIntent = Intent(this, LanListenerService::class.java).apply {
            action = LanListenerService.ACTION_START
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Go to main menu
        val intent = Intent(this, UserActivity::class.java)
        startActivity(intent)
        finish()
    }
}
