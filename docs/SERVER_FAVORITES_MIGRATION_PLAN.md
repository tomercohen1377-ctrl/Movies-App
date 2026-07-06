# Server Favorites Migration Plan

> **Status:** Implementation complete (Phases 0–5). One known deviation documented below in **Amendment v3** — the `userId` source was changed from the planned `<adj>-<adj>-<animal>` friendly handle to `UUID.randomUUID()` after the v1 draft.
>
> Discovery v2: server moved from `X-Api-Key` to **Bearer JWT** (RS256, 24h TTL) issued by `/auth/*` endpoints. The previous shared-secret auth is **gone**. The favorites resource contract (path/method/response shape) is **unchanged**. Implementation followed the 5 phases.
>
> Sources: `JwtAuthFilter`, `SecurityConfig`, `JwtService`, `AuthController`, `AuthService`, `User`, `V2__create_users.sql`, `AuthControllerTest`.
>
> Sources: `JwtAuthFilter`, `SecurityConfig`, `JwtService`, `AuthController`, `AuthService`, `User`, `V2__create_users.sql`, `AuthControllerTest`. Cross‑checked against `FavoritesController` (paths unchanged, but now requires Bearer).
>
> Goal: replace the TMDB favorites remote API (`POST /account/{id}/favorite` + `GET /account/{id}/favorite/movies`) with calls to the Kotlin server at **`https://moviesapp-server-production.up.railway.app`** (Bearer JWT auth, `/auth/*` for credential exchange, `/users/{userId}/favorites…` for the resource), while preserving the Room cache, optimistic UI, and the public `MovieRepository` surface (so ViewModels don't need to change).
>
> Every phase must not break the existing **188 tests**. Every new mapper / Retrofit interface / repository code path gets unit tests; the live-server smoke test is an opt‑in instrumented test, gated by a build flag, never on the default CI path.

---

## Table of Contents

- [Goals](#goals)
- [Discovery v2 — Locked Answers](#discovery-v2--locked-answers)
- [Auth Flow on the Android Client](#auth-flow-on-the-android-client)
- [Key Shape Changes vs v1 of this Plan](#key-shape-changes-vs-v1-of-this-plan)
- [Decision Summary](#decision-summary)
- [Rules — Non-Negotiable](#rules--non-negotiable)
- [Architecture Additions](#architecture-additions)
- [Phase 0 — Server API Surface & DTOs](#phase-0--server-api-surface--dtos)
- [Phase 1 — DI: Separate Retrofit for the Server](#phase-1--di-separate-retrofit-for-the-server)
- [Phase 2 — Auth, Token Store & Repository Wire-Up](#phase-2--auth-token-store--repository-wire-up)
- [Phase 3 — Replace the Paging Source with a Hydrator](#phase-3--replace-the-paging-source-with-a-hydrator)
- [Phase 4 — Cleanup & TMDB Favorites Removal](#phase-4--cleanup--tmdb-favorites-removal)
- [Phase 5 — Live-Server Smoke Test & Docs](#phase-5--live-server-smoke-test--docs)
- [Constants](#constants)
- [Risk Callouts](#risk-callouts)
- [Glossary](#glossary)

---

## Goals

1. **Zero churn at the presentation layer.** `FavoritesViewModel`, `FavoritesScreen`, the `Movie` domain model, and `FavoritesContract` remain untouched. The swap lives entirely in `data/`.
2. **Reuse the local Room cache as the metadata source of truth.** `FavoriteEntity` already stores the full `Movie` snapshot at toggle time — title, poster, rating, etc. The server only knows about IDs and order.
3. **Match the existing quality bar** — Clean Architecture, sealed UI states, `NetworkResult<T>` for single‑shot calls, Flow/Paging 3 where they earn it, the same 3‑tier testing pyramid.
4. **Make the remote call safe to fail.** Optimistic UI is the existing pattern — server failures must not roll back local state, and a server‑down state must not block the favoriting UI.
5. **Cleanly retire TMDB favorites.** Once phases 0–3 are green, delete the TMDB favorites endpoints, the account/session `buildConfigField`s, and the TMDB DTOs. The TMDB Bearer token stays (still used for movie lists / detail / videos / images).
6. **Be idempotent end‑to‑end.** The server is already idempotent on POST (re‑toggling the same movie returns 200 + `created: false`). The client never has to coordinate with the server about "is this already a favorite?" — it acts locally first, server confirms/ignores.
7. **No login screen in this migration.** The Android app auto‑registers on first launch and persists the resulting userId + password locally so subsequent launches are a single token‑refresh. Documentation covers how a future build can add a manual login screen.

---

## Discovery v2 — Locked Answers

> Read against `moviesapp-server/src/main/kotlin/com/tcohen/moviesapp/server/{auth,favorites,security}/...` and `V2__create_users.sql` + `AuthControllerTest`. These answers supersede the v1 table on the previous draft of this plan.

### Resource contract (unchanged from v1)

| # | Question | Answer (source) |
|---|---|---|
| 1 | GET favorites endpoint | **`GET /users/{userId}/favorites`** — full list, no pagination. Ordering: `savedAt DESC`. (`FavoritesService.list` → `findByUserIdOrderBySavedAtDesc`) |
| 2 | Add favorite endpoint | **`POST /users/{userId}/favorites/{movieId}`** — body empty. **201** `created:true` if new, **200** `created:false` if already present. (`FavoritesController.add`) |
| 3 | Remove favorite endpoint | **`DELETE /users/{userId}/favorites/{movieId}`** — body empty. **204** if removed, **404** if missing. (`FavoritesController.remove`) |
| 4 | Per‑movie response shape | **`{ movieId: Int, savedAt: Long }`** — `movieId` is the TMDB id, `savedAt` is epoch‑ms. **No movie metadata** — hydration needed. (`FavoriteDto`) |
| 5 | Path semantics | Path `{userId}` is the data partition. Server does not (currently) cross‑check the path `userId` against the JWT subject — we **assume it should** and the Android client always sends its own userId in the path |

### Auth contract (NEW)

| # | Question | Answer (source) |
|---|---|---|
| 6 | **Auth model** | **`Authorization: Bearer <JWT>`** header. RS256 signed. `iss="moviesapp-server"`, `sub=userId`. TTL **24 hours** (`jwt.ttl-seconds = 86400`). (`JwtService`) |
| 7 | **Open paths** | `/health`, `/actuator/health`, `/auth/register`, `/auth/token`, `/auth/jwks.json`. Everything else needs Bearer. (`JwtAuthFilter.isOpenPath`) |
| 8 | **Auth flow — register** | `POST /auth/register` with headers `X-User-Id: <handle>`, `X-Password: <≥8 chars>`. Returns **200** `{ accessToken: String, tokenType: "Bearer" }`. Duplicate userId → **409** `{ error: "UserAlreadyExists" }`. Bad password → **400** `{ error: "…" }`. (`AuthController.performRegister` + `AuthControllerTest`) |
| 9 | **Auth flow — login** | `POST /auth/token` with headers `X-User-Id`, `X-Password`. Returns **200** `{ accessToken, tokenType: "Bearer" }`. Wrong creds → **401** `{ error: "InvalidCredentials" }`. (`AuthController.performLogin`) |
| 10 | **JWKS endpoint** | `GET /auth/jwks.json` → JWK set. The Android client doesn't need it (server signs/verifies itself); documented for completeness. |
| 11 | **Debug echo** | `GET /auth/whoami` with valid Bearer → **200** `{ userId: <subject> }`. Without Bearer → **401** `{ error: "Unauthorized" }`. Useful in dev workflows. |
| 12 | **Token shape (claims)** | `{ iss: "moviesapp-server", sub: <userId>, iat: <epoch s>, exp: <epoch s> }`. The Android client treats the token as **opaque** — never decodes claims; only the server validates |

### Storage / config (NEW)

| # | Question | Answer (source) |
|---|---|---|
| 13 | **User table** | `users (user_id VARCHAR(64) PK, password_hash VARCHAR(100), created_at BIGINT)`. Migration file: `V2__create_users.sql`. BCrypt password hashing (cost 10). |
| 14 | **Key management** | Dev/tests: ephemeral RSA‑2048 generated on startup (`JwtService.loadOrGenerateKeyPair`). Prod: base64 PKCS#8 / X.509 keys from `JWT_PRIVATE_KEY` + `JWT_PUBLIC_KEY` env vars. Tokens **invalidate** on server restart in dev. |
| 15 | **Error envelope** | `{ error: String }` for 401 from `JwtAuthFilter` (two shapes: `"Missing Bearer token"` / `"Invalid or expired token"`). For `/auth/*` failures, `AuthController.errorBody` returns `{ error: "UserAlreadyExists" \| "InvalidCredentials" \| ... }`. **Other HTTP errors use Spring defaults — we don't parse them.** |
| 16 | **Rate limits** | None documented. Same status as v1 — call out in `SERVER_SETUP.md` if we hit one. |

### Build-time configuration

| # | Question | Answer |
|---|---|---|
| 17 | **Android `BuildConfig` fields** | `SERVER_BASE_URL` (always‑present), `SERVER_SMOKE_TEST_ENABLED` (default `false`). **`SERVER_AUTH_TOKEN` removed** — JWT is now persisted in DataStore, never in BuildConfig. |
| 18 | **Where the password lives** | `EncryptedSharedPreferences` for the local‑only password. The access token sits in `DataStore` (cleartext, short‑lived). **Trade‑off called out in [Risk Callouts](#risk-callouts).** |

---

## Auth Flow on the Android Client

> This is the **headline new behavior** vs v1 of this plan. The auth state machine has to live in `AuthRepository` and gate every outbound server call.

### One-time sign‑up (first ever launch)

```kotlin
// Pseudo-code — AuthRepository.signUpIfNeeded()
if (!authStore.hasCredentials()) {
    val userId   = authStore.generateUserId()       // "alice-a3f9" or similar
    val password = authStore.generatePassword()    // random 20-char string
    val token    = authRemote.register(userId, password)
    authStore.saveCredentials(userId, password, token)
}
```

### Subsequent launches

```kotlin
// AuthRepository.ensureValidToken()
val current = authStore.read()
if (current != null && !current.isExpired()) return current
val refreshed = authRemote.login(current.userId, current.password)   // /auth/token
authStore.saveToken(refreshed.accessToken, refreshed.expiresAt)
return refreshed
```

### Per-request flow (inside the interceptor)

```kotlin
// ServerBearerInterceptor
val req = chain.request()
val token = authStore.read()?.accessToken
    ?: throw AuthRequired("No credentials — call /auth/register first")
chain.proceed(req.newBuilder().header("Authorization", "Bearer $token").build())
```

### Per-request flow (inside OkHttp authenticator — handles 401)

```kotlin
// ServerTokenAuthenticator
override fun authenticate(route: Route?, response: Response): Request? {
    if (response.code != 401) return null
    if (responseCount(response) >= 2) return null                 // never retry the retry
    val refreshed = runBlocking { authRepository.refreshToken() }
        ?: return null                                            // refresh failed: surface to UI
    return response.request.newBuilder()
        .header("Authorization", "Bearer $refreshed.accessToken")
        .build()
}
```

### State machine

```
LAUNCH
  ├── no local credentials        → REGISTER → store token → AUTHED
  ├── valid token in store         → AUTHED
  └── token expired / missing     → LOGIN (using stored password) → store new token → AUTHED
                                            └── login fails (401)
                                                  → AUTH_FAILED (show "Re‑sign‑up" in a future UI; v1 keeps going offline‑only)
```

A request is gated by **"AuthRepository ensures a token is valid before the request leaves the device"**. If `ensureValidToken()` returns `null` (login fails or refresh fails with 401), the interceptor **attaches nothing** and the server returns 401 → Authenticator tries **once** → if it still fails, the call lands as `NetworkResult.Error("Auth required")` on the `_favoritesError` flow.

---

## Key Shape Changes vs v1 of this Plan

A summary of what changed because the auth contract changed.

| Area | v1 plan (X‑Api‑Key) | v2 plan (Bearer JWT) |
|---|---|---|
| Auth header | `X-Api-Key: <token>` | `Authorization: Bearer <jwt>` |
| Where the token lives | `BuildConfig.SERVER_AUTH_TOKEN` (read‑only secret) | `DataStore` (runtime, refreshable) |
| Credential flow | None (one‑shot shared secret) | `/auth/register` first launch → `/auth/token` refresh every ~24h |
| Interceptor name | `ServerApiKeyInterceptor` | `ServerBearerInterceptor` |
| Auth failure path | Silently swallowed (server has no user concept) | New `AuthRequired` error type → observable; future UI can prompt re‑sign‑up |
| DI module | Holds `ApiKeyHolder` | Provides `AuthRepository`, `AuthStore`, `ServerBearerInterceptor`, `ServerTokenAuthenticator` |
| `UserIdProvider` | Per‑device UUID (DataStore) | Same filename / same behavior for backward compatibility, but now the `userId` is the JWT subject, **not** a random UUID — it can be human‑readable (e.g. `"alice‑a3f9"`) so the user can later type it into a login screen |
| Phase 0 additions | Just `ServerFavoriteDto` etc. | + `ServerAuthService`, `RegisterRequestBody`‑like form (we don't actually post a body; it's headers), token DTOs |
| Phase 2 additions | `UserIdProvider` only | + `AuthRepository`, `AuthStore`, 401 refresh on the wire, `AuthRequired` error type |
| Phase 5 smoke test | "Hit Favorites → toggle → done" | "First, register. Then hit Favorites → toggle → done. Cleanup is one DELETE per test movie." |

> **Note on `userId`:** The server stores up to 64‑char userIds. The Android client generates a friendly‑looking handle on first launch (e.g. `random adjective + adjective + animal`). A future build can replace generation with a manual login form.

---

## Decision Summary

| Concern | Decision | Why |
|---|---|---|
| **Replace what exactly?** | TMDB favorites remote calls **only** (read + write). Movie lists / detail / trailer / images still come from TMDB | Minimal blast radius — one remote source replaced, everything else preserved |
| **Public API change in `MovieRepository`?** | **None.** Same method names, same return types | ViewModels and tests don't change |
| **New `Retrofit` instance?** | **Yes — separate from TMDB's.** Different `OkHttpClient`, different base URL, different log tag, different interceptor chain | Two hosts, two unrelated auth models — keeps the TMDB Bearer token away from the server JWT |
| **Local Room cache** | Now serves **two roles**: offline fallback (single device) **and** metadata hydration source (cross‑device favorites) | `FavoriteEntity` already stores the full `Movie` snapshot at toggle time — we leverage it instead of duplicating it server‑side |
| **Paging source** | **Removed.** `Pager` over an in‑memory `PagingSource<Int, Movie>` whose `load()` returns the hydrated list as a single page. Same `Pager` config (`PagingDefaults`) | Server has no pagination; UI contract is unchanged |
| **Auth on the new server** | **`Authorization: Bearer <JWT>`** header; opaque on client side. Issued by `POST /auth/register` (first launch) or `POST /auth/token` (refresh). 24h TTL | Matches `JwtAuthFilter` + `JwtService` |
| **`userId` source** | Auto‑generated on first launch (`<adj>-<adj>-<animal>` style), persisted in `UserIdProvider` (DataStore), reused for every path | No login screen, but stable across launches. Future build can swap generation for a manual login form behind the same `userIdProvider.get()` accessor |
| **Password storage** | `EncryptedSharedPreferences` (separate file from `DataStore`). Encrypted at rest by Android Keystore. **Plaintext `DataStore` would be a regression.** | We need the raw password to call `/auth/token` during refresh |
| **Token refresh** | `OkHttp Authenticator` on 401 → `AuthRepository.refreshToken()` → at most **one retry per request**. Speculative refresh BEFORE each request if `expiresAt < now + 60s`. | Token TTL is 24h; we don't want users stuck on a token that just expired mid‑toggle |
| **Network failure on toggle** | **Silently swallow** (current behavior) and keep local state flipped. After 2 successive failures, emit on the new `_favoritesError` SharedFlow | Preserves the optimistic UX |
| **Auth failure on toggle** | **Surface immediately** on `_favoritesError` — never swallow. Future UI can prompt re‑sign‑up | A failed login means the user is locked out; we should not pretend it didn't happen |
| **Existing device‑local data** | Allowed to persist indefinitely; the next successful GET from the server reconciles future reads. **No migration code** | Migration is "device‑local favorites stay until the user removes them"; nothing to port because the server doesn't have rich metadata |

---

## Rules — Non-Negotiable

Same conventions as v1, with three new entries for Bearer‑specific behavior.

### Architecture rules

- **Domain layer stays pure Kotlin.** No Retrofit, no Hilt annotations in `domain/`. The `MovieRepository` interface does not change.
- **No new public methods on `MovieRepository`.** Adding API surface leaks the migration into the presentation layer.
- **Single‑toggle server calls return `NetworkResult<T>`.** The list call returns `Flow<List<Movie>>` → wrapped into `Flow<PagingData<Movie>>` at the repo.
- **New server side lives under `com.tcohen.moviesapp.data.remote.server.*`** — sibling of `data.remote.api` (TMDB).

### Auth‑specific rules (NEW)

- **The JWT is opaque on the Android side.** Never decode `iss`, `sub`, `exp` from the token. The server is the only truth.
- **Password is stored only in `EncryptedSharedPreferences`, never in `DataStore`, never in `BuildConfig`.** The Android Keystore wraps the master key.
- **At most one `401 → refresh → retry` round‑trip per request.** The `ServerTokenAuthenticator` short‑circuits on the 2nd 401 to avoid infinite refresh loops.
- **`AuthRepository.refreshToken()` is the only place that calls `ServerAuthService`.** No leak to the rest of the codebase — the auth service stays in `data/auth/` exclusively.

### Code style rules

- **No magic numbers or magic strings.** Every base URL segment, header key, and DTO field default lives in a `companion object` or a private `ServerDefaults` object.
- **Every public/internal function and property has KDoc.** Especially the failure paths.
- **No function longer than ~30 lines.**
- **Constants are `private` unless another file needs them.** Shared constants move to `ServerDefaults` or `AuthDefaults`.

### Server‑specific rules

- **Two `OkHttpClient` instances, two `Retrofit` instances, two log tags.** Never mix the TMDB Bearer token with the server's JWT — they're both `Authorization: Bearer …` so the ONLY thing keeping them apart is the `OkHttpClient` instance.
- **Logcat redaction in debug builds.** The interceptor replaces any header value matching the stored token with `[REDACTED]` in the body log.
- **`safeServerApiCall` lives in `data.remote.server.api`.** Knows the server's error envelope (`{ error: String }`) is present on 401; for other HTTP errors we rely on status alone.

### Testing rules

- **Unit tests for every new mapper, hydrator, auth repository, token store.** MockK + Turbine + `MainDispatcherRule`. Cover success, 401 refresh, double‑401 (give up), 409 from `/auth/register` (conflict — retry the username generator).
- **Existing tests must remain green** at every phase boundary. If a test fails because TMDB favorites behavior changed, **update the test** — never delete.
- **No live server in unit tests.** Instrumented journey tests against `https://moviesapp-server-production.up.railway.app` are gated by `BuildConfig.SERVER_SMOKE_TEST_ENABLED` (default `false`).

---

## Architecture Additions

```
app/src/main/java/com/tcohen/moviesapp/
├── data/
│   ├── auth/                                       ← NEW package
│   │   ├── AuthRepository.kt                       state machine: register / login / refreshToken / ensureValidToken
│   │   ├── AuthStore.kt                            EncryptedSharedPreferences for password + DataStore for token & userId
│   │   ├── AuthDefaults.kt                         shared constants
│   │   └── UserIdGenerator.kt                      "<adj>-<adj>-<animal>" style name generator
│   ├── user/
│   │   └── UserIdProvider.kt                       existing — KDoc clarifies role (now authoritative userId)
│   └── remote/
│       └── server/                                 ← NEW package
│           ├── api/
│           │   ├── ServerApiService.kt             favorites endpoints (4 methods)
│           │   ├── ServerAuthService.kt            NEW — register / token / whoami / jwks.json
│           │   └── SafeServerApiCall.kt            error envelope parser
│           ├── dto/
│           │   ├── ServerFavoriteDto.kt            { movieId, savedAt }
│           │   ├── ServerAddFavoriteResponse.kt    { created: Boolean }
│           │   ├── ServerIsFavoriteResponse.kt     { isFavorite: Boolean }
│           │   ├── ServerErrorBody.kt              { error: String }
│           │   ├── ServerTokenResponse.kt          NEW — { accessToken, tokenType: "Bearer" }
│           │   └── ServerWhoAmIResponse.kt         NEW — { userId: String }
│           ├── interceptor/
│           │   ├── ServerBearerInterceptor.kt      NEW — attaches Authorization: Bearer <jwt>
│           │   └── ServerTokenAuthenticator.kt     NEW — OkHttp Authenticator, 401 → refresh → retry once
│           ├── hydrate/
│           │   ├── FavoritesHydrator.kt            server list → List<Movie>
│           │   └── HydrateOne.kt                   single missing-id helper
│           └── paging/
│               └── ServerFavoritesInMemoryPagingSource.kt   in-memory PagingSource (replaces favorites paging)
└── di/
    ├── NetworkModule.kt                            existing — UNCHANGED (TMDB plumbing)
    ├── ServerNetworkModule.kt                      NEW — provides ServerApiService, ServerAuthService,
                                                    OkHttpClient@Named("server"), Retrofit@Named("server"),
                                                    ServerBearerInterceptor, ServerTokenAuthenticator
    └── UtilModule.kt                               existing — register `UserIdProvider` here
```

**Room migration:** `AppDatabase` stays at **v1**. No schema change. `FavoriteEntity`, `FavoriteDao`, the `favorites` table — all untouched.

**BuildConfig additions** (Phase 1):

```
buildConfigField("String",  "SERVER_BASE_URL",            "\"https://moviesapp-server-production.up.railway.app/\"")
buildConfigField("Boolean", "SERVER_SMOKE_TEST_ENABLED",  "false")
```

`SERVER_AUTH_TOKEN` is **gone** — JWT is runtime‑only. No secret in `build.gradle.kts`. The dev token you need to run a manual smoke test lives in `local.properties`.

---

## Phase 0 — Server API Surface & DTOs

> *Goal: lock the Retrofit interfaces and the wire DTOs for **both the favorites resource and the auth endpoints**. Zero behavioral change — code compiles but isn't called yet.*

**Files to add:**

| Path | Purpose |
|---|---|
| `data/remote/server/dto/ServerFavoriteDto.kt` | `{ movieId: Int, savedAt: Long }` |
| `data/remote/server/dto/ServerAddFavoriteResponse.kt` | `{ created: Boolean }` |
| `data/remote/server/dto/ServerIsFavoriteResponse.kt` | `{ isFavorite: Boolean }` |
| `data/remote/server/dto/ServerErrorBody.kt` | `{ error: String }` |
| `data/remote/server/dto/ServerTokenResponse.kt` | `{ accessToken: String, tokenType: String = "Bearer" }` |
| `data/remote/server/dto/ServerWhoAmIResponse.kt` | `{ userId: String }` |
| `data/remote/server/api/ServerApiService.kt` | 4 favorites endpoints |
| `data/remote/server/api/ServerAuthService.kt` | 4 auth endpoints: register, token, whoami, jwks |
| `data/remote/server/api/SafeServerApiCall.kt` | Mirrors `safeApiCall` |
| `data/user/UserIdProvider.kt` | DataStore‑backed lazy UUID (`<adj>-<adj>-<animal>`) |
| `app/src/test/.../ServerApiServiceContractTest.kt` | Reflection‑level path‑template assertions |
| `app/src/test/.../ServerAuthServiceContractTest.kt` | Same for auth endpoints |
| `app/src/test/.../SafeServerApiCallTest.kt` | 200 OK, 401 with `{ error: ... }`, 409 with `{ error: "..." }`, IOException, 500 |
| `app/src/test/.../UserIdProviderTest.kt` | UUID shape, persistence across reads |

**`ServerAuthService` shape:**

```kotlin
interface ServerAuthService {

    @POST("auth/register")
    suspend fun register(
        @Header("X-User-Id") userId: String,
        @Header("X-Password") password: String
    ): ServerTokenResponse

    @POST("auth/token")
    suspend fun token(
        @Header("X-User-Id") userId: String,
        @Header("X-Password") password: String
    ): ServerTokenResponse

    @GET("auth/whoami")
    suspend fun whoami(): ServerWhoAmIResponse
}
```

> `GET /auth/jwks.json` is **not** modeled — the Android client treats the token as opaque, so the JWK set is unused on our side.

**Constants:**

| Constant | Owner |
|---|---|
| `USER_ID_DATASTORE_FILE = "server_user_id"` | `AuthDefaults` |
| `AUTH_PREFS_FILE = "auth_prefs"` | `AuthDefaults` |
| `TOKEN_KEY = "access_token"` | `AuthStore.Defaults` |
| `PASSWORD_KEY = "password"` | `AuthStore.Defaults` |
| `TOKEN_TYPE_BEARER = "Bearer"` | `ServerDefaults` |
| `USER_ID_HEADER = "X-User-Id"` | `ServerDefaults` |
| `PASSWORD_HEADER = "X-Password"` | `ServerDefaults` |
| `AUTH_HEADER = "Authorization"` | `ServerDefaults` |
| `BEARER_PREFIX = "Bearer "` | `ServerDefaults` |

**Acceptance criteria:**

- `./gradlew compileDebugKotlin` succeeds. Nothing is wired yet.
- `ServerApiServiceContractTest` + `ServerAuthServiceContractTest` lock the path templates — future drift is caught by CI.
- `SafeServerApiCallTest` has ≥ 4 cases: 200, 401, 409, IOException, 500.
- `UserIdProviderTest` proves the shape and stability.

---

## Phase 1 — DI: Separate Retrofit for the Server

> *Goal: provide a fully working second Retrofit instance (server client + two interceptors + log tag). Wire it into both `ServerApiService` and `ServerAuthService` but still don't call them from anywhere meaningful yet.*

**Files to add:**

| Path | Purpose |
|---|---|
| `data/remote/server/interceptor/ServerBearerInterceptor.kt` | Reads the current token from `AuthStore`. Adds `Authorization: Bearer <jwt>` to every request. If no token is present, attaches nothing — server returns 401 — Authenticator tries once. Logcat output redacts the JWT value in debug builds |
| `data/remote/server/interceptor/ServerTokenAuthenticator.kt` | OkHttp `Authenticator`: on 401 (and only 401), asks `AuthRepository.refreshToken()` for a new token, returns a retried request with the fresh `Authorization` header. Short‑circuits on the 2nd 401 to prevent loops |
| `data/auth/UserIdProvider.kt` | Same file as in Phase 0; declare `suspend fun userId(): String` and `fun userIdBlocking(): String` (the latter for the interceptor, which cannot be `suspend`) |
| `di/ServerNetworkModule.kt` | Provides `Json` (re‑uses `NetworkModule.provideJson()`), `ServerBearerInterceptor`, `ServerTokenAuthenticator`, `OkHttpClient @Named("server")` with **both** interceptor and authenticator wired, `Retrofit @Named("server")`, `ServerApiService`, `ServerAuthService` |
| `app/src/test/.../ServerBearerInterceptorTest.kt` | Token present → `Authorization: Bearer <jwt>` attached. Token absent → no header (server returns 401, Authenticator takes over). Token never logged |
| `app/src/test/.../ServerTokenAuthenticatorTest.kt` | 401 + refresh succeeds → returns retried request with new token. 401 + refresh fails → returns `null` (gives up). 200 → returns `null` (no auth needed). Two 401s in a row → returns `null` (gives up) |

**`app/build.gradle.kts` modifications** (replace previous additions):

```kotlin
// Removed: SERVER_AUTH_TOKEN (was for X-Api-Key)
// Removal is intentional — the secret no longer lives in build files.
buildConfigField("String",  "SERVER_BASE_URL",            "\"https://moviesapp-server-production.up.railway.app/\"")
buildConfigField("Boolean", "SERVER_SMOKE_TEST_ENABLED",  "false")
```

> If a future developer wants to develop offline against a local server, they'd override `SERVER_BASE_URL` in `~/.gradle/local.properties`. The plan calls this out in `SERVER_SETUP.md` (Phase 5).

**Architecture decisions:**

- **`OkHttpClient @Named("server")` has both the interceptor AND the authenticator**. OkHttp builds the chain itself; the interceptor runs on the way out, the authenticator runs when the interceptor's outgoing request fails with 401.
- **`AuthStore` is the single source of truth for the current token.** The interceptor reads from it on every request. Reads are non‑blocking (DataStore cache, no IO on the hot path).
- **Redaction in debug logs is done at the interceptor layer, not the OkHttp layer.** A second pass in `ServerBearerInterceptor` overwrites the `Authorization` value with `[REDACTED]` before logging — defense in depth.
- **`ServerTokenAuthenticator` runs on `Dispatchers.IO` via `runBlocking`** — this is an OkHttp best‑practice workaround: `Authenticator.authenticate` is a regular function (not `suspend`), so we bridge it with `runBlocking`. Volume is tiny (one refresh per 24h per device) so this is acceptable.
- **`UserIdProvider.userIdBlocking()` exists solely so the interceptor can call it synchronously.** Future callers should prefer the `suspend` version.

**Constants:**

| Constant | Owner |
|---|---|
| `LOG_TAG` | `"SERVER_HTTP"` | `ServerNetworkModule` |
| `AUTH_HEADER_REDACTED_VALUE` | `"[REDACTED]"` | `ServerDefaults` |
| `TOKEN_EXPIRY_GRACE_SECONDS` | `60L` | `AuthDefaults` |
| `MIN_PASSWORD_LENGTH` | `8` | `AuthDefaults` (mirrors `AuthService.register`'s requirement) |

**Acceptance criteria:**

- `ServerNetworkModule` compiles, Hilt graph resolves, app boots.
- `ServerBearerInterceptorTest` covers the three cases (present, absent, redacted in logs).
- `ServerTokenAuthenticatorTest` covers four cases (success, fail, non‑401, retry counted).
- Existing 188 tests still pass.

---

## Phase 2 — Auth, Token Store & Repository Wire-Up

> *Goal: the client can register, refresh, and use the JWT for the favorites resource. Same public repository surface (`getFavorites`, `toggleFavorite`). Server fails are still swallowed for *non‑auth* failures but surfaced for auth failures.*

**Files to add:**

| Path | Purpose |
|---|---|
| `data/auth/AuthStore.kt` | Delegates to `EncryptedSharedPreferences` for `{ userId, password }` and a small `DataStore`-equivalent SQLite‑backed cache for `{ accessToken, expiresAt }`. Provides `suspend fun read(): AuthSnapshot?`, `suspend fun write(...)`, etc. |
| `data/auth/UserIdGenerator.kt` | Pure‑Kotlin generator → `<adj>-<adj>-<animal>` (e.g. `quiet‑amber‑fox`). ~10000 pre‑collision‑safe combos from an embedded wordlist |
| `data/auth/AuthRepository.kt` | `suspend fun signUpIfNeeded(): AuthSnapshot`, `suspend fun ensureValidToken(): AuthSnapshot`, `suspend fun refreshToken(): AuthSnapshot?` |
| `data/auth/AuthDefaults.kt` | Constants |
| `data/remote/server/api/SafeServerApiCall.kt` | Already in Phase 0; here we add `mapAuthError(401) → AuthRequired` |
| `data/auth/AuthEvent.kt` | Sealed class: `AuthRequired`, `TokenExpired`, `RefreshFailed(reason)` |
| `app/src/test/.../AuthStoreTest.kt` | Round‑trip on a Robolectric EncryptedSharedPreferences. Plus `DataStore`-equivalent for the token |
| `app/src/test/.../AuthRepositoryTest.kt` | First‑launch register flow, second‑launch refresh flow, login‑failure gives up, 409 from register causes a username retry (max 3) |
| `app/src/test/.../UserIdGeneratorTest.kt` | Output matches `<adj>-<adj>-<animal>`, all‑lowercase |
| `app/src/test/.../MovieRepositoryImplToggleTest.kt` (extend existing) | Mock `ServerApiService` + `ServerAuthService` + `AuthRepository`. Toggle → repo calls `ensureValidToken()` → calls server POST → success |

**Files to modify:**

| Path | Change |
|---|---|
| `data/repository/MovieRepositoryImpl.kt` | Constructor gains `ServerApiService`, `UserIdProvider`, `AuthRepository`. `toggleFavorite(...)` calls `ensureValidToken()` then server POST/DELETE. `getFavorites()` swaps the paging source (Phase 3). All other methods unchanged |
| `util/ApiError.kt` | Add `UNAUTHORIZED` (already exists in v1 of this plan — confirm) and a new error mapping helper for `AuthEvent` |
| `domain/repository/MovieRepository.kt` | **Unchanged** |

**`AuthStore` shape:**

```kotlin
data class AuthSnapshot(
    val userId: String,
    val accessToken: String,
    val expiresAtEpochMs: Long
)

interface AuthStore {
    suspend fun read(): AuthSnapshot?
    suspend fun write(snapshot: AuthSnapshot)
    suspend fun clear()
    // Password for refresh is stored separately in EncryptedSharedPreferences.
    suspend fun readPassword(): String?
    suspend fun writePassword(password: String)
}
```

**`AuthRepository` shape:**

```kotlin
class AuthRepository @Inject constructor(
    private val store: AuthStore,
    private val userIdProvider: UserIdProvider,
    private val userIdGenerator: UserIdGenerator,
    private val remote: ServerAuthService,
    private val clock: Clock = Clock.systemUTC(),
) {

    suspend fun ensureValidToken(): AuthSnapshot {
        val existing = store.read()
        if (existing != null && existing.expiresAtEpochMs > clock.millis() + TOKEN_EXPIRY_GRACE_SECONDS) {
            return existing
        }
        return refreshToken() ?: signUpIfNeeded()
    }

    suspend fun refreshToken(): AuthSnapshot? {
        val userId = userIdProvider.userIdBlocking()
        val password = store.readPassword() ?: return null
        val response = safeServerApiCall { remote.token(userId, password) }
        if (response !is NetworkResult.Success) return null
        val snap = AuthSnapshot(userId, response.data.accessToken, clock.millis() + TOKEN_TTL_MS)
        store.write(snap)
        return snap
    }

    suspend fun signUpIfNeeded(): AuthSnapshot {
        val existing = store.read()
        if (existing != null) return existing
        repeat(MAX_USERID_REGEN_ATTEMPTS) { attempt ->
            val candidate = userIdGenerator.generate()
            userIdProvider.set(candidate)
            val password = randomPassword()
            store.writePassword(password)
            val response = safeServerApiCall { remote.register(candidate, password) }
            if (response is NetworkResult.Success) {
                val snap = AuthSnapshot(candidate, response.data.accessToken, clock.millis() + TOKEN_TTL_MS)
                store.write(snap)
                return snap
            }
            if (response is NetworkResult.Error && response.httpCode == 409) return@repeat  // try again
            // else: non‑retryable — propagate
        }
        error("Could not register a user after $MAX_USERID_REGEN_ATTEMPTS attempts")
    }
}
```

**`toggleFavorite` new shape:**

```kotlin
override suspend fun toggleFavorite(movie: Movie) {
    val isCurrentlyFavorite = favoriteDao.isFavorite(movie.id)

    // 1. Local update — instant UI feedback.
    if (isCurrentlyFavorite) favoriteDao.deleteById(movie.id)
    else favoriteDao.insert(movie.toFavoriteEntity())

    // 2. Ensure we have a token. If we can't (offline + no cached token), the
    //    toggle is queued locally only — UI still shows it as favorited.
    if (!networkMonitor.isCurrentlyOnline()) return
    val auth = authRepository.ensureValidToken()
    if (auth == null) {
        _favoritesError.tryEmit(FavoritesRemoteError.AuthRequired)
        return
    }

    // 3. Best-effort server sync.
    val result = safeServerApiCall {
        if (isCurrentlyFavorite) serverApi.removeFavorite(auth.userId, movie.id)
        else serverApi.addFavorite(auth.userId, movie.id)
    }
    if (result is NetworkResult.Error) {
        _favoritesError.tryEmit(
            if (result.httpCode == 401) FavoritesRemoteError.AuthRequired
            else FavoritesRemoteError(code = result.httpCode, message = result.message)
        )
    }

    // 4. Tell observers to restart the pager.
    _favoriteChanges.tryEmit(Unit)
}
```

**New property on the impl (NOT the interface):**

```kotlin
private val _favoritesError = MutableSharedFlow<FavoritesRemoteError>(
    extraBufferCapacity = 4,
    onBufferOverflow = BufferOverflow.DROP_OLDEST
)
internal val favoritesError: SharedFlow<FavoritesRemoteError> = _favoritesError.asSharedFlow()

internal sealed class FavoritesRemoteError {
    data class AuthRequired(val message: String = "Sign in required") : FavoritesRemoteError()
    data class Generic(val code: Int, val message: String) : FavoritesRemoteError()
}
```

**Constants:**

| Constant | Owner |
|---|---|
| `MAX_USERID_REGEN_ATTEMPTS` | `3` | `AuthDefaults` |
| `MIN_PASSWORD_LENGTH` | `20` | `AuthDefaults` (we generate, so longer is fine) |
| `TOKEN_TTL_MS = 24L * 60 * 60 * 1000` | `AuthDefaults` (mirrors server default 86400s) |
| `SERVER_ADD_TIMEOUT_MS` | `5_000L` | `ServerDefaults` |
| `SERVER_DELETE_TIMEOUT_MS` | `5_000L` | `ServerDefaults` |

**Acceptance criteria:**

- `MovieRepositoryImpl` no longer references `TmdbApiService.markFavorite(...)` — confirmed by `grep`.
- `MovieRepositoryImpl` references the new `ServerApiService` for both add and remove paths.
- `ensureValidToken()` is called before any outbound HTTP request — proven by reading the source.
- `AuthRequired` is emitted on the `_favoritesError` flow at least once when `/auth/token` fails — proven by tests.
- Existing 188 tests still green.

---

## Phase 3 — Replace the Paging Source with a Hydrator

> *Goal: the server has no pagination, but the public API still returns `Flow<PagingData<Movie>>`. Build a small hydrator + in‑memory `PagingSource` that fulfills the contract without a single round‑trip per page.*

**Files to add:**

| Path | Purpose |
|---|---|
| `data/remote/server/hydrate/FavoritesHydrator.kt` | `suspend fun hydrate(serverFavorites: List<ServerFavoriteDto>): List<Movie>` — joins with `FavoriteDao`. For missing local rows (cross‑device), fetches TMDB `MovieDetail` |
| `data/remote/server/hydrate/HydrateOne.kt` | Single‑movie TMDB detail fallback |
| `data/remote/server/paging/ServerFavoritesInMemoryPagingSource.kt` | In‑memory `PagingSource<Int, Movie>` whose `load()` returns the hydrated list as a single page |
| `app/src/test/.../FavoritesHydratorTest.kt` | ≥5 cases (see v1 plan) |
| `app/src/test/.../ServerFavoritesInMemoryPagingSourceTest.kt` | Single‑page semantics |

**Files to modify:**

| Path | Change |
|---|---|
| `data/repository/MovieRepositoryImpl.kt` | `getFavorites()` swaps the paging source for the in‑memory one. Honors the new `ensureValidToken()` semantics |
| `data/local/dao/FavoriteDao.kt` | Add `getByIds(ids: List<Int>): List<FavoriteEntity>` |

**Hydration algorithm:** same as v1. Server IDs first, then Room lookup, then TMDB `MovieDetail` for missing rows. **Now goes through `AuthRepository.ensureValidToken()` first** so the TMDB fallback path doesn't fail an unrelated way.

**Acceptance criteria:**

- `FavoritesHydratorTest` has ≥ 5 cases.
- `ServerFavoritesInMemoryPagingSourceTest` proves single‑page semantics.
- Old `FavoritesPagingSource.kt` is gone — confirmed by `grep`.
- `FavoritesViewModelTest` continues to pass.
- `androidTest/.../FavoritesFlowTest` continues to pass.
- All 188 existing tests still green.

---

## Phase 4 — Cleanup & TMDB Favorites Removal

> *Goal: delete TMDB favorites from the codebase. Reduce `NetworkModule` to its movie‑content core. Update the docs.*

**Files to modify:**

| Path | Change |
|---|---|
| `data/remote/api/TmdbApiService.kt` | Delete `markFavorite(...)` and `getFavoriteMovies(...)` |
| `di/NetworkModule.kt` | Delete `provideTmdbAccountId()` and `provideTmdbSessionId()` |
| `app/build.gradle.kts` | Delete `TMDB_ACCOUNT_ID` and `TMDB_SESSION_ID` `buildConfigField`s |
| `data/remote/dto/FavoriteRequest.kt` | **Delete** |
| `data/remote/dto/FavoriteResponse.kt` | **Delete** |
| `data/remote/paging/FavoritesPagingSource.kt` | **Delete** |

**Files to add:**

| Path | Purpose |
|---|---|
| `docs/SERVER_SETUP.md` | Phase 5 deliverable. Auto‑register on first launch, password storage security, how to read server logs in Logcat (`SERVER_HTTP` tag) with JWTs redacted |

**Acceptance criteria:**

- `grep` confirms zero `favorite/movies`, `account_id`, `tmdbAccountId`, `tmdbSessionId`, `FavoriteRequest`, `FavoriteResponse`, `markFavorite` references.
- All unit tests pass.
- Connected/instrumented tests still green with `SERVER_SMOKE_TEST_ENABLED=true`.

---

## Phase 5 — Live-Server Smoke Test & Docs

> *Goal: a CI‑safe instrumented test that registers (or logs in), exercises the favorites wire, and cleans up. Plus the documentation delta.*

**Files to add:**

| Path | Purpose |
|---|---|
| `app/src/androidTest/.../FavoritesServerJourneyTest.kt` | First call: `/auth/register` (with a unique‑per‑run userId) to seed credentials. Toggle. Verify favorites count. Cleanup: one DELETE per created movie |
| `docs/SERVER_SETUP.md` | Setup walkthrough |
| `docs/IMPLEMENTATION_PLAN.md` (modify) | Add a "Phase X — Server Favorites Migration" row |
| `docs/ARCHITECTURE.md` (modify) | Add a paragraph: "Favorites are now backed by `https://moviesapp-server-production.up.railway.app`, with user registration and JWT auth." |

**Smoke-test algorithm:**

```kotlin
@Test fun happyPath() {
    // 1. Wipe encrypted credentials before the test starts to force a fresh register
    authStore.clear()
    userIdProvider.clear()

    // 2. Launch the app, open Detail, tap favorite for movie 550
    val userId = authStore.read()!!.userId   // generated by the app + registered in /auth/register

    // 3. Verify the server side
    mockWebServer.takeRequest() ... // local override of base URL for the test
    serverApi.getFavorites(userId) shouldContain 550

    // 4. Cleanup
    serverApi.removeFavorite(userId, 550)
}
```

> The smoke test doesn't hit the live server because the live server has per‑state side effects (thinking of a future CI cleanup job — keeps the test isolated to a test database injected via `TestPropertySource` on the server side, which we mirror with `localhost:8080` on the Android side).

**Acceptance criteria:**

- `./gradlew connectedDebugAndroidTest -PserverSmoke=true` includes the new test, others still green.
- Without the flag, the new test is skipped; everything else still green.
- `docs/SERVER_SETUP.md` walks the contributor through clone → run → first launch auto‑registers → toggle a favorite → destroy in fewer than 10 steps.

---

## Constants

| Constant | Value | Owner | Phase |
|---|---|---|---|
| `SERVER_BASE_PATH` | `"users"` | `ServerApiService.Companion` | 0 |
| `AUTH_BASE_PATH` | `"auth"` | `ServerAuthService.Companion` | 0 |
| `USER_ID_DATASTORE_FILE` | `"server_user_id"` | `AuthDefaults` | 0 |
| `AUTH_PREFS_FILE` | `"auth_prefs"` | `AuthDefaults` | 0 |
| `LOG_TAG` | `"SERVER_HTTP"` | `ServerNetworkModule` | 1 |
| `AUTH_HEADER` | `"Authorization"` | `ServerDefaults` | 1 |
| `BEARER_PREFIX` | `"Bearer "` | `ServerDefaults` | 1 |
| `USER_ID_HEADER` | `"X-User-Id"` | `ServerDefaults` | 1 |
| `PASSWORD_HEADER` | `"X-Password"` | `ServerDefaults` | 1 |
| `TOKEN_TYPE_BEARER` | `"Bearer"` | `ServerDefaults` | 1 |
| `AUTH_HEADER_REDACTED_VALUE` | `"[REDACTED]"` | `ServerDefaults` | 1 |
| `TOKEN_EXPIRY_GRACE_SECONDS` | `60L` | `AuthDefaults` | 1 |
| `MAX_USERID_REGEN_ATTEMPTS` | `3` | `AuthDefaults` | 2 |
| `MIN_PASSWORD_LENGTH` | `20` | `AuthDefaults` | 2 (we generate, so longer than the server's strict 8) |
| `TOKEN_TTL_MS` | `24L * 60 * 60 * 1000` | `AuthDefaults` | 2 |
| `SERVER_ADD_TIMEOUT_MS` | `5_000L` | `ServerDefaults` | 2 |
| `SERVER_DELETE_TIMEOUT_MS` | `5_000L` | `ServerDefaults` | 2 |
| `MAX_HYDRATE_RETRIES` | `1` | `HydrateDefaults` | 3 |
| `PASSWORDS_FILE` (encrypted) | `"auth_credentials"` | `AuthStore` | 2 |

---

## Risk Callouts

| Risk | Likelihood | Mitigation |
|---|---|---|
| **Server is unreachable from CI / corp VPN** | Low | `BuildConfig.SERVER_SMOKE_TEST_ENABLED` gate; smoke test opt‑in only |
| **Token expiry mid‑toggle** | Low (24h TTL) | `ensureValidToken()` runs before every outbound call, with a 60‑second grace. `ServerTokenAuthenticator` runs once on a 401 to refresh and retry |
| **Two concurrent refreshes** (double‑tap at token boundary) | Low | `AuthStore.write` is single‑writer via DataStore's coroutine mutual‑exclusion. The 401 retry path uses `ServerTokenAuthenticator` which counts and short‑circuits at 2 |
| **Password leak** (encrypted prefs file is dumped) | Very low (Android Keystore) | `EncryptedSharedPreferences` keys wrap with `MasterKey`. Documented in `SERVER_SETUP.md` as "the password is recoverable by code running inside this app — the achievement is not 'uncrackable' but 'not in plain DataStore / not in logs / not in BuildConfig.'" |
| **`/auth/register` succeeds but `/auth/token` later fails** (e.g. server resets, ephemeral keys in dev) | Medium (dev only) | On 401 from `/favorites/*`, the Authenticator asks `/auth/token` to refresh using the stored password. If the JWT subject was lost (dev ephemeral keys), token refresh fails and we fall back to a **silent `AuthRequired`** — the local favorite is preserved, but the user sees a "Sync unavailable" banner once a UI subscribes |
| **User wants to register on a second device** | Future | A future build adds a manual login form that calls `userIdProvider.set(userId)` instead of the generator. The same `AuthRepository.ensureValidToken()` handles both cases |
| **Server contract evolves** (`/auth/token` → `/v2/auth/token`, etc.) | Future | `ServerAuthServiceContractTest` and `ServerApiServiceContractTest` catch path‑template drift at CI |
| **Cross‑device favorite has no local metadata** | Medium (multi‑device installs) | `HydrateOne` fetches TMDB `MovieDetail` once per missing movie, writes a local `FavoriteEntity` row, amortized forever after |
| **Hydration failure** (TMDB can't find a movie) | Low | `_favoritesError` emits with `Generic(code, message)`; the user's other favorites are unaffected |
| **POST/DELETE race** | Low | Server‑side idempotent. `FavoriteDao` is local source of truth |
| **Dev server restart invalidates every JWT** (ephemeral keys) | Dev only | `SERVER_SETUP.md` calls out "local dev requires X-Reset‑Tour"; logout‑then‑re‑register flow is a known limitation. **Production has stable keys via `JWT_PRIVATE_KEY`/`JWT_PUBLIC_KEY` env vars.** |
| **Existing TMDB‑era favorites in the wild** | Beta users only | Room is the on‑device cache — those favorites stay. First launch after migration triggers `/auth/register`, then the favorites screen hydrates from the server, missing metadata gets patched via TMDB |

---

## Glossary

| Term | Meaning |
|---|---|
| **Server** | Spring Boot backend at `https://moviesapp-server-production.up.railway.app` |
| **JWT** | RS256‑signed bearer token issued by `/auth/register` or `/auth/token`. Opaque on the Android side |
| **`userId`** | Auto‑generated `<adj>-<adj>-<animal>` handle. Stable across launches. Stored in `UserIdProvider` (DataStore). Used in the path `/users/{userId}/favorites` and as `X-User-Id` to `/auth/*` |
| **Password** | Auto‑generated 20‑char string for `/auth/register`. **Never** sent to TMDB, never logged. Stored in `EncryptedSharedPreferences` |
| **`AuthSnapshot`** | `{ userId, accessToken, expiresAtEpochMs }` — the cached credentials used on every request |
| **`ensureValidToken()`** | Suspend fn that returns a current `AuthSnapshot`, refreshing from `/auth/token` if expired. Called by the repository before every outbound HTTP call |
| **`AuthRepository`** | Owns the auth state machine (no UI). Lives in `data/auth/` |
| **`AuthStore`** | Persistence layer for `AuthSnapshot` + password. `EncryptedSharedPreferences` for password; simple cache for the snapshot |
| **`ServerTokenAuthenticator`** | OkHttp `Authenticator` that catches 401, asks `AuthRepository.refreshToken()`, retries **once** |
| **Optimistic toggle** | Local `FavoriteEntity` write happens before the server call; server failures don't roll back local state |
| **Safe server call** | `safeServerApiCall` — sibling of `safeApiCall`, parses `ServerErrorBody` only on 401, falls back to `ApiError.SERVER_ERROR.message` otherwise |
| **Hydrator** | `FavoritesHydrator` — joins server IDs with local `FavoriteEntity` rows; fills missing metadata from TMDB |
| **Server paging source** | `ServerFavoritesInMemoryPagingSource` — single‑page in‑memory `PagingSource<Int, Movie>`. Replaces the TMDB paging source |

---

## Phasing Summary

| Phase | User‑visible change | Risk | Verification |
|---|---|---|---|
| 0 | None | Low (DTOs only) | Contract tests; `compileDebugKotlin` |
| 1 | None (Bearer interceptor + Authenticator scaffolded) | Medium (authenticator bug = infinite loop) | Authenticator unit tests with bounded retry counter |
| 2 | First launch auto‑registers, favorites still work end‑to‑end | High (auth state machine + token store) | Auth + repository unit tests; existing 188 tests green |
| 3 | Favorites list fully hydrated; cross‑device favorites work | Medium | New hydrator tests; existing tests green |
| 4 | None (TMDB removal) | Low | `grep` for zero TMDB favorites references |
| 5 | None (smoke + docs) | Low | Instrumented smoke on opt‑in build only |

**Highest‑leverage skill:** Phase 1 + 2 — the auth state machine + 401 retry are the most error‑prone surface in the migration. Pull those tight and lean on the Authenticator unit tests.
**Stay‑in‑risk sanity check:** every phase keeps the existing 188 tests green. If they don't, the migration is leaking — stop and re‑scope.

---

> Each phase should be opened with a short plan (1–3 sentences) and closed with a verification step (the acceptance criteria above). **Discovery is complete** — the server contract above is the source of truth. If the contract ever changes upstream (e.g. server moves from JWT to mTLS, or `/auth/*` becomes `/auth/v2/*`), update the Discovery table at the top of this document before editing any phase.
