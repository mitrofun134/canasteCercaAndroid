import ar.canastacerca.buildlogic.lib
import ar.canastacerca.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/**
 * :feature:x:impl — экраны, ViewModel, репозитории и вклад фичи в граф Metro
 * (ViewModel в map, EntryProviderInstaller и вкладки в set). Зависит только от api-модулей других фич.
 */
class FeatureImplConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("canasta.android.library")
            pluginManager.apply("canasta.android.compose")
            pluginManager.apply("canasta.metro")
            pluginManager.apply("canasta.serialization")

            dependencies {
                add("implementation", project(":core:common"))
                add("implementation", project(":core:model"))
                add("implementation", project(":core:designsystem"))
                add("implementation", project(":core:navigation"))
                add("implementation", project(":core:network:api"))

                add("implementation", libs.lib("androidx-navigation3-runtime"))
                add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
                add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
                add("implementation", libs.lib("metrox-viewmodel-compose"))
                add("implementation", libs.lib("coil-compose"))
            }
        }
    }
}
