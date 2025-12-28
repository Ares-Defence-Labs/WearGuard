package com.architect.wearguardclient

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform