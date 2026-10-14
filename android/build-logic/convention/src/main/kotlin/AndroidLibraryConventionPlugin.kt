import com.android.build.api.dsl.LibraryExtension
import com.zikra.buildlogic.configureKotlinAndroid
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("zikra.detekt")

            extensions.configure<LibraryExtension> {
                configureKotlinAndroid(this)
                // Each library declares its own namespace: com.zikra.<group>.<name>
            }
        }
    }
}
