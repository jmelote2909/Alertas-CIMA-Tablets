package com.example.overlayapp.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.overlayapp.databinding.ActivityAdminBinding
import com.example.overlayapp.model.AlertPriority
import com.example.overlayapp.service.OverlayService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.app.TimePickerDialog
import androidx.lifecycle.ViewModelProvider
import com.example.overlayapp.viewmodel.AdminViewModel
import java.util.Calendar
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.lifecycle.lifecycleScope
import com.example.overlayapp.network.LanListenerService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.graphics.Color


class AdminActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAdminBinding
    private lateinit var viewModel: AdminViewModel
    private var selectedPriority: AlertPriority = AlertPriority.HIGH
    private var selectedCalendar: Calendar? = null
    private val receivedAcks = mutableSetOf<String>()
    private val receivedPings = mutableSetOf<String>()
    
    private val ackReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                LanListenerService.ACTION_ALERT_ACK_RECEIVED -> {
                    val fromIp = intent.getStringExtra("fromIp")
                    if (fromIp != null) receivedAcks.add(fromIp)
                }
                "com.example.overlayapp.PING_ACK_RECEIVED" -> {
                    val fromIp = intent.getStringExtra("fromIp")
                    if (fromIp != null) receivedPings.add(fromIp)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)
        viewModel = ViewModelProvider(this).get(AdminViewModel::class.java)
        setupUI()
    }

    private fun setupUI() {
        binding.btnBack.setOnClickListener { finish() }

        // Time Selection
        binding.btnSelectTime.setOnClickListener {
            val now = Calendar.getInstance()
            TimePickerDialog(this, { _, hour, minute ->
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, hour)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                
                if (cal.before(now)) {
                    cal.add(Calendar.DATE, 1)
                }
                
                selectedCalendar = cal
                val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
                binding.tvScheduledInfo.text = "Hora seleccionada: ${fmt.format(cal.time)}"
            }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), true).show()
        }

        // Send Now
        binding.btnSend.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            val title = binding.etTitle.text.toString().trim().ifEmpty { "Alertas Cima" }

            if (message.isEmpty()) {
                binding.etMessage.error = "Escribe un mensaje"
                return@setOnClickListener
            }
            
            // Send via Network
            receivedAcks.clear()
            viewModel.sendAlert(message, AlertPriority.HIGH, title)
            
            // Show feedback after a short delay to collect ACKs
            lifecycleScope.launch {
                binding.btnSend.isEnabled = false
                binding.btnSend.text = "ENVIANDO..."
                binding.btnSend.setBackgroundColor(Color.GRAY)
                
                delay(2000) // Wait 2s for ACKs
                
                val count = receivedAcks.size
                if (count > 0) {
                    Toast.makeText(this@AdminActivity, "Alerta enviada correctamente a $count dispositivos", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@AdminActivity, "Alerta enviada, pero no se ha confirmado recepción", Toast.LENGTH_LONG).show()
                }
                
                binding.btnSend.isEnabled = true
                binding.btnSend.text = "ENVIAR AHORA"
                binding.btnSend.setBackgroundColor(Color.parseColor("#2196F3"))
            }
        }

        // Schedule
        binding.btnSchedule.setOnClickListener {
            val message = binding.etMessage.text.toString().trim()
            val title = binding.etTitle.text.toString().trim().ifEmpty { "Alertas Cima" }
            val cal = selectedCalendar

            if (message.isEmpty()) {
                binding.etMessage.error = "Escribe un mensaje"
                return@setOnClickListener
            }
            if (cal == null) {
                Toast.makeText(this, "Selecciona una hora primero", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            scheduleAlertAtTime(message, title, AlertPriority.HIGH, cal)
            Toast.makeText(this, "Alerta programada correctamente", Toast.LENGTH_LONG).show()
            finish()
        }

        // Simulate
        binding.btnSimulateNow.setOnClickListener {
            if (!hasOverlayPermission()) {
                requestOverlayPermission()
                return@setOnClickListener
            }
            launchAlert(
                "Prueba del sistema: Todo funciona correctamente.",
                "Alertas Cima",
                AlertPriority.CRITICAL
            )
        }

        // Ping Network
        binding.btnPingNetwork.setOnClickListener {
            receivedPings.clear()
            viewModel.sendPing()
            binding.btnPingNetwork.text = "VERIFICANDO RED..."
            binding.btnPingNetwork.isEnabled = false
            
            lifecycleScope.launch {
                delay(1500)
                val count = receivedPings.size
                if (count > 0) {
                    Toast.makeText(this@AdminActivity, "✅ Red verificada: $count dispositivos activos", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@AdminActivity, "❌ No se detectaron otros dispositivos activos", Toast.LENGTH_LONG).show()
                }
                binding.btnPingNetwork.text = "TEST DE CONEXIÓN DE RED"
                binding.btnPingNetwork.isEnabled = true
            }
        }
    }

    private fun launchAlert(message: String, title: String, priority: AlertPriority) {
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra(OverlayService.EXTRA_MESSAGE, message)
            putExtra(OverlayService.EXTRA_PRIORITY, priority.name)
            putExtra(OverlayService.EXTRA_TITLE, title)
        }
        startService(intent)
    }

    private fun scheduleAlertAtTime(message: String, title: String, priority: AlertPriority, calendar: Calendar) {
        val triggerAt = calendar.timeInMillis

        val intent = Intent(this, com.example.overlayapp.network.AlertScheduleReceiver::class.java).apply {
            putExtra(OverlayService.EXTRA_MESSAGE, message)
            putExtra(OverlayService.EXTRA_PRIORITY, priority.name)
            putExtra(OverlayService.EXTRA_TITLE, title)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            (System.currentTimeMillis() % 10000).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }

        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeStr = fmt.format(calendar.time)
        binding.tvScheduledInfo.text = "Programada para las $timeStr"
        Toast.makeText(this, "Alerta programada para las $timeStr", Toast.LENGTH_LONG).show()
    }

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(LanListenerService.ACTION_ALERT_ACK_RECEIVED)
            addAction("com.example.overlayapp.PING_ACK_RECEIVED")
        }
        registerReceiver(ackReceiver, filter)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(ackReceiver)
    }

    private fun requestOverlayPermission() {
        Toast.makeText(this, "Activa el permiso de superposición primero", Toast.LENGTH_LONG).show()
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}
