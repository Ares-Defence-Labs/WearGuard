@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.UnsafeNumber::class)

package com.architect.wearguard.ble

import com.architect.wearguard.models.*
import kotlinx.cinterop.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
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

    private val activation = CompletableDeferred<Boolean>()
    private val delegate = IosSessionDelegate(
        namespace = namespace,
        onEvent = { evt -> scope.launch { _events.emit(evt) } },
        onMessage = { msg -> scope.launch { _incoming.emit(msg) } },
        onActivated = { ok ->
            if (!activation.isCompleted) activation.complete(ok)
        }
    )

    override suspend fun connect(policy: ConnectionPolicy): ConnectionResult {
        return try {
            withContext(Dispatchers.Main) {
                session?.delegate = delegate
                session?.activateSession()
            }

            val ok = withTimeout(policy.connectTimeoutMs) { activation.await() }
            if (!ok) {
                return ConnectionResult.Failure(
                    error = WearError.TransportFailure(null, "WCSession activation failed"),
                    retryable = true
                )
            }

            val peer = PeerInfo(id = "watchconnectivity", name = "AppleWatch")

            scope.launch {
                _events.emit(
                    WearEvent.Log(
                        "WCSession activated (paired=${session?.paired}, watchAppInstalled=${session?.watchAppInstalled}, reachable=${session?.reachable}, state=${session?.activationState})"
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
            val err = WearError.TransportFailure(null, t.message ?: "WCSession activation failed")
            scope.launch { _events.emit(WearEvent.Error(err)) }
            ConnectionResult.Failure(err, retryable = true)
        }
    }

    override suspend fun disconnect(): Boolean {
        return try {
            withContext(Dispatchers.Main) { session?.delegate = null }
            scope.launch { _events.emit(WearEvent.Log("WCSession delegate cleared")) }
            true
        } catch (t: Throwable) {
            scope.launch {
                _events.emit(
                    WearEvent.Error(
                        WearError.TransportFailure(
                            null,
                            t.message ?: "disconnect failed"
                        )
                    )
                )
            }
            false
        }
    }

    override suspend fun send(message: WearMessage): SendResult {
        if (!activation.isCompleted) {
            return SendResult.Failed(
                WearError.TransportFailure(
                    null,
                    "WCSession not activated (call connect() first)"
                )
            )
        }

        return try {
            val path =
                if (message.type.startsWith("/")) message.type else "$namespace/${message.type}"
            val payloadMap: Map<Any?, Any?> = mapOf(
                "path" to path,
                "bytes" to message.payload.toNSData(),
                "timestampMs" to NSNumber.numberWithLongLong(message.timestampMs),
                "id" to message.id
            )

            withContext(Dispatchers.Main) {
                if (session?.reachable == false) {
                    throw IllegalStateException("Counterpart not reachable (WCSession.reachable=false). Use send when reachable.")
                }

                session?.sendMessage(
                    message = payloadMap,
                    replyHandler = { _ ->
                        // optional ack
                    },
                    errorHandler = { error ->
                        val detail = error?.localizedDescription ?: "sendMessage error"
                        scope.launch {
                            _events.emit(
                                WearEvent.Error(
                                    WearError.TransportFailure(
                                        null,
                                        detail
                                    )
                                )
                            )
                        }
                    }
                )
            }

            SendResult.Sent
        } catch (t: Throwable) {
            val err = WearError.TransportFailure(null, t.message ?: "send failed")
            scope.launch { _events.emit(WearEvent.Error(err)) }
            SendResult.Failed(err)
        }
    }
}

internal class IosSessionDelegate(
    private val namespace: String,
    private val onEvent: (WearEvent) -> Unit,
    private val onMessage: (WearMessage) -> Unit,
    private val onActivated: (Boolean) -> Unit,
) : NSObject(), WCSessionDelegateProtocol {

    override fun session(
        session: WCSession,
        activationDidCompleteWithState: WCSessionActivationState,
        error: NSError?
    ) {
        if (error != null) {
            onEvent(WearEvent.Error(WearError.TransportFailure(null, error.localizedDescription)))
            onActivated(false)
            return
        }
        onEvent(WearEvent.Log("WCSession activated state=$activationDidCompleteWithState"))
        onActivated(activationDidCompleteWithState == WCSessionActivationStateActivated)
    }

    override fun sessionReachabilityDidChange(session: WCSession) {
        onEvent(WearEvent.Log("Reachability changed reachable=${session.reachable}"))
    }

    override fun session(
        session: WCSession,
        didReceiveMessageData: NSData,
        replyHandler: (NSData?) -> Unit
    ) {
        val payload = didReceiveMessageData.toByteArray()

        onMessage(
            WearMessage(
                id = "wc-msgdata",
                type = "$namespace/messageData",
                correlationId = null,
                payload = payload,
                expectsAck = false,
                timestampMs = nowEpochMs()
            )
        )

        replyHandler(NSData())
    }

    override fun session(
        session: WCSession,
        didReceiveMessage: Map<Any?, *>,
        replyHandler: (Map<Any?, *>?) -> Unit
    ) {
        emitMap(didReceiveMessage)

        replyHandler(mapOf("ok" to true))
    }

    override fun sessionDidBecomeInactive(session: WCSession) {}
    override fun sessionDidDeactivate(session: WCSession) {
        session.activateSession()
    }

    private fun emitMap(map: Map<Any?, *>) {
        val path = (map["path"] as? String) ?: "$namespace/unknown"
        val data = map["bytes"] as? NSData
        val payload = data?.toByteArray() ?: ByteArray(0)
        val ts = (map["timestampMs"] as? NSNumber)?.longLongValue ?: nowEpochMs()
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
        if (n <= 0) return ByteArray(0)
        val ptr = bytes ?: return ByteArray(0)
        return ptr.reinterpret<ByteVar>().readBytes(n)
    }

    private fun nowEpochMs(): Long =
        (NSDate().timeIntervalSince1970 * 1000.0).toLong()
}

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return this.usePinned { pinned ->
        NSData.dataWithBytes(
            bytes = pinned.addressOf(0),
            length = this.size.convert()
        )
    }
}