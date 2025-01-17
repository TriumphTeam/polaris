import dev.triumphteam.root.includeProject

dependencyResolutionManagement {
    includeBuild("build-logic")
    repositories.gradlePluginPortal()
}

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.triumphteam.dev/releases")
    }
}

rootProject.name = "polaris"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

plugins {
    id("dev.triumphteam.root.settings") version "0.0.14"
}

listOf(
    "core" to "core",

    "hocon" to "hocon",
    "yaml" to "yaml",

    "serialization/hocon" to "serialization-hocon",
    "serialization/yaml" to "serialization-yaml",
).forEach(::includeProject)
