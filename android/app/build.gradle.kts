plugins {
    alias(libs.plugins.zikra.android.application)
    alias(libs.plugins.zikra.android.compose)
}

android {
    namespace = "com.zikra.app"

    defaultConfig {
        // PERMANENT after first Play upload (ADR-022). Guarded by ApplicationIdTest.
        applicationId = "com.zikra.app"
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Release signing is configured outside the repository (CONTRIBUTING §4).
        }
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    testImplementation(libs.junit)
}
