package com.architect.wearguard.models

sealed class WearError {
    data class PermissionMissing(val permission: String) : WearError()
    data class BluetoothOff(val detail: String? = null) : WearError()
    data class PeerNotFound(val timeoutMs: Long) : WearError()
    data class PairingRequired(val detail: String? = null) : WearError()
    data class Timeout(val operation: String, val timeoutMs: Long) : WearError()
    data class TransportFailure(val code: Int? = null, val detail: String? = null) : WearError()
    data class Protocol(val detail: String) : WearError()
}
