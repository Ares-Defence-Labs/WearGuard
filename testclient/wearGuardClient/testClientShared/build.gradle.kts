import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

kotlin {
    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_1_8)
                }
            }
        }
    }

    val xcf = XCFramework("wearGuard")

    val iosTargets = listOf(
        iosArm64(),
        iosSimulatorArm64()
    )

    val watchTargets = listOf(
        watchosDeviceArm64(),          // watch device
        watchosSimulatorArm64()  // watch simulator (Apple Silicon)
    )

    (iosTargets
//            +
//            watchTargets
            ).forEach { target ->
        target.binaries.framework {
            baseName = "wearGuard"
            isStatic = true
            xcf.add(this)
        }
    }

    // --------------------------
    // Source sets
    // --------------------------
    sourceSets {
        kotlin.applyDefaultHierarchyTemplate()
        val commonMain by getting {
            dependencies {
                implementation(projects.wearguardShared)
                implementation(libs.coroutines.core)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
            }
        }

        val androidMain by getting {
            dependencies {
                implementation("com.google.android.gms:play-services-wearable:19.0.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
            }
        }

        val iosMain by getting
        val watchosMain by getting

        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
    }
}

android {
    namespace = "com.architect.wearguard"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}