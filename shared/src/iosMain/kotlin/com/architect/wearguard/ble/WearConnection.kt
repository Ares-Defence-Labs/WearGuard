@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.UnsafeNumber::class)

package com.architect.wearguard.ble

import com.architect.wearguard.models.*
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
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
    private val pendingReplies = mutableMapOf<String, (Map<Any?, *>?) -> Unit>()
    private val pendingLock = SynchronizedObject()

    private fun storeReply(requestId: String, handler: (Map<Any?, *>?) -> Unit) {
        synchronized(pendingLock) {
            pendingReplies[requestId] = handler
        }
    }

    private fun takeReply(requestId: String): ((Map<Any?, *>?) -> Unit)? {
        return synchronized(pendingLock) {
            pendingReplies.remove(requestId)
        }
    }

    override suspend fun activateConnectionOnLaunch() {
        val s = WCSession.defaultSession() ?: return
        s.delegate = delegate
        s.activateSession()
    }

    override suspend fun onReceived(requestId: String, type: String, payload: ByteArray): SendResult {
        val reply = takeReply(requestId)
            ?: return SendResult.Failed(
                WearError.TransportFailure(
                    null,
                    "No pending reply for $requestId"
                )
            )

        reply(
            mapOf(
                "id" to "resp-$requestId",
                "correlationId" to requestId,
                "path" to type,
                "bytes" to payload.toNSData(),
                "timestampMs" to NSNumber.numberWithLongLong(nowEpochMs())
            )
        )

        return SendResult.Sent
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _events = MutableSharedFlow<WearEvent>(
        replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val events: Flow<WearEvent> = _events.asSharedFlow()

    private val _incoming = MutableSharedFlow<WearMessage>(
        replay = 0, extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    override val incoming: Flow<WearMessage> = _incoming.asSharedFlow()

    private val session: WCSession? =
        if (WCSession.isSupported()) WCSession.defaultSession() else null

    private val activation = CompletableDeferred<Boolean>()

    private val delegate = IosSessionDelegate(
        namespace = namespace,
        onEvent = { e -> scope.launch { _events.emit(e) } },
        onIncoming = { m -> scope.launch { _incoming.emit(m) } },
        onActivated = { ok -> if (!activation.isCompleted) activation.complete(ok) },
        storeReply = ::storeReply
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
        return try {
            val map = encodeWearMessageToMap(message, namespace)

            withContext(Dispatchers.Main) {
                if (session == null) throw IllegalStateException("WCSession not supported")

                if (session.reachable == true) {
                    session.sendMessage(
                        message = map,
                        replyHandler = null,
                        errorHandler = { err ->
                            val detail = err?.localizedDescription ?: "sendMessage failed"
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
                } else {
                    session.updateApplicationContext(map, error = null)
                }
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
    private val onIncoming: (WearMessage) -> Unit,
    private val onActivated: (Boolean) -> Unit,
    private val storeReply: (String, (Map<Any?, *>?) -> Unit) -> Unit,
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

    override fun sessionDidBecomeInactive(session: WCSession) {}
    override fun sessionDidDeactivate(session: WCSession) {
        session.activateSession()
    }

    override fun session(session: WCSession, didReceiveApplicationContext: Map<Any?, *>) {
        emitMap(didReceiveApplicationContext)
    }

    override fun session(
        session: WCSession,
        didReceiveMessage: Map<Any?, *>,
        replyHandler: (Map<Any?, *>?) -> Unit
    ) {
        val requestId = didReceiveMessage["id"] as? String ?: "unknown"
        val path = didReceiveMessage["path"] as? String ?: "$namespace/request"
        val bytes = didReceiveMessage["bytes"] as? NSData
        val payload = bytes?.toByteArray() ?: ByteArray(0)
        val ts = (didReceiveMessage["timestampMs"] as? NSNumber)?.longLongValue ?: nowEpochMs()

        storeReply(requestId, replyHandler)
        onIncoming(
            WearMessage(
                id = requestId,
                type = path,
                correlationId = requestId,
                payload = payload,
                expectsAck = true,
                timestampMs = ts
            )
        )
    }

    private fun emitMap(map: Map<Any?, *>) {
        val path = (map["path"] as? String) ?: "$namespace/unknown"
        val data = map["bytes"] as? NSData
        val payload = data?.toByteArray() ?: ByteArray(0)
        val ts = (map["timestampMs"] as? NSNumber)?.longLongValue ?: nowEpochMs()
        val id = (map["id"] as? String) ?: "wc-msg"
        val correlationId = map["correlationId"] as? String

        onIncoming(
            WearMessage(
                id = id,
                type = path,
                correlationId = correlationId,
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

    private fun nowEpochMs(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()
}

@OptIn(ExperimentalForeignApi::class, UnsafeNumber::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned ->
        NSData.dataWithBytes(bytes = pinned.addressOf(0), length = size.convert())
    }
}

private fun nowEpochMs(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

private fun encodeWearMessageToMap(message: WearMessage, namespace: String): Map<Any?, Any?> {
    val path = if (message.type.startsWith("/")) message.type else "$namespace/${message.type}"
    return mapOf(
        "id" to message.id,
        "correlationId" to message.correlationId,
        "path" to path,
        "bytes" to message.payload.toNSData(),
        "timestampMs" to NSNumber.numberWithLongLong(message.timestampMs)
    )
}