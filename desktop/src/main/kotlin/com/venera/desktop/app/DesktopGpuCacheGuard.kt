package com.venera.desktop.app

import androidx.compose.runtime.staticCompositionLocalOf
import org.jetbrains.skia.DirectContext

/**
 * 主动清理窗口 GPU 纹理缓存的入口；参数为触发原因标签，供日志定位。
 */
val LocalGpuCachePurge = staticCompositionLocalOf<((String) -> Unit)?> { null }

/**
 * Nucleus Tao 窗口后端默认在 attach/resize 时把 Skia DirectContext 缓存上限硬编码为 256 MB，
 * 无视 `-Dskiko.gpu.resourceCacheLimit`。本守卫在挂载与窗口缩放后把上限接管回配置值，
 * 并在页面离场/窗口闲置时主动把上限归零再恢复以驱逐未锁定纹理。
 */
object DesktopGpuCacheGuard {
    const val DEFAULT_LIMIT_BYTES: Long = 48L * 1024L * 1024L

    /**
     * 解析 `-Dskiko.gpu.resourceCacheLimit` 字符串为字节数。
     *
     * @param raw 系统属性原始文本（如 `32M`、`64MB`、`512K`、`1G` 或纯字节数），可空。
     * @param defaultBytes 缺失或无法解析时回落的默认字节上限。
     * @return 解析得到的正字节数上限。
     */
    fun parseLimitBytes(raw: String?, defaultBytes: Long = DEFAULT_LIMIT_BYTES): Long {
        val text = raw?.trim()?.uppercase().orEmpty()
        if (text.isEmpty()) return defaultBytes
        val (numPart, mult) = when {
            text.endsWith("GB") -> text.dropLast(2) to (1024L * 1024L * 1024L)
            text.endsWith("G") -> text.dropLast(1) to (1024L * 1024L * 1024L)
            text.endsWith("MB") -> text.dropLast(2) to (1024L * 1024L)
            text.endsWith("M") -> text.dropLast(1) to (1024L * 1024L)
            text.endsWith("KB") -> text.dropLast(2) to 1024L
            text.endsWith("K") -> text.dropLast(1) to 1024L
            else -> text to 1L
        }
        val base = numPart.trim().toLongOrNull() ?: return defaultBytes
        if (base <= 0L) return defaultBytes
        return runCatching { Math.multiplyExact(base, mult) }.getOrDefault(defaultBytes)
    }

    /**
     * 若当前 [DirectContext] 资源上限与目标值不一致，将其覆盖为 [limitBytes]。
     * 必须在对应的 GPU 渲染线程中调用。
     *
     * @param context Skia GPU 上下文实例。
     * @param limitBytes 期望的纹理资源缓存字节上限。
     * @param reason 本次校准触发点说明（用于运行读数日志）。
     */
    fun enforceLimit(context: DirectContext, limitBytes: Long, reason: String) {
        val prev = context.resourceCacheLimit
        if (prev != limitBytes) {
            context.resourceCacheLimit = limitBytes
            println(
                "D_GPU缓存 限额校准 reason=$reason 原=${prev / (1024L * 1024L)}MB " +
                    "新=${limitBytes / (1024L * 1024L)}MB",
            )
        }
    }

    /**
     * 通过先归零再恢复上限，驱逐 [DirectContext] 中所有未锁定 GPU 资源。
     * 必须在对应的 GPU 渲染线程中调用。
     *
     * @param context Skia GPU 上下文实例。
     * @param limitBytes 清理后恢复的资源缓存字节上限。
     * @param reason 本次清理触发点说明（用于运行读数日志）。
     */
    fun purgeAndRestore(context: DirectContext, limitBytes: Long, reason: String) {
        context.resourceCacheLimit = 0L
        context.resourceCacheLimit = limitBytes
        println("D_GPU缓存 已清理 reason=$reason 上限=${limitBytes / (1024L * 1024L)}MB")
    }
}
