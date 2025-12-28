import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),

        // apple watch
        watchosArm64(),
        watchosArm32(),
        watchosX64(),
        watchosSimulatorArm64(),
        watchosDeviceArm64(),
    ).forEach {
        it.binaries.framework {
            baseName = "wearGuard"
            isStatic = true
        }
    }

    sourceSets {
        kotlin.applyDefaultHierarchyTemplate()
        val commonMain by getting {
            dependencies {
                implementation(libs.coroutines.core)
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.0")
            }
        }

        val androidMain by getting {
            dependencies {
                implementation(libs.coroutines.core)
                implementation("com.google.android.gms:play-services-wearable:19.0.0")

                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.8.1")
            }
        }

        val watchosMain by getting
        val iosArm64Main by getting
        val iosX64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by getting {
            dependencies {

            }
        }
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
