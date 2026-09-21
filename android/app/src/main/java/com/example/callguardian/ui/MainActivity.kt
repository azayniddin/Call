package com.example.callguardian.ui

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.telecom.TelecomManager
import android.text.InputType
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.callguardian.R
import com.example.callguardian.data.BlockRepository
import com.example.callguardian.databinding.ActivityMainBinding
import com.example.callguardian.service.VolumeKeyAccessibilityService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var repo: BlockRepository
    private lateinit var adapter: BlockedNumbersAdapter

    private val requiredPermissions = buildList {
        add(Manifest.permission.READ_PHONE_STATE)
        add(Manifest.permission.READ_CALL_LOG)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Manifest.permission.ANSWER_PHONE_CALLS)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            @Suppress("DEPRECATION")
            add(Manifest.permission.PROCESS_OUTGOING_CALLS)
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

        repo = BlockRepository.getInstance(this)
        setupRecyclerView()
        setupListeners()
        loadBlockedNumbers()

        repo.registerListener {
            runOnUiThread {
                loadBlockedNumbers()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusIndicators()
        loadBlockedNumbers()
    }

    private fun setupRecyclerView() {
        adapter = BlockedNumbersAdapter(
            onUnblockClick = { item ->
                showUnblockConfirmDialog(item.phoneNumber)
            }
        )
        binding.rvBlockedNumbers.layoutManager = LinearLayoutManager(this)
        binding.rvBlockedNumbers.adapter = adapter
    }

    private fun setupListeners() {
        binding.btnAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        binding.btnCallScreening.setOnClickListener {
            requestCallScreeningRole()
        }

        binding.btnPhonePermissions.setOnClickListener {
            requestPermissionsLauncher.launch(requiredPermissions.toTypedArray())
        }

        binding.fabAdd.setOnClickListener {
            showAddManualDialog()
        }
    }

    private fun updateStatusIndicators() {
        // 1. Accessibility Service status
        val isAccessibilityOn = isAccessibilityServiceEnabled(this, VolumeKeyAccessibilityService::class.java)
        if (isAccessibilityOn) {
            binding.btnAccessibility.text = getString(R.string.btn_enabled)
            binding.btnAccessibility.isEnabled = false
        } else {
            binding.btnAccessibility.text = getString(R.string.btn_enable)
            binding.btnAccessibility.isEnabled = true
        }

        // 2. Call Screening status
        val isScreeningActive = isCallScreeningApp()
        if (isScreeningActive) {
            binding.btnCallScreening.text = getString(R.string.btn_enabled)
            binding.btnCallScreening.isEnabled = false
        } else {
            binding.btnCallScreening.text = getString(R.string.btn_enable)
            binding.btnCallScreening.isEnabled = true
        }

        // 3. Permissions status
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

    private fun loadBlockedNumbers() {
        val list = repo.getAllBlocked()
        adapter.submitList(list)
        binding.tvBlockedCount.text = "(${list.size})"

        if (list.isEmpty()) {
            binding.tvEmptyState.visibility = android.view.View.VISIBLE
            binding.rvBlockedNumbers.visibility = android.view.View.GONE
        } else {
            binding.tvEmptyState.visibility = android.view.View.GONE
            binding.rvBlockedNumbers.visibility = android.view.View.VISIBLE
        }
    }

    private fun showAddManualDialog() {
        val input = EditText(this).apply {
            hint = getString(R.string.dialog_add_hint)
            inputType = InputType.TYPE_CLASS_PHONE
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dialog_add_title))
            .setView(input)
            .setPositiveButton(getString(R.string.dialog_btn_add)) { _, _ ->
                val number = input.text.toString().trim()
                if (number.isNotBlank()) {
                    val added = repo.addBlockedNumber(number, "Qo'lda kiritilgan")
                    if (added) {
                        Toast.makeText(this, getString(R.string.blocked_toast, number), Toast.LENGTH_SHORT).show()
                        loadBlockedNumbers()
                    } else {
                        Toast.makeText(this, "Bu raqam allaqachon ro'yxatda mavjud", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(getString(R.string.dialog_btn_cancel), null)
            .show()
    }

    private fun showUnblockConfirmDialog(phoneNumber: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.btn_unblock))
            .setMessage("$phoneNumber raqamini blokdan chiqarmoqchimisiz?")
            .setPositiveButton("Ha") { _, _ ->
                repo.removeBlockedNumber(phoneNumber)
                Toast.makeText(this, getString(R.string.unblocked_toast, phoneNumber), Toast.LENGTH_SHORT).show()
                loadBlockedNumbers()
            }
            .setNegativeButton("Yo'q", null)
            .show()
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

    private fun isAccessibilityServiceEnabled(context: Context, service: Class<*>): Boolean {
        val expectedComponentName = "${context.packageName}/${service.name}"
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        return enabledServices.split(":").any { it.equals(expectedComponentName, ignoreCase = true) }
    }
}
