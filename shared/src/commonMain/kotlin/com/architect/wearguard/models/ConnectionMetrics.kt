package com.architect.wearguard.models

data class ConnectionMetrics(
    val lastSeenMs: Long,
    val lastRttMs: Long?,
    val sentCount: Long,
    val receivedCount: Long,
    val lastError: WearError?
)