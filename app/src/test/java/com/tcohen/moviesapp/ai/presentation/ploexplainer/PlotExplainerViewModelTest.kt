package com.tcohen.moviesapp.ai.presentation.ploexplainer

import com.tcohen.moviesapp.ai.data.local.cache.InMemoryLlmResponseCache
import com.tcohen.moviesapp.ai.data.local.cache.LlmResponseCache
import com.tcohen.moviesapp.ai.domain.client.LlmClient
import com.tcohen.moviesapp.ai.domain.model.ChatCompletion
import com.tcohen.moviesapp.ai.domain.model.ChatRequest
import com.tcohen.moviesapp.util.ApiError
import com.tcohen.moviesapp.util.MainDispatcherRule
import com.tcohen.moviesapp.util.NetworkResult
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlotExplainerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `initial state is Idle`() = runTest {
        val (vm, _) = createViewModel(script = emptyFlow())

        assertEquals(PlotExplainerState.Idle, vm.state.value)
    }

    @Test
    fun `Explain with empty cache streams tokens and lands on Done`() = runTest {
        val script = flowOf(
            NetworkResult.Success("Paul ") as NetworkResult<String>,
            NetworkResult.Success("Fremen "),
            NetworkResult.Success("revenge.")
        )
        val (vm, client) = createViewModel(script = script)
        assertEquals(0, client.streamCalls.size)

        vm.processIntent(PlotExplainerIntent.Explain(SAMPLE_TITLE, SAMPLE_YEAR, SAMPLE_RUNTIME))

        assertEquals(PlotExplainerState.Done("Paul Fremen revenge."), vm.state.value)
        assertEquals(1, client.streamCalls.size)
    }

    @Test
    fun `streaming emits multiple intermediate Streaming states with growing text`() = runTest {
        val script = flowOf(
            NetworkResult.Success("A ") as NetworkResult<String>,
            NetworkResult.Success("B "),
            NetworkResult.Success("C.")
        )
        val (vm, _) = createViewModel(script = script)

        vm.processIntent(PlotExplainerIntent.Explain(SAMPLE_TITLE, SAMPLE_YEAR, SAMPLE_RUNTIME))

        assertEquals(PlotExplainerState.Done("A B C."), vm.state.value)

    }

    @Test
    fun `second Explain on the same movie does NOT call LlmClient (cache hit)`() = runTest {
        val script = flowOf(
            NetworkResult.Success("cached text") as NetworkResult<String>
        )
        val (vm, client) = createViewModel(script = script)

        vm.processIntent(PlotExplainerIntent.Explain(SAMPLE_TITLE, SAMPLE_YEAR, SAMPLE_RUNTIME))
        assertEquals(1, client.streamCalls.size)
        assertEquals(PlotExplainerState.Done("cached text"), vm.state.value)

        vm.processIntent(PlotExplainerIntent.Explain(SAMPLE_TITLE, SAMPLE_YEAR, SAMPLE_RUNTIME))

        assertEquals(
            "second Explain must hit cache and never stream again",
            1, client.streamCalls.size
        )
        assertEquals(PlotExplainerState.Done("cached text"), vm.state.value)
    }

    @Test
    fun `Explain with different movie title misses cache and re-streams`() = runTest {
        val scriptForInception = flowOf(NetworkResult.Success("inception-summary") as NetworkResult<String>)
        val scriptForArrival   = flowOf(NetworkResult.Success("arrival-summary")   as NetworkResult<String>)
        val (vm, client) = createViewModel(scriptByCall = listOf(scriptForInception, scriptForArrival))

        vm.processIntent(PlotExplainerIntent.Explain("Inception", 2010, 148))
        assertEquals(PlotExplainerState.Done("inception-summary"), vm.state.value)
        assertEquals(1, client.streamCalls.size)

        vm.processIntent(PlotExplainerIntent.Explain("Arrival", 2016, 116))
        assertEquals(
            "different movie title must produce a different cache key and re-stream",
            2, client.streamCalls.size
        )
        assertEquals(PlotExplainerState.Done("arrival-summary"), vm.state.value)
    }

    @Test
    fun `streaming that emits an Error transitions to Error state`() = runTest {
        val script = flowOf(
            NetworkResult.Success("partial ") as NetworkResult<String>,
            NetworkResult.Error(ApiError.UNAUTHORIZED.message, httpCode = 401)
        )
        val (vm, _) = createViewModel(script = script)

        vm.processIntent(PlotExplainerIntent.Explain(SAMPLE_TITLE, SAMPLE_YEAR, SAMPLE_RUNTIME))

        val finalState = vm.state.value
        assertTrue("expected Error but was $finalState", finalState is PlotExplainerState.Error)
        assertEquals(
            ApiError.UNAUTHORIZED.message,
            (finalState as PlotExplainerState.Error).message
        )
    }

    @Test
    fun `Explain after Error transitions to Streaming then Done on retry`() = runTest {
        val errorStream = flowOf(
            NetworkResult.Error(ApiError.RATE_LIMITED.message, httpCode = 429) as NetworkResult<String>
        )
        val (vm, _) = createViewModel(scriptByCall = listOf(errorStream, errorStream))

        vm.processIntent(PlotExplainerIntent.Explain(SAMPLE_TITLE, SAMPLE_YEAR, SAMPLE_RUNTIME))
        assertTrue(vm.state.value is PlotExplainerState.Error)

    }

    @Test
    fun `Explain never streams when daily cap is exhausted`() = runTest {
        val tracker = FakeAiUsageTracker().also {

            it.seedUsedTokens(
                inputTokens = 250_001,
                outputTokens = 0
            )
        }
        val (vm, client) = createViewModel(
            script = flowOf(NetworkResult.Success("never streamed") as NetworkResult<String>),
            usageTracker = tracker
        )

        vm.processIntent(PlotExplainerIntent.Explain(SAMPLE_TITLE, SAMPLE_YEAR, SAMPLE_RUNTIME))

        assertEquals(
            "no streaming should occur when the daily cap is over",
            0, client.streamCalls.size
        )
        val finalState = vm.state.value
        assertTrue("expected Error but was $finalState", finalState is PlotExplainerState.Error)
    }

    @Test
    fun `Cancel from Streaming returns to Idle`() = runTest {
        val cancellable = MutableSharedFlow<NetworkResult<String>>()
        val (vm, _) = createViewModel(script = cancellable.asSharedFlow())

        vm.processIntent(PlotExplainerIntent.Explain(SAMPLE_TITLE, SAMPLE_YEAR, SAMPLE_RUNTIME))
        assertEquals(PlotExplainerState.Streaming(""), vm.state.value)

        vm.processIntent(PlotExplainerIntent.Cancel)

        assertEquals(PlotExplainerState.Idle, vm.state.value)
    }

    @Test
    fun `Cancel from Done is a no-op`() = runTest {
        val (vm, _) = createViewModel(
            script = flowOf(NetworkResult.Success("final") as NetworkResult<String>)
        )
        vm.processIntent(PlotExplainerIntent.Explain(SAMPLE_TITLE, SAMPLE_YEAR, SAMPLE_RUNTIME))
        assertEquals(PlotExplainerState.Done("final"), vm.state.value)

        vm.processIntent(PlotExplainerIntent.Cancel)

        assertEquals(PlotExplainerState.Done("final"), vm.state.value)
    }

    @Test
    fun `Reset from Done transitions to Idle`() = runTest {
        val (vm, _) = createViewModel(
            script = flowOf(NetworkResult.Success("text") as NetworkResult<String>)
        )
        vm.processIntent(PlotExplainerIntent.Explain(SAMPLE_TITLE, SAMPLE_YEAR, SAMPLE_RUNTIME))
        assertEquals(PlotExplainerState.Done("text"), vm.state.value)

        vm.processIntent(PlotExplainerIntent.Reset)

        assertEquals(PlotExplainerState.Idle, vm.state.value)
    }

    private fun createViewModel(
        script: Flow<NetworkResult<String>> = flowOf(),
        scriptByCall: List<Flow<NetworkResult<String>>> = emptyList(),
        usageTracker: FakeAiUsageTracker = FakeAiUsageTracker()
    ): Pair<PlotExplainerViewModel, ScriptedLlmClient> {
        val client = ScriptedLlmClient(script, scriptByCall)
        val cache: LlmResponseCache = InMemoryLlmResponseCache()
        return PlotExplainerViewModel(
            llmClient = client,
            responseCache = cache,
            usageRepository = usageTracker.asRepository
        ) to client
    }

    private fun emptyFlow() = flowOf<NetworkResult<String>>()

    companion object {
        private const val SAMPLE_TITLE = "Inception"
        private const val SAMPLE_YEAR = 2010
        private const val SAMPLE_RUNTIME = 148
    }
}

