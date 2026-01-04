package com.architect.wearguardclient

import com.architect.wearguard.ble.WearConnectionFactory
import com.architect.wearguard.ble.WearConnectionRegistry
import com.architect.wearguard.models.ConnectionPolicy
import com.architect.wearguard.models.WearConnectionConfig
import com.architect.wearguard.models.WearConnectionId
import com.architect.wearguard.models.WearMessage
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

actual class TriggerConnection {
    actual companion object {
        actual fun runConnection() {
            val wearConnection = WearConnectionFactory.create(
                WearConnectionConfig(
                    id = WearConnectionId("testClientApp"),
                    appId = "testClientApp",
                    namespace = "/testClient"
                )
            )

            // to register the wearConnection as a default connection (there can only be a single default)
            WearConnectionRegistry.register(
                wearConnection,
                asDefault = true
            )

            GlobalScope.launch {
                WearConnectionRegistry
                    .default()
                    .connect(ConnectionPolicy())

                wearConnection.incoming.collect { message ->
                    println(
                        "Results Captured - ${
                            message.payload.decodeToString()
                        }"
                    )
                    // Handle incoming data here
                }
            }
        }

        actual fun sendData() {
            GlobalScope.launch {
                val message = WearMessage(
                    id = "msg-001",
                    type = "ping",
                    correlationId = null,
                    payload = "hello from watch".encodeToByteArray(),
                    expectsAck = false,
                )

                WearConnectionRegistry
                    .default().send(message)
            }
        }
    }
}