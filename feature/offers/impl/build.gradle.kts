plugins {
    alias(libs.plugins.canasta.feature.impl)
}

dependencies {
    api(projects.feature.offers.api)
    implementation(projects.feature.basket.api)
}
