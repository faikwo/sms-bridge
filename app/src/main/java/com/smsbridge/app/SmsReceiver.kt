package com.smsbridge.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log

class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        // Group multi-part messages by sender
        val grouped = mutableMapOf<String, StringBuilder>()
        for (sms in messages) {
            val phone = sms.originatingAddress ?: continue
            grouped.getOrPut(phone) { StringBuilder() }.append(sms.messageBody)
        }

        for ((phone, bodyBuilder) in grouped) {
            val body = bodyBuilder.toString()
            val name = ContactHelper.getContactName(context, phone)
            val timestamp = System.currentTimeMillis()

            Log.d(TAG, "SMS from $phone ($name): $body")

            // Forward to WebSocket service
            val serviceIntent = Intent(context, WebSocketService::class.java).apply {
                action = WebSocketService.ACTION_SEND_SMS
                putExtra(WebSocketService.EXTRA_PHONE, phone)
                putExtra(WebSocketService.EXTRA_NAME, name)
                putExtra(WebSocketService.EXTRA_BODY, body)
                putExtra(WebSocketService.EXTRA_TIMESTAMP, timestamp)
            }
            context.startService(serviceIntent)
        }
    }
}
