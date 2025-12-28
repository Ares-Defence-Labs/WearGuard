package com.architect.wearguard.models

sealed class DisconnectionState {

    /** User explicitly disconnected */
    data object UserInitiated : DisconnectionState()

    /** Connection dropped unexpectedly */
    data object ConnectionLost : DisconnectionState()

    /** Peer (watch/phone) disconnected */
    data object PeerDisconnected : DisconnectionState()

    /** Bluetooth / transport turned off */
    data object TransportUnavailable : DisconnectionState()

    /** Permissions revoked while connected */
    data class PermissionRevoked(
        val permission: String
    ) : DisconnectionState()

    /** Timed out during operation (handshake, keepalive, request) */
    data class Timeout(
        val operation: String,
        val timeoutMs: Long
    ) : DisconnectionState()

    /** Protocol / decoding error */
    data class ProtocolError(
        val detail: String
    ) : DisconnectionState()

    /** OS killed the connection (background limits, watchdog, etc.) */
    data object SystemTerminated : DisconnectionState()

    /** Fatal error — no auto-reconnect */
    data class Fatal(
        val error: WearError
    ) : DisconnectionState()

    /** Unknown or uncategorised reason */
    data object Unknown : DisconnectionState()
}

