import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)

    id("org.gradle.maven-publish")
    id("signing")
    id("com.vanniktech.maven.publish") version "0.29.0"
    id("kotlin-parcelize")
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
                implementation("org.jetbrains.kotlinx:atomicfu:0.29.0")
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

////////////////////////////////
//mavenPublishing {
//    // Define coordinates for the published artifact
//    coordinates(
//        groupId = "io.github.thearchitect123",
//        artifactId = "wear-guard",
//        version = "1.0.1"
//    )
//
//    // Configure POM metadata for the published artifact
//    pom {
//        name.set("WearGuard")
//        description.set("WearGuard is a KMP Library for secure and customisable communication between wearable devices and mobile devices")
//        inceptionYear.set("2024")
//        url.set("https://github.com/Ares-Defence-Labs/WearGuard")
//
//        licenses {
//            license {
//                name.set("MIT")
//                url.set("https://opensource.org/licenses/MIT")
//            }
//        }
//
//        // Specify developers information
//        developers {
//            developer {
//                id.set("Dan Gerchcovich")
//                name.set("TheArchitect123")
//                email.set("dan.developer789@gmail.com")
//            }
//        }
//
//        // Specify SCM information
//        scm {
//            url.set("https://github.com/Ares-Defence-Labs/WearGuard")
//        }
//    }
//
//    // Configure publishing to Maven Central
//    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
//
//    // Enable GPG signing for all publications
//    signAllPublications()
//}
//
//signing {
//    val privateKeyFile = project.findProperty("signing.privateKeyFile") as? String
//        ?: error("No Private key file found")
//    val passphrase = project.findProperty("signing.password") as? String
//        ?: error("No Passphrase found for signing")
//
//    // Read the private key from the file
//    val privateKey = File(privateKeyFile).readText(Charsets.UTF_8)
//
//    useInMemoryPgpKeys(privateKey, passphrase)
//    sign(publishing.publications)
//}