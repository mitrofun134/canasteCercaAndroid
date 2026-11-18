import ar.canastacerca.buildlogic.JAVA_VERSION
import ar.canastacerca.buildlogic.androidNamespace
import ar.canastacerca.buildlogic.configureKotlin
import ar.canastacerca.buildlogic.intVersion
import ar.canastacerca.buildlogic.lib
import ar.canastacerca.buildlogic.libs
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                namespace = androidNamespace()
                compileSdk = libs.intVersion("compileSdk")
                defaultConfig {
                    minSdk = libs.intVersion("minSdk")
                }
                compileOptions {
                    sourceCompatibility = JAVA_VERSION
                    targetCompatibility = JAVA_VERSION
                }
            }
            configureKotlin()

            dependencies {
                add("implementation", libs.lib("kotlinx-coroutines-android"))
            }
        }
    }
}
