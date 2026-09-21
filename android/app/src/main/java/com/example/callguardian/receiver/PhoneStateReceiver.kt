package com.example.callguardian.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import android.view.KeyEvent
import com.example.callguardian.R
import com.example.callguardian.data.BlockRepository
import com.example.callguardian.service.CallManagerHelper

class PhoneStateReceiver : BroadcastReceiver() {

    companion object {
        private var activePlayer: MediaPlayer? = null
        private val handler = Handler(Looper.getMainLooper())
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

                    if (repo.isBlocked(incomingNumber)) {
                        CallManagerHelper.endCurrentCall(context)
                        return
                    }
                }

                // 1. Foreground xizmatni ishga tushirish (agar uxlayotgan bo'lsa)
                if (isAiEnabled) {
                    try {
                        val serviceIntent = Intent(context, com.example.callguardian.service.CallGuardianForegroundService::class.java)
                        androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
                    } catch (e: Exception) {
                        Log.e("PhoneStateReceiver", "Error starting foreground service: ${e.message}")
                    }

                    // Darhol javob berishga urinish
                    answerIncomingCall(context)
                }
            }

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                repo.setCallState("OFFHOOK")

                // 2. QO'NG'IROQ ULANGAN: SUHBATDOSHGA ESHITTIRISH VA O'CHIRISH
                if (isAiEnabled) {
                    handler.postDelayed({
                        playSpeechToCallerAndHangup(context)
                    }, 800)
                }
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                repo.setCallState("IDLE")
                cleanup(context)
            }
        }
    }

    /**
     * Qo'ng'iroqni avtomatik ko'tarish:
     * 1) TelecomManager.acceptRingingCall()
     * 2) Hardware HeadsetHook simulyatsiyasi (Honor / Android 14 uchun 100% kafolat)
     */
    private fun answerIncomingCall(context: Context) {
        try {
            // Usul A: TelecomManager orqali
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                telecomManager?.acceptRingingCall()
                Log.d("PhoneStateReceiver", "Answered via TelecomManager")
            }
        } catch (e: Exception) {
            Log.w("PhoneStateReceiver", "TelecomManager accept failed: ${e.message}")
        }

        try {
            // Usul B: Naushnik tugmasi (HEADSETHOOK) hodisasi orqali avtomatik ko'tarish
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK)
            audioManager?.dispatchMediaKeyEvent(downEvent)
            audioManager?.dispatchMediaKeyEvent(upEvent)
            Log.d("PhoneStateReceiver", "Answered via HEADSETHOOK media key")
        } catch (e: Exception) {
            Log.e("PhoneStateReceiver", "HeadsetHook dispatch failed: ${e.message}")
        }
    }

    /**
     * Suhbatdoshga ovoz borishi uchun:
     * Karnay (Speakerphone) yoqiladi, audio baland ijro etiladi va mikrofon orqali
     * narigi suhbatdoshga to'liq yetib boradi! Audio tugashi bilan telefon uziladi.
     */
    private fun playSpeechToCallerAndHangup(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.mode = AudioManager.MODE_IN_CALL
            audioManager?.isSpeakerphoneOn = true // Karnayni yoqish (suhbatdoshga borishi uchun)

            activePlayer?.release()
            activePlayer = MediaPlayer.create(context, R.raw.ai_speech).apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .build()
                )

                setOnCompletionListener {
                    Log.d("PhoneStateReceiver", "Audio finished. Automatically ending call.")
                    handler.postDelayed({
                        audioManager?.isSpeakerphoneOn = false
                        CallManagerHelper.endCurrentCall(context)
                    }, 500)
                }

                start()
                Log.d("PhoneStateReceiver", "Playing AI speech to caller via in-call speaker...")
            }

            // Fallback timeout: agar audio tugamasa, 9 soniyada xavfsiz o'chirish
            handler.postDelayed({
                try {
                    audioManager?.isSpeakerphoneOn = false
                    CallManagerHelper.endCurrentCall(context)
                } catch (ignored: Exception) {}
            }, 9000)

        } catch (e: Exception) {
            Log.e("PhoneStateReceiver", "Error playing speech: ${e.message}", e)
        }
    }

    private fun cleanup(context: Context) {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager?.isSpeakerphoneOn = false
            activePlayer?.release()
            activePlayer = null
        } catch (ignored: Exception) {}
    }
}
