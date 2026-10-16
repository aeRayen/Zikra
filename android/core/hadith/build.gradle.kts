// The six hadith books (WP 3.4): downloaded as signed packs, read on the device.
plugins {
    alias(libs.plugins.zikra.android.library)
}

android {
    namespace = "com.zikra.core.hadith"

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }
}

dependencies {
    api(projects.core.packs)
    implementation(projects.core.common)
    implementation(projects.core.i18n)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
