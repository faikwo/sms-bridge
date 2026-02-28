package com.smsbridge.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val PERMISSIONS = arrayOf(
        Manifest.permission.RECEIVE_SMS,
        Manifest.permission.READ_SMS,
        Manifest.permission.SEND_SMS,
        Manifest.permission.READ_CONTACTS
    )
    private val PERMISSION_REQUEST = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Simple layout programmatically
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 80, 48, 48)
        }

        val title = TextView(this).apply {
            text = "SMS Bridge"
            textSize = 24f
            setPadding(0, 0, 0, 32)
        }

        val hostLabel = TextView(this).apply { text = "Server WebSocket URL:" }

        val prefs = getSharedPreferences("sms_bridge", Context.MODE_PRIVATE)
        val hostInput = EditText(this).apply {
            setText(prefs.getString("server_host", "ws://192.168.1.100:3000"))
            hint = "ws://192.168.1.100:3000"
            setPadding(0, 16, 0, 16)
        }

        val statusText = TextView(this).apply {
            text = "Service status: checking…"
            setPadding(0, 24, 0, 16)
        }

        val saveBtn = Button(this).apply {
            text = "Save & Start Service"
            setOnClickListener {
                val url = hostInput.text.toString().trim()
                if (url.isBlank()) {
                    Toast.makeText(this@MainActivity, "Enter a server URL", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                prefs.edit().putString("server_host", url).apply()

                // Restart service with new URL
                stopService(Intent(this@MainActivity, WebSocketService::class.java))
                startService(Intent(this@MainActivity, WebSocketService::class.java))

                statusText.text = "Service started. Connecting to $url"
                Toast.makeText(this@MainActivity, "Saved!", Toast.LENGTH_SHORT).show()
            }
        }

        val permBtn = Button(this).apply {
            text = "Grant Permissions"
            setOnClickListener { requestPermissions() }
        }

        val infoText = TextView(this).apply {
            text = "\nMake sure your phone and server are on the same WiFi network.\n\n" +
                   "The service runs in the background and automatically reconnects if the connection drops."
            textSize = 13f
        }

        layout.addView(title)
        layout.addView(hostLabel)
        layout.addView(hostInput)
        layout.addView(permBtn)
        layout.addView(saveBtn)
        layout.addView(statusText)
        layout.addView(infoText)

        setContentView(layout)

        // Request permissions on first launch
        if (!hasPermissions()) requestPermissions()
        else statusText.text = "All permissions granted. Service running."
    }

    private fun hasPermissions() = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, PERMISSIONS, PERMISSION_REQUEST)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                startService(Intent(this, WebSocketService::class.java))
                Toast.makeText(this, "Permissions granted! Service started.", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Some permissions denied — app may not work correctly.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
