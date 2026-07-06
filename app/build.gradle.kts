plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.tcohen.moviesapp"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.tcohen.moviesapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "com.tcohen.moviesapp.HiltTestRunner"

        // TMDB credentials — committed so anyone who clones the repo can run the app.
        // Both are public read-only and scoped only to TMDB data reads.
        buildConfigField("String", "TMDB_API_KEY", "\"b355446380d009699a7f3d386309528c\"")
        buildConfigField("String", "TMDB_READ_ACCESS_TOKEN", "\"eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJiMzU1NDQ2MzgwZDAwOTY5OWE3ZjNkMzg2MzA5NTI4YyIsIm5iZiI6MTc4MDQ3NjMwNS4wODYwMDAyLCJzdWIiOiI2YTFmZTk5MWMxYjZkMzYwM2I2NzYwNWYiLCJzY29wZXMiOlsiYXBpX3JlYWQiXSwidmVyc2lvbiI6MX0.x_nDchzUfxvVQ7ww2njVgOzst7zhJuCD186CMfP_7gc\"")
        buildConfigField("String", "TMDB_BASE_URL", "\"https://api.themoviedb.org/3/\"")
        buildConfigField("String", "TMDB_IMAGE_BASE_URL", "\"https://image.tmdb.org/t/p/\"")

        // Phase 0 — AI/LLM provider. Empty by default; drop in your Gemini key
        // from https://aistudio.google.com to enable. Without a key, every
        // LLM call fails fast with ApiError.UNAUTHORIZED and the rest of the
        // app continues to work normally.
        buildConfigField("String", "GEMINI_API_KEY", "\"\"")
        buildConfigField("String", "LLM_BASE_URL", "\"https://generativelanguage.googleapis.com/v1beta/openai/\"")
        buildConfigField("String", "LLM_DEFAULT_MODEL", "\"gemini-2.5-flash\"")

        // Server favorites backend.
        // SERVER_BASE_URL — the live Kotlin server. Override in local.properties
        // to point at a localhost:8080 instance during integration testing.
        // SERVER_SMOKE_TEST_ENABLED — flips the live-server FavoritesServerJourneyTest
        // on; default off so it never runs on CI without an explicit opt-in.
        buildConfigField("String",  "SERVER_BASE_URL",            "\"https://moviesapp-server-production.up.railway.app/\"")
        buildConfigField("Boolean", "SERVER_SMOKE_TEST_ENABLED",  "false")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // Allow Android framework classes (e.g. android.util.Log) to return default
            // values in unit tests instead of throwing "not mocked" RuntimeException.
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    // Network
    implementation(libs.retrofit)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.retrofit.kotlinx.serialization)

    // Image
    implementation(libs.coil.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Paging
    implementation(libs.androidx.paging.runtime)
    implementation(libs.androidx.paging.compose)

    // Media3 (embedded trailer player)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)

    // YouTube Player (trailer embedding)
    implementation(libs.youtube.player)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Encrypted shared preferences — Phase 2 of the server migration needs
    // Android Keystore-backed storage for the refresh-token password. The
    // access token itself stays in plain DataStore (short-lived).
    implementation(libs.androidx.security.crypto)

    // Debug tooling
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Unit tests
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.androidx.paging.testing)

    // Instrumented tests
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    // Hilt for full-app journey tests (ActivityScenario + real DI with fakes)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.android.compiler)
}
