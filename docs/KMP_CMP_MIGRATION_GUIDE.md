# KMP + Compose Multiplatform Migration Guide

## Goal

Convert the current Android-only Movies App into a Kotlin Multiplatform (KMP) and Compose Multiplatform (CMP) project while keeping the existing Android app safe and runnable during the whole migration.

The recommended approach is gradual migration, not a full rewrite.

## Current Project Snapshot

The current project is an Android application with one main module:

```text
Movies-App/
  app/
  docs/
  gradle/
  build.gradle.kts
  settings.gradle.kts
  gradle.properties
```

Current Android module characteristics:

- Single Android app module: `:app`
- Kotlin version: `2.0.21`
- Android Gradle Plugin: `8.7.0`
- Jetpack Compose Android UI
- Hilt dependency injection
- Retrofit + OkHttp networking
- Kotlinx Serialization
- Room local database
- AndroidX Paging
- Coil image loading
- Media3 / YouTube player for trailers
- Android-specific navigation and tests

## Main Principle

Do not delete or replace the existing `app` module at the beginning.

Instead:

1. Preserve the existing Android app.
2. Create a migration branch.
3. Add KMP/CMP modules next to the current app.
4. Move code gradually into shared modules.
5. Keep Android building after each phase.
6. Only remove old Android-only code after feature parity is reached.

## Can the Current Project Be Saved?

Yes.

The safest strategy is to keep the current Android app as the stable reference implementation while building the multiplatform version alongside it.

Recommended protection steps:

```bash
git status
git add .
git commit -m "Save Android app before KMP migration"
git tag android-before-kmp
git checkout -b kmp-compose-migration
```

If something goes wrong, the project can return to the pre-migration state using the branch or tag.

## Recommended Final Structure

A practical target structure is:

```text
Movies-App/
  app/                         Existing Android app, kept during migration
  composeApp/                  New Compose Multiplatform application entry point
  shared/                      Shared KMP business/data/domain code
  docs/
  gradle/
  build.gradle.kts
  settings.gradle.kts
```

Alternative structure:

```text
Movies-App/
  androidApp/                  Android host app
  shared/                      Shared KMP module including shared Compose UI
  iosApp/                      iOS app host, created later
  desktopApp/                  Optional desktop host
```

For this project, the first structure is safer because it avoids breaking the current `app` module immediately.

## Migration Strategy Overview

The migration should happen in phases:

1. Preparation and safety
2. Gradle/plugin setup
3. Add a KMP shared module
4. Move pure domain code
5. Move DTOs, mappers, and utility code
6. Replace networking with KMP-compatible networking
7. Replace or adapt local database
8. Replace or adapt dependency injection
9. Move shared state/view-model logic
10. Move Compose UI to CMP
11. Add platform targets gradually
12. Testing and validation
13. Cleanup and final module consolidation

## Phase 0: Preparation and Safety

### Goals

- Save the current Android project.
- Create a safe migration branch.
- Make sure the app builds before migration starts.
- Define target platforms.

### Tasks

- Commit all current work.
- Create a tag such as `android-before-kmp`.
- Create a branch such as `kmp-compose-migration`.
- Run current Android build and tests.
- Decide initial KMP targets:
  - Android first
  - Desktop second, optional but useful for fast UI validation
  - iOS later if needed

### Recommended Validation

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

