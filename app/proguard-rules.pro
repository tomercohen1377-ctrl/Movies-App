# ── kotlinx.serialization ─────────────────────────────────────────────────────
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class **$$serializer { *; }
-keep @kotlinx.serialization.Serializable class * { *; }

# ── Ktor ──────────────────────────────────────────────────────────────────────
-dontwarn io.ktor.**
-keep class io.ktor.** { *; }

# ── OkHttp / Okio ─────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**

# ── SLF4J (used by Ktor logging — no Android implementation needed at runtime) ─
-dontwarn org.slf4j.**
-keep class org.slf4j.** { *; }

# ── Coil 3 ────────────────────────────────────────────────────────────────────
-dontwarn coil3.**

# ── Koin ──────────────────────────────────────────────────────────────────────
-keep class org.koin.** { *; }
-dontwarn org.koin.**

# ── Compose ─────────────────────────────────��─────────────────────────────────
-keep class androidx.compose.** { *; }

# ── Domain / DTO models (needed for Kotlinx serialization) ────────────────────
-keep class com.tcohen.moviesapp.data.remote.dto.** { *; }
-keep class com.tcohen.moviesapp.domain.model.** { *; }

# ── SQLDelight ────────────────────────────────────────────────────────────────
-dontwarn app.cash.sqldelight.**
