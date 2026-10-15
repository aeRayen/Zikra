plugins {
    alias(libs.plugins.zikra.android.application)
    alias(libs.plugins.zikra.android.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.zikra.app"

    defaultConfig {
        // PERMANENT after first Play upload (ADR-022). Guarded by ApplicationIdTest.
        applicationId = "com.zikra.app"
        versionCode = 1
        versionName = "0.1.0"

        // Public Supabase client values (protected by RLS), from env vars or the git-ignored ../.env.
        // Empty when absent: account features stay disabled and the app works as a guest.
        buildConfigField("String", "SUPABASE_URL", "\"${clientValue("SUPABASE_URL")}\"")
        buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${clientValue("SUPABASE_PUBLISHABLE_KEY")}\"")
        // E-mail code sign-in needs a custom SMTP provider on Supabase (ADR-029). Off unless explicitly enabled.
        buildConfigField("boolean", "EMAIL_SIGN_IN_ENABLED", clientValue("ZIKRA_EMAIL_SIGN_IN").ifBlank { "false" })
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

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

// UI tests need the debug-only test activity manifest; release adds nothing for unit tests.
androidComponents {
    beforeVariants(selector().withBuildType("release")) { it.enableUnitTest = false }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.compose.material3.window.size)
    implementation(libs.compose.material.icons.core)
    implementation(projects.core.common)
    implementation(projects.core.i18n)
    implementation(projects.core.designSystem)
    implementation(projects.core.network)
    implementation(projects.core.places)
    implementation(projects.core.prayer)
    implementation(projects.core.settings)
    implementation(projects.platform.alarm)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.compose.ui.test.manifest)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}

/** Reads a public client value from the environment, then from the repository's .env file. */
fun clientValue(name: String): String {
    System.getenv(name)?.takeIf { it.isNotBlank() }?.let { return it }
    val envFile = rootProject.file("../.env")
    if (!envFile.isFile) return ""
    return envFile.readLines()
        .map { it.trim() }
        .firstOrNull { it.startsWith("$name=") }
        ?.substringAfter('=')
        ?.trim()
        .orEmpty()
}
