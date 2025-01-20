plugins {
    id("polaris.base")
    id("polaris.library")
}

dependencies {
    api(projects.polarisCore)

    implementation(libs.yaml)
}
