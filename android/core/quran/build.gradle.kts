import com.zikra.buildlogic.GenerateBundledDatabase

// Quran text bundled in the APK (WP 2.9). quran.sqlite is GENERATED at build time from the pinned
// Tanzil sources (content/sources, SHA-256 checked) by the content pipeline: the same checks as the
// signed pack (114 surahs, 6236 ayahs, structure), and no generated binary in git.
plugins {
    alias(libs.plugins.zikra.android.library)
}

android {
    namespace = "com.zikra.core.quran"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

val contentTool: Configuration by configurations.creating {
    isCanBeConsumed = false
}

val generateQuran = tasks.register<GenerateBundledDatabase>("generateQuranDatabase") {
    description = "Builds quran.sqlite from the pinned Tanzil sources."
    classpath = contentTool
    sourcesDir.set(rootProject.layout.projectDirectory.dir("../content/sources"))
    command.set("quran-bundle")
    fileName.set("quran.sqlite")
    outputDir.set(layout.buildDirectory.dir("generated/quran-assets"))
}

val generateTranslation = tasks.register<GenerateBundledDatabase>("generateTranslationDatabase") {
    description = "Builds translation-en.sqlite (Saheeh International via QuranEnc) from the pinned source."
    classpath = contentTool
    sourcesDir.set(rootProject.layout.projectDirectory.dir("../content/sources"))
    command.set("translation-bundle")
    fileName.set("translation-en.sqlite")
    outputDir.set(layout.buildDirectory.dir("generated/translation-assets"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(generateQuran, GenerateBundledDatabase::outputDir)
        variant.sources.assets?.addGeneratedSourceDirectory(generateTranslation, GenerateBundledDatabase::outputDir)
    }
    beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }
}

tasks.withType<Test>().configureEach {
    // Tests compare the displayed text with the pinned source file, character by character.
    systemProperty("zikra.contentDir", rootProject.file("../content").absolutePath)
}

dependencies {
    contentTool(projects.tooling.contentPipeline)

    // Queries are normalized exactly like the index (I18N_SPEC §10).
    implementation(projects.core.i18n)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
