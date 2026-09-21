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

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val repo = BlockRepository.getInstance(applicationContext)

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

        // Agar ekranda qo'ng'iroq oynasi chiqsa, "Javob berish" tugmasini ekrandan bosish
        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {

            val now = System.currentTimeMillis()
            if (now - lastAutoAnswerTimestamp < 3000) return // Takroriy bosishdan himoya

            val rootNode = rootInActiveWindow ?: return
            try {
                findAndClickAnswerButton(rootNode)
            } catch (e: Exception) {
                Log.e(TAG, "Accessibility auto-click error: ${e.message}")
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

        // "Rad etish" / "Отклонить" emasligini tekshirish
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
            // Agar tugmaning o'zi emas ota elementi bosiladigan bo'lsa
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

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }
}
