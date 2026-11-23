package com.venera.desktop.gallery.data

import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Pixiv 接口通道：匿名网页端与带凭据移动端出口配额独立。 */
internal enum class PixivApiChannel {
    WWW,
    APP,
}

/** 请求调度优先级：高优先级插队放行，后台推荐与预取主动让路。 */
internal enum class PixivApiPriority {
    HIGH,
    LOW,
}

/**
 * 触发 429 熔断冷却时抛出的受控异常。
 *
 * @param remainingSeconds 距离恢复还要等待的秒数。
 */
internal class PixivRateLimitCooldownException(
    val remainingSeconds: Int,
) : IOException("Pixiv 限流冷却中（约 ${remainingSeconds} 秒后恢复）")

/**
 * Pixiv 请求调度保护器：同键并发去重、分通道节流排队、优先级让路与 429 熔断冷却。
 *
 * @param nowMs 当前毫秒时钟提供器，测试可注入虚拟时钟。
 * @param sleeper 挂起等待函数，测试可替换以加速验证。
 * @param wwwIntervalMs 匿名网页端最小发车间隔（毫秒）。
 * @param appIntervalMs 登录移动端接口最小发车间隔（毫秒）。
 * @param baseCooldownMs 首次触发 429 的基础冷却时长（毫秒）。
 */
