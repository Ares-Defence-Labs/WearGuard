package com.architect.wearguard.models

data class WearConnectionConfig(
    val id: WearConnectionId,
    val appId: String,
    val namespace: String = "/wearguard"
)