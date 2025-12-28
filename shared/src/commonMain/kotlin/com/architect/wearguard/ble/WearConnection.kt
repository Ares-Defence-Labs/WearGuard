package com.architect.wearguard.ble

import com.architect.wearguard.models.ConnectionPolicy
import com.architect.wearguard.models.ConnectionResult
import com.architect.wearguard.models.SendResult
import com.architect.wearguard.models.WearConnectionId
import com.architect.wearguard.models.WearEvent
import com.architect.wearguard.models.WearMessage
import kotlinx.coroutines.flow.Flow

interface WearConnection {
    val id: WearConnectionId

    val events: Flow<WearEvent>
    val incoming: Flow<WearMessage>

    suspend fun connect(policy: ConnectionPolicy = ConnectionPolicy()): ConnectionResult
    suspend fun disconnect(): Boolean
    suspend fun send(message: WearMessage): SendResult
}

