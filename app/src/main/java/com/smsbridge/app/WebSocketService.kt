package com.smsbridge.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class WebSocketService : Service() {

    companion object {
        private const val TAG = "WebSocketService"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "sms_bridge_channel"
        private const val RECONNECT_DELAY_MS = 5000L

        const val ACTION_SEND_SMS = "com.smsbridge.app.SEND_SMS"
        const val EXTRA_PHONE = "phone"
        const val EXTRA_NAME = "name"
        const val EXTRA_BODY = "body"
        const val EXTRA_TIMESTAMP = "timestamp"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS) // No timeout for WebSocket
        .build()

    private var webSocket: WebSocket? = null
    private var isConnected = false
    private val messageQueue = mutableListOf<JSONObject>()

    // ── Lifecycle ──────────────────────────────────────────────────────────────
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Connecting…"))
        connect()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SEND_SMS) {
            val phone = intent.getStringExtra(EXTRA_PHONE) ?: return START_STICKY
            val name = intent.getStringExtra(EXTRA_NAME)
            val body = intent.getStringExtra(EXTRA_BODY) ?: return START_STICKY
            val timestamp = intent.getLongExtra(EXTRA_TIMESTAMP, System.currentTimeMillis())

            val json = JSONObject().apply {
                put("type", "sms_incoming")
                put("phone", phone)
                put("name", name ?: JSONObject.NULL)
                put("body", body)
                put("timestamp", timestamp)
            }
            sendOrQueue(json)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        webSocket?.close(1000, "Service stopped")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── WebSocket Connection ───────────────────────────────────────────────────
    private fun connect() {
        val prefs = getSharedPreferences("sms_bridge", Context.MODE_PRIVATE)
        val host = prefs.getString("server_host", "ws://192.168.1.100:3000") ?: return

        Log.d(TAG, "Connecting to $host")
        val request = Request.Builder().url(host).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected")
                isConnected = true
                updateNotification("Connected")

                // Register as phone
                webSocket.send(JSONObject().apply { put("type", "register_phone") }.toString())

                // Flush queued messages
                synchronized(messageQueue) {
                    for (msg in messageQueue) webSocket.send(msg.toString())
                    messageQueue.clear()
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                handleServerMessage(bytes.utf8())
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
                onDisconnected()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}")
                onDisconnected()
            }
        })
    }

    private fun onDisconnected() {
        isConnected = false
        updateNotification("Disconnected — retrying…")
        scope.launch {
            delay(RECONNECT_DELAY_MS)
            connect()
        }
    }

    private fun handleServerMessage(text: String) {
        try {
            val json = JSONObject(text)
            when (json.getString("type")) {
                "send_sms" -> {
                    // Server is asking us to send an SMS
                    val phone = json.getString("phone")
                    val body = json.getString("body")
                    SmsHelper.sendSms(this, phone, body)
                    // Ack
                    webSocket?.send(JSONObject().apply {
                        put("type", "sms_sent_ack")
                        put("phone", phone)
                    }.toString())
                }
                "registered" -> Log.d(TAG, "Registered with server")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing server message: ${e.message}")
        }
    }

    private fun sendOrQueue(json: JSONObject) {
        val ws = webSocket
        if (isConnected && ws != null) {
            ws.send(json.toString())
        } else {
            synchronized(messageQueue) { messageQueue.add(json) }
        }
    }

    // ── Notification ──────────────────────────────────────────────────────────
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "SMS Bridge", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "SMS Bridge background service" }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMS Bridge")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(status: String) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(status))
    }
}
