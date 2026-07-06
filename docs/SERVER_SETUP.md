# Server Setup

> **For:** anyone developing Android client changes that talk to the
> Kotlin favorites server (`https://moviesapp-server-production.up.railway.app`).
>
> The Spring Boot server sits in a separate repo: `tcohen/moviesapp-server`
> (or your local clone at `Movies-App-Server`). This document covers
> only the **Android** side of the migration — what the app expects
> from the server, and how to point it at a local instance while
> developing offline.

---

## What the Android client does

The Android app's `MovieRepositoryImpl` swaps TMDB favorites for
calls to **`https://moviesapp-server-production.up.railway.app`**:

| App action            | HTTP call                                              |
|-----------------------|--------------------------------------------------------|
| Tap a movie's ♡ on the Detail screen   | `POST /users/{userId}/favorites/{movieId}` |
| Tap again to un‑favorite              | `DELETE /users/{userId}/favorites/{movieId}` |
| Open the Favorites tab                | `GET /users/{userId}/favorites` + hydrate from local Room |
| First‑ever app launch                 | `POST /auth/register` with `X‑User‑Id` + `X‑Password` headers |
| Subsequent launches (token expired)   | `POST /auth/token` (refresh) |
| Every server request                  | `Authorization: Bearer <jwt>` header attached by `ServerBearerInterceptor` |

On a 401 from any server call, the OkHttp `ServerTokenAuthenticator`
attempts **one** `/auth/token` refresh + retry before giving up. Any
auth failure is surfaced to `_favoritesError` on the repository, which
a future UI can subscribe to.

---

## Where the user handle and password live

Both are auto‑generated on first launch — there is no login screen in
this migration.

| Item        | File / location                                  | Encryption                       |
|-------------|--------------------------------------------------|----------------------------------|
| `userId`    | SharedPreferences `server_user_id` file, key `userId`  | Plaintext (not a secret)         |
| `password`  | EncryptedSharedPreferences `auth_credentials`, key `password` | Android Keystore `AES256_GCM` master key + `AES256_SIV` keys + `AES256_GCM` values |
| `accessToken` (bearer) | Same EncryptedSharedPreferences file, key `access_token` | Same — encrypted at rest         |
| `expiresAt` (epoch ms) | Same EncryptedSharedPreferences file, key `expires_at`     | Same — encrypted at rest         |

The `userId` is a `UUID.randomUUID().toString()` canonical form
(`xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx`, 36 chars) — universally
unique (122 bits of entropy) against the server's
`users.user_id VARCHAR(64)` constraint. There's no collision‑recovery
loop: `409 UserAlreadyExists` from the server is treated like any
other non‑2xx and surfaces as `SignUpFailure`.

The `password` is a 20‑char alphanumeric string from `SecureRandom`.
It's not human‑typed, just stored once and used for `/auth/token`.

---

## How to point the Android app at a local server

By default `BuildConfig.SERVER_BASE_URL` points at the live Railway
deployment. Three ways to override it:

### Option A — `local.properties` (recommended for local dev)

Add to `local.properties`:

```properties
SERVER_BASE_URL=http://10.0.2.2:8080/
```

`10.0.2.2` is the Android emulator's alias for the host machine's
`localhost`. Use `localhost:8080` instead if running on a physical
device + adb‑reverse.

### Option B — Gradle property

```bash
./gradlew :app:installDebug -PSERVER_BASE_URL=http://localhost:8080/
```

### Option C — Edit `app/build.gradle.kts` directly

Default the `buildConfigField("String", "SERVER_BASE_URL", ...)`
line. **Don't** commit your change.

---

## What you'll see on first dev launch

