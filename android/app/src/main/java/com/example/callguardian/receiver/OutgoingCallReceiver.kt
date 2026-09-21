package com.example.callguardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import com.example.callguardian.R
import com.example.callguardian.data.BlockRepository

class OutgoingCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val outgoingNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
        if (!outgoingNumber.isNullOrBlank()) {
            Log.d("OutgoingCallReceiver", "Outgoing call detected to: $outgoingNumber")
            val repo = BlockRepository.getInstance(context)
            if (repo.isBlocked(outgoingNumber)) {
                val removed = repo.removeBlockedNumber(outgoingNumber)
                if (removed) {
                    Log.d("OutgoingCallReceiver", "Auto-unblocked $outgoingNumber because user initiated outgoing call.")
                    Toast.makeText(
                        context,
                        context.getString(R.string.unblocked_toast, outgoingNumber),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}
