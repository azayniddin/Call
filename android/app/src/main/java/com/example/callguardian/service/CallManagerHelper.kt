package com.example.callguardian.service

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import java.lang.reflect.Method

object CallManagerHelper {
    private const val TAG = "CallManagerHelper"

    @SuppressLint("MissingPermission")
    fun endCurrentCall(context: Context): Boolean {
        try {
            // Android 9.0 (API 28) and above
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                if (ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ANSWER_PHONE_CALLS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    val telecomManager =
                        context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    if (telecomManager != null) {
                        val ended = telecomManager.endCall()
                        Log.d(TAG, "Call ended via TelecomManager: $ended")
                        if (ended) return true
                    }
                }
            }

            // Fallback for older APIs or if TelecomManager did not succeed
            val telephonyService = context.getSystemService(Context.TELEPHONY_SERVICE)
            val telephonyClass = Class.forName(telephonyService.javaClass.name)
            val getITelephonyMethod: Method = telephonyClass.getDeclaredMethod("getITelephony")
            getITelephonyMethod.isAccessible = true
            val iTelephony = getITelephonyMethod.invoke(telephonyService)

            val iTelephonyClass = Class.forName(iTelephony.javaClass.name)
            val endCallMethod = iTelephonyClass.getDeclaredMethod("endCall")
            endCallMethod.isAccessible = true
            endCallMethod.invoke(iTelephony)
            Log.d(TAG, "Call ended via ITelephony reflection")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to end call: ${e.message}", e)
            return false
        }
    }
}
