package com.architect.wearguard.models

sealed class WearEvent {

    data class PeerUpdated(
        val peer: PeerInfo
    ) : WearEvent()

    data class Error(
        val error: WearError
    ) : WearEvent()

    data class Log(
        val message: String
    ) : WearEvent()

    data class ConnectionStateChanged(
        val state: ConnectionState
    ) : WearEvent()
}