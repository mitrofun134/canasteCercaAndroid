package ar.canastacerca.buildlogic

import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

internal fun VersionCatalog.lib(alias: String): Provider<MinimalExternalModuleDependency> =
    findLibrary(alias).get()

internal fun VersionCatalog.intVersion(alias: String): Int =
    findVersion(alias).get().requiredVersion.toInt()

internal val JAVA_VERSION: JavaVersion = JavaVersion.VERSION_17

/** `:feature:offers:impl` → `ar.canastacerca.feature.offers.impl` */
internal fun Project.androidNamespace(): String =
    "ar.canastacerca." + path.removePrefix(":").replace(':', '.').replace('-', '_')

/**
 * AGP 9 компилирует Kotlin сам (built-in Kotlin), отдельный плагин kotlin-android не нужен.
 * Задачи компиляции — те же KotlinJvmCompile, поэтому настраиваем их напрямую.
 */
internal fun Project.configureKotlin() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }
}
