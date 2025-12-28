package com.architect.wearguard.models

data class ConnectionPolicy(
    val autoReconnect: Boolean = true,
    val maxReconnectAttempts: Int = 5,
    val backoffMs: List<Long> = listOf(500, 1000, 2000, 4000, 8000),
    val scanTimeoutMs: Long = 15_000
)



