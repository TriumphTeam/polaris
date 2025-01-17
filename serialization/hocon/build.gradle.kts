plugins {
    id("polaris.base")
}

dependencies {
    api(projects.polarisSerializationCommon)

    implementation(libs.hocon)
}
