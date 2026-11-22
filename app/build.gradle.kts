plugins {
    alias(libs.plugins.android.application)
}

// Всё личное — в ~/.gradle/gradle.properties, не в проекте: репозиторий публичный (README → «Сборка для Google Play»)
val siteUrl = providers.gradleProperty("canasta.baseUrl")
val accessKey = providers.gradleProperty("canasta.accessKey").orElse("").get()
val uploadStore = providers.gradleProperty("canasta.upload.storeFile").orNull

// Firebase Crashlytics (отчёты о падениях) — только если в app/ лежит google-services.json из консоли Firebase.
// Файл не в git (.gitignore): репозиторий публичный. Без файла сборка — как раньше, Firebase в приложении нет вовсе.
// Плагины google-services и Crashlytics не берём (плагин Crashlytics падает на AGP 9, firebase-android-sdk #7652):
// строки, которые они кладут в ресурсы (google_app_id и др., build id Crashlytics), пишем сами из файла.
val firebaseJson = providers.fileContents(layout.projectDirectory.file("google-services.json")).asText.orNull
val firebaseConfig: Map<String, String>? = firebaseJson?.let { text ->
    val json = groovy.json.JsonSlurper().parseText(text) as Map<*, *>
    val project = json["project_info"] as Map<*, *>
    val client = (json["client"] as List<*>).map { it as Map<*, *> }.firstOrNull { c ->
        ((c["client_info"] as Map<*, *>)["android_client_info"] as Map<*, *>)["package_name"] == "ar.canastacerca"
    } ?: error("В app/google-services.json нет приложения ar.canastacerca: скачайте файл для этого пакета")
    val key = ((client["api_key"] as List<*>).first() as Map<*, *>)["current_key"].toString()
    buildMap {
        put("google_app_id", (client["client_info"] as Map<*, *>)["mobilesdk_app_id"].toString())
        put("gcm_defaultSenderId", project["project_number"].toString())
        put("google_api_key", key)
        put("google_crash_reporting_api_key", key)
        put("project_id", project["project_id"].toString())
        project["storage_bucket"]?.let { put("google_storage_bucket", it.toString()) }
    }
}

android {
    namespace = "ar.canastacerca"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "ar.canastacerca"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 6
        versionName = "0.3.3"

        // Ключ доступа: пока сайт закрыт (B80), сборки для тестеров и для проверки Google несут ключ
        buildConfigField("String", "CANASTA_ACCESS_KEY", "\"$accessKey\"")

        // Crashlytics: есть ли он в этой сборке (firebaseConfig выше) и его настройки
        buildConfigField("boolean", "CRASHLYTICS", (firebaseConfig != null).toString())
        firebaseConfig?.forEach { (name, value) -> resValue("string", name, value) }
        if (firebaseConfig != null) {
            // Build id, который иначе ставит плагин Crashlytics: без него SDK падает при запуске («build ID is missing»)
            resValue("string", "com.google.firebase.crashlytics.mapping_file_id", "none")
            resValue("string", "com.crashlytics.android.build_id", "android-$versionName-$versionCode")
        }
    }

    buildFeatures {
        buildConfig = true
        resValues = true
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
            // Отладочная сборка отчёты о падениях не шлёт
            manifestPlaceholders["crashlyticsCollection"] = "false"
        }
        release {
            buildConfigField("String", "CANASTA_BASE_URL", "\"${siteUrl.orElse("https://canastacerca.com/").get()}\"")
            manifestPlaceholders["usesCleartextTraffic"] = "false"
            manifestPlaceholders["crashlyticsCollection"] = "true"
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName(if (uploadStore != null) "upload" else "debug")
        }
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.webkit)
    implementation(libs.androidx.swiperefreshlayout)

    // Crashlytics — только с app/google-services.json (firebaseConfig выше)
    if (firebaseConfig != null) {
        implementation(platform(libs.firebase.bom))
        implementation(libs.firebase.crashlytics)
        // Google Play services внутри Firebase тянут androidx.fragment 1.0: без новой версии lint останавливает release
        // (InvalidFragmentVersionForActivityResult — запросы камеры и местоположения через registerForActivityResult)
        implementation(libs.androidx.fragment)
    }
}
