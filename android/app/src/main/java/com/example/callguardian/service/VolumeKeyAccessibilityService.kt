package com.example.callguardian.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.telephony.TelephonyManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import com.example.callguardian.R
import com.example.callguardian.data.BlockRepository

class VolumeKeyAccessibilityService : AccessibilityService() {

    private val TAG = "VolumeKeyService"
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastAutoAnswerTimestamp = 0L
    private var lastSpeakerClickTimestamp = 0L

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val repo = BlockRepository.getInstance(applicationContext)

            @Suppress("DEPRECATION")
            val isRingingByTelephony = telephonyManager?.callState == TelephonyManager.CALL_STATE_RINGING
            val isRingingByRepo = repo.getCallState() == "RINGING"

            Log.d(TAG, "Volume Down pressed. isRingingByTelephony=$isRingingByTelephony, isRingingByRepo=$isRingingByRepo")

            if (isRingingByTelephony || isRingingByRepo) {
                val incomingNumber = repo.getLastIncomingNumber()
                Log.d(TAG, "Rejecting incoming call from: $incomingNumber")

                // 1. End current call
                CallManagerHelper.endCurrentCall(this)

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

                return true
            }
        }
        return super.onKeyEvent(event)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val prefs = applicationContext.getSharedPreferences("call_guardian_prefs", Context.MODE_PRIVATE)
        val isAiEnabled = prefs.getBoolean("ai_assistant_enabled", true)
        if (!isAiEnabled) return

        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            val rootNode = rootInActiveWindow ?: return
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            @Suppress("DEPRECATION")
            val callState = tm?.callState ?: TelephonyManager.CALL_STATE_IDLE

            val now = System.currentTimeMillis()

            // 1. Agar telefon jiringlayotgan bo'lsa -> "Javob berish" tugmasini bosish
            if (callState == TelephonyManager.CALL_STATE_RINGING) {
                if (now - lastAutoAnswerTimestamp >= 3000) {
                    try {
                        findAndClickAnswerButton(rootNode)
                    } catch (e: Exception) {
                        Log.e(TAG, "Accessibility auto-click answer error: ${e.message}")
                    }
                }
            }

            // 2. Agar telefon ko'tarilgan bo'lsa (OFFHOOK) -> "Karnay/Динамик/Speaker" tugmasini bosish
            if (callState == TelephonyManager.CALL_STATE_OFFHOOK) {
                if (now - lastSpeakerClickTimestamp >= 5000) {
                    try {
                        findAndClickSpeakerButton(rootNode)
                    } catch (e: Exception) {
                        Log.e(TAG, "Accessibility auto-click speaker error: ${e.message}")
                    }
                }
            }
        }
    }

    private fun findAndClickAnswerButton(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        val answerKeywords = listOf(
            "javob", "ko'tarish", "ulash", "ответить", "принять",
            "answer", "accept", "incoming_call_answer", "call_answer"
        )

        val matchesKeyword = answerKeywords.any { keyword ->
            text.contains(keyword) || desc.contains(keyword) || viewId.contains(keyword)
        }

        val isReject = text.contains("rad") || text.contains("отклон") || text.contains("decline") || text.contains("reject")

        if (matchesKeyword && !isReject) {
            Log.d(TAG, "Found answer target: text='$text', desc='$desc', id='$viewId'")
            if (node.isClickable) {
                val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    lastAutoAnswerTimestamp = System.currentTimeMillis()
                    Log.d(TAG, "Answer button clicked successfully via Accessibility!")
                    return true
                }
            }
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable) {
                    val parentClicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (parentClicked) {
                        lastAutoAnswerTimestamp = System.currentTimeMillis()
                        Log.d(TAG, "Parent answer container clicked successfully!")
                        return true
                    }
                }
                parent = parent.parent
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (findAndClickAnswerButton(child)) {
                return true
            }
        }

        return false
    }

    private fun findAndClickSpeakerButton(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false

        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val viewId = node.viewIdResourceName?.lowercase() ?: ""

        val speakerKeywords = listOf(
            "громкая связь", "динамик", "спикер", "karnay", "spiker", "speaker", "loudspeaker"
        )

        val matches = speakerKeywords.any { keyword ->
            text.contains(keyword) || desc.contains(keyword) || viewId.contains(keyword)
        }

        // Agar bu karnay tugmasi bo'lsa va u hali yoqilmagan bo'lsa (isChecked = false yoki isSelected = false)
        if (matches && !node.isChecked && !node.isSelected) {
            Log.d(TAG, "Found in-call speaker target: text='$text', desc='$desc', id='$viewId'")
            if (node.isClickable) {
                val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (clicked) {
                    lastSpeakerClickTimestamp = System.currentTimeMillis()
                    Log.d(TAG, "Speaker button clicked via Accessibility!")
                    return true
                }
            }
            var parent = node.parent
            while (parent != null) {
                if (parent.isClickable && !parent.isChecked && !parent.isSelected) {
                    val pClicked = parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    if (pClicked) {
                        lastSpeakerClickTimestamp = System.currentTimeMillis()
                        Log.d(TAG, "Parent speaker button clicked via Accessibility!")
                        return true
                    }
                }
                parent = parent.parent
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (findAndClickSpeakerButton(child)) {
                return true
            }
        }

        return false
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }
}
