import com.zikra.buildlogic.GenerateBundledDatabase
import org.gradle.api.tasks.PathSensitivity

// Adhkar bundled in the APK (WP 2.14b). adhkar.sqlite is GENERATED at build time from the pinned
// Hisn al-Muslim texts, the author's verified references and the Tanzil text of quoted verses
// (content/sources, SHA-256 checked); the build fails if any reference or verse is not verified.
plugins {
    alias(libs.plugins.zikra.android.library)
}

android {
    namespace = "com.zikra.core.adhkar"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

val contentTool: Configuration by configurations.creating {
    isCanBeConsumed = false
}

val generateAdhkar = tasks.register<GenerateBundledDatabase>("generateAdhkarDatabase") {
    description = "Builds adhkar.sqlite from the pinned Hisn al-Muslim, Shamela and Tanzil sources."
    classpath = contentTool
    sourcesDir.set(rootProject.layout.projectDirectory.dir("../content/sources"))
    command.set("adhkar-bundle")
    fileName.set("adhkar.sqlite")
    outputDir.set(layout.buildDirectory.dir("generated/adhkar-assets"))
    // The manual verdicts of the independent check (ADR-042) decide whether the build may proceed.
    inputs.dir(rootProject.layout.projectDirectory.dir("../content/review"))
        .withPathSensitivity(PathSensitivity.RELATIVE)
        .withPropertyName("manualReview")
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(generateAdhkar, GenerateBundledDatabase::outputDir)
    }
    beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }
}

tasks.withType<Test>().configureEach {
    // Tests compare the displayed text with the pinned source files.
    systemProperty("zikra.contentDir", rootProject.file("../content").absolutePath)
}

dependencies {
    contentTool(projects.tooling.contentPipeline)

    // Bundled-database opening, and the Quran font for quoted verses.
    api(projects.core.quran)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
