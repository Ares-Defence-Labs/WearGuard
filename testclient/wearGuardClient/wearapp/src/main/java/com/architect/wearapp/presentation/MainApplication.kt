package com.architect.wearapp.presentation

import android.app.Application
import com.architect.wearguard.ble.WearConnection
import com.architect.wearguard.ble.WearConnectionFactory
import com.architect.wearguard.ble.WearConnectionRegistry
import com.architect.wearguard.models.WearConnectionConfig
import com.architect.wearguard.models.WearConnectionId
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MainApplication : Application() {

    companion object {
        lateinit var wearConnection: WearConnection
    }

    override fun onCreate() {
        super.onCreate()

        WearConnectionFactory.initContext(this)
        wearConnection = WearConnectionFactory.create(
            WearConnectionConfig(
                id = WearConnectionId("testClientApp"),
                appId = "testClientApp",
                namespace = "/testClient"
            )
        )

        WearConnectionRegistry.register(wearConnection, asDefault = true)
        GlobalScope.launch {
            wearConnection.incoming.collect {
                val q = it
                // process any incoming tests
            }
        }
    }
}