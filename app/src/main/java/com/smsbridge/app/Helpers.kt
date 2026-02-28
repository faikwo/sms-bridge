package com.smsbridge.app

import android.content.Context
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.util.Log
import android.os.Build

object ContactHelper {
    fun getContactName(context: Context, phone: String): String? {
        return try {
            val uri = android.net.Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                android.net.Uri.encode(phone)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
                } else null
            }
        } catch (e: Exception) {
            Log.e("ContactHelper", "Error looking up contact: ${e.message}")
            null
        }
    }
}

object SmsHelper {
    private const val TAG = "SmsHelper"

    fun sendSms(context: Context, phone: String, body: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(body)
            if (parts.size == 1) {
                smsManager.sendTextMessage(phone, null, body, null, null)
            } else {
                smsManager.sendMultipartTextMessage(phone, null, parts, null, null)
            }
            Log.d(TAG, "SMS sent to $phone")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send SMS to $phone: ${e.message}")
        }
    }
}
