// Content pack builder (BUILD_PLAN WP 1.15, CONTENT_PACKS_AR.md). Runs on the JVM, never in the app.
// Usage: ./gradlew :tooling:content-pipeline:run --args="help"
plugins {
    alias(libs.plugins.zikra.jvm.library)
    alias(libs.plugins.kotlin.serialization)
    application
}

application {
    mainClass.set("com.zikra.tooling.content.MainKt")
}

// The CLI resolves relative paths (e.g. ../content) from the repository's android/ directory.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}

tasks.withType<Test>().configureEach {
    systemProperty("zikra.contentDir", rootProject.file("../content").absolutePath)
}

dependencies {
    // Same search normalization as the app: the index and the user's query must match (I18N_SPEC §10).
    implementation(projects.core.i18n)
    implementation(libs.sqlite.jdbc)
    implementation(libs.jsoup)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
