package com.architect.wearguard.models

data class PeerInfo(
    val id: String,
    val name: String? = null,
    val model: String? = null,
    val osVersion: String? = null
)