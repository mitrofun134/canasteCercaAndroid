import ar.canastacerca.buildlogic.JAVA_VERSION
import ar.canastacerca.buildlogic.configureKotlin
import ar.canastacerca.buildlogic.intVersion
import ar.canastacerca.buildlogic.lib
import ar.canastacerca.buildlogic.libs
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")

            extensions.configure<ApplicationExtension> {
                compileSdk = libs.intVersion("compileSdk")
                defaultConfig {
                    minSdk = libs.intVersion("minSdk")
                    targetSdk = libs.intVersion("targetSdk")
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
