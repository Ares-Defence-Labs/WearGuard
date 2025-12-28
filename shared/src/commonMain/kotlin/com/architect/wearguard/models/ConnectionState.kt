package com.architect.wearguard.models

sealed class ConnectionState {
    data object Idle : ConnectionState()
    data object Scanning : ConnectionState()
    data class Connecting(val attempt: Int) : ConnectionState()
    data class Connected(
        val peer: PeerInfo,
        val rssi: Int? = null
    ) : ConnectionState()
    data class Reconnecting(val attempt: Int) : ConnectionState()
    data class Disconnected(val reason: DisconnectionState) : ConnectionState()
}


