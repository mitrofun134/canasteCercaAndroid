plugins {
    alias(libs.plugins.canasta.feature.impl)
}

dependencies {
    api(projects.feature.basket.api)
    implementation(projects.feature.location.api)
    implementation(projects.feature.payments.api)
    implementation(projects.feature.catalog.api)
    implementation(projects.core.storage.api)
}
