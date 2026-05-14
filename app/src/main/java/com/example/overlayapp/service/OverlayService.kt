package com.example.overlayapp.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.media.RingtoneManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.AlphaAnimation
import android.view.animation.ScaleAnimation
import android.view.animation.AnimationSet
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Button
import androidx.core.content.ContextCompat
import com.example.overlayapp.model.AlertPriority

class OverlayService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var vibrator: Vibrator? = null
    private val handler = Handler(Looper.getMainLooper())
    private var isVibrating = false

    companion object {
        const val EXTRA_MESSAGE = "EXTRA_MESSAGE"
        const val EXTRA_PRIORITY = "EXTRA_PRIORITY"
        const val EXTRA_TITLE = "EXTRA_TITLE"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.getStringExtra(EXTRA_MESSAGE) ?: "¡Alerta del Sistema!"
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Alertas Cima"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        showOverlay(message, title)
        startContinuousVibration()
        playAlertSound()

        return START_NOT_STICKY
    }

    private fun showOverlay(message: String, title: String) {
        if (overlayView != null) {
            windowManager?.removeView(overlayView)
            overlayView = null
        }

        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            PixelFormat.TRANSLUCENT
        )

        val rootFrame = FrameLayout(this)

        // === VERY TRANSPARENT DARK BACKGROUND ===
        val dimBg = View(this)
        dimBg.setBackgroundColor(Color.parseColor("#40000000")) 
        rootFrame.addView(dimBg, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // === SCROLL VIEW TO HANDLE LANDSCAPE ===
        val scrollView = ScrollView(this).apply {
            isFillViewport = true
        }

        // === CARD CONTAINER ===
        val cardContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(40, 40, 40, 40)
        }

        // Blue theme
        val topColor = "#2196F3"
        val bottomColor = "#0D47A1"

        val cardGradient = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor(topColor), Color.parseColor(bottomColor))
        ).apply {
            cornerRadius = 40f
        }

        val cardView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(60, 60, 60, 60)
            background = cardGradient
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) elevation = 20f
        }

        // Smaller card (approx 65% screen width)
        val cardLp = LinearLayout.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.50).toInt(),
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        cardView.layoutParams = cardLp

        // === ICON ===
        val tvEmoji = TextView(this).apply {
            text = "🔔"
            textSize = 42f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 10)
        }

        // === TITLE ===
        val tvTitle = TextView(this).apply {
            text = "Alertas Cima"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 4)
        }

        // === DIVIDER ===
        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#44FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 2
            ).also { it.setMargins(0, 8, 0, 16) }
        }

        // === MESSAGE ===
        val tvMessage = TextView(this).apply {
            text = message
            setTextColor(Color.parseColor("#2196F3"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 32)
        }

        // === ACCEPT BUTTON WITH COUNTDOWN ===
        val btnAccept = Button(this).apply {
            text = "Aceptar (5)"
            isEnabled = false
            setTextColor(Color.LTGRAY)
            textSize = 16f
            setTypeface(null, Typeface.BOLD)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 60f
                setColor(Color.WHITE)
                alpha = 180
            }
            setPadding(40, 24, 40, 24)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) elevation = 4f
            setOnClickListener { dismissOverlay() }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Countdown Timer
        var secondsLeft = 5
        val countdownRunnable = object : Runnable {
            override fun run() {
                if (secondsLeft > 0) {
                    btnAccept.text = "Aceptar ($secondsLeft)"
                    secondsLeft--
                    handler.postDelayed(this, 1000)
                } else {
                    btnAccept.text = "Aceptar"
                    btnAccept.isEnabled = true
                    btnAccept.setTextColor(Color.parseColor(topColor))
                    btnAccept.background.alpha = 255
                }
            }
        }
        handler.post(countdownRunnable)

        cardView.addView(tvEmoji)
        cardView.addView(tvTitle)
        cardView.addView(divider)
        cardView.addView(tvMessage)
        cardView.addView(btnAccept)

        cardContainer.addView(cardView)
        scrollView.addView(cardContainer)

        rootFrame.addView(scrollView, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
            Gravity.CENTER
        ))

        overlayView = rootFrame
        windowManager?.addView(overlayView, params)

        // Animation
        val scaleAnim = ScaleAnimation(0.85f, 1f, 0.85f, 1f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f, ScaleAnimation.RELATIVE_TO_SELF, 0.5f)
        val alphaAnim = AlphaAnimation(0f, 1f)
        val animSet = AnimationSet(true).apply {
            addAnimation(scaleAnim)
            addAnimation(alphaAnim)
            duration = 300
        }
        cardView.startAnimation(animSet)
    }

    private fun dismissOverlay() {
        stopVibration()
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        overlayView = null
        stopSelf()
    }

    private fun startContinuousVibration() {
        isVibrating = true
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vm.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val pattern = longArrayOf(0, 600, 300, 600, 300, 600, 800)
            vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) 
        } else {
            @Suppress("DEPRECATION")
            val pattern = longArrayOf(0, 600, 300, 600, 300, 600, 800)
            vibrator?.vibrate(pattern, 0)
        }
    }

    private fun stopVibration() {
        isVibrating = false
        vibrator?.cancel()
    }

    private fun playAlertSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val ringtone = RingtoneManager.getRingtone(applicationContext, uri)
            ringtone?.play()
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        stopVibration()
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (_: Exception) {}
        overlayView = null
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
