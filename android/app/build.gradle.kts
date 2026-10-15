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
        debug {
            // Pseudo-locales en-XA (long text) and ar-XB (mirrored) to catch i18n bugs early (I18N_SPEC §7).
            isPseudoLocalesEnabled = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Release signing is configured outside the repository (CONTRIBUTING §4).
        }
    }

    androidResources {
        // Generates locales_config.xml from res/values-<tag>/ folders (Android 13+ per-app language).
        generateLocaleConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.appcompat)
    implementation(projects.core.common)
    implementation(projects.core.i18n)
    implementation(projects.core.designSystem)

    testImplementation(libs.junit)
}
