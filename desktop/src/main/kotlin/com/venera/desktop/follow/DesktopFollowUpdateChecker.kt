package com.venera.desktop.follow

import com.venera.compose.data.db.ComicFollowDao
import com.venera.compose.data.db.ComicFollowRecord
import com.venera.compose.data.db.ComicFollowStatus
import com.venera.compose.data.db.FollowCheckOutcome
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 一轮巡检的实时进度。`running` 为 false 时其余读数是**上一轮终值**，`summary` 是可直接上屏的一句总结。
 */
data class FollowCheckProgress(
    val running: Boolean = false,
    val total: Int = 0,
    val completed: Int = 0,
    val currentSource: String = "",
    val currentTitle: String = "",
    val found: Int = 0,
    val skipped: Int = 0,
    val failed: Int = 0,
    /** 同源熔断等需要用户处理的一句话；空串 = 没发生。 */
    val notice: String = "",
    val summary: String = "",
)

/**
 * 巡检取数口：一次只取一本，实现方负责引擎会话的装载与回收。
 *
 * 抽成接口而不是直接吃 `EngineSession`：判据要能在零网络、零 JS 的 JVM 里跑；
 * 生产实现见 `EngineFollowDetailSource`，按源复用临时会话。
 */
interface FollowDetailSource {

    /** 取一本详情并压成 [FollowSnapshot]；失败直接抛，由巡检器记进 `last_error`。 */
    suspend fun load(sourceName: String, comicId: String): FollowSnapshot

    /** 一轮结束（正常、取消、异常都算）时释放临时会话；默认什么都不做。 */
    fun closeRound() {}
}

/**
 * 单飞行串行巡检器：一轮只跑一本，同源节流、冷却跳过、同源熔断、可随时取消。
 *
 * 三条硬约束：
 *  - [start] 用 CAS 防重入，已有一轮在跑时第二次调用直接返回 null；
 *  - 每本检查完立刻释放取数会话（[FollowDetailSource] 实现负责），不长期占引擎锁；
 *  - 失败只写 `last_error` 与连败计数，绝不覆盖已有章节快照与红点。
 *
 * @param dao 追更账本读写口，巡检结果只经它落库。
 * @param details 详情取数口；测试传假实现即零真网。
 * @param cooldownMillis 同一本成功检查后多久内再次巡检自动跳过；手动强制重查可绕过。
 * @param sameSourceDelayMillis 同一源相邻两本之间的间隔，压住站点风控。
 * @param crossSourceDelayMillis 换源后的间隔。
 * @param maxSourceFailures 同一源连续失败多少次后熔断该源本轮剩余条目。
 * @param clock 取当前 epoch 毫秒；测试注入假时钟。
 */
