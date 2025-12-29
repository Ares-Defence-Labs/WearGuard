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
- 🔋 Battery-aware connection handling (connect/disconnect explicitly)
- 📩 Reactive incoming stream using Kotlin `Flow`
- 🧩 Transport-agnostic architecture (future-proof)
- 🌍 Works across **KMP** and **Flutter**

---

## 🧠 Core Concepts

### `WearConnection`
A logical connection between:
- A wearable device
- A mobile host app
- A connection namespace + identity

### `WearMessage`
A strongly-typed message envelope:

- `id` (unique message id)
- `type` (string type like `"ping"`, `"telemetry"`, etc.)
- `payload` (raw bytes for flexibility)
- `correlationId` (optional)
- `expectsAck` (optional delivery semantics)
- `timestampMs`

### `WearConnectionRegistry`
A registry to:
- Manage active connections
- Resolve the default connection
- Coordinate lifecycle safely

---

## 📦 Installation

### Gradle (Kotlin / KMP)

```kotlin
implementation("io.github.thearchitect123:wear-guard:+")