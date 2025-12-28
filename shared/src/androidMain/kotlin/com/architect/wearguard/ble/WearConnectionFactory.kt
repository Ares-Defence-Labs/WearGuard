package com.architect.wearguard.ble

import android.content.Context
import com.architect.wearguard.models.WearConnectionConfig

actual object WearConnectionFactory {
    private lateinit var appContext: Context;
    fun initContext(appContext: Context) {
        this.appContext = appContext
    }

    actual fun create(config: WearConnectionConfig): WearConnection {
        return AndroidWearConnection(
            id = config.id,
            namespace = config.namespace,
            context = appContext
        )
    }
}