package com.venera.desktop.gallery.data

import com.venera.compose.data.platform.PathProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 桌面缓存占用与清理（磁盘只涨不消，给个能看能清的入口）。
 *
 * ## 只清"我们自己、无锁"的两块，别硬删 okhttp 的活缓存
 * `cacheRoot` 下并存几套缓存：
 * - `pixiv_cache/`（接口 JSON 落盘，[DesktopPixivCache] 纯文件读写、无常驻句柄）与 `ugoira/`（逐帧解包目录）——**可安全删**；
 * - `venera_http_cache` / `venera_image_cache`——是 okhttp `Cache`（DiskLruCache）**开着句柄**在管，
 * 直接删文件会重演 `DiskLruCache corrupt`（真窗见过）。那两块的正规清法是 `Cache.evictAll`，
 *   得从引擎那台拿句柄，本件**不越俎代庖去删**（宁可少清，不可清坏）。
 *
 * 因此 [clear] 只回收 pixiv + ugoira；[measure] 只读统计整个 cacheRoot 的占用给用户看。均 IO 线程、可中断。
 */
internal object DesktopCacheMaintenance {

    private val CLEARABLE_DIRS = listOf("pixiv_cache", "ugoira")

    /** 占用统计（字节）。只读、不抛：算不动就当 0。 */
    suspend fun measureBytes(paths: PathProvider): Long = withContext(Dispatchers.IO) {
        runCatching { directorySize(paths.cacheRoot) }.getOrDefault(0L)
    }

    /**
     * 清"接口 + 动图"缓存，返回**释放字节数**（负数 = cacheRoot 都没了，异常态）。
     * 删后建回空目录，避免 [DesktopPixivCache] 下次写时路径不存在。
     */
    suspend fun clear(paths: PathProvider): Long = withContext(Dispatchers.IO) {
        var freed = 0L
        for (name in CLEARABLE_DIRS) {
            val dir = File(paths.cacheRoot, name)
            if (!dir.isDirectory) continue
            freed += runCatching { directorySize(dir) }.getOrDefault(0L)
            runCatching {
                dir.listFiles()?.forEach { it.deleteRecursively() }
            }
        }
        freed
    }

    private fun directorySize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty()) {
            val f = stack.removeLast()
            if (f.isDirectory) {
                f.listFiles()?.let { stack.addAll(it) }
            } else {
                total += f.length()
            }
        }
        return total
    }
}

/** 字节数 → 人读（MB 为主，<1MB 退 KB）。 */
internal fun formatBytes(bytes: Long): String {
    val mb = bytes / 1024.0 / 1024.0
    return when {
        bytes <= 0L -> "0 MB"
        mb >= 1.0 -> "%.1f MB".format(mb)
        else -> "%.0f KB".format(bytes / 1024.0)
    }
}
