plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.androidx.baselineprofile)
}

android {
    namespace = "com.example.carheadunit"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.carheadunit"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        // The app is English-only: strip every other locale's resources for
        // a smaller APK and lighter resource loading on weak units.
        resourceConfigurations += listOf("en")
    }

    buildTypes {
        release {
            // Personal-use shortcut: sign release with the debug keystore.
            // Not for Play Store distribution — updates must share a key.
            signingConfig = signingConfigs.getByName("debug")
            // R8 shrinking is a solid win on low-end head units: smaller code,
            // less memory pressure. Compose keep-rules are applied by AGP.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        compose = true
    }
    // Compose is compiled by the org.jetbrains.kotlin.plugin.compose Gradle
    // plugin (Kotlin 2.0.x): strong skipping mode is on by default, which makes
    // composables with unstable params (List<AppEntry>, Set<String>…) skippable —
    // the biggest remaining recomposition win for the drawer on weak SoCs.
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    // Real org.json for host JVM tests of TelemetryApi (runtime uses Android's built-in).
    testImplementation("org.json:json:20240303")
    // Baseline profiles: ART pre-compiles the app's hot paths on install, so
    // cold start isn't fully interpreted on slow head-unit SoCs.
    implementation("androidx.profileinstaller:profileinstaller:1.3.1")
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}