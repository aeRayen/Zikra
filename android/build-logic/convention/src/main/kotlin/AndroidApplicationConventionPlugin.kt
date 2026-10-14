import com.android.build.api.dsl.ApplicationExtension
import com.zikra.buildlogic.configureKotlinAndroid
import com.zikra.buildlogic.libs
import com.zikra.buildlogic.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("zikra.detekt")

            extensions.configure<ApplicationExtension> {
                configureKotlinAndroid(this)
                defaultConfig.targetSdk = libs.version("targetSdk").toInt()
                buildFeatures.buildConfig = true
            }
        }
    }
}
