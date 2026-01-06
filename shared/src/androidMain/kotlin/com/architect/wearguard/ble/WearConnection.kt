package com.architect.wearguard.ble

import android.content.Context
import com.architect.wearguard.models.*
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

class AndroidWearConnection(
    private val context: Context,
    override val id: WearConnectionId,
    private val namespace: String = "/wearguard"
) : WearConnection {
    override suspend fun activateConnectionOnLaunch() {
        // apple specific connection
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    private val messageClient: MessageClient =
        Wearable.getMessageClient(context.applicationContext)

    private var peerNodeId: String? = null
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
    private val listener = MessageClient.OnMessageReceivedListener { event ->
        val msg = WearMessage(
            id = event.requestId.toString(),
            type = event.path,
            correlationId = null,
            payload = event.data,
            expectsAck = false,
            timestampMs = System.currentTimeMillis()
        )

        scope.launch {
            _incoming.emit(msg)
        }
    }

    override suspend fun onReceived(requestId: String, type: String, payload: ByteArray): SendResult {
        return send(
            WearMessage(
                id = "resp-$requestId",
                type = type,
                correlationId = requestId,
                payload = payload,
                expectsAck = false
            )
        )
    }

    override suspend fun connect(policy: ConnectionPolicy): ConnectionResult = mutex.withLock {
        try {
            val node = resolveBestPeer()
                ?: return ConnectionResult.Failure(
                    WearError.PeerNotFound(policy.scanTimeoutMs),
                    retryable = true
                )

            peerNodeId = node.id
            messageClient.addListener(listener)

            _events.emit(
                WearEvent.PeerUpdated(
                    PeerInfo(id = node.id, name = node.displayName)
                )
            )

            ConnectionResult.Success(
                peer = PeerInfo(id = node.id, name = node.displayName),
                negotiatedMtu = null,
                transport = TransportType.WEAR_OS_DATA_LAYER
            )
        } catch (t: Throwable) {
            val err = WearError.TransportFailure(null, t.message)
            _events.emit(WearEvent.Error(err))
            ConnectionResult.Failure(err, retryable = true)
        }
    }

    override suspend fun disconnect(): Boolean = mutex.withLock {
        try {
            messageClient.removeListener(listener)
            peerNodeId = null
            _events.emit(WearEvent.Log("Disconnected"))
            true
        } catch (t: Throwable) {
            _events.emit(WearEvent.Error(WearError.TransportFailure(null, t.message)))
            false
        }
    }

    override suspend fun send(message: WearMessage): SendResult = mutex.withLock {
        try {
            val nodeId = peerNodeId ?: resolveBestPeer()?.id
            ?: return@withLock SendResult.Failed(
                WearError.PeerNotFound(0)
            )

            peerNodeId = nodeId

            val path = if (message.type.startsWith("/"))
                message.type
            else
                "$namespace/${message.type}"

            messageClient
                .sendMessage(nodeId, path, message.payload)
                .await()

            SendResult.Sent
        } catch (t: Throwable) {
            val err = WearError.TransportFailure(null, t.message)
            _events.emit(WearEvent.Error(err))
            SendResult.Failed(err)
        }
    }

    fun ingest(fromNodeId: String, path: String, payload: ByteArray) {
        scope.launch {
            peerNodeId = fromNodeId
            _incoming.emit(
                WearMessage(
                    id = "service",
                    type = path,
                    correlationId = null,
                    payload = payload,
                    expectsAck = false,
                    timestampMs = System.currentTimeMillis()
                )
            )
        }
    }

    private suspend fun resolveBestPeer(): Node? {
        val nodes = Wearable
            .getNodeClient(context)
            .connectedNodes
            .await()

        if (nodes.isEmpty()) return null

        return nodes.firstOrNull { runCatching { it.isNearby }.getOrDefault(false) }
            ?: nodes.first()
    }
}