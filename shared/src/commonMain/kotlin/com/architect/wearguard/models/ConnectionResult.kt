package com.architect.wearguard.models

sealed class ConnectionResult {

    /** Successfully connected to a peer */
    data class Success(
        val peer: PeerInfo,
        val negotiatedMtu: Int? = null,
        val transport: TransportType
    ) : ConnectionResult()

    /** Failed before connection was established */
    data class Failure(
        val error: WearError,
        val retryable: Boolean
    ) : ConnectionResult()

    /** Connection attempt was cancelled */
    data object Cancelled : ConnectionResult()
}

