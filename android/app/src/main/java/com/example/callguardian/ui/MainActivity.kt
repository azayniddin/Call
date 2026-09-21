package com.example.callguardian.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.callguardian.R
import com.example.callguardian.databinding.ActivityMainBinding
import com.example.callguardian.service.CallGuardianForegroundService
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val TAG = "MainActivity"
    private lateinit var binding: ActivityMainBinding
    private var testPlayer: MediaPlayer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val PREFS_NAME = "call_guardian_prefs"
    private val KEY_CUSTOM_MESSAGE = "custom_message"
    private val DEFAULT_MESSAGE =
        "Assalomu alaykum! Men Zayniddinning sun'iy intellekt yordamchisiman. Zayniddin hozir ishda, ishdan chiqib o'zlari sizga telefon qiladi. Xayr, salomat bo'ling!"

    private val requiredPermissions = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.READ_PHONE_NUMBERS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updateStatusIndicators()
            checkAndStartService()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        tts = TextToSpeech(this, this)

        loadSettings()
        setupListeners()
        updateStatusIndicators()

        // Missing permission bo'lsa darhol ruxsat so'rash
        val hasMissing = requiredPermissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (hasMissing) {
            requestPermissionsLauncher.launch(requiredPermissions.toTypedArray())
        } else {
            checkAndStartService()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusIndicators()
        checkAndStartService()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("uz"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale("ru"))
            }
            isTtsReady = true
        }
    }

    private fun checkAndStartService() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("ai_assistant_enabled", true)
        if (isEnabled) {
            try {
                val serviceIntent = Intent(this, CallGuardianForegroundService::class.java)
                ContextCompat.startForegroundService(this, serviceIntent)
                Log.d(TAG, "CallGuardianForegroundService started/ensured.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service: ${e.message}")
            }
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("ai_assistant_enabled", true)
        val selectedSim = prefs.getString("selected_sim", "both") ?: "both"
        val savedMessage = prefs.getString(KEY_CUSTOM_MESSAGE, DEFAULT_MESSAGE)

        binding.switchAiAssistant.isChecked = isEnabled
        updateSwitchSubtitle(isEnabled)
        binding.etCustomMessage.setText(savedMessage)

        when (selectedSim) {
            "sim1" -> binding.rbSim1.isChecked = true
            "sim2" -> binding.rbSim2.isChecked = true
            else -> binding.rbBothSim.isChecked = true
        }
    }

    private fun setupListeners() {
        // AI Assistant Switch
        binding.switchAiAssistant.setOnCheckedChangeListener { _, isChecked ->
            val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean("ai_assistant_enabled", isChecked).apply()
            updateSwitchSubtitle(isChecked)

            if (isChecked) {
                checkAndStartService()
                Toast.makeText(this, "🟢 AI Yordamchi YOQILDI! Qo'ng'iroqlarga o'zi javob beradi.", Toast.LENGTH_SHORT).show()
            } else {
                try {
                    stopService(Intent(this, CallGuardianForegroundService::class.java))
                } catch (ignored: Exception) {}
                Toast.makeText(this, "AI Yordamchi O'CHIRILDI.", Toast.LENGTH_SHORT).show()
            }
        }

        // SIM Selection
        binding.rgSimSelection.setOnCheckedChangeListener { _, checkedId ->
            val sim = when (checkedId) {
                R.id.rbSim1 -> "sim1"
                R.id.rbSim2 -> "sim2"
                else -> "both"
            }
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString("selected_sim", sim)
                .apply()
        }

        // Save Custom Message Button
        binding.btnSaveMessage.setOnClickListener {
            val text = binding.etCustomMessage.text.toString().trim()
            if (text.isNotBlank()) {
                getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(KEY_CUSTOM_MESSAGE, text)
                    .apply()

                val customFile = File(filesDir, "custom_ai_speech.wav")
                if (text == DEFAULT_MESSAGE) {
                    // Default bo'lsa maxsus faylni o'chiramiz, OpenAI audiosi yangraydi
                    if (customFile.exists()) customFile.delete()
                    Toast.makeText(this, "Asl OpenAI audiosi tanlandi! ✅", Toast.LENGTH_SHORT).show()
                } else if (isTtsReady && tts != null) {
                    // Foydalanuvchi yangi matn kiritgan bo'lsa TTS orqali sintez qilamiz
                    try {
                        val bundle = Bundle()
                        val utteranceId = "save_speech"
                        tts?.synthesizeToFile(text, bundle, customFile, utteranceId)
                        Toast.makeText(this, "Matn saqlandi va ovoz tayyorlandi! ✅", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(this, "Matn saqlandi! ✅", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(this, "Matn saqlandi! ✅", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Iltimos, matn kiriting", Toast.LENGTH_SHORT).show()
            }
        }

        // Test Speech Button
        binding.btnTestSpeech.setOnClickListener {
            if (testPlayer?.isPlaying == true) {
                testPlayer?.stop()
                testPlayer?.release()
                testPlayer = null
                binding.btnTestSpeech.text = "🔊 Ovozni telefonda eshitib ko'rish"
            } else {
                try {
                    testPlayer?.release()
                    val customFile = File(filesDir, "custom_ai_speech.wav")
                    val player = if (customFile.exists() && customFile.length() > 0) {
                        MediaPlayer().apply {
                            setDataSource(applicationContext, Uri.fromFile(customFile))
                            prepare()
                        }
                    } else {
                        MediaPlayer.create(this, R.raw.ai_speech)
                    }

                    testPlayer = player?.apply {
                        setOnCompletionListener {
                            binding.btnTestSpeech.text = "🔊 Ovozni telefonda eshitib ko'rish"
                        }
                        start()
                    }
                    binding.btnTestSpeech.text = "⏹️ Ovoz yangramoqda (To'xtatish)"
                } catch (e: Exception) {
                    Toast.makeText(this, "Audio xatosi: " + e.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Phone Permissions Button
        binding.btnPhonePermissions.setOnClickListener {
            requestPermissionsLauncher.launch(requiredPermissions.toTypedArray())
        }
    }

    private fun updateSwitchSubtitle(isEnabled: Boolean) {
        if (isEnabled) {
            binding.tvAiStatusSubtitle.text = "🟢 Faol: Qo'ng'iroqlarni o'zi ko'tarib javob beradi"
            binding.tvAiStatusSubtitle.setTextColor(ContextCompat.getColor(this, R.color.status_green))
            binding.tvServiceStatusTitle.text = "AI Avto-javob: 24/7 Faol"
            binding.tvServiceStatusSubtitle.text = "Qo'ng'iroqlarga o'zi javob beradi va xabaringizni eshittiradi"
            binding.layoutActiveServiceCard.backgroundTintList =
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#064E3B"))
        } else {
            binding.tvAiStatusSubtitle.text = "⚪ O'chirilgan"
            binding.tvAiStatusSubtitle.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
            binding.tvServiceStatusTitle.text = "AI Avto-javob: To'xtatilgan"
            binding.tvServiceStatusSubtitle.text = "Qo'ng'iroqlarga avtomatik javob berish o'chirilgan"
            binding.layoutActiveServiceCard.backgroundTintList =
                android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#334155"))
        }
    }

    private fun updateStatusIndicators() {
        val allPermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
        if (allPermissionsGranted) {
            binding.btnPhonePermissions.text = getString(R.string.btn_granted)
            binding.btnPhonePermissions.isEnabled = false
        } else {
            binding.btnPhonePermissions.text = getString(R.string.btn_grant)
            binding.btnPhonePermissions.isEnabled = true
        }
    }

    override fun onDestroy() {
        testPlayer?.release()
        testPlayer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }
}
