package com.farel.waresponder

import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.content.pm.PackageManager
import android.graphics.Color
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val btnEnable     = findViewById<Button>(R.id.btnEnable)
        val btnFloat      = findViewById<Button>(R.id.btnFloat)
        val tvFloatStatus = findViewById<TextView>(R.id.tvFloatStatus)

        // ---- Notification Listener ----
        btnEnable.setOnClickListener {
            toggleNotificationListenerService()
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // ---- Floating Window ----
        btnFloat.setOnClickListener {
            if (!hasOverlayPermission()) {
                requestOverlayPermission()
            } else {
                toggleFloatingWindow(tvFloatStatus)
            }
        }

        updateFloatButton(tvFloatStatus)
    }

    override fun onResume() {
        super.onResume()
        updateFloatButton(findViewById(R.id.tvFloatStatus))
    }

    private fun updateFloatButton(tv: TextView) {
        val running = FloatingWindowService.isRunning
        tv.text = if (running) "● Floating ON" else "○ Floating OFF"
        tv.setTextColor(if (running) Color.parseColor("#25D366") else Color.parseColor("#AAAAAA"))
    }

    private fun toggleFloatingWindow(tv: TextView) {
        val intent = Intent(this, FloatingWindowService::class.java)
        if (FloatingWindowService.isRunning) {
            stopService(intent)
            Toast.makeText(this, "Floating window stopped", Toast.LENGTH_SHORT).show()
        } else {
            startForegroundService(intent)
            Toast.makeText(this, "Floating window started!", Toast.LENGTH_SHORT).show()
        }
        tv.postDelayed({ updateFloatButton(tv) }, 300)
    }

    private fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(this)

    private fun requestOverlayPermission() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("WhatsMiku needs 'Display over other apps' permission to show the floating window.")
            .setPositiveButton("Grant") { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                )
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toggleNotificationListenerService() {
        val cn = ComponentName(this, NotificationService::class.java)
        val pm = packageManager
        pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        pm.setComponentEnabledSetting(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
    }
}
