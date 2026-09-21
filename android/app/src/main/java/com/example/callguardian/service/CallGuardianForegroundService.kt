package com.example.callguardian.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.telecom.TelecomManager
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.KeyEvent
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.example.callguardian.R
import com.example.callguardian.ui.MainActivity
import java.io.File

class CallGuardianForegroundService : Service() {

    private val TAG = "CallGuardianService"
    private val CHANNEL_ID = "call_guardian_service_channel"
    private val NOTIFICATION_ID = 101

    private var telephonyManager: TelephonyManager? = null
    private var telephonyCallback: Any? = null
    private var legacyListener: PhoneStateListener? = null
    private var activePlayer: MediaPlayer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private val handler = Handler(Looper.getMainLooper())

    private var isIncomingRinging = false
    private var isCallConnected = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Foreground Service created")
        createNotificationChannel()

        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        registerCallListener()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Foreground Service onStartCommand")
        return START_STICKY
    }

    private fun registerCallListener() {
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            registerTelephonyCallbackApi31()
        } else {
            registerLegacyPhoneListener()
        }
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun registerTelephonyCallbackApi31() {
        val callback = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
            override fun onCallStateChanged(state: Int) {
                handleCallState(state)
            }
        }
        telephonyCallback = callback
        telephonyManager?.registerTelephonyCallback(mainExecutor, callback)
    }

    @Suppress("DEPRECATION")
    private fun registerLegacyPhoneListener() {
        legacyListener = object : PhoneStateListener() {
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                super.onCallStateChanged(state, phoneNumber)
                handleCallState(state)
            }
        }
        telephonyManager?.listen(legacyListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    private fun handleCallState(state: Int) {
        val prefs = getSharedPreferences("call_guardian_prefs", Context.MODE_PRIVATE)
        val isAiEnabled = prefs.getBoolean("ai_assistant_enabled", true)

        Log.d(TAG, "Call State Changed: $state, isAiEnabled: $isAiEnabled")

        when (state) {
            TelephonyManager.CALL_STATE_RINGING -> {
                if (isAiEnabled) {
                    isIncomingRinging = true
                    isCallConnected = false
                    Log.d(TAG, "Incoming Call ringing! Attempting auto-answer...")

                    // 1-urinish: 600ms dan so'ng
                    handler.postDelayed({
                        if (isIncomingRinging && !isCallConnected) {
                            autoAnswerCall()
                        }
                    }, 600)

                    // 2-urinish (retry): 1500ms dan so'ng
                    handler.postDelayed({
                        if (isIncomingRinging && !isCallConnected) {
                            autoAnswerCall()
                        }
                    }, 1500)
                }
            }
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                // Faqat kiruvchi qo'ng'iroq bo'lsa va AI yoqilgan bo'lsa ishlaydi
                if (isIncomingRinging && isAiEnabled && !isCallConnected) {
                    isCallConnected = true
                    Log.d(TAG, "Call connected (OFFHOOK)! Activating loudspeaker...")

                    // Karnayni darhol yoqish
                    setupLoudspeaker()

                    // Suhbatdosh bilan aloqa to'liq o'rnatilgach (1.2s) ovozni boshlash
                    handler.postDelayed({
                        if (isCallConnected) {
                            playSpeechToCaller()
                        }
                    }, 1200)
                }
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                Log.d(TAG, "Call ended (IDLE)")
                isIncomingRinging = false
                isCallConnected = false
                cleanupAudio()
            }
        }
    }

    private fun autoAnswerCall() {
        Log.d(TAG, "Executing autoAnswerCall...")

        // Usul 1: TelecomManager.acceptRingingCall()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val tm = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                tm?.acceptRingingCall()
                Log.d(TAG, "Accepted via TelecomManager.acceptRingingCall()")
            }
        } catch (e: Exception) {
            Log.w(TAG, "TelecomManager accept failed: ${e.message}")
        }

        // Usul 2: Hardware HEADSETHOOK (Garnitura tugmasi)
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK)
            am?.dispatchMediaKeyEvent(downEvent)
            am?.dispatchMediaKeyEvent(upEvent)
            Log.d(TAG, "Dispatched HEADSETHOOK key event to AudioManager")
        } catch (e: Exception) {
            Log.e(TAG, "HeadsetHook key event failed: ${e.message}")
        }

        // Usul 3: Media button broadcast intent
        try {
            val mediaIntentDown = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK))
                flags = Intent.FLAG_RECEIVER_FOREGROUND
            }
            sendOrderedBroadcast(mediaIntentDown, null)

            val mediaIntentUp = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
                putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK))
                flags = Intent.FLAG_RECEIVER_FOREGROUND
            }
            sendOrderedBroadcast(mediaIntentUp, null)
            Log.d(TAG, "Broadcasted ACTION_MEDIA_BUTTON")
        } catch (e: Exception) {
            Log.e(TAG, "Media button broadcast failed: ${e.message}")
        }
    }

    /**
     * Ovoz narigi odamga (suhbatdoshga) yetib borishi uchun:
     * 1) Android 12+ (Honor 400 Lite) da setCommunicationDevice orqali asosiy karnayni yoqish
     * 2) Mikrofonni ochiq (unmute) holatda ushlab turish
     * 3) Volume darajalarini maksimal 100% ga ko'tarish
     */
    private fun setupLoudspeaker() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return

            // 1. Android 12+ (API 31+) uchun maxsus setCommunicationDevice
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    am.mode = AudioManager.MODE_IN_COMMUNICATION
                    val devices = am.availableCommunicationDevices
                    val speaker = devices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                    if (speaker != null) {
                        val success = am.setCommunicationDevice(speaker)
                        Log.d(TAG, "setCommunicationDevice(BUILTIN_SPEAKER) success: $success")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "setCommunicationDevice error: ${e.message}")
                }
            }

            // 2. Standart isSpeakerphoneOn
            try {
                @Suppress("DEPRECATION")
                am.isSpeakerphoneOn = true
            } catch (ignored: Exception) {}

            // 3. Mikrofon mute bo'lmasligini ta'minlash
            try {
                am.isMicrophoneMute = false
            } catch (ignored: Exception) {}

            // 4. Ovoz balandligini eng yuqoriga ko'tarish
            val maxMusic = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            am.setStreamVolume(AudioManager.STREAM_MUSIC, maxMusic, 0)

            val maxVoice = am.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL)
            am.setStreamVolume(AudioManager.STREAM_VOICE_CALL, maxVoice, 0)

            Log.d(TAG, "Loudspeaker configured: musicVol=$maxMusic, voiceVol=$maxVoice")

        } catch (e: Exception) {
            Log.e(TAG, "Error configuring loudspeaker: ${e.message}", e)
        }
    }

    private fun playSpeechToCaller() {
        try {
            setupLoudspeaker()

            activePlayer?.release()
            loudnessEnhancer?.release()

            // Agar foydalanuvchi maxsus matn saqlagan bo'lsa
            val customFile = File(filesDir, "custom_ai_speech.wav")
            val player = if (customFile.exists() && customFile.length() > 0) {
                Log.d(TAG, "Using custom speech: ${customFile.absolutePath}")
                MediaPlayer().apply {
                    setDataSource(applicationContext, Uri.fromFile(customFile))
                    prepare()
                }
            } else {
                Log.d(TAG, "Using default OpenAI speech R.raw.ai_speech")
                MediaPlayer.create(applicationContext, R.raw.ai_speech)
            }

            activePlayer = player?.apply {
                // USAGE_MEDIA orqali asosiy pastki karnayda baland yangrashini ta'minlaymiz
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setLegacyStreamType(AudioManager.STREAM_MUSIC)
                        .build()
                )

                setVolume(1.0f, 1.0f)

                // LoudnessEnhancer orqali ovozni +15dB kuchaytirib beramiz
                try {
                    loudnessEnhancer = LoudnessEnhancer(audioSessionId).apply {
                        setTargetGain(1500)
                        enabled = true
                    }
                    Log.d(TAG, "LoudnessEnhancer (+15dB) enabled")
                } catch (e: Exception) {
                    Log.w(TAG, "LoudnessEnhancer: ${e.message}")
                }

                setOnCompletionListener {
                    Log.d(TAG, "Speech finished! Hanging up call in 800ms...")
                    handler.postDelayed({
                        hangupCall()
                    }, 800)
                }

                start()
                Log.d(TAG, "In-call loudspeaker audio playback started successfully!")
            }

            // Xavfsizlik uchun: 12 soniyadan so'ng majburiy o'chirish
            handler.postDelayed({
                if (isCallConnected) {
                    hangupCall()
                }
            }, 12000)

        } catch (e: Exception) {
            Log.e(TAG, "Error playing audio on call: ${e.message}", e)
        }
    }

    private fun hangupCall() {
        try {
            cleanupAudio()
            CallManagerHelper.endCurrentCall(applicationContext)
            Log.d(TAG, "Call disconnected via CallManagerHelper")
        } catch (e: Exception) {
            Log.e(TAG, "Error hanging up call: ${e.message}")
        } finally {
            isCallConnected = false
            isIncomingRinging = false
        }
    }

    private fun cleanupAudio() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    am?.clearCommunicationDevice()
                } catch (ignored: Exception) {}
            }
            @Suppress("DEPRECATION")
            am?.isSpeakerphoneOn = false
            am?.mode = AudioManager.MODE_NORMAL

            loudnessEnhancer?.release()
            loudnessEnhancer = null

            activePlayer?.release()
            activePlayer = null
        } catch (ignored: Exception) {}
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Zayniddin AI Assistant")
            .setContentText("🟢 Faol: Qo'ng'iroqlarga avtomatik javob beriladi")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AI Call Assistant Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Avtomatik qo'ng'iroqlarga javob berish doimiy xizmati"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        Log.d(TAG, "Foreground Service destroyed")
        cleanupAudio()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && telephonyCallback != null) {
            telephonyManager?.unregisterTelephonyCallback(telephonyCallback as TelephonyCallback)
        } else if (legacyListener != null) {
            telephonyManager?.listen(legacyListener, PhoneStateListener.LISTEN_NONE)
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
