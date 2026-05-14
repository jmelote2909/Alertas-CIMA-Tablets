package com.example.overlayapp.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.example.overlayapp.databinding.ActivityUserBinding
import com.example.overlayapp.model.AlertPriority
import com.example.overlayapp.network.LanListenerService
import com.example.overlayapp.service.OverlayService
import com.example.overlayapp.data.AppDatabase
import com.example.overlayapp.data.AlertEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UserActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserBinding
    
    private val alertReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                LanListenerService.ACTION_NEW_ALERT -> {
                    val title = intent.getStringExtra("title") ?: "Alerta"
                    val message = intent.getStringExtra("message") ?: ""
                    val time = intent.getStringExtra("time") ?: ""
                    val date = intent.getStringExtra("date") ?: ""
                    addAlertToHistory(title, message, time, date)
                }
                LanListenerService.ACTION_DEVICE_COUNT_CHANGED -> {
                    val count = intent.getIntExtra("count", 1)
                    binding.tvConnectedDevices.text = getString(com.example.overlayapp.R.string.connected_devices, count)
                }
            }
        }
    }

    private val allAlerts = mutableListOf<AlertEntry>()
    private var visibleCount = 5
    private var currentFilter = "Todas"
    private var currentSearch = ""

    data class AlertEntry(val title: String, val message: String, val time: String, val date: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupUI()
        setupHistoryControls()
        checkBatteryOptimizations()
        loadHistoryFromDb()
        
        // Register receiver for new alerts and device count
        val filter = IntentFilter().apply {
            addAction(LanListenerService.ACTION_NEW_ALERT)
            addAction(LanListenerService.ACTION_DEVICE_COUNT_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(alertReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(alertReceiver, filter)
        }
    }

    private fun setupHistoryControls() {
        // Search
        binding.etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentSearch = s.toString().lowercase()
                visibleCount = 5 // Reset pagination on search
                updateHistoryDisplay()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // Filter
        binding.spinnerFilter.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentFilter = parent?.getItemAtPosition(position).toString()
                visibleCount = 5 // Reset pagination on filter
                updateHistoryDisplay()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        // Load More
        binding.btnLoadMore.setOnClickListener {
            visibleCount += 5
            updateHistoryDisplay()
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(alertReceiver)
    }

    private fun setupUI() {
        binding.btnEnterAdmin.setOnClickListener {
            if (hasOverlayPermission()) {
                startActivity(Intent(this, LoginActivity::class.java))
            } else {
                Toast.makeText(this, "⚠️ Debes conceder los permisos de superposición primero", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnModeUser.setOnClickListener {
            if (hasOverlayPermission()) {
                Toast.makeText(this, "Modo Usuario: Escuchando alertas Cima", Toast.LENGTH_SHORT).show()
                moveTaskToBack(true)
            } else {
                Toast.makeText(this, "⚠️ Debes conceder los permisos de superposición primero", Toast.LENGTH_LONG).show()
            }
        }

        binding.btnGrantPermissions.setOnClickListener {
            if (hasOverlayPermission()) {
                Toast.makeText(this, "✅ Permisos concedidos", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }
    }

    private fun addAlertToHistory(title: String, message: String, time: String, date: String) {
        // Just reload everything from DB to be safe and consistent
        loadHistoryFromDb()
    }

    private fun loadHistoryFromDb() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dbAlerts = AppDatabase.getDatabase(this@UserActivity).alertDao().getAllAlertsList()
                withContext(Dispatchers.Main) {
                    allAlerts.clear()
                    allAlerts.addAll(dbAlerts.map { AlertEntry(it.title, it.message, it.time, it.date) })
                    updateHistoryDisplay()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("UserActivity", "Error cargando historial", e)
                }
            }
        }
    }

    private fun updateHistoryDisplay() {
        binding.layoutHistory.removeAllViews()
        
        // Apply Search and Filter
        val filtered = allAlerts.filter { alert ->
            val matchesSearch = if (currentSearch.isEmpty()) true else {
                alert.title.lowercase().contains(currentSearch) || 
                alert.message.lowercase().contains(currentSearch)
            }
            
            val matchesFilter = when (currentFilter) {
                "Título" -> alert.title.lowercase().contains(currentSearch)
                "Mensaje" -> alert.message.lowercase().contains(currentSearch)
                "Fecha" -> alert.date.contains(currentSearch)
                else -> true
            }
            
            matchesSearch && (currentFilter == "Todas" || matchesFilter)
        }

        if (filtered.isEmpty()) {
            binding.tvEmptyHistory.visibility = View.VISIBLE
            binding.btnLoadMore.visibility = View.GONE
            binding.layoutHistory.addView(binding.tvEmptyHistory)
            return
        }

        binding.tvEmptyHistory.visibility = View.GONE
        
        // Show up to visibleCount
        val toShow = filtered.take(visibleCount)
        
        for (alert in toShow) {
            renderAlertItem(alert)
        }

        // Show/Hide Load More
        if (filtered.size > visibleCount) {
            binding.btnLoadMore.visibility = View.VISIBLE
        } else {
            binding.btnLoadMore.visibility = View.GONE
        }
    }

    private fun renderAlertItem(alert: AlertEntry) {
        val itemLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 24, 16, 24)
            
            val bottomLine = View(context).apply {
                setBackgroundColor(Color.parseColor("#22FFFFFF"))
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
            }
            
            val titleRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val tvTitle = TextView(context).apply {
                text = alert.title
                setTextColor(Color.WHITE)
                textSize = 15f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
            }

            val tvDate = TextView(context).apply {
                text = alert.date
                setTextColor(Color.parseColor("#66FFFFFF"))
                textSize = 11f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

            val tvTime = TextView(context).apply {
                text = alert.time
                setTextColor(Color.parseColor("#88FFFFFF"))
                textSize = 12f
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f)
            }

            titleRow.addView(tvTitle)
            titleRow.addView(tvDate)
            titleRow.addView(tvTime)

            val tvMsg = TextView(context).apply {
                text = alert.message
                setTextColor(Color.parseColor("#2196F3"))
                textSize = 14f
                setPadding(0, 8, 0, 0)
            }

            addView(titleRow)
            addView(tvMsg)
            
            binding.layoutHistory.addView(this)
            binding.layoutHistory.addView(bottomLine)
        }
    }

    private fun updatePermissionStatus() {
        if (hasOverlayPermission()) {
            binding.tvPermissionStatus.text = "🟢 Permisos OK"
            binding.tvPermissionStatus.setTextColor(Color.parseColor("#42A5F5"))
        } else {
            binding.tvPermissionStatus.text = "🔴 Sin permisos"
            binding.tvPermissionStatus.setTextColor(Color.parseColor("#E53935"))
        }
    }

    private fun hasOverlayPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)

    private fun checkBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                Toast.makeText(this, "Para recibir alertas siempre, desactiva el ahorro de batería para esta app", Toast.LENGTH_LONG).show()
                try {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    try {
                        startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                    } catch (ex: Exception) {}
                }
            }
        }
    }
}
