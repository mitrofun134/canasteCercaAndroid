import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

group = "ar.canastacerca.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("androidApplication") {
            id = libs.plugins.canasta.android.application.get().pluginId
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLibrary") {
            id = libs.plugins.canasta.android.library.get().pluginId
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidCompose") {
            id = libs.plugins.canasta.android.compose.get().pluginId
            implementationClass = "AndroidComposeConventionPlugin"
        }
        register("metro") {
            id = libs.plugins.canasta.metro.get().pluginId
            implementationClass = "MetroConventionPlugin"
        }
        register("serialization") {
            id = libs.plugins.canasta.serialization.get().pluginId
            implementationClass = "SerializationConventionPlugin"
        }
        register("featureApi") {
            id = libs.plugins.canasta.feature.api.get().pluginId
            implementationClass = "FeatureApiConventionPlugin"
        }
        register("featureImpl") {
            id = libs.plugins.canasta.feature.impl.get().pluginId
            implementationClass = "FeatureImplConventionPlugin"
        }
    }
}
