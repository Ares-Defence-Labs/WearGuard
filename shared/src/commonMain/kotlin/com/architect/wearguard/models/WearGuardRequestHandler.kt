package com.architect.wearguard.models

import kotlin.concurrent.Volatile

object WearGuardRequestHandler {
    @Volatile
    var handler: ((String) -> String)? = null
}