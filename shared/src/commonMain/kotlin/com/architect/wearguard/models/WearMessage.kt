package com.architect.wearguard.models

import kotlinx.datetime.Clock

data class WearMessage(
    val id: String,
    val type: String,
    val correlationId: String? = null,
    val payload: ByteArray = byteArrayOf(),
    val expectsAck: Boolean = false,
    val timestampMs: Long = Clock.System.now().toEpochMilliseconds()
)






