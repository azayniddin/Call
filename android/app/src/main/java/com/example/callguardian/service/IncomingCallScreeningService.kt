package com.example.callguardian.service

import android.os.Build
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.callguardian.data.BlockRepository

@RequiresApi(Build.VERSION_CODES.Q)
class IncomingCallScreeningService : CallScreeningService() {

    private val TAG = "CallScreeningService"

    override fun onScreenCall(callDetails: Call.Details) {
        val handle = callDetails.handle
        val rawNumber = handle?.schemeSpecificPart ?: ""
        val repo = BlockRepository.getInstance(applicationContext)

        Log.d(TAG, "Screening incoming call: $rawNumber")

        if (rawNumber.isNotBlank()) {
            repo.saveLastIncomingNumber(rawNumber)
        }

        if (repo.isBlocked(rawNumber)) {
            Log.d(TAG, "Call from $rawNumber is in blocked list. Auto-rejecting silently!")

            val response = CallResponse.Builder()
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipCallLog(false)
                .setSkipNotification(true)
                .build()

            respondToCall(callDetails, response)
        } else {
            val response = CallResponse.Builder()
                .setDisallowCall(false)
                .setRejectCall(false)
                .build()

            respondToCall(callDetails, response)
        }
    }
}
