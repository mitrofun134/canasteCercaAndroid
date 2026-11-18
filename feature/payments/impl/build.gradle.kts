plugins {
    alias(libs.plugins.canasta.feature.impl)
}

dependencies {
    api(projects.feature.payments.api)
    implementation(projects.core.storage.api)
}
