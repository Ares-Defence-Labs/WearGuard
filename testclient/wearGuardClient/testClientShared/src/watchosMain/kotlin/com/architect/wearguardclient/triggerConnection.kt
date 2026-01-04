package com.architect.wearguardclient

import com.architect.wearguard.ble.WearConnection
import com.architect.wearguard.ble.WearConnectionFactory
import com.architect.wearguard.ble.WearConnectionRegistry
import com.architect.wearguard.models.ConnectionPolicy
import com.architect.wearguard.models.ConnectionResult
import com.architect.wearguard.models.WearConnectionConfig
import com.architect.wearguard.models.WearConnectionId
import com.architect.wearguard.models.WearMessage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlin.native.concurrent.ThreadLocal

actual class TriggerConnection {
    actual companion object {
        private var connection: WearConnection? = null

        private var connectDeferred: Deferred<ConnectionResult>? = null

        private var listenJob: Job? = null

        // One scope
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        actual fun runConnection() {
            // Create/register only once
            val c = connection ?: WearConnectionFactory.create(
                WearConnectionConfig(
                    id = WearConnectionId("testClientApp"),
                    appId = "testClientApp",
                    namespace = "/testClient"
                )
            ).also { created ->
                connection = created
                WearConnectionRegistry.register(created, asDefault = true)
            }

            // Start connect only once (store deferred)
            if (connectDeferred == null) {
                connectDeferred = scope.async {
                    val result = c.connect(ConnectionPolicy())
                    println("connect() result = $result")
                    result
                }
            }

            // Start listener only once
            if (listenJob == null) {
                listenJob = scope.launch {
                    c.incoming.collectLatest { message ->
                        println("Results Captured - ${message.payload.decodeToString()}")
                    }
                }
            }
        }

        actual fun sendData() {
            scope.launch {
                val c = connection ?: run {
                    println("sendData() called before runConnection(); calling runConnection() now")
                    runConnection()
                    connection!!
                }

                // WAIT for connect to complete
                val result = connectDeferred?.await()
                if (result !is ConnectionResult.Success) {
                    println("Not connected, cannot send. connect result = $result")
                    return@launch
                }

                val message = WearMessage(
                    id = "msg-001",
                    type = "ping",
                    correlationId = null,
                    payload = "hello from watch".encodeToByteArray(),
                    expectsAck = false,
                )

                val send = c.send(message)
                println("send() result = $send")
            }
        }
    }
}