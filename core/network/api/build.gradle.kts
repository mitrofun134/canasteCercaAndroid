plugins {
    alias(libs.plugins.canasta.android.library)
}

dependencies {
    api(projects.core.common)
    // Фичи ходят в API через HttpClient: клиент уже настроен в :core:network:impl
    api(libs.ktor.client.core)
}
