package com.example.callguardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.example.callguardian.service.CallGuardianForegroundService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            val prefs = context.getSharedPreferences("call_guardian_prefs", Context.MODE_PRIVATE)
            val isEnabled = prefs.getBoolean("ai_assistant_enabled", true)
            if (isEnabled) {
                val serviceIntent = Intent(context, CallGuardianForegroundService::class.java)
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
