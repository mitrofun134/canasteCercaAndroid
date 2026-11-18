plugins {
    alias(libs.plugins.canasta.feature.impl)
}

dependencies {
    api(projects.feature.recipes.api)
    implementation(projects.feature.basket.api)
}
