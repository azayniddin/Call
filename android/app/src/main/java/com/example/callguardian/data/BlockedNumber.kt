package com.example.callguardian.data

data class BlockedNumber(
    val phoneNumber: String,
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String = "Ovoz pasaytirish orqali bloklangan"
)
