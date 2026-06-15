plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.sqldelight)
    // Compose Compiler plugin (Kotlin 2.x)
    alias(libs.plugins.kotlin.compose)
    // JetBrains Compose Multiplatform runtime + accessors
    alias(libs.plugins.compose.multiplatform)
}

kotlin {
    // ── Android ───────────────────────────────────────────────────────────────
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    // ── iOS ───────────────────────────────────────────────────────────────────
    // Each target exposes a static framework named "shared" so that the Xcode
    // project can use embedAndSignAppleFrameworkForXcode to embed it.
    listOf(iosX64(), iosArm64(), iosSimulatorArm64()).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

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
            implementation(compose.materialIconsExtended)
            // Coil 3 — KMP image loading
            implementation(libs.coil3.compose)
            // Koin — KMP-compatible DI
            implementation(libs.koin.core)
            implementation(libs.koin.compose.viewmodel)
            // CashApp Multiplatform Paging — PagingData/PagingSource/Pager in commonMain
            implementation(libs.cashapp.paging.common)
            // CashApp Paging Compose — collectAsLazyPagingItems / LazyPagingItems in commonMain
            implementation(libs.cashapp.paging.compose.common)
            // JetBrains lifecycle KMP — ViewModel + viewModelScope + collectAsStateWithLifecycle
            implementation(libs.jetbrains.lifecycle.viewmodel)
            implementation(libs.jetbrains.lifecycle.viewmodel.compose)
            // JetBrains navigation KMP — NavHost, composable, rememberNavController in commonMain
            implementation(libs.jetbrains.navigation.compose)
        }

        // ── Android ───────────────────────────────────────────────────────────
        androidMain.dependencies {
            // SQLDelight Android SQLite driver
            implementation(libs.sqldelight.android.driver)
            // Coil 3 OkHttp network fetcher (Android-only networking engine)
            implementation(libs.coil3.network.okhttp)
            // Paging — runtime for cachedIn/PagingData; compose for collectAsLazyPagingItems
            implementation(libs.androidx.paging.runtime)
            implementation(libs.androidx.paging.compose)
            // Lifecycle — LocalLifecycleOwner for TrailerPlayerSection
            implementation(libs.androidx.lifecycle.runtime.compose)
            // YouTube player — for TrailerPlayerSection
            implementation(libs.youtube.player)
            // Koin Android — androidContext() helper for Koin modules in androidMain
            implementation(libs.koin.android)
        }

        // ── iOS ───────────────────────────────────────────────────────────────
        iosMain.dependencies {
            // Ktor Darwin (URLSession-based) HTTP engine for iOS/macOS
            implementation(libs.ktor.client.darwin)
            // SQLDelight native SQLite driver for iOS
            implementation(libs.sqldelight.native.driver)
            // Coil 3 — Ktor-based network fetcher for iOS (no OkHttp available)
            implementation(libs.coil3.network.ktor)
        }

        // ── Tests ─────────────────────────────────────────────────────────────
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

sqldelight {
    databases {
        create("MoviesDatabase") {
            packageName.set("com.tcohen.moviesapp.data.local.db")
        }
    }
}

// Disable the SQLDelight migration-verification tasks — we have no .sqm migration files
// and the verifier requires the native sqlitejdbc library which is not bundled on Windows.
afterEvaluate {
    tasks.matching { task ->
        task.name.startsWith("verify") && task.name.contains("MoviesDatabaseMigration")
    }.configureEach {
        enabled = false
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
