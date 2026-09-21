package com.example.callguardian.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.example.callguardian.R
import com.example.callguardian.data.BlockRepository

class VolumeKeyAccessibilityService : AccessibilityService() {

    private val TAG = "VolumeKeyService"
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val repo = BlockRepository.getInstance(applicationContext)

            val isRingingByTelephony = telephonyManager?.callState == TelephonyManager.CALL_STATE_RINGING
            val isRingingByRepo = repo.getCallState() == "RINGING"

            Log.d(TAG, "Volume Down pressed. isRingingByTelephony=$isRingingByTelephony, isRingingByRepo=$isRingingByRepo")

            if (isRingingByTelephony || isRingingByRepo) {
                val incomingNumber = repo.getLastIncomingNumber()
                Log.d(TAG, "Rejecting incoming call from: $incomingNumber")

                // 1. End current call
                val callEnded = CallManagerHelper.endCurrentCall(this)

                // 2. Add to blocked numbers list
                if (!incomingNumber.isNullOrBlank()) {
                    val added = repo.addBlockedNumber(
                        incomingNumber,
                        "Ovoz pasaytirish orqali bloklangan"
                    )
                    if (added) {
                        mainHandler.post {
                            Toast.makeText(
                                applicationContext,
                                applicationContext.getString(R.string.blocked_toast, incomingNumber),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                // Consume the key event so volume HUD doesn't pop up unnecessarily
                return true
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not used
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }
}
