plugins {
    alias(libs.plugins.canasta.feature.impl)
}

dependencies {
    api(projects.feature.location.api)
    implementation(projects.core.storage.api)
}