On Windows PowerShell:

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
```

### Guidelines

- Do not rename `:app` yet.
- Do not delete Hilt, Room, Retrofit, or Android-only tests yet.
- Keep the current app as the behavior reference.

## Phase 1: Gradle and Version Catalog Preparation

### Goals

Prepare the build system for KMP and CMP without changing app behavior.

### Tasks

Add version catalog entries for:

- Kotlin Multiplatform plugin
- Compose Multiplatform plugin
- Android library plugin
- Ktor client
- Kotlinx Coroutines core
- Kotlinx Serialization
- Multiplatform lifecycle/viewmodel support if used
- Multiplatform image loading library if selected
- SQLDelight or Room KMP if selected

Potential plugins:

```toml
[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
android-library = { id = "com.android.library", version.ref = "agp" }
compose-multiplatform = { id = "org.jetbrains.compose", version = "<compose-multiplatform-version>" }
```

### Guidelines

- Keep existing Android Compose dependencies for the current `:app` module.
- Do not convert everything in one Gradle edit.
- Prefer small Gradle changes followed by sync/build.
- Use versions compatible with Kotlin `2.0.21`, or upgrade Kotlin intentionally in a separate step.

## Phase 2: Add a `shared` KMP Module

### Goals

Create a KMP module where reusable non-UI code can move first.

### Proposed module:

```text
shared/
  build.gradle.kts
  src/
    commonMain/kotlin/
    commonTest/kotlin/
    androidMain/kotlin/
    androidUnitTest/kotlin/
```

Initial targets:

- Android target only at first
- Add JVM/Desktop or iOS only after the shared module is stable

### Initial shared module responsibility

Start with code that has no Android dependencies:

- Domain models
- Repository interfaces
- Basic utilities
- Constants
- Result wrappers
- Error models
- Pure mappers
- Pure business rules

Good first candidates from this project:

- `domain/model`
- `domain/repository`
- `util/NetworkResult.kt`
- `util/ApiError.kt`
- `util/TmdbImageUrl.kt`
- Some mapper code from `data/mapper`

### Avoid moving at first

- Hilt modules
- Room database/DAO/entities if still Android Room-only
- Retrofit API interfaces
- Android network monitor
- Android `ViewModel` classes if they use Hilt or AndroidX directly
- Media3 / YouTube player UI
- Android navigation graph

### Guidelines

- Move one package at a time.
- After each move, update imports and build.
- If code requires `android.*`, `androidx.*`, or Hilt, keep it in Android code for now.

## Phase 3: Move Domain Layer

### Goals

Move domain code into `shared/commonMain`.

### Likely shared packages

```text
shared/src/commonMain/kotlin/com/tcohen/moviesapp/domain/model/
shared/src/commonMain/kotlin/com/tcohen/moviesapp/domain/repository/
```

### Tasks

- Move movie domain models.
- Move category/domain extensions if they are pure Kotlin.
- Move repository interfaces.
- Update `:app` to depend on `:shared`.
- Remove duplicate domain classes from `:app` only after imports are updated.

### Guidelines

- Domain models should not depend on Android classes.
- Prefer immutable data classes.
- Keep serialization annotations only if they are truly needed in common code.
- Keep API DTOs separate from domain models.

## Phase 4: Move DTOs, Mappers, and Pure Utilities

### Goals

Move data structures that are platform-independent.

### Good candidates

- Remote DTO data classes using Kotlinx Serialization
- Movie mapper functions that do not depend on Android or Room
- TMDB image URL builder
- API error models
- Network result wrapper

### Tasks

- Move DTOs into `shared/commonMain`.
- Move pure mappers into `shared/commonMain`.
- Keep Android database entity mappers in Android code until database strategy is decided.

### Guidelines

- DTOs should use `kotlinx.serialization`, not Gson/Moshi Android-specific setup.
- Do not leak Retrofit annotations into common code.
- If a DTO currently depends on Retrofit annotations, replace that with Ktor request code later.

## Phase 5: Networking Migration

### Current State

The project currently uses:

- Retrofit
- OkHttp
- Kotlinx Serialization converter
- Auth interceptor
- Safe API call abstraction

Retrofit is not suitable for shared iOS/common KMP networking.

### Recommended KMP Replacement

Use Ktor Client:

- `ktor-client-core` in `commonMain`
- `ktor-client-okhttp` in `androidMain`
- `ktor-client-darwin` in `iosMain`
- `ktor-client-content-negotiation`
- `ktor-serialization-kotlinx-json`
- `ktor-client-logging`

### Proposed shared networking structure

```text
shared/src/commonMain/kotlin/com/tcohen/moviesapp/data/remote/
  TmdbClient.kt
  TmdbEndpoints.kt
  dto/
```

Platform-specific engines:

```text
shared/src/androidMain/kotlin/.../HttpClientEngineFactory.android.kt
shared/src/iosMain/kotlin/.../HttpClientEngineFactory.ios.kt
```

### Tasks

- Create a Ktor `HttpClient` provider.
- Replace Retrofit `TmdbApiService` with a Ktor-based client.
- Move API call logic to common code.
- Keep existing Retrofit implementation in `:app` temporarily if needed.
- Compare behavior between Retrofit and Ktor using tests.

### Guidelines

- Do not migrate networking and database in the same phase.
- Keep API credentials out of common source when possible.
- Prefer injecting config values such as base URL, image base URL, and token.
- Use platform-specific configuration for secrets and environment values.

## Phase 6: Local Database Strategy

### Current State

The project currently uses Android Room.

### Options

#### Option A: Room KMP

Room has multiplatform support in newer versions. This can be considered if the project stays close to AndroidX APIs.

Pros:

- Familiar API
- Easier migration from current Room code

Cons:

- KMP setup can be more restrictive
- iOS/Desktop support needs careful validation

#### Option B: SQLDelight

SQLDelight is a mature KMP database solution.

Pros:

- Strong KMP support
- Explicit schema
- Good iOS support

Cons:

- Requires rewriting DAO/entity layer
- Different development style from Room

#### Option C: Keep Room Android-only temporarily

Pros:

- Lowest risk
- Lets networking/domain/UI migration continue first

Cons:

- Shared repository remains limited
- iOS/Desktop cannot use local cache yet

### Recommended Approach

For this project, keep Room Android-only during early migration. After domain and networking are stable in `shared`, decide between Room KMP and SQLDelight.

### Guidelines

- Do not move `AppDatabase`, DAOs, or Room entities to common code immediately.
- Introduce a repository interface in common code.
- Provide Android implementation using Room first.
- Add iOS/Desktop storage later.

## Phase 7: Dependency Injection Strategy

### Current State

The project currently uses Hilt.

Hilt is Android-only and should not be used in common KMP code.

### Options

#### Option A: Manual dependency injection

Recommended for the early migration.

Pros:

- Simple
- No extra framework
- Easy to understand during migration

Cons:

- More boilerplate as the app grows

#### Option B: Koin Multiplatform

Pros:

- KMP-compatible
- Easy setup

Cons:

- Runtime DI
- Adds framework dependency

#### Option C: Keep Hilt in Android host only

Pros:

- Keeps Android app stable
- Allows gradual migration

Cons:

- Common code cannot use Hilt annotations

### Recommended Approach

Use Hilt only in the Android app during the migration. Keep shared code constructor-injected and framework-free. Later, decide whether to adopt Koin or keep manual DI.

### Guidelines

- Do not put `@Inject`, `@HiltViewModel`, or Hilt modules in `commonMain`.
- Prefer constructors with explicit dependencies.
- Keep platform wiring at the application boundary.

## Phase 8: Shared State and ViewModel Migration

### Current State

The project has Android presentation classes such as:

- `HomeViewModel`
- `FavoritesViewModel`
- `MovieDetailViewModel`
- UI contracts and state classes

### Goals

Move state models and business presentation logic to shared code where possible.

### Good candidates

- UI state data classes
- UI event sealed classes
- UI effect sealed classes
- Reducer-style logic
- Use cases
- Paging-independent state transformations

### Needs caution

- AndroidX `ViewModel`
- Hilt annotations
- AndroidX Paging APIs
- Android lifecycle APIs

### Options for shared view models

- Use AndroidX Lifecycle ViewModel Multiplatform if compatible with selected versions.
- Use plain Kotlin presenter/state-holder classes in common code.
- Keep Android ViewModels as wrappers around shared interactors.

### Recommended Approach

Start by moving contracts/state classes into common code. Keep Android `ViewModel` wrappers in `:app` until dependencies are KMP-safe.

### Guidelines

- Avoid Android lifecycle types in common code.
- Use `StateFlow` and `SharedFlow` for state/events.
- Inject repositories/use cases via constructors.
- Keep paging strategy separate until KMP paging support is selected.

## Phase 9: Compose Multiplatform UI Migration

### Current State

The app already uses Jetpack Compose, which helps migration, but not all Android Compose APIs are available in CMP.

### Likely reusable UI

- Pure composables that use Material3, layout, text, buttons, rows, columns, lazy grids/lists
- Theme colors and typography if they do not depend on Android resources
- UI components such as cards, badges, filters, error views, and banners

### Needs adaptation

- Android-only previews
- Android resources such as `R.drawable`, `R.string`, etc.
- Android-specific navigation
- Coil 2 Android image loading
- Media3 / YouTube player
- Context usage
- Toasts, intents, permissions

### Recommended CMP libraries

- Compose Multiplatform Material3
- Compose Resources for shared strings/images
- Coil 3 for multiplatform image loading, or another KMP image library
- Compose Navigation Multiplatform or a simple custom navigation state

### Migration order

1. Theme
2. Small common components
3. Movie cards and badges
4. Home screen content without navigation
5. Favorites screen content
6. Movie detail content
7. Navigation shell
8. Platform-specific trailer player

### Guidelines

- Move UI composables only after their models are available in shared code.
- Keep platform-specific composables behind `expect/actual` or interfaces.
- Avoid direct Android context access in shared UI.
- Replace Android resources with Compose Multiplatform resources.

## Phase 10: Navigation Migration

### Current State

The project uses AndroidX Navigation Compose.

### Options

#### Option A: Keep AndroidX Navigation in Android app

Good during migration.

#### Option B: Use Compose Navigation Multiplatform

Useful if the same navigation graph should run on Android, Desktop, and iOS.

#### Option C: Use custom navigation state

Simple and effective for smaller apps.

### Recommended Approach

Keep AndroidX Navigation in `:app` until screens are shared. Then introduce a shared navigation abstraction or CMP navigation library.

### Guidelines

- Keep route definitions platform-neutral.
- Avoid passing Android `Bundle`, `Parcelable`, or `NavController` into common UI.
- Shared screens should receive callbacks like `onMovieClick(movieId)` instead of navigating directly.

## Phase 11: Trailer Player Strategy

### Current State

The app uses Android-specific video/trailer dependencies:

- Media3
- Android YouTube player library

These are not common KMP UI components.

### Recommended Approach

Use platform-specific trailer players.

Common API example:

```kotlin
@Composable
expect fun TrailerPlayer(videoKey: String, modifier: Modifier = Modifier)
```

Android implementation can use Media3 or the current YouTube player.

iOS/Desktop implementations can be added later.

### Guidelines

- Do not block the whole migration on trailer playback.
- First migrate the detail screen with a placeholder trailer component.
- Add platform-specific implementations after core UI works.

## Phase 12: Image Loading Strategy

### Current State

The project uses Coil 2 Android Compose.

### Recommended Options

- Upgrade to Coil 3 for multiplatform image loading.
- Use a CMP-compatible image loading library.
- Temporarily keep image loading Android-only with an abstraction.

### Guidelines

- Keep image URL building in common code.
- Keep image rendering behind a shared composable or wrapper.
- Avoid direct dependency on Android-only Coil APIs in common UI.

## Phase 13: Paging Strategy

### Current State

The app uses AndroidX Paging runtime and Paging Compose.

Paging is one of the more sensitive migration areas.

### Options

#### Option A: Keep AndroidX Paging Android-only initially

Recommended for early migration.

#### Option B: Replace with custom page-loading state in common code

Often simpler for KMP.

#### Option C: Use a KMP-compatible paging library

Only after checking library maturity and compatibility.

### Recommended Approach

Keep AndroidX Paging in the Android app until networking and repository logic are moved. Later, introduce a common paging abstraction such as:

```kotlin
data class PagedItems<T>(
    val items: List<T>,
    val isLoading: Boolean,
    val hasMore: Boolean,
    val error: String?
)
```

### Guidelines

- Do not expose AndroidX `PagingData` from common repositories.
- Common repositories can expose page-based suspend functions.
- UI state holders can combine pages into screen state.

## Phase 14: Add `composeApp`

### Goals

Create the new Compose Multiplatform app module while keeping `:app` intact.

### Proposed structure

```text
composeApp/
  build.gradle.kts
  src/
    commonMain/kotlin/
    androidMain/kotlin/
    desktopMain/kotlin/
    iosMain/kotlin/
```

### Initial target

Start with Android target inside `composeApp`, or Desktop if you want a fast proof of concept.

### Tasks

- Add `:composeApp` to `settings.gradle.kts`.
- Configure Compose Multiplatform plugin.
- Depend on `:shared`.
- Add a minimal app shell.
- Render one shared screen or component.

### Guidelines

- Do not remove `:app`.
- Keep package names consistent where helpful.
- Avoid copying large amounts of code blindly.
- Build after every meaningful migration step.

## Phase 15: Add iOS Target

### Goals

Add iOS only after shared logic and at least one CMP screen works.

### Tasks

- Add iOS targets to `shared` and/or `composeApp`.
- Add Darwin Ktor engine.
- Add iOS app host if needed.
- Validate Kotlin/Native compilation.
- Resolve APIs that are JVM/Android-only.

### Guidelines

- Do not start with iOS first.
- Expect dependency issues when adding iOS.
- Use `expect/actual` for platform APIs.
- Keep iOS feature parity incremental.

## Phase 16: Testing Strategy

### Current Tests

The project has:

- JVM unit tests
- Android instrumented tests
- Compose UI tests
- Hilt-based journey tests

### Future Test Layout

```text
shared/src/commonTest/kotlin/       Pure common tests
shared/src/androidUnitTest/kotlin/  Android-specific shared tests
app/src/test/                       Existing Android app tests
app/src/androidTest/                Existing Android UI/instrumented tests
composeApp/src/...                  CMP app-specific tests where supported
```

### What to move first

- Domain model tests
- Mapper tests that do not depend on Android
- URL builder tests
- API error/result tests
- Repository tests after repository becomes KMP-safe

### Keep Android-only

- Hilt tests
- Room tests until database migration
- Android Compose UI tests for `:app`
- Media3/YouTube player tests

### Guidelines

- Every migration phase should include build validation.
- Move tests together with the code they protect.
- Keep old Android tests until the new shared/CMP implementation has equivalent coverage.

## Phase 17: Cleanup and Consolidation

### Goals

Remove duplicate Android-only code only after the KMP/CMP version is stable.

### Cleanup tasks

- Remove duplicated domain models from `:app`.
- Remove old Retrofit implementation if fully replaced by Ktor.
- Remove old Room implementation only if replaced by a KMP database solution.
- Remove Android-only UI components only if shared UI fully replaces them.
- Decide whether to keep `:app` or rename `:composeApp` as the main app.

### Final options

#### Option A: Keep both apps

Useful if you want an Android-only production app and a multiplatform app separately.

#### Option B: Replace `:app` with `:composeApp`

Useful when CMP reaches feature parity.

#### Option C: Rename modules

For example:

```text
androidApp/
shared/
composeApp/
```

Only do this after the migration is stable.

## Dependency Replacement Matrix

| Current dependency | Current role | KMP/CMP strategy |
| --- | --- | --- |
| Hilt | Android DI | Keep Android-only first; use constructor injection in shared; consider Koin later |
| Retrofit | Networking | Replace with Ktor Client in shared |
| OkHttp | Android HTTP engine/interceptors | Use as Ktor Android engine if needed |
| Room | Local database | Keep Android-only first; later choose Room KMP or SQLDelight |
| AndroidX Paging | Paging | Keep Android-only first; later create common paging abstraction |
| Coil 2 | Image loading | Upgrade to Coil 3 or use CMP image abstraction |
| AndroidX Navigation Compose | Navigation | Keep Android-only first; later use CMP navigation or custom navigation state |
| Media3 | Trailer/video | Platform-specific implementation behind abstraction |
| YouTube Player Android library | Trailer/video | Platform-specific implementation behind abstraction |
| Android Compose Material3 | UI | Move to Compose Multiplatform Material3 where compatible |

## Code Migration Priority

### Move first

- Domain models
- Repository interfaces
- API result/error wrappers
- URL builders
- DTOs using Kotlinx Serialization
- Pure mapper functions
- UI state/event/effect contracts

### Move later

- Network client
- Repository implementations
- ViewModel/state holders
- Shared UI components
- Full screens
- Navigation

### Move last

- Database implementation
- Platform network monitoring
- Trailer player
- Android-specific tests
- App startup/application wiring

## Platform Boundary Guidelines

Common code can use:

- Kotlin standard library
- Kotlinx Coroutines core
- Kotlinx Serialization
- Ktor common client APIs
- Compose Multiplatform common APIs
- Pure Kotlin interfaces and data classes

Common code should avoid:

- `android.*`
- Android `Context`
- Android resources via `R`
- Hilt annotations
- Retrofit annotations
- Room annotations unless intentionally using Room KMP
- AndroidX Paging types
- AndroidX Navigation `NavController`
- Media3 / ExoPlayer classes
- Java APIs not supported on Kotlin/Native

## `expect/actual` Usage Guidelines

Use `expect/actual` only for true platform differences.

Good candidates:

- Network connectivity monitor
- Platform logger
- Database driver/provider
- HTTP client engine provider
- Secure token/config provider
- Trailer/video player
- External browser opening

Avoid `expect/actual` for ordinary business logic. Prefer interfaces and dependency injection when possible.

## Configuration and Secrets Guidelines

The current app defines TMDB values in `BuildConfig`.

For KMP:

- Do not rely on Android `BuildConfig` in common code.
- Create a common configuration interface.
- Provide Android implementation from `BuildConfig` initially.
- Provide iOS/Desktop implementation later.
- Avoid committing private tokens or write-capable credentials.

Example boundary:

```kotlin
interface TmdbConfig {
    val baseUrl: String
    val imageBaseUrl: String
    val readAccessToken: String
    val accountId: String
    val sessionId: String?
}
```

## Recommended Build Checks Per Phase

After Gradle/module changes:

```powershell
.\gradlew.bat projects
.\gradlew.bat assembleDebug
```

After moving common code:

```powershell
.\gradlew.bat :shared:compileKotlinAndroid
.\gradlew.bat :app:assembleDebug
```

After adding tests:

```powershell
.\gradlew.bat :shared:allTests
.\gradlew.bat testDebugUnitTest
```

After adding `composeApp`:

```powershell
.\gradlew.bat :composeApp:assembleDebug
```

Command names may vary depending on exact target configuration.

## Suggested Milestones

### Milestone 1: Safe baseline

- Android app committed and tagged.
- Migration branch created.
- Current app builds.

### Milestone 2: Shared domain module

- `:shared` exists.
- Domain models and repository interfaces moved.
- Android app depends on `:shared`.
- Android app still builds.

### Milestone 3: Shared DTOs and utilities

- DTOs, pure mappers, and utility code moved.
- Common tests added.
- Android app still builds.

### Milestone 4: Shared networking

- Ktor client implemented.
- Repository can fetch data through shared networking.
- Existing Retrofit path can be removed or kept behind a flag during comparison.

### Milestone 5: Shared state holders

- UI contracts and state classes moved.
- Android ViewModels wrap shared logic or shared state holders.
- Unit tests pass.

### Milestone 6: Shared UI components

- Theme and small components moved to CMP.
- Movie cards, badges, filters, error views, and banners render from shared code.

### Milestone 7: Shared screens

- Home, favorites, and detail content moved to CMP.
- Android-specific navigation remains outside screens.

### Milestone 8: New `composeApp`

- CMP app module exists.
- At least Android target works.
- Optional Desktop target renders app shell.

### Milestone 9: iOS readiness

- iOS target compiles.
- Platform-specific gaps are documented.
- Networking works on iOS.

### Milestone 10: Feature parity and cleanup

- CMP version matches current Android behavior.
- Old duplicate code removed.
- Final module structure selected.

## Risk Areas

### High risk

- Database migration
- Paging migration
- Trailer playback
- Navigation migration
- iOS dependency compatibility

### Medium risk

- DI migration
- Image loading migration
- ViewModel/state-holder migration
- Compose UI resource migration

### Low risk

- Domain models
- DTOs
- Pure mappers
- Utility functions
- Result/error wrappers

## Recommended First Implementation Plan

When starting actual migration, do the first PR/commit as follows:

1. Create branch and tag current Android app.
2. Add KMP plugin entries to version catalog.
3. Add `:shared` module with Android target only.
4. Move domain models and repository interfaces.
5. Move pure utility classes.
6. Add common tests for moved utilities.
7. Make `:app` depend on `:shared`.
8. Build `:app` successfully.

This gives the project a safe KMP foundation without touching the most fragile Android-specific parts.

## Definition of Done for Migration

The migration can be considered complete when:

- The shared module contains platform-independent domain/data/presentation logic.
- Compose UI runs from shared CMP code.
- Android app builds from the new shared/CMP architecture.
- Optional iOS/Desktop targets compile and run if they are in scope.
- Android-only dependencies are isolated to Android source sets or Android host modules.
- Tests cover common logic in `commonTest`.
- The old Android-only implementation is either removed or intentionally kept as a legacy/reference app.

## Final Recommendation

Use a conservative incremental migration.

The best first target is not full iOS support. The best first target is a stable `:shared` KMP module used by the existing Android app. Once that is stable, move UI components to Compose Multiplatform, then add `composeApp`, and only then add iOS/Desktop targets.
