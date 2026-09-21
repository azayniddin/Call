package com.example.callguardian.service

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import java.util.Locale

/**
 * Professional AI Call Assistant Service for Android
 * 1. Detects incoming call on SIM 1 / SIM 2
 * 2. Automatically answers the call
 * 3. Speaks the assistant message using Android TextToSpeech
 * 4. Automatically disconnects when speech ends
 */
class AiCallAnswerService : InCallService(), TextToSpeech.OnInitListener {

    private val TAG = "AiCallAnswerService"
    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("uz")) // fallback or default
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.getDefault())
            }
            isTtsReady = true
            Log.d(TAG, "TTS initialized successfully")
        } else {
            Log.e(TAG, "TTS initialization failed")
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "New call detected. State: ${call.state}")

        val prefs = applicationContext.getSharedPreferences("call_guardian_prefs", Context.MODE_PRIVATE)
        val isAiEnabled = prefs.getBoolean("ai_assistant_enabled", true)

        if (!isAiEnabled) {
            Log.d(TAG, "AI Assistant is currently OFF.")
            return
        }

        // 1. Qo'ng'iroq kelganda 1.5 soniyadan keyin avtomatik ko'tarish
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

        // 2. Qo'ng'iroq ulangach, gapirish va o'chirish
        call.registerCallback(object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) {
                if (state == Call.STATE_ACTIVE) {
                    speakAndDisconnect(call)
                }
            }
        })
    }

    private fun speakAndDisconnect(call: Call) {
        val speechText = "Assalomu alaykum! Men Zayniddinning sun'iy intellekt yordamchisiman. Zayniddin hozir ishda, ishdan chiqib o'zlari sizga telefon qiladi. Xayr, salomat bo'ling!"

        mainHandler.postDelayed({
            try {
                val utteranceId = "CALL_SPEECH_ID"
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        Log.d(TAG, "Speech started on call.")
                    }

                    override fun onDone(utteranceId: String?) {
                        Log.d(TAG, "Speech finished. Disconnecting call now.")
                        mainHandler.postDelayed({
                            try {
                                call.disconnect()
                            } catch (e: Exception) {
                                Log.e(TAG, "Error disconnecting: ${e.message}")
                            }
                        }, 500)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        Log.e(TAG, "Speech error, disconnecting fallback")
                        try {
                            call.disconnect()
                        } catch (ignored: Exception) {}
                    }
                })

                val params = android.os.Bundle().apply {
                    putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, android.media.AudioManager.STREAM_VOICE_CALL)
                }

                tts?.speak(speechText, TextToSpeech.QUEUE_FLUSH, params, utteranceId)

                // Fallback: Agar 10 soniyada o'chmasa, majburiy o'chirish
                mainHandler.postDelayed({
                    try {
                        call.disconnect()
                    } catch (ignored: Exception) {}
                }, 9000)

            } catch (e: Exception) {
                Log.e(TAG, "Error speaking on call: ${e.message}", e)
                try {
                    call.disconnect()
                } catch (ignored: Exception) {}
            }
        }, 800)
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