```
Movies-App dev launch
   ├─ No userId in SharedPreferences →
   │   UserIdProvider.generateIfMissing() → "bright-velvet-moth"
   ├─ AuthRepository.signUpIfNeeded()
   │   ├─ generateIfMissing returns the handle
   │   ├─ generatePassword() → 32‑char alphanumeric
   │   ├─ POST /auth/register (X‑User‑Id, X‑Password)
   │   ├─ 200 OK → accessToken + tokenType
   │   └─ AuthStore.writeSnapshot(...)  ← encrypted at rest
   ├─ Toggle a favorite in the Detail screen →
   │   └─ POST /users/bright‑velvet‑moth/favorites/550
   └─ Open Favorites tab →
       └─ GET /users/bright‑velvet‑moth/favorites
           server returns: [{ movieId, savedAt }, ...]
           client hydrates via FavoritesHydrator (Room‑first, TMDB fallback)
```

Subsequent launches skip the register step if the cached token is
still within `AuthDefaults.TOKEN_TTL_MS` (24 h). After 24 h, the
Authenticator on the next 401 auto‑refreshes via `POST /auth/token`.

---

## Logcat redaction

The server interceptor chain (`ServerBearerInterceptor` +
`ServerNetworkModule.serverLoggingInterceptor`) ensures no JWT ever
appears in Logcat. Filter for the server's traffic with:

```
adb logcat -s SERVER_HTTP
```

Every request line includes `<-- METHOD URL` but **never** the bearer
header value — the wrapping logging interceptor rewrites
`Authorization: Bearer ...` to `Authorization: [REDACTED]` before
printing.

---

## Running against the live server

Hit `/actuator/health` first to confirm reachability:

```bash
curl https://moviesapp-server-production.up.railway.app/actuator/health
# {"status":"UP"}
```

Then exercise the auth + favorites flow with curl:

```bash
# 1. Register
USER="ck-test-$(date +%s)"
PASS="$(openssl rand -base64 24 | tr -d '/+= |')"
curl -sS -X POST \
    -H "X-User-Id: $USER" -H "X-Password: $PASS" \
    https://moviesapp-server-production.up.railway.app/auth/register | jq
# → {"accessToken":"eyJ...","tokenType":"Bearer"}

# 2. Add a favorite
TOKEN="eyJ..."        # paste from #1
curl -sS -X POST \
    -H "Authorization: Bearer $TOKEN" \
    https://moviesapp-server-production.up.railway.app/users/$USER/favorites/550 | jq
# → {"created":true}

# 3. List favorites
curl -sS \
    -H "Authorization: Bearer $TOKEN" \
    https://moviesapp-server-production.up.railway.app/users/$USER/favorites | jq
# → [{"movieId":550,"savedAt":175...}]

# 4. Cleanup
curl -sS -X DELETE \
    -H "Authorization: Bearer $TOKEN" \
    https://moviesapp-server-production.up.railway.app/users/$USER/favorites/550 -i
# → 204 No Content
```

Always clean up test users on the live server — the Postgres database
is persistent and grows over time.

---

## Local dev tips

- Watch the server's H2‑mode database tables — favorites get cleaned
  every test class thanks to `@BeforeEach` in `FavoritesControllerTest`.
- The ephemeral RSA‑2048 keypair generated on JVM startup means
  **every `POST /auth/register` issues the same JWT until restart** —
  wrong! `JwtService.issueToken` signs with the current key; **restart
  the server and old tokens are immediately invalid**. Plan dev
  work accordingly.
- The Android Keystore‑backed master key for EncryptedSharedPreferences
  does **not** survive app uninstalls in some emulators — if you
  re‑install the app mid‑debug, expect a fresh register round.
- Postgres mode in prod means the `users` and `favorites` tables
  outlive the JVM. Run `SELECT COUNT(*) FROM favorites;` periodically
  to see growth.

---

## Smoke test (opt‑in)

The instrumented `FavoritesServerJourneyTest` exercises the full flow
against the live server. It's gated by `BuildConfig.SERVER_SMOKE_TEST_ENABLED`,
default `false`:

```bash
# Enable for one run — never runs on default CI
./gradlew :app:connectedDebugAndroidTest -PSERVER_SMOKE_TEST_ENABLED=true
```

If the smoke test is left disabled (the default), only the unit tests
run. They cover the repository, hydrator, paging source, interceptor,
and authenticator — no live server needed.
