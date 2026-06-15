plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // TMDB credentials — committed so anyone who clones the repo can run the app.
        buildConfigField("String", "TMDB_API_KEY", "\"b355446380d009699a7f3d386309528c\"")
        buildConfigField("String", "TMDB_READ_ACCESS_TOKEN", "\"eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJiMzU1NDQ2MzgwZDAwOTY5OWE3ZjNkMzg2MzA5NTI4YyIsIm5iZiI6MTc4MDQ3NjMwNS4wODYwMDAyLCJzdWIiOiI2YTFmZTk5MWMxYjZkMzYwM2I2NzYwNWYiLCJzY29wZXMiOlsiYXBpX3JlYWQiXSwidmVyc2lvbiI6MX0.x_nDchzUfxvVQ7ww2njVgOzst7zhJuCD186CMfP_7gc\"")
        buildConfigField("String", "TMDB_BASE_URL", "\"https://api.themoviedb.org/3/\"")
        buildConfigField("String", "TMDB_IMAGE_BASE_URL", "\"https://image.tmdb.org/t/p/\"")

        // TMDB account credentials for favorites server sync.
        buildConfigField("String", "TMDB_ACCOUNT_ID", "\"me\"")
        buildConfigField("String", "TMDB_SESSION_ID", "\"\"")
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
            isReturnDefaultValues = true
        }
    }
}

dependencies {
    // Shared KMP module — contains all domain, data, shared UI, and androidMain code.
    implementation(project(":shared"))

    // ── Android Entry-Point ────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // ── Compose (minimal — entry-point only) ──────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)

    // ── Koin DI (Android host) ────────────────────────────────────────────────
    // koin-core and koin-compose-viewmodel come transitively from :shared
    // koin-android is needed directly here for startKoin + androidContext in Application
    implementation(libs.koin.android)

    // ── Networking (AppModule builds HttpClient here) ─────────────────────────
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.logging)
    implementation(libs.kotlinx.serialization.json)

    // ── Image loading (AppModule + MoviesApplication build ImageLoader) ────────
    implementation(libs.coil3.compose)
    implementation(libs.coil3.network.okhttp)

    // ── Coroutines ────────────────────────────────────────────────────────────
    implementation(libs.kotlinx.coroutines.android)

    // ── Debug tooling ─────────────────────────────────────────────────────────
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // ── Unit tests ────────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    // paging-runtime needed for test compilation (PagingData, Pager etc. from :shared impl scope)
    testImplementation(libs.androidx.paging.runtime)
    testImplementation(libs.androidx.paging.testing)

    // ── Instrumented tests ────────────────────────────────────────────────────
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}
