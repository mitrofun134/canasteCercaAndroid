plugins {
    alias(libs.plugins.canasta.android.library)
    alias(libs.plugins.canasta.android.compose)
}

dependencies {
    api(libs.androidx.navigation3.runtime)
    api(libs.androidx.lifecycle.viewmodel.navigation3)
}