internal class DesktopPixivRequestGuard(
    private val nowMs: () -> Long = { System.currentTimeMillis() },
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
    private val wwwIntervalMs: Long = DEFAULT_WWW_INTERVAL_MS,
    private val appIntervalMs: Long = DEFAULT_APP_INTERVAL_MS,
    private val baseCooldownMs: Long = DEFAULT_BASE_COOLDOWN_MS,
) {

    private class ChannelState {
        val mutex = Mutex()
        val highWaiting = AtomicInteger(0)
        var lastScheduledMs = 0L
        @Volatile var cooldownUntilMs = 0L
        @Volatile var consecutive429 = 0
    }

    private val channels = mapOf(
        PixivApiChannel.WWW to ChannelState(),
        PixivApiChannel.APP to ChannelState(),
    )

    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<Result<Any?>>>()

    private val _cooldownUntilMs = MutableStateFlow(0L)

    /** 全站最近一次冷却截止时间戳（毫秒），供状态展示与测试观察。 */
    val cooldownUntilMs: StateFlow<Long> = _cooldownUntilMs.asStateFlow()

    /** 查询指定 [channel] 当前是否仍处于 429 熔断冷却期内。 */
    fun isInCooldown(channel: PixivApiChannel): Boolean =
        remainingCooldownSeconds(channel) > 0

    /** 查询指定 [channel] 剩余冷却秒数；未冷却时返回 0。 */
    fun remainingCooldownSeconds(channel: PixivApiChannel): Int {
        val state = channels.getValue(channel)
        val remainMs = state.cooldownUntilMs - nowMs()
        if (remainMs <= 0L) return 0
        return ((remainMs + 999L) / 1000L).toInt().coerceAtLeast(1)
    }

    /**
     * 合并同一 [flightKey] 的并发请求，并按 [channel] 与 [priority] 排队发车。
     *
     * @param flightKey 并发去重键；相同键在飞行期间只发出一次网络调用。
     * @param channel 目标接口通道。
     * @param priority 调度优先级。
     * @param block 实际发起网络请求并返回 [Result] 的挂起块。
     */
    @Suppress("UNCHECKED_CAST")
    suspend fun <T> execute(
        flightKey: String,
        channel: PixivApiChannel,
        priority: PixivApiPriority = PixivApiPriority.HIGH,
        block: suspend () -> Result<T>,
    ): Result<T> {
        while (true) {
            val existing = inFlight[flightKey]
            if (existing != null) {
                return try {
                    existing.await() as Result<T>
                } catch (_: kotlinx.coroutines.CancellationException) {
                    // 仅当领飞协程被取消时重入竞争，避免误把取消当网络失败。
                    continue
                }
            }
            val created = CompletableDeferred<Result<Any?>>()
            val prev = inFlight.putIfAbsent(flightKey, created)
            if (prev != null) {
                return try {
                    prev.await() as Result<T>
                } catch (_: kotlinx.coroutines.CancellationException) {
                    continue
                }
            }
            return try {
                val res = dispatchWithThrottle(channel, priority, block)
                created.complete(res as Result<Any?>)
                res
            } catch (t: Throwable) {
                created.completeExceptionally(t)
                throw t
            } finally {
                inFlight.remove(flightKey, created)
            }
        }
    }

    private suspend fun <T> dispatchWithThrottle(
        channel: PixivApiChannel,
        priority: PixivApiPriority,
        block: suspend () -> Result<T>,
    ): Result<T> {
        val remainSec = remainingCooldownSeconds(channel)
        if (remainSec > 0) {
            return Result.failure(PixivRateLimitCooldownException(remainSec))
        }

        val state = channels.getValue(channel)
        val minInterval = when (channel) {
            PixivApiChannel.WWW -> wwwIntervalMs
            PixivApiChannel.APP -> appIntervalMs
        }

        val waitMs = if (priority == PixivApiPriority.HIGH) {
            state.highWaiting.incrementAndGet()
            try {
                reserveSlot(state, minInterval)
            } finally {
                state.highWaiting.decrementAndGet()
            }
        } else {
            var yieldCount = 0
            while (state.highWaiting.get() > 0 && yieldCount < MAX_LOW_YIELD_ROUNDS) {
                yieldCount++
                sleeper(LOW_YIELD_STEP_MS)
            }
            reserveSlot(state, minInterval)
        }

        if (waitMs > 0L) {
            sleeper(waitMs)
        }

        // 等待排队期间如果其他在途请求刚触发熔断，直接拦截不再往站方送请求。
        val postWaitRemain = remainingCooldownSeconds(channel)
        if (postWaitRemain > 0) {
            return Result.failure(PixivRateLimitCooldownException(postWaitRemain))
        }

        val result = block()
        val error = result.exceptionOrNull()
        if (result.isSuccess) {
            state.consecutive429 = 0
        } else if (error != null && isRateLimitError(error)) {
            tripCooldown(state, error.message)
        }
        return result
    }

    private suspend fun reserveSlot(state: ChannelState, minInterval: Long): Long {
        return state.mutex.withLock {
            val now = nowMs()
            val scheduled = if (state.lastScheduledMs == 0L || minInterval <= 0L) {
                now
            } else {
                maxOf(now, state.lastScheduledMs + minInterval)
            }
            state.lastScheduledMs = scheduled
            (scheduled - now).coerceAtLeast(0L)
        }
    }

    private fun tripCooldown(state: ChannelState, message: String?) {
        val streak = state.consecutive429
        state.consecutive429 = streak + 1
        val exponential = baseCooldownMs * (1L shl streak.coerceAtMost(2))
        val retryAfterSec = parseRetryAfterSeconds(message)
        val durationMs = maxOf(
            exponential,
            (retryAfterSec ?: 0L) * 1000L,
        ).coerceAtMost(MAX_COOLDOWN_MS)
        val until = nowMs() + durationMs
        if (until > state.cooldownUntilMs) {
            state.cooldownUntilMs = until
        }
        val maxUntil = channels.values.maxOf { it.cooldownUntilMs }
        _cooldownUntilMs.value = maxUntil
        println("D_pixiv 触发限流熔断 冷却=${durationMs / 1000}s 连击=${state.consecutive429}")
    }

    companion object {
        private const val DEFAULT_WWW_INTERVAL_MS = 600L
        private const val DEFAULT_APP_INTERVAL_MS = 300L
        private const val DEFAULT_BASE_COOLDOWN_MS = 30_000L
        private const val MAX_COOLDOWN_MS = 300_000L
        private const val LOW_YIELD_STEP_MS = 40L
        private const val MAX_LOW_YIELD_ROUNDS = 50

        private val RETRY_AFTER_REGEX = Regex("""Retry-After=(\d+)s""", RegexOption.IGNORE_CASE)

        internal fun isRateLimitError(t: Throwable): Boolean {
            val msg = t.message.orEmpty()
            return msg.contains("429") ||
                msg.contains("Too Many Requests", ignoreCase = true) ||
                msg.contains("限流")
        }

        internal fun parseRetryAfterSeconds(message: String?): Long? {
            if (message.isNullOrBlank()) return null
            return RETRY_AFTER_REGEX.find(message)?.groupValues?.getOrNull(1)?.toLongOrNull()
        }
    }
}
