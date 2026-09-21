package com.example.callguardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import com.example.callguardian.R
import com.example.callguardian.data.BlockRepository
import com.example.callguardian.service.CallManagerHelper

class PhoneStateReceiver : BroadcastReceiver() {

    companion object {
        private var activePlayer: MediaPlayer? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val stateStr = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
        val repo = BlockRepository.getInstance(context)
        val prefs = context.getSharedPreferences("call_guardian_prefs", Context.MODE_PRIVATE)
        val isAiEnabled = prefs.getBoolean("ai_assistant_enabled", true)

        Log.d("PhoneStateReceiver", "State: $stateStr, Number: $incomingNumber, isAiEnabled: $isAiEnabled")

        when (stateStr) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                repo.setCallState("RINGING")
                if (!incomingNumber.isNullOrBlank()) {
                    repo.saveLastIncomingNumber(incomingNumber)

                    // Agar bloklangan bo'lsa darhol uzish
                    if (repo.isBlocked(incomingNumber)) {
                        Log.d("PhoneStateReceiver", "Blocked number: $incomingNumber. Ending call.")
                        CallManagerHelper.endCurrentCall(context)
                        return
                    }
                }

                // Agar AI Yordamchi yoqilgan bo'lsa -> 1.5 soniyadan keyin avtomatik ko'tarish!
                if (isAiEnabled) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                                telecomManager?.acceptRingingCall()
                                Log.d("PhoneStateReceiver", "Call auto-answered via TelecomManager!")
                            }
                        } catch (e: Exception) {
                            Log.e("PhoneStateReceiver", "Failed to accept call: ${e.message}")
                        }
                    }, 1500)
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                repo.setCallState("OFFHOOK")
                // Agar AI Yordamchi yoqilgan bo'lsa va qo'ng'iroq endi ulanganda -> OpenAI audiosini ijro etish
                if (isAiEnabled) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        try {
                            activePlayer?.release()
                            activePlayer = MediaPlayer.create(context, R.raw.ai_speech).apply {
                                setAudioAttributes(
                                    AudioAttributes.Builder()
                                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                        .build()
                                )
                                setOnCompletionListener {
                                    Log.d("PhoneStateReceiver", "Speech finished, ending call.")
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        CallManagerHelper.endCurrentCall(context)
                                    }, 500)
                                }
                                start()
                                Log.d("PhoneStateReceiver", "Audio playback started.")
                            }
                        } catch (e: Exception) {
                            Log.e("PhoneStateReceiver", "Error playing audio: ${e.message}")
                        }
                    }, 800)
                }
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                repo.setCallState("IDLE")
                activePlayer?.release()
                activePlayer = null
            }
        }
    }
}
