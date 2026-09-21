package com.example.callguardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import com.example.callguardian.data.BlockRepository
import com.example.callguardian.service.CallManagerHelper

class PhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        val repo = BlockRepository.getInstance(context)

        Log.d("PhoneStateReceiver", "State: $stateStr, Number: $incomingNumber")

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                repo.setCallState("RINGING")
                if (!incomingNumber.isNullOrBlank()) {
                    repo.saveLastIncomingNumber(incomingNumber)

                    // If already blocked in repository, automatically reject/end call immediately!
                    if (repo.isBlocked(incomingNumber)) {
                        Log.d("PhoneStateReceiver", "Incoming number is blocked: $incomingNumber. Ending call.")
                        CallManagerHelper.endCurrentCall(context)
                    }
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                repo.setCallState("OFFHOOK")
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                repo.setCallState("IDLE")
            }
        }
    }
}
