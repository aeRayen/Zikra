plugins {
    alias(libs.plugins.zikra.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.zikra.core.database"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Exported schemas are committed: they are the migration contract (LOCAL_SCHEMA_AR.md).
    sourceSets {
        getByName("test").assets.srcDir("$projectDir/schemas")
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }
}

dependencies {
    api(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.androidx.sqlite.ktx)
    implementation(libs.sqlcipher.android)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
