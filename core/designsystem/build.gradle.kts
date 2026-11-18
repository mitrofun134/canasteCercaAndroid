plugins {
    alias(libs.plugins.canasta.android.library)
    alias(libs.plugins.canasta.android.compose)
}

dependencies {
    api(projects.core.common)
    api(projects.core.model)
    implementation(libs.coil.compose)
    // Сетевой загрузчик Coil на Ktor; движок OkHttp приходит из :core:network:impl
    implementation(libs.coil.network.ktor3)
}