private class ScriptedLlmClient(
    private val defaultScript: Flow<NetworkResult<String>>,
    private val scriptsByCall: List<Flow<NetworkResult<String>>> = emptyList()
) : LlmClient {

    val streamCalls = mutableListOf<ChatRequest>()

    override suspend fun complete(request: ChatRequest): NetworkResult<ChatCompletion> =
        error("complete() not used in plot-explainer tests")

    override fun stream(request: ChatRequest): Flow<NetworkResult<String>> {
        streamCalls.add(request)
        val index = streamCalls.size - 1
        return if (index in scriptsByCall.indices) scriptsByCall[index] else defaultScript
    }
}

internal class FakeAiUsageTracker {
    private val fakeDao = FakeAiUsageDao()

    val asRepository: com.tcohen.moviesapp.ai.data.repository.AiUsageRepositoryImpl =
        com.tcohen.moviesapp.ai.data.repository.AiUsageRepositoryImpl(
            dao = fakeDao,
            clock = object : com.tcohen.moviesapp.ai.data.repository.TimeProvider {
                override fun startOfTodayMillis(): Long = 0L
            }
        )

    fun seedUsedTokens(inputTokens: Int, outputTokens: Int) {
        fakeDao.seedUsedTokens(inputTokens, outputTokens)
    }
}

private class FakeAiUsageDao : com.tcohen.moviesapp.ai.data.local.dao.AiUsageDao {
    private val entries = mutableListOf<com.tcohen.moviesapp.ai.data.local.entity.AiUsageEntity>()

    override suspend fun insert(usage: com.tcohen.moviesapp.ai.data.local.entity.AiUsageEntity) {
        entries += usage
    }

    override suspend fun totalTokensSince(sinceMillis: Long): Int =
        entries.filter { it.timestamp >= sinceMillis }
            .sumOf { it.inputTokens + it.outputTokens }

    override suspend fun inputTokensSince(sinceMillis: Long): Int =
        entries.filter { it.timestamp >= sinceMillis }.sumOf { it.inputTokens }

    override suspend fun outputTokensSince(sinceMillis: Long): Int =
        entries.filter { it.timestamp >= sinceMillis }.sumOf { it.outputTokens }

    override suspend fun deleteOlderThan(beforeMillis: Long) {
        entries.removeAll { it.timestamp < beforeMillis }
    }

    fun seedUsedTokens(inputTokens: Int, outputTokens: Int) {
        entries += com.tcohen.moviesapp.ai.data.local.entity.AiUsageEntity(
            model = "test", feature = "test",
            inputTokens = inputTokens, outputTokens = outputTokens,
            timestamp = 0L
        )
    }
}
