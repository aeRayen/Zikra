plugins {
    alias(libs.plugins.zikra.android.library)
    alias(libs.plugins.zikra.android.compose)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.zikra.core.designsystem"

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Single source of truth for colours (DESIGN_SYSTEM_TOKENS_AR.md); TokensSyncTest reads it.
                it.systemProperty(
                    "zikra.tokensFile",
                    rootProject.file("../docs/specs/design/tokens.json").absolutePath,
                )
            }
        }
    }
}

roborazzi {
    // Golden screenshots are committed and compared in CI (verifyRoborazziDebug).
    outputDir.set(file("src/test/screenshots"))
}

dependencies {
    implementation(projects.core.i18n)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}

// Screenshot tests are slow; render them once (debug variant) instead of twice.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }
}
