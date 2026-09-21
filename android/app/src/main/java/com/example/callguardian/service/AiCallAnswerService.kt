package com.example.callguardian.service

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import com.example.callguardian.R

class AiCallAnswerService : InCallService() {

    private val TAG = "AiCallAnswerService"
    private var mediaPlayer: MediaPlayer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "New call detected. State: ${call.state}")

        val prefs = applicationContext.getSharedPreferences("call_guardian_prefs", Context.MODE_PRIVATE)
        val isAiEnabled = prefs.getBoolean("ai_assistant_enabled", true)

        if (!isAiEnabled) {
            Log.d(TAG, "AI Assistant is currently OFF.")
            return
        }

        // 1. Qo'ng'iroq kelganda 1.5 soniyada avtomatik javob berish
        if (call.state == Call.STATE_RINGING) {
            mainHandler.postDelayed({
                try {
                    call.answer(0)
                    Log.d(TAG, "Call auto-answered by Zayniddin AI Assistant!")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to auto-answer call: ${e.message}", e)
                }
            }, 1500)
        }

        // 2. Qo'ng'iroq ulangach, OpenAI audiosini suhbatdoshga eshittirish
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state == Call.STATE_ACTIVE) {
                    playAudioAndDisconnect(call)
                }
            }
        })
    }

    private fun playAudioAndDisconnect(call: Call) {
        mainHandler.postDelayed({
            try {
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer.create(applicationContext, R.raw.ai_speech).apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .build()
                    )

                    setOnCompletionListener {
                        Log.d(TAG, "OpenAI audio finished. Disconnecting call now.")
                        mainHandler.postDelayed({
                            try {
                                call.disconnect()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error disconnecting: ${e.message}")
                            }
                        }, 500)
                    }

                    start()
                    Log.d(TAG, "OpenAI in-call audio playback started successfully!")
                }

                // Fallback timeout agar audio to'xtab qolsa
                mainHandler.postDelayed({
                    try {
                        call.disconnect()
                    } catch (ignored: Exception) {}
                }, 10000)

            } catch (e: Exception) {
                Log.e(TAG, "Error playing in-call audio: ${e.message}", e)
                try {
                    call.disconnect()
                } catch (ignored: Exception) {}
            }
        }, 600)
    }

    override fun onDestroy() {
        mediaPlayer?.release()
        mediaPlayer = null
        super.onDestroy()
    }
}
