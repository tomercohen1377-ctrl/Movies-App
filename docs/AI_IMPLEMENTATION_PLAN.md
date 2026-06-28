# AI Implementation Plan

> **Status:** Draft — 6 phases, single Gradle module (`:app`), Google Gemini `gemini-2.0-flash` as the default provider (OpenAI-compatible HTTP API, free tier).
>
> Every phase must not break the existing 188 tests. Every new ViewModel gets unit tests, every new composable gets UI tests, and AI-specific behavior gets an evaluation harness.

---

## Table of Contents

- [Goals](#goals)
- [Provider & Cost](#provider--cost)
- [Rules — Non-Negotiable](#rules--non-negotiable)
- [Architecture Additions](#architecture-additions)
- [Phase 0 — LLM Foundation](#phase-0--llm-foundation-no-ui)
- [Phase 1 — Plot Explainer](#phase-1--plot-explainer-on-detail)
- [Phase 2 — Response Cache & Streaming Plumbing](#phase-2--response-cache--streaming-plumbing)
- [Phase 3 — Semantic "More Like This"](#phase-3--semantic-more-like-this)
- [Phase 4 — AI Assistant Chat with Tool Calling](#phase-4--ai-assistant-chat-with-tool-calling)
- [Phase 5 — On-Device LLM Switch](#phase-5--on-device-llm-switch)
- [Phase 6 — Observability & Evaluation Harness](#phase-6--observability--evaluation-harness)
- [Glossary](#glossary)

---

## Goals

1. Add AI features that genuinely earn their place: semantic recommendations, tool‑calling assistant, and an offline LLM option.
2. Match the existing quality bar — Clean Architecture, MVI, sealed UI states, `NetworkResult<T>` errors, Room caching, Paging 3 where applicable, and the same 3‑tier testing pyramid.
3. Teach the skills that make an AI engineer: prompt design, function/tool calling schemas, streaming UX, retrieval, agent loops, cost control, evaluation, and on-device inference.

---

## Provider & Cost

| Choice | Value | Why |
|---|---|---|
| Default model | `gemini-2.0-flash` | Free tier (15 RPM, 1500 RPD), supports tool calling + streaming, OpenAI‑compatible schema |
| HTTP shape | OpenAI‑compatible `/chat/completions` | Same client works for Groq, OpenRouter, OpenAI, Together — swap by base URL + key |
| Embedding (Phase 3) | `text-embedding-3-small` ($0.02/1M tokens) | One‑time pass over ~6k TMDB overviews ≈ $0.025, monthly queries ≈ $0 |
| Fallback (Phase 5) | MediaPipe LLM Inference (Gemma 2B) | Free forever, fully offline, key for portfolio |
| Budget guardrail | Per‑day token cap surfaced in Settings | Prevents surprise bills; default 250k tokens/day |

API key is added to `build.gradle.kts` like the existing TMDB values — read‑only, scoped to chat completions + embeddings.

---

## Rules — Non-Negotiable

These rules apply to every phase below. They mirror the conventions already established in the repo.

### Architecture rules

- **Domain layer stays pure Kotlin.** No `android.*`, no Retrofit, no Hilt annotations in `domain/`. Repository interfaces, models, and value objects only.
- **New screens follow MVI.** Every screen has a `Contract` file with `State`, `Intent`, `Effect`. Use **sealed UI state** for screens with mutually exclusive render branches (matches `MovieDetailUiState`).
- **New public APIs return `NetworkResult<T>`** when the call can fail and the UI should show the failure inline. Match the existing `safeApiCall` shape: connectivity → `ApiError.NO_CONNECTION`, etc.
- **New cross‑cutting concerns get Hilt modules by name.** `LlmModule`, `EmbeddingModule`, `ChatModule` — one per concern, in `di/`.
- **Single Gradle module (`:app`).** New code lives under `com.tcohen.moviesapp.ai.*` with package mirroring the multi‑module layout it would later occupy: `ai/data/`, `ai/domain/`, `ai/presentation/`, `di/`.

### Code style rules

- **No magic numbers or magic strings.** Every literal lives in a `companion object` or a private defaults object co‑located with its owner (see `MovieRepositoryImpl.Companion.VIDEO_SITE_YOUTUBE`, `MovieCardDefaults.ASPECT_RATIO`).
- **Every public/internal function and property has KDoc.** State changes, intent handlers, and tool definitions explain the why.
- **No function longer than ~30 lines.** Long ones split into named private helpers.
- **No raw `TODO()` in committed code.** Phases ship complete or split into a child phase.
- **Constants are `private` unless another file needs them.** LLM prompts, model names, URL bases go in `companion object`s.
- **Add `@Preview` on every new composable.**
- **Add `testTag(...)` on every new clickable / testable composable**, matching the existing `movie_card`, `offline_banner` convention.

### AI‑specific rules

- **All LLM calls go through `LlmClient` interface.** Retrofit impl (`OpenAiCompatibleLlmClient`) and on‑device impl (`MediaPipeLlmClient`) both satisfy it. Code outside the `ai/` package never sees the transport.
- **Tool definitions live in a `ToolRegistry`.** Each tool has a stable JSON schema (name, description, parameters), a Kotlin signature, and a deterministic `String → ToolResult` executor.
- **Tool arguments are validated against the schema before execution.** If a model hallucinates an ID or a missing field, we return a `ToolResult.Error` so the LLM can self‑correct next round.
- **Conversation memory is bounded.** Sliding window of last `MAX_TURNS_FOR_CONTEXT` (default 8) + a one‑line rolling summary. The summary itself is generated via LLM (cheap, structured).
- **LLM responses are cached in Room** keyed by `sha256(prompt + model + temperature + tools_version)`. Cache hits never call out.
- **Streaming uses `Flow<String>`.** The ViewModel collects, `collectAsState` drives the bubble. Cancellation is `viewModelScope`‑bound — no leaked streams.
- **Cost is tracked.** Every LLM call accumulates `inputTokens`/`outputTokens` in `AiUsageDao`; a daily cap is enforced before the request leaves the device.
- **PII redaction is out of scope, but prompts must never embed user account IDs, session IDs, or any `BuildConfig` secret.** The chat system prompt is fixed static text.

### Testing rules

- **Unit tests for every new ViewModel** — MockK + Turbine + `MainDispatcherRule`. Cover every state transition + every intent.
- **Unit tests for every new repository / data source** — MockK. Cover success, network error, validation error.
- **Unit tests for the `ToolRegistry`** — each tool given synthetic args. Hallucinated‑args tests are mandatory (hallucinated ID → `ToolResult.Error`).
- **UI component tests for every new composable** — `createComposeRule`. Cover loading, success (partial + full), error, empty, and offline if applicable.
- **Determinism rule:** any test that touches a real LLM is **not** a unit test. Live‑LLM behavior belongs in the **evaluation harness** (Phase 6), gated behind a build flag, never green‑on‑CI‑default.
- **Existing tests must remain green** at every phase boundary. Add new tests incrementally; the previous phase's tests are the regression net for the next phase.

---

## Architecture Additions

```
app/src/main/java/com/tcohen/moviesapp/
├── ai/
│   ├── domain/
│   │   ├── model/           ChatMessage, ChatRole, ChatCompletion, ChatRequest, MessagePart
│   │   ├── tool/            ToolDefinition, ToolParameter, ToolResult, ToolRegistry
│   │   ├── embedding/       Embedding model + VectorSearcher interface
│   │   └── repository/     ChatRepository, EmbeddingRepository, AiUsageRepository
│   ├── data/
│   │   ├── remote/
│   │   │   ├── api/         OpenAiCompatibleApi (Retrofit interface)
│   │   │   ├── dto/         ChatCompletionRequest/Response, ToolCallDto, EmbeddingResponse
│   │   │   ├── interceptor/ LlmAuthInterceptor, LlmLoggingInterceptor
│   │   │   ├── client/      OpenAiCompatibleLlmClient, OpenAiCompatibleEmbeddingClient
│   │   │   └── streaming/   SseStreamParser (Flow<String> of token deltas)
│   │   ├── local/
│   │   │   ├── entity/      ChatMessageEntity, ChatSessionEntity, MovieEmbeddingEntity, AiUsageEntity
│   │   │   ├── dao/         ChatDao, MovieEmbeddingDao, AiUsageDao
│   │   │   └── cache/       ResponseCacheDao + ResponseCacheEntity (sha256 keyed LLM response cache)
│   │   ├── repository/      *RepositoryImpl (each domain repository above)
│   │   └── safe/            safeLlmCall — analogue of safeApiCall, returning NetworkResult<T>
│   └── presentation/
│       ├── chat/            ChatScreen, ChatViewModel, ChatContract, ChatComponents
│       ├── ploexplainer/    PlotExplainerSection (embedded in detail), PlotExplainerViewModel
│       ├── morelikethis/    MoreLikeThisRow (embedded in detail), MoreLikeThisViewModel
│       ├── chatnav/         Chat route registered in AppNavGraph
│       └── common/          AiMarkdownText (token-safe Compose Markdown), StreamingText
└── di/
    ├── LlmModule.kt         (provides LlmClient, OkHttpClient@Named("llm"), Retrofit@Named("llm"))
    ├── EmbeddingModule.kt   (provides EmbeddingClient, VectorSearcher)
    └── ChatModule.kt        (binds repositories + ToolRegistry)
```

**Room migration:** `AppDatabase` moves from v1 → v2 with a migration that adds 4 new tables (`chat_sessions`, `chat_messages`, `movie_embeddings`, `ai_usage`, `llm_response_cache`). The migration:

1. Creates the new tables.
2. Adds indices the new DAOs depend on.
3. **Does not** touch the existing `movies` and `favorites` tables (preserve user data).

**Navigation:** `Screen.Chat` becomes the optional 3rd bottom‑nav destination. Phase 1/3 do not add a tab — they are embedded in existing screens.

---

## Phase 0 — LLM Foundation (No UI)

**Goal:** Every later phase plugs into a working `LlmClient` and an LLM‑specific error model. Nothing user‑visible yet.

**Files to add:**

| Path | Purpose |
|---|---|
| `ai/domain/model/ChatMessage.kt` | `ChatRole` (`SYSTEM`, `USER`, `ASSISTANT`, `TOOL`), `ChatMessage(role, content)`, `MessagePart` |
| `ai/domain/tool/ToolDefinition.kt` | `ToolDefinition(name, description, parameters, executor: (JsonObject) -> ToolResult)`, `ToolParameter(name, type, required, description)`, `ToolResult` (`Success(text)` / `Error(message)`) |
| `ai/domain/tool/ToolRegistry.kt` | Holds a `Map<String, ToolDefinition>`, exposes `definitions(): List<ToolDefinition>` + `execute(name, args): ToolResult` |
| `ai/data/remote/dto/*` | Request/Response DTOs (`@Serializable`) — shape parity with OpenAI `chat/completions` |
| `ai/data/remote/streaming/SseStreamParser.kt` | `Flow<String>` that yields token deltas from `text/event-stream` chunks |
| `ai/data/remote/client/OpenAiCompatibleLlmClient.kt` | Implements `LlmClient` (interface defined here) using Retrofit |
| `ai/data/safe/safeLlmCall.kt` | Mirrors `safeApiCall`: maps 401→`ApiError.UNAUTHORIZED`, 429→`ApiError.RATE_LIMITED`, 5xx→`ApiError.SERVER_ERROR`, IO→`ApiError.NO_CONNECTION` |
| `ai/data/local/cache/ResponseCacheDao.kt` + `ResponseCacheEntity` | Keyed by `sha256(model + system + messages + temp + toolVersion)`, stores the full assistant text + tool calls |
| `util/ApiError.kt` | **Add new entries:** `RATE_LIMITED`, `CONTEXT_TOO_LONG`, `UNAUTHORIZED`, `LLM_UNAVAILABLE`. Each entry owns its user‑facing message string |
| `di/LlmModule.kt` | Hilt module: provides `OkHttpClient` `@Named("llm")` (separate from `image` and `api`), `Retrofit` `@Named("llm")`, `LlmClient` |
| `build.gradle.kts` | `buildConfigField("String", "GEMINI_API_KEY", "\"...\"")`; `buildConfigField("String", "LLM_BASE_URL", "\"https://generativelanguage.googleapis.com/v1beta/openai/\"")` |
| `app/src/test/.../SseStreamParserTest.kt` | Parses fenced `data: {...}` chunks, splits on `data: `, yields delta strings, ends on `[DONE]` |
| `app/src/test/.../safeLlmCallTest.kt` | Maps `HttpException(429)`, `IOException`, and `SocketTimeoutException` to the right `NetworkResult.Error` |
| `app/src/test/.../ResponseCacheDaoTest.kt` | Robolectric (Room‑in‑memory) — set/get round‑trip; sha256 key collision → hit |

**Architecture decisions:**

- **LlmClient interface** lives in `ai/domain/` so domain depends only on the abstraction.
  ```kotlin
  interface LlmClient {
      suspend fun complete(request: ChatRequest): NetworkResult<ChatCompletion>
      fun stream(request: ChatRequest): Flow<NetworkResult<String>>   // NetworkResult per chunk so a mid-stream error can propagate cleanly
  }
  ```
- **`safeLlmCall` is a thin wrapper around the Retrofit call.** Same shape as existing `safeApiCall` (`Result<T>` → `NetworkResult<T>`), same error mapping. Not a copy‑paste hell — both call a shared `mapThrowable(e: Throwable, httpCode: Int): NetworkResult.Error`.
- **Response cache is a write‑through DAO.** Every successful completion writes the cache entry inside the same transaction as the response; the next identical request skips the network entirely.

**Acceptance criteria:**

- `./gradlew testDebugUnitTest` passes (all 101 existing tests + ~6 new ones stay green).
- The Gemini client is wired and a one‑shot `LlmClient.complete()` call against `gemini-2.0-flash` works in instrumented test (gated by a `BuildConfig` flag — no CI side effect).
- `LlmModule` is documented in KDoc with: model list swap instructions, base‑URL swap instructions.

---

## Phase 1 — Plot Explainer (On Detail)

**Goal:** Working end‑to‑end LLM flow with a tiny UI footprint. Proves the foundation, builds confidence before bigger features.

**Files to add:**

| Path | Purpose |
|---|---|
| `ai/presentation/ploexplainer/PlotExplainerContract.kt` | `PlotExplainerState` (sealed): `Idle`, `Streaming(text)`, `Done(text)`, `Error(message)`. Intents: `Explain`, `Cancel`, `Reset` |
| `ai/presentation/ploexplainer/PlotExplainerViewModel.kt` | Owns the streaming collection, cancellation tied to `viewModelScope` |
| `ai/presentation/ploexplainer/PlotExplainerSection.kt` | Bottom sheet or inline section under `MovieDetailContent` with a `Start` button, streaming text bubble, retry, cancel |
| `app/src/main/.../MovieDetailScreen.kt` (modify) | Host `PlotExplainerSection` inside the `Success` branch — mirrors how `FAB` is rendered only when state is `Success` |
| `app/src/test/.../PlotExplainerViewModelTest.kt` | Every state transition; cancellation mid‑stream emits `Error("cancelled")`; test that the cached response hit on the second click does not invoke `LlmClient.complete(…)` |

**Architecture decisions:**

- **Plot explainer uses a single static prompt template** in `PlotExplainerPrompt.kt`:
  `companion object { const val SYSTEM = "You are a concise movie plot narrator. Avoid spoilers. Use plain English."; const val USER_TEMPLATE = "Explain the plot of \"%s\" (%d) in %d sentences, %, no spoilers." }`
- **Caching kicks in on the second click.** We see it in the test (no LLM call on hit).
- **Streaming bubble uses `animateContentSize`** — visually obvious each token arriving.

**Constants:**

| Constant | Owner |
|---|---|
| `PLOT_EXPLAINER_TEMPERATURE = 0.4f` | `PlotExplainerPrompt.Companion` |
| `PLOT_EXPLAINER_MAX_TOKENS = 200` | `PlotExplainerPrompt.Companion` |
| `PROMPT_VERSION = "plot-v1"` | `PlotExplainerPrompt.Companion` |

**Acceptance criteria:**

- New ViewModel `PlotExplainerViewModelTest` with at least 6 cases.
- New component test `PlotExplainerSectionTest` covering Idle, Streaming, Done, Error.
- Detail screen flow test extended: tapping "Explain plot" streams a response (using a fake `LlmClient` returning scripted chunks).

---

## Phase 2 — Response Cache & Streaming Plumbing Hardening

**Goal:** Before adding heavier features (embeddings, RAG, chat), make sure caching, streaming, and cost tracking are solid.

**Files to add/modify:**

| Path | Purpose |
|---|---|
| `ai/data/local/cache/ResponseCacheEntity.kt` + DAO | Already added in Phase 0; harden with indexes and expiry (TTL = 7 days for stable prompts) |
| `ai/data/repository/AiUsageRepositoryImpl.kt` | Counts tokens, enforces `DAILY_TOKEN_CAP`, exposes `observeUsageToday(): Flow<Int>` (drives Settings UI) |
| `ai/data/local/entity/AiUsageEntity.kt` + DAO | One row per completed call: `model`, `inputTokens`, `outputTokens`, `timestamp` |
| `ai/presentation/common/StreamingText.kt` | Reusable composable used by every later streaming surface |
| `ai/presentation/common/AiMarkdownText.kt` | Wraps a Markdown renderer; safe for partial tokens (tolerates unterminated `**`, `[`, etc — does not crash on a half‑chunk) |
| `app/src/test/.../AiUsageRepositoryImplTest.kt` | Cap enforcement emits an error before the LLM call is made; counting matches a synthetic completion |

**Architecture decisions:**

- **Daily cap is enforced in the repository, not the client.** The client is a dumb pipe.
- **Cache TTL is per‑entity.** `PROMPT_VERSION = "chat-v3"` rows live longer than `PROMPT_VERSION = "plot-v1"` ones if we want — for now keep it simple (7 days flat).

**Constants:**

| Constant | Owner |
|---|---|
| `DAILY_TOKEN_CAP = 250_000` | `AiUsageDefaults` (object) |
| `RESPONSE_CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000L` | `ResponseCacheDefaults` |
| `COST_PER_1K_INPUT_TOKENS_USD`, `COST_PER_1K_OUTPUT_TOKENS_USD` | `AiUsageDefaults` |

**Acceptance criteria:**

- `AiUsageRepositoryImplTest` shows no LLM call when over cap.
- Streaming text rendered from a 5‑chunk synthetic stream produces exactly the concatenated string.

---

## Phase 3 — Semantic "More Like This"

**Goal:** Embedding pipeline + a small `MoreLikeThisRow` on the detail screen. Establishes the pattern for all RAG-ish features.

**Files to add:**

| Path | Purpose |
|---|---|
| `ai/domain/embedding/VectorSearcher.kt` | `suspend fun similar(movieId: Int, k: Int): List<Movie>` — domain‑level interface |
| `ai/data/local/entity/MovieEmbeddingEntity.kt` | `movieId` (PK), `embedding: ByteArray` (Float16 packed), `modelVersion`, `embeddedAt` |
| `ai/data/local/dao/MovieEmbeddingDao.kt` | `upsert`, `getAll()`, `getById(id)`, `deleteOlderThan(version)` |
| `ai/data/remote/client/OpenAiCompatibleEmbeddingClient.kt` | Implements `EmbeddingClient` interface; defaults to `text-embedding-3-small` |
| `ai/data/repository/EmbeddingRepositoryImpl.kt` | Builds text from `Movie.overview + tagline + genres`; uploads batch to embedding API; computes cosine similarity on‑device with packed Float16 |
| `ai/data/repository/VectorSearcherImpl.kt` | Loads the full embedding table into RAM (6k rows × 1.5KB Float16 ≈ 9MB), brute‑force cosine — fine for this scale, no separate vector DB needed |
| `ai/presentation/morelikethis/MoreLikeThisRow.kt` + ViewModel + Contract | A horizontal `LazyRow` of `MovieCard`s; "More like this" header; loading/error/empty states |
| `MovieDetailScreen.kt` (modify) | Render `MoreLikeThisRow` below `MovieMetadata` inside the `Success` branch |
| `app/src/test/.../EmbeddingRepositoryImplTest.kt` | Batch request correctness, Float16 pack/unpack round‑trip, embedding cache hit on second run |
| `app/src/test/.../VectorSearcherImplTest.kt` | Synthesize 5 embeddings → top‑k works, similarity ordering correct, ties broken by id ascending |
| `app/src/test/.../MoreLikeThisViewModelTest.kt` | Loading → Success (N items), Error, Empty (no neighbors above threshold) |

**Architecture decisions:**

- **One‑off embedding backfill via a `WorkManager` job.** `EmbeddingBackfillWorker` runs once per cache age; idempotent (skips existing rows). Scheduled in `MoviesApp.onCreate()`.
- **No external vector DB.** Brute force on packed Float16 is fast enough at this scale and avoids another moving part.
- **Thresholds:** `MIN_COSINE_SIMILARITY = 0.65f`. Below this we hide the row (avoid bad recommendations).

**Constants:**

| Constant | Owner |
|---|---|
| `EMBEDDING_MODEL = "text-embedding-3-small"` | `EmbeddingDefaults` |
| `EMBEDDING_DIMENSIONS = 1536` | `EmbeddingDefaults` |
| `MIN_COSINE_SIMILARITY = 0.65f` | `VectorSearcherDefaults` |
| `MORE_LIKE_THIS_TOP_K = 10` | `MoreLikeThisDefaults` |
| `EMBEDDING_BACKFILL_BATCH_SIZE = 100` | `EmbeddingDefaults` |

**Acceptance criteria:**

- One‑time backfill of all TMDB movies completes (`./gradlew connectedDebugAndroidTest` — instrumented embedding test logs `embeddedCount`).
- A query like "More like The Godfather" returns a sensible list (manual smoke test; recorded in the eval harness later).

---

## Phase 4 — AI Assistant Chat with Tool Calling

**Goal:** The headline feature. Tool‑calling chat that calls real repository methods as tools.

**Files to add:**

| Path | Purpose |
|---|---|
| `ai/domain/tool/ToolDefinitions.kt` | 5 concrete tools wired to `MovieRepository` and `VectorSearcher`: |
| | 1. `search_movies(query, category?, year?, min_rating?)` |
| | 2. `get_movie_detail(id OR title)` |
| | 3. `toggle_favorite(movie_id, action: "add"|"remove")` |
| | 4. `list_favorites(filter?)` |
| | 5. `get_recommendations(seed_movie_id, mood?)` |
| `ai/data/repository/ChatRepositoryImpl.kt` | Owns the agent loop: alternates `assistant → tool_call → tool_result → assistant` until the model emits a final message or hits `MAX_TOOL_ROUNDS = 4` |
| `ai/data/local/entity/ChatSessionEntity.kt`, `ChatMessageEntity.kt` + DAOs | Session list per chat; messages ordered by `createdAt` |
| `ai/presentation/chat/ChatContract.kt` | `ChatState(sessions, currentSessionId, messages, inputDraft, streamingText, toolCallInProgress)`. Sealed `ChatMessage` (User/Assistant/Tool). Intents: `Send(text)`, `Cancel`, `SelectSession`, `NewSession`, `DeleteSession` |
| `ai/presentation/chat/ChatViewModel.kt` | Drives the agent loop. Holds the `ChatMessage` list, the streaming delta, and one in‑flight tool call at a time |
| `ai/presentation/chat/ChatScreen.kt` | Material 3 `Scaffold`; `LazyColumn` of messages; sticky input bar; optional session drawer |
| `ai/presentation/chat/ChatComponents.kt` | `UserBubble`, `AssistantBubble`, `ToolCallChip`, `StreamingText` |
| `presentation/navigation/AppNavGraph.kt` (modify) | New `Screen.Chat` route, conditionally added as 3rd bottom‑nav tab behind a `@Named("chatEnabled") Boolean` |
| `app/src/test/.../ChatRepositoryImplTest.kt` | Agent loop terminates on `finish_reason: stop` and on `MAX_TOOL_ROUNDS`. Tool args validation. Hallucinated `movie_id` → `ToolResult.Error`. Tool execution verifies against `MovieRepository` mocks |
| `app/src/test/.../ChatViewModelTest.kt` | Every state transition. Sending a message → streaming → tool chip → final assistant message. Cancel mid‑tool emits `Error("cancelled")` and rolls back optimistic state |
| `app/src/androidTest/.../ChatScreenTest.kt` | UI test with a fake `LlmClient` simulating a 3‑round tool call |

**Architecture decisions:**

- **The agent loop is in the repository.** ViewModel only dispatches user intent and observes `Flow<ChatEvent>`.
- **Optimistic tool calls.** Toggling a favorite shows up in the favorites tab instantly (`favoriteChanges` SharedFlow already wired from Phase 0).
- **Tool argument validation lives in `ToolDefinition.executor`.** Empty catch‑all → `ToolResult.Error("invalid arg: …")`.
- **MAX_TOOL_ROUNDS = 4.** Prevents infinite loops if the model gets stuck. The loop yields a final assistant turn saying "I wasn't able to complete that" before terminating.
- **Memory strategy:** last 8 turns + 1 summary. The summary is itself a cheap LLM call that runs in `ChatRepositoryImpl.consolidateMemory()` (called when the session crosses 10 turns).

**Constants:**

| Constant | Owner |
|---|---|
| `MAX_TOOL_ROUNDS = 4` | `ChatAgentDefaults` |
| `MAX_TURNS_FOR_CONTEXT = 8` | `ChatAgentDefaults` |
| `MEMORY_CONSOLIDATION_THRESHOLD = 10` | `ChatAgentDefaults` |
| `CHAT_SYSTEM_PROMPT_VERSION = "chat-v3"` | `ChatAgentDefaults` |
| `CHAT_DAILY_TOKEN_BUDGET = 100_000` | `AiUsageDefaults` (chat has its own sub‑budget) |

**Acceptance criteria:**

- A scripted LlmClient sequence (assistant → tool_calls[search_movies] → tool_result → assistant text) is unit‑tested end‑to‑end at the repo level.
- A manual smoke test: *"Recommend something like Interstellar but less than 2 hours"* calls `get_recommendations(329)`, filters runtime, returns a list, and the detail card is tappable — the assistant message includes a working UI chip.
- `favoritesChanges` flow integration verified: toggling via chat adds the movie to the Favorites tab without app restart.

---

## Phase 5 — On-Device LLM Switch

**Goal:** Settings toggle that swaps `LlmClient.complete()` over to a fully local model. Demonstrates cross‑provider abstraction.

**Files to add:**

| Path | Purpose |
|---|---|
| `ai/data/remote/client/MediaPipeLlmClient.kt` | Implements `LlmClient` using MediaPipe LLM Inference. Wraps model download, accelerator selection (CPU/GPU/NNAPI), cancellation |
| `ai/data/local/entity/LocalModelEntity.kt` + DAO | Tracks `LocalLlmModel` rows (name, path, sizeBytes, downloadedAt) |
| `presentation/settings/SettingsScreen.kt` (new) | Provider dropdown (Hosted Gemini / Local Gemma 2B / Local Phi‑3 mini), token cap, prompt version display |
| `ai/presentation/common/ModelDownloadCard.kt` | Shows download progress, requires user confirmation (>500MB) |
| `di/LlmModule.kt` (modify) | Provides both impls behind a `LlmProvider` enum binding; Settings writes to `DataStore`; `LlmClient` is `ProviderScoped` and re‑binds via a top‑level mutable `MutableStateFlow<LlmProvider>` |

**Architecture decisions:**

- **`LlmClient` is wrapped in `SwitchingLlmClient`** that delegates to whatever provider is currently active. The chat, plot explainer, and more‑like‑this layers are unaware.
- **Local model fallback rule:** if hosted fails with `ApiError.RATE_LIMITED` or `LLM_UNAVAILABLE`, the UI offers "try local?" rather than silently swapping.
- **Cancellation must reach MediaPipe.** Wrap the inference call in `coroutineContext.isActive` checks; cancellation propagates via `CancellationException`.

**Constants:**

| Constant | Owner |
|---|---|
| `LOCAL_MODEL_PRIMARY = "gemma-2b-it-int4"` | `LocalLlmDefaults` |
| `LOCAL_MODEL_FALLBACK = "phi-3-mini-4k-int4"` | `LocalLlmDefaults` |
| `LOCAL_MODEL_STORAGE_DIR = "local_llm/"` | `LocalLlmDefaults` |
| `LOCAL_MODEL_INFERENCE_TIMEOUT_MS = 30_000L` | `LocalLlmDefaults` |

**Acceptance criteria:**

- With hosted disabled (airplane mode), toggling to Local Gemma and starting a chat produces a response end‑to‑end (manual smoke test on device).
- Switching providers mid‑stream cancels the previous one cleanly (no zombie requests in logs).

---

## Phase 6 — Observability & Evaluation Harness

**Goal:** Treat the LLM stack as production software. Logs, eval, cost controls, prompt versioning.

**Files to add:**

| Path | Purpose |
|---|---|
| `ai/data/local/entity/AiLogEntity.kt` + DAO | Every prompt/response pair: `model`, `temperature`, `tokens`, `latencyMs`, `toolsCalled`, `errorCode` |
| `ai/data/repository/AiUsageRepositoryImpl.kt` (extend) | Writes a log row on every completion (success or failure) |
| `presentation/settings/AiLogsList.kt` | View the last 100 calls — for debugging; redact user input text in production |
| `app/src/androidTest/.../AiEvalTest.kt` | Asserts canned prompt/output pairs (goldens). Uses a `FakeLlmClient` keyed by prompt hash. Demo only — not a real LLM eval |
| `app/src/journey/.../AiJourneyE2ETest.kt` | "Type `Recommend like Interstellar`. Verify a rec card renders and is tappable." Uses a scripted `LlmClient` so the test is deterministic and CI‑safe |

**Architecture decisions:**

- **Logs are local‑only and capped at 100 rows.** Server aggregation is out of scope here.
- **Eval tests are not required to pass CI — they are a developer tool.** Gated behind a Gradle task: `./gradlew runAiEval`. They use a recorded response file under `app/src/test/resources/ai_eval/` to be deterministic.
- **Prompt versioning changes are explicit.** Bumping `CHAT_SYSTEM_PROMPT_VERSION` invalidates the response cache for that version.

**Acceptance criteria:**

- `./gradlew runAiEval` prints a pass/fail table for each canned prompt (5+ cases).
- A `Movie.json` golden file under `ai_eval/` documents expected behavior for "Explain Inception".
- `AiLogEntity` rows visible in `AiLogsList` show: prompt, response, token count, latency — for hosted and local both.

---

## Glossary

| Term | Meaning |
|---|---|
| **Tool calling** | The LLM emits structured JSON tool invocations rather than plain text; we parse them and run code, then feed results back |
| **Prompt version** | A string constant attached to every system prompt; bumped when the prompt changes; invalidates response cache |
| **Response cache** | Room table keyed by `sha256(model + prompt + messages + temperature + tools_version)`; stores the exact completion |
| **Vector searcher** | Domain interface that takes a movie id and returns top‑k similar movies by cosine similarity over Float16 embeddings |
| **Agent loop** | The repeating assistant → tool_calls → tool_results → assistant cycle until the model emits `finish_reason: stop` or we hit `MAX_TOOL_ROUNDS` |
| **Eval harness** | Offline test suite (not in CI) that replays canned prompts through `LlmClient` and asserts outputs against recorded goldens |

---

## Phasing Summary & Risk Callouts

| Phase | New user‑visible surface | LLM volume / phase | Risk |
|---|---|---|---|
| 0 | None | 0 | Low — pure plumbing |
| 1 | "Explain" button on detail | Low | Low — caches aggressively |
| 2 | None (hardening) | 0 | Low — internal |
| 3 | "More like this" row on detail | Medium (one‑off backfill) | Medium — embedding cost + storage |
| 4 | Chat tab | Medium‑high | Highest — agent loops, hallucination, cost |
| 5 | Settings provider toggle | 0 (download) | Medium — model size, on‑device latency |
| 6 | Settings logs + offline eval | 0 | Low |

**Highest‑leverage skill:** Phase 4 (chat with tools). Plan extra time there.
**Highest‑leverage marketing:** Phase 5 + Phase 3 — on‑device + semantic recs are uniquely impressive.
**Stay‑in‑budget sanity check:** default to Gemini free tier; embed once; cap daily tokens; cache aggressively.

---

> Each phase should be opened with a short plan (1–3 sentences) and closed with a verification step (the acceptance criteria above). Phase 0 must land first; no later phase starts until its acceptance criteria are green.
