# LLM Setup Guide

> **Audience:** anyone cloning this repo who wants to exercise the AI features
> (plot explainer in Phase 1, semantic recommendations in Phase 3, the AI
> assistant chat in Phase 4, on-device inference in Phase 5).

The TMDB credentials are committed for convenience — they're read-only public
keys scoped to TMDB data. The LLM credentials are intentionally **not**
committed: they belong to your Google account and are rate-limited per-key, so
publishing them would burn your quota (or worse, get the key abused).

This guide walks through provisioning, drop-in setup, swapping providers, and
the prompt-versioning rules baked into the response cache.

---

## 1. Get a free Gemini API key

1. Visit **https://aistudio.google.com/app/apikey**.
2. Sign in with any Google account.
3. Click **"Create API key"** → pick an existing Google Cloud project or accept
   the default new one.
4. Copy the key string (starts with `AIza…`).

That's it. No credit card. No quota payment. Free tier covers personal use.

### Free-tier limits

| Limit | Value | What it means in practice |
|---|---|---|
| Requests / minute | 15 | one chat exchange every 4 seconds |
| Tokens / minute | 1,000,000 | several thousand normal replies |
| Requests / day | 1,500 | ~50 sustained back-and-forth conversations + plot explainers |

If you ever exceed these, every LLM call surfaces `ApiError.RATE_LIMITED` and
the UI shows *"Too many requests — please slow down and retry"*. Phase 5 adds
an on-device fallback in that case.

---

## 2. Drop the key into the project

Open `app/build.gradle.kts` and find the Phase 0 block:

```kotlin
// Phase 0 — AI/LLM provider. Empty by default; drop in your Gemini key
// from https://aistudio.google.com to enable. Without a key, every
// LLM call fails fast with ApiError.UNAUTHORIZED and the rest of the
// app continues to work normally.
buildConfigField("String", "GEMINI_API_KEY", "\"\"")
buildConfigField("String", "LLM_BASE_URL", "\"https://generativelanguage.googleapis.com/v1beta/openai/\"")
buildConfigField("String", "LLM_DEFAULT_MODEL", "\"gemini-2.0-flash\"")
```

Paste your key inside the empty quotes:

```kotlin
buildConfigField("String", "GEMINI_API_KEY", "\"AIzaSy...your-key...\"")
```

Sync Gradle (Android Studio → *Sync Now* — banner appears automatically — or
run `./gradlew :app:assembleDebug`). Done.

---

## 3. Verify it works

Launch the app and you should see no error toast on Home or Favorites (TMDB
loads as before). AI-touching surfaces — the plot explainer button on the
detail screen (Phase 1), the *More like this* row (Phase 3), the chat icon
(Phase 4) — will return text instead of an error message.

If you see *"Couldn't authenticate with the AI provider — check your API
key"*, double-check the string in `build.gradle.kts` (no trailing spaces,
still inside the inner open/close quotes) and that you re-synced Gradle.

---

## 4. Swap providers (one line each)

The `LlmClient` is a transport-agnostic interface. Every consumer of
`LlmClient` (chat, plot explainer, vector search) is unaware of the underlying
provider. To switch from Gemini to **Groq**, **OpenRouter**, **OpenAI**, or
**Together**, change just two `buildConfigField` values:

```kotlin
// Groq (Llama 3.3 70B, very cheap, very fast)
buildConfigField("String", "LLM_BASE_URL", "\"https://api.groq.com/openai/v1/\"")
buildConfigField("String", "LLM_DEFAULT_MODEL", "\"llama-3.3-70b-versatile\"")

// OpenRouter (multi-model aggregator)
buildConfigField("String", "LLM_BASE_URL", "\"https://openrouter.ai/api/v1/\"")
buildConfigField("String", "LLM_DEFAULT_MODEL", "\"google/gemini-2.0-flash-exp:free\"")

// OpenAI
buildConfigField("String", "LLM_BASE_URL", "\"https://api.openai.com/v1/\"")
buildConfigField("String", "LLM_DEFAULT_MODEL", "\"gpt-4o-mini\"")
```

Only `LLM_BASE_URL` and `LLM_DEFAULT_MODEL` change. The `GEMINI_API_KEY`
field can be renamed (or just repointed to your new provider's key) without
touching any Kotlin code.

> **Securing the key in production** — for a portfolio project the committed
> empty placeholder is fine; for a release build, move the key to
> `local.properties` (gitignored) and read it from there:
>
> ```kotlin
> val geminiKey = providers.gradleProperty("GEMINI_API_KEY").orNull ?: ""
> buildConfigField("String", "GEMINI_API_KEY", "\"$geminiKey\"")
> ```
>
> Then `echo "GEMINI_API_KEY=AIza…" >> local.properties` locally. Anything in
> `local.properties` never enters git history.

---

## 5. Response cache key & prompt versioning

The `LlmResponseCache` keys each entry by **sha256(prompt_version + model +
temperature + max_tokens + messages)**:

```
sha256("phase0-v1" | "gemini-2.0-flash" | 0.7 | 200 | "[user]hello;" | …)
```

That means an identical request is a cache hit regardless of when you made it
or whether the model was "warm". Two requests that produce different LLM output
produce different keys automatically.

**Bumping** the **prompt version** is how you deliberately invalidate the cache
when the system prompt of any feature changes semantically (tone change,
new constraints, schema update). Each feature owns its own constant:

| Phase | Constant | Owner |
|---|---|---|
| 0  | `phase0-v1` | `OpenAiCompatibleLlmClient.PROMPT_VERSION_DEFAULT` |
| 1  | `plot-v1` | `PlotExplainerPrompt.Companion` |
| 3  | `recs-v1` | `MoreLikeThisPrompt.Companion` |
| 4  | `chat-v1` | `ChatAgentDefaults` |

To force a cache wipe after a meaningful prompt change, just bump the version
string (`plot-v1` → `plot-v2`). The hashed key is different, the next user
request hits the network, the old entry eventually expires.

---

## 6. Cost control

By default the LLM layer has **no rate limiter**; it relies on the provider
free tier to keep costs at zero. The 250k-tokens/day budget referenced in the
implementation plan ships in **Phase 2** (response cache & streaming
hardening) and will surface in a Settings screen.

Until then, the natural cost levers you already have:

| Lever | Where |
|---|---|
| Switch to cheaper model | `LLM_DEFAULT_MODEL` in `build.gradle.kts` |
| Switch to free aggregator | OpenRouter `:free` models — change `LLM_BASE_URL` |
| Cache aggressively | already on by default for `complete()`, see `LlmResponseCache` |

**Phase 5** adds the on-device Gemma 2B fallback — completely free, runs on
your phone, no network — which is the portfolio-friendly escape hatch when
hosted providers change pricing.

---

## 7. Troubleshooting

| Symptom | Likely cause |
|---|---|
| `ApiError.UNAUTHORIZED` toast | Empty key, bad key, or re-sync needed after editing `build.gradle.kts` |
| `ApiError.RATE_LIMITED` toast | Hit 15 req/min — wait a minute, or use a slower model |
| `NetworkResult.Error(ApiError.CONTEXT_TOO_LONG…)` | Conversation too long — start a new chat |
| Streaming bubbles stutter mid-sentence | Network blip — `safeLlmCall` retries on the next attempt; the partial text is preserved |

Logcat under tag `TMDB_HTTP` also captures LLM HTTP traffic in debug builds.
Use `adb logcat -s TMDB_HTTP` to inspect request/response bodies.
