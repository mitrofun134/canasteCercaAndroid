import ar.canastacerca.buildlogic.lib
import ar.canastacerca.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * :feature:x:api — публичная поверхность фичи: ключи навигации (NavKey) и контракты (интерфейсы, модели).
 * Без Compose и без DI: от api-модулей зависят impl-модули других фич.
 */
class FeatureApiConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("canasta.android.library")
            pluginManager.apply("canasta.serialization")

            dependencies {
                add("api", libs.lib("androidx-navigation3-runtime"))
                add("api", project(":core:model"))
            }
        }
    }
}
