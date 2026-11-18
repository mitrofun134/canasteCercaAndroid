plugins {
    alias(libs.plugins.canasta.android.library)
    alias(libs.plugins.canasta.metro)
}

dependencies {
    api(projects.core.storage.api)
    implementation(libs.androidx.datastore.preferences)
}
