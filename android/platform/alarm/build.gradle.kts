// Shared exact-alarm infrastructure (WP 2.4, ADR-033): adhan (2.5) and the smart alarm (Phase 5) use
// the same scheduler, reboot / direct-boot re-arming, time and time-zone handling, and fallback policy.
plugins {
    alias(libs.plugins.zikra.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.zikra.platform.alarm"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
