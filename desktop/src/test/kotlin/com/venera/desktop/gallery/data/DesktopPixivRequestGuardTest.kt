package com.venera.desktop.gallery.data

import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopPixivRequestGuardTest {

    @Test
    fun `同键并发请求合并为一次网络执行`(): Unit = runBlocking {
        val guard = DesktopPixivRequestGuard(wwwIntervalMs = 0L, appIntervalMs = 0L)
        val hits = AtomicInteger(0)
        val results = (1..10).map {
            async {
                guard.execute("workDetail|123", PixivApiChannel.APP, PixivApiPriority.HIGH) {
                    hits.incrementAndGet()
                    delay(40)
                    Result.success("ok-123")
                }
            }
        }.awaitAll()

        assertEquals(1, hits.get())
        assertTrue(results.all { it.getOrNull() == "ok-123" })
    }

    @Test
    fun `高优先级等待时低优先级让路，且同通道按间隔排队`(): Unit = runBlocking {
        val clock = AtomicLong(1_000L)
        val order = mutableListOf<String>()
        val guard = DesktopPixivRequestGuard(
            nowMs = { clock.get() },
            sleeper = { ms -> clock.addAndGet(ms) },
            wwwIntervalMs = 0L,
            appIntervalMs = 300L,
        )

        // 先占第一槽，使后续并发进入节流竞争。
        guard.execute("seed", PixivApiChannel.APP, PixivApiPriority.HIGH) {
            order += "seed@${clock.get()}"
            Result.success(Unit)
        }

        val lowJob = async {
            guard.execute("low-key", PixivApiChannel.APP, PixivApiPriority.LOW) {
                order += "low"
                Result.success(Unit)
            }
        }
        val highJob = async {
            guard.execute("high-key", PixivApiChannel.APP, PixivApiPriority.HIGH) {
                order += "high"
                Result.success(Unit)
            }
        }
        awaitAll(lowJob, highJob)

        assertEquals("seed", order.first().substringBefore('@'))
        assertTrue(clock.get() >= 1_600L)
    }

    @Test
    fun `触发429后进入冷却拦截新请求，冷却结束恢复并清零连击`(): Unit = runBlocking {
        val clock = AtomicLong(10_000L)
        val calls = AtomicInteger(0)
        val guard = DesktopPixivRequestGuard(
            nowMs = { clock.get() },
            sleeper = { ms -> clock.addAndGet(ms) },
            wwwIntervalMs = 0L,
            appIntervalMs = 0L,
            baseCooldownMs = 30_000L,
        )

        val first = guard.execute("k1", PixivApiChannel.WWW) {
            calls.incrementAndGet()
            Result.failure<String>(IOException("pixiv 返回 429（Retry-After=45s）"))
        }
        assertTrue(first.isFailure)
        assertTrue(guard.isInCooldown(PixivApiChannel.WWW))
        assertFalse(guard.isInCooldown(PixivApiChannel.APP))
        assertEquals(45, guard.remainingCooldownSeconds(PixivApiChannel.WWW))

        // 冷却期内再次请求应被直接拦截，不产生网络调用。
        val blocked = guard.execute("k2", PixivApiChannel.WWW) {
            calls.incrementAndGet()
            Result.success("should-not-run")
        }
        assertTrue(blocked.exceptionOrNull() is PixivRateLimitCooldownException)
        assertEquals(1, calls.get())

        // 推进时钟越过 45s 冷却期。
        clock.addAndGet(46_000L)
        assertFalse(guard.isInCooldown(PixivApiChannel.WWW))
        val recovered = guard.execute("k3", PixivApiChannel.WWW) {
            calls.incrementAndGet()
            Result.success("recovered")
        }
        assertEquals("recovered", recovered.getOrNull())
        assertEquals(2, calls.get())
    }
}
