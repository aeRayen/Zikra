// Signed content packs on the device (WP 3.4b, CONTENT_PACKS_AR.md §3): download, verify, install.
plugins {
    alias(libs.plugins.zikra.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.zikra.core.packs"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(libs.kotlinx.serialization.json)
    // Ed25519 on every supported Android version (the platform has it only from Android 13).
    implementation(libs.bouncycastle.prov)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}
