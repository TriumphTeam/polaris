import dev.triumphteam.root.KotlinOpt
import dev.triumphteam.root.repository.Repository
import dev.triumphteam.root.repository.applyRepo
import org.gradle.accessors.dm.LibrariesForLibs

// Hack which exposes `libs` to this convention plugin
val libs = the<LibrariesForLibs>()

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("dev.triumphteam.root")
}

repositories {
    mavenCentral()
    applyRepo(Repository.TRIUMPH_PUBLIC, project)
}

dependencies {
    api(kotlin("stdlib"))
}

root {

    configureKotlin {
        jvmVersion(17)
        explicitApi()

        optIn(KotlinOpt.ALL)
    }
}
