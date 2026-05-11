package com.farel.waresponder

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.view.WindowManager.LayoutParams.*
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout

class FloatingWindowService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var logTextView: TextView
    private lateinit var statusDot: View

    private var params: WindowManager.LayoutParams? = null
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    private var isMinimized = false
    private lateinit var expandedPanel: View
    private lateinit var bubbleView: View

    companion object {
        const val CHANNEL_ID = "floating_service"
        const val NOTIF_ID = 1
        var logCallback: ((String) -> Unit)? = null
        var isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        setupFloatingView()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        if (::floatingView.isInitialized) {
            try { windowManager.removeView(floatingView) } catch (_: Exception) {}
        }
    }

    // ===================== LAYOUT =====================

    private fun dp(value: Int): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics).toInt()

    private fun setupFloatingView() {
        val root = FrameLayout(this)

        // ---- BUBBLE (minimized) ----
        bubbleView = buildBubble()
        bubbleView.visibility = View.GONE

        // ---- EXPANDED PANEL ----
        expandedPanel = buildExpandedPanel()

        root.addView(bubbleView)
        root.addView(expandedPanel)
        floatingView = root

        params = WindowManager.LayoutParams(
            dp(300),
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                TYPE_APPLICATION_OVERLAY else @Suppress("DEPRECATION") TYPE_PHONE,
            FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 30
            y = 120
        }

        windowManager.addView(floatingView, params)
        setupDrag()
        setupLogCallback()
    }

    private fun buildBubble(): View {
        val bubble = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(dp(56), dp(56))
        }

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#1A1A2E"))
            setStroke(dp(2), Color.parseColor("#00D9FF"))
        }
        bubble.background = bg

        val icon = TextView(this).apply {
            text = "✦"
            textSize = 20f
            setTextColor(Color.parseColor("#00D9FF"))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
        }
        bubble.addView(icon)

        bubble.setOnClickListener { setMinimized(false) }
        return bubble
    }

    private fun buildExpandedPanel(): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val panelBg = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(16).toFloat()
            setColor(Color.parseColor("#1A1A2E"))
            setStroke(dp(1), Color.parseColor("#00D9FF"))
        }
        card.background = panelBg

        // ---- TITLE BAR ----
        val titleBar = buildTitleBar()
        card.addView(titleBar)

        // ---- DIVIDER ----
        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(Color.parseColor("#00D9FF"))
            alpha = 0.3f
        }
        card.addView(divider)

        // ---- STATUS ROW ----
        val statusRow = buildStatusRow()
        card.addView(statusRow)

        // ---- LOG AREA ----
        val logArea = buildLogArea()
        card.addView(logArea)

        return card
    }

    private fun buildTitleBar(): LinearLayout {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(10), dp(10), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // App icon dot
        val iconDot = TextView(this).apply {
            text = "✦"
            textSize = 16f
            setTextColor(Color.parseColor("#00D9FF"))
        }
        bar.addView(iconDot)

        // Title
        val title = TextView(this).apply {
            text = "  WhatsMiku"
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        bar.addView(title)

        // Minimize button
        val btnMin = TextView(this).apply {
            text = "─"
            textSize = 16f
            setTextColor(Color.parseColor("#AAAAAA"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        btnMin.setOnClickListener { setMinimized(true) }
        bar.addView(btnMin)

        // Close button
        val btnClose = TextView(this).apply {
            text = "✕"
            textSize = 14f
            setTextColor(Color.parseColor("#FF5555"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        btnClose.setOnClickListener { stopSelf() }
        bar.addView(btnClose)

        return bar
    }

    private fun buildStatusRow(): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(8), dp(14), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        statusDot = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), dp(8)).also {
                it.marginEnd = dp(8)
            }
            val dotBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#25D366"))
            }
            background = dotBg
        }
        row.addView(statusDot)

        val statusText = TextView(this).apply {
            text = "Active • Listening"
            textSize = 11f
            setTextColor(Color.parseColor("#25D366"))
        }
        row.addView(statusText)

        return row
    }

    private fun buildLogArea(): ScrollView {
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(160)
            )
            setPadding(dp(14), dp(6), dp(14), dp(14))
        }

        logTextView = TextView(this).apply {
            text = "⟨ waiting for messages... ⟩"
            textSize = 10.5f
            setTextColor(Color.parseColor("#88CCFF"))
            setTypeface(android.graphics.Typeface.MONOSPACE)
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        scroll.addView(logTextView)
        return scroll
    }

    // ===================== DRAG =====================

    private fun setupDrag() {
        floatingView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params!!.x
                    initialY = params!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params!!.x = initialX + (event.rawX - initialTouchX).toInt()
                    params!!.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(floatingView, params)
                    true
                }
                else -> false
            }
        }

        // Title bar drag (more precise area)
        expandedPanel.findViewWithTag<View>("titlebar")?.setOnTouchListener { _, event ->
            floatingView.onTouchEvent(event)
        }
    }

    // ===================== STATE =====================

    private fun setMinimized(minimize: Boolean) {
        isMinimized = minimize
        if (minimize) {
            expandedPanel.visibility = View.GONE
            bubbleView.visibility = View.VISIBLE
            params!!.width = dp(56)
        } else {
            bubbleView.visibility = View.GONE
            expandedPanel.visibility = View.VISIBLE
            params!!.width = dp(300)
        }
        windowManager.updateViewLayout(floatingView, params)
    }

    // ===================== LOG CALLBACK =====================

    private fun setupLogCallback() {
        logCallback = { message ->
            floatingView.post {
                val current = logTextView.text.toString()
                val lines = current.lines().takeLast(40)
                logTextView.text = (lines + message).joinToString("\n")
                    .replace("⟨ waiting for messages... ⟩\n", "")
            }
        }
    }

    // ===================== NOTIFICATION =====================

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WhatsMiku Floating",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps WhatsMiku running"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val stopIntent = PendingIntent.getService(
            this, 0,
            Intent(this, FloatingWindowService::class.java).apply { action = "STOP" },
            PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WhatsMiku")
            .setContentText("Floating window active")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .addAction(android.R.drawable.ic_delete, "Stop", stopIntent)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") stopSelf()
        return START_STICKY
    }
}
