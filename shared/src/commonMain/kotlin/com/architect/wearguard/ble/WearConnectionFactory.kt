package com.architect.wearguard.ble

import com.architect.wearguard.models.WearConnectionConfig

expect object WearConnectionFactory {
    fun create(config: WearConnectionConfig): WearConnection
}