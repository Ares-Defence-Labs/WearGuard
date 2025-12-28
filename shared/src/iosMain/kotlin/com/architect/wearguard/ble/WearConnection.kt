@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.UnsafeNumber::class)

package com.architect.wearguard.ble

import com.architect.wearguard.models.*
import kotlinx.cinterop.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import platform.Foundation.*
import platform.WatchConnectivity.*
import platform.darwin.NSObject

class ApplePhoneConnection(
    override val id: WearConnectionId,
    private val namespace: String = "/wearguard",
) : WearConnection {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _events = MutableSharedFlow<WearEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val events: Flow<WearEvent> = _events.asSharedFlow()

    private val _incoming = MutableSharedFlow<WearMessage>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val incoming: Flow<WearMessage> = _incoming.asSharedFlow()

    private val session: WCSession? =
        if (WCSession.isSupported()) WCSession.defaultSession() else null

    private val delegate = IosSessionDelegate(
        namespace = namespace,
        onEvent = { evt -> scope.launch { _events.emit(evt) } },
        onMessage = { msg -> scope.launch { _incoming.emit(msg) } },
    )

    override suspend fun connect(policy: ConnectionPolicy): ConnectionResult {
        val s = session ?: return ConnectionResult.Failure(
            error = WearError.TransportFailure(code = null, detail = "WatchConnectivity not supported"),
            retryable = false
        )

        return try {
            s.delegate = delegate
            s.activateSession()

            val peer = PeerInfo(id = "watchconnectivity", name = "AppleWatch")

            scope.launch {
                _events.emit(
                    WearEvent.Log(
                        "WCSession activate called (paired=${s.paired}, watchAppInstalled=${s.watchAppInstalled}, reachable=${s.reachable}, state=${s.activationState})"
                    )
                )
                _events.emit(WearEvent.PeerUpdated(peer))
            }

            ConnectionResult.Success(
                peer = peer,
                negotiatedMtu = null,
                transport = TransportType.WATCH_CONNECTIVITY
            )
        } catch (t: Throwable) {
            val err = WearError.TransportFailure(code = null, detail = t.message ?: "WCSession activation failed")
            scope.launch { _events.emit(WearEvent.Error(err)) }
            ConnectionResult.Failure(err, retryable = true)
        }
    }

    override suspend fun disconnect(): Boolean {
        return try {
            session?.delegate = null
            scope.launch { _events.emit(WearEvent.Log("WCSession delegate cleared")) }
            true
        } catch (t: Throwable) {
            scope.launch { _events.emit(WearEvent.Error(WearError.TransportFailure(null, t.message ?: "disconnect failed"))) }
            false
        }
    }

    override suspend fun send(message: WearMessage): SendResult {
        val s = session ?: return SendResult.Failed(
            WearError.TransportFailure(code = null, detail = "WatchConnectivity not supported")
        )

        return try {
            val path = if (message.type.startsWith("/")) message.type else "$namespace/${message.type}"
            val map: Map<Any?, Any?> = mapOf(
                "path" to path,
                "bytes" to message.payload.toNSData(),
                "timestampMs" to NSNumber.numberWithLongLong(message.timestampMs),
                "id" to message.id
            )

            if (s.reachable) {
                s.sendMessage(
                    message = map,
                    replyHandler = null,
                    errorHandler = { error ->
                        val detail = error?.localizedDescription ?: "sendMessage error"
                        scope.launch { _events.emit(WearEvent.Error(WearError.TransportFailure(null, detail))) }
                    }
                )
            } else {
                s.transferUserInfo(map.toNSDictionary())
            }

            SendResult.Sent
        } catch (t: Throwable) {
            val err = WearError.TransportFailure(code = null, detail = t.message ?: "send failed")
            scope.launch { _events.emit(WearEvent.Error(err)) }
            SendResult.Failed(err)
        }
    }

    // ---- helpers ----
    @OptIn(ExperimentalForeignApi::class)
    private fun ByteArray.toNSData(): NSData {
        if (isEmpty()) return NSData()
        return usePinned { pinned ->
            NSData.dataWithBytes(
                bytes = pinned.addressOf(0),
                length = size.convert()
            )
        }
    }

    private fun Map<Any?, Any?>.toNSDictionary(): Map<Any?, *> =
        NSDictionary.dictionaryWithDictionary(this)
}

/**
 * iOS-only delegate (contains sessionDidBecomeInactive / sessionDidDeactivate).
 */
private class IosSessionDelegate(
    private val namespace: String,
    private val onEvent: (WearEvent) -> Unit,
    private val onMessage: (WearMessage) -> Unit,
) : NSObject(), WCSessionDelegateProtocol {

    override fun session(
        session: WCSession,
        activationDidCompleteWithState: WCSessionActivationState,
        error: NSError?
    ) {
        if (error != null) {
            onEvent(WearEvent.Error(WearError.TransportFailure(null, error.localizedDescription)))
            return
        }
        onEvent(WearEvent.Log("WCSession activated state=$activationDidCompleteWithState"))
    }

    override fun sessionReachabilityDidChange(session: WCSession) {
        onEvent(WearEvent.Log("Reachability changed reachable=${session.reachable}"))
    }

    override fun session(
        session: WCSession,
        didReceiveMessage: Map<Any?, *>,
        replyHandler: (Map<Any?, *>?) -> Unit
    ) {
        emitWearMessage(didReceiveMessage)
    }

    override fun sessionDidBecomeInactive(session: WCSession) {}
    override fun sessionDidDeactivate(session: WCSession) {
        session.activateSession()
    }

    private fun emitWearMessage(map: Map<Any?, *>) {
        val path = (map["path"] as? String) ?: "$namespace/unknown"
        val data = map["bytes"] as? NSData
        val payload = data?.toByteArray() ?: ByteArray(0)
        val ts = (map["timestampMs"] as? NSNumber)?.longLongValue ?: 0L
        val id = (map["id"] as? String) ?: "wc-msg"

        onMessage(
            WearMessage(
                id = id,
                type = path,
                correlationId = null,
                payload = payload,
                expectsAck = false,
                timestampMs = ts
            )
        )
    }

    private fun NSData.toByteArray(): ByteArray {
        val n = length.toInt()
        if (n == 0) return ByteArray(0)
        val ptr = bytes ?: return ByteArray(0)
        return ptr.reinterpret<ByteVar>().readBytes(n)
    }
}