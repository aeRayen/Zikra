// Typed, atomic on-device settings (LOCAL_SCHEMA_AR.md §settings, ADR-031).
plugins {
    alias(libs.plugins.zikra.jvm.library)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(libs.androidx.datastore.core)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
