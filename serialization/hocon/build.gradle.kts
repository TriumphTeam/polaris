plugins {
    id("polaris.base")
}

dependencies {
    api(projects.polarisCore)
    implementation(libs.hocon)

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
}
