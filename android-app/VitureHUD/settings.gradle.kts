pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        // Add serenegiant repository for UVCCamera dependencies
        maven { url = uri("https://raw.github.com/saki4510t/libcommon/master/repository/") }
    }
}

rootProject.name = "VitureHUD"
include(":app")

// Include UVCCamera modules
include(":libuvccamera")
include(":usbCameraCommon")

// Point to cloned UVCCamera modules
project(":libuvccamera").projectDir = file("../UVCCamera/libuvccamera")
project(":usbCameraCommon").projectDir = file("../UVCCamera/usbCameraCommon")