class DesktopFollowUpdateChecker(
    private val dao: ComicFollowDao,
    private val details: FollowDetailSource,
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
    private val sameSourceDelayMillis: Long = DEFAULT_SAME_SOURCE_DELAY_MILLIS,
    private val crossSourceDelayMillis: Long = DEFAULT_CROSS_SOURCE_DELAY_MILLIS,
    private val maxSourceFailures: Int = DEFAULT_MAX_SOURCE_FAILURES,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val cooldownMillisProvider: (() -> Long)? = null,
) {

    private val _progress = MutableStateFlow(FollowCheckProgress())

    /** 巡检进度：UI 订阅这一条流即可画进度条与停止按钮。 */
    val progress: StateFlow<FollowCheckProgress> = _progress.asStateFlow()

    private val running = AtomicBoolean(false)

    private var job: Job? = null

    /**
     * 起一轮巡检。
     *
     * @param scope 巡检协程的宿主作用域（窗口级），随窗口关闭一起收。
     * @param records 本轮候选；只取 `ACTIVE`，其余记跳过。
     * @param force 为 true 时忽略冷却与同源熔断（用户手动「强制全部重查」）。
     * @return 本轮 Job；已有一轮在跑或没有可检查条目时交回 null（后者不占运行位）。
     */
    fun start(scope: CoroutineScope, records: List<ComicFollowRecord>, force: Boolean = false): Job? {
        val targets = records.filter { it.status == ComicFollowStatus.ACTIVE }
        if (!running.compareAndSet(false, true)) return null
        if (targets.isEmpty()) {
            running.set(false)
            _progress.value = FollowCheckProgress(total = 0, summary = "没有可检查的追更")
            return null
        }

        val startedAt = clock()
        val effectiveCooldownMillis = (cooldownMillisProvider?.invoke() ?: cooldownMillis).coerceAtLeast(0L)
        _progress.value = FollowCheckProgress(
            running = true,
            total = targets.size,
            currentSource = targets.first().sourceName,
            currentTitle = targets.first().title,
        )

        val started = scope.launch(Dispatchers.IO) {
            var found = 0
            val updatedTitles = mutableListOf<String>()
            var skipped = 0
            var failed = 0
            var notice = ""
            var cancelled = false
            val sourceFailures = mutableMapOf<String, Int>()
            val broken = mutableSetOf<String>()
            var lastSource: String? = null

            try {
                targets.forEachIndexed { index, record ->
                    ensureActive()
                    _progress.value = _progress.value.copy(
                        currentSource = record.sourceName,
                        currentTitle = record.title,
                    )

                    val since = startedAt - record.lastCheckAt
                    if (!force && record.lastCheckAt > 0L && since in 0L until effectiveCooldownMillis) {
                        skipped++
                        _progress.value = _progress.value.copy(completed = index + 1, skipped = skipped)
                        return@forEachIndexed
                    }

                    if (!force && record.sourceName in broken) {
                        skipped++
                        _progress.value = _progress.value.copy(completed = index + 1, skipped = skipped)
                        return@forEachIndexed
                    }

                    if (lastSource != null) {
                        delay(if (lastSource == record.sourceName) sameSourceDelayMillis else crossSourceDelayMillis)
                    }
                    lastSource = record.sourceName
                    ensureActive()

                    val snapshot = try {
                        details.load(record.sourceName, record.comicId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        val message = e.message?.take(140) ?: e::class.java.simpleName
                        dao.applyCheckFailure(record.comicId, record.sourceName, message)
                        failed++
                        val failures = (sourceFailures[record.sourceName] ?: 0) + 1
                        sourceFailures[record.sourceName] = failures
                        if (failures >= maxSourceFailures && record.sourceName !in broken) {
                            broken += record.sourceName
                            notice = "源「${record.sourceName}」连续失败 $failures 次，已跳过本轮剩余条目：请手动过盾或重新登录"
                            com.venera.desktop.DesktopErrorBus.report(
                                category = "漫画",
                                headline = "追更巡检失败：${record.sourceName}",
                                humanReason = "这颗源连续失败 $failures 次，本轮剩余条目已跳过；多半是被盾拦住，可手动过盾或重新登录后重查。",
                                detail = "${record.sourceName} ${record.comicId}: ${e::class.java.simpleName}: ${e.message.orEmpty()}",
                                dedupKey = "comic:follow_check_broken:${record.sourceName}",
                            )
                        }
                        _progress.value = _progress.value.copy(
                            completed = index + 1,
                            failed = failed,
                            notice = notice,
                        )
                        return@forEachIndexed
                    }

                    sourceFailures[record.sourceName] = 0
                    val latest = dao.get(record.comicId, record.sourceName) ?: record
                    val plan = FollowUpdateDiff.plan(latest, snapshot, clock())
                    if (plan.kind == FollowDiffKind.UPDATED) {
                        found++
                        if (updatedTitles.size < 3) updatedTitles += latest.title
                    }
                    dao.applyCheckSuccess(
                        record.comicId,
                        record.sourceName,
                        FollowCheckOutcome(
                            latestChapterId = plan.snapshot.latestChapterId,
                            latestChapterTitle = plan.snapshot.latestChapterTitle,
                            chapterCount = plan.snapshot.chapterCount,
                            remoteUpdateTime = plan.snapshot.remoteUpdateTime,
                            hasNewUpdate = plan.hasNewUpdate,
                            newChaptersCount = plan.newChaptersCount,
                            detectedAt = plan.detectedAt,
                        ),
                    )
                    _progress.value = _progress.value.copy(
                        completed = index + 1,
                        found = found,
                        failed = failed,
                        notice = notice,
                    )
                }
            } catch (e: CancellationException) {
                cancelled = true
                throw e
            } finally {
                details.closeRound()
                running.set(false)
                val cooldownHours = effectiveCooldownMillis / (60L * 60L * 1000L)
                val done = _progress.value.completed
                val summary = if (cancelled) {
                    "已停止：完成 $done / ${targets.size}，发现 $found 本有新更新，$failed 本失败"
                } else {
                    "检查完成：发现 $found 本有新更新，$skipped 本跳过（${cooldownHours} 小时内已查），$failed 本失败"
                }
                _progress.value = _progress.value.copy(
                    running = false,
                    currentSource = "",
                    currentTitle = "",
                    found = found,
                    skipped = skipped,
                    failed = failed,
                    notice = notice,
                    summary = summary,
                )
                if (failed > 0) {
                    com.venera.desktop.DesktopErrorBus.report(
                        category = "漫画",
                        headline = "追更巡检有失败",
                        humanReason = "本轮有 $failed 本没查成，逐条原因已写进各自的追更记录；可稍后重查。",
                        detail = summary,
                        dedupKey = "comic:follow_check_failed",
                    )
                }
                if (!cancelled && found > 0) {
                    val body = if (updatedTitles.isNotEmpty()) {
                        val preview = updatedTitles.joinToString("、")
                        if (found > updatedTitles.size) "$preview 等 $found 本已更新" else preview
                    } else {
                        "发现 $found 本追更漫画有新章节"
                    }
                    com.venera.desktop.platform.DesktopSystemTray.notifyDownloadComplete(
                        "漫画追更发现 $found 本更新",
                        body,
                    )
                }
            }
        }
        job = started
        return started
    }

    /** 请求停止本轮巡检；已在飞的取数会随协程取消一起中断，未跑到的条目不再发起。 */
    fun cancel() {
        job?.cancel()
    }

    /** 窗口关闭收尾：停巡检并释放取数会话。 */
    fun close() {
        job?.cancel()
        job = null
        details.closeRound()
        running.set(false)
    }

    companion object {
        /** 默认冷却 6 小时：同一本成功检查后这段时间内自动跳过。 */
        const val DEFAULT_COOLDOWN_MILLIS = 6L * 60L * 60L * 1000L
        /** 同源两本之间 1.8 秒，压住站点风控与 Cloudflare 盾。 */
        const val DEFAULT_SAME_SOURCE_DELAY_MILLIS = 1_800L
        const val DEFAULT_CROSS_SOURCE_DELAY_MILLIS = 400L
        const val DEFAULT_MAX_SOURCE_FAILURES = 3
    }
}
