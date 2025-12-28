package com.architect.wearguard.models

sealed class SendResult {
    data object Sent : SendResult()
    data class Failed(val error: WearError) : SendResult()
    data class Acked(val rttMs: Long) : SendResult()
}

