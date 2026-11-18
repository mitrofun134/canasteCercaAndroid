plugins {
    alias(libs.plugins.canasta.android.library)
    alias(libs.plugins.canasta.metro)
    alias(libs.plugins.canasta.serialization)
}

dependencies {
    api(projects.core.network.api)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
}
