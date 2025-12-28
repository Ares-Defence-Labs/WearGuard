package com.architect.wearguard.ble

import com.architect.wearguard.models.WearConnectionConfig

actual object WearConnectionFactory {
    actual fun create(config: WearConnectionConfig): WearConnection {
        return AppleWatchConnection(
            id = config.id,
            namespace = config.namespace,
        )
    }
}