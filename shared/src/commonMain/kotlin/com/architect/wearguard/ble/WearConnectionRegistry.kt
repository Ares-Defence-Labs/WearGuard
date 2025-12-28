package com.architect.wearguard.ble

import com.architect.wearguard.models.WearConnectionId

object WearConnectionRegistry {
    private val map = mutableMapOf<WearConnectionId, WearConnection>()
    private var defaultId: WearConnectionId? = null

    fun register(connection: WearConnection, asDefault: Boolean = false) {
        map[connection.id] = connection
        if (asDefault || defaultId == null) defaultId = connection.id
    }

    fun get(id: WearConnectionId): WearConnection =
        requireNotNull(map[id]) { "No WearConnection registered for id=${id.value}" }

    fun default(): WearConnection =
        requireNotNull(defaultId).let { get(it) }

    fun all(): List<WearConnection> = map.values.toList()
}

