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

class AppleWatchConnection(
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

    // Activation gate
    private var activation = CompletableDeferred<Boolean>()
    private var connectJob: Deferred<ConnectionResult>? = null

    private val delegate = WatchSessionDelegate(
        namespace = namespace,
        onEvent = { e -> scope.launch { _events.emit(e) } },
        onMessage = { m -> scope.launch { _incoming.emit(m) } },
        onActivated = { ok ->
            if (!activation.isCompleted) activation.complete(ok)
        }
    )

    override suspend fun connect(policy: ConnectionPolicy): ConnectionResult {
        connectJob?.let { return it.await() }
        connectJob = scope.async {
            try {
                activation = CompletableDeferred()
                withContext(Dispatchers.Main) {
                    session?.delegate = delegate
                    session?.activateSession()
                }

                val ok = withTimeout(policy.connectTimeoutMs) { activation.await() }
                if (!ok) {
                    return@async ConnectionResult.Failure(
                        error = WearError.TransportFailure(null, "WCSession activation failed"),
                        retryable = true
                    )
                }

                val activated = (session?.activationState == WCSessionActivationStateActivated)
                if (!activated) {
                    return@async ConnectionResult.Failure(
                        error = WearError.TransportFailure(null, "WCSession not activated yet (state=${session?.activationState})"),
                        retryable = true
                    )
                }

                val peer = PeerInfo(id = "watchconnectivity", name = "PairediPhone")

                scope.launch {
                    _events.emit(
                        WearEvent.Log(
                            "WCSession activated (state=${session?.activationState}, reachable=${session.reachable})"
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
                val err = WearError.TransportFailure(null, t.message ?: "connect failed")
                scope.launch { _events.emit(WearEvent.Error(err)) }
                ConnectionResult.Failure(err, retryable = true)
            } finally {
                connectJob = null
            }
        }

        return connectJob!!.await()
    }

    override suspend fun disconnect(): Boolean {
        return try {
            withContext(Dispatchers.Main) {
                session?.delegate = null
            }
            scope.launch { _events.emit(WearEvent.Log("WCSession delegate cleared")) }
            true
        } catch (t: Throwable) {
            scope.launch { _events.emit(WearEvent.Error(WearError.TransportFailure(null, t.message ?: "disconnect failed"))) }
            false
        }
    }

    override suspend fun send(message: WearMessage): SendResult {
        val path = if (message.type.startsWith("/")) message.type else "$namespace/${message.type}"
        val map: Map<Any?, Any?> = mapOf(
            "path" to path,
            "bytes" to message.payload.toNSData(),
            "timestampMs" to NSNumber.numberWithLongLong(message.timestampMs),
            "id" to message.id
        )

        return if (session?.reachable == true) {
            session?.sendMessage(
                message = map,
                replyHandler = null,
                errorHandler = { err ->
                    val detail = err?.localizedDescription ?: "sendMessage failed"
                    scope.launch { _events.emit(WearEvent.Error(WearError.TransportFailure(null, detail))) }
                }
            )
            SendResult.Sent
        } else {
            session?.transferUserInfo(map)
            SendResult.Sent
        }
    }
}

private class WatchSessionDelegate(
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
        onEvent(WearEvent.Log("activationDidComplete state=$activationDidCompleteWithState"))
        onActivated(activationDidCompleteWithState == WCSessionActivationStateActivated)
    }

    override fun sessionReachabilityDidChange(session: WCSession) {
        onEvent(WearEvent.Log("reachability changed reachable=${session.reachable}"))
    }

    override fun session(session: WCSession, didReceiveMessage: Map<Any?, *>) {
        emitMap(didReceiveMessage)
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

    private fun nowEpochMs(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
}

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
private fun NSData.toByteArray(): ByteArray {
    val n = length.toInt()
    if (n == 0) return ByteArray(0)
    val ptr = bytes ?: return ByteArray(0)
    return ptr.reinterpret<ByteVar>().readBytes(n)
}

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.dataWithBytes(
            bytes = pinned.addressOf(0),
            length = size.convert()
        )
    }
}