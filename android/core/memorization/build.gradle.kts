plugins {
    alias(libs.plugins.zikra.jvm.library)
}

// Memorization plan and spaced review (WP 3.9): pure logic, no Android, fully unit-tested.
dependencies {
    testImplementation(libs.junit)
}
