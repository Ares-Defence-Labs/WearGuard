package com.architect.wearguard.services

import com.architect.wearguard.models.WearMessage
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

class OneMessageMobileServiceListener : WearableListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val messageClient by lazy { Wearable.getMessageClient(this) }

    companion object {
        private const val PATH_SEND = "/wearguard/send"
        private const val PATH_REQUEST_LATEST = "/wearguard/request_latest"
        private const val PATH_RESPONSE_LATEST = "/wearguard/response_latest"
    }

    override fun onMessageReceived(event: MessageEvent) {
        val path = event.path
        val fromNodeId = event.sourceNodeId
        val bytes = event.data ?: byteArrayOf()

        val msg = WearMessage(
            id = UUID.randomUUID().toString(),
            type = path,
            correlationId = null,
            payload = bytes,
            expectsAck = false,
            timestampMs = System.currentTimeMillis()
        )

        when (path) {
            PATH_REQUEST_LATEST -> {
                scope.launch {
                    val payload = buildLatestPayload()

                    // respond back to the watch that requested it
                    messageClient.sendMessage(fromNodeId, PATH_RESPONSE_LATEST, payload).await()
                }
            }

            PATH_SEND -> {
                // already emitted into flow; app-specific routing sits above this (RPC)
            }

            else -> {
                // unknown path; still emitted
            }
        }
    }

    private fun buildLatestPayload(): ByteArray {
        // Replace with real storage fetch (JSON/Proto etc)
        return "latest:ok".encodeToByteArray()
    }
}