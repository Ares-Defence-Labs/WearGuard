package com.architect.wearguardclient

expect class TriggerConnection{
    companion object {
        fun runConnection()

        fun sendData()
    }
}