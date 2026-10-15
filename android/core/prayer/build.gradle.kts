plugins {
    alias(libs.plugins.zikra.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    // Batoul Apps Adhan (MIT): high-precision prayer time astronomy. 0.0.5 is the newest release
    // compatible with our Kotlin 2.0 toolchain; upgrade together with Kotlin (dependency package).
    api(libs.adhan)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.serialization.json)
}
