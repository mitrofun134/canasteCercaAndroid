plugins {
    alias(libs.plugins.canasta.android.application)
    alias(libs.plugins.canasta.android.compose)
    alias(libs.plugins.canasta.metro)
}

android {
    namespace = "ar.canastacerca"

    defaultConfig {
        applicationId = "ar.canastacerca"
        versionCode = 1
        versionName = "0.1.0"

        val baseUrl = providers.gradleProperty("canasta.baseUrl").orElse("http://10.0.2.2:3000/").get()
        val accessKey = providers.gradleProperty("canasta.accessKey").orElse("").get()
        buildConfigField("String", "CANASTA_BASE_URL", "\"$baseUrl\"")
        buildConfigField("String", "CANASTA_ACCESS_KEY", "\"$accessKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            // Локальный сервер (npm run dev) отвечает по http
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            isMinifyEnabled = false
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    // :app — единственное место, где собираются все impl-модули и строится граф Metro
    implementation(projects.core.common)
    implementation(projects.core.model)
    implementation(projects.core.designsystem)
    implementation(projects.core.navigation)
    implementation(projects.core.network.impl)
    implementation(projects.core.storage.impl)

    implementation(projects.feature.offers.impl)
    implementation(projects.feature.catalog.impl)
    implementation(projects.feature.recipes.impl)
    implementation(projects.feature.basket.impl)
    implementation(projects.feature.location.impl)
    implementation(projects.feature.payments.impl)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.metrox.android)
    implementation(libs.metrox.viewmodel.compose)
}
