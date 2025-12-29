# 🛡️ WearGuard

**Secure, customisable communication between wearable devices and mobile devices**  
Built for **Kotlin Multiplatform (KMP)** and **Flutter**

WearGuard provides a **reliable, structured, and battery-aware communication layer** between wearables (Wear OS, watchOS) and companion mobile apps.  
It abstracts transport, connection lifecycle, retries, acknowledgements, and message routing — so you focus on **data**, not plumbing.

---

<p align="center">
  <a href="https://github.com/Ares-Defence-Labs/WearGuard">
    <img src="./kotlin.jpg" width="350" />
  </a>
</p>

<p align="center">
  <strong>Targets:</strong> JVM · Android · iOS · Wear OS · watchOS  
</p>

<p align="center">
  <a href="https://central.sonatype.com/artifact/io.github.thearchitect123/wear-guard">
    <img src="https://central.sonatype.com/artifact/io.github.thearchitect123/wear-guard.svg" />
  </a>
  <a href="https://github.com/Ares-Defence-Labs/WearGuard">
    <img src="https://img.shields.io/badge/targets-JVM,_Android,_iOS-white.svg" />
  </a>
</p>

---

## ✨ What is WearGuard?

WearGuard is a library for secure and customisable communication between wearable devices and mobile devices.

Wearable ↔ mobile communication is deceptively hard:

- Multiple transports (Bluetooth, platform bridges, sockets)
- Unstable connectivity
- Background restrictions
- Battery constraints
- Message ordering & delivery guarantees
- ACK / retry semantics

**WearGuard solves this once — properly.**

---

## ✅ Key Features

- 🔐 Secure, structured messaging via `WearMessage`
- 🔁 Connection lifecycle + retry support
- 🔋 Battery-aware connection handling
- 📩 Reactive incoming stream using Kotlin `Flow`
- 🧩 Transport-agnostic architecture
- 🌍 Works across **KMP** and **Flutter**

---

## 🧠 Core Concepts

### WearConnection
A logical connection between:
- A wearable device
- A mobile host app
- A connection namespace + identity

### WearMessage
A strongly-typed message envelope:
- `id`
- `type`
- `payload`
- `correlationId`
- `expectsAck`
- `timestampMs`

### WearConnectionRegistry
Manages active connections, lifecycle, and default resolution.

---

## 📦 Installation

### Gradle (Kotlin / KMP)
```kotlin
implementation("io.github.thearchitect123:wear-guard:+")
```

### How to use (Kotlin / KMP)
1️⃣ Initialise WearGuard Context

Registration & Setup.
<br/>**On Android** (Same concept applies to all other platforms): 

```kotlin
 WearConnectionFactory.initContext(this)
 
 val wearConnection = WearConnectionFactory.create(
    WearConnectionConfig(
        id = WearConnectionId("testClientApp"),
        appId = "testClientApp",
        namespace = "/testClient"
    )
)
  
  // to register the wearConnection as a default connection (there can only be a single default)
  WearConnectionRegistry.register(
    wearConnection,
    asDefault = true
)
```

📥 Receiving Messages (Host / Listener Side)

```kotlin
WearConnectionRegistry
.default()
.connect(ConnectionPolicy())

    wearConnection.incoming.collect { message ->
        println(
            "Results Captured - ${
                message.payload.decodeToString()
            }"
        )
        // Handle incoming data here
    }
```

📤 Sending Messages (Client / Initiator Side)
```kotlin
val connection = WearConnectionRegistry.default() // connect to the remote device first

when (val result = connection.connect()) {
    is ConnectionResult.Success -> { // on success, send a simple string 
        val peer = result.peer
        val transport = result.transport

        println("Connected to ${peer.name} via $transport")

        val message = WearMessage(
            id = "msg-001",
            type = "ping",
            correlationId = null,
            payload = "hello from watch".encodeToByteArray(),
            expectsAck = false,
            timestampMs = System.currentTimeMillis()
        )

        connection.send(message)
    }

    is ConnectionResult.Failure -> {
        println("Failed to connect: ${result.error}")

        if (result.retryable) {
            println("Retry is allowed")
        }
    }

    ConnectionResult.Cancelled -> {
        println("Connection was cancelled by user/system")
    }
}

```