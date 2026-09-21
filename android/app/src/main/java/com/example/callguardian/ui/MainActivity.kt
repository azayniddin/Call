package com.example.callguardian.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.callguardian.R
import com.example.callguardian.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var testPlayer: MediaPlayer? = null
    private val PREFS_NAME = "call_guardian_prefs"

    private val requiredPermissions = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.READ_PHONE_NUMBERS)
        }
    }

    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            updateStatusIndicators()
        }

    private val requestRoleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            updateStatusIndicators()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        loadSettings()
        setupListeners()
        updateStatusIndicators()
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("ai_assistant_enabled", true)
        val selectedSim = prefs.getString("selected_sim", "both") ?: "both"

        binding.switchAiAssistant.isChecked = isEnabled
        updateSwitchSubtitle(isEnabled)

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
            val msg = if (isChecked) "AI Yordamchi YOQILDI! Qo'ng'iroqlarga o'zi javob beradi." else "AI Yordamchi O'CHIRILDI."
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
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

        // Default Dialer Button
        binding.btnSetDefaultDialer.setOnClickListener {
            requestDefaultDialer()
        }

        // Test Speech using real OpenAI MP3
        binding.btnTestSpeech.setOnClickListener {
            if (testPlayer?.isPlaying == true) {
                testPlayer?.stop()
                testPlayer?.release()
                testPlayer = null
                binding.btnTestSpeech.text = "🔊 Ovozni telefonda eshitib ko'rish"
            } else {
                try {
                    testPlayer?.release()
                    testPlayer = MediaPlayer.create(this, R.raw.ai_speech)
                    testPlayer?.setOnCompletionListener {
                        binding.btnTestSpeech.text = "🔊 Ovozni telefonda eshitib ko'rish"
                    }
                    testPlayer?.start()
                    binding.btnTestSpeech.text = "⏹️ Ovoz yangramoqda (To'xtatish)"
                } catch (e: Exception) {
                    Toast.makeText(this, "Audio xatosi: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Call Screening Button
        binding.btnCallScreening.setOnClickListener {
            requestCallScreeningRole()
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
        } else {
            binding.tvAiStatusSubtitle.text = "⚪ O'chirilgan"
            binding.tvAiStatusSubtitle.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        }
    }

    private fun requestDefaultDialer() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                startActivity(intent)
                return
            }
        }
        val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER).apply {
            putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
        }
        startActivity(intent)
    }

    private fun isDefaultDialer(): Boolean {
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
        return telecomManager?.defaultDialerPackage == packageName
    }

    private fun updateStatusIndicators() {
        // 0. Default Dialer status
        val isDefault = isDefaultDialer()
        if (isDefault) {
            binding.btnSetDefaultDialer.text = "FAOL"
            binding.btnSetDefaultDialer.isEnabled = false
            binding.btnSetDefaultDialer.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#10B981"))
            binding.tvDefaultDialerDesc.text = "✅ Qo'ng'iroqlarni avtomatik ko'tarishga to'liq ruxsat berilgan"
        } else {
            binding.btnSetDefaultDialer.text = "Tanlash"
            binding.btnSetDefaultDialer.isEnabled = true
            binding.tvDefaultDialerDesc.text = "Telefon avtomatik ko'tarishi uchun Standart ilova qilib tanlang"
        }

        // 1. Call Screening status
        val isScreeningActive = isCallScreeningApp()
        if (isScreeningActive) {
            binding.btnCallScreening.text = getString(R.string.btn_enabled)
            binding.btnCallScreening.isEnabled = false
        } else {
            binding.btnCallScreening.text = getString(R.string.btn_enable)
            binding.btnCallScreening.isEnabled = true
        }

        // 2. Permissions status
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

    private fun requestCallScreeningRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
                    val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)
                    requestRoleLauncher.launch(intent)
                    return
                }
            }
        }
        Toast.makeText(this, "Qo'ng'iroq filtri allaqachon faollashtirilgan", Toast.LENGTH_SHORT).show()
    }

    private fun isCallScreeningApp(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as? RoleManager
            return roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        }
        return true
    }

    override fun onDestroy() {
        testPlayer?.release()
        testPlayer = null
        super.onDestroy()
    }
}
