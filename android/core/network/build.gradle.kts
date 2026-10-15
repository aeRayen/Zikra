plugins {
    alias(libs.plugins.zikra.jvm.library)
}

dependencies {
    api(projects.core.common)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
