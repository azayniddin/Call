package com.example.callguardian.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

class BlockRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "call_guardian_prefs"
        private const val KEY_BLOCKED_NUMBERS = "blocked_numbers"
        private const val KEY_LAST_INCOMING = "last_incoming_number"
        private const val KEY_CALL_STATE = "current_call_state"

        @Volatile
        private var instance: BlockRepository? = null

        fun getInstance(context: Context): BlockRepository {
            return instance ?: synchronized(this) {
                instance ?: BlockRepository(context).also { instance = it }
            }
        }

        fun normalizeNumber(raw: String?): String {
            if (raw.isNullOrBlank()) return ""
            return raw.replace(Regex("[^0-9+]"), "")
        }

        fun numbersMatch(num1: String?, num2: String?): Boolean {
            val digits1 = (num1 ?: "").filter { it.isDigit() }
            val digits2 = (num2 ?: "").filter { it.isDigit() }
            if (digits1.isEmpty() || digits2.isEmpty()) return false
            if (digits1 == digits2) return true
            // Compare last 9 digits (common for Uzbekistan and many mobile carriers)
            val minLen = minOf(digits1.length, digits2.length, 9)
            if (minLen >= 7) {
                val sub1 = digits1.takeLast(minLen)
                val sub2 = digits2.takeLast(minLen)
                return sub1 == sub2
            }
            return false
        }
    }

    private val listeners = mutableListOf<() -> Unit>()

    fun registerListener(listener: () -> Unit) {
        synchronized(listeners) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: () -> Unit) {
        synchronized(listeners) {
            listeners.remove(listener)
        }
    }

    private fun notifyListeners() {
        synchronized(listeners) {
            listeners.forEach { it.invoke() }
        }
    }

    fun saveLastIncomingNumber(number: String) {
        prefs.edit().putString(KEY_LAST_INCOMING, number).apply()
    }

    fun getLastIncomingNumber(): String? {
        return prefs.getString(KEY_LAST_INCOMING, null)
    }

    fun setCallState(state: String) {
        prefs.edit().putString(KEY_CALL_STATE, state).apply()
    }

    fun getCallState(): String {
        return prefs.getString(KEY_CALL_STATE, "IDLE") ?: "IDLE"
    }

    fun getAllBlocked(): List<BlockedNumber> {
        val jsonString = prefs.getString(KEY_BLOCKED_NUMBERS, "[]") ?: "[]"
        val list = mutableListOf<BlockedNumber>()
        try {
            val jsonArray = JSONArray(jsonString)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    BlockedNumber(
                        phoneNumber = obj.getString("phoneNumber"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        reason = obj.optString("reason", "Ovoz pasaytirish orqali")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list.sortedByDescending { it.timestamp }
    }

    fun addBlockedNumber(rawNumber: String, reason: String = "Ovoz pasaytirish orqali bloklandi"): Boolean {
        val normalized = normalizeNumber(rawNumber)
        if (normalized.isBlank()) return false

        val currentList = getAllBlocked().toMutableList()
        // Check if already exists
        val exists = currentList.any { numbersMatch(it.phoneNumber, normalized) }
        if (exists) {
            return false
        }

        currentList.add(0, BlockedNumber(normalized, System.currentTimeMillis(), reason))
        saveList(currentList)
        notifyListeners()
        return true
    }

    fun removeBlockedNumber(rawNumber: String): Boolean {
        val currentList = getAllBlocked().toMutableList()
        val removed = currentList.removeAll { numbersMatch(it.phoneNumber, rawNumber) }
        if (removed) {
            saveList(currentList)
            notifyListeners()
        }
        return removed
    }

    fun isBlocked(incomingNumber: String): Boolean {
        if (incomingNumber.isBlank()) return false
        val list = getAllBlocked()
        return list.any { numbersMatch(it.phoneNumber, incomingNumber) }
    }

    private fun saveList(list: List<BlockedNumber>) {
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("phoneNumber", item.phoneNumber)
            obj.put("timestamp", item.timestamp)
            obj.put("reason", item.reason)
            jsonArray.put(obj)
        }
        prefs.edit().putString(KEY_BLOCKED_NUMBERS, jsonArray.toString()).apply()
    }
}
