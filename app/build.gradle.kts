plugins {
    alias(libs.plugins.android.application)
}

// Всё личное — в ~/.gradle/gradle.properties, не в проекте: репозиторий публичный (README → «Сборка для Google Play»)
val siteUrl = providers.gradleProperty("canasta.baseUrl")
val accessKey = providers.gradleProperty("canasta.accessKey").orElse("").get()
val uploadStore = providers.gradleProperty("canasta.upload.storeFile").orNull

android {
    namespace = "ar.canastacerca"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "ar.canastacerca"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 3
        versionName = "0.3.0"

        // Ключ доступа: пока сайт закрыт (B80), сборки для тестеров и для проверки Google несут ключ
        buildConfigField("String", "CANASTA_ACCESS_KEY", "\"$accessKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        // Ключ загрузки в Google Play. Без него релиз подписывается отладочным ключом: ставится на телефон, но Play не примет
        if (uploadStore != null) {
            create("upload") {
                storeFile = file(uploadStore)
                storePassword = providers.gradleProperty("canasta.upload.storePassword").get()
                keyAlias = providers.gradleProperty("canasta.upload.keyAlias").get()
                keyPassword = providers.gradleProperty("canasta.upload.keyPassword").get()
            }
        }
    }

    buildTypes {
        debug {
            // Локальный сервер (npm run dev): эмулятор видит компьютер как 10.0.2.2, отвечает он по http
            buildConfigField("String", "CANASTA_BASE_URL", "\"${siteUrl.orElse("http://10.0.2.2:3000/").get()}\"")
            manifestPlaceholders["usesCleartextTraffic"] = "true"
        }
        release {
            buildConfigField("String", "CANASTA_BASE_URL", "\"${siteUrl.orElse("https://canastacerca.com/").get()}\"")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (uploadStore != null) "upload" else "debug")
        }
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)
}
