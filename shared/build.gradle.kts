plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    // Compose Compiler plugin (Kotlin 2.x)
    alias(libs.plugins.kotlin.compose)
    // JetBrains Compose Multiplatform runtime + accessors
    alias(libs.plugins.compose.multiplatform)
    // Hilt — processes @HiltViewModel / @Inject in androidMain
    alias(libs.plugins.hilt)
    // KSP — needed to run Hilt's annotation processor for the Android target
    alias(libs.plugins.ksp)
}

kotlin {
    // ── Android ───────────────────────────────────────────────────────────────
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // ── iOS ───────────────────────────────────────────────────────────────────
    // All three targets share the `iosMain` source set (created automatically
    // by KMP's default hierarchy). Compilation to native binaries requires macOS.
    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        // ── Common ────────────────────────────────────────────────────────────
        commonMain.dependencies {
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // Ktor — common HTTP client, content negotiation, and JSON serialization
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
            // SQLDelight — reactive Flow extensions (common)
            implementation(libs.sqldelight.coroutines.extensions)
            // Compose Multiplatform — shared UI
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            // Coil 3 — KMP image loading
            implementation(libs.coil3.compose)
        }

        // ── Android ───────────────────────────────────────────────────────────
        androidMain.dependencies {
            // SQLDelight Android SQLite driver
            implementation(libs.sqldelight.android.driver)
            // Material Icons Extended — via CMP accessor (resolves correct version per platform)
            implementation(compose.materialIconsExtended)
            // Coil 3 OkHttp network fetcher (Android-only networking engine)
            implementation(libs.coil3.network.okhttp)
            // Navigation Compose — for Screen, BottomNavBar, AppNavGraph
            implementation(libs.androidx.navigation.compose)
            // Paging — runtime for cachedIn/PagingData; compose for collectAsLazyPagingItems
            implementation(libs.androidx.paging.runtime)
            implementation(libs.androidx.paging.compose)
            // Lifecycle — ViewModel, collectAsStateWithLifecycle, LocalLifecycleOwner
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
            // YouTube player — for TrailerPlayerSection
            implementation(libs.youtube.player)
            // Hilt — DI runtime + Navigation-Compose integration
            implementation(libs.hilt.android)
            implementation(libs.androidx.hilt.navigation.compose)
        }

        // ── iOS ───────────────────────────────────────────────────────────────
        // `iosMain` is the shared parent of iosX64Main, iosArm64Main, iosSimulatorArm64Main.
        // It is automatically created by KMP's default hierarchy template.
        iosMain.dependencies {
            // Ktor Darwin (URLSession-based) HTTP engine for iOS/macOS
            implementation(libs.ktor.client.darwin)
            // SQLDelight native SQLite driver for iOS
            implementation(libs.sqldelight.native.driver)
        }

        // ── Tests ─────────────────────────────────────────────────────────────
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// Hilt's KSP processor for the Android target.
// MUST be declared after kotlin {} so that the 'kspAndroid' configuration exists.
dependencies {
    add("kspAndroid", libs.hilt.android.compiler)
}

sqldelight {
    databases {
        create("MoviesDatabase") {
            // Generated MoviesDatabase class will live in this package.
            packageName.set("com.tcohen.moviesapp.data.local.db")
        }
    }
}

android {
    namespace = "com.tcohen.moviesapp.shared"
    compileSdk = 35

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
