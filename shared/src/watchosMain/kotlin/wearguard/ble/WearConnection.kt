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
import kotlin.concurrent.Volatile

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

    private val activation = CompletableDeferred<Boolean>()

    @Volatile
    private var activateCalled = false

    private val delegate = WatchSessionDelegate(
        onEvent = { evt -> scope.launch { _events.emit(evt) } },
        onActivated = { ok ->
            if (!activation.isCompleted) activation.complete(ok)
        },
        onIncomingMessage = { msg ->
            scope.launch { _incoming.emit(msg) }
        }
    )

    override suspend fun connect(policy: ConnectionPolicy): ConnectionResult {
        val s = session ?: return ConnectionResult.Failure(
            error = WearError.TransportFailure(null, "WatchConnectivity not supported"),
            retryable = false
        )

        return try {
            withContext(Dispatchers.Main) {
                s.delegate = delegate
                if (!activateCalled) {
                    activateCalled = true
                    s.activateSession()
                }
            }

            val ok = withTimeout(policy.connectTimeoutMs) { activation.await() }
            if (!ok) {
                return ConnectionResult.Failure(
                    error = WearError.TransportFailure(null, "WCSession activation failed"),
                    retryable = true
                )
            }

            val peer = PeerInfo(id = "watchconnectivity", name = "PairedDevice")

            scope.launch {
                _events.emit(
                    WearEvent.Log(
                        "WCSession activated (reachable=${s.reachable}, state=${s.activationState})"
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
        }
    }

    override suspend fun disconnect(): Boolean {
        return try {
            withContext(Dispatchers.Main) {
                session?.delegate = null
            }
            scope.launch { _events.emit(WearEvent.Log("WCSession delegate cleared")) }
            true
        } catch (t: Throwable) {
            val err = WearError.TransportFailure(null, t.message ?: "disconnect failed")
            scope.launch { _events.emit(WearEvent.Error(err)) }
            false
        }
    }

    override suspend fun send(message: WearMessage): SendResult {
        val s = session ?: return SendResult.Failed(
            WearError.TransportFailure(null, "WatchConnectivity not supported")
        )

        if (!activation.isCompleted || activation.getCompleted() != true) {
            return SendResult.Failed(
                WearError.TransportFailure(null, "WCSession not activated (call connect() first)")
            )
        }

        return try {
            val path =
                if (message.type.startsWith("/")) message.type else "$namespace/${message.type}"

            withContext(Dispatchers.Main) {
                if (s.reachable) {
                    val envelope = encodeEnvelope(path, message.payload)
                    s.sendMessageData(
                        data = envelope.toNSData(),
                        replyHandler = null,
                        errorHandler = { error ->
                            val detail = error?.localizedDescription ?: "sendMessageData error"
                            scope.launch { _events.emit(WearEvent.Error(WearError.TransportFailure(null, detail))) }
                        }
                    )
                } else {
                    val userInfo: Map<Any?, Any?> = mapOf(
                        "path" to path,
                        "bytes" to message.payload.toNSData(),
                        "timestampMs" to NSNumber.numberWithLongLong(message.timestampMs),
                        "id" to message.id
                    )
                    s.transferUserInfo(userInfo)
                }
            }

            SendResult.Sent
        } catch (t: Throwable) {
            val err = WearError.TransportFailure(null, t.message ?: "send failed")
            scope.launch { _events.emit(WearEvent.Error(err)) }
            SendResult.Failed(err)
        }
    }

    private fun encodeEnvelope(path: String, payload: ByteArray): ByteArray {
        val pathBytes = path.encodeToByteArray()
        val len = pathBytes.size

        val out = ByteArray(4 + len + payload.size)
        out[0] = ((len ushr 24) and 0xFF).toByte()
        out[1] = ((len ushr 16) and 0xFF).toByte()
        out[2] = ((len ushr 8) and 0xFF).toByte()
        out[3] = (len and 0xFF).toByte()

        pathBytes.copyInto(out, destinationOffset = 4)
        payload.copyInto(out, destinationOffset = 4 + len)
        return out
    }

    private fun decodeEnvelope(bytes: ByteArray): Pair<String, ByteArray> {
        if (bytes.size < 4) return "/unknown" to bytes

        val len =
            ((bytes[0].toInt() and 0xFF) shl 24) or
                    ((bytes[1].toInt() and 0xFF) shl 16) or
                    ((bytes[2].toInt() and 0xFF) shl 8) or
                    (bytes[3].toInt() and 0xFF)

        if (len < 0 || 4 + len > bytes.size) return "/unknown" to bytes

        val path = bytes.copyOfRange(4, 4 + len).decodeToString()
        val payload = bytes.copyOfRange(4 + len, bytes.size)
        return path to payload
    }

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

    @OptIn(ExperimentalForeignApi::class)
    private fun NSData.toByteArray(): ByteArray {
        val n = length.toInt()
        if (n == 0) return ByteArray(0)
        val ptr = bytes ?: return ByteArray(0)
        return ptr.reinterpret<ByteVar>().readBytes(n)
    }

    private fun mutableNSDictionaryOf(vararg pairs: Pair<String, Any?>): NSDictionary {
        val dict = NSMutableDictionary()
        for ((k, v) in pairs) {
            if (v == null) continue
            dict.setObject(v, forKey = (k as NSString))
        }
        return dict
    }

    private inner class WatchSessionDelegate(
        private val onEvent: (WearEvent) -> Unit,
        private val onActivated: (Boolean) -> Unit,
        private val onIncomingMessage: (WearMessage) -> Unit
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

        override fun session(session: WCSession, didReceiveMessageData: NSData) {
            val raw = didReceiveMessageData.toByteArray()
            val (path, payload) = decodeEnvelope(raw)

            onIncomingMessage(
                WearMessage(
                    id = "wc-data",
                    type = path,
                    correlationId = null,
                    payload = payload,
                    expectsAck = false,
                    timestampMs = nowEpochMs()
                )
            )
        }

        override fun session(
            session: WCSession,
            didReceiveMessage: Map<Any?, *>,
            replyHandler: (Map<Any?, *>?) -> Unit
        ) {
            val path = didReceiveMessage["path"] as? String ?: "$namespace/unknown"
            val data = didReceiveMessage["bytes"] as? NSData
            val payload = data?.toByteArray() ?: ByteArray(0)
            val ts = (didReceiveMessage["timestampMs"] as? NSNumber)?.longLongValue ?: nowEpochMs()
            val id = didReceiveMessage["id"] as? String ?: "wc-userInfo"

            onIncomingMessage(
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

        private fun nowEpochMs(): Long =
            (NSDate().timeIntervalSince1970 * 1000.0).toLong()
    }
}