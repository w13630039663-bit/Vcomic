package com.venera.desktop.search

import com.venera.desktop.settings.DesktopI18n
import com.venera.engineprobe.EngineSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * 聚合搜索并发执行器：利用 QuickJS 轻量特性派生有限路独立会话并发检索，避免单源超时阻塞全局。
 *
 * 所有临时会话共享主引擎的 Cookie 罐、HTTP 连接池与源设置存储，跑完或取消立即 close 归零。
 */
internal object DesktopAggregateSearchRunner {

    const val DEFAULT_CONCURRENCY: Int = 4
    const val DEFAULT_PER_SOURCE_TIMEOUT_MS: Long = 15_000L

    /**
     * 并发检索多个源，每完成一个源即在调用方协程上下文回调 [onLaneComplete] 流式上屏。
     *
     * @param query 搜索关键词。
     * @param targets 待检索源列表（源键 -> 展示名）。
     * @param engine 主引擎会话；为空或仅单源时退回 [fallbackSearch]。
     * @param fallbackSearch 无真引擎或测试桩时的检索回调。
     * @param concurrency 最大并发路数。
     * @param perSourceTimeoutMs 单源超时上限（毫秒）。
     * @param onLaneComplete 单源结束时在调用方主协程上下文触发的状态回写回调。
     */
    suspend fun run(
        query: String,
        targets: List<Pair<String, String>>,
        engine: EngineSession?,
        fallbackSearch: suspend (query: String, page: Int, sourceKey: String, options: List<String?>) -> DesktopComicSearchOutcome,
        concurrency: Int = DEFAULT_CONCURRENCY,
        perSourceTimeoutMs: Long = DEFAULT_PER_SOURCE_TIMEOUT_MS,
        onLaneComplete: (DesktopAggregateLaneState) -> Unit,
    ) {
        if (targets.isEmpty()) return
        val callerContext = kotlin.coroutines.coroutineContext
        val workerCount = concurrency.coerceIn(1, targets.size)

        // 单源或无真引擎（单测/无引擎环境）直接走注入的 fallbackSearch，保持确定性行为。
        if (engine == null || workerCount == 1) {
            for ((key, title) in targets) {
                val lane = runSingleWithFallback(query, key, title, perSourceTimeoutMs, fallbackSearch)
                onLaneComplete(lane)
            }
            return
        }

        val taskChannel = Channel<Pair<String, String>>(Channel.UNLIMITED)
        for (item in targets) {
            taskChannel.trySend(item)
        }
        taskChannel.close()

        coroutineScope {
            repeat(workerCount) {
                launch(Dispatchers.IO) {
                    var worker: EngineSession? = null
                    try {
                        for ((sourceKey, sourceLabel) in taskChannel) {
                            if (worker == null) {
                                worker = runCatching { engine.forkEphemeral() }.getOrNull()
                            }
                            val currentWorker = worker
                            val outcomeResult = if (currentWorker == null) {
                                runCatching {
                                    withTimeout(perSourceTimeoutMs) {
                                        fallbackSearch(query, 1, sourceKey, emptyList())
                                    }
                                }
                            } else {
                                runCatching {
                                    val page = searchOnWorker(currentWorker, sourceKey, query, perSourceTimeoutMs)
                                    DesktopComicSearchOutcome(
                                        cards = page.comics,
                                        maxPage = page.maxPage,
                                        sourceError = page.error?.takeIf { it.isNotBlank() },
                                        next = page.next,
                                    )
                                }
                            }

                            val lane = outcomeResult.fold(
                                onSuccess = { outcome -> toLaneState(sourceKey, sourceLabel, outcome) },
                                onFailure = { t ->
                                    if (t is CancellationException && t !is TimeoutCancellationException) {
                                        throw t
                                    }
                                    val isTimeout = t is TimeoutCancellationException ||
                                        (t.message?.contains("timeout", ignoreCase = true) == true)
                                    if (isTimeout && worker != null) {
                                        // 超时源可能仍有残留 JS 微任务，销毁该临时上下文，下个源按需重建。
                                        runCatching { worker?.close() }
                                        worker = null
                                    }
                                    toFailedLaneState(sourceKey, sourceLabel, t, isTimeout)
                                },
                            )
                            withContext(callerContext) {
                                onLaneComplete(lane)
                            }
                        }
                    } finally {
                        runCatching { worker?.close() }
                    }
                }
            }
        }
    }

    private fun searchOnWorker(
        worker: EngineSession,
        sourceKey: String,
        query: String,
        timeoutMs: Long,
    ): EngineSession.Page {
        worker.load(sourceKey)
        return worker.search(query, 1, timeoutMs = timeoutMs)
    }

    private suspend fun runSingleWithFallback(
        query: String,
        sourceKey: String,
        sourceLabel: String,
        perSourceTimeoutMs: Long,
        fallbackSearch: suspend (query: String, page: Int, sourceKey: String, options: List<String?>) -> DesktopComicSearchOutcome,
    ): DesktopAggregateLaneState {
        val got = withContext(Dispatchers.IO) {
            runCatching {
                withTimeout(perSourceTimeoutMs) {
                    fallbackSearch(query, 1, sourceKey, emptyList())
                }
            }
        }
        return got.fold(
            onSuccess = { outcome -> toLaneState(sourceKey, sourceLabel, outcome) },
            onFailure = { t ->
                if (t is CancellationException && t !is TimeoutCancellationException) {
                    throw t
                }
                val isTimeout = t is TimeoutCancellationException ||
                    (t.message?.contains("timeout", ignoreCase = true) == true)
                toFailedLaneState(sourceKey, sourceLabel, t, isTimeout)
            },
        )
    }

    private fun toLaneState(
        sourceKey: String,
        sourceLabel: String,
        outcome: DesktopComicSearchOutcome,
    ): DesktopAggregateLaneState = when {
        outcome.sourceError != null -> DesktopAggregateLaneState(
            sourceKey = sourceKey,
            sourceLabel = sourceLabel,
            status = AggregateLaneStatus.Failed,
            error = com.venera.desktop.platform.desktopFailureNoteOfText(outcome.sourceError),
        )
        outcome.cards.isEmpty() -> DesktopAggregateLaneState(
            sourceKey = sourceKey,
            sourceLabel = sourceLabel,
            status = AggregateLaneStatus.Empty,
        )
        else -> DesktopAggregateLaneState(
            sourceKey = sourceKey,
            sourceLabel = sourceLabel,
            status = AggregateLaneStatus.Ready,
            cards = outcome.cards,
        )
    }

    private fun toFailedLaneState(
        sourceKey: String,
        sourceLabel: String,
        t: Throwable,
        isTimeout: Boolean,
    ): DesktopAggregateLaneState {
        val reason = if (isTimeout) {
            if (sourceKey in com.venera.desktop.platform.PROXY_REQUIRED_SOURCE_KEYS) {
                com.venera.desktop.platform.desktopFailureNoteForSource(t, sourceKey)
            } else {
                DesktopI18n.tr("检索超时（15s），可单独重试", "Search timed out (15s). You can retry this source.")
            }
        } else {
            com.venera.desktop.platform.desktopFailureNoteForSource(t, sourceKey)
        }
        return DesktopAggregateLaneState(
            sourceKey = sourceKey,
            sourceLabel = sourceLabel,
            status = AggregateLaneStatus.Failed,
            error = reason,
        )
    }
}
