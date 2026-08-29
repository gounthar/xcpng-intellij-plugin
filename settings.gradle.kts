rootProject.name = "xcpng-ide"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()

        // The IntelliJ Platform Gradle plugin adds the JetBrains repositories through its own
        // `intellijPlatform` repository extension, declared in build.gradle.kts. That extension
        // only exists once the plugin has been applied, which is a build-script concern.
    }
}
