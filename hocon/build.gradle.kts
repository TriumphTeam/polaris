plugins {
    id("polaris.base")
    id("polaris.library")
}

dependencies {
    api(projects.polarisCore)

    implementation(libs.hocon)

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
}
