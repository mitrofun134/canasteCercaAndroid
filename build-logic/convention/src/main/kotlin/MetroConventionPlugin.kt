import org.gradle.api.Plugin
import org.gradle.api.Project

/** Metro — DI на компиляторном плагине Kotlin, без kapt/ksp. Runtime-артефакт плагин добавляет сам. */
class MetroConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.pluginManager.apply("dev.zacsweers.metro")
    }
}
