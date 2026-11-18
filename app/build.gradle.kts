plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "ar.canastacerca"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "ar.canastacerca"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 2
        versionName = "0.2.0"

        // Какой сайт открывать. Свой адрес и ключ из `npm run share` — в ~/.gradle/gradle.properties (см. README)
        val baseUrl = providers.gradleProperty("canasta.baseUrl").orElse("http://10.0.2.2:3000/").get()
        val accessKey = providers.gradleProperty("canasta.accessKey").orElse("").get()
        buildConfigField("String", "CANASTA_BASE_URL", "\"$baseUrl\"")
        buildConfigField("String", "CANASTA_ACCESS_KEY", "\"$accessKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
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
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core.ktx)
}
