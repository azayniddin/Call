package com.example.callguardian.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.util.Log
import java.io.File

/**
 * Professional AI Call Assistant Service for Android
 * 1. Detects incoming call on SIM 1 or SIM 2
 * 2. Automatically answers the call
 * 3. Plays OpenAI-generated TTS audio to caller
 * 4. Automatically hangs up when speech finishes
 */
class AiCallAnswerService : InCallService() {

    private val TAG = "AiCallAnswerService"
    private var mediaPlayer: MediaPlayer? = null

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "New call detected. State: ${call.state}")

        val prefs = applicationContext.getSharedPreferences("call_guardian_prefs", Context.MODE_PRIVATE)
        val isAiEnabled = prefs.getBoolean("ai_assistant_enabled", true)
        val targetSim = prefs.getString("selected_sim", "both") ?: "both"

        if (!isAiEnabled) {
            Log.d(TAG, "AI Assistant is currently OFF.")
            return
        }

        // SIM Kartani tekshirish
        val callSubId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            call.details.accountHandle?.id?.toIntOrNull() ?: -1
        } else {
            -1
        }

        Log.d(TAG, "Incoming Call on SIM ID: $callSubId, Target setting: $targetSim")

        // 1. Qo'ng'iroqni avtomatik ko'tarish (Auto-Answer)
        if (call.state == Call.STATE_RINGING) {
            try {
                // Javob berish
                call.answer(0)
                Log.d(TAG, "Call auto-answered by AI Assistant!")

                // 2. Qo'ng'iroq ulangach, OpenAI audiosini ijro etish
                call.registerCallback(object : Call.Callback() {
                    override fun onStateChanged(call: Call, state: Int) {
                        if (state == Call.STATE_ACTIVE) {
                            playAssistantSpeechAndHangUp(call)
                        }
                    }
                })
            } catch (e: Exception) {
                Log.e(TAG, "Failed to auto-answer call: ${e.message}", e)
            }
        }
    }

    private fun playAssistantSpeechAndHangUp(call: Call) {
        try {
            val audioFile = File(applicationContext.filesDir, "ai_assistant_speech.mp3")

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .build()
                )

                if (audioFile.exists()) {
                    setDataSource(applicationContext, Uri.fromFile(audioFile))
                } else {
                    // Agar maxsus audio yuklanmagan bo'lsa
                    Log.w(TAG, "Speech audio file not found locally, waiting fallback timeout")
                }

                setOnCompletionListener {
                    Log.d(TAG, "AI Speech finished. Automatically disconnecting call.")
                    try {
                        call.disconnect()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error disconnecting: ${e.message}")
                    }
                }

                prepare()
                start()
                Log.d(TAG, "AI Speech playback started on active call stream.")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error playing in-call speech: ${e.message}", e)
            // Xatolik bo'lsa ham 5 soniyadan keyin xavfsiz o'chirish
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    call.disconnect()
                } catch (ignored: Exception) {}
            }, 6000)
        }
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }
}
