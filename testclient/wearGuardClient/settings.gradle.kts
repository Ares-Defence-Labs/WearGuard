enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")
pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "wearGuardClient"
include(":androidApp")
include(":testClientShared")

include(":shared")
project(":shared").projectDir = file("../../shared")
project(":shared").name = "wearguard-shared"
include(":wearapp")
 