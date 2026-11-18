plugins {
    alias(libs.plugins.canasta.feature.impl)
}

dependencies {
    api(projects.feature.catalog.api)
    implementation(projects.feature.basket.api)
}
