import com.zikra.buildlogic.GeneratePlacesDatabase

// Offline city database for the manual location picker (WP 2.1, ADR-018).
// places.sqlite is GENERATED at build time from the pinned GeoNames sources in ../content/sources
// by the content pipeline, so the repository holds no generated binary and every build is reproducible.
plugins {
    alias(libs.plugins.zikra.android.library)
}

android {
    namespace = "com.zikra.core.places"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

val placesTool: Configuration by configurations.creating {
    isCanBeConsumed = false
}

val generatePlaces = tasks.register<GeneratePlacesDatabase>("generatePlacesDatabase") {
    description = "Builds places.sqlite from the pinned GeoNames sources."
    classpath = placesTool
    // The whole sources dir is the input: the lock file pins every file's SHA-256.
    sourcesDir.set(rootProject.layout.projectDirectory.dir("../content/sources"))
    outputDir.set(layout.buildDirectory.dir("generated/places-assets"))
}

androidComponents {
    onVariants { variant ->
        variant.sources.assets?.addGeneratedSourceDirectory(generatePlaces, GeneratePlacesDatabase::outputDir)
    }
    beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }
}

dependencies {
    placesTool(projects.tooling.contentPipeline)

    // Queries are normalized exactly like the index (I18N_SPEC §10).
    implementation(projects.core.i18n)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
